package net.igng.mcstatus.feature.wiki

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.igng.core.data.IgngCacheScope
import net.igng.core.data.rememberScreenHolder
import net.igng.core.data.rememberSwrResource
import net.igng.core.data.runSuspendCatching
import net.igng.core.designsystem.IgngAmbientCanvas
import net.igng.core.designsystem.IgngBadge
import net.igng.core.designsystem.IgngBadgeTone
import net.igng.core.designsystem.IgngGroupedSection
import net.igng.core.designsystem.IgngHapticKind
import net.igng.core.designsystem.IgngListRow
import net.igng.core.designsystem.IgngMotion
import net.igng.core.designsystem.IgngPullToRefreshBox
import net.igng.core.designsystem.IgngSearchField
import net.igng.core.designsystem.IgngSectionHeader
import net.igng.core.designsystem.IgngSkeletonRows
import net.igng.core.designsystem.IgngStateBlock
import net.igng.core.designsystem.IgngStroke
import net.igng.core.designsystem.IgngTagChip
import net.igng.core.designsystem.IgngTheme
import net.igng.core.designsystem.rememberIgngHaptics
import net.igng.mcstatus.core.data.McCacheKeys
import net.igng.mcstatus.core.data.McCachePolicies
import net.igng.mcstatus.core.data.McCacheTags
import net.igng.mcstatus.core.data.McDiskCodecs
import net.igng.mcstatus.core.data.SettingsRepository
import net.igng.mcstatus.core.data.WikiCatalogResponse
import net.igng.mcstatus.core.data.WikiRepository
import net.igng.mcstatus.core.model.IgngFormatters
import net.igng.mcstatus.core.model.WikiReadingProgress

private class WikiCatalogHubUiHolder {
    var searchQuery by mutableStateOf("")
    var selectedSectionId by mutableStateOf<String?>(null)
}

/**
 * Primary mobile-native Wiki / Tutorial Catalog Hub (Layer 1 of the two-layer Wiki architecture).
 * Keeps the bottom dock visible and focuses on glanceable discovery: unified search, a calm
 * Continue Reading / Starter focus card, personal bookmarks, horizontal category filter pills,
 * and Quiet Craft hairline-grouped tutorial lists.
 */
@Composable
fun WikiCatalogScreen(
    wikiRepository: WikiRepository,
    settingsRepository: SettingsRepository,
    bottomPadding: PaddingValues,
    onOpenArticle: (pageId: Int, resumeProgress: Boolean) -> Unit,
) {
    val colors = IgngTheme.colors
    val haptic = rememberIgngHaptics()
    val holder = rememberScreenHolder("wiki_catalog_hub_v2") { WikiCatalogHubUiHolder() }

    val recentIds by settingsRepository.recentWikiIds.collectAsState(initial = emptyList())
    val bookmarkedIds by settingsRepository.bookmarkedWikiIds.collectAsState(initial = emptyList())
    val progressMap by settingsRepository.wikiReadingProgress.collectAsState(initial = emptyMap())

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

    // Clear stale section selection if catalog structure changes
    val effectiveSelectedSectionId = remember(catalogIndex, holder.selectedSectionId) {
        val current = holder.selectedSectionId
        if (current != null && catalogIndex?.sections?.any { it.id == current } == true) current else null
    }

    val lastReadEntry = remember(catalogIndex, recentIds) {
        if (catalogIndex == null) null
        else recentIds.firstNotNullOfOrNull { catalogIndex.entryOf(it) }
    }
    val bookmarkedEntries = remember(catalogIndex, bookmarkedIds) {
        if (catalogIndex == null) emptyList()
        else bookmarkedIds.mapNotNull { catalogIndex.entryOf(it) }
    }
    val otherRecentEntries = remember(catalogIndex, recentIds, lastReadEntry) {
        if (catalogIndex == null) emptyList()
        else recentIds
            .filter { it != lastReadEntry?.page?.id }
            .mapNotNull { catalogIndex.entryOf(it) }
            .take(4)
    }

    IgngAmbientCanvas {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // 1. Quiet Craft top header aligned with Explore & Chat tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = catalogIndex?.kbName ?: "IGNGmc 百科",
                        style = IgngTheme.type.pageTitle,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (catalogIndex != null) {
                            "共 ${catalogIndex.totalPages} 篇官方教程与玩法指南"
                        } else {
                            "官方教程与玩法指南"
                        },
                        style = IgngTheme.type.meta,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                catalogIndex?.rootPage?.let { root ->
                    Box(
                        modifier = Modifier
                            .clip(IgngTheme.radii.sm)
                            .background(colors.tile)
                            .border(IgngStroke.hairline, colors.lineStrong, IgngTheme.radii.sm)
                            .clickable {
                                haptic(IgngHapticKind.Tap)
                                onOpenArticle(root.id, false)
                            }
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = "新手起步",
                            style = IgngTheme.type.caption.copy(fontWeight = FontWeight.Medium),
                            color = colors.textPrimary,
                            maxLines = 1,
                        )
                    }
                }
            }

            // 2. Unified inline search field (zero modal search duplication)
            IgngSearchField(
                value = holder.searchQuery,
                onValueChange = { holder.searchQuery = it },
                placeholder = "搜索教程、指令、领地或插件说明…",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(10.dp))

            // 3. Main discovery body
            IgngPullToRefreshBox(
                isRefreshing = catalogState.isUserRefreshing,
                isRevalidating = catalogState.isRevalidating,
                onRefresh = { catalogHandle.refresh() },
                modifier = Modifier.weight(1f),
            ) {
                when {
                    catalogState.isInitialLoading && catalog == null -> {
                        Box(Modifier.padding(16.dp)) { IgngSkeletonRows(5) }
                    }

                    catalogState.error != null && catalog == null -> {
                        Box(Modifier.padding(16.dp)) {
                            IgngStateBlock(
                                title = "百科目录暂时不可用",
                                description = catalogState.error,
                                isError = true,
                                actionLabel = "重试",
                                onAction = { catalogHandle.refresh() },
                            )
                        }
                    }

                    catalogIndex != null -> {
                        val query = holder.searchQuery.trim()
                        val searchMatches = remember(catalogIndex, query) {
                            if (query.isEmpty()) emptyList() else catalogIndex.search(query)
                        }
                        val visibleSections = remember(catalogIndex, effectiveSelectedSectionId) {
                            if (effectiveSelectedSectionId == null) {
                                catalogIndex.sections
                            } else {
                                catalogIndex.sections.filter { it.id == effectiveSelectedSectionId }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = bottomPadding.calculateBottomPadding() + 24.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            if (query.isNotEmpty()) {
                                item(key = "wiki_search_results") {
                                    if (searchMatches.isEmpty()) {
                                        IgngStateBlock(
                                            title = "没有找到相关教程",
                                            description = "试试换个关键词，如插件名、指令或分类名称。",
                                        )
                                    } else {
                                        IgngGroupedSection(
                                            title = "搜索结果",
                                            trailing = {
                                                Text(
                                                    text = "${searchMatches.size} 篇",
                                                    style = IgngTheme.type.meta,
                                                    color = colors.textMuted,
                                                )
                                            },
                                        ) {
                                            searchMatches.forEachIndexed { idx, entry ->
                                                val sub = buildList {
                                                    entry.folderLabel.takeIf { it.isNotBlank() }?.let(::add)
                                                    entry.page.updatedAt?.let {
                                                        add("更新于 ${IgngFormatters.formatIsoDateTime(it, short = true)}")
                                                    }
                                                }.joinToString(" · ")
                                                IgngListRow(
                                                    title = entry.page.title,
                                                    subtitle = sub.ifBlank { null },
                                                    showDivider = idx < searchMatches.lastIndex,
                                                    onClick = { onOpenArticle(entry.page.id, false) },
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                // A. Continue Reading or First-Visit Starter Focus Card
                                val focusPage = lastReadEntry ?: catalogIndex.rootPage?.let { catalogIndex.entryOf(it.id) }
                                if (focusPage != null) {
                                    item(key = "wiki_focus_card") {
                                        val isResume = lastReadEntry != null
                                        val savedProgress = if (isResume) progressMap[focusPage.page.id] else null
                                        WikiFocusCard(
                                            entry = focusPage,
                                            isResume = isResume,
                                            progress = savedProgress,
                                            onClick = { onOpenArticle(focusPage.page.id, isResume) },
                                        )
                                    }
                                }

                                // B. Personal Bookmarks (shown only when user has saved tutorials & viewing All)
                                if (effectiveSelectedSectionId == null && bookmarkedEntries.isNotEmpty()) {
                                    item(key = "wiki_bookmarks") {
                                        IgngGroupedSection(
                                            title = "常用收藏",
                                            trailing = {
                                                Text(
                                                    text = "${bookmarkedEntries.size} 篇",
                                                    style = IgngTheme.type.meta,
                                                    color = colors.textMuted,
                                                )
                                            },
                                        ) {
                                            bookmarkedEntries.forEachIndexed { idx, entry ->
                                                IgngListRow(
                                                    title = entry.page.title,
                                                    subtitle = entry.folderLabel.takeIf { it.isNotBlank() },
                                                    icon = Icons.Rounded.Bookmark,
                                                    iconTint = colors.warning,
                                                    showDivider = idx < bookmarkedEntries.lastIndex,
                                                    onClick = { onOpenArticle(entry.page.id, false) },
                                                )
                                            }
                                        }
                                    }
                                }

                                // C. Recent Reads Horizontal Strip (excluding current focus card to avoid repetition)
                                if (effectiveSelectedSectionId == null && otherRecentEntries.isNotEmpty()) {
                                    item(key = "wiki_recent_strip") {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            IgngSectionHeader(
                                                title = "最近浏览",
                                                modifier = Modifier.padding(horizontal = 4.dp),
                                            )
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                otherRecentEntries.forEach { entry ->
                                                    IgngTagChip(
                                                        text = entry.page.title,
                                                        onClick = { onOpenArticle(entry.page.id, false) },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // D. Horizontal Category Filter Strip
                                if (catalogIndex.sections.size > 1) {
                                    item(key = "wiki_category_strip") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            WikiCategoryFilterPill(
                                                label = "全部",
                                                count = catalogIndex.sections.sumOf { it.items.size },
                                                selected = effectiveSelectedSectionId == null,
                                                onClick = { holder.selectedSectionId = null },
                                            )
                                            catalogIndex.sections.forEach { section ->
                                                WikiCategoryFilterPill(
                                                    label = section.title,
                                                    count = section.items.size,
                                                    selected = effectiveSelectedSectionId == section.id,
                                                    onClick = { holder.selectedSectionId = section.id },
                                                )
                                            }
                                        }
                                    }
                                }

                                // E. Grouped Tutorial Sections
                                if (visibleSections.isEmpty()) {
                                    item(key = "wiki_empty_sections") {
                                        IgngStateBlock(
                                            title = "暂无可浏览的教程",
                                            description = "下拉刷新获取最新知识库目录。",
                                            actionLabel = "刷新",
                                            onAction = { catalogHandle.refresh() },
                                        )
                                    }
                                } else {
                                    visibleSections.forEach { section ->
                                        item(key = "wiki_sec_${section.id}") {
                                            IgngGroupedSection(
                                                title = section.title,
                                                trailing = {
                                                    Text(
                                                        text = "${section.items.size} 篇",
                                                        style = IgngTheme.type.meta,
                                                        color = colors.textMuted,
                                                    )
                                                },
                                            ) {
                                                section.items.forEachIndexed { idx, row ->
                                                    val subtitle = buildList {
                                                        row.subGroupLabel?.takeIf { it.isNotBlank() }?.let(::add)
                                                        row.updatedAt?.let {
                                                            add("更新于 ${IgngFormatters.formatIsoDateTime(it, short = true)}")
                                                        }
                                                    }.joinToString(" · ")
                                                    IgngListRow(
                                                        title = row.title,
                                                        subtitle = subtitle.ifBlank { null },
                                                        trailing = if (row.isOverview) {
                                                            { IgngBadge(text = "概览", tone = IgngBadgeTone.Accent) }
                                                        } else {
                                                            null
                                                        },
                                                        showDivider = idx < section.items.lastIndex,
                                                        onClick = { onOpenArticle(row.pageId, false) },
                                                    )
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
        }
    }
}

@Composable
private fun WikiFocusCard(
    entry: WikiLinearPage,
    isResume: Boolean,
    progress: WikiReadingProgress?,
    onClick: () -> Unit,
) {
    val colors = IgngTheme.colors
    val intensity = IgngTheme.motionIntensity
    val haptic = rememberIgngHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val bg by animateColorAsState(
        targetValue = if (pressed) colors.tilePressed else colors.tile,
        animationSpec = IgngMotion.microTween(intensity),
        label = "wikiFocusCardBg",
    )

    val pct = progress?.progressPercent?.coerceIn(0, 100) ?: 0
    val showProgressBar = isResume && pct > 0
    val categoryHint = entry.folderLabel.takeIf { it.isNotBlank() && it != entry.page.title }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(IgngTheme.radii.lg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic(IgngHapticKind.Tap)
                    onClick()
                },
            ),
        shape = IgngTheme.radii.lg,
        color = bg,
        border = BorderStroke(IgngStroke.hairline, colors.line),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            // Calm 3dp Quiet Craft brand accent bar (consistent with Explore Hero card)
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(colors.accent),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = buildString {
                            append(if (isResume) "继续上次阅读" else "新手起步导览")
                            if (!categoryHint.isNullOrBlank()) {
                                append(" · ")
                                append(categoryHint)
                            }
                        },
                        style = IgngTheme.type.badge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (showProgressBar) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "已读 $pct%",
                            style = IgngTheme.type.meta,
                            color = colors.textMuted,
                            maxLines = 1,
                        )
                    }
                }

                Text(
                    text = entry.page.title,
                    style = IgngTheme.type.cardTitle,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (showProgressBar) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(IgngTheme.radii.pill)
                                .background(colors.line),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth((pct / 100f).coerceIn(0.04f, 1f))
                                    .clip(IgngTheme.radii.pill)
                                    .background(colors.accent),
                            )
                        }
                    } else {
                        Text(
                            text = if (isResume) "上次打开的教程文档" else "从官方总览快速了解服务器玩法",
                            style = IgngTheme.type.meta,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = if (isResume) "继续 ›" else "开始阅读 ›",
                        style = IgngTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.accent,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun WikiCategoryFilterPill(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = IgngTheme.colors
    val intensity = IgngTheme.motionIntensity
    val haptic = rememberIgngHaptics()
    val shape = RoundedCornerShape(8.dp)

    val bg by animateColorAsState(
        targetValue = if (selected) colors.accentSoft else colors.tile,
        animationSpec = IgngMotion.microTween(intensity),
        label = "wikiCatBg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.textSecondary,
        animationSpec = IgngMotion.microTween(intensity),
        label = "wikiCatFg",
    )
    val borderColor = if (selected) Color.Transparent else colors.line

    Row(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .border(IgngStroke.hairline, borderColor, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (!selected) {
                        haptic(IgngHapticKind.Tick)
                        onClick()
                    }
                },
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = label,
            style = IgngTheme.type.caption.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = fg,
            maxLines = 1,
        )
        Text(
            text = "$count",
            style = IgngTheme.type.meta,
            color = fg.copy(alpha = 0.72f),
            maxLines = 1,
        )
    }
}
