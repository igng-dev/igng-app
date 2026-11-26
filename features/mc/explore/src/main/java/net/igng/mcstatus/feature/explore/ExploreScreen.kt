package net.igng.mcstatus.feature.explore

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.igng.core.data.IgngCacheScope
import net.igng.core.data.rememberScreenHolder
import net.igng.core.data.rememberSwrResource
import net.igng.core.data.runSuspendCatching
import net.igng.core.designsystem.IgngAmbientCanvas
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
import net.igng.core.designsystem.IgngSectionHeader
import net.igng.core.designsystem.IgngStateBlock
import net.igng.core.designsystem.IgngStroke
import net.igng.core.designsystem.IgngTheme
import net.igng.core.designsystem.IgngTile
import net.igng.core.designsystem.rememberIgngHaptics
import net.igng.core.model.SavedAccount
import net.igng.core.model.SessionAuthStatus
import net.igng.mcstatus.common.serverFamilyVisual
import net.igng.mcstatus.core.data.ChatLogsResponse
import net.igng.mcstatus.core.data.ChatRepository
import net.igng.mcstatus.core.data.ChatServerSourceDto
import net.igng.mcstatus.core.data.ChatSourcesResponse
import net.igng.mcstatus.core.data.ExploreRepository
import net.igng.mcstatus.core.data.HallFakePlayerDto
import net.igng.mcstatus.core.data.HallLandDto
import net.igng.mcstatus.core.data.McCacheKeys
import net.igng.mcstatus.core.data.McCachePolicies
import net.igng.mcstatus.core.data.McCacheTags
import net.igng.mcstatus.core.data.McDiskCodecs
import net.igng.mcstatus.core.data.PlayerFakePlayerDto
import net.igng.mcstatus.core.data.PlayerLandDto
import net.igng.mcstatus.core.data.PlayerRepository
import net.igng.mcstatus.core.data.PlayerTeleportDto
import net.igng.mcstatus.core.data.PublicServerStatusDto
import net.igng.mcstatus.core.data.PublicStatusHistoryResponse
import net.igng.mcstatus.core.data.PublicStatusResponse
import net.igng.mcstatus.core.data.ServerFamilyDto
import net.igng.mcstatus.core.data.ServerSeasonDto
import net.igng.mcstatus.core.data.SettingsRepository
import net.igng.mcstatus.core.data.StatusRangePreset
import net.igng.mcstatus.core.data.StatusRepository
import net.igng.mcstatus.core.data.VanillaStatsResponse
import net.igng.mcstatus.core.data.VanillaStatsServerDto
import net.igng.mcstatus.core.model.IgngFormatters
import net.igng.mcstatus.core.model.ServerThemeKey
import net.igng.mcstatus.core.model.SiteEndpoints

private class CompanionWorkbenchHolder {
    var initializedFromPrefs by mutableStateOf(false)
    var activeTabKey by mutableStateOf<String?>(null)
    val fallbackSeasonOverrides = mutableStateMapOf<String, String>()
}

@Composable
fun ExploreScreen(
    endpoints: SiteEndpoints,
    exploreRepository: ExploreRepository,
    statusRepository: StatusRepository,
    chatRepository: ChatRepository,
    playerRepository: PlayerRepository,
    settingsRepository: SettingsRepository,
    activeAccount: SavedAccount?,
    sessionAuthStatus: SessionAuthStatus = if (activeAccount != null) SessionAuthStatus.ACTIVE else SessionAuthStatus.SIGNED_OUT,
    bottomPadding: PaddingValues,
    onSignIn: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenStatusWall: () -> Unit,
    onOpenMap: (familySlug: String?, seasonSlug: String?) -> Unit,
    onOpenFamilyDetail: (ServerFamilyDto) -> Unit,
    onOpenCommunityHall: (serverId: Int?, tab: String?) -> Unit,
    onOpenPerfectWall: () -> Unit,
    onOpenMyProfile: () -> Unit,
    onOpenServerChat: (serverId: Int, title: String) -> Unit,
    onOpenMyLands: (serverId: Int?) -> Unit,
    onOpenMyTeleports: (serverId: Int?) -> Unit,
    onOpenMyFakePlayers: (serverId: Int?) -> Unit,
    onOpenVanillaStats: (serverId: Int?) -> Unit,
    onOpenCreateTicket: (serverId: Int?) -> Unit,
) {
    val colors = IgngTheme.colors
    val haptic = rememberIgngHaptics()
    val scope = rememberCoroutineScope()
    val holder = rememberScreenHolder("companion_workbench") { CompanionWorkbenchHolder() }

    val isAuthenticated = activeAccount != null && sessionAuthStatus == SessionAuthStatus.ACTIVE
    val requiresReauth = activeAccount != null && sessionAuthStatus.requiresReauth
    val persistedServerKey by settingsRepository.lastSelectedServerKey.collectAsState(initial = null)

    // 1. Core shared public resources (pre-warmed from disk on cold start)
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

    val (chatSourcesState, chatSourcesHandle) = rememberSwrResource<ChatSourcesResponse>(
        cache = chatRepository.swrCache,
        cacheKey = McCacheKeys.CHAT_SOURCES,
        scope = IgngCacheScope.SESSION_AWARE,
        policy = McCachePolicies.Chat,
        tags = setOf(McCacheTags.CHAT_SOURCES),
        diskCodec = McDiskCodecs.chatSources,
        slotKey = "explore_chat_sources",
    ) {
        runSuspendCatching { chatRepository.fetchSources() }
    }

    val (hallFakesState, hallFakesHandle) = rememberSwrResource<List<HallFakePlayerDto>>(
        cache = exploreRepository.swrCache,
        cacheKey = McCacheKeys.HALL_FAKES,
        scope = IgngCacheScope.PUBLIC,
        policy = McCachePolicies.Realtime,
        tags = setOf(McCacheTags.HALL),
        pollIntervalMs = 45_000L,
        slotKey = "explore_hall_fakes",
    ) {
        runSuspendCatching { exploreRepository.fetchPublicFakePlayers() }
    }

    // 2. Shared lightweight user resources (fetched once, sliced per server in memory with zero swipe latency)
    val (myFakesState, myFakesHandle) = rememberSwrResource<List<PlayerFakePlayerDto>>(
        cache = playerRepository.swrCache,
        cacheKey = McCacheKeys.PLAYER_FAKE_PLAYERS,
        scope = IgngCacheScope.USER_ONLY,
        policy = McCachePolicies.Realtime,
        enabled = isAuthenticated,
        pollIntervalMs = 30_000L,
        slotKey = "explore_my_fakes",
    ) {
        runSuspendCatching { playerRepository.fetchFakePlayers() }
    }

    val (myTeleportsState, myTeleportsHandle) = rememberSwrResource<List<PlayerTeleportDto>>(
        cache = playerRepository.swrCache,
        cacheKey = McCacheKeys.PLAYER_TELEPORTS,
        scope = IgngCacheScope.USER_ONLY,
        policy = McCachePolicies.Detail,
        enabled = isAuthenticated,
        slotKey = "explore_my_teleports",
    ) {
        runSuspendCatching { playerRepository.fetchTeleports() }
    }

    val (myStatsState, myStatsHandle) = rememberSwrResource<VanillaStatsResponse>(
        cache = playerRepository.swrCache,
        cacheKey = McCacheKeys.playerStats(null),
        scope = IgngCacheScope.USER_ONLY,
        policy = McCachePolicies.Detail,
        enabled = isAuthenticated,
        slotKey = "explore_my_stats",
    ) {
        runSuspendCatching { playerRepository.fetchVanillaStats(serverId = null) }
    }

    val families = familiesState.data.orEmpty()
    val statuses = statusState.data?.servers.orEmpty()
    val statusByServerId = remember(statuses) { statuses.associateBy { it.id } }
    val chatSourceByServerId = remember(chatSourcesState.data) {
        chatSourcesState.data?.servers.orEmpty().associateBy { it.id }
    }
    val hallFakesByServerId = remember(hallFakesState.data) {
        hallFakesState.data.orEmpty().groupBy { it.numericServerId }
    }
    val myFakesByServerId = remember(myFakesState.data) {
        myFakesState.data.orEmpty().groupBy { it.server_id }
    }
    val myTeleportsByServerId = remember(myTeleportsState.data) {
        myTeleportsState.data.orEmpty().groupBy { it.server_id }
    }
    val myStatsByServerId = remember(myStatsState.data) {
        myStatsState.data?.servers.orEmpty().associateBy { it.serverId }
    }

    val tabs = remember(families) { buildCompanionTabs(families) }
    val totalOnline = remember(statuses) { statuses.sumOf { it.onlinePlayers ?: 0 } }
    val onlineServers = remember(statuses) { statuses.count { it.online } }
    val loading = familiesState.isInitialLoading && families.isEmpty()
    val error = if (families.isEmpty()) familiesState.error else null

    // Overlay sheets managed at workbench level
    var telemetrySheetServer by remember { mutableStateOf<PublicServerStatusDto?>(null) }
    var inspectedPublicLand by remember { mutableStateOf<HallLandDto?>(null) }
    var editingMyLand by remember { mutableStateOf<PlayerLandDto?>(null) }
    var guideSheetTarget by remember { mutableStateOf<Pair<ServerFamilyDto, ServerSeasonDto>?>(null) }

    IgngAmbientCanvas {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // 1. Compact Play Companion Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "IGNGmc",
                    style = IgngTheme.type.pageTitle,
                    color = colors.textPrimary,
                )
                if (statuses.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .clip(IgngTheme.radii.pill)
                            .background(colors.tile)
                            .border(IgngStroke.hairline, colors.line, IgngTheme.radii.pill)
                            .clickable {
                                haptic(IgngHapticKind.Tap)
                                onOpenStatusWall()
                            }
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (onlineServers > 0) colors.success else colors.danger),
                        )
                        Text(
                            text = "$onlineServers/${statuses.size} · ${totalOnline}人",
                            style = IgngTheme.type.badge,
                            color = colors.textSecondary,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        haptic(IgngHapticKind.Tap)
                        onOpenSearch()
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "全局搜索",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(IgngTheme.radii.sm)
                        .background(if (requiresReauth) colors.warningBg else colors.tile)
                        .border(
                            IgngStroke.hairline,
                            if (requiresReauth) colors.warning.copy(alpha = 0.55f) else colors.lineStrong,
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
                            requiresReauth -> "重新授权"
                            activeAccount != null -> activeAccount.displayName
                            else -> "登录"
                        },
                        style = IgngTheme.type.caption.copy(fontWeight = FontWeight.Medium),
                        color = when {
                            requiresReauth -> colors.warning
                            activeAccount != null -> colors.textPrimary
                            else -> colors.accent
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            when {
                loading -> Box(Modifier.fillMaxSize().padding(16.dp)) { IgngLoadingBlock(fill = true) }
                error != null -> Box(Modifier.fillMaxSize().padding(16.dp)) {
                    IgngStateBlock(
                        title = "服务器目录暂时不可用",
                        description = error,
                        isError = true,
                        actionLabel = "重试",
                        onAction = {
                            familiesHandle.refresh()
                            statusHandle.refresh()
                        },
                    )
                }
                tabs.isNotEmpty() -> {
                    val initialIndex = remember(tabs, holder.activeTabKey, persistedServerKey) {
                        resolveInitialTabIndex(tabs, holder.activeTabKey ?: persistedServerKey)
                    }
                    val pagerState = rememberPagerState(
                        initialPage = initialIndex.coerceIn(0, tabs.lastIndex),
                        pageCount = { tabs.size },
                    )
                    val tabListState = rememberLazyListState()

                    // Restore persisted last server once when DataStore delivers initial key
                    LaunchedEffect(tabs, persistedServerKey) {
                        if (!holder.initializedFromPrefs && !persistedServerKey.isNullOrBlank()) {
                            val targetIdx = resolveInitialTabIndex(tabs, persistedServerKey)
                            holder.initializedFromPrefs = true
                            holder.activeTabKey = tabs.getOrNull(targetIdx)?.key
                            if (targetIdx != pagerState.currentPage) {
                                pagerState.scrollToPage(targetIdx)
                            }
                        }
                    }

                    // Keep selector strip scrolled into view & persist last chosen server on settle
                    LaunchedEffect(pagerState.settledPage, tabs) {
                        val settled = pagerState.settledPage.coerceIn(0, tabs.lastIndex)
                        val key = tabs[settled].key
                        holder.initializedFromPrefs = true
                        if (holder.activeTabKey != key) {
                            holder.activeTabKey = key
                            settingsRepository.setLastSelectedServerKey(key)
                        }
                        tabListState.animateScrollToItem((settled - 1).coerceAtLeast(0))
                    }

                    // 2. Sticky Top Server Selector Strip
                    LazyRow(
                        state = tabListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(tabs, key = { _, t -> t.key }) { idx, tab ->
                            val isSelected = pagerState.currentPage == idx
                            val tabStatus = (tab as? CompanionTabTarget.ActiveServer)?.serverId?.let { statusByServerId[it] }
                            ServerSelectorTabPill(
                                tab = tab,
                                status = tabStatus,
                                selected = isSelected,
                                onClick = {
                                    haptic(IgngHapticKind.Tick)
                                    scope.launch { pagerState.animateScrollToPage(idx) }
                                },
                            )
                        }
                    }
                    IgngHairlineDivider()

                    // 3. Swipeable Server Pages
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        modifier = Modifier.weight(1f),
                    ) { pageIndex ->
                        val tab = tabs[pageIndex]
                        val isSettledActive = pagerState.settledPage == pageIndex

                        when (tab) {
                            is CompanionTabTarget.ActiveServer -> {
                                val resolvedSeason = remember(tab, holder.fallbackSeasonOverrides[tab.key]) {
                                    val overrideSlug = holder.fallbackSeasonOverrides[tab.key]
                                    if (overrideSlug != null && tab.siblingAliveSeasons.isNotEmpty()) {
                                        tab.siblingAliveSeasons.firstOrNull { it.slug == overrideSlug } ?: tab.season
                                    } else {
                                        tab.season
                                    }
                                }
                                val serverId = resolvedSeason.serverId
                                val liveStatus = serverId?.let { statusByServerId[it] }

                                ActiveServerCompanionPage(
                                    endpoints = endpoints,
                                    family = tab.family,
                                    season = resolvedSeason,
                                    siblingAliveSeasons = tab.siblingAliveSeasons,
                                    themeKey = ServerThemeKey.resolve(resolvedSeason.backgroundKey ?: tab.family.themeKey),
                                    status = liveStatus,
                                    chatSource = serverId?.let { chatSourceByServerId[it] },
                                    publicFakes = serverId?.let { hallFakesByServerId[it] }.orEmpty(),
                                    myFakes = serverId?.let { myFakesByServerId[it] }.orEmpty(),
                                    myTeleports = serverId?.let { myTeleportsByServerId[it] }.orEmpty(),
                                    serverStats = serverId?.let { myStatsByServerId[it] },
                                    isAuthenticated = isAuthenticated,
                                    requiresReauth = requiresReauth,
                                    isActivePage = isSettledActive,
                                    exploreRepository = exploreRepository,
                                    statusRepository = statusRepository,
                                    chatRepository = chatRepository,
                                    playerRepository = playerRepository,
                                    bottomPadding = bottomPadding,
                                    onRefreshShared = {
                                        familiesHandle.refresh()
                                        statusHandle.refresh()
                                        chatSourcesHandle.refresh()
                                        hallFakesHandle.refresh()
                                        if (isAuthenticated) {
                                            myFakesHandle.refresh()
                                            myTeleportsHandle.refresh()
                                            myStatsHandle.refresh()
                                        }
                                    },
                                    sharedRefreshing = familiesState.isUserRefreshing || statusState.isUserRefreshing,
                                    onSelectSiblingSeason = { next -> holder.fallbackSeasonOverrides[tab.key] = next.slug },
                                    onSignIn = onSignIn,
                                    onOpenTelemetrySheet = { liveStatus?.let { telemetrySheetServer = it } },
                                    onOpenMap = onOpenMap,
                                    onOpenFamilyDetail = { onOpenFamilyDetail(tab.family) },
                                    onOpenServerChat = { sid -> onOpenServerChat(sid, tab.fullTitle) },
                                    onOpenMyLands = { onOpenMyLands(serverId) },
                                    onOpenMyTeleports = { onOpenMyTeleports(serverId) },
                                    onOpenMyFakePlayers = { onOpenMyFakePlayers(serverId) },
                                    onOpenVanillaStats = { onOpenVanillaStats(serverId) },
                                    onOpenPublicHall = { tabKey -> onOpenCommunityHall(serverId, tabKey) },
                                    onOpenCreateTicket = { onOpenCreateTicket(serverId) },
                                    onInspectPublicLand = { inspectedPublicLand = it },
                                    onEditMyLand = { editingMyLand = it },
                                    onOpenGuideSheet = { guideSheetTarget = tab.family to resolvedSeason },
                                )
                            }
                            is CompanionTabTarget.ArchiveHub -> {
                                ArchiveHubPage(
                                    endpoints = endpoints,
                                    hub = tab,
                                    statusByServerId = statusByServerId,
                                    totalOnline = totalOnline,
                                    statusesCount = statuses.size,
                                    refreshing = familiesState.isUserRefreshing || statusState.isUserRefreshing,
                                    bottomPadding = bottomPadding,
                                    onRefresh = {
                                        familiesHandle.refresh()
                                        statusHandle.refresh()
                                    },
                                    onOpenMap = onOpenMap,
                                    onOpenFamilyDetail = onOpenFamilyDetail,
                                    onOpenStatusWall = onOpenStatusWall,
                                    onOpenPerfectWall = onOpenPerfectWall,
                                    onOpenPublicTickets = { onOpenCommunityHall(null, "tickets") },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    telemetrySheetServer?.let { srv ->
        ServerTelemetryDetailSheet(
            server = srv,
            statusRepository = statusRepository,
            onDismiss = { telemetrySheetServer = null },
        )
    }

    inspectedPublicLand?.let { land ->
        PublicLandDetailSheet(
            land = land,
            onDismiss = { inspectedPublicLand = null },
        )
    }

    editingMyLand?.let { land ->
        QuickMyLandEditSheet(
            land = land,
            playerRepository = playerRepository,
            onSaved = {
                playerRepository.swrCache.markStale(McCacheTags.LANDS, McCacheTags.HALL)
            },
            onDismiss = { editingMyLand = null },
        )
    }

    guideSheetTarget?.let { (fam, season) ->
        ServerGuideDetailSheet(
            family = fam,
            season = season,
            onDismiss = { guideSheetTarget = null },
        )
    }
}

@Composable
private fun ServerSelectorTabPill(
    tab: CompanionTabTarget,
    status: PublicServerStatusDto?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = IgngTheme.colors
    val visual = serverFamilyVisual(tab.themeKey, colors.isDark)
    val intensity = IgngTheme.motionIntensity

    val pillBg by animateColorAsState(
        targetValue = if (selected) visual.softTint else colors.tile,
        animationSpec = IgngMotion.microTween(intensity),
        label = "srvTabBg",
    )
    val pillBorder by animateColorAsState(
        targetValue = if (selected) visual.accent.copy(alpha = 0.48f) else colors.line,
        animationSpec = IgngMotion.microTween(intensity),
        label = "srvTabBorder",
    )
    val dotColor = when {
        tab is CompanionTabTarget.ArchiveHub -> visual.accent
        status == null -> visual.accent
        status.online -> visual.accent
        status.status == "degraded" -> colors.warning
        else -> colors.danger
    }

    Row(
        modifier = Modifier
            .clip(IgngTheme.radii.sm)
            .background(pillBg)
            .border(IgngStroke.hairline, pillBorder, IgngTheme.radii.sm)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text = tab.shortTitle,
            style = IgngTheme.type.caption.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (selected) visual.accent else colors.textSecondary,
            maxLines = 1,
        )
        val players = status?.onlinePlayers
        if (tab is CompanionTabTarget.ActiveServer && players != null && status.online) {
            Text(
                text = players.toString(),
                style = IgngTheme.type.badge,
                color = if (selected) visual.accent.copy(alpha = 0.85f) else colors.textMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ActiveServerCompanionPage(
    endpoints: SiteEndpoints,
    family: ServerFamilyDto,
    season: ServerSeasonDto,
    siblingAliveSeasons: List<ServerSeasonDto>,
    themeKey: ServerThemeKey,
    status: PublicServerStatusDto?,
    chatSource: ChatServerSourceDto?,
    publicFakes: List<HallFakePlayerDto>,
    myFakes: List<PlayerFakePlayerDto>,
    myTeleports: List<PlayerTeleportDto>,
    serverStats: VanillaStatsServerDto?,
    isAuthenticated: Boolean,
    requiresReauth: Boolean,
    isActivePage: Boolean,
    exploreRepository: ExploreRepository,
    statusRepository: StatusRepository,
    chatRepository: ChatRepository,
    playerRepository: PlayerRepository,
    bottomPadding: PaddingValues,
    onRefreshShared: () -> Unit,
    sharedRefreshing: Boolean,
    onSelectSiblingSeason: (ServerSeasonDto) -> Unit,
    onSignIn: () -> Unit,
    onOpenTelemetrySheet: () -> Unit,
    onOpenMap: (familySlug: String?, seasonSlug: String?) -> Unit,
    onOpenFamilyDetail: () -> Unit,
    onOpenServerChat: (Int) -> Unit,
    onOpenMyLands: () -> Unit,
    onOpenMyTeleports: () -> Unit,
    onOpenMyFakePlayers: () -> Unit,
    onOpenVanillaStats: () -> Unit,
    onOpenPublicHall: (tabKey: String) -> Unit,
    onOpenCreateTicket: () -> Unit,
    onInspectPublicLand: (HallLandDto) -> Unit,
    onEditMyLand: (PlayerLandDto) -> Unit,
    onOpenGuideSheet: () -> Unit,
) {
    val serverId = season.serverId
    // Debounce lazy per-server loads so fast pager flings never trigger wasted requests
    var lazyEnabled by remember(season.slug) { mutableStateOf(false) }
    LaunchedEffect(isActivePage, season.slug) {
        if (isActivePage) {
            delay(150L)
            lazyEnabled = true
        } else {
            lazyEnabled = false
        }
    }

    val (historyState, historyHandle) = rememberSwrResource<PublicStatusHistoryResponse>(
        cache = statusRepository.swrCache,
        cacheKey = McCacheKeys.statusHistory(StatusRangePreset.H24, serverId),
        scope = IgngCacheScope.PUBLIC,
        policy = McCachePolicies.Realtime,
        tags = setOf(McCacheTags.STATUS),
        enabled = lazyEnabled && serverId != null,
        slotKey = "srv_page_hist_${season.slug}",
    ) {
        runSuspendCatching { statusRepository.fetchPublicHistory(StatusRangePreset.H24, serverId = serverId) }
    }

    val (publicLandsState, publicLandsHandle) = rememberSwrResource<List<HallLandDto>>(
        cache = exploreRepository.swrCache,
        cacheKey = McCacheKeys.hallLands(serverId),
        scope = IgngCacheScope.PUBLIC,
        policy = McCachePolicies.Standard,
        tags = setOf(McCacheTags.HALL),
        enabled = lazyEnabled && serverId != null,
        slotKey = "srv_page_pub_lands_${season.slug}",
    ) {
        runSuspendCatching { exploreRepository.fetchPublicLands(serverId = serverId) }
    }

    val (myLandsState, myLandsHandle) = rememberSwrResource<List<PlayerLandDto>>(
        cache = playerRepository.swrCache,
        cacheKey = McCacheKeys.playerLands(query = "", serverId = serverId),
        scope = IgngCacheScope.USER_ONLY,
        policy = McCachePolicies.Standard,
        tags = setOf(McCacheTags.LANDS),
        enabled = lazyEnabled && isAuthenticated && serverId != null,
        slotKey = "srv_page_my_lands_${season.slug}",
    ) {
        runSuspendCatching { playerRepository.fetchLands(serverId = serverId?.toString()).records }
    }

    val (chatPreviewState, chatPreviewHandle) = rememberSwrResource<ChatLogsResponse>(
        cache = chatRepository.swrCache,
        cacheKey = serverId?.let { McCacheKeys.serverChatPreview(it) } ?: "mc:chat:preview:none",
        scope = IgngCacheScope.SESSION_AWARE,
        policy = McCachePolicies.Chat,
        tags = setOf(McCacheTags.CHAT_MESSAGES),
        enabled = lazyEnabled && serverId != null && chatSource != null,
        slotKey = "srv_page_chat_${season.slug}",
    ) {
        runSuspendCatching {
            chatRepository.fetchMessages(source = "server", serverId = serverId, limit = 6)
        }
    }

    val historySeries = serverId?.let { historyState.data?.seriesFor(it) }
    val publicLands = publicLandsState.data.orEmpty()
    val myLands = myLandsState.data.orEmpty()
    val recentMessages = chatPreviewState.data?.messages.orEmpty()
    val radarProjection = remember(myTeleports, myLands, myFakes, publicLands, publicFakes) {
        buildServerRadarProjection(
            myTeleports = myTeleports,
            myLands = myLands,
            myFakes = myFakes,
            publicLands = publicLands,
            publicFakes = publicFakes,
        )
    }
    val snapshotHistorySeasons = remember(family.historicalSeasons) {
        family.historicalSeasons.filter { it.isSnapshotMap }
    }
    val refreshing = sharedRefreshing ||
        historyState.isUserRefreshing ||
        publicLandsState.isUserRefreshing ||
        myLandsState.isUserRefreshing ||
        chatPreviewState.isUserRefreshing

    IgngPullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            onRefreshShared()
            if (serverId != null) {
                historyHandle.refresh()
                publicLandsHandle.refresh()
                if (isAuthenticated) myLandsHandle.refresh()
                if (chatSource != null) chatPreviewHandle.refresh()
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = bottomPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ① P0: Live Server Telemetry, 24h Uptime Strip & Quick-Join Address/Command
            item(key = "telemetry_${season.slug}") {
                ServerTelemetryHeroCard(
                    family = family,
                    season = season,
                    status = status,
                    historySeries = historySeries,
                    themeKey = themeKey,
                    siblingAliveSeasons = siblingAliveSeasons,
                    onSelectSiblingSeason = onSelectSiblingSeason,
                    onOpenTelemetrySheet = onOpenTelemetrySheet,
                )
            }

            // ② P0: World Map Preview (when server has viewable map) OR Modpack Brief (for MOD servers without live map)
            if (season.hasViewableMap) {
                item(key = "map_${season.slug}") {
                    ServerMapPreviewCard(
                        endpoints = endpoints,
                        family = family,
                        season = season,
                        themeKey = themeKey,
                        radarProjection = radarProjection,
                        publicLandCount = publicLands.size,
                        activeFakeCount = publicFakes.size,
                        onOpenMap = { onOpenMap(family.slug, season.slug) },
                    )
                }
            } else if (!season.modpack.isNullOrBlank() || season.highlights.isNotEmpty() || season.rules.isNotEmpty()) {
                item(key = "modpack_${season.slug}") {
                    ModpackBriefCard(
                        season = season,
                        themeKey = themeKey,
                        onOpenFullGuide = onOpenGuideSheet,
                    )
                }
            }

            // ③ P1: My Server Companion Toolbox (Fakes / Teleports / Lands / Per-Server Stats)
            if (serverId != null) {
                item(key = "my_companion_${season.slug}") {
                    MyServerCompanionCard(
                        serverId = serverId,
                        isAuthenticated = isAuthenticated,
                        requiresReauth = requiresReauth,
                        myFakes = myFakes,
                        myTeleports = myTeleports,
                        myLands = myLands,
                        serverStats = serverStats,
                        onSignIn = onSignIn,
                        onOpenFakes = onOpenMyFakePlayers,
                        onOpenTeleports = onOpenMyTeleports,
                        onOpenLands = onOpenMyLands,
                        onOpenStats = onOpenVanillaStats,
                        onEditLand = onEditMyLand,
                    )
                }
            }

            // ④ P1: Live In-Game Server Chat Preview
            if (serverId != null && chatSource != null) {
                item(key = "chat_${season.slug}") {
                    ServerChatPreviewCard(
                        endpoints = endpoints,
                        chatSource = chatSource,
                        recentMessages = recentMessages,
                        onOpenChat = { onOpenServerChat(serverId) },
                    )
                }
            }

            // ⑤ P2: Server World Ecology (Public Lands & Online Fake Players on this server)
            if (serverId != null && (publicLands.isNotEmpty() || publicFakes.isNotEmpty())) {
                item(key = "ecology_${season.slug}") {
                    ServerEcologyCard(
                        serverId = serverId,
                        publicLands = publicLands,
                        publicFakes = publicFakes,
                        onSelectLand = onInspectPublicLand,
                        onOpenAllHall = onOpenPublicHall,
                    )
                }
            }

            // Adaptive Historical Snapshot Gallery (especially great for MOD series with 14 snapshots)
            if (snapshotHistorySeasons.isNotEmpty()) {
                item(key = "snapshots_${season.slug}") {
                    HistoricalSnapshotGalleryCard(
                        endpoints = endpoints,
                        family = family,
                        snapshotSeasons = snapshotHistorySeasons,
                        onOpenMap = onOpenMap,
                        onOpenFamilyArchive = onOpenFamilyDetail,
                    )
                }
            }

            // ⑥ P2~P3: Server Guide, Rules, Quick Ticket & Historical Archive (Monochrome Quiet Craft Group)
            item(key = "guide_actions_${season.slug}") {
                val hasWorlds = season.worlds.isNotEmpty()
                val hasHistory = family.historicalSeasons.isNotEmpty()
                IgngGroupedSection(title = "游玩指南与服务") {
                    if (hasWorlds) {
                        IgngListRow(
                            title = "世界与维度",
                            subtitle = season.worlds.joinToString(" · ") { it.displayLabel },
                            icon = Icons.Outlined.Public,
                            onClick = onOpenGuideSheet,
                            showDivider = true,
                        )
                    }
                    IgngListRow(
                        title = "规则、亮点与特色插件",
                        subtitle = buildList {
                            if (season.rules.isNotEmpty()) add("${season.rules.size} 条规则")
                            if (season.plugins.isNotEmpty()) add("${season.plugins.size} 项插件")
                            if (isEmpty()) add("查看服务器配置与种子档案")
                        }.joinToString(" · "),
                        icon = Icons.Outlined.Rule,
                        onClick = onOpenGuideSheet,
                        showDivider = true,
                    )
                    IgngListRow(
                        title = "提交本服问题工单",
                        subtitle = if (isAuthenticated) "自动关联当前服务器" else "登录后可提交反馈与申诉",
                        icon = Icons.Outlined.ConfirmationNumber,
                        onClick = onOpenCreateTicket,
                        showDivider = hasHistory,
                    )
                    if (hasHistory) {
                        IgngListRow(
                            title = "往期周目与历史档案",
                            subtitle = "查看${family.name}历代周目与存档快照",
                            value = "${family.historicalSeasons.size} 个",
                            icon = Icons.Outlined.History,
                            onClick = onOpenFamilyDetail,
                            showDivider = false,
                        )
                    }
                }
            }
        }
    }
}

// ==================== Archive & Global Community Hub Page ====================

@Composable
private fun ArchiveHubPage(
    endpoints: SiteEndpoints,
    hub: CompanionTabTarget.ArchiveHub,
    statusByServerId: Map<Int, PublicServerStatusDto>,
    totalOnline: Int,
    statusesCount: Int,
    refreshing: Boolean,
    bottomPadding: PaddingValues,
    onRefresh: () -> Unit,
    onOpenMap: (familySlug: String?, seasonSlug: String?) -> Unit,
    onOpenFamilyDetail: (ServerFamilyDto) -> Unit,
    onOpenStatusWall: () -> Unit,
    onOpenPerfectWall: () -> Unit,
    onOpenPublicTickets: () -> Unit,
) {
    val colors = IgngTheme.colors
    val lobby = hub.archiveFamily?.primarySeason
    val lobbyStatus = lobby?.serverId?.let { statusByServerId[it] }
    val hibernating = remember(hub) { hub.hibernatingSeasons }
    val retiredGroups = remember(hub) { hub.retiredGroups }

    IgngPullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = bottomPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Archive Lobby Card (if configured)
            if (hub.archiveFamily != null && lobby != null) {
                item(key = "archive_lobby") {
                    IgngTile(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = hub.archiveFamily.name.ifBlank { "周目档案馆" },
                                style = IgngTheme.type.sectionTitle,
                                color = colors.textPrimary,
                            )
                            IgngBadge(
                                text = if (lobbyStatus?.online == true) "在线 · ${lobbyStatus.onlinePlayers ?: 0} 人" else "档案馆大厅",
                                tone = if (lobbyStatus?.online == true) IgngBadgeTone.Success else IgngBadgeTone.Neutral,
                            )
                        }
                        val desc = lobby.description.ifBlank { hub.archiveFamily.description }
                        if (desc.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(desc, style = IgngTheme.type.caption, color = colors.textSecondary)
                        }
                        lobby.address?.takeIf { it.isNotBlank() }?.let { addr ->
                            Spacer(Modifier.height(10.dp))
                            IgngCopyPill(value = addr, label = "大厅地址：$addr", modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            // 2. Wakeable Hibernating Seasons
            if (hibernating.isNotEmpty()) {
                item(key = "archive_hibernating") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        IgngSectionHeader(
                            title = "休眠可唤醒存档",
                            subtitle = "通过档案馆大厅按需进入或直接浏览归档地图",
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        IgngTile(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                            hibernating.forEachIndexed { idx, (fam, season) ->
                                if (idx > 0) {
                                    Spacer(Modifier.height(8.dp))
                                    IgngHairlineDivider()
                                    Spacer(Modifier.height(8.dp))
                                }
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
                                                text = "${fam.name} · ${season.label.ifBlank { season.slug }}",
                                                style = IgngTheme.type.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = colors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            IgngBadge(text = "休眠", tone = IgngBadgeTone.Warning)
                                        }
                                        val meta = listOfNotNull(season.version, season.startedAt).joinToString(" · ")
                                        if (meta.isNotBlank()) {
                                            Text(meta, style = IgngTheme.type.meta, color = colors.textSecondary)
                                        }
                                    }
                                    if (season.hasViewableMap) {
                                        IgngButton(
                                            text = "地图",
                                            onClick = { onOpenMap(fam.slug, season.slug) },
                                            style = IgngButtonStyle.Secondary,
                                            icon = Icons.Rounded.Map,
                                            compact = true,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Retired Seasons & Snapshot Galleries by Family
            retiredGroups.forEach { (fam, retiredList) ->
                val snapshotItems = retiredList.filter { it.isSnapshotMap }
                if (snapshotItems.isNotEmpty()) {
                    item(key = "archive_snap_${fam.slug}") {
                        HistoricalSnapshotGalleryCard(
                            endpoints = endpoints,
                            family = fam,
                            snapshotSeasons = snapshotItems,
                            onOpenMap = onOpenMap,
                            onOpenFamilyArchive = { onOpenFamilyDetail(fam) },
                        )
                    }
                }
            }

            if (retiredGroups.isNotEmpty()) {
                item(key = "archive_families_list") {
                    IgngGroupedSection(title = "历代服务器家族档案") {
                        retiredGroups.forEachIndexed { idx, (fam, retiredList) ->
                            IgngListRow(
                                title = fam.name,
                                subtitle = "${retiredList.size} 个已关停历史周目",
                                icon = Icons.Outlined.Dns,
                                onClick = { onOpenFamilyDetail(fam) },
                                showDivider = idx < retiredGroups.lastIndex,
                            )
                        }
                    }
                }
            }

            // 4. Global Community & Cross-Server Services (clean home for non-per-server features)
            item(key = "archive_global_services") {
                IgngGroupedSection(title = "全服社区与服务") {
                    IgngListRow(
                        title = "全服运行状态墙",
                        subtitle = "跨服 TPS、延迟对比与历史健康曲线",
                        icon = Icons.Outlined.Timeline,
                        value = if (statusesCount > 0) "$totalOnline 人在线" else null,
                        onClick = onOpenStatusWall,
                        showDivider = true,
                    )
                    IgngListRow(
                        title = "完美之人",
                        subtitle = "荣誉成员名录与专属权益",
                        icon = Icons.Outlined.WorkspacePremium,
                        onClick = onOpenPerfectWall,
                        showDivider = true,
                    )
                    IgngListRow(
                        title = "社区公开工单",
                        subtitle = "浏览全服公开处理的反馈与公示记录",
                        icon = Icons.Outlined.Groups,
                        onClick = onOpenPublicTickets,
                        showDivider = true,
                    )
                    IgngListRow(
                        title = "全服世界地图目录",
                        subtitle = "快速切换实时地图、归档地图与历史快照",
                        icon = Icons.Outlined.Map,
                        onClick = { onOpenMap(null, null) },
                        showDivider = false,
                    )
                }
            }
        }
    }
}
