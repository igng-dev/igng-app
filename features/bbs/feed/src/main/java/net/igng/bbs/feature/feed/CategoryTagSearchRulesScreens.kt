package net.igng.bbs.feature.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.igng.bbs.common.BbsPostRowCard
import net.igng.bbs.core.data.BbsAppContainer
import net.igng.bbs.core.data.BbsCacheKeys
import net.igng.bbs.core.data.BbsCachePolicies
import net.igng.bbs.core.data.BbsCacheTags
import net.igng.bbs.core.data.BbsDiskCodecs
import net.igng.bbs.core.model.BbsFormatters
import net.igng.bbs.core.model.BbsRulesDto
import net.igng.bbs.core.model.ListWrapperDto
import net.igng.bbs.core.model.PaginatedResponseDto
import net.igng.bbs.core.model.PostSummaryDto
import net.igng.bbs.core.model.SearchResponseDto
import net.igng.bbs.core.model.TagStatDto
import net.igng.core.data.IgngCacheScope
import net.igng.core.data.rememberScreenHolder
import net.igng.core.data.rememberSwrResource
import net.igng.core.data.toKotlinResult
import net.igng.core.designsystem.IgngAvatar
import net.igng.core.designsystem.IgngEmptyTile
import net.igng.core.designsystem.IgngErrorTile
import net.igng.core.designsystem.IgngLoadingCenter
import net.igng.core.designsystem.IgngPullToRefreshBox
import net.igng.core.designsystem.IgngRadius
import net.igng.core.designsystem.IgngSectionHeader
import net.igng.core.designsystem.IgngTagChip
import net.igng.core.designsystem.IgngTheme
import net.igng.core.designsystem.IgngTile
import net.igng.core.designsystem.IgngTone
import net.igng.core.designsystem.IgngTopBar
import net.igng.core.designsystem.igngBentoEnter
import net.igng.core.designsystem.rememberIgngPageContentPadding
import net.igng.core.network.ApiResult

@Composable
fun CategoryFeedScreen(
    container: BbsAppContainer,
    categorySlug: String,
    categoryTitle: String,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenTag: (String) -> Unit,
) {
    FilteredPostListScreen(
        container = container,
        title = BbsFormatters.categoryDisplayName(categorySlug, categoryTitle),
        subtitle = "分区文章",
        categorySlug = categorySlug,
        tag = null,
        onBack = onBack,
        onOpenPost = onOpenPost,
        onOpenUser = onOpenUser,
        onOpenTag = onOpenTag,
    )
}

@Composable
fun TagFeedScreen(
    container: BbsAppContainer,
    tag: String,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenTag: (String) -> Unit,
) {
    FilteredPostListScreen(
        container = container,
        title = BbsFormatters.tagDisplayLabel(tag),
        subtitle = "标签聚合",
        categorySlug = null,
        tag = tag,
        onBack = onBack,
        onOpenPost = onOpenPost,
        onOpenUser = onOpenUser,
        onOpenTag = onOpenTag,
    )
}

private class FilteredFeedUiHolder {
    val sortState = mutableStateOf("latest")
    var extraKey by mutableStateOf("")
    var extraPosts by mutableStateOf<List<PostSummaryDto>>(emptyList())
    var loadedPage by mutableIntStateOf(1)
    var extraTotalPages by mutableIntStateOf(1)
    var loadingMore by mutableStateOf(false)

    fun resetPagination(key: String) {
        extraKey = key
        extraPosts = emptyList()
        loadedPage = 1
        extraTotalPages = 1
        loadingMore = false
    }
}

@Composable
private fun FilteredPostListScreen(
    container: BbsAppContainer,
    title: String,
    subtitle: String,
    categorySlug: String?,
    tag: String?,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenTag: (String) -> Unit,
) {
    val colors = IgngTheme.colors
    val scope = rememberCoroutineScope()
    val holderKey = "bbs_filtered_${categorySlug.orEmpty()}_${tag.orEmpty()}"
    val holder = rememberScreenHolder(holderKey) { FilteredFeedUiHolder() }
    var sort by holder.sortState

    val cacheKey = if (categorySlug != null) {
        BbsCacheKeys.feed(categorySlug, sort)
    } else {
        BbsCacheKeys.tagFeed(tag.orEmpty(), sort)
    }

    val (feedState, feedHandle) = rememberSwrResource<PaginatedResponseDto<PostSummaryDto>>(
        cache = container.swrCache,
        cacheKey = cacheKey,
        scope = IgngCacheScope.SESSION_AWARE,
        policy = BbsCachePolicies.Standard,
        tags = setOf(BbsCacheTags.FEED, BbsCacheTags.POST_LISTS),
        slotKey = holderKey,
    ) {
        container.feedRepository.listPosts(
            page = 1,
            pageSize = 20,
            sort = sort,
            category = categorySlug,
            tag = tag,
        ).toKotlinResult()
    }

    val page1Posts = feedState.data?.data.orEmpty()
    val extraPosts = if (holder.extraKey == cacheKey) holder.extraPosts else emptyList()
    val posts = remember(page1Posts, extraPosts) {
        if (extraPosts.isEmpty()) {
            page1Posts
        } else {
            val seen = page1Posts.mapTo(HashSet(page1Posts.size)) { it.id }
            page1Posts + extraPosts.filterNot { it.id in seen }
        }
    }
    val page = if (holder.extraKey == cacheKey && holder.loadedPage > 1) {
        holder.loadedPage
    } else {
        feedState.data?.pagination?.page ?: 1
    }
    val totalPages = if (holder.extraKey == cacheKey && holder.loadedPage > 1) {
        holder.extraTotalPages.coerceAtLeast(feedState.data?.pagination?.totalPages ?: 1)
    } else {
        (feedState.data?.pagination?.totalPages ?: 1).coerceAtLeast(1)
    }

    suspend fun loadMore() {
        if (holder.loadingMore || page >= totalPages) return
        val targetKey = cacheKey
        holder.loadingMore = true
        when (
            val res = container.feedRepository.listPosts(
                page = page + 1,
                pageSize = 20,
                sort = sort,
                category = categorySlug,
                tag = tag,
            )
        ) {
            is ApiResult.Success -> {
                if (cacheKey == targetKey) {
                    val existing = if (holder.extraKey == targetKey) holder.extraPosts else emptyList()
                    val seen = HashSet<String>(page1Posts.size + existing.size)
                    page1Posts.forEach { seen.add(it.id) }
                    existing.forEach { seen.add(it.id) }
                    holder.extraKey = targetKey
                    holder.extraPosts = existing + res.data.data.filterNot { it.id in seen }
                    holder.loadedPage = res.data.pagination.page
                    holder.extraTotalPages = res.data.pagination.totalPages.coerceAtLeast(1)
                }
            }
            is ApiResult.Failure -> Unit
        }
        holder.loadingMore = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding(),
    ) {
        IgngTopBar(
            title = title,
            subtitle = subtitle,
            onBack = onBack,
            actions = {
                IgngTagChip(text = "最新", selected = sort == "latest", onClick = { sort = "latest" })
                Spacer(Modifier.width(4.dp))
                IgngTagChip(text = "热门", selected = sort == "hot", onClick = { sort = "hot" })
            },
        )
        IgngPullToRefreshBox(
            isRefreshing = feedState.isUserRefreshing,
            isRevalidating = feedState.isRevalidating,
            onRefresh = {
                holder.resetPagination(cacheKey)
                feedHandle.refresh()
            },
            modifier = Modifier.weight(1f),
        ) {
            when {
                feedState.isInitialLoading && posts.isEmpty() -> IgngLoadingCenter(loading = true)
                feedState.error != null && posts.isEmpty() -> Box(Modifier.padding(16.dp)) {
                    IgngErrorTile(message = feedState.error!!, onRetry = { feedHandle.refresh() })
                }
                posts.isEmpty() -> Box(Modifier.padding(16.dp)) {
                    IgngEmptyTile(title = "暂无相关文章", subtitle = "这里暂时还没有符合条件的内容。")
                }
                else -> LazyColumn(
                    contentPadding = rememberIgngPageContentPadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(posts, key = { _, p -> p.id }) { idx, post ->
                        BbsPostRowCard(
                            post = post,
                            onClick = { onOpenPost(post.id) },
                            onAuthorClick = post.author?.id?.let { uid -> { onOpenUser(uid) } },
                            onTagClick = onOpenTag,
                            modifier = Modifier.igngBentoEnter(idx),
                        )
                    }
                    if (page < totalPages) {
                        item(key = "load_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                IgngTagChip(
                                    text = if (holder.loadingMore) "加载中…" else "加载更多",
                                    tone = IgngTone.ACCENT,
                                    onClick = { scope.launch { loadMore() } },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private class SearchUiHolder {
    val queryState = mutableStateOf("")
    val resultState = mutableStateOf<SearchResponseDto?>(null)
    val searchingState = mutableStateOf(false)
    val errorState = mutableStateOf<String?>(null)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    container: BbsAppContainer,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenColumn: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenTag: (String) -> Unit,
) {
    val colors = IgngTheme.colors
    val scope = rememberCoroutineScope()
    val holder = rememberScreenHolder("bbs_search_ui") { SearchUiHolder() }
    var query by holder.queryState
    var result by holder.resultState
    var searching by holder.searchingState
    var error by holder.errorState

    val (tagsState, _) = rememberSwrResource<ListWrapperDto<TagStatDto>>(
        cache = container.swrCache,
        cacheKey = BbsCacheKeys.TAGS,
        scope = IgngCacheScope.PUBLIC,
        policy = BbsCachePolicies.Static,
        tags = setOf(BbsCacheTags.TAGS),
        diskCodec = BbsDiskCodecs.TAGS,
        slotKey = "bbs_search_tags",
    ) {
        container.feedRepository.listTags(30).toKotlinResult()
    }
    val hotTags = tagsState.data?.data.orEmpty()

    fun executeSearch(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return
        // Direct jump if user enters post numeric ID or igngbbs.net/post/<id>
        val directId = Regex("^(?:post_)?(\\d+)$").matchEntire(trimmed)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("igngbbs\\.net/post/(\\d+)", RegexOption.IGNORE_CASE).find(trimmed)?.groupValues?.get(1)?.toIntOrNull()
        if (directId != null && trimmed.length <= 64) {
            onOpenPost(net.igng.bbs.core.model.BbsV1Id.post(directId))
            return
        }
        scope.launch {
            searching = true
            error = null
            when (val res = container.feedRepository.search(trimmed)) {
                is ApiResult.Success -> result = res.data
                is ApiResult.Failure -> error = res.error.message
            }
            searching = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = colors.textPrimary)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("关键词 / 文章编号 / igngbbs 链接", style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(IgngRadius.pill),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.tile,
                    unfocusedContainerColor = colors.tile,
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.line,
                ),
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.textMuted) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = ""; result = null; error = null }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "清除", tint = colors.textMuted)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { executeSearch(query) }),
            )
            IgngTagChip(text = "搜索", tone = IgngTone.ACCENT, onClick = { executeSearch(query) })
            Spacer(Modifier.width(4.dp))
        }

        when {
            searching -> IgngLoadingCenter(loading = true)
            error != null -> Box(Modifier.padding(16.dp)) {
                IgngErrorTile(message = error!!, onRetry = { executeSearch(query) })
            }
            result == null -> LazyColumn(
                contentPadding = rememberIgngPageContentPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    IgngTile {
                        Text("热门标签", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        Spacer(Modifier.height(10.dp))
                        if (hotTags.isEmpty()) {
                            Text("输入关键词开始搜索文章、专栏或作者。", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                hotTags.forEach { tag ->
                                    IgngTagChip(
                                        text = "${BbsFormatters.tagDisplayLabel(tag.name)} (${tag.count})",
                                        onClick = { onOpenTag(tag.name) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                val res = result!!
                LazyColumn(
                    contentPadding = rememberIgngPageContentPadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (res.users.isNotEmpty()) {
                        item { IgngSectionHeader(title = "相关用户") }
                        items(res.users, key = { "u_${it.id}" }) { user ->
                            IgngTile(onClick = { onOpenUser(user.id) }, padding = PaddingValues(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    IgngAvatar(user.avatarUrl, user.displayName, size = 36.dp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(user.displayName, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                                        Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                                    }
                                }
                            }
                        }
                    }
                    if (res.columns.isNotEmpty()) {
                        item { IgngSectionHeader(title = "相关专栏") }
                        items(res.columns, key = { "c_${it.id}" }) { col ->
                            IgngTile(onClick = { onOpenColumn(col.id) }, padding = PaddingValues(14.dp)) {
                                Text(col.name, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                                col.description?.takeIf { it.isNotBlank() }?.let { desc ->
                                    Spacer(Modifier.height(4.dp))
                                    Text(desc, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 2)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text("${col.postCount} 篇文章 · ${col.memberCount} 位成员", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                            }
                        }
                    }
                    item { IgngSectionHeader(title = "相关文章（${res.pagination.total}）") }
                    if (res.data.isEmpty()) {
                        item {
                            IgngEmptyTile(title = "没有匹配的文章", subtitle = "试试更换关键词，或直接输入文章编号跳转。")
                        }
                    } else {
                        items(res.data, key = { "p_${it.id}" }) { post ->
                            BbsPostRowCard(
                                post = post,
                                onClick = { onOpenPost(post.id) },
                                onAuthorClick = post.author?.id?.let { uid -> { onOpenUser(uid) } },
                                onTagClick = onOpenTag,
                            )
                        }
                    }
                }
            }
        }
    }
}

private class RulesUiHolder {
    val expandedClauseState = mutableStateOf<String?>(null)
}

@Composable
fun RulesScreen(
    container: BbsAppContainer,
    onBack: () -> Unit,
) {
    val colors = IgngTheme.colors
    val holder = rememberScreenHolder("bbs_rules_ui") { RulesUiHolder() }
    var expandedClause by holder.expandedClauseState

    val (rulesState, rulesHandle) = rememberSwrResource<BbsRulesDto>(
        cache = container.swrCache,
        cacheKey = BbsCacheKeys.RULES,
        scope = IgngCacheScope.PUBLIC,
        policy = BbsCachePolicies.Rules,
        tags = setOf(BbsCacheTags.RULES),
        diskCodec = BbsDiskCodecs.RULES,
        slotKey = "bbs_rules_slot",
    ) {
        container.feedRepository.fetchRules().toKotlinResult()
    }
    val rules = rulesState.data

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding(),
    ) {
        IgngTopBar(
            title = "社区规范与申诉准则",
            subtitle = rules?.let { "版本 ${it.version} · 更新于 ${it.updatedAt}" },
            onBack = onBack,
        )
        IgngPullToRefreshBox(
            isRefreshing = rulesState.isUserRefreshing,
            isRevalidating = rulesState.isRevalidating,
            onRefresh = { rulesHandle.refresh() },
            modifier = Modifier.weight(1f),
        ) {
            when {
                rulesState.isInitialLoading && rules == null -> IgngLoadingCenter(loading = true)
                rulesState.error != null && rules == null -> Box(Modifier.padding(16.dp)) {
                    IgngErrorTile(message = rulesState.error!!, onRetry = { rulesHandle.refresh() })
                }
                rules != null -> {
                    LazyColumn(
                        contentPadding = rememberIgngPageContentPadding(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (rules.principles.isNotEmpty()) {
                            item {
                                IgngTile {
                                    Text("基本原则", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                                    Spacer(Modifier.height(8.dp))
                                    rules.principles.forEach { p ->
                                        Text(p.title, style = MaterialTheme.typography.titleSmall, color = colors.accent)
                                        Text(p.text, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                        Spacer(Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                        item { IgngSectionHeader(title = "细则条款（${rules.clauses.size}）") }
                        items(rules.clauses, key = { it.code }) { clause ->
                            val isOpen = expandedClause == clause.code
                            IgngTile(onClick = { expandedClause = if (isOpen) null else clause.code }) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "${clause.code} · ${clause.title}",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.textPrimary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IgngTagChip(
                                        text = "S${clause.severity}",
                                        tone = if (clause.severity >= 3) IgngTone.DANGER else if (clause.severity == 2) IgngTone.WARNING else IgngTone.NEUTRAL,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(clause.description, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                val ruleIntensity = IgngTheme.motionIntensity
                                AnimatedVisibility(
                                    visible = isOpen,
                                    enter = androidx.compose.animation.expandVertically(net.igng.core.designsystem.IgngMotion.standardSpring(ruleIntensity)) +
                                        androidx.compose.animation.fadeIn(net.igng.core.designsystem.IgngMotion.microTween(ruleIntensity)),
                                    exit = androidx.compose.animation.shrinkVertically(net.igng.core.designsystem.IgngMotion.standardSpring(ruleIntensity)) +
                                        androidx.compose.animation.fadeOut(net.igng.core.designsystem.IgngMotion.microTween(ruleIntensity)),
                                ) {
                                    Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (clause.elements.isNotEmpty()) {
                                            Text("构成要件", style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
                                            clause.elements.forEach { e ->
                                                Text("• $e", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                            }
                                        }
                                        if (clause.excludes.isNotEmpty()) {
                                            Text("例外情形", style = MaterialTheme.typography.labelLarge, color = colors.success)
                                            clause.excludes.forEach { e ->
                                                Text("• $e", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                            }
                                        }
                                        if (clause.examples.violate.isNotEmpty()) {
                                            Text("违规示例", style = MaterialTheme.typography.labelLarge, color = colors.danger)
                                            clause.examples.violate.forEach { ex ->
                                                Text("× $ex", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                            }
                                        }
                                        if (clause.examples.allow.isNotEmpty()) {
                                            Text("允许示例", style = MaterialTheme.typography.labelLarge, color = colors.success)
                                            clause.examples.allow.forEach { ex ->
                                                Text("✓ $ex", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (rules.appeal.isNotEmpty()) {
                            item {
                                IgngTile(soft = true) {
                                    Text("申诉与复核机制", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                                    Spacer(Modifier.height(8.dp))
                                    rules.appeal.forEach { item ->
                                        Text(item.title, style = MaterialTheme.typography.titleSmall, color = colors.accent)
                                        Text(item.text, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                        Spacer(Modifier.height(6.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
