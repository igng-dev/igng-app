package net.igng.mcstatus.feature.explore

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.igng.core.data.IgngCacheScope
import net.igng.core.data.rememberSwrResource
import net.igng.core.data.runSuspendCatching
import net.igng.core.designsystem.HealthBucketItem
import net.igng.core.designsystem.HealthBucketLevel
import net.igng.core.designsystem.IgngAnimatedCounter
import net.igng.core.designsystem.IgngAvatar
import net.igng.core.designsystem.IgngBadge
import net.igng.core.designsystem.IgngBadgeTone
import net.igng.core.designsystem.IgngBrowserLauncher
import net.igng.core.designsystem.IgngButton
import net.igng.core.designsystem.IgngButtonStyle
import net.igng.core.designsystem.IgngChartPoint
import net.igng.core.designsystem.IgngCopyPill
import net.igng.core.designsystem.IgngDetailSheet
import net.igng.core.designsystem.IgngHairlineDivider
import net.igng.core.designsystem.IgngHapticKind
import net.igng.core.designsystem.IgngInteractiveTile
import net.igng.core.designsystem.IgngLineChart
import net.igng.core.designsystem.IgngLoadingBlock
import net.igng.core.designsystem.IgngMotion
import net.igng.core.designsystem.IgngSectionHeader
import net.igng.core.designsystem.IgngSegmentedTabs
import net.igng.core.designsystem.IgngStatusHealthStrip
import net.igng.core.designsystem.IgngStroke
import net.igng.core.designsystem.IgngSwitchRow
import net.igng.core.designsystem.IgngTextField
import net.igng.core.designsystem.IgngTheme
import net.igng.core.designsystem.IgngTile
import net.igng.core.designsystem.copyTextToClipboard
import net.igng.core.designsystem.rememberIgngHaptics
import net.igng.core.model.asBooleanFlag
import net.igng.mcstatus.common.IgngServerBackdropCanvas
import net.igng.mcstatus.common.serverFamilyVisual
import net.igng.mcstatus.core.data.ChatMessageDto
import net.igng.mcstatus.core.data.ChatServerSourceDto
import net.igng.mcstatus.core.data.HallFakePlayerDto
import net.igng.mcstatus.core.data.HallLandDto
import net.igng.mcstatus.core.data.McCacheKeys
import net.igng.mcstatus.core.data.McCachePolicies
import net.igng.mcstatus.core.data.McCacheTags
import net.igng.mcstatus.core.data.PlayerFakePlayerDto
import net.igng.mcstatus.core.data.PlayerLandDto
import net.igng.mcstatus.core.data.PlayerRepository
import net.igng.mcstatus.core.data.PlayerTeleportDto
import net.igng.mcstatus.core.data.PublicServerStatusDto
import net.igng.mcstatus.core.data.PublicStatusHistoryResponse
import net.igng.mcstatus.core.data.ServerFamilyDto
import net.igng.mcstatus.core.data.ServerHistorySeriesDto
import net.igng.mcstatus.core.data.ServerSeasonDto
import net.igng.mcstatus.core.data.StatusRangePreset
import net.igng.mcstatus.core.data.StatusRepository
import net.igng.mcstatus.core.data.VanillaStatsServerDto
import net.igng.mcstatus.core.model.IgngFormatters
import net.igng.mcstatus.core.model.ServerThemeKey
import net.igng.mcstatus.core.model.SiteEndpoints

// ==================== P0: Live Server Telemetry & Quick-Join Hero Card ====================

@Composable
internal fun ServerTelemetryHeroCard(
    family: ServerFamilyDto,
    season: ServerSeasonDto,
    status: PublicServerStatusDto?,
    historySeries: ServerHistorySeriesDto?,
    themeKey: ServerThemeKey,
    siblingAliveSeasons: List<ServerSeasonDto>,
    onSelectSiblingSeason: (ServerSeasonDto) -> Unit,
    onOpenTelemetrySheet: () -> Unit,
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Top badges: season/version identity + live telemetry status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    val primaryBadge = season.modpack?.takeIf { it.isNotBlank() }
                        ?: season.label.ifBlank { themeKey.badge }
                    IgngBadge(
                        text = primaryBadge,
                        customColor = visual.accent,
                        leadingDot = true,
                        pulse = false,
                    )
                    val versionCore = listOfNotNull(
                        season.version?.takeIf { it.isNotBlank() } ?: status?.version?.takeIf { it.isNotBlank() },
                        season.core?.takeIf { it.isNotBlank() } ?: status?.core?.takeIf { it.isNotBlank() },
                    ).joinToString(" · ")
                    if (versionCore.isNotBlank()) {
                        Text(
                            text = versionCore,
                            style = IgngTheme.type.meta,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                val statusText = when {
                    status == null -> IgngFormatters.seasonStateLabel(season.state)
                    status.online && status.pingMs != null -> "在线 · ${IgngFormatters.formatDecimal(status.pingMs, 0, "ms")}"
                    status.online -> "在线"
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
                    leadingDot = status != null,
                    pulse = status?.online == true,
                )
            }

            // Server title + optional notice banner
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                            if (season.label.isNotBlank() && !season.label.equals(family.name, true) && !(family.kind.equals("single", true) && season.label == "长期服")) {
                                append(" · ")
                                append(season.label)
                            }
                        },
                        style = IgngTheme.type.pageTitle,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                season.notice?.takeIf { it.isNotBlank() }?.let { noticeText ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(IgngTheme.radii.sm)
                            .background(colors.warningBg)
                            .border(IgngStroke.hairline, colors.warning.copy(alpha = 0.32f), IgngTheme.radii.sm)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = colors.warning,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = noticeText,
                            style = IgngTheme.type.caption,
                            color = colors.warning,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Fallback inline season selector (only active when total alive seasons > 6)
            if (siblingAliveSeasons.size > 1) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    siblingAliveSeasons.forEach { s ->
                        val picked = s.slug == season.slug
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
                                    onSelectSiblingSeason(s)
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

            // Flat hairline-separated live metrics strip
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                IgngHairlineDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val onlinePlayers = status?.onlinePlayers
                    val maxPlayers = status?.maxPlayers
                    val playerValue = when {
                        onlinePlayers != null && maxPlayers != null && maxPlayers > 0 ->
                            "${IgngFormatters.formatNumber(onlinePlayers)}/${IgngFormatters.formatNumber(maxPlayers)}"
                        else -> IgngFormatters.formatNumber(onlinePlayers)
                    }
                    TelemetryMetricCell(
                        label = "在线玩家",
                        value = playerValue,
                        modifier = Modifier.weight(1.1f),
                    )
                    Box(
                        modifier = Modifier
                            .width(IgngStroke.hairline)
                            .height(28.dp)
                            .background(colors.line),
                    )
                    val middleLabel: String
                    val middleValue: String
                    when {
                        status?.tps != null -> {
                            middleLabel = "TPS"
                            middleValue = IgngFormatters.formatDecimal(status.tps, 1)
                        }
                        status?.packetLossPct != null -> {
                            middleLabel = "丢包率"
                            middleValue = IgngFormatters.formatDecimal(status.packetLossPct, 1, "%")
                        }
                        else -> {
                            middleLabel = "版本"
                            middleValue = season.version?.ifBlank { "—" } ?: "—"
                        }
                    }
                    TelemetryMetricCell(
                        label = middleLabel,
                        value = middleValue,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    )
                    Box(
                        modifier = Modifier
                            .width(IgngStroke.hairline)
                            .height(28.dp)
                            .background(colors.line),
                    )
                    val rightLabel: String
                    val rightValue: String
                    when {
                        status?.mspt != null -> {
                            rightLabel = "MSPT"
                            rightValue = IgngFormatters.formatDecimal(status.mspt, 1, "ms")
                        }
                        status?.pingMs != null -> {
                            rightLabel = "网络延迟"
                            rightValue = IgngFormatters.formatDecimal(status.pingMs, 0, "ms")
                        }
                        else -> {
                            rightLabel = "状态"
                            rightValue = IgngFormatters.seasonStateLabel(season.state)
                        }
                    }
                    TelemetryMetricCell(
                        label = rightLabel,
                        value = rightValue,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    )
                }
                IgngHairlineDivider()
            }

            // Embedded 24h uptime health strip + drill-down into full curves
            val healthBuckets = remember(historySeries) {
                historySeries?.statusGrid.orEmpty().map { b ->
                    val level = when (b.status.lowercase()) {
                        "good", "healthy", "online", "ok" -> HealthBucketLevel.Healthy
                        "degraded", "warn", "warning", "stale" -> HealthBucketLevel.Degraded
                        "critical", "down", "offline", "unreachable", "error" -> HealthBucketLevel.Down
                        else -> HealthBucketLevel.NoData
                    }
                    HealthBucketItem(
                        level = level,
                        title = IgngFormatters.formatIsoDateTime(b.start, short = true),
                        detail = b.coverageLabel.ifBlank { b.status },
                    )
                }
            }
            if (healthBuckets.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IgngStatusHealthStrip(
                        buckets = healthBuckets,
                        startLabel = "24小时前",
                        endLabel = "现在",
                        windowLabel = "24h",
                    )
                    if (status != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(IgngTheme.radii.xs)
                                .clickable {
                                    haptic(IgngHapticKind.Tap)
                                    onOpenTelemetrySheet()
                                }
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Timeline,
                                    contentDescription = null,
                                    tint = visual.accent,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = "查看 TPS、延迟与在线趋势曲线",
                                    style = IgngTheme.type.caption.copy(fontWeight = FontWeight.Medium),
                                    color = visual.accent,
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = visual.accent,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            } else if (status != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(IgngTheme.radii.xs)
                        .clickable {
                            haptic(IgngHapticKind.Tap)
                            onOpenTelemetrySheet()
                        }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Timeline,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "查看 1h / 24h / 7天 详细趋势曲线",
                            style = IgngTheme.type.caption,
                            color = colors.textSecondary,
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // Quick-join address + optional server switch command
            val address = season.address?.takeIf { it.isNotBlank() } ?: status?.address?.takeIf { it.isNotBlank() }
            val switchCommand = season.command?.takeIf { it.isNotBlank() }
            if (address != null || switchCommand != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (address != null) {
                        IgngCopyPill(
                            value = address,
                            label = address,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (switchCommand != null) {
                        CompactCopyChip(
                            value = switchCommand,
                            label = switchCommand,
                            icon = Icons.Rounded.Terminal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryMetricCell(
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

// ==================== P0: World Map & Coordinate Radar Preview Card ====================

@Composable
internal fun ServerMapPreviewCard(
    endpoints: SiteEndpoints,
    family: ServerFamilyDto,
    season: ServerSeasonDto,
    themeKey: ServerThemeKey,
    radarProjection: ServerRadarProjection?,
    publicLandCount: Int,
    activeFakeCount: Int,
    onOpenMap: () -> Unit,
) {
    val colors = IgngTheme.colors
    val visual = serverFamilyVisual(themeKey, colors.isDark)

    IgngInteractiveTile(
        onClick = onOpenMap,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Map,
                    contentDescription = null,
                    tint = visual.accent,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "世界地图",
                    style = IgngTheme.type.cardTitle,
                    color = colors.textPrimary,
                )
                IgngBadge(
                    text = when {
                        season.isSnapshotMap -> "历史快照"
                        season.isArchivedMap -> "归档交互"
                        else -> "实时同步"
                    },
                    tone = when {
                        season.isSnapshotMap -> IgngBadgeTone.Neutral
                        season.isArchivedMap -> IgngBadgeTone.Accent
                        else -> IgngBadgeTone.Success
                    },
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "全屏地图",
                    style = IgngTheme.type.caption.copy(fontWeight = FontWeight.Medium),
                    color = colors.accent,
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (season.isSnapshotMap) {
            SnapshotMapPreviewFrame(
                endpoints = endpoints,
                season = season,
                familyName = family.name,
            )
        } else {
            CoordinateRadarPreviewFrame(
                projection = radarProjection,
                familyAccent = visual.accent,
                worlds = season.worlds.map { it.displayLabel },
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val worldSummary = season.worlds.take(3).joinToString(" · ") { it.displayLabel }
                .ifBlank { "主世界 · 下界 · 末地" }
            Text(
                text = worldSummary,
                style = IgngTheme.type.meta,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val rightMeta = buildList {
                if (publicLandCount > 0) add("$publicLandCount 处公开领地")
                if (activeFakeCount > 0) add("$activeFakeCount 个在线假人")
                if (isEmpty() && radarProjection != null) add("跨度约 ${radarProjection.spanBlocks} 格")
            }.joinToString(" · ")
            if (rightMeta.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = rightMeta,
                    style = IgngTheme.type.meta,
                    color = colors.textMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SnapshotMapPreviewFrame(
    endpoints: SiteEndpoints,
    season: ServerSeasonDto,
    familyName: String,
) {
    val colors = IgngTheme.colors
    val urls = remember(season.slug) { endpoints.snapshotMapUrls(season.slug) }
    var idx by remember(season.slug) { mutableIntStateOf(0) }
    var failed by remember(season.slug) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(IgngTheme.radii.md)
            .background(colors.tileSoft)
            .border(IgngStroke.hairline, colors.line, IgngTheme.radii.md),
        contentAlignment = Alignment.Center,
    ) {
        if (!failed) {
            AsyncImage(
                model = urls[idx],
                contentDescription = "$familyName ${season.label} 历史快照",
                contentScale = ContentScale.Crop,
                onError = {
                    if (idx < urls.lastIndex) idx++ else failed = true
                },
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, colors.scrim.copy(alpha = 0.42f)),
                        ),
                    ),
            )
        } else {
            Text("历史快照预览暂不可用", style = IgngTheme.type.caption, color = colors.textMuted)
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
        ) {
            IgngBadge(
                text = "点击双指缩放查看完整快照",
                tone = IgngBadgeTone.Neutral,
            )
        }
    }
}

@Composable
private fun CoordinateRadarPreviewFrame(
    projection: ServerRadarProjection?,
    familyAccent: Color,
    worlds: List<String>,
) {
    val colors = IgngTheme.colors
    val lineColor = colors.line
    val axisColor = colors.lineStrong
    val accentColor = colors.accent
    val successColor = colors.success
    val mutedColor = colors.textMuted

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(126.dp)
            .clip(IgngTheme.radii.md)
            .background(colors.tileSoft)
            .border(IgngStroke.hairline, colors.line, IgngTheme.radii.md),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Soft cartographic corner glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(familyAccent.copy(alpha = if (colors.isDark) 0.14f else 0.08f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                    radius = size.maxDimension * 0.62f,
                ),
                center = Offset(size.width * 0.5f, size.height * 0.5f),
                radius = size.maxDimension * 0.62f,
            )

            // Subtle dashed coordinate crosshairs (2 calm reference axes only)
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            drawLine(
                color = axisColor,
                start = Offset(0f, size.height * 0.5f),
                end = Offset(size.width, size.height * 0.5f),
                strokeWidth = 0.8.dp.toPx(),
                pathEffect = dash,
            )
            drawLine(
                color = axisColor,
                start = Offset(size.width * 0.5f, 0f),
                end = Offset(size.width * 0.5f, size.height),
                strokeWidth = 0.8.dp.toPx(),
                pathEffect = dash,
            )

            // Calm concentric range rings
            listOf(0.22f, 0.40f).forEach { factor ->
                drawCircle(
                    color = lineColor,
                    radius = size.minDimension * factor,
                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                    style = Stroke(width = 0.8.dp.toPx()),
                )
            }

            projection?.markers?.forEach { marker ->
                val cx = size.width * marker.normX
                val cy = size.height * marker.normZ
                when (marker.kind) {
                    RadarMarkerKind.PUBLIC_LAND -> {
                        drawCircle(color = mutedColor.copy(alpha = 0.45f), radius = 3.2.dp.toPx(), center = Offset(cx, cy))
                    }
                    RadarMarkerKind.MY_LAND -> {
                        val half = 4.5.dp.toPx()
                        drawRoundRect(
                            color = familyAccent.copy(alpha = 0.24f),
                            topLeft = Offset(cx - half, cy - half),
                            size = Size(half * 2, half * 2),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                        )
                        drawRoundRect(
                            color = familyAccent,
                            topLeft = Offset(cx - half, cy - half),
                            size = Size(half * 2, half * 2),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                            style = Stroke(width = 1.2.dp.toPx()),
                        )
                    }
                    RadarMarkerKind.FAKE_PLAYER -> {
                        drawCircle(color = successColor.copy(alpha = 0.24f), radius = 7.dp.toPx(), center = Offset(cx, cy))
                        drawCircle(color = successColor, radius = 3.3.dp.toPx(), center = Offset(cx, cy))
                    }
                    RadarMarkerKind.MY_TELEPORT -> {
                        drawCircle(color = accentColor.copy(alpha = 0.22f), radius = 6.5.dp.toPx(), center = Offset(cx, cy))
                        drawCircle(color = accentColor, radius = 3.dp.toPx(), center = Offset(cx, cy))
                    }
                }
            }
        }

        // Top-left dimension pill
        val dimensionBadge = projection?.let { IgngFormatters.worldLabel(it.worldKey) }
            ?: worlds.firstOrNull()
            ?: "主世界"
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
        ) {
            IgngBadge(text = "$dimensionBadge · 坐标分布")
        }

        // Bottom legend or calm prompt when no points yet
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (projection == null) {
                Text(
                    text = "点击打开 Conflux 实时交互地形与路径点",
                    style = IgngTheme.type.badge,
                    color = colors.textSecondary,
                )
            } else {
                val hasTp = projection.markers.any { it.kind == RadarMarkerKind.MY_TELEPORT }
                val hasMyLand = projection.markers.any { it.kind == RadarMarkerKind.MY_LAND }
                val hasFake = projection.markers.any { it.kind == RadarMarkerKind.FAKE_PLAYER }
                val hasPub = projection.markers.any { it.kind == RadarMarkerKind.PUBLIC_LAND }
                if (hasTp) RadarLegendItem("传送点", accentColor)
                if (hasMyLand) RadarLegendItem("我的领地", familyAccent)
                if (hasFake) RadarLegendItem("假人", successColor)
                if (hasPub) RadarLegendItem("公开领地", mutedColor)
            }
        }
    }
}

@Composable
private fun RadarLegendItem(label: String, tint: Color) {
    val colors = IgngTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(tint),
        )
        Text(text = label, style = IgngTheme.type.badge, color = colors.textSecondary)
    }
}

// ==================== Adaptive P0/P1 Card for Modpack Servers Without Live Map ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ModpackBriefCard(
    season: ServerSeasonDto,
    themeKey: ServerThemeKey,
    onOpenFullGuide: () -> Unit,
) {
    val colors = IgngTheme.colors
    val context = LocalContext.current
    val visual = serverFamilyVisual(themeKey, colors.isDark)

    IgngTile(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
    ) {
        IgngSectionHeader(
            title = season.modpack?.takeIf { it.isNotBlank() }?.let { "整合包 · $it" } ?: "开荒与玩法须知",
            actionLabel = "完整指南",
            onAction = onOpenFullGuide,
            prominent = true,
        )
        val summary = season.cardDescription.ifBlank { season.description }
        if (summary.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = summary,
                style = IgngTheme.type.caption,
                color = colors.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val keyPoints = (season.highlights.take(2) + season.rules.take(2)).take(3)
        if (keyPoints.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                keyPoints.forEach { line ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(visual.accent),
                        )
                        Text(
                            text = line,
                            style = IgngTheme.type.caption,
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        val linkPlugins = season.plugins.filter { !it.href.isNullOrBlank() || !it.value.isNullOrBlank() }.take(4)
        if (linkPlugins.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                linkPlugins.forEach { p ->
                    val label = if (!p.value.isNullOrBlank() && p.label.isNotBlank()) "${p.label}：${p.value}" else p.displayTitle
                    val href = p.href?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
                    if (href != null) {
                        Row(
                            modifier = Modifier
                                .clip(IgngTheme.radii.badge)
                                .clickable { IgngBrowserLauncher.openExternalUrl(context, href, colors.isDark) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            IgngBadge(text = label, tone = IgngBadgeTone.Accent)
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    } else if (label.isNotBlank()) {
                        IgngBadge(text = label)
                    }
                }
            }
        }
    }
}

// ==================== P1: My Server Companion Toolbox (Fakes / Teleports / Lands / Stats) ====================

@Composable
internal fun MyServerCompanionCard(
    serverId: Int,
    isAuthenticated: Boolean,
    requiresReauth: Boolean,
    myFakes: List<PlayerFakePlayerDto>,
    myTeleports: List<PlayerTeleportDto>,
    myLands: List<PlayerLandDto>,
    serverStats: VanillaStatsServerDto?,
    onSignIn: () -> Unit,
    onOpenFakes: () -> Unit,
    onOpenTeleports: () -> Unit,
    onOpenLands: () -> Unit,
    onOpenStats: () -> Unit,
    onEditLand: (PlayerLandDto) -> Unit,
) {
    val colors = IgngTheme.colors
    val haptic = rememberIgngHaptics()

    if (!isAuthenticated) {
        IgngInteractiveTile(
            onClick = onSignIn,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Login,
                    contentDescription = null,
                    tint = if (requiresReauth) colors.warning else colors.accent,
                    modifier = Modifier.size(19.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (requiresReauth) "重新授权以恢复个人游玩伴侣" else "登录解锁本服个人游玩伴侣",
                        style = IgngTheme.type.bodyMedium,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "直接查看你的挂机假人健康、常用传送点坐标与领地状态",
                        style = IgngTheme.type.meta,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        return
    }

    val hasAnyPersonalData = myFakes.isNotEmpty() || myTeleports.isNotEmpty() || myLands.isNotEmpty() || serverStats != null
    if (!hasAnyPersonalData) {
        return
    }

    val availableTabs = remember(myFakes.size, myTeleports.size, myLands.size) {
        buildList {
            if (myFakes.isNotEmpty()) add(CompanionMyToolTab.FAKES)
            if (myTeleports.isNotEmpty()) add(CompanionMyToolTab.TELEPORTS)
            if (myLands.isNotEmpty()) add(CompanionMyToolTab.LANDS)
        }
    }

    var selectedTab by remember(serverId, availableTabs) {
        mutableStateOf(
            resolveDefaultMyToolTab(myFakes.size, myTeleports.size, myLands.size)
                .takeIf { it in availableTabs }
                ?: availableTabs.firstOrNull()
                ?: CompanionMyToolTab.TELEPORTS,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IgngSectionHeader(
            title = "我的本服伴侣",
            actionLabel = when (selectedTab) {
                CompanionMyToolTab.FAKES -> "全部假人"
                CompanionMyToolTab.TELEPORTS -> "全部传送点"
                CompanionMyToolTab.LANDS -> "管理领地"
            },
            onAction = {
                when (selectedTab) {
                    CompanionMyToolTab.FAKES -> onOpenFakes()
                    CompanionMyToolTab.TELEPORTS -> onOpenTeleports()
                    CompanionMyToolTab.LANDS -> onOpenLands()
                }
            },
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        IgngTile(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
        ) {
            if (availableTabs.size > 1) {
                IgngSegmentedTabs(
                    items = availableTabs,
                    selected = selectedTab,
                    labelOf = { tab ->
                        when (tab) {
                            CompanionMyToolTab.FAKES -> "${tab.label} (${myFakes.size})"
                            CompanionMyToolTab.TELEPORTS -> "${tab.label} (${myTeleports.size})"
                            CompanionMyToolTab.LANDS -> "${tab.label} (${myLands.size})"
                        }
                    },
                    onSelect = { selectedTab = it },
                )
                Spacer(Modifier.height(12.dp))
            }

            if (availableTabs.isNotEmpty()) {
                when (selectedTab) {
                    CompanionMyToolTab.FAKES -> {
                        myFakes.take(4).forEachIndexed { idx, fp ->
                            if (idx > 0) {
                                Spacer(Modifier.height(8.dp))
                                IgngHairlineDivider()
                                Spacer(Modifier.height(8.dp))
                            }
                            MyFakeCompanionRow(fp)
                        }
                    }
                    CompanionMyToolTab.TELEPORTS -> {
                        myTeleports.take(4).forEachIndexed { idx, tp ->
                            if (idx > 0) {
                                Spacer(Modifier.height(8.dp))
                                IgngHairlineDivider()
                                Spacer(Modifier.height(8.dp))
                            }
                            MyTeleportCompanionRow(tp)
                        }
                    }
                    CompanionMyToolTab.LANDS -> {
                        myLands.take(3).forEachIndexed { idx, land ->
                            if (idx > 0) {
                                Spacer(Modifier.height(8.dp))
                                IgngHairlineDivider()
                                Spacer(Modifier.height(8.dp))
                            }
                            MyLandCompanionRow(land = land, onClick = { onEditLand(land) })
                        }
                    }
                }
            }

            // Bottom 1-line per-server vanilla stats strip
            if (serverStats != null) {
                if (availableTabs.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    IgngHairlineDivider()
                    Spacer(Modifier.height(10.dp))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(IgngTheme.radii.xs)
                        .clickable {
                            haptic(IgngHapticKind.Tick)
                            onOpenStats()
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BarChart,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    val playtime = serverStats.playTimeFormatted.ifBlank {
                        IgngFormatters.formatPlaytimeSeconds(serverStats.playTimeSeconds)
                    }
                    Text(
                        text = "本服游玩 $playtime · 挖掘 ${IgngFormatters.formatNumber(serverStats.blocksMined)} · 击败 ${IgngFormatters.formatNumber(serverStats.mobKills)}",
                        style = IgngTheme.type.meta,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MyFakeCompanionRow(fp: PlayerFakePlayerDto) {
    val colors = IgngTheme.colors
    val healthTone = evaluateFakeHealthTone(fp.health)
    val healthBadgeTone = when (healthTone) {
        FakeHealthTone.CRITICAL -> IgngBadgeTone.Danger
        FakeHealthTone.WARNING -> IgngBadgeTone.Warning
        FakeHealthTone.NORMAL -> IgngBadgeTone.Success
    }
    val coord = IgngFormatters.formatCoordinate(fp.x, fp.y, fp.z)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.SmartToy,
            contentDescription = null,
            tint = when (healthTone) {
                FakeHealthTone.CRITICAL -> colors.danger
                FakeHealthTone.WARNING -> colors.warning
                FakeHealthTone.NORMAL -> colors.success
            },
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = fp.fake_name?.ifBlank { "在线假人" } ?: "在线假人",
                    style = IgngTheme.type.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                IgngBadge(
                    text = "血 ${IgngFormatters.formatDecimal(fp.health, 1)} · 饱 ${IgngFormatters.formatDecimal(fp.hunger, 0)}",
                    tone = healthBadgeTone,
                )
            }
            Text(
                text = "${IgngFormatters.worldLabel(fp.world)} ($coord)",
                style = IgngTheme.type.mono,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (coord != "—") {
            MiniCopyIconButton(value = coord, contentLabel = "复制假人坐标")
        }
    }
}

@Composable
private fun MyTeleportCompanionRow(tp: PlayerTeleportDto) {
    val colors = IgngTheme.colors
    val coord = IgngFormatters.formatCoordinate(tp.x, tp.y, tp.z)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = tp.point_name?.ifBlank { "未命名传送点" } ?: "未命名传送点",
                    style = IgngTheme.type.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                IgngBadge(text = IgngFormatters.worldLabel(tp.world))
            }
            Text(
                text = coord,
                style = IgngTheme.type.mono,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (coord != "—") {
            MiniCopyIconButton(value = coord, contentLabel = "复制传送点坐标")
        }
    }
}

@Composable
private fun MyLandCompanionRow(
    land: PlayerLandDto,
    onClick: () -> Unit,
) {
    val colors = IgngTheme.colors
    val coord = IgngFormatters.formatCoordinate(land.x, land.y, land.z)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(IgngTheme.radii.xs)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = land.land_name?.ifBlank { "未命名领地" } ?: "未命名领地",
                    style = IgngTheme.type.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                IgngBadge(
                    text = if (land.is_public) "大厅公开" else "仅自己可见",
                    tone = if (land.is_public) IgngBadgeTone.Success else IgngBadgeTone.Neutral,
                )
            }
            Text(
                text = "${IgngFormatters.worldLabel(land.world)} ($coord)",
                style = IgngTheme.type.mono,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ==================== P1: Server Live Chat Preview Card ====================

@Composable
internal fun ServerChatPreviewCard(
    endpoints: SiteEndpoints,
    chatSource: ChatServerSourceDto,
    recentMessages: List<ChatMessageDto>,
    onOpenChat: () -> Unit,
) {
    val colors = IgngTheme.colors
    val displayMessages = remember(recentMessages) {
        recentMessages
            .filterNot { it.isRecalled }
            .sortedBy { it.createdAt.orEmpty() }
            .takeLast(3)
    }
    val latestTimestamp = displayMessages.lastOrNull()?.createdAt
        ?: chatSource.lastMessage?.createdAt
        ?: chatSource.lastMessageAt

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IgngSectionHeader(
            title = "服内实时聊天",
            subtitle = latestTimestamp?.let { "最新消息 · ${IgngFormatters.formatRelativeTime(it)}" },
            actionLabel = "进入聊天",
            onAction = onOpenChat,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        IgngInteractiveTile(
            onClick = onOpenChat,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
        ) {
            if (displayMessages.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    displayMessages.forEach { msg ->
                        val sender = msg.senderName.ifBlank { "玩家" }
                        val body = cleanMcChatPreview(msg.content).ifBlank {
                            if (msg.attachments.isNotEmpty()) "[附件]" else "…"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            IgngAvatar(
                                name = sender,
                                avatarUrl = endpoints.mcAvatarUrl(sender, size = 48),
                                size = 24.dp,
                            )
                            Text(
                                text = sender,
                                style = IgngTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.textPrimary,
                                maxLines = 1,
                            )
                            Text(
                                text = body,
                                style = IgngTheme.type.caption,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            msg.createdAt?.let { ts ->
                                Text(
                                    text = IgngFormatters.formatRelativeTime(ts),
                                    style = IgngTheme.type.badge,
                                    color = colors.textMuted,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            } else {
                val fallbackSender = chatSource.lastMessage?.sender?.takeIf { it.isNotBlank() }
                val fallbackBody = cleanMcChatPreview(
                    chatSource.lastMessage?.content?.takeIf { it.isNotBlank() } ?: chatSource.lastPreview,
                ).ifBlank { "点击查看该服务器的实时游戏内聊天记录" }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Forum,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = if (fallbackSender != null) "$fallbackSender：$fallbackBody" else fallbackBody,
                        style = IgngTheme.type.caption,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

// ==================== P2: Server World Ecology (Public Lands & Online Fake Players) ====================

@Composable
internal fun ServerEcologyCard(
    serverId: Int,
    publicLands: List<HallLandDto>,
    publicFakes: List<HallFakePlayerDto>,
    onSelectLand: (HallLandDto) -> Unit,
    onOpenAllHall: (tabKey: String) -> Unit,
) {
    val colors = IgngTheme.colors
    if (publicLands.isEmpty() && publicFakes.isEmpty()) return

    val tabs = remember(publicLands.size, publicFakes.size) {
        buildList {
            if (publicLands.isNotEmpty()) add(CompanionEcologyTab.LANDS)
            if (publicFakes.isNotEmpty()) add(CompanionEcologyTab.FAKES)
        }
    }
    var activeTab by remember(serverId, tabs) {
        mutableStateOf(tabs.first())
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IgngSectionHeader(
            title = "本服世界生态",
            actionLabel = "查看全部",
            onAction = {
                onOpenAllHall(if (activeTab == CompanionEcologyTab.FAKES) "fakes" else "lands")
            },
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        IgngTile(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
        ) {
            if (tabs.size > 1) {
                IgngSegmentedTabs(
                    items = tabs,
                    selected = activeTab,
                    labelOf = { tab ->
                        when (tab) {
                            CompanionEcologyTab.LANDS -> "${tab.label} (${publicLands.size})"
                            CompanionEcologyTab.FAKES -> "${tab.label} (${publicFakes.size})"
                        }
                    },
                    onSelect = { activeTab = it },
                )
                Spacer(Modifier.height(12.dp))
            }

            when (activeTab) {
                CompanionEcologyTab.LANDS -> {
                    publicLands.take(3).forEachIndexed { idx, land ->
                        if (idx > 0) {
                            Spacer(Modifier.height(8.dp))
                            IgngHairlineDivider()
                            Spacer(Modifier.height(8.dp))
                        }
                        val coord = IgngFormatters.formatCoordinate(land.center.x, land.center.y, land.center.z)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(IgngTheme.radii.xs)
                                .clickable { onSelectLand(land) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = land.name.ifBlank { "未命名领地" },
                                        style = IgngTheme.type.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    IgngBadge(text = IgngFormatters.worldLabel(land.world))
                                }
                                Text(
                                    text = "领主 ${land.ownerName} · $coord",
                                    style = IgngTheme.type.meta,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (land.description.isNotBlank()) {
                                    Text(
                                        text = land.description,
                                        style = IgngTheme.type.caption,
                                        color = colors.textMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (coord != "—") {
                                MiniCopyIconButton(value = coord, contentLabel = "复制领地坐标")
                            }
                        }
                    }
                }
                CompanionEcologyTab.FAKES -> {
                    publicFakes.take(4).forEachIndexed { idx, fp ->
                        if (idx > 0) {
                            Spacer(Modifier.height(8.dp))
                            IgngHairlineDivider()
                            Spacer(Modifier.height(8.dp))
                        }
                        val coord = IgngFormatters.formatCoordinate(fp.x, fp.y, fp.z)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = fp.name,
                                        style = IgngTheme.type.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    IgngBadge(
                                        text = "在线 ${IgngFormatters.formatPlaytimeSeconds(fp.onlineSeconds)}",
                                        tone = IgngBadgeTone.Success,
                                    )
                                }
                                Text(
                                    text = "召唤者 ${fp.ownerName}" + if (coord != "—") " · ${IgngFormatters.worldLabel(fp.world)} ($coord)" else "",
                                    style = IgngTheme.type.meta,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (coord != "—") {
                                MiniCopyIconButton(value = coord, contentLabel = "复制假人坐标")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== Adaptive Historical Snapshot Gallery (e.g. MOD Seasons) ====================

@Composable
internal fun HistoricalSnapshotGalleryCard(
    endpoints: SiteEndpoints,
    family: ServerFamilyDto,
    snapshotSeasons: List<ServerSeasonDto>,
    onOpenMap: (familySlug: String, seasonSlug: String) -> Unit,
    onOpenFamilyArchive: () -> Unit,
) {
    val colors = IgngTheme.colors
    if (snapshotSeasons.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IgngSectionHeader(
            title = "往期周目地图快照",
            subtitle = "${snapshotSeasons.size} 个历史存档可缩放查看",
            actionLabel = "全部周目",
            onAction = onOpenFamilyArchive,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            snapshotSeasons.take(10).forEach { season ->
                IgngInteractiveTile(
                    onClick = { onOpenMap(family.slug, season.slug) },
                    modifier = Modifier.width(178.dp),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    val urls = remember(season.slug) { endpoints.snapshotMapUrls(season.slug) }
                    var srcIdx by remember(season.slug) { mutableIntStateOf(0) }
                    var failed by remember(season.slug) { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 10f)
                            .clip(IgngTheme.radii.sm)
                            .background(colors.tileSoft)
                            .border(IgngStroke.hairline, colors.line, IgngTheme.radii.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!failed) {
                            AsyncImage(
                                model = urls[srcIdx],
                                contentDescription = season.label,
                                contentScale = ContentScale.Crop,
                                onError = { if (srcIdx < urls.lastIndex) srcIdx++ else failed = true },
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(Icons.Rounded.Map, null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = season.label.ifBlank { season.slug },
                        style = IgngTheme.type.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(season.version, season.startedAt).joinToString(" · ").ifBlank { "历史快照" },
                        style = IgngTheme.type.badge,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ==================== Interactive Multi-Range Server Telemetry Sheet ====================

@Composable
internal fun ServerTelemetryDetailSheet(
    server: PublicServerStatusDto,
    statusRepository: StatusRepository,
    onDismiss: () -> Unit,
) {
    val colors = IgngTheme.colors
    val themeKey = ServerThemeKey.resolve(server.themeKey ?: server.familySlug)
    val visual = serverFamilyVisual(themeKey, colors.isDark)
    var preset by remember(server.id) { mutableStateOf(StatusRangePreset.H24) }

    val (historyState, _) = rememberSwrResource<PublicStatusHistoryResponse>(
        cache = statusRepository.swrCache,
        cacheKey = McCacheKeys.statusHistory(preset, server.id),
        scope = IgngCacheScope.PUBLIC,
        policy = McCachePolicies.Realtime,
        tags = setOf(McCacheTags.STATUS),
        slotKey = "companion_telemetry_sheet_${server.id}",
    ) {
        runSuspendCatching { statusRepository.fetchPublicHistory(preset = preset, serverId = server.id) }
    }
    val series = historyState.data?.seriesFor(server.id)

    IgngDetailSheet(
        onDismiss = onDismiss,
        title = listOfNotNull(server.familyName ?: server.name, server.seasonLabel).joinToString(" · "),
        subtitle = "拖动图表查看逐点遥测数据",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IgngSegmentedTabs(
                items = StatusRangePreset.entries,
                selected = preset,
                labelOf = { it.label },
                onSelect = { preset = it },
            )

            server.address?.takeIf { it.isNotBlank() }?.let { addr ->
                IgngCopyPill(value = addr, label = "连接地址：$addr", modifier = Modifier.fillMaxWidth())
            }

            if (historyState.isInitialLoading && series == null) {
                IgngLoadingBlock(compact = true)
            } else {
                val buckets = remember(series) {
                    series?.statusGrid.orEmpty().map { b ->
                        val level = when (b.status.lowercase()) {
                            "good", "healthy", "online", "ok" -> HealthBucketLevel.Healthy
                            "degraded", "warn", "warning", "stale" -> HealthBucketLevel.Degraded
                            "critical", "down", "offline", "unreachable", "error" -> HealthBucketLevel.Down
                            else -> HealthBucketLevel.NoData
                        }
                        HealthBucketItem(
                            level = level,
                            title = IgngFormatters.formatIsoDateTime(b.start, short = true),
                            detail = b.coverageLabel.ifBlank { b.status },
                        )
                    }
                }
                if (buckets.isNotEmpty()) {
                    IgngStatusHealthStrip(
                        buckets = buckets,
                        startLabel = "${preset.label}前",
                        endLabel = "现在",
                        windowLabel = preset.label,
                    )
                }

                val tpsPoints = remember(series) {
                    series?.performance.orEmpty().mapNotNull { p ->
                        p.avg_tps?.let {
                            IgngChartPoint(
                                label = IgngFormatters.formatIsoDateTime(p.recorded_at, short = true),
                                value = it,
                                detail = p.avg_mspt?.let { ms -> "MSPT ${IgngFormatters.formatDecimal(ms, 1)}ms" }.orEmpty(),
                            )
                        }
                    }
                }
                if (tpsPoints.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        IgngSectionHeader(title = "TPS 曲线")
                        IgngLineChart(
                            points = tpsPoints,
                            lineColor = visual.accent,
                            valueFormatter = { "TPS ${IgngFormatters.formatDecimal(it, 2)}" },
                            height = 160.dp,
                            minY = 0.0,
                            maxY = 20.5,
                            referenceValue = 20.0,
                            referenceLabel = "20.0",
                            axisValueFormatter = { IgngFormatters.formatDecimal(it, 0) },
                        )
                    }
                }

                val pingPoints = remember(series) {
                    series?.latencies.orEmpty().mapNotNull { l ->
                        l.avg_latency_ms?.let {
                            IgngChartPoint(
                                label = IgngFormatters.formatIsoDateTime(l.timestamp_utc, short = true),
                                value = it,
                                detail = l.packet_loss_pct?.let { loss -> "丢包 ${IgngFormatters.formatDecimal(loss, 1)}%" }.orEmpty(),
                            )
                        }
                    }
                }
                val avgPing = remember(pingPoints) {
                    pingPoints.takeIf { it.isNotEmpty() }?.map { it.value }?.average()
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    IgngSectionHeader(title = "网络延迟")
                    IgngLineChart(
                        points = pingPoints,
                        lineColor = colors.accent,
                        valueFormatter = { "${IgngFormatters.formatDecimal(it, 1)} ms" },
                        height = 160.dp,
                        referenceValue = avgPing,
                        referenceLabel = avgPing?.let { "均值 ${it.toInt()}ms" },
                        axisValueFormatter = { "${it.toInt()}ms" },
                    )
                }

                val playerPoints = remember(series) {
                    series?.performance.orEmpty().mapNotNull { p ->
                        p.online_players?.let {
                            IgngChartPoint(
                                label = IgngFormatters.formatIsoDateTime(p.recorded_at, short = true),
                                value = it.toDouble(),
                            )
                        }
                    }
                }
                if (playerPoints.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        IgngSectionHeader(title = "在线玩家数")
                        IgngLineChart(
                            points = playerPoints,
                            lineColor = colors.success,
                            valueFormatter = { "${it.toInt()} 人" },
                            height = 152.dp,
                            minY = 0.0,
                            axisValueFormatter = { "${it.toInt()}" },
                        )
                    }
                }
            }
        }
    }
}

// ==================== Quick Detail Sheets (Public Land / My Land Edit / Server Guide) ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PublicLandDetailSheet(
    land: HallLandDto,
    onDismiss: () -> Unit,
) {
    val colors = IgngTheme.colors
    IgngDetailSheet(
        onDismiss = onDismiss,
        title = land.name.ifBlank { "领地详情" },
        subtitle = "${land.serverName} · ${IgngFormatters.worldLabel(land.world)} · 领主 ${land.ownerName}",
    ) {
        val coord = IgngFormatters.formatCoordinate(land.center.x, land.center.y, land.center.z)
        IgngCopyPill(value = coord, label = "中心坐标：$coord", modifier = Modifier.fillMaxWidth())
        if (land.description.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(land.description, style = IgngTheme.type.body, color = colors.textSecondary)
        }
        if (land.playerPermissions.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("访客权限", style = IgngTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                land.playerPermissions.entries.take(16).forEach { (k, v) ->
                    IgngBadge(k, tone = if (v.asBooleanFlag()) IgngBadgeTone.Success else IgngBadgeTone.Neutral)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickMyLandEditSheet(
    land: PlayerLandDto,
    playerRepository: PlayerRepository,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = IgngTheme.colors
    val scope = rememberCoroutineScope()
    var currentPublic by remember(land.id) { mutableStateOf(land.is_public) }
    var descDraft by remember(land.id) { mutableStateOf(land.description) }
    var saving by remember(land.id) { mutableStateOf(false) }

    IgngDetailSheet(
        onDismiss = { if (!saving) onDismiss() },
        title = land.land_name ?: "我的领地",
        subtitle = "${land.server_name ?: ""} · ${IgngFormatters.worldLabel(land.world)} · 领主 ${land.owner_name ?: "—"}",
    ) {
        val coord = IgngFormatters.formatCoordinate(land.x, land.y, land.z)
        IgngCopyPill(value = coord, label = "中心坐标：$coord", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        if (land.can_edit) {
            IgngSwitchRow(
                title = "在本服公开展示",
                checked = currentPublic,
                onCheckedChange = { next ->
                    scope.launch {
                        saving = true
                        runCatching { playerRepository.updateLand(land.id, isPublic = next) }
                            .onSuccess {
                                currentPublic = next
                                onSaved()
                            }
                        saving = false
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            IgngTextField(
                value = descDraft,
                onValueChange = { descDraft = it },
                label = "领地介绍",
                singleLine = false,
                minLines = 3,
            )
            Spacer(Modifier.height(10.dp))
            IgngButton(
                text = "保存领地介绍",
                loading = saving,
                icon = Icons.Rounded.Edit,
                onClick = {
                    scope.launch {
                        saving = true
                        runCatching { playerRepository.updateLand(land.id, description = descDraft.trim()) }
                            .onSuccess {
                                onSaved()
                                onDismiss()
                            }
                        saving = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (land.members.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("领地成员（${land.members.size}）", style = IgngTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                land.members.forEach { m -> IgngBadge(m.name ?: m.uuid?.take(8) ?: "成员") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ServerGuideDetailSheet(
    family: ServerFamilyDto,
    season: ServerSeasonDto,
    onDismiss: () -> Unit,
) {
    val colors = IgngTheme.colors
    val context = LocalContext.current

    IgngDetailSheet(
        onDismiss = onDismiss,
        title = listOfNotNull(family.name.takeIf { it.isNotBlank() }, season.label.takeIf { it.isNotBlank() }).joinToString(" · "),
        subtitle = "游玩规则、世界维度与配置档案",
        scrollable = true,
    ) {
        val desc = season.description.ifBlank { family.description }
        if (desc.isNotBlank()) {
            Text(desc, style = IgngTheme.type.body, color = colors.textSecondary)
            Spacer(Modifier.height(12.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            season.version?.takeIf { it.isNotBlank() }?.let { IgngBadge("版本 $it") }
            season.core?.takeIf { it.isNotBlank() }?.let { IgngBadge("核心 $it") }
            season.modpack?.takeIf { it.isNotBlank() }?.let { IgngBadge("整合包 $it", tone = IgngBadgeTone.Accent) }
            season.startedAt?.takeIf { it.isNotBlank() }?.let { IgngBadge("开启 $it") }
            season.cpuSpec?.takeIf { it.isNotBlank() }?.let { IgngBadge(it) }
            season.memorySpec?.takeIf { it.isNotBlank() }?.let { IgngBadge(it) }
        }
        season.seed?.takeIf { it.isNotBlank() }?.let { seed ->
            Spacer(Modifier.height(10.dp))
            IgngCopyPill(value = seed, label = "地图种子：$seed", modifier = Modifier.fillMaxWidth())
        }
        if (season.worlds.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = season.command?.takeIf { it.isNotBlank() }?.let { "世界维度（$it）" } ?: "世界维度",
                style = IgngTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            season.worlds.forEach { w ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("· ${w.displayLabel}", style = IgngTheme.type.caption, color = colors.textSecondary)
                    if (w.world.isNotBlank() && w.world != w.displayLabel) {
                        Text(w.world, style = IgngTheme.type.mono, color = colors.textMuted)
                    }
                }
            }
        }
        if (season.highlights.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("玩法亮点", style = IgngTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            season.highlights.forEach { hl ->
                Text("· $hl", style = IgngTheme.type.caption, color = colors.textSecondary, modifier = Modifier.padding(vertical = 1.dp))
            }
        }
        if (season.rules.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("游玩规则", style = IgngTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            season.rules.forEach { rule ->
                Text("· $rule", style = IgngTheme.type.caption, color = colors.textSecondary, modifier = Modifier.padding(vertical = 1.dp))
            }
        }
        if (season.plugins.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("特色插件与链接", style = IgngTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                season.plugins.forEach { plugin ->
                    val title = plugin.displayTitle.ifBlank { return@forEach }
                    val label = if (!plugin.value.isNullOrBlank() && plugin.label.isNotBlank()) {
                        "${plugin.label}：${plugin.value}"
                    } else {
                        title
                    }
                    val href = plugin.href?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
                    if (href != null) {
                        Row(
                            modifier = Modifier.clickable { IgngBrowserLauncher.openExternalUrl(context, href, colors.isDark) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            IgngBadge(text = label, tone = IgngBadgeTone.Accent)
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = colors.accent, modifier = Modifier.size(12.dp))
                        }
                    } else {
                        IgngBadge(text = label)
                    }
                }
            }
        }
    }
}

// ==================== Micro Interactive Helpers ====================

@Composable
internal fun MiniCopyIconButton(
    value: String,
    contentLabel: String,
) {
    val context = LocalContext.current
    val colors = IgngTheme.colors
    val intensity = IgngTheme.motionIntensity
    val haptic = rememberIgngHaptics()
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500L)
            copied = false
        }
    }

    val bg by animateColorAsState(
        targetValue = if (copied) colors.successBg else colors.tileSoft,
        animationSpec = IgngMotion.microTween(intensity),
        label = "miniCopyBg",
    )
    val border by animateColorAsState(
        targetValue = if (copied) colors.success else colors.line,
        animationSpec = IgngMotion.microTween(intensity),
        label = "miniCopyBorder",
    )

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(IgngTheme.radii.xs)
            .background(bg)
            .border(IgngStroke.hairline, border, IgngTheme.radii.xs)
            .clickable {
                copyTextToClipboard(context, contentLabel, value)
                haptic(IgngHapticKind.Confirm)
                copied = true
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = copied,
            transitionSpec = {
                (fadeIn(IgngMotion.microTween(intensity)) + scaleIn(IgngMotion.microTween(intensity), initialScale = 0.72f)) togetherWith
                    (fadeOut(IgngMotion.microTween(intensity)) + scaleOut(IgngMotion.microTween(intensity), targetScale = 0.72f))
            },
            label = "miniCopyIcon",
        ) { isCopied ->
            Icon(
                imageVector = if (isCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                contentDescription = contentLabel,
                tint = if (isCopied) colors.success else colors.textSecondary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun CompactCopyChip(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val context = LocalContext.current
    val colors = IgngTheme.colors
    val intensity = IgngTheme.motionIntensity
    val haptic = rememberIgngHaptics()
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1600L)
            copied = false
        }
    }

    val bg by animateColorAsState(
        targetValue = if (copied) colors.successBg else colors.tileSoft,
        animationSpec = IgngMotion.microTween(intensity),
        label = "cmdCopyBg",
    )
    val border by animateColorAsState(
        targetValue = if (copied) colors.success else colors.line,
        animationSpec = IgngMotion.microTween(intensity),
        label = "cmdCopyBorder",
    )

    Row(
        modifier = Modifier
            .clip(IgngTheme.radii.sm)
            .background(bg)
            .border(IgngStroke.hairline, border, IgngTheme.radii.sm)
            .clickable {
                copyTextToClipboard(context, "切服指令", value)
                haptic(IgngHapticKind.Confirm)
                copied = true
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = if (copied) Icons.Rounded.Check else icon,
            contentDescription = "复制指令",
            tint = if (copied) colors.success else colors.textSecondary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = if (copied) "已复制" else label,
            style = IgngTheme.type.mono,
            color = if (copied) colors.success else colors.textPrimary,
            maxLines = 1,
        )
    }
}
