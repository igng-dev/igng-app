package net.igng.mcstatus.feature.admin

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import net.igng.core.data.IgngCacheScope
import net.igng.core.data.rememberScreenHolder
import net.igng.core.data.rememberSwrResource
import net.igng.core.data.runSuspendCatching
import net.igng.core.designsystem.IgngBrowserLauncher
import net.igng.core.designsystem.IgngPullToRefreshBox
import net.igng.core.markdown.IgngMarkdownDocumentView
import net.igng.core.markdown.MarkdownParser
import net.igng.mcstatus.core.data.AdminCurrentNodesResponse
import net.igng.mcstatus.core.data.AdminPlayerResultDto
import net.igng.mcstatus.core.data.AdminPunishmentsResponse
import net.igng.mcstatus.core.data.AdminRepository
import net.igng.mcstatus.core.data.HistorianDetailResponse
import net.igng.mcstatus.core.data.HistorianListResponse
import net.igng.mcstatus.core.data.McCacheKeys
import net.igng.mcstatus.core.data.McCachePolicies
import net.igng.mcstatus.core.data.McCacheTags
import net.igng.mcstatus.core.data.TicketDetailDto
import net.igng.mcstatus.core.data.TicketSummaryDto
import net.igng.mcstatus.core.data.TrafficAdminResponse
import net.igng.core.designsystem.IgngAmbientCanvas
import net.igng.core.designsystem.IgngBadge
import net.igng.core.designsystem.IgngBadgeTone
import net.igng.core.designsystem.IgngBarMetric
import net.igng.core.designsystem.IgngButton
import net.igng.core.designsystem.IgngButtonStyle
import net.igng.core.designsystem.IgngDetailSheet
import net.igng.core.designsystem.IgngHorizontalBarList
import net.igng.core.designsystem.IgngInteractiveTile
import net.igng.core.designsystem.IgngLoadingBlock
import net.igng.core.designsystem.IgngSearchField
import net.igng.core.designsystem.IgngSectionHeader
import net.igng.core.designsystem.IgngSegmentedTabs
import net.igng.core.designsystem.IgngSkeletonRows
import net.igng.core.designsystem.IgngStateBlock
import net.igng.core.designsystem.IgngTextField
import net.igng.core.designsystem.IgngTheme
import net.igng.core.designsystem.IgngTile
import net.igng.core.designsystem.IgngTopBar
import net.igng.core.designsystem.rememberIgngPageContentPadding
import net.igng.mcstatus.core.model.IgngFormatters
import net.igng.mcstatus.core.model.McAdminRole
import net.igng.mcstatus.core.model.SiteEndpoints

private enum class AdminSection(val label: String, val elevatedOnly: Boolean = false) {
    TICKETS("工单"),
    AUDIT("玩家审计"),
    HISTORIAN("观察周报"),
    OPS("运维流量", elevatedOnly = true),
}

private data class AdminOpsBundle(
    val nodes: AdminCurrentNodesResponse?,
    val traffic: TrafficAdminResponse?,
)

private class AdminPocketUiHolder {
    var section by mutableStateOf(AdminSection.TICKETS)
    var playerQuery by mutableStateOf("")
    var submittedPlayerQuery by mutableStateOf("")
    var playerResults by mutableStateOf<List<AdminPlayerResultDto>>(emptyList())
    var trafficRangeHours by mutableStateOf(24L)
}

@Composable
fun AdminPocketConsoleScreen(
    role: McAdminRole,
    endpoints: SiteEndpoints,
    adminRepository: AdminRepository,
    onBack: () -> Unit,
) {
    val colors = IgngTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiHolder = rememberScreenHolder("mc_admin_pocket_ui") { AdminPocketUiHolder() }

    val availableSections = remember(role) {
        AdminSection.entries.filter { !it.elevatedOnly || role.isElevated() }
    }
    var inspectedTicket by remember { mutableStateOf<TicketDetailDto?>(null) }
    var adminReplyDraft by remember { mutableStateOf("") }
    var historianDetail by remember { mutableStateOf<HistorianDetailResponse?>(null) }
    var loadingHistorianDetail by remember { mutableStateOf(false) }

    val (ticketsState, ticketsHandle) = rememberSwrResource<List<TicketSummaryDto>>(
        cache = adminRepository.swrCache,
        cacheKey = McCacheKeys.ADMIN_TICKETS,
        scope = IgngCacheScope.USER_ONLY,
        policy = McCachePolicies.Standard,
        tags = setOf(McCacheTags.ADMIN),
        enabled = uiHolder.section == AdminSection.TICKETS,
        slotKey = "mc_admin_tickets",
    ) { runSuspendCatching { adminRepository.fetchAdminTickets(includeClosed = false).resolvedTickets } }

    val (punishState, punishHandle) = rememberSwrResource<AdminPunishmentsResponse>(
        cache = adminRepository.swrCache,
        cacheKey = McCacheKeys.adminPunishments(uiHolder.submittedPlayerQuery),
        scope = IgngCacheScope.USER_ONLY,
        policy = McCachePolicies.Standard,
        tags = setOf(McCacheTags.ADMIN),
        enabled = uiHolder.section == AdminSection.AUDIT,
        slotKey = "mc_admin_punishments",
    ) { runSuspendCatching { adminRepository.fetchRecentPunishments(search = uiHolder.submittedPlayerQuery) } }

    val (historianState, historianHandle) = rememberSwrResource<HistorianListResponse>(
        cache = adminRepository.swrCache,
        cacheKey = McCacheKeys.ADMIN_HISTORIAN,
        scope = IgngCacheScope.USER_ONLY,
        policy = McCachePolicies.Detail,
        tags = setOf(McCacheTags.ADMIN),
        enabled = uiHolder.section == AdminSection.HISTORIAN,
        slotKey = "mc_admin_historian",
    ) { runSuspendCatching { adminRepository.fetchHistorianReports() } }

    val (opsState, opsHandle) = rememberSwrResource<AdminOpsBundle>(
        cache = adminRepository.swrCache,
        cacheKey = McCacheKeys.adminOps(uiHolder.trafficRangeHours),
        scope = IgngCacheScope.USER_ONLY,
        policy = McCachePolicies.Realtime,
        tags = setOf(McCacheTags.ADMIN),
        enabled = uiHolder.section == AdminSection.OPS,
        pollIntervalMs = 30_000L,
        slotKey = "mc_admin_ops",
    ) {
        runSuspendCatching {
            coroutineScope {
                val nDef = async { runSuspendCatching { adminRepository.fetchAdminCurrentNodes() }.getOrNull() }
                val tDef = async { runSuspendCatching { adminRepository.fetchAdminTraffic(rangeHours = uiHolder.trafficRangeHours) }.getOrNull() }
                AdminOpsBundle(nodes = nDef.await(), traffic = tDef.await())
            }
        }
    }

    val adminTickets = ticketsState.data.orEmpty()
    val punishments = punishState.data
    val historianList = historianState.data
    val nodesData = opsState.data?.nodes
    val trafficData = opsState.data?.traffic

    val loading = when (uiHolder.section) {
        AdminSection.TICKETS -> ticketsState.isInitialLoading
        AdminSection.AUDIT -> punishState.isInitialLoading
        AdminSection.HISTORIAN -> historianState.isInitialLoading
        AdminSection.OPS -> opsState.isInitialLoading
    }
    val refreshing = when (uiHolder.section) {
        AdminSection.TICKETS -> ticketsState.isUserRefreshing
        AdminSection.AUDIT -> punishState.isUserRefreshing
        AdminSection.HISTORIAN -> historianState.isUserRefreshing
        AdminSection.OPS -> opsState.isUserRefreshing
    }
    val error = when (uiHolder.section) {
        AdminSection.TICKETS -> ticketsState.error
        AdminSection.AUDIT -> punishState.error
        AdminSection.HISTORIAN -> historianState.error
        AdminSection.OPS -> opsState.error
    }

    fun loadSection(userInitiated: Boolean = false) {
        when (uiHolder.section) {
            AdminSection.TICKETS -> if (userInitiated) ticketsHandle.refresh() else ticketsHandle.silentRefresh()
            AdminSection.AUDIT -> if (userInitiated) punishHandle.refresh() else punishHandle.silentRefresh()
            AdminSection.HISTORIAN -> if (userInitiated) historianHandle.refresh() else historianHandle.silentRefresh()
            AdminSection.OPS -> if (userInitiated) opsHandle.refresh() else opsHandle.silentRefresh()
        }
    }

    IgngAmbientCanvas {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            IgngTopBar(
                title = "管理控制台",
                subtitle = "${role.label} · 移动端高频运维",
                onBack = onBack,
                actions = {
                    IgngButton(
                        text = "网页版",
                        onClick = { IgngBrowserLauncher.openExternalUrl(context, "${endpoints.dash}/mc/admin", colors.isDark) },
                        style = IgngButtonStyle.Secondary,
                        icon = Icons.Rounded.OpenInBrowser,
                        compact = true,
                    )
                },
            )

            Box(Modifier.padding(horizontal = 16.dp)) {
                IgngSegmentedTabs(availableSections, uiHolder.section, { it.label }, { uiHolder.section = it })
            }
            Spacer(Modifier.height(10.dp))

            IgngPullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { loadSection(userInitiated = true) },
                modifier = Modifier.weight(1f),
            ) {
                when {
                    loading -> Box(Modifier.padding(16.dp)) { IgngSkeletonRows(4) }
                    error != null -> Box(Modifier.padding(16.dp)) {
                        IgngStateBlock(title = "读取失败", description = error, isError = true, actionLabel = "重试", onAction = { loadSection(userInitiated = true) })
                    }
                    uiHolder.section == AdminSection.TICKETS -> LazyColumn(contentPadding = rememberIgngPageContentPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (adminTickets.isEmpty()) {
                            item { IgngStateBlock(title = "一切正常", description = "当前没有待处理的全站工单。") }
                        } else {
                            itemsIndexed(adminTickets, key = { _, t -> t.id }) { idx, t ->
                                IgngInteractiveTile(
                                    onClick = {
                                        scope.launch {
                                            adminReplyDraft = ""
                                            inspectedTicket = runCatching { adminRepository.fetchAdminTicketDetail(t.id) }.getOrNull()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("#${t.id} ${t.title}", style = IgngTheme.type.cardTitle, color = colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Spacer(Modifier.width(8.dp))
                                        IgngBadge(t.typeLabel.ifBlank { t.type }, tone = IgngBadgeTone.Accent)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text("${t.creatorName} · ${t.serverName ?: "全服"} · ${IgngFormatters.formatRelativeTime(t.updatedAt)}", style = IgngTheme.type.caption, color = colors.textSecondary)
                                }
                            }
                        }
                    }
                    uiHolder.section == AdminSection.AUDIT -> LazyColumn(contentPadding = rememberIgngPageContentPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            IgngSearchField(
                                value = uiHolder.playerQuery,
                                onValueChange = {
                                    uiHolder.playerQuery = it
                                    if (it.isBlank()) {
                                        uiHolder.submittedPlayerQuery = ""
                                        uiHolder.playerResults = emptyList()
                                    }
                                },
                                placeholder = "输入玩家名或账号搜索审计记录，回车查询",
                                onSubmit = {
                                    val next = uiHolder.playerQuery.trim()
                                    if (next == uiHolder.submittedPlayerQuery) punishHandle.refresh() else uiHolder.submittedPlayerQuery = next
                                    scope.launch {
                                        if (next.length >= 2) {
                                            uiHolder.playerResults = runCatching { adminRepository.searchAdminPlayers(next) }.getOrDefault(emptyList())
                                        }
                                    }
                                },
                            )
                        }
                        if (uiHolder.playerResults.isNotEmpty()) {
                            item { IgngSectionHeader(title = "匹配玩家") }
                            items(uiHolder.playerResults, key = { "${it.kind}:${it.igngId}:${it.mcUsername}:${it.displayName}" }) { p ->
                                IgngTile(modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(p.mcUsername ?: p.displayName ?: "玩家", style = IgngTheme.type.cardTitle, color = colors.textPrimary)
                                        IgngBadge(
                                            text = if (p.hasBoundMc) "已绑定" else if (p.kind == "igng") "IGNG 账户" else "MC 角色",
                                            tone = if (p.hasBoundMc) IgngBadgeTone.Success else IgngBadgeTone.Neutral,
                                        )
                                    }
                                    val detailLine = buildList {
                                        p.displayName?.takeIf { it != p.mcUsername }?.let { add("昵称：$it") }
                                        if (p.playTimeFormatted.isNotBlank()) add("游玩 ${p.playTimeFormatted}")
                                        if (p.medalCount > 0) add("勋章 ${p.medalCount}")
                                    }.joinToString(" · ")
                                    if (detailLine.isNotBlank()) {
                                        Text(detailLine, style = IgngTheme.type.caption, color = colors.textSecondary)
                                    }
                                }
                            }
                        }
                        item { IgngSectionHeader(title = "近期处罚记录", subtitle = punishments?.let { "共 ${it.total} 条" }) }
                        val recs = punishments?.records.orEmpty()
                        if (recs.isEmpty()) {
                            item { IgngStateBlock(title = "没有匹配的处罚记录") }
                        } else {
                            itemsIndexed(recs, key = { i, r -> "${r.id}:$i" }) { _, r ->
                                IgngTile(modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(r.player_name ?: "未知玩家", style = IgngTheme.type.cardTitle, color = colors.textPrimary)
                                        IgngBadge(r.type ?: "处罚", tone = IgngBadgeTone.Warning)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(r.reason ?: "无理由说明", style = IgngTheme.type.body, color = colors.textSecondary)
                                    Text("执行人：${r.admin_name ?: "系统"} · ${IgngFormatters.formatIsoDateTime(r.started_at, short = true)}", style = IgngTheme.type.badge, color = colors.textMuted)
                                }
                            }
                        }
                    }
                    uiHolder.section == AdminSection.HISTORIAN -> LazyColumn(contentPadding = rememberIgngPageContentPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val serverNames = historianList?.servers.orEmpty().associate { it.serverId to it.serverName }
                        val reports = historianList?.items.orEmpty()
                        if (reports.isEmpty()) {
                            item { IgngStateBlock(title = "还没有服务器观察周报") }
                        } else {
                            itemsIndexed(reports, key = { _, r -> r.id }) { idx, report ->
                                IgngInteractiveTile(
                                    onClick = {
                                        scope.launch {
                                            loadingHistorianDetail = true
                                            historianDetail = runCatching { adminRepository.fetchHistorianDetail(report.id) }.getOrNull()
                                            loadingHistorianDetail = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            text = "${serverNames[report.serverId] ?: report.serverId} · ${report.periodStart}",
                                            style = IgngTheme.type.cardTitle,
                                            color = colors.textPrimary,
                                        )
                                        IgngBadge(report.status ?: report.periodKind, tone = if (report.status == "succeeded" || report.status == "completed") IgngBadgeTone.Success else IgngBadgeTone.Neutral)
                                    }
                                    report.title?.takeIf { it.isNotBlank() }?.let { titleText ->
                                        Spacer(Modifier.height(4.dp))
                                        Text(titleText, style = IgngTheme.type.body, color = colors.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    uiHolder.section == AdminSection.OPS -> LazyColumn(contentPadding = rememberIgngPageContentPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            IgngSegmentedTabs(
                                items = listOf(1L, 0L, 24L, 168L),
                                selected = uiHolder.trafficRangeHours,
                                labelOf = {
                                    when (it) {
                                        1L -> "1小时"
                                        0L -> "今天"
                                        24L -> "24小时"
                                        else -> "近7天"
                                    }
                                },
                                onSelect = { uiHolder.trafficRangeHours = it },
                            )
                        }
                        trafficData?.let { tf ->
                            item {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IgngTile(Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
                                        Text("总流量", style = IgngTheme.type.badge, color = colors.textSecondary)
                                        Text(IgngFormatters.formatBytes(tf.totals.total), style = IgngTheme.type.metricMedium, color = colors.accent)
                                    }
                                    IgngTile(Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
                                        Text("出站 TX", style = IgngTheme.type.badge, color = colors.textSecondary)
                                        Text(IgngFormatters.formatBytes(tf.totals.tx), style = IgngTheme.type.metricMedium, color = colors.textPrimary)
                                    }
                                    IgngTile(Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
                                        Text("入站 RX", style = IgngTheme.type.badge, color = colors.textSecondary)
                                        Text(IgngFormatters.formatBytes(tf.totals.rx), style = IgngTheme.type.metricMedium, color = colors.textPrimary)
                                    }
                                }
                            }
                            if (tf.topIps.isNotEmpty()) {
                                item {
                                    IgngTile(modifier = Modifier.fillMaxWidth()) {
                                        IgngSectionHeader(title = "Top 来源 IP 流量", subtitle = tf.sourceLabel ?: "后端流量")
                                        Spacer(Modifier.height(10.dp))
                                        val maxTotal = tf.topIps.maxOf { it.total }.coerceAtLeast(1L).toFloat()
                                        IgngHorizontalBarList(
                                            items = tf.topIps.take(10).map {
                                                IgngBarMetric(
                                                    label = it.ip,
                                                    formattedValue = IgngFormatters.formatBytes(it.total),
                                                    ratio = it.total / maxTotal,
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        nodesData?.let { nd ->
                            item {
                                IgngSectionHeader(
                                    title = "探针与服务器实时健康",
                                    subtitle = nd.collector?.label ?: "${nd.servers.size} 台启用服务器",
                                )
                            }
                            items(nd.servers, key = { it.serverId.ifBlank { it.hashCode().toString() } }) { srv ->
                                IgngTile(modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(srv.serverName.ifBlank { srv.serverId }, style = IgngTheme.type.cardTitle, color = colors.textPrimary)
                                        val overall = srv.status.overall
                                        IgngBadge(
                                            overall,
                                            tone = when (overall) {
                                                "healthy" -> IgngBadgeTone.Success
                                                "degraded" -> IgngBadgeTone.Warning
                                                else -> IgngBadgeTone.Danger
                                            },
                                        )
                                    }
                                    Text(
                                        "TPS ${IgngFormatters.formatDecimal(srv.performance?.tps, 1)} · 玩家 ${IgngFormatters.formatNumber(srv.performance?.onlinePlayers)} · 网络 ${srv.status.network}",
                                        style = IgngTheme.type.caption,
                                        color = colors.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    inspectedTicket?.let { t ->
        IgngDetailSheet(
            onDismiss = { inspectedTicket = null },
            title = "#${t.id} ${t.title}",
            subtitle = "管理员快速处置 · ${t.creatorName}",
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                t.messages.takeLast(6).forEach { m ->
                    IgngTile(soft = true, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(10.dp)) {
                        Text("${m.authorName} · ${IgngFormatters.formatIsoDateTime(m.createdAt, short = true)}", style = IgngTheme.type.badge, color = colors.textSecondary)
                        Spacer(Modifier.height(3.dp))
                        Text(m.content, style = IgngTheme.type.body, color = colors.textPrimary)
                    }
                }
                IgngTextField(value = adminReplyDraft, onValueChange = { adminReplyDraft = it }, label = "管理员回复", singleLine = false, minLines = 3)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IgngButton(
                        text = "发送回复",
                        enabled = adminReplyDraft.isNotBlank(),
                        onClick = {
                            scope.launch {
                                runCatching { adminRepository.replyAdminTicket(t.id, adminReplyDraft.trim()) }
                                    .onSuccess {
                                        adminReplyDraft = ""
                                        inspectedTicket = runCatching { adminRepository.fetchAdminTicketDetail(t.id) }.getOrNull()
                                    }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    IgngButton(
                        text = if (t.status.equals("CLOSED", true)) "重开" else "结单",
                        style = if (t.status.equals("CLOSED", true)) IgngButtonStyle.Secondary else IgngButtonStyle.Danger,
                        onClick = {
                            scope.launch {
                                runCatching {
                                    if (t.status.equals("CLOSED", true)) adminRepository.reopenAdminTicket(t.id)
                                    else adminRepository.closeAdminTicket(t.id)
                                }.onSuccess {
                                    inspectedTicket = null
                                    loadSection()
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (loadingHistorianDetail || historianDetail != null) {
        IgngDetailSheet(
            onDismiss = { historianDetail = null; loadingHistorianDetail = false },
            title = historianDetail?.report?.let { "周报 ${it.periodStart}" } ?: "服务器观察报告",
            subtitle = historianDetail?.run?.createdAt?.let { "生成于 ${IgngFormatters.formatIsoDateTime(it, short = true)}" },
        ) {
            if (loadingHistorianDetail) {
                IgngLoadingBlock(compact = true)
            } else {
                val run = historianDetail?.run
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val bodyText = run?.markdown ?: run?.summary ?: "暂无正文"
                    val parsedDoc = remember(bodyText) { MarkdownParser.parse(bodyText) }
                    IgngMarkdownDocumentView(
                        document = parsedDoc,
                        onLinkClick = { url -> IgngBrowserLauncher.openExternalUrl(context, url, colors.isDark) },
                    )
                    if (!run?.observations.isNullOrEmpty()) {
                        IgngSectionHeader(title = "关键观察项")
                        run?.observations.orEmpty().forEach { obs ->
                            IgngTile(soft = true, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(10.dp)) {
                                Text(obs.title ?: obs.category ?: "观察", style = IgngTheme.type.bodyMedium.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                                Text(obs.detail ?: obs.summary ?: "", style = IgngTheme.type.caption, color = colors.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}
