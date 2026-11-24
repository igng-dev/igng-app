package net.igng.mcstatus.feature.wiki

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.igng.core.data.IgngCacheScope
import net.igng.core.data.rememberScreenHolder
import net.igng.core.data.rememberSwrResource
import net.igng.core.data.runSuspendCatching
import net.igng.core.designsystem.IgngBadge
import net.igng.core.designsystem.IgngBadgeTone
import net.igng.core.designsystem.IgngBrowserLauncher
import net.igng.core.designsystem.IgngDetailSheet
import net.igng.core.designsystem.IgngHapticKind
import net.igng.core.designsystem.IgngImageGalleryLightbox
import net.igng.core.designsystem.IgngInteractiveTile
import net.igng.core.designsystem.IgngLoadingBlock
import net.igng.core.designsystem.IgngMotion
import net.igng.core.designsystem.IgngPullToRefreshBox
import net.igng.core.designsystem.IgngReaderTypographySheet
import net.igng.core.designsystem.IgngSearchField
import net.igng.core.designsystem.IgngSectionHeader
import net.igng.core.designsystem.IgngSkeletonRows
import net.igng.core.designsystem.IgngStateBlock
import net.igng.core.designsystem.IgngTheme
import net.igng.core.designsystem.ProvideIgngReaderSurface
import net.igng.core.designsystem.rememberIgngHaptics
import net.igng.core.markdown.IgngMarkdownBlockView
import net.igng.core.markdown.MarkdownBlock
import net.igng.core.markdown.MarkdownParser
import net.igng.core.markdown.TocEntry
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

private class WikiWorkspaceUiHolder {
    var activePageId by mutableStateOf<Int?>(null)
    var initialResolved by mutableStateOf(false)
    var historyStack by mutableStateOf<List<Int>>(emptyList())
    var expandedFolderIds by mutableStateOf<Set<Int>>(emptySet())
    var drawerSearch by mutableStateOf("")
}

/**
 * Primary Wiki tab entry (Plan A): opens directly into a classic mobile Wiki reader workspace
 * (KB Home / last read page) with a swipeable left directory tree drawer, breadcrumbs,
 * sticky on-page TOC with active-section tracking, in-app cross-link interception, and
 * VitePress/GitBook-style Prev / Next page navigation.
 */
@Composable
fun WikiCatalogScreen(
    wikiRepository: WikiRepository,
    settingsRepository: SettingsRepository,
    bottomPadding: PaddingValues,
    onOpenArticle: (Int) -> Unit,
) {
    val holder = rememberScreenHolder("wiki_workspace_tab") { WikiWorkspaceUiHolder() }
    WikiWorkspaceCore(
        holder = holder,
        initialExplicitPageId = null,
        showBackNavigation = false,
        wikiRepository = wikiRepository,
        settingsRepository = settingsRepository,
        bottomPadding = bottomPadding,
        onExit = {},
    )
}

/**
 * Deep-link / standalone article route reusing the exact same Wiki reader workspace so users
 * retain sidebar tree navigation, breadcrumbs, prev/next flipping, and in-app wiki links.
 */
@Composable
fun WikiArticleScreen(
    pageId: Int,
    wikiRepository: WikiRepository,
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
) {
    val holder = rememberScreenHolder("wiki_article_ws_$pageId") {
        WikiWorkspaceUiHolder().apply {
            activePageId = pageId
            initialResolved = true
        }
    }
    WikiWorkspaceCore(
        holder = holder,
        initialExplicitPageId = pageId,
        showBackNavigation = true,
        wikiRepository = wikiRepository,
        settingsRepository = settingsRepository,
        bottomPadding = PaddingValues(0.dp),
        onExit = onBack,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WikiWorkspaceCore(
    holder: WikiWorkspaceUiHolder,
    initialExplicitPageId: Int?,
    showBackNavigation: Boolean,
    wikiRepository: WikiRepository,
    settingsRepository: SettingsRepository,
    bottomPadding: PaddingValues,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberIgngHaptics()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val listState = rememberLazyListState()

    val prefs by settingsRepository.preferences.collectAsState(initial = AppPreferences())
    val recentIds by settingsRepository.recentWikiIds.collectAsState(initial = emptyList())

    var showTocSheet by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var showTypoSheet by remember { mutableStateOf(false) }
    var lightboxIndex by remember { mutableStateOf<Int?>(null) }

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
    val catalogIndex = remember(catalog) { catalog?.let(::WikiCatalogIndex) }

    // Resolve initial landing page once catalog loads: explicit route -> last read -> KB home root
    LaunchedEffect(catalogIndex, recentIds, initialExplicitPageId) {
        val index = catalogIndex ?: return@LaunchedEffect
        if (!holder.initialResolved || holder.activePageId == null) {
            val preferred = initialExplicitPageId
                ?: recentIds.firstOrNull { index.entryOf(it) != null }
                ?: index.defaultPageId
            if (preferred != null) {
                holder.activePageId = preferred
                holder.initialResolved = true
            }
        }
        // Auto-expand top-level folders initially if nothing expanded yet, plus active page's ancestors
        val activeId = holder.activePageId
        val activeAncestors = activeId?.let { index.ancestorFoldersOf(it) }.orEmpty()
        if (holder.expandedFolderIds.isEmpty()) {
            val defaultExpanded = index.tree.filter { it.isFolder }.map { it.id }.toSet()
            holder.expandedFolderIds = defaultExpanded + activeAncestors
        } else if (activeAncestors.isNotEmpty()) {
            holder.expandedFolderIds = holder.expandedFolderIds + activeAncestors
        }
    }

    val activePageId = holder.activePageId

    // Expand ancestor folders and record reading history whenever active page changes
    LaunchedEffect(activePageId, catalogIndex) {
        val pid = activePageId ?: return@LaunchedEffect
        settingsRepository.recordRecentWiki(pid)
        catalogIndex?.ancestorFoldersOf(pid)?.let { ancestors ->
            if (ancestors.isNotEmpty()) {
                holder.expandedFolderIds = holder.expandedFolderIds + ancestors
            }
        }
    }

    fun navigateToPage(targetPageId: Int, pushHistory: Boolean = true) {
        val prev = holder.activePageId
        if (prev == targetPageId) {
            scope.launch {
                drawerState.close()
                listState.animateScrollToItem(0)
            }
            return
        }
        if (pushHistory && prev != null) {
            holder.historyStack = (holder.historyStack + prev).takeLast(32)
        }
        holder.activePageId = targetPageId
        haptic(IgngHapticKind.Tap)
        scope.launch {
            drawerState.close()
            listState.scrollToItem(0)
        }
    }

    fun popWikiHistoryOrExit() {
        val prev = holder.historyStack.lastOrNull()
        if (prev != null) {
            holder.historyStack = holder.historyStack.dropLast(1)
            holder.activePageId = prev
            scope.launch { listState.scrollToItem(0) }
        } else if (showBackNavigation) {
            onExit()
        }
    }

    // Intercept system back to close drawer first, then walk back through in-Wiki page history
    BackHandler(enabled = drawerState.isOpen || holder.historyStack.isNotEmpty()) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            popWikiHistoryOrExit()
        }
    }

    val (pageState, pageHandle) = rememberSwrResource<WikiPageDetail>(
        cache = wikiRepository.swrCache,
        cacheKey = McCacheKeys.wikiPage(activePageId ?: 0),
        scope = IgngCacheScope.PUBLIC,
        policy = McCachePolicies.WikiArticle,
        tags = setOf(McCacheTags.WIKI),
        diskCodec = McDiskCodecs.wikiPage(activePageId ?: 0),
        legacyRawProvider = {
            activePageId?.let { id ->
                wikiRepository.readCachedPage(id)?.let { McDiskCodecs.wikiPage(id).encode(it) }
            }
        },
        enabled = activePageId != null && activePageId > 0,
        slotKey = "wiki_ws_page_${activePageId ?: 0}",
    ) {
        val pid = activePageId ?: return@rememberSwrResource Result.failure(IllegalStateException("缺少文档编号"))
        runSuspendCatching { wikiRepository.fetchPage(pid) }
    }

    val page = pageState.data
    val document = remember(page?.content) {
        MarkdownParser.parse(source = page?.content.orEmpty())
    }
    val readingMinutes = remember(page?.content) {
        estimateWikiReadingMinutes(page?.content.orEmpty())
    }

    // Avoid duplicate H1 when the Markdown body already starts with the exact page title
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

    // Header item sits at LazyColumn index 0, markdown blocks start at index 1
    val blocksStartIndex = 1
    val activeTocIndex by remember(visibleToc) {
        derivedStateOf {
            val firstBlockIdx = (listState.firstVisibleItemIndex - blocksStartIndex).coerceAtLeast(0)
            findActiveTocIndex(visibleToc, firstBlockIdx)
        }
    }
    val activeTocEntry = visibleToc.getOrNull(activeTocIndex)

    val currentEntry = remember(catalogIndex, activePageId) {
        activePageId?.let { catalogIndex?.entryOf(it) }
    }
    val breadcrumbs = remember(catalogIndex, activePageId) {
        activePageId?.let { catalogIndex?.breadcrumbsOf(it) }.orEmpty()
    }
    val prevEntry = remember(catalogIndex, activePageId) {
        activePageId?.let { catalogIndex?.prevOf(it) }
    }
    val nextEntry = remember(catalogIndex, activePageId) {
        activePageId?.let { catalogIndex?.nextOf(it) }
    }
    val isRootHome = catalogIndex?.rootPage != null && activePageId == catalogIndex.rootPage.id
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val effectiveBottomPadding = maxOf(bottomPadding.calculateBottomPadding(), navBottom) + 28.dp

    ProvideIgngReaderSurface(tone = prefs.reader.canvasTone) {
        val colors = IgngTheme.colors

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = true,
            drawerContent = {
                WikiSidebarDrawerSheet(
                    catalogIndex = catalogIndex,
                    activePageId = activePageId,
                    expandedFolderIds = holder.expandedFolderIds,
                    recentIds = recentIds,
                    searchQuery = holder.drawerSearch,
                    onSearchQueryChange = { holder.drawerSearch = it },
                    onToggleFolder = { folderId ->
                        holder.expandedFolderIds = if (folderId in holder.expandedFolderIds) {
                            holder.expandedFolderIds - folderId
                        } else {
                            holder.expandedFolderIds + folderId
                        }
                    },
                    onSelectPage = { targetId -> navigateToPage(targetId) },
                    onClose = { scope.launch { drawerState.close() } },
                )
            },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.canvas)
                    .statusBarsPadding(),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Compact Wiki Workspace Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.tile.copy(alpha = 0.94f))
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (showBackNavigation || holder.historyStack.isNotEmpty()) {
                            IconButton(onClick = { popWikiHistoryOrExit() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = if (holder.historyStack.isNotEmpty()) "返回上一篇" else "返回",
                                    tint = colors.textPrimary,
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                haptic(IgngHapticKind.Tap)
                                scope.launch { drawerState.open() }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Menu,
                                contentDescription = "打开百科目录",
                                tint = colors.textPrimary,
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                        ) {
                            Text(
                                text = page?.title?.takeIf { it.isNotBlank() }
                                    ?: currentEntry?.page?.title
                                    ?: catalogIndex?.kbName
                                    ?: "IGNGmc 百科",
                                style = IgngTheme.type.cardTitle.copy(fontSize = 15.5.sp),
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val sub = buildList {
                                currentEntry?.folderLabel?.takeIf { it.isNotBlank() && !isRootHome }?.let { add(it) }
                                if (page != null) add("约 $readingMinutes 分钟")
                                page?.updatedAt?.let { add(IgngFormatters.formatIsoDateTime(it, short = true)) }
                            }.joinToString(" · ")
                            if (sub.isNotBlank()) {
                                Text(
                                    text = sub,
                                    style = IgngTheme.type.badge,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                haptic(IgngHapticKind.Tap)
                                showSearchSheet = true
                            },
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = "搜索百科", tint = colors.textSecondary)
                        }
                        if (visibleToc.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    haptic(IgngHapticKind.Tap)
                                    showTocSheet = true
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.FormatListBulleted,
                                    contentDescription = "本页大纲",
                                    tint = colors.accent,
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                haptic(IgngHapticKind.Tap)
                                showTypoSheet = true
                            },
                        ) {
                            Icon(Icons.Rounded.TextFields, contentDescription = "阅读排版", tint = colors.textSecondary)
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = colors.line)

                    // Sticky Breadcrumbs + Active On-Page Section Strip
                    if (breadcrumbs.isNotEmpty() || activeTocEntry != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.tileSoft.copy(alpha = 0.72f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                breadcrumbs.forEachIndexed { idx, crumb ->
                                    val isLast = idx == breadcrumbs.lastIndex
                                    if (idx > 0) {
                                        Icon(
                                            imageVector = Icons.Rounded.ChevronRight,
                                            contentDescription = null,
                                            tint = colors.textMuted,
                                            modifier = Modifier.size(13.dp),
                                        )
                                    }
                                    Text(
                                        text = crumb.label,
                                        style = IgngTheme.type.badge.copy(
                                            fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                                        ),
                                        color = if (isLast) colors.textPrimary else colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .then(
                                                if (!isLast) {
                                                    Modifier.clickable {
                                                        when {
                                                            crumb.pageId != null -> navigateToPage(crumb.pageId)
                                                            crumb.folderNodeId != null -> {
                                                                holder.expandedFolderIds = holder.expandedFolderIds + crumb.folderNodeId
                                                                scope.launch { drawerState.open() }
                                                            }
                                                            else -> scope.launch { drawerState.open() }
                                                        }
                                                    }
                                                } else {
                                                    Modifier
                                                },
                                            )
                                            .padding(horizontal = 3.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            if (activeTocEntry != null) {
                                Row(
                                    modifier = Modifier
                                        .clip(IgngTheme.radii.pill)
                                        .background(colors.tile)
                                        .border(0.6.dp, colors.line, IgngTheme.radii.pill)
                                        .clickable {
                                            haptic(IgngHapticKind.Tap)
                                            showTocSheet = true
                                        }
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.FormatListBulleted,
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Text(
                                        text = activeTocEntry.title,
                                        style = IgngTheme.type.badge.copy(fontSize = 10.sp),
                                        color = colors.accent,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.width(88.dp),
                                    )
                                }
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = colors.line)
                    }

                    // Main Reader Content
                    IgngPullToRefreshBox(
                        isRefreshing = catalogState.isUserRefreshing || pageState.isUserRefreshing,
                        onRefresh = {
                            catalogHandle.refresh()
                            if (activePageId != null) pageHandle.refresh()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        when {
                            catalogState.isInitialLoading && catalog == null ->
                                Box(Modifier.padding(16.dp)) { IgngSkeletonRows(5) }

                            catalogState.error != null && catalog == null ->
                                Box(Modifier.padding(16.dp)) {
                                    IgngStateBlock(
                                        title = "百科目录没有读到",
                                        description = catalogState.error,
                                        isError = true,
                                        actionLabel = "重试",
                                        onAction = { catalogHandle.refresh() },
                                    )
                                }

                            pageState.isInitialLoading && page == null ->
                                IgngLoadingBlock(fill = true)

                            pageState.error != null && page == null ->
                                Box(Modifier.padding(16.dp)) {
                                    IgngStateBlock(
                                        title = "文档读取失败",
                                        description = pageState.error,
                                        isError = true,
                                        actionLabel = "重试",
                                        onAction = { pageHandle.refresh() },
                                    )
                                }

                            page != null -> LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(
                                    start = 18.dp,
                                    end = 18.dp,
                                    top = 14.dp,
                                    bottom = effectiveBottomPadding,
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                // Index 0: Wiki Article Header Hero
                                item(key = "wiki_article_header_${page.id}") {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            IgngBadge(
                                                text = if (isRootHome) "百科首页" else (currentEntry?.folderLabel?.ifBlank { "文档" } ?: "文档"),
                                                tone = IgngBadgeTone.Accent,
                                            )
                                            if (visibleToc.isNotEmpty()) {
                                                Text(
                                                    text = "${visibleToc.size} 个小节 · 约 $readingMinutes 分钟",
                                                    style = IgngTheme.type.badge,
                                                    color = colors.textMuted,
                                                )
                                            }
                                        }
                                        Text(
                                            text = page.title.ifBlank { currentEntry?.page?.title ?: "百科文档" },
                                            style = IgngTheme.type.pageTitle,
                                            color = colors.textPrimary,
                                        )
                                        page.updatedAt?.let { updated ->
                                            Text(
                                                text = "版本 v${page.currentVersion} · 最后更新 ${IgngFormatters.formatIsoDateTime(updated, short = false)}",
                                                style = IgngTheme.type.badge,
                                                color = colors.textMuted,
                                            )
                                        }
                                        HorizontalDivider(color = colors.line)
                                    }
                                }

                                // Index 1..N: Parsed Markdown Document Blocks
                                itemsIndexed(
                                    items = visibleBlocks,
                                    key = { idx, _ -> "blk_${page.id}_$idx" },
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
                                                navigateToPage(internalTarget)
                                            } else {
                                                IgngBrowserLauncher.openExternalUrl(context, url, colors.isDark)
                                            }
                                        },
                                    )
                                }

                                // On KB Home, append a clean Section Directory Grid so new visitors see the full map
                                if (isRootHome && catalogIndex != null && catalogIndex.tree.isNotEmpty()) {
                                    item(key = "wiki_home_directory_map") {
                                        WikiHomeDirectorySection(
                                            tree = catalogIndex.tree,
                                            onSelectPage = { targetId -> navigateToPage(targetId) },
                                            onOpenDrawerForFolder = { folderId ->
                                                holder.expandedFolderIds = holder.expandedFolderIds + folderId
                                                scope.launch { drawerState.open() }
                                            },
                                        )
                                    }
                                }

                                // Standard Wiki Footer: Prev / Next Page Cards + Quick Actions
                                item(key = "wiki_footer_nav_${page.id}") {
                                    WikiFooterNavigation(
                                        prev = prevEntry,
                                        next = nextEntry,
                                        onSelectPage = { targetId -> navigateToPage(targetId) },
                                        onOpenDrawer = { scope.launch { drawerState.open() } },
                                        onScrollTop = { scope.launch { listState.animateScrollToItem(0) } },
                                    )
                                }
                            }

                            else -> Box(Modifier.padding(16.dp)) {
                                IgngStateBlock(
                                    title = "百科内容准备中",
                                    description = "点击左上角目录图标浏览知识库文档。",
                                    actionLabel = "打开目录",
                                    onAction = { scope.launch { drawerState.open() } },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Upgraded On-Page TOC Sheet with Active-Section Highlight
        if (showTocSheet) {
            IgngDetailSheet(
                onDismiss = { showTocSheet = false },
                title = "本页大纲",
                subtitle = "共 ${visibleToc.size} 个章节 · 点击跳转",
                scrollable = true,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(IgngTheme.radii.sm)
                        .background(colors.tileSoft)
                        .clickable {
                            haptic(IgngHapticKind.Tap)
                            showTocSheet = false
                            scope.launch { listState.animateScrollToItem(0) }
                        }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.ArrowUpward, null, tint = colors.accent, modifier = Modifier.size(15.dp))
                    Text("回到文档顶部", style = IgngTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.accent)
                }
                Spacer(Modifier.height(6.dp))
                visibleToc.forEachIndexed { idx, entry: TocEntry ->
                    val isActive = idx == activeTocIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(IgngTheme.radii.sm)
                            .background(if (isActive) colors.accentSoft else Color.Transparent)
                            .clickable {
                                haptic(IgngHapticKind.Tap)
                                showTocSheet = false
                                scope.launch { listState.animateScrollToItem(entry.blockIndex + blocksStartIndex) }
                            }
                            .padding(
                                start = (10 + (entry.level - 1).coerceAtLeast(0) * 14).dp,
                                top = 9.dp,
                                bottom = 9.dp,
                                end = 10.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(16.dp)
                                .clip(IgngTheme.radii.pill)
                                .background(if (isActive) colors.accent else colors.line),
                        )
                        Text(
                            text = entry.title,
                            style = if (entry.level <= 2 || isActive) {
                                IgngTheme.type.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            } else {
                                IgngTheme.type.body
                            },
                            color = if (isActive) colors.accent else colors.textPrimary,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isActive) {
                            Text("当前", style = IgngTheme.type.badge, color = colors.accent)
                        }
                    }
                }
            }
        }

        // Full-Library Quick Search Sheet
        if (showSearchSheet && catalogIndex != null) {
            WikiQuickSearchSheet(
                catalogIndex = catalogIndex,
                recentIds = recentIds,
                activePageId = activePageId,
                onSelectPage = { targetId ->
                    showSearchSheet = false
                    navigateToPage(targetId)
                },
                onDismiss = { showSearchSheet = false },
            )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WikiSidebarDrawerSheet(
    catalogIndex: WikiCatalogIndex?,
    activePageId: Int?,
    expandedFolderIds: Set<Int>,
    recentIds: List<Int>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleFolder: (Int) -> Unit,
    onSelectPage: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val colors = IgngTheme.colors
    val recentEntries = remember(catalogIndex, recentIds) {
        if (catalogIndex == null) emptyList()
        else recentIds.mapNotNull { catalogIndex.entryOf(it) }.take(4)
    }
    val filteredEntries = remember(catalogIndex, searchQuery) {
        if (catalogIndex == null || searchQuery.isBlank()) emptyList()
        else catalogIndex.search(searchQuery)
    }

    ModalDrawerSheet(
        modifier = Modifier.width(308.dp),
        drawerContainerColor = colors.tile,
        drawerContentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        ) {
            // Sidebar Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(
                            text = catalogIndex?.kbName ?: "IGNGmc 百科",
                            style = IgngTheme.type.cardTitle,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (catalogIndex != null) {
                            Text(
                                text = "共 ${catalogIndex.totalPages} 篇文档",
                                style = IgngTheme.type.badge,
                                color = colors.textMuted,
                            )
                        }
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭目录", tint = colors.textSecondary)
                }
            }

            IgngSearchField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = "筛选文档标题或分类…",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(thickness = 0.5.dp, color = colors.line)

            if (catalogIndex == null) {
                Box(Modifier.fillMaxWidth().padding(16.dp)) { IgngSkeletonRows(4) }
            } else if (searchQuery.isNotBlank()) {
                if (filteredEntries.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(16.dp)) {
                        IgngStateBlock(title = "未匹配到文档", description = "换个关键词试试。")
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(filteredEntries, key = { "drawer_search_${it.page.id}" }) { entry ->
                            val isActive = entry.page.id == activePageId
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(IgngTheme.radii.sm)
                                    .background(if (isActive) colors.accentSoft else Color.Transparent)
                                    .clickable { onSelectPage(entry.page.id) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = entry.page.title,
                                    style = IgngTheme.type.bodyMedium.copy(
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    ),
                                    color = if (isActive) colors.accent else colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (entry.folderLabel.isNotBlank()) {
                                    Text(
                                        text = entry.folderLabel,
                                        style = IgngTheme.type.badge,
                                        color = colors.textMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Pinned KB Home Item
                    catalogIndex.rootPage?.let { root ->
                        item(key = "drawer_root_home") {
                            val isActive = activePageId == root.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(IgngTheme.radii.sm)
                                    .background(if (isActive) colors.accentSoft else Color.Transparent)
                                    .clickable { onSelectPage(root.id) }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Home,
                                    contentDescription = null,
                                    tint = if (isActive) colors.accent else colors.textSecondary,
                                    modifier = Modifier.size(17.dp),
                                )
                                Text(
                                    text = "百科首页",
                                    style = IgngTheme.type.bodyMedium.copy(
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                                    ),
                                    color = if (isActive) colors.accent else colors.textPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    // Recent Reads Quick Strip
                    if (recentEntries.isNotEmpty()) {
                        item(key = "drawer_recent") {
                            Column(
                                modifier = Modifier.padding(top = 6.dp, bottom = 6.dp, start = 4.dp, end = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text("最近阅读", style = IgngTheme.type.badge, color = colors.textMuted)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    recentEntries.forEach { entry ->
                                        val isCurrent = entry.page.id == activePageId
                                        Text(
                                            text = entry.page.title,
                                            style = IgngTheme.type.badge.copy(
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            ),
                                            color = if (isCurrent) colors.accent else colors.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .clip(IgngTheme.radii.pill)
                                                .background(if (isCurrent) colors.accentSoft else colors.tileSoft)
                                                .border(0.6.dp, if (isCurrent) colors.accent.copy(alpha = 0.4f) else colors.line, IgngTheme.radii.pill)
                                                .clickable { onSelectPage(entry.page.id) }
                                                .padding(horizontal = 9.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = colors.line)
                        }
                    }

                    // Multi-level Collapsible Directory Tree
                    items(catalogIndex.tree, key = { "tree_root_${it.id}" }) { node ->
                        WikiTreeNodeBranch(
                            node = node,
                            activePageId = activePageId,
                            expandedFolderIds = expandedFolderIds,
                            onToggleFolder = onToggleFolder,
                            onSelectPage = onSelectPage,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WikiTreeNodeBranch(
    node: WikiTreeNode,
    activePageId: Int?,
    expandedFolderIds: Set<Int>,
    onToggleFolder: (Int) -> Unit,
    onSelectPage: (Int) -> Unit,
) {
    val colors = IgngTheme.colors
    val motion = IgngTheme.motionIntensity
    val indentDp = (node.depth * 14).dp

    if (node.isFolder) {
        val isExpanded = node.id in expandedFolderIds
        val containsActive = remember(node, activePageId) { nodeContainsPage(node, activePageId) }
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = indentDp)
                    .clip(IgngTheme.radii.sm)
                    .clickable { onToggleFolder(node.id) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
                    contentDescription = if (isExpanded) "收起分类" else "展开分类",
                    tint = colors.textMuted,
                    modifier = Modifier.size(16.dp),
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.FolderOpen else Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = if (containsActive) colors.accent else colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = node.title,
                    style = IgngTheme.type.bodyMedium.copy(
                        fontWeight = if (containsActive) FontWeight.Bold else FontWeight.SemiBold,
                    ),
                    color = if (containsActive) colors.textPrimary else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${node.pageCount}",
                    style = IgngTheme.type.badge.copy(fontSize = 10.sp),
                    color = colors.textMuted,
                    modifier = Modifier
                        .clip(IgngTheme.radii.pill)
                        .background(colors.tileSoft)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(IgngMotion.standardSpring(motion)) + fadeIn(IgngMotion.microTween(motion)),
                exit = shrinkVertically(IgngMotion.standardSpring(motion)) + fadeOut(IgngMotion.microTween(motion)),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    node.pageId?.let { folderPageId ->
                        WikiTreeLeafRow(
                            title = "${node.title}（概览）",
                            depth = node.depth + 1,
                            isActive = folderPageId == activePageId,
                            onClick = { onSelectPage(folderPageId) },
                        )
                    }
                    node.children.forEach { child ->
                        WikiTreeNodeBranch(
                            node = child,
                            activePageId = activePageId,
                            expandedFolderIds = expandedFolderIds,
                            onToggleFolder = onToggleFolder,
                            onSelectPage = onSelectPage,
                        )
                    }
                }
            }
        }
    } else if (node.pageId != null) {
        WikiTreeLeafRow(
            title = node.title,
            depth = node.depth,
            isActive = node.pageId == activePageId,
            onClick = { onSelectPage(node.pageId) },
        )
    }
}

@Composable
private fun WikiTreeLeafRow(
    title: String,
    depth: Int,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val colors = IgngTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 14).dp)
            .clip(IgngTheme.radii.sm)
            .background(if (isActive) colors.accentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .height(16.dp)
                .clip(IgngTheme.radii.pill)
                .background(if (isActive) colors.accent else colors.line),
        )
        Icon(
            imageVector = Icons.Rounded.Description,
            contentDescription = null,
            tint = if (isActive) colors.accent else colors.textMuted,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = title,
            style = IgngTheme.type.caption.copy(
                fontSize = 13.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            ),
            color = if (isActive) colors.accent else colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun nodeContainsPage(node: WikiTreeNode, targetPageId: Int?): Boolean {
    if (targetPageId == null) return false
    if (node.pageId == targetPageId) return true
    return node.children.any { nodeContainsPage(it, targetPageId) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WikiHomeDirectorySection(
    tree: List<WikiTreeNode>,
    onSelectPage: (Int) -> Unit,
    onOpenDrawerForFolder: (Int) -> Unit,
) {
    val colors = IgngTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalDivider(color = colors.line)
        IgngSectionHeader(title = "知识库分类导览", subtitle = "点击文档直达，或从左上角拉开完整目录树")
        tree.forEach { node ->
            val previewPages = remember(node) { collectFirstLeafPages(node, limit = 4) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(IgngTheme.radii.md)
                    .background(colors.tile)
                    .border(0.8.dp, colors.line, IgngTheme.radii.md)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (node.isFolder) onOpenDrawerForFolder(node.id)
                            else node.pageId?.let(onSelectPage)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Icon(
                            imageVector = if (node.isFolder) Icons.Rounded.Folder else Icons.Rounded.Description,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(17.dp),
                        )
                        Text(
                            text = node.title,
                            style = IgngTheme.type.cardTitle.copy(fontSize = 14.5.sp),
                            color = colors.textPrimary,
                        )
                    }
                    Text(
                        text = "${node.pageCount} 篇 ›",
                        style = IgngTheme.type.badge,
                        color = colors.accent,
                    )
                }
                if (previewPages.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        previewPages.forEach { (pid, title) ->
                            Text(
                                text = title,
                                style = IgngTheme.type.caption,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(IgngTheme.radii.sm)
                                    .background(colors.tileSoft)
                                    .border(0.6.dp, colors.line, IgngTheme.radii.sm)
                                    .clickable { onSelectPage(pid) }
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun collectFirstLeafPages(node: WikiTreeNode, limit: Int): List<Pair<Int, String>> {
    val result = mutableListOf<Pair<Int, String>>()
    fun walk(n: WikiTreeNode) {
        if (result.size >= limit) return
        n.pageId?.let { pid -> result += pid to n.title }
        n.children.forEach { walk(it) }
    }
    walk(node)
    return result.take(limit)
}

@Composable
private fun WikiFooterNavigation(
    prev: WikiLinearPage?,
    next: WikiLinearPage?,
    onSelectPage: (Int) -> Unit,
    onOpenDrawer: () -> Unit,
    onScrollTop: () -> Unit,
) {
    val colors = IgngTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalDivider(color = colors.line)

        if (prev != null || next != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (prev != null) {
                    IgngInteractiveTile(
                        onClick = { onSelectPage(prev.page.id) },
                        contentPadding = PaddingValues(12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = colors.textMuted, modifier = Modifier.size(13.dp))
                            Text("上一篇", style = IgngTheme.type.badge, color = colors.textMuted)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = prev.page.title,
                            style = IgngTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }

                if (next != null) {
                    IgngInteractiveTile(
                        onClick = { onSelectPage(next.page.id) },
                        contentPadding = PaddingValues(12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("下一篇", style = IgngTheme.type.badge, color = colors.accent)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = colors.accent, modifier = Modifier.size(13.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = next.page.title,
                            style = IgngTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                            color = colors.accent,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clip(IgngTheme.radii.pill)
                    .background(colors.tileSoft)
                    .border(0.6.dp, colors.line, IgngTheme.radii.pill)
                    .clickable(onClick = onOpenDrawer)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Rounded.Menu, null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                Text("完整目录", style = IgngTheme.type.badge, color = colors.textSecondary)
            }
            Spacer(Modifier.width(10.dp))
            Row(
                modifier = Modifier
                    .clip(IgngTheme.radii.pill)
                    .background(colors.tileSoft)
                    .border(0.6.dp, colors.line, IgngTheme.radii.pill)
                    .clickable(onClick = onScrollTop)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Rounded.ArrowUpward, null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                Text("回到顶部", style = IgngTheme.type.badge, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun WikiQuickSearchSheet(
    catalogIndex: WikiCatalogIndex,
    recentIds: List<Int>,
    activePageId: Int?,
    onSelectPage: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = IgngTheme.colors
    var query by remember { mutableStateOf("") }
    val results = remember(catalogIndex, query) { catalogIndex.search(query) }
    val recentEntries = remember(catalogIndex, recentIds) {
        recentIds.mapNotNull { catalogIndex.entryOf(it) }.take(5)
    }

    IgngDetailSheet(
        onDismiss = onDismiss,
        title = "搜索百科文档",
        subtitle = "${catalogIndex.kbName} · 共 ${catalogIndex.totalPages} 篇",
    ) {
        IgngSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "输入文档名、插件名、分类或路径…",
        )
        Spacer(Modifier.height(10.dp))

        val listToShow = if (query.isBlank() && recentEntries.isNotEmpty()) recentEntries else results
        if (query.isBlank() && recentEntries.isNotEmpty()) {
            Text(
                text = "最近阅读",
                style = IgngTheme.type.badge,
                color = colors.textMuted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        } else if (query.isNotBlank()) {
            Text(
                text = "找到 ${results.size} 篇相关文档",
                style = IgngTheme.type.badge,
                color = colors.textMuted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        if (listToShow.isEmpty()) {
            IgngStateBlock(title = "没有找到相关词条", description = "换一个关键词试试。")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(listToShow, key = { "search_item_${it.page.id}" }) { entry ->
                    val isCurrent = entry.page.id == activePageId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(IgngTheme.radii.md)
                            .background(if (isCurrent) colors.accentSoft else colors.tileSoft)
                            .border(0.6.dp, if (isCurrent) colors.accent.copy(alpha = 0.4f) else colors.line, IgngTheme.radii.md)
                            .clickable { onSelectPage(entry.page.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = null,
                            tint = if (isCurrent) colors.accent else colors.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = entry.page.title,
                                style = IgngTheme.type.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isCurrent) colors.accent else colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (entry.folderLabel.isNotBlank()) {
                                Text(
                                    text = entry.folderLabel,
                                    style = IgngTheme.type.badge,
                                    color = colors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
