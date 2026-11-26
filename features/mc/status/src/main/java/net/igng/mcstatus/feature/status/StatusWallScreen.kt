package net.igng.mcstatus.feature.status

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import net.igng.core.data.IgngCacheScope
import net.igng.core.data.rememberScreenHolder
import net.igng.core.data.rememberSwrResource
import net.igng.core.data.runSuspendCatching
import net.igng.mcstatus.core.data.McCacheKeys
import net.igng.mcstatus.core.data.McCachePolicies
import net.igng.mcstatus.core.data.McCacheTags
import net.igng.mcstatus.core.data.McDiskCodecs
import net.igng.mcstatus.core.data.PublicServerStatusDto
import net.igng.mcstatus.core.data.PublicStatusHistoryResponse
import net.igng.mcstatus.core.data.PublicStatusResponse
import net.igng.mcstatus.core.data.ServerHistorySeriesDto
import net.igng.mcstatus.core.data.StatusRangePreset
import net.igng.mcstatus.core.data.StatusRepository
import net.igng.core.designsystem.HealthBucketItem
import net.igng.core.designsystem.HealthBucketLevel
import net.igng.core.designsystem.IgngAmbientCanvas
import net.igng.core.designsystem.IgngAnimatedCounter
import net.igng.core.designsystem.IgngBadge
import net.igng.core.designsystem.IgngBadgeTone
import net.igng.core.designsystem.IgngChartPoint
import net.igng.core.designsystem.IgngCopyPill
import net.igng.core.designsystem.IgngDetailSheet
import net.igng.core.designsystem.IgngInteractiveTile
import net.igng.core.designsystem.IgngLineChart
import net.igng.core.designsystem.IgngPullToRefreshBox
import net.igng.core.designsystem.IgngSectionHeader
import net.igng.core.designsystem.IgngSegmentedTabs
import net.igng.core.designsystem.IgngSkeletonRows
import net.igng.core.designsystem.IgngStateBlock
import net.igng.core.designsystem.IgngStatusHealthStrip
import net.igng.core.designsystem.IgngTheme
import net.igng.core.designsystem.IgngTile
import net.igng.core.designsystem.IgngTopBar
import net.igng.core.designsystem.rememberIgngPageContentPadding
import net.igng.mcstatus.common.serverFamilyVisual
import net.igng.mcstatus.core.model.IgngFormatters
import net.igng.mcstatus.core.model.ServerThemeKey

private class StatusWallUiHolder {
    var preset by mutableStateOf(StatusRangePreset.H24)
}

@Composable
fun StatusWallScreen(
    statusRepository: StatusRepository,
    onBack: () -> Unit,
) {
    val colors = IgngTheme.colors
    val uiHolder = rememberScreenHolder("status_wall_ui") { StatusWallUiHolder() }
    var inspectedServer by remember { mutableStateOf<PublicServerStatusDto?>(null) }

    val (statusState, statusHandle) = rememberSwrResource<PublicStatusResponse>(
        cache = statusRepository.swrCache,
        cacheKey = McCacheKeys.STATUS,
        scope = IgngCacheScope.PUBLIC,
        policy = McCachePolicies.Status,
        tags = setOf(McCacheTags.STATUS),
        diskCodec = McDiskCodecs.status,
        pollIntervalMs = 30_000L,
        slotKey = "status_wall_live",
    ) {
        runSuspendCatching { statusRepository.fetchPublicStatus() }
    }

    val (historyState, historyHandle) = rememberSwrResource<PublicStatusHistoryResponse>(
        cache = statusRepository.swrCache,
        cacheKey = McCacheKeys.statusHistory(uiHolder.preset),
        scope = IgngCacheScope.PUBLIC,
        policy = McCachePolicies.Realtime,
        tags = setOf(McCacheTags.STATUS),
        slotKey = "status_wall_history",
    ) {
        runSuspendCatching { statusRepository.fetchPublicHistory(uiHolder.preset) }
    }

    val statusResponse = statusState.data
    val historyResponse = historyState.data
    val servers = statusResponse?.servers.orEmpty()
    val loading = statusState.isInitialLoading && servers.isEmpty()
    val refreshing = statusState.isUserRefreshing || historyState.isUserRefreshing
    val error = if (servers.isEmpty()) statusState.error else null
    val onlineCount = servers.count { it.online }
    val totalPlayers = servers.sumOf { it.onlinePlayers ?: 0 }

    IgngAmbientCanvas {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            IgngTopBar(
                title = "服务器状态",
                subtitle = statusResponse?.generatedAt?.let { "更新于 ${IgngFormatters.formatRelativeTime(it)}" },
                onBack = onBack,
            )

            IgngPullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    statusHandle.refresh()
                    historyHandle.refresh()
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    loading && servers.isEmpty() -> Box(Modifier.padding(16.dp)) { IgngSkeletonRows(4) }
                    error != null && servers.isEmpty() -> Box(Modifier.padding(16.dp)) {
                        IgngStateBlock(
                            title = "服务器状态没有读到",
                            description = error,
                            isError = true,
                            actionLabel = "重试",
                            onAction = {
                                statusHandle.refresh()
                                historyHandle.refresh()
                            },
                        )
                    }
                    else -> LazyColumn(
                        contentPadding = rememberIgngPageContentPadding(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item(key = "overview") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatusOverviewCard("存活周目", "$onlineCount / ${servers.size}", Modifier.weight(1f))
                                StatusOverviewCard("在线玩家", IgngFormatters.formatNumber(totalPlayers), Modifier.weight(1f))
                                val avgTps = servers.mapNotNull { it.tps }.takeIf { it.isNotEmpty() }?.average()
                                StatusOverviewCard("平均 TPS", IgngFormatters.formatDecimal(avgTps, 1), Modifier.weight(1f))
                            }
                        }

                        item(key = "range") {
                            IgngSegmentedTabs(
                                items = StatusRangePreset.entries,
                                selected = uiHolder.preset,
                                labelOf = { it.label },
                                onSelect = { uiHolder.preset = it },
                            )
                        }

                        items(servers, key = { it.id }) { server ->
                            val series = historyResponse?.seriesFor(server.id)
                            ServerStatusCard(
                                server = server,
                                series = series,
                                preset = uiHolder.preset,
                                onClick = { inspectedServer = server },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    inspectedServer?.let { server ->
        val series = historyResponse?.seriesFor(server.id)
        ServerDetailBottomSheet(
            server = server,
            series = series,
            preset = uiHolder.preset,
            onDismiss = { inspectedServer = null },
        )
    }
}

@Composable
private fun StatusOverviewCard(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = IgngTheme.colors
    IgngTile(modifier = modifier, contentPadding = PaddingValues(12.dp)) {
        Text(label, style = IgngTheme.type.badge, color = colors.textSecondary)
        Spacer(Modifier.height(4.dp))
        IgngAnimatedCounter(text = value, style = IgngTheme.type.metricMedium, color = colors.textPrimary)
    }
}

@Composable
private fun ServerStatusCard(
    server: PublicServerStatusDto,
    series: ServerHistorySeriesDto?,
    preset: StatusRangePreset,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IgngTheme.colors
    val themeKey = ServerThemeKey.resolve(server.themeKey ?: server.familySlug)
    val visual = serverFamilyVisual(themeKey, colors.isDark)

    IgngInteractiveTile(
        onClick = onClick,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(22.dp)
                        .clip(IgngTheme.radii.pill)
                        .background(visual.accent),
                )
                Column {
                    Text(
                        text = buildString {
                            append(server.familyName ?: server.name)
                            if (!server.seasonLabel.isNullOrBlank()) {
                                append(" · ")
                                append(server.seasonLabel)
                            }
                        },
                        style = IgngTheme.type.cardTitle,
                        color = colors.textPrimary,
                    )
                    if (!server.version.isNullOrBlank()) {
                        Text(
                            listOfNotNull(server.version, server.core).joinToString(" · "),
                            style = IgngTheme.type.badge,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
            IgngBadge(
                text = when (server.status) {
                    "online" -> "在线"
                    "degraded" -> "波动"
                    "offline" -> "离线"
                    else -> "未知"
                },
                tone = when (server.status) {
                    "online" -> IgngBadgeTone.Success
                    "degraded" -> IgngBadgeTone.Warning
                    "offline" -> IgngBadgeTone.Danger
                    else -> IgngBadgeTone.Neutral
                },
                leadingDot = true,
                pulse = server.online,
            )
        }

        Spacer(Modifier.height(12.dp))
        val onlinePlayers = server.onlinePlayers
        val maxPlayers = server.maxPlayers
        val playerLabel = when {
            onlinePlayers != null && maxPlayers != null && maxPlayers > 0 ->
                "${IgngFormatters.formatNumber(onlinePlayers)}/${IgngFormatters.formatNumber(maxPlayers)}"
            else -> IgngFormatters.formatNumber(onlinePlayers)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricInline("玩家", playerLabel, Modifier.weight(1f))
            MetricInline("TPS", IgngFormatters.formatDecimal(server.tps, 1), Modifier.weight(1f))
            MetricInline("延迟", IgngFormatters.formatDecimal(server.pingMs, 0, "ms"), Modifier.weight(1f))
            MetricInline("内存", server.memoryUsageMb?.let { "${it.toInt()}M" } ?: "—", Modifier.weight(1f))
        }

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
            Spacer(Modifier.height(12.dp))
            IgngStatusHealthStrip(
                buckets = buckets,
                startLabel = "${preset.label}前",
                endLabel = "现在",
                windowLabel = preset.label,
            )
        }
    }
}

@Composable
private fun MetricInline(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = IgngTheme.colors
    Column(modifier = modifier) {
        Text(label, style = IgngTheme.type.badge, color = colors.textMuted)
        Text(value, style = IgngTheme.type.mono, color = colors.textPrimary)
    }
}

@Composable
private fun ServerDetailBottomSheet(
    server: PublicServerStatusDto,
    series: ServerHistorySeriesDto?,
    preset: StatusRangePreset,
    onDismiss: () -> Unit,
) {
    val colors = IgngTheme.colors
    val themeKey = ServerThemeKey.resolve(server.themeKey ?: server.familySlug)
    val visual = serverFamilyVisual(themeKey, colors.isDark)

    IgngDetailSheet(
        onDismiss = onDismiss,
        title = listOfNotNull(server.familyName ?: server.name, server.seasonLabel).joinToString(" · "),
        subtitle = "时间窗口：${preset.label}（手指拖动图表查看逐点数据）",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            server.address?.takeIf { it.isNotBlank() }?.let { addr ->
                IgngCopyPill(value = addr, label = "连接地址：$addr", modifier = Modifier.fillMaxWidth())
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
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IgngSectionHeader(title = "TPS 曲线")
                IgngLineChart(
                    points = tpsPoints,
                    lineColor = visual.accent,
                    valueFormatter = { "TPS ${IgngFormatters.formatDecimal(it, 2)}" },
                    height = 172.dp,
                    minY = 0.0,
                    maxY = 20.5,
                    referenceValue = 20.0,
                    referenceLabel = "20.0",
                    axisValueFormatter = { IgngFormatters.formatDecimal(it, 0) },
                )
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
                    height = 172.dp,
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
                        height = 164.dp,
                        minY = 0.0,
                        axisValueFormatter = { "${it.toInt()}" },
                    )
                }
            } else {
                val cpuPoints = remember(series) {
                    series?.performance.orEmpty().mapNotNull { p ->
                        p.cpu_usage?.let {
                            IgngChartPoint(
                                label = IgngFormatters.formatIsoDateTime(p.recorded_at, short = true),
                                value = it,
                                detail = p.memory_usage_mb?.let { m -> "内存 ${m.toInt()} MB" }.orEmpty(),
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    IgngSectionHeader(title = "CPU 负载")
                    IgngLineChart(
                        points = cpuPoints,
                        lineColor = colors.success,
                        valueFormatter = { "${IgngFormatters.formatDecimal(it, 1)}%" },
                        height = 164.dp,
                        minY = 0.0,
                        axisValueFormatter = { "${it.toInt()}%" },
                    )
                }
            }
        }
    }
}
