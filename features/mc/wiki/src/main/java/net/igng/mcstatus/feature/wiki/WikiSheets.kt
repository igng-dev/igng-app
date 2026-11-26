package net.igng.mcstatus.feature.wiki

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import net.igng.core.designsystem.IgngDetailSheet
import net.igng.core.designsystem.IgngGroupedSection
import net.igng.core.designsystem.IgngHairlineDivider
import net.igng.core.designsystem.IgngHapticKind
import net.igng.core.designsystem.IgngListRow
import net.igng.core.designsystem.IgngMotion
import net.igng.core.designsystem.IgngSearchField
import net.igng.core.designsystem.IgngSegmentedTabs
import net.igng.core.designsystem.IgngStateBlock
import net.igng.core.designsystem.IgngTheme
import net.igng.core.designsystem.rememberIgngHaptics
import net.igng.core.markdown.TocEntry

private enum class WikiDirectoryTab(val label: String) {
    ALL("全部目录"),
    BOOKMARKS("我的收藏"),
}

/**
 * Full-width thumb-friendly directory sheet replacing the edge-conflicting left navigation drawer.
 * Gives tutorial titles 100% viewport width, supports quick toggling to bookmarked tutorials,
 * and eliminates cluttered multi-icon tree rows.
 */
@Composable
internal fun WikiDirectorySheet(
    catalogIndex: WikiCatalogIndex,
    activePageId: Int?,
    bookmarkedIds: List<Int>,
    expandedFolderIds: Set<Int>,
    onToggleFolder: (Int) -> Unit,
    onSelectPage: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = IgngTheme.colors
    val haptic = rememberIgngHaptics()
    var activeTab by remember { mutableStateOf(WikiDirectoryTab.ALL) }
    val bookmarkedEntries = remember(catalogIndex, bookmarkedIds) {
        bookmarkedIds.mapNotNull { catalogIndex.entryOf(it) }
    }
    val dirListState = rememberLazyListState()

    // Auto-scroll to the active top-level branch on sheet open
    LaunchedEffect(activeTab, activePageId) {
        if (activeTab == WikiDirectoryTab.ALL && activePageId != null) {
            val activeTopIdx = catalogIndex.tree.indexOfFirst { nodeContainsPage(it, activePageId) }
            if (activeTopIdx > 0) {
                val offset = if (catalogIndex.rootPage != null) 1 else 0
                dirListState.scrollToItem(activeTopIdx + offset)
            }
        }
    }

    IgngDetailSheet(
        onDismiss = onDismiss,
        title = "教程目录",
        subtitle = "${catalogIndex.kbName} · 共 ${catalogIndex.totalPages} 篇",
        scrollable = false,
    ) {
        IgngSegmentedTabs(
            items = WikiDirectoryTab.entries,
            selected = activeTab,
            labelOf = { tab ->
                if (tab == WikiDirectoryTab.BOOKMARKS && bookmarkedEntries.isNotEmpty()) {
                    "${tab.label} (${bookmarkedEntries.size})"
                } else {
                    tab.label
                }
            },
            onSelect = { activeTab = it },
        )
        Spacer(Modifier.height(12.dp))

        if (activeTab == WikiDirectoryTab.BOOKMARKS) {
            if (bookmarkedEntries.isEmpty()) {
                IgngStateBlock(
                    title = "还没有收藏的教程",
                    description = "阅读时轻点底部「收藏」即可保存常用指令或插件手册。",
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(bookmarkedEntries, key = { "dir_bm_${it.page.id}" }) { entry ->
                        val isActive = entry.page.id == activePageId
                        WikiDirectoryPageRow(
                            title = entry.page.title,
                            subtitle = entry.folderLabel.takeIf { it.isNotBlank() },
                            depth = 0,
                            isActive = isActive,
                            onClick = { onSelectPage(entry.page.id) },
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                state = dirListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Pinned KB Home row
                catalogIndex.rootPage?.let { root ->
                    item(key = "dir_root_home") {
                        val isActive = root.id == activePageId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(IgngTheme.radii.sm)
                                .background(if (isActive) colors.accentSoft else Color.Transparent)
                                .clickable {
                                    haptic(IgngHapticKind.Tick)
                                    onSelectPage(root.id)
                                }
                                .defaultMinSize(minHeight = 46.dp)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Home,
                                contentDescription = null,
                                tint = if (isActive) colors.accent else colors.textSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = root.title.ifBlank { "百科首页" },
                                style = IgngTheme.type.bodyMedium.copy(
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                                ),
                                color = if (isActive) colors.accent else colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (isActive) {
                                ActiveDot()
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        IgngHairlineDivider(startIndent = 12.dp, endIndent = 12.dp)
                    }
                }

                items(catalogIndex.tree, key = { "dir_node_${it.id}" }) { node ->
                    WikiDirectoryBranch(
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

@Composable
private fun WikiDirectoryBranch(
    node: WikiTreeNode,
    activePageId: Int?,
    expandedFolderIds: Set<Int>,
    onToggleFolder: (Int) -> Unit,
    onSelectPage: (Int) -> Unit,
) {
    val colors = IgngTheme.colors
    val motion = IgngTheme.motionIntensity
    val haptic = rememberIgngHaptics()
    val visualIndent = (node.depth.coerceAtMost(2) * 12).dp

    if (node.isFolder) {
        val isExpanded = node.id in expandedFolderIds
        val containsActive = remember(node, activePageId) { nodeContainsPage(node, activePageId) }
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = visualIndent)
                    .clip(IgngTheme.radii.sm)
                    .clickable {
                        haptic(IgngHapticKind.Tick)
                        onToggleFolder(node.id)
                    }
                    .defaultMinSize(minHeight = 44.dp)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = node.title,
                    style = if (node.depth == 0) {
                        IgngTheme.type.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    } else {
                        IgngTheme.type.caption.copy(fontWeight = FontWeight.SemiBold)
                    },
                    color = if (containsActive) colors.textPrimary else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${node.pageCount} 篇",
                    style = IgngTheme.type.meta,
                    color = colors.textMuted,
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
                    contentDescription = if (isExpanded) "收起分类" else "展开分类",
                    tint = colors.textMuted,
                    modifier = Modifier.size(18.dp),
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
                        WikiDirectoryPageRow(
                            title = node.title,
                            subtitle = "本分类概览",
                            depth = node.depth + 1,
                            isActive = folderPageId == activePageId,
                            onClick = { onSelectPage(folderPageId) },
                        )
                    }
                    node.children.forEach { child ->
                        WikiDirectoryBranch(
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
        WikiDirectoryPageRow(
            title = node.title,
            subtitle = null,
            depth = node.depth,
            isActive = node.pageId == activePageId,
            onClick = { onSelectPage(node.pageId) },
        )
    }
}

@Composable
private fun WikiDirectoryPageRow(
    title: String,
    subtitle: String?,
    depth: Int,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val colors = IgngTheme.colors
    val haptic = rememberIgngHaptics()
    val startPad = (12 + depth.coerceAtMost(2) * 12).dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(IgngTheme.radii.sm)
            .background(if (isActive) colors.accentSoft else Color.Transparent)
            .clickable {
                haptic(IgngHapticKind.Tick)
                onClick()
            }
            .defaultMinSize(minHeight = 46.dp)
            .padding(start = startPad, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = IgngTheme.type.body.copy(
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (isActive) colors.accent else colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = IgngTheme.type.meta,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isActive) {
            ActiveDot()
        }
    }
}

@Composable
private fun ActiveDot() {
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(IgngTheme.colors.accent),
    )
}

@Composable
internal fun WikiTocSheet(
    toc: List<TocEntry>,
    activeTocIndex: Int,
    onJumpTop: () -> Unit,
    onJumpSection: (TocEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = IgngTheme.colors
    val haptic = rememberIgngHaptics()

    IgngDetailSheet(
        onDismiss = onDismiss,
        title = "本页大纲",
        subtitle = "共 ${toc.size} 个小节 · 点击直达",
        scrollable = false,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(IgngTheme.radii.sm)
                .background(colors.tileSoft)
                .clickable {
                    haptic(IgngHapticKind.Tap)
                    onJumpTop()
                }
                .defaultMinSize(minHeight = 44.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "回到文档顶部",
                style = IgngTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                color = colors.accent,
            )
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(toc, key = { idx, entry -> "toc_${idx}_${entry.blockIndex}" }) { idx, entry ->
                val isActive = idx == activeTocIndex
                val indent = (12 + (entry.level - 1).coerceIn(0, 3) * 14).dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(IgngTheme.radii.sm)
                        .background(if (isActive) colors.accentSoft else Color.Transparent)
                        .clickable {
                            haptic(IgngHapticKind.Tap)
                            onJumpSection(entry)
                        }
                        .defaultMinSize(minHeight = 44.dp)
                        .padding(start = indent, end = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = entry.title,
                        style = if (entry.level <= 2 || isActive) {
                            IgngTheme.type.bodyMedium.copy(
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                            )
                        } else {
                            IgngTheme.type.body
                        },
                        color = if (isActive) colors.accent else colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isActive) {
                        Text(
                            text = "当前",
                            style = IgngTheme.type.badge,
                            color = colors.accent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun WikiQuickSearchSheet(
    catalogIndex: WikiCatalogIndex,
    recentIds: List<Int>,
    activePageId: Int?,
    onSelectPage: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(catalogIndex, query) { catalogIndex.search(query) }
    val recentEntries = remember(catalogIndex, recentIds) {
        recentIds.mapNotNull { catalogIndex.entryOf(it) }.take(5)
    }

    IgngDetailSheet(
        onDismiss = onDismiss,
        title = "快速切换教程",
        subtitle = "${catalogIndex.kbName} · 共 ${catalogIndex.totalPages} 篇",
        scrollable = false,
    ) {
        IgngSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "输入教程名、插件名或分类…",
        )
        Spacer(Modifier.height(12.dp))

        val listToShow = if (query.isBlank() && recentEntries.isNotEmpty()) recentEntries else results
        val sectionTitle = when {
            query.isBlank() && recentEntries.isNotEmpty() -> "最近阅读"
            query.isNotBlank() -> "找到 ${results.size} 篇相关教程"
            else -> "全部教程"
        }

        if (listToShow.isEmpty()) {
            IgngStateBlock(
                title = "没有找到相关教程",
                description = "换一个关键词试试。",
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
            ) {
                item(key = "quick_search_grouped") {
                    IgngGroupedSection(title = sectionTitle) {
                        listToShow.forEachIndexed { idx, entry ->
                            val isCurrent = entry.page.id == activePageId
                            IgngListRow(
                                title = entry.page.title,
                                subtitle = entry.folderLabel.takeIf { it.isNotBlank() },
                                value = if (isCurrent) "当前" else null,
                                showChevron = !isCurrent,
                                showDivider = idx < listToShow.lastIndex,
                                onClick = { onSelectPage(entry.page.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun nodeContainsPage(node: WikiTreeNode, targetPageId: Int?): Boolean {
    if (targetPageId == null) return false
    if (node.pageId == targetPageId) return true
    return node.children.any { nodeContainsPage(it, targetPageId) }
}
