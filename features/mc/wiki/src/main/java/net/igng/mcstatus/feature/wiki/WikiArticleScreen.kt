package net.igng.mcstatus.feature.wiki

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.igng.core.data.IgngCacheScope
import net.igng.core.data.rememberScreenHolder
import net.igng.core.data.rememberSwrResource
import net.igng.core.data.runSuspendCatching
import net.igng.core.designsystem.IgngBrowserLauncher
import net.igng.core.designsystem.IgngGroupedSection
import net.igng.core.designsystem.IgngHairlineDivider
import net.igng.core.designsystem.IgngHapticKind
import net.igng.core.designsystem.IgngImageGalleryLightbox
import net.igng.core.designsystem.IgngLazyColumnScrollbar
import net.igng.core.designsystem.IgngLoadingBlock
import net.igng.core.designsystem.IgngMotion
import net.igng.core.designsystem.IgngPullToRefreshBox
import net.igng.core.designsystem.IgngRadius
import net.igng.core.designsystem.IgngReaderTypographySheet
import net.igng.core.designsystem.IgngStateBlock
import net.igng.core.designsystem.IgngStroke
import net.igng.core.designsystem.IgngTheme
import net.igng.core.designsystem.ProvideIgngReaderSurface
import net.igng.core.designsystem.copyTextToClipboard
import net.igng.core.designsystem.igngPressScale
import net.igng.core.designsystem.rememberIgngHaptics
import net.igng.core.designsystem.rememberIgngReadingProgress
import net.igng.core.designsystem.rememberIgngScrollBarVisibility
import net.igng.core.markdown.IgngMarkdownBlockView
import net.igng.core.markdown.MarkdownBlock
import net.igng.core.markdown.MarkdownParser
import net.igng.mcstatus.core.data.McCacheKeys
import net.igng.mcstatus.core.data.McCachePolicies
import net.igng.mcstatus.core.data.McCacheTags
import net.igng.mcstatus.core.data.McDiskCodecs
import net.igng.mcstatus.core.data.SettingsRepository
import net.igng.mcstatus.core.data.WikiCatalogResponse
import net.igng.mcstatus.core.data.WikiPageDetail
import net.igng.mcstatus.core.data.WikiRepository
import net.igng.mcstatus.core.model.AppPreferences
import net.igng.mcstatus.core.model.IgngFormatters

private class WikiArticleUiHolder(initialPageId: Int) {
    var activePageId by mutableStateOf(initialPageId)
    var expandedFolderIds by mutableStateOf<Set<Int>>(emptySet())
    var restoredInitialScroll by mutableStateOf(false)
}

/**
 * Dedicated full-screen mobile Wiki / Tutorial reader (Layer 2 of the two-layer Wiki architecture).
 * - Hides the bottom main dock so content fills the viewport and participates cleanly in predictive back
 * - Single-row quiet top bar whose title & current section fade in only after scrolling past the hero title
 * - Hairline reading progress line + right-edge scrubbing scrollbar
 * - Auto-hiding thumb-zone floating reader capsule (Directory / TOC / Typography / Bookmark)
 * - Full-width bottom sheets replace left edge-conflict drawers
 * - Parallel article switching (Directory / Search / Prev-Next) replaces in-place;
 *   in-article Wiki links push onto the navigation stack via [onOpenLinkedArticle] so predictive back
 *   returns directly to the exact reading position in the source tutorial.
 */
@Composable
fun WikiArticleScreen(
    pageId: Int,
    resumeProgress: Boolean = false,
    wikiRepository: WikiRepository,
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    onOpenLinkedArticle: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberIgngHaptics()
    val listState = rememberLazyListState()
    val holder = rememberScreenHolder("wiki_article_v2_$pageId") { WikiArticleUiHolder(pageId) }

    val prefs by settingsRepository.preferences.collectAsState(initial = AppPreferences())
    val recentIds by settingsRepository.recentWikiIds.collectAsState(initial = emptyList())
    val bookmarkedIds by settingsRepository.bookmarkedWikiIds.collectAsState(initial = emptyList())
    val progressMap by settingsRepository.wikiReadingProgress.collectAsState(initial = emptyMap())

    var showDirectorySheet by remember { mutableStateOf(false) }
    var showTocSheet by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var showTypoSheet by remember { mutableStateOf(false) }
    var lightboxIndex by remember { mutableStateOf<Int?>(null) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(1800L)
            toastMessage = null
        }
    }

    val (catalogState, _) = rememberSwrResource<WikiCatalogResponse>(
        cache = wikiRepository.swrCache,
        cacheKey = McCacheKeys.wikiCatalog(),
        scope = IgngCacheScope.PUBLIC,
        policy = McCachePolicies.Static,
        tags = setOf(McCacheTags.WIKI),
        diskCodec = McDiskCodecs.wikiCatalog(),
        legacyRawProvider = {
            wikiRepository.readCachedCatalog()?.let { McDiskCodecs.wikiCatalog().encode(it) }
        },
        slotKey = "wiki_catalog_reader",
    ) {
        runSuspendCatching { wikiRepository.fetchCatalog() }
    }
    val catalogIndex = remember(catalogState.data) { catalogState.data?.let(::WikiCatalogIndex) }

    val activePageId = holder.activePageId
    val isBookmarked = activePageId in bookmarkedIds

    // Keep ancestor folders expanded and record reading history whenever active page changes
    LaunchedEffect(activePageId, catalogIndex) {
        settingsRepository.recordRecentWiki(activePageId)
        val idx = catalogIndex ?: return@LaunchedEffect
        val ancestors = idx.ancestorFoldersOf(activePageId)
        if (holder.expandedFolderIds.isEmpty()) {
            val topExpanded = idx.firstTopFolderIdOf(activePageId)?.let { setOf(it) }
                ?: idx.tree.filter { it.isFolder }.take(2).map { it.id }.toSet()
            holder.expandedFolderIds = topExpanded + ancestors
        } else if (ancestors.isNotEmpty()) {
            holder.expandedFolderIds = holder.expandedFolderIds + ancestors
        }
    }

    val (pageState, pageHandle) = rememberSwrResource<WikiPageDetail>(
        cache = wikiRepository.swrCache,
        cacheKey = McCacheKeys.wikiPage(activePageId),
        scope = IgngCacheScope.PUBLIC,
        policy = McCachePolicies.WikiArticle,
        tags = setOf(McCacheTags.WIKI),
        diskCodec = McDiskCodecs.wikiPage(activePageId),
        legacyRawProvider = {
            wikiRepository.readCachedPage(activePageId)?.let { McDiskCodecs.wikiPage(activePageId).encode(it) }
        },
        slotKey = "wiki_article_detail_$activePageId",
    ) {
        runSuspendCatching { wikiRepository.fetchPage(activePageId) }
    }

    val page = pageState.data
    val document = remember(page?.content) {
        MarkdownParser.parse(source = page?.content.orEmpty())
    }
    val readingMinutes = remember(page?.content) {
        estimateWikiReadingMinutes(page?.content.orEmpty())
    }

    // Avoid duplicate H1 when Markdown body begins with the same title
    val skipLeadingH1 = remember(document.blocks, page?.title) {
        val first = document.blocks.firstOrNull() as? MarkdownBlock.Heading
        val title = page?.title?.trim().orEmpty()
        first != null && first.level == 1 && title.isNotBlank() && first.plainText.trim().equals(title, ignoreCase = true)
    }
    val visibleBlocks = remember(document.blocks, skipLeadingH1) {
        if (skipLeadingH1) document.blocks.drop(1) else document.blocks
    }
    val visibleToc = remember(document.toc, skipLeadingH1) {
        if (skipLeadingH1) {
            document.toc.mapNotNull { entry ->
                if (entry.blockIndex == 0) null else entry.copy(blockIndex = entry.blockIndex - 1)
            }
        } else {
            document.toc
        }
    }

    // Index 0 = editorial header, 1..N = markdown blocks
    val blocksStartIndex = 1
    val readingProgress by rememberIgngReadingProgress(listState)
    val barVisibility = rememberIgngScrollBarVisibility(hideThreshold = 32.dp, showThreshold = 20.dp)

    val showTopBarTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 140
        }
    }
    val activeTocIndex by remember(visibleToc) {
        derivedStateOf {
            val firstBlockIdx = (listState.firstVisibleItemIndex - blocksStartIndex).coerceAtLeast(0)
            findActiveTocIndex(visibleToc, firstBlockIdx)
        }
    }
    val activeTocEntry = visibleToc.getOrNull(activeTocIndex)

    // Ensure floating bar reappears at top or bottom of the document
    LaunchedEffect(listState.firstVisibleItemIndex, readingProgress) {
        if (listState.firstVisibleItemIndex == 0 || readingProgress >= 0.96f) {
            barVisibility.forceShow()
        }
    }

    // Restore saved reading position once when launched from Continue Reading card
    LaunchedEffect(page?.id, visibleBlocks.size, resumeProgress, progressMap) {
        val loadedPage = page ?: return@LaunchedEffect
        if (!resumeProgress || holder.restoredInitialScroll || loadedPage.id != pageId) return@LaunchedEffect
        val saved = progressMap[pageId] ?: return@LaunchedEffect
        if (saved.firstVisibleItemIndex > 0 || saved.firstVisibleItemScrollOffset > 0) {
            holder.restoredInitialScroll = true
            val maxIdx = (blocksStartIndex + visibleBlocks.size).coerceAtLeast(0)
            listState.scrollToItem(
                index = saved.firstVisibleItemIndex.coerceIn(0, maxIdx),
                scrollOffset = saved.firstVisibleItemScrollOffset.coerceAtLeast(0),
            )
            if (saved.progressPercent > 0) {
                toastMessage = "已恢复至上次阅读位置（${saved.progressPercent}%）"
            }
        } else {
            holder.restoredInitialScroll = true
        }
    }

    // Debounced save of reading progress
    LaunchedEffect(activePageId, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, readingProgress) {
        if (page == null || page.id != activePageId) return@LaunchedEffect
        delay(600L)
        settingsRepository.saveWikiReadingProgress(
            pageId = activePageId,
            progressRatio = readingProgress,
            firstVisibleItemIndex = listState.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
        )
    }

    fun switchPageInPlace(targetPageId: Int) {
        if (targetPageId == holder.activePageId) {
            scope.launch { listState.animateScrollToItem(0) }
            return
        }
        holder.activePageId = targetPageId
        holder.restoredInitialScroll = true
        barVisibility.forceShow()
        haptic(IgngHapticKind.Tap)
        scope.launch { listState.scrollToItem(0) }
    }

    val currentEntry = remember(catalogIndex, activePageId) { catalogIndex?.entryOf(activePageId) }
    val breadcrumbs = remember(catalogIndex, activePageId) { catalogIndex?.breadcrumbsOf(activePageId).orEmpty() }
    val prevEntry = remember(catalogIndex, activePageId) { catalogIndex?.prevOf(activePageId) }
    val nextEntry = remember(catalogIndex, activePageId) { catalogIndex?.nextOf(activePageId) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    ProvideIgngReaderSurface(tone = prefs.reader.canvasTone) {
        val colors = IgngTheme.colors
        val intensity = IgngTheme.motionIntensity
        val titleAlpha by animateFloatAsState(
            targetValue = if (showTopBarTitle) 1f else 0f,
            animationSpec = IgngMotion.microTween(intensity),
            label = "wikiTopTitleAlpha",
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.canvas)
                .statusBarsPadding(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Single-row minimal Quiet Craft reader top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(
                        onClick = {
                            haptic(IgngHapticKind.Tap)
                            onBack()
                        },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .alpha(titleAlpha)
                            .padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Text(
                            text = page?.title?.ifBlank { currentEntry?.page?.title }
                                ?: currentEntry?.page?.title
                                ?: "百科文档",
                            style = IgngTheme.type.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (activeTocEntry != null) {
                            Text(
                                text = activeTocEntry.title,
                                style = IgngTheme.type.badge,
                                color = colors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    if (catalogIndex != null) {
                        IconButton(
                            onClick = {
                                haptic(IgngHapticKind.Tap)
                                showSearchSheet = true
                            },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "搜索切换教程",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                    }
                }

                // Hairline divider with continuous 2dp reading progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .background(colors.line),
                ) {
                    val progressFraction = if (page != null) readingProgress.coerceIn(0f, 1f) else 0f
                    if (progressFraction > 0.005f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressFraction)
                                .background(colors.accent),
                        )
                    }
                }

                // Full-screen reading canvas
                IgngPullToRefreshBox(
                    isRefreshing = pageState.isUserRefreshing,
                    isRevalidating = pageState.isRevalidating,
                    onRefresh = { pageHandle.refresh() },
                    modifier = Modifier.weight(1f),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            pageState.isInitialLoading && page == null -> {
                                IgngLoadingBlock(fill = true)
                            }

                            pageState.error != null && page == null -> {
                                Box(Modifier.padding(16.dp)) {
                                    IgngStateBlock(
                                        title = "文档读取失败",
                                        description = pageState.error,
                                        isError = true,
                                        actionLabel = "重试",
                                        onAction = { pageHandle.refresh() },
                                    )
                                }
                            }

                            page != null -> {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .nestedScroll(barVisibility.nestedScrollConnection),
                                    contentPadding = PaddingValues(
                                        start = 20.dp,
                                        end = 20.dp,
                                        top = 16.dp,
                                        bottom = navBottom + 96.dp,
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    // Item 0: Scrollable Editorial Header (zero repetition with top bar)
                                    item(key = "wiki_header_${page.id}") {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 4.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            // Scrollable lightweight category trail
                                            val parentCrumbs = breadcrumbs.drop(1).dropLast(1)
                                            if (parentCrumbs.isNotEmpty() || catalogIndex != null) {
                                                val folderCrumb = parentCrumbs.lastOrNull()
                                                val trailLabel = parentCrumbs.joinToString(" / ") { it.label }
                                                    .ifBlank { catalogIndex?.kbName ?: "IGNGmc 百科" }
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                ) {
                                                    Text(
                                                        text = trailLabel,
                                                        style = IgngTheme.type.meta.copy(fontWeight = FontWeight.Medium),
                                                        color = colors.accent,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier
                                                            .clip(IgngTheme.radii.xs)
                                                            .clickable {
                                                                haptic(IgngHapticKind.Tap)
                                                                folderCrumb?.folderNodeId?.let { fid ->
                                                                    holder.expandedFolderIds = holder.expandedFolderIds + fid
                                                                }
                                                                showDirectorySheet = true
                                                            }
                                                            .padding(vertical = 2.dp),
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Rounded.ChevronRight,
                                                        contentDescription = null,
                                                        tint = colors.textMuted,
                                                        modifier = Modifier.size(14.dp),
                                                    )
                                                    Text(
                                                        text = page.title.ifBlank { currentEntry?.page?.title ?: "教程" },
                                                        style = IgngTheme.type.meta,
                                                        color = colors.textMuted,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }

                                            Text(
                                                text = page.title.ifBlank { currentEntry?.page?.title ?: "百科文档" },
                                                style = IgngTheme.type.pageTitle,
                                                color = colors.textPrimary,
                                            )

                                            val metaLine = buildList {
                                                add("约 $readingMinutes 分钟")
                                                if (visibleToc.isNotEmpty()) add("${visibleToc.size} 个小节")
                                                page.updatedAt?.let {
                                                    add("更新于 ${IgngFormatters.formatIsoDateTime(it, short = true)}")
                                                }
                                            }.joinToString(" · ")
                                            Text(
                                                text = metaLine,
                                                style = IgngTheme.type.meta,
                                                color = colors.textMuted,
                                            )

                                            Spacer(Modifier.height(2.dp))
                                            IgngHairlineDivider()
                                        }
                                    }

                                    // Items 1..N: Markdown Blocks with one-tap inline command copy
                                    itemsIndexed(
                                        items = visibleBlocks,
                                        key = { idx, _ -> "wiki_blk_${page.id}_$idx" },
                                    ) { _, block ->
                                        IgngMarkdownBlockView(
                                            block = block,
                                            typography = prefs.reader,
                                            onImageClick = { clickedUrl ->
                                                val idx = document.imageUrls.indexOf(clickedUrl).coerceAtLeast(0)
                                                if (document.imageUrls.isNotEmpty()) lightboxIndex = idx
                                            },
                                            onLinkClick = { url ->
                                                val internalTarget = catalogIndex?.resolveWikiLink(url, page.path)
                                                if (internalTarget != null) {
                                                    haptic(IgngHapticKind.Tap)
                                                    if (internalTarget == activePageId) {
                                                        scope.launch { listState.animateScrollToItem(0) }
                                                    } else {
                                                        onOpenLinkedArticle(internalTarget)
                                                    }
                                                } else {
                                                    IgngBrowserLauncher.openExternalUrl(context, url, colors.isDark)
                                                }
                                            },
                                            onInlineCodeClick = { code ->
                                                copyTextToClipboard(context, "IGNG Command", code)
                                                haptic(IgngHapticKind.Confirm)
                                                val preview = if (code.length > 26) "${code.take(26)}…" else code
                                                toastMessage = "已复制：$preview"
                                            },
                                        )
                                    }

                                    // Footer: Quiet Craft full-width grouped Prev / Next card + calm web footnote
                                    item(key = "wiki_footer_${page.id}") {
                                        WikiArticleFooter(
                                            prev = prevEntry,
                                            next = nextEntry,
                                            updatedAt = page.updatedAt,
                                            onSelectPage = { targetId -> switchPageInPlace(targetId) },
                                            onOpenWebOriginal = {
                                                val webUrl = catalogIndex?.publicWebUrlOf(page.id) ?: "https://docs.igng.net/IGNGmc"
                                                IgngBrowserLauncher.openExternalUrl(context, webUrl, colors.isDark)
                                            },
                                        )
                                    }
                                }

                                // Right-edge scrubbing scrollbar matching BBS PostReaderScreen
                                IgngLazyColumnScrollbar(
                                    listState = listState,
                                    modifier = Modifier.align(Alignment.CenterEnd),
                                    topPadding = 10.dp,
                                    bottomPadding = navBottom + 84.dp,
                                )
                            }
                        }

                        // Auto-hiding thumb-zone floating reader capsule
                        if (page != null) {
                            WikiFloatingReaderBar(
                                visible = barVisibility.visible,
                                hasDirectory = catalogIndex != null,
                                hasToc = visibleToc.isNotEmpty(),
                                isBookmarked = isBookmarked,
                                onOpenDirectory = {
                                    haptic(IgngHapticKind.Tap)
                                    showDirectorySheet = true
                                },
                                onOpenToc = {
                                    haptic(IgngHapticKind.Tap)
                                    showTocSheet = true
                                },
                                onOpenTypography = {
                                    haptic(IgngHapticKind.Tap)
                                    showTypoSheet = true
                                },
                                onToggleBookmark = {
                                    scope.launch {
                                        val added = settingsRepository.toggleWikiBookmark(activePageId)
                                        haptic(if (added) IgngHapticKind.Confirm else IgngHapticKind.Tick)
                                        toastMessage = if (added) "已加入常用收藏" else "已取消收藏"
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = navBottom + 14.dp),
                            )
                        }
                    }
                }
            }

            // Calm top confirmation toast (never collides with floating bottom reader bar)
            AnimatedVisibility(
                visible = toastMessage != null,
                enter = fadeIn(IgngMotion.microTween(intensity)) + slideInVertically(IgngMotion.microTween(intensity)) { -it / 2 },
                exit = fadeOut(IgngMotion.microTween(intensity)) + slideOutVertically(IgngMotion.microTween(intensity)) { -it / 2 },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 62.dp, start = 20.dp, end = 20.dp),
            ) {
                Surface(
                    shape = IgngTheme.radii.pill,
                    color = colors.textPrimary,
                    shadowElevation = 4.dp,
                ) {
                    Text(
                        text = toastMessage.orEmpty(),
                        style = IgngTheme.type.caption.copy(fontWeight = FontWeight.Medium),
                        color = colors.canvas,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }

        // Full-width Bottom Directory Sheet (replaces left edge-conflict drawer)
        if (showDirectorySheet && catalogIndex != null) {
            WikiDirectorySheet(
                catalogIndex = catalogIndex,
                activePageId = activePageId,
                bookmarkedIds = bookmarkedIds,
                expandedFolderIds = holder.expandedFolderIds,
                onToggleFolder = { fid ->
                    holder.expandedFolderIds = if (fid in holder.expandedFolderIds) {
                        holder.expandedFolderIds - fid
                    } else {
                        holder.expandedFolderIds + fid
                    }
                },
                onSelectPage = { targetId ->
                    showDirectorySheet = false
                    switchPageInPlace(targetId)
                },
                onDismiss = { showDirectorySheet = false },
            )
        }

        // Active-Section Highlighted On-Page TOC Sheet
        if (showTocSheet) {
            WikiTocSheet(
                toc = visibleToc,
                activeTocIndex = activeTocIndex,
                onJumpTop = {
                    showTocSheet = false
                    scope.launch { listState.animateScrollToItem(0) }
                },
                onJumpSection = { entry ->
                    showTocSheet = false
                    scope.launch { listState.animateScrollToItem(entry.blockIndex + blocksStartIndex) }
                },
                onDismiss = { showTocSheet = false },
            )
        }

        // Quick Search Sheet
        if (showSearchSheet && catalogIndex != null) {
            WikiQuickSearchSheet(
                catalogIndex = catalogIndex,
                recentIds = recentIds,
                activePageId = activePageId,
                onSelectPage = { targetId ->
                    showSearchSheet = false
                    switchPageInPlace(targetId)
                },
                onDismiss = { showSearchSheet = false },
            )
        }

        // Shared Reader Typography Sheet
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

@Composable
private fun WikiFloatingReaderBar(
    visible: Boolean,
    hasDirectory: Boolean,
    hasToc: Boolean,
    isBookmarked: Boolean,
    onOpenDirectory: () -> Unit,
    onOpenToc: () -> Unit,
    onOpenTypography: () -> Unit,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IgngTheme.colors
    val intensity = IgngTheme.motionIntensity
    val bookmarkScale by animateFloatAsState(
        targetValue = if (isBookmarked) 1.12f else 1f,
        animationSpec = IgngMotion.quickSpring(intensity),
        label = "wikiBookmarkScale",
    )

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(IgngMotion.standardSpring(intensity)) { it } + fadeIn(IgngMotion.microTween(intensity)),
        exit = slideOutVertically(IgngMotion.normalTween(intensity)) { it } + fadeOut(IgngMotion.microTween(intensity)),
        modifier = modifier,
    ) {
        val shape = RoundedCornerShape(IgngRadius.pill)
        Surface(
            shape = shape,
            color = colors.glassBar,
            border = BorderStroke(IgngStroke.hairline, colors.lineStrong),
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (hasDirectory) {
                    FloatingBarAction(
                        icon = Icons.AutoMirrored.Rounded.MenuBook,
                        label = "目录",
                        tint = colors.textPrimary,
                        onClick = onOpenDirectory,
                    )
                }
                if (hasToc) {
                    FloatingBarAction(
                        icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
                        label = "大纲",
                        tint = colors.textPrimary,
                        onClick = onOpenToc,
                    )
                }
                FloatingBarAction(
                    icon = Icons.Rounded.TextFields,
                    label = "排版",
                    tint = colors.textPrimary,
                    onClick = onOpenTypography,
                )
                FloatingBarAction(
                    icon = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    label = if (isBookmarked) "已藏" else "收藏",
                    tint = if (isBookmarked) colors.warning else colors.textSecondary,
                    iconScale = bookmarkScale,
                    onClick = onToggleBookmark,
                )
            }
        }
    }
}

@Composable
private fun FloatingBarAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    iconScale: Float = 1f,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(RoundedCornerShape(IgngRadius.pill))
            .igngPressScale(haptic = false, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
        )
        Text(
            text = label,
            style = IgngTheme.type.meta.copy(fontWeight = FontWeight.Medium),
            color = tint,
            maxLines = 1,
        )
    }
}

@Composable
private fun WikiArticleFooter(
    prev: WikiLinearPage?,
    next: WikiLinearPage?,
    updatedAt: String?,
    onSelectPage: (Int) -> Unit,
    onOpenWebOriginal: () -> Unit,
) {
    val colors = IgngTheme.colors
    val haptic = rememberIgngHaptics()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (prev != null || next != null) {
            IgngGroupedSection {
                if (prev != null) {
                    WikiFooterNavRow(
                        eyebrow = "上一篇",
                        title = prev.page.title,
                        isNext = false,
                        onClick = { onSelectPage(prev.page.id) },
                    )
                }
                if (prev != null && next != null) {
                    IgngHairlineDivider(startIndent = 16.dp, endIndent = 16.dp)
                }
                if (next != null) {
                    WikiFooterNavRow(
                        eyebrow = "下一篇",
                        title = next.page.title,
                        isNext = true,
                        onClick = { onSelectPage(next.page.id) },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!updatedAt.isNullOrBlank()) {
                Text(
                    text = "最后更新 ${IgngFormatters.formatIsoDateTime(updatedAt, short = true)} · ",
                    style = IgngTheme.type.meta,
                    color = colors.textMuted,
                )
            }
            Text(
                text = "在浏览器查看原文",
                style = IgngTheme.type.meta.copy(textDecoration = TextDecoration.Underline),
                color = colors.textSecondary,
                modifier = Modifier
                    .clip(IgngTheme.radii.xs)
                    .clickable {
                        haptic(IgngHapticKind.Tap)
                        onOpenWebOriginal()
                    }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun WikiFooterNavRow(
    eyebrow: String,
    title: String,
    isNext: Boolean,
    onClick: () -> Unit,
) {
    val colors = IgngTheme.colors
    val haptic = rememberIgngHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (pressed) colors.tilePressed else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic(IgngHapticKind.Tap)
                    onClick()
                },
            )
            .defaultMinSize(minHeight = 60.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!isNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = eyebrow,
                style = IgngTheme.type.badge.copy(
                    fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = if (isNext) colors.accent else colors.textMuted,
            )
            Text(
                text = title,
                style = IgngTheme.type.bodyMedium.copy(
                    fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = if (isNext) colors.textPrimary else colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
