package net.igng.mcstatus.feature.wiki

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.igng.core.data.IgngCacheScope
import net.igng.core.data.rememberScreenHolder
import net.igng.core.data.rememberSwrResource
import net.igng.core.data.runSuspendCatching
import net.igng.core.designsystem.IgngAmbientCanvas
import net.igng.core.designsystem.IgngBrowserLauncher
import net.igng.core.designsystem.IgngButton
import net.igng.core.designsystem.IgngButtonStyle
import net.igng.core.designsystem.IgngDetailSheet
import net.igng.core.designsystem.IgngHapticKind
import net.igng.core.designsystem.IgngImageGalleryLightbox
import net.igng.core.designsystem.IgngInteractiveTile
import net.igng.core.designsystem.IgngLoadingBlock
import net.igng.core.designsystem.IgngPullToRefreshBox
import net.igng.core.designsystem.IgngReaderTypographySheet
import net.igng.core.designsystem.IgngSearchField
import net.igng.core.designsystem.IgngSectionHeader
import net.igng.core.designsystem.IgngSkeletonRows
import net.igng.core.designsystem.IgngStateBlock
import net.igng.core.designsystem.IgngTheme
import net.igng.core.designsystem.IgngTopBar
import net.igng.core.designsystem.ProvideIgngReaderSurface
import net.igng.core.designsystem.igngStaggeredReveal
import net.igng.core.designsystem.rememberIgngHaptics
import net.igng.core.markdown.IgngMarkdownBlockView
import net.igng.core.markdown.MarkdownParser
import net.igng.mcstatus.core.data.McCacheKeys
import net.igng.mcstatus.core.data.McCachePolicies
import net.igng.mcstatus.core.data.McCacheTags
import net.igng.mcstatus.core.data.McDiskCodecs
import net.igng.mcstatus.core.data.SettingsRepository
import net.igng.mcstatus.core.data.WikiCatalogResponse
import net.igng.mcstatus.core.data.WikiPageDetail
import net.igng.mcstatus.core.data.WikiPageSummary
import net.igng.mcstatus.core.data.WikiRepository
import net.igng.mcstatus.core.model.AppPreferences
import net.igng.mcstatus.core.model.IgngFormatters

private class WikiCatalogUiHolder {
    var search by mutableStateOf("")
}

@Composable
fun WikiCatalogScreen(
    wikiRepository: WikiRepository,
    settingsRepository: SettingsRepository,
    bottomPadding: PaddingValues,
    onOpenArticle: (Int) -> Unit,
) {
    val colors = IgngTheme.colors
    val recentIds by settingsRepository.recentWikiIds.collectAsState(initial = emptyList())
    val uiHolder = rememberScreenHolder("wiki_catalog_ui") { WikiCatalogUiHolder() }

    val (catalogState, catalogHandle) = rememberSwrResource<WikiCatalogResponse>(
        cache = wikiRepository.swrCache,
        cacheKey = McCacheKeys.wikiCatalog(),
        scope = IgngCacheScope.PUBLIC,
        policy = McCachePolicies.Static,
        tags = setOf(McCacheTags.WIKI),
        diskCodec = McDiskCodecs.wikiCatalog(),
        legacyRawProvider = {
            wikiRepository.readCachedCatalog()?.let { McDiskCodecs.wikiCatalog().encode(it) }
        },
        slotKey = "wiki_catalog",
    ) {
        runSuspendCatching { wikiRepository.fetchCatalog() }
    }

    val catalog = catalogState.data
    val loading = catalogState.isInitialLoading
    val refreshing = catalogState.isUserRefreshing
    val error = catalogState.error

    val allPages = remember(catalog) {
        val grouped = catalog?.groups.orEmpty().flatMap { g ->
            g.pages.map { it.copy(groupTitle = g.title) }
        }
        (grouped + catalog?.pages.orEmpty()).distinctBy { it.id }
    }
    val recentPages = remember(recentIds, allPages) {
        val byId = allPages.associateBy { it.id }
        recentIds.mapNotNull { byId[it] }
    }

    IgngAmbientCanvas {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            IgngTopBar(
                title = "IGNGmc 百科",
                subtitle = "官方 IGNGmc 知识库文档",
            )
            IgngSearchField(
                value = uiHolder.search,
                onValueChange = { uiHolder.search = it },
                placeholder = "搜索教程、指令、领地规则或插件说明…",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(10.dp))

            IgngPullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { catalogHandle.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    loading && catalog == null -> Box(Modifier.padding(16.dp)) { IgngSkeletonRows(4) }
                    error != null && catalog == null -> Box(Modifier.padding(16.dp)) {
                        IgngStateBlock(
                            title = "百科目录没有读到",
                            description = error,
                            isError = true,
                            actionLabel = "重试",
                            onAction = { catalogHandle.refresh() },
                        )
                    }
                    else -> {
                        val q = uiHolder.search.trim().lowercase()
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = bottomPadding.calculateBottomPadding() + 20.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (q.isEmpty() && recentPages.isNotEmpty()) {
                                item(key = "recent") {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        IgngSectionHeader(title = "最近阅读")
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            recentPages.forEach { page ->
                                                IgngInteractiveTile(
                                                    onClick = { onOpenArticle(page.id) },
                                                    contentPadding = PaddingValues(horizontal = 13.dp, vertical = 10.dp),
                                                    modifier = Modifier.width(176.dp),
                                                ) {
                                                    Text(page.title, style = IgngTheme.type.bodyMedium.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Spacer(Modifier.height(2.dp))
                                                    Text(page.groupTitle ?: "文档", style = IgngTheme.type.badge, color = colors.textSecondary, maxLines = 1)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (q.isNotEmpty()) {
                                val matched = allPages.filter {
                                    it.title.lowercase().contains(q) || (it.summary?.lowercase()?.contains(q) == true)
                                }
                                if (matched.isEmpty()) {
                                    item { IgngStateBlock(title = "没有找到相关词条", description = "换一个关键词试试。") }
                                } else {
                                    itemsIndexed(matched, key = { _, p -> p.id }) { idx, page ->
                                        WikiPageRow(page, onOpenArticle, Modifier.fillMaxWidth().igngStaggeredReveal(idx))
                                    }
                                }
                            } else {
                                val groups = catalog?.groups.orEmpty()
                                if (groups.isNotEmpty()) {
                                    groups.forEach { group ->
                                        item(key = "grp_${group.id}") {
                                            IgngSectionHeader(title = group.title, subtitle = "${group.pages.size} 篇文档")
                                        }
                                        itemsIndexed(group.pages, key = { _, p -> p.id }) { idx, page ->
                                            WikiPageRow(page.copy(groupTitle = group.title), onOpenArticle, Modifier.fillMaxWidth().igngStaggeredReveal(idx))
                                        }
                                    }
                                } else {
                                    itemsIndexed(allPages, key = { _, p -> p.id }) { idx, page ->
                                        WikiPageRow(page, onOpenArticle, Modifier.fillMaxWidth().igngStaggeredReveal(idx))
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

@Composable
private fun WikiPageRow(
    page: WikiPageSummary,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IgngTheme.colors
    IgngInteractiveTile(
        onClick = { onOpen(page.id) },
        modifier = modifier,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.Book, null, tint = colors.accent, modifier = Modifier.size(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(page.title, style = IgngTheme.type.cardTitle, color = colors.textPrimary)
                page.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                    Spacer(Modifier.height(2.dp))
                    Text(summary, style = IgngTheme.type.caption, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = colors.textSecondary)
        }
    }
}

@Composable
fun WikiArticleScreen(
    pageId: Int,
    wikiRepository: WikiRepository,
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberIgngHaptics()
    val listState = rememberLazyListState()
    val prefs by settingsRepository.preferences.collectAsState(initial = AppPreferences())

    var showToc by remember { mutableStateOf(false) }
    var showTypoSheet by remember { mutableStateOf(false) }
    var lightboxIndex by remember { mutableStateOf<Int?>(null) }

    val (pageState, pageHandle) = rememberSwrResource<WikiPageDetail>(
        cache = wikiRepository.swrCache,
        cacheKey = McCacheKeys.wikiPage(pageId),
        scope = IgngCacheScope.PUBLIC,
        policy = McCachePolicies.WikiArticle,
        tags = setOf(McCacheTags.WIKI),
        diskCodec = McDiskCodecs.wikiPage(pageId),
        legacyRawProvider = {
            wikiRepository.readCachedPage(pageId)?.let { McDiskCodecs.wikiPage(pageId).encode(it) }
        },
        slotKey = "wiki_article_$pageId",
    ) {
        runSuspendCatching { wikiRepository.fetchPage(pageId) }
    }
    val page = pageState.data
    val loading = pageState.isInitialLoading
    val error = pageState.error

    LaunchedEffect(pageId) {
        settingsRepository.recordRecentWiki(pageId)
    }

    val document = remember(page?.content) {
        MarkdownParser.parse(source = page?.content.orEmpty())
    }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    ProvideIgngReaderSurface(tone = prefs.reader.canvasTone) {
        val colors = IgngTheme.colors
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.canvas)
                .statusBarsPadding(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                IgngTopBar(
                    title = page?.title ?: "百科文档",
                    subtitle = page?.updatedAt?.let { "更新于 ${IgngFormatters.formatIsoDateTime(it, short = true)}" },
                    onBack = onBack,
                    actions = {
                        if (document.toc.isNotEmpty()) {
                            IconButton(onClick = {
                                haptic(IgngHapticKind.Tap)
                                showToc = true
                            }) {
                                Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, contentDescription = "目录", tint = colors.textPrimary)
                            }
                        }
                        IconButton(onClick = {
                            haptic(IgngHapticKind.Tap)
                            showTypoSheet = true
                        }) {
                            Icon(Icons.Rounded.TextFields, contentDescription = "阅读排版", tint = colors.textPrimary)
                        }
                    },
                )
                IgngPullToRefreshBox(
                    isRefreshing = pageState.isUserRefreshing,
                    onRefresh = { pageHandle.refresh() },
                    modifier = Modifier.weight(1f),
                ) {
                    when {
                        loading && page == null -> IgngLoadingBlock(fill = true)
                        error != null && page == null -> Box(Modifier.padding(16.dp)) {
                            IgngStateBlock(
                                title = "读取失败",
                                description = error,
                                isError = true,
                                actionLabel = "重试",
                                onAction = { pageHandle.refresh() },
                            )
                        }
                        else -> LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 18.dp,
                            end = 18.dp,
                            top = 12.dp,
                            bottom = navBottom + 28.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(document.blocks) { _, block ->
                            IgngMarkdownBlockView(
                                block = block,
                                typography = prefs.reader,
                                onImageClick = { clickedUrl ->
                                    val idx = document.imageUrls.indexOf(clickedUrl).coerceAtLeast(0)
                                    if (document.imageUrls.isNotEmpty()) lightboxIndex = idx
                                },
                                onLinkClick = { url ->
                                    IgngBrowserLauncher.openExternalUrl(context, url, colors.isDark)
                                },
                            )
                        }
                    }
                    }
                }
            }
        }

        if (showToc) {
            IgngDetailSheet(
                onDismiss = { showToc = false },
                title = "文章目录",
                scrollable = true,
            ) {
                document.toc.forEach { entry ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(IgngTheme.radii.sm)
                            .clickable {
                                haptic(IgngHapticKind.Tap)
                                showToc = false
                                scope.launch { listState.animateScrollToItem(entry.blockIndex) }
                            }
                            .padding(
                                start = ((entry.level - 1) * 14).dp,
                                top = 10.dp,
                                bottom = 10.dp,
                                end = 8.dp,
                            ),
                    ) {
                        Text(
                            text = entry.title,
                            style = if (entry.level <= 2) IgngTheme.type.bodyMedium.copy(fontWeight = FontWeight.Bold) else IgngTheme.type.body,
                            color = colors.textPrimary,
                        )
                    }
                }
            }
        }

        if (showTypoSheet) {
            IgngReaderTypographySheet(
                prefs = prefs.reader,
                onUpdate = { transform -> scope.launch { settingsRepository.updateReaderPrefs(transform) } },
                onDismiss = { showTypoSheet = false },
            )
        }

        lightboxIndex?.let { idx ->
            IgngImageGalleryLightbox(
                imageUrls = document.imageUrls,
                initialIndex = idx,
                onDismiss = { lightboxIndex = null },
            )
        }
    }
}
