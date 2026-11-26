package net.igng.mcstatus.feature.explore

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.igng.core.data.IgngCacheScope
import net.igng.core.data.rememberScreenHolder
import net.igng.core.data.rememberSwrResource
import net.igng.core.data.runSuspendCatching
import net.igng.core.designsystem.IgngAmbientCanvas
import net.igng.core.designsystem.IgngAnimatedCounter
import net.igng.core.designsystem.IgngBadge
import net.igng.core.designsystem.IgngBadgeTone
import net.igng.core.designsystem.IgngButton
import net.igng.core.designsystem.IgngButtonStyle
import net.igng.core.designsystem.IgngCopyPill
import net.igng.core.designsystem.IgngGroupedSection
import net.igng.core.designsystem.IgngHairlineDivider
import net.igng.core.designsystem.IgngHapticKind
import net.igng.core.designsystem.IgngListRow
import net.igng.core.designsystem.IgngLoadingBlock
import net.igng.core.designsystem.IgngMotion
import net.igng.core.designsystem.IgngPullToRefreshBox
import net.igng.core.designsystem.IgngStateBlock
import net.igng.core.designsystem.IgngStroke
import net.igng.core.designsystem.IgngTheme
import net.igng.core.designsystem.rememberIgngHaptics
import net.igng.core.model.SavedAccount
import net.igng.core.model.SessionAuthStatus
import net.igng.mcstatus.common.IgngServerBackdropCanvas
import net.igng.mcstatus.common.serverFamilyVisual
import net.igng.mcstatus.core.data.ExploreRepository
import net.igng.mcstatus.core.data.McCacheKeys
import net.igng.mcstatus.core.data.McCachePolicies
import net.igng.mcstatus.core.data.McCacheTags
import net.igng.mcstatus.core.data.McDiskCodecs
import net.igng.mcstatus.core.data.PublicServerStatusDto
import net.igng.mcstatus.core.data.PublicStatusResponse
import net.igng.mcstatus.core.data.ServerFamilyDto
import net.igng.mcstatus.core.data.ServerSeasonDto
import net.igng.mcstatus.core.data.StatusRepository
import net.igng.mcstatus.core.model.IgngFormatters
import net.igng.mcstatus.core.model.ServerThemeKey

private class ExploreSelectionHolder {
    var selectedFamilySlug by mutableStateOf<String?>(null)
    var selectedSeasonSlug by mutableStateOf<String?>(null)
}

@Composable
fun ExploreScreen(
    exploreRepository: ExploreRepository,
    statusRepository: StatusRepository,
    activeAccount: SavedAccount?,
    sessionAuthStatus: SessionAuthStatus = if (activeAccount != null) SessionAuthStatus.ACTIVE else SessionAuthStatus.SIGNED_OUT,
    bottomPadding: PaddingValues,
    onOpenSearch: () -> Unit,
    onOpenStatusWall: () -> Unit,
    onOpenMap: (familySlug: String?, seasonSlug: String?) -> Unit,
    onOpenFamilyDetail: (ServerFamilyDto) -> Unit,
    onOpenCommunityHall: () -> Unit,
    onOpenPerfectWall: () -> Unit,
    onOpenMyProfile: () -> Unit,
) {
    val colors = IgngTheme.colors
    val haptic = rememberIgngHaptics()
    val selection = rememberScreenHolder("explore_selection") { ExploreSelectionHolder() }

    val (familiesState, familiesHandle) = rememberSwrResource<List<ServerFamilyDto>>(
        cache = exploreRepository.swrCache,
        cacheKey = McCacheKeys.SERVERS,
        scope = IgngCacheScope.PUBLIC,
        policy = McCachePolicies.Static,
        tags = setOf(McCacheTags.SERVERS),
        diskCodec = McDiskCodecs.servers,
        legacyRawProvider = {
            val legacy = exploreRepository.readCachedFamilies()
            if (legacy.isNotEmpty()) McDiskCodecs.servers.encode(legacy) else null
        },
        slotKey = "explore_families",
    ) {
        runSuspendCatching { exploreRepository.fetchHomeFamilies() }
    }

    val (statusState, statusHandle) = rememberSwrResource<PublicStatusResponse>(
        cache = statusRepository.swrCache,
        cacheKey = McCacheKeys.STATUS,
        scope = IgngCacheScope.PUBLIC,
        policy = McCachePolicies.Status,
        tags = setOf(McCacheTags.STATUS),
        diskCodec = McDiskCodecs.status,
        pollIntervalMs = 30_000L,
        slotKey = "explore_status",
    ) {
        runSuspendCatching { statusRepository.fetchPublicStatus() }
    }

    val families = familiesState.data.orEmpty()
    val statuses = statusState.data?.servers.orEmpty()
    val loading = familiesState.isInitialLoading && families.isEmpty()
    val refreshing = familiesState.isUserRefreshing || statusState.isUserRefreshing
    val error = if (families.isEmpty()) familiesState.error else null

    val selectedFamily = remember(families, selection.selectedFamilySlug) {
        families.firstOrNull { it.slug == selection.selectedFamilySlug } ?: families.firstOrNull()
    }
    val selectedSeason = remember(selectedFamily, selection.selectedSeasonSlug) {
        val alive = selectedFamily?.aliveSeasons.orEmpty()
        alive.firstOrNull { it.slug == selection.selectedSeasonSlug }
            ?: selectedFamily?.primarySeason
            ?: alive.firstOrNull()
            ?: selectedFamily?.allKnownSeasons?.firstOrNull()
    }
    val activeThemeKey = remember(selectedFamily, selectedSeason) {
        ServerThemeKey.resolve(selectedSeason?.backgroundKey ?: selectedFamily?.themeKey)
    }

    val statusByServerId = remember(statuses) { statuses.associateBy { it.id } }
    val selectedServerStatus = selectedSeason?.serverId?.let { statusByServerId[it] }
    val totalOnline = remember(statuses) { statuses.sumOf { it.onlinePlayers ?: 0 } }
    val onlineServers = remember(statuses) { statuses.count { it.online } }

    IgngAmbientCanvas {
        IgngPullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                familiesHandle.refresh()
                statusHandle.refresh()
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 10.dp,
                    bottom = bottomPadding.calculateBottomPadding() + 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 1. Quiet Craft brand header + search icon action + account entry
                item(key = "header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "IGNG Minecraft",
                                style = IgngTheme.type.pageTitle,
                                color = colors.textPrimary,
                            )
                            if (statuses.isNotEmpty()) {
                                Text(
                                    text = "$onlineServers/${statuses.size} 台周目在线 · $totalOnline 名玩家",
                                    style = IgngTheme.type.meta,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                haptic(IgngHapticKind.Tap)
                                onOpenSearch()
                            },
                            modifier = Modifier.size(38.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "全局搜索",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                        val needsReauth = activeAccount != null && sessionAuthStatus.requiresReauth
                        Box(
                            modifier = Modifier
                                .clip(IgngTheme.radii.sm)
                                .background(if (needsReauth) colors.warningBg else colors.tile)
                                .border(
                                    IgngStroke.hairline,
                                    if (needsReauth) colors.warning.copy(alpha = 0.55f) else colors.lineStrong,
                                    IgngTheme.radii.sm,
                                )
                                .clickable {
                                    haptic(IgngHapticKind.Tap)
                                    onOpenMyProfile()
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = when {
                                    needsReauth -> "需重新授权"
                                    activeAccount != null -> activeAccount.displayName
                                    else -> "登录"
                                },
                                style = IgngTheme.type.caption.copy(fontWeight = FontWeight.Medium),
                                color = when {
                                    needsReauth -> colors.warning
                                    activeAccount != null -> colors.textPrimary
                                    else -> colors.accent
                                },
                                maxLines = 1,
                            )
                        }
                    }
                }

                // 2. Server Family Switcher + Quiet Craft Hero Card
                item(key = "hero") {
                    when {
                        loading && families.isEmpty() -> IgngLoadingBlock()
                        error != null && families.isEmpty() -> IgngStateBlock(
                            title = "服务器目录暂时不可用",
                            description = error,
                            isError = true,
                            actionLabel = "重试",
                            onAction = {
                                familiesHandle.refresh()
                                statusHandle.refresh()
                            },
                        )
                        selectedFamily != null -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // Compact family selector strip
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    families.forEach { family ->
                                        val isSelected = family.slug == selectedFamily.slug
                                        val key = ServerThemeKey.resolve(family.themeKey)
                                        val visual = serverFamilyVisual(key, colors.isDark)
                                        val intensity = IgngTheme.motionIntensity
                                        val pillBg by animateColorAsState(
                                            targetValue = if (isSelected) visual.softTint else colors.tile,
                                            animationSpec = IgngMotion.microTween(intensity),
                                            label = "famPillBg",
                                        )
                                        val pillBorder by animateColorAsState(
                                            targetValue = if (isSelected) visual.accent.copy(alpha = 0.45f) else colors.line,
                                            animationSpec = IgngMotion.microTween(intensity),
                                            label = "famPillBorder",
                                        )
                                        Row(
                                            modifier = Modifier
                                                .clip(IgngTheme.radii.sm)
                                                .background(pillBg)
                                                .border(IgngStroke.hairline, pillBorder, IgngTheme.radii.sm)
                                                .clickable {
                                                    haptic(IgngHapticKind.Tick)
                                                    selection.selectedFamilySlug = family.slug
                                                    selection.selectedSeasonSlug = null
                                                }
                                                .padding(horizontal = 12.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(visual.accent),
                                            )
                                            Text(
                                                text = family.name,
                                                style = IgngTheme.type.caption.copy(
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                                ),
                                                color = if (isSelected) visual.accent else colors.textSecondary,
                                            )
                                        }
                                    }
                                }

                                // Quiet Craft Server Family Hero Card
                                ServerFamilyHeroCard(
                                    family = selectedFamily,
                                    season = selectedSeason,
                                    status = selectedServerStatus,
                                    themeKey = activeThemeKey,
                                    onSelectSeason = { selection.selectedSeasonSlug = it.slug },
                                    onOpenMap = { onOpenMap(selectedFamily.slug, selectedSeason?.slug) },
                                    onOpenFamilyDetail = { onOpenFamilyDetail(selectedFamily) },
                                )
                            }
                        }
                    }
                }

                // 3. Quiet Craft Grouped Discovery & Services List (replacing status box + 4 rainbow tiles)
                item(key = "discoveryGroup") {
                    IgngGroupedSection(title = "发现与服务") {
                        IgngListRow(
                            title = "服务器运行状态墙",
                            subtitle = "实时 TPS、延迟趋势曲线与 90 格健康记录",
                            icon = Icons.Outlined.Timeline,
                            value = if (statuses.isNotEmpty()) "$totalOnline 人在线" else null,
                            onClick = onOpenStatusWall,
                            showDivider = true,
                        )
                        IgngListRow(
                            title = "世界地图",
                            subtitle = "实时在线与历史快照",
                            icon = Icons.Outlined.Map,
                            onClick = { onOpenMap(selectedFamily?.slug, selectedSeason?.slug) },
                            showDivider = true,
                        )
                        IgngListRow(
                            title = "社区大厅",
                            subtitle = "领地、假人与公开工单",
                            icon = Icons.Outlined.Groups,
                            onClick = onOpenCommunityHall,
                            showDivider = true,
                        )
                        IgngListRow(
                            title = "完美之人",
                            subtitle = "荣誉成员名录与权益",
                            icon = Icons.Outlined.WorkspacePremium,
                            onClick = onOpenPerfectWall,
                            showDivider = true,
                        )
                        IgngListRow(
                            title = "全服目录",
                            subtitle = "${families.size} 个服务器家族档案",
                            icon = Icons.Outlined.Dns,
                            onClick = { selectedFamily?.let(onOpenFamilyDetail) ?: onOpenStatusWall() },
                            showDivider = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerFamilyHeroCard(
    family: ServerFamilyDto,
    season: ServerSeasonDto?,
    status: PublicServerStatusDto?,
    themeKey: ServerThemeKey,
    onSelectSeason: (ServerSeasonDto) -> Unit,
    onOpenMap: () -> Unit,
    onOpenFamilyDetail: () -> Unit,
) {
    val colors = IgngTheme.colors
    val visual = serverFamilyVisual(themeKey, colors.isDark)
    val haptic = rememberIgngHaptics()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(IgngTheme.radii.hero)
            .background(colors.tile)
            .border(IgngStroke.hairline, colors.line, IgngTheme.radii.hero),
    ) {
        IgngServerBackdropCanvas(themeKey = themeKey, modifier = Modifier.matchParentSize())

        val heroIntensity = IgngTheme.motionIntensity
        AnimatedContent(
            targetState = "${family.slug}:${season?.slug}",
            transitionSpec = {
                fadeIn(IgngMotion.normalTween(heroIntensity)) togetherWith
                    fadeOut(IgngMotion.microTween(heroIntensity))
            },
            label = "heroSwap",
        ) { _ ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IgngBadge(
                        text = season?.label?.ifBlank { themeKey.badge } ?: themeKey.badge,
                        customColor = visual.accent,
                        leadingDot = true,
                        pulse = false,
                    )
                    val statusText = when {
                        status == null -> IgngFormatters.seasonStateLabel(season?.state)
                        status.online -> "在线 · ${IgngFormatters.formatDecimal(status.pingMs, 0, " ms")}"
                        status.status == "degraded" -> "波动"
                        status.status == "offline" -> "离线"
                        else -> "状态未知"
                    }
                    IgngBadge(
                        text = statusText,
                        tone = when {
                            status?.online == true -> IgngBadgeTone.Success
                            status?.status == "degraded" -> IgngBadgeTone.Warning
                            status?.status == "offline" -> IgngBadgeTone.Danger
                            else -> IgngBadgeTone.Neutral
                        },
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .clip(IgngTheme.radii.pill)
                                .background(visual.accent),
                        )
                        Text(
                            text = buildString {
                                append(family.name)
                                if (!season?.label.isNullOrBlank()) {
                                    append(" · ")
                                    append(season?.label)
                                }
                            },
                            style = IgngTheme.type.pageTitle,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val desc = season?.cardDescription?.ifBlank { season.description }
                        ?.ifBlank { family.tagline }
                        ?.ifBlank { family.description }
                        .orEmpty()
                    if (desc.isNotBlank()) {
                        Text(
                            text = desc,
                            style = IgngTheme.type.body,
                            color = colors.textSecondary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Multi-season tabs when family has > 1 alive season
                if (family.aliveSeasons.size > 1) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        family.aliveSeasons.forEach { s ->
                            val picked = s.slug == season?.slug
                            Box(
                                modifier = Modifier
                                    .clip(IgngTheme.radii.badge)
                                    .background(if (picked) visual.softTint else colors.tileSoft)
                                    .border(
                                        IgngStroke.hairline,
                                        if (picked) visual.accent.copy(alpha = 0.45f) else colors.line,
                                        IgngTheme.radii.badge,
                                    )
                                    .clickable {
                                        haptic(IgngHapticKind.Tick)
                                        onSelectSeason(s)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    text = s.label.ifBlank { s.slug },
                                    style = IgngTheme.type.badge.copy(
                                        fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Medium,
                                    ),
                                    color = if (picked) visual.accent else colors.textSecondary,
                                )
                            }
                        }
                    }
                }

                // Flat hairline-separated live metrics strip (zero nested metric boxes)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    IgngHairlineDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HeroMetricItem(
                            label = "在线玩家",
                            value = IgngFormatters.formatNumber(status?.onlinePlayers),
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .width(IgngStroke.hairline)
                                .height(30.dp)
                                .background(colors.line),
                        )
                        HeroMetricItem(
                            label = "TPS",
                            value = IgngFormatters.formatDecimal(status?.tps, 1),
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                        )
                        Box(
                            modifier = Modifier
                                .width(IgngStroke.hairline)
                                .height(30.dp)
                                .background(colors.line),
                        )
                        HeroMetricItem(
                            label = "版本",
                            value = season?.version?.ifBlank { "—" } ?: "—",
                            modifier = Modifier
                                .weight(1.2f)
                                .padding(start = 12.dp),
                        )
                    }
                    IgngHairlineDivider()
                }

                // Address copy pill
                val address = season?.address ?: status?.address
                if (!address.isNullOrBlank()) {
                    IgngCopyPill(
                        value = address,
                        label = address,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Primary & secondary actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IgngButton(
                        text = "查看周目档案",
                        onClick = onOpenFamilyDetail,
                        style = IgngButtonStyle.Primary,
                        icon = Icons.Rounded.Explore,
                        modifier = Modifier.weight(1f),
                    )
                    if (season?.hasViewableMap == true) {
                        IgngButton(
                            text = "打开地图",
                            onClick = onOpenMap,
                            style = IgngButtonStyle.Secondary,
                            icon = Icons.Rounded.Map,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroMetricItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = IgngTheme.colors
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = IgngTheme.type.meta, color = colors.textMuted)
        IgngAnimatedCounter(
            text = value,
            style = IgngTheme.type.metricMedium,
            color = colors.textPrimary,
        )
    }
}
