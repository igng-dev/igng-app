package net.igng.bbs.feature.feed

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.igng.bbs.common.BbsContinueReadingCard
import net.igng.bbs.common.BbsPostRowCard
import net.igng.bbs.common.BbsRecommendRail
import net.igng.bbs.core.data.BbsAppContainer
import net.igng.bbs.core.data.BbsCacheKeys
import net.igng.bbs.core.data.BbsCachePolicies
import net.igng.bbs.core.data.BbsCacheTags
import net.igng.bbs.core.data.BbsDiskCodecs
import net.igng.bbs.core.model.BbsFormatters
import net.igng.bbs.core.model.BbsHomeDto
import net.igng.bbs.core.model.CategoryDto
import net.igng.bbs.core.model.PaginatedResponseDto
import net.igng.bbs.core.model.PostSummaryDto
import net.igng.core.data.IgngCacheScope
import net.igng.core.data.rememberScreenHolder
import net.igng.core.data.rememberSwrResource
import net.igng.core.data.toKotlinResult
import net.igng.core.designsystem.IgngEmptyTile
import net.igng.core.designsystem.IgngErrorTile
import net.igng.core.designsystem.IgngFoldedCoreGlyph
import net.igng.core.designsystem.IgngHapticKind
import net.igng.core.designsystem.IgngLoadingCenter
import net.igng.core.designsystem.IgngMotion
import net.igng.core.designsystem.IgngPullToRefreshBox
import net.igng.core.designsystem.IgngRadius
import net.igng.core.designsystem.IgngSectionHeader
import net.igng.core.designsystem.IgngTagChip
import net.igng.core.designsystem.IgngTheme
import net.igng.core.designsystem.igngBentoEnter
import net.igng.core.designsystem.igngPressScale
import net.igng.core.designsystem.rememberIgngHaptics
import net.igng.core.network.ApiResult

private class FeedHomeUiHolder {
    val sortState = mutableStateOf("latest")
    val selectedCategoryState = mutableStateOf<CategoryDto?>(null)

    var extraFeedKey by mutableStateOf("")
    var extraPosts by mutableStateOf<List<PostSummaryDto>>(emptyList())
    var loadedPage by mutableIntStateOf(1)
    var extraTotalPages by mutableIntStateOf(1)
    var loadingMore by mutableStateOf(false)

    fun resetPagination(feedKey: String) {
        extraFeedKey = feedKey
        extraPosts = emptyList()
        loadedPage = 1
        extraTotalPages = 1
        loadingMore = false
    }
}

@Composable
fun FeedHomeScreen(
    container: BbsAppContainer,
    onOpenPost: (String) -> Unit,
    onOpenCategory: (String, String) -> Unit,
    onOpenTag: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenRules: () -> Unit,
    bottomInsetPadding: PaddingValues = PaddingValues(bottom = 96.dp),
) {
    val colors = IgngTheme.colors
    val scope = rememberCoroutineScope()
    val bootstrap by container.bootstrap.collectAsState()
    val history by container.settingsRepository.readingHistory.collectAsState(initial = emptyList())

    val holder = rememberScreenHolder("bbs_feed_home_ui") { FeedHomeUiHolder() }
    var sort by holder.sortState
    var selectedCategory by holder.selectedCategoryState

    val excludeSeriesOnFeed = selectedCategory == null
    val isDefaultFeed = selectedCategory == null && sort == "latest"
    val feedKey = BbsCacheKeys.feed(selectedCategory?.slug, sort, excludeSeries = excludeSeriesOnFeed)

    val (homeState, homeHandle) = rememberSwrResource<BbsHomeDto>(
        cache = container.swrCache,
        cacheKey = BbsCacheKeys.HOME,
        scope = IgngCacheScope.SESSION_AWARE,
        policy = BbsCachePolicies.Standard,
        tags = setOf(BbsCacheTags.HOME),
        diskCodec = BbsDiskCodecs.HOME,
        enabled = selectedCategory == null,
        slotKey = "bbs_home_slot",
    ) {
        container.feedRepository.fetchHome().toKotlinResult()
    }

    val (feedState, feedHandle) = rememberSwrResource<PaginatedResponseDto<PostSummaryDto>>(
        cache = container.swrCache,
        cacheKey = feedKey,
        scope = IgngCacheScope.SESSION_AWARE,
        policy = BbsCachePolicies.Standard,
        tags = setOf(BbsCacheTags.FEED, BbsCacheTags.POST_LISTS),
        diskCodec = if (isDefaultFeed) BbsDiskCodecs.DEFAULT_FEED else null,
        slotKey = "bbs_feed_slot",
    ) {
        container.feedRepository.listPosts(
            page = 1,
            pageSize = 20,
            sort = sort,
            category = selectedCategory?.slug,
            excludeSeries = excludeSeriesOnFeed,
        ).toKotlinResult()
    }

    val home = homeState.data
    val page1Data = feedState.data
    val rawPage1Posts = page1Data?.data ?: if (isDefaultFeed) home?.latestPosts.orEmpty() else emptyList()
    val page1Posts = remember(rawPage1Posts, excludeSeriesOnFeed) {
        if (excludeSeriesOnFeed) rawPage1Posts.filter { it.series == null } else rawPage1Posts
    }
    val extraPosts = if (holder.extraFeedKey == feedKey) holder.extraPosts else emptyList()
    val posts = remember(page1Posts, extraPosts, excludeSeriesOnFeed) {
        val merged = if (extraPosts.isEmpty()) {
            page1Posts
        } else {
            val seen = page1Posts.mapTo(HashSet(page1Posts.size)) { it.id }
            page1Posts + extraPosts.filterNot { it.id in seen }
        }
        if (excludeSeriesOnFeed) merged.filter { it.series == null } else merged
    }

    val page = if (holder.extraFeedKey == feedKey && holder.loadedPage > 1) {
        holder.loadedPage
    } else {
        page1Data?.pagination?.page ?: 1
    }
    val totalPages = if (holder.extraFeedKey == feedKey && holder.loadedPage > 1) {
        holder.extraTotalPages.coerceAtLeast(page1Data?.pagination?.totalPages ?: 1)
    } else {
        (page1Data?.pagination?.totalPages ?: 1).coerceAtLeast(1)
    }

    val loading = feedState.isInitialLoading && posts.isEmpty()
    val errorMessage = if (posts.isEmpty()) feedState.error else null
    val refreshing = feedState.isUserRefreshing || homeState.isUserRefreshing
    val revalidating = feedState.isRevalidating || (selectedCategory == null && homeState.isRevalidating)
    val loadingMore = holder.loadingMore && holder.extraFeedKey == feedKey

    suspend fun loadNextPage() {
        if (holder.loadingMore || page >= totalPages) return
        val targetKey = feedKey
        val nextPage = page + 1
        holder.loadingMore = true
        when (
            val res = container.feedRepository.listPosts(
                page = nextPage,
                pageSize = 20,
                sort = sort,
                category = selectedCategory?.slug,
                excludeSeries = excludeSeriesOnFeed,
            )
        ) {
            is ApiResult.Success -> {
                if (feedKey == targetKey) {
                    val existing = if (holder.extraFeedKey == targetKey) holder.extraPosts else emptyList()
                    val seen = HashSet<String>(page1Posts.size + existing.size)
                    page1Posts.forEach { seen.add(it.id) }
                    existing.forEach { seen.add(it.id) }
                    val incoming = if (excludeSeriesOnFeed) res.data.data.filter { it.series == null } else res.data.data
                    holder.extraFeedKey = targetKey
                    holder.extraPosts = existing + incoming.filterNot { it.id in seen }
                    holder.loadedPage = res.data.pagination.page
                    holder.extraTotalPages = res.data.pagination.totalPages.coerceAtLeast(1)
                }
            }
            is ApiResult.Failure -> Unit
        }
        holder.loadingMore = false
    }

    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= (listState.layoutInfo.totalItemsCount - 4).coerceAtLeast(0)
        }
    }
    LaunchedEffect(shouldLoadMore, posts.size, feedKey) {
        if (shouldLoadMore && posts.isNotEmpty() && page < totalPages && !holder.loadingMore) {
            loadNextPage()
        }
    }

    val categories = bootstrap?.categories?.takeIf { it.isNotEmpty() } ?: home?.categories.orEmpty()
    val recs = remember(home?.recommendations) {
        home?.recommendations.orEmpty().filter { it.post.series == null }
    }
    val unreadCount = bootstrap?.viewer?.unread?.total ?: 0
    val continueEntry = history.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding(),
    ) {
        // Brand Header + Search Pill + Inbox Bell
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "IGNG BBS",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(IgngRadius.pill))
                    .background(colors.tile)
                    .border(1.dp, colors.line, RoundedCornerShape(IgngRadius.pill))
                    .igngPressScale(onClick = onOpenSearch)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "搜索",
                    tint = colors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "搜索文章、专栏、作者…",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    maxLines = 1,
                )
            }
            Box {
                IconButton(onClick = onOpenInbox) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsNone,
                        contentDescription = "消息收件箱",
                        tint = colors.textPrimary,
                    )
                }
                if (unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 8.dp)
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(colors.danger),
                    )
                }
            }
        }

        // Fixed Top Category Navigation Bar (directly beneath search bar)
        if (categories.isNotEmpty()) {
            BbsCategoryTabBar(
                categories = categories,
                selectedCategory = selectedCategory,
                onSelect = { cat ->
                    if (selectedCategory?.id != cat?.id) {
                        selectedCategory = cat
                        scope.launch { listState.scrollToItem(0) }
                    }
                },
            )
        }

        IgngPullToRefreshBox(
            isRefreshing = refreshing,
            isRevalidating = revalidating,
            showIdleDivider = true,
            onRefresh = {
                holder.resetPagination(feedKey)
                scope.launch { container.refreshBootstrap(force = true) }
                if (selectedCategory == null) {
                    homeHandle.silentRefresh()
                }
                feedHandle.refresh()
            },
            modifier = Modifier.weight(1f),
        ) {
            if (loading) {
                IgngLoadingCenter(loading = true)
            } else if (errorMessage != null) {
                Box(modifier = Modifier.padding(16.dp)) {
                    IgngErrorTile(
                        message = errorMessage,
                        onRetry = {
                            if (selectedCategory == null) homeHandle.silentRefresh()
                            feedHandle.refresh()
                        },
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 12.dp,
                        bottom = bottomInsetPadding.calculateBottomPadding() + 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Continue Reading Card
                    if (continueEntry != null) {
                        item(key = "continue_reading") {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                BbsContinueReadingCard(
                                    entry = continueEntry,
                                    onResume = { onOpenPost(continueEntry.postId) },
                                )
                            }
                        }
                    }

                    // Recommendations Rail (only on All view, series-free)
                    if (selectedCategory == null && recs.isNotEmpty()) {
                        item(key = "rec_header") {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                IgngSectionHeader(
                                    title = "社区精选",
                                    actionLabel = "社区规范",
                                    onAction = onOpenRules,
                                )
                            }
                        }
                        item(key = "rec_rail") {
                            BbsRecommendRail(
                                slots = recs,
                                onPostClick = { onOpenPost(it.id) },
                            )
                        }
                    }

                    // Sort Bar
                    item(key = "sort_bar") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            IgngTagChip(
                                text = "最新",
                                selected = sort == "latest",
                                onClick = {
                                    if (sort != "latest") {
                                        sort = "latest"
                                        scope.launch { listState.scrollToItem(0) }
                                    }
                                },
                            )
                            IgngTagChip(
                                text = "热门",
                                selected = sort == "hot",
                                onClick = {
                                    if (sort != "hot") {
                                        sort = "hot"
                                        scope.launch { listState.scrollToItem(0) }
                                    }
                                },
                            )
                        }
                    }

                    if (posts.isEmpty()) {
                        item(key = "empty_feed") {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                IgngEmptyTile(
                                    title = "这里还没有文章",
                                    subtitle = "换个分类看看，或者去创作中心发布第一篇内容。",
                                )
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = posts,
                            key = { _, post -> "post_${post.id}" },
                        ) { index, post ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .igngBentoEnter(index),
                            ) {
                                BbsPostRowCard(
                                    post = post,
                                    onClick = { onOpenPost(post.id) },
                                    onAuthorClick = post.author?.id?.let { uid -> { onOpenUser(uid) } },
                                    onTagClick = onOpenTag,
                                    showSeriesBadge = selectedCategory != null,
                                )
                            }
                        }
                    }

                    item(key = "footer_loader") {
                        val footIntensity = IgngTheme.motionIntensity
                        androidx.compose.animation.AnimatedVisibility(
                            visible = loadingMore,
                            enter = fadeIn(IgngMotion.microTween(footIntensity)),
                            exit = fadeOut(IgngMotion.microTween(footIntensity)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                IgngFoldedCoreGlyph(size = 24.dp)
                            }
                        }
                        if (!loadingMore && page >= totalPages && posts.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "已浏览全部内容",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BbsCategoryTabBar(
    categories: List<CategoryDto>,
    selectedCategory: CategoryDto?,
    onSelect: (CategoryDto?) -> Unit,
) {
    val tabListState = rememberLazyListState()
    val selectedIndex = remember(categories, selectedCategory?.id) {
        if (selectedCategory == null) 0
        else (categories.indexOfFirst { it.id == selectedCategory.id } + 1).coerceAtLeast(0)
    }

    LaunchedEffect(selectedIndex) {
        val target = (selectedIndex - 1).coerceAtLeast(0)
        tabListState.animateScrollToItem(target)
    }

    LazyRow(
        state = tabListState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "tab_all") {
            CategoryTabItem(
                label = "全部",
                selected = selectedCategory == null,
                onClick = { onSelect(null) },
            )
        }
        itemsIndexed(categories, key = { _, cat -> "tab_${cat.id}" }) { _, cat ->
            CategoryTabItem(
                label = BbsFormatters.categoryDisplayName(cat),
                selected = selectedCategory?.id == cat.id,
                onClick = { onSelect(cat) },
            )
        }
    }
}

@Composable
private fun CategoryTabItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = IgngTheme.colors
    val intensity = IgngTheme.motionIntensity
    val haptic = rememberIgngHaptics()

    val textColor by animateColorAsState(
        targetValue = if (selected) colors.textPrimary else colors.textSecondary,
        animationSpec = IgngMotion.microTween(intensity),
        label = "catTabText",
    )
    val indicatorWidth by animateDpAsState(
        targetValue = if (selected) 20.dp else 0.dp,
        animationSpec = IgngMotion.dockSpring(intensity),
        label = "catTabIndicatorWidth",
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(IgngRadius.control))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (!selected) haptic(IgngHapticKind.Tick)
                    onClick()
                },
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = textColor,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(indicatorWidth)
                .height(3.dp)
                .clip(RoundedCornerShape(IgngRadius.pill))
                .background(colors.accent),
        )
    }
}
