package net.igng.mcstatus

import net.igng.mcstatus.core.model.adminRole

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import net.igng.core.auth.AuthUiState
import net.igng.core.data.LaunchedAppForegroundResumeEffect
import net.igng.core.designsystem.IgngAuthOverlayHost
import net.igng.core.designsystem.IgngDockHost
import net.igng.core.designsystem.IgngNavTabItem
import net.igng.core.designsystem.IgngPredictiveBackProfile
import net.igng.core.designsystem.IgngPredictiveNavHost
import net.igng.core.designsystem.IgngTheme
import net.igng.core.designsystem.LocalIgngDockBottomPadding
import net.igng.core.designsystem.LocalIgngPredictiveBackState
import net.igng.core.designsystem.igngComposable
import net.igng.core.designsystem.rememberIgngDockBottomPadding
import net.igng.core.designsystem.rememberIgngPredictiveBackState
import net.igng.core.model.FamilySharedAccountHint
import net.igng.core.model.SessionAuthStatus
import net.igng.mcstatus.core.data.IgngAppContainer
import net.igng.mcstatus.core.model.AppPreferences
import net.igng.mcstatus.feature.admin.AdminPocketConsoleScreen
import net.igng.mcstatus.feature.chat.ChatConversationScreen
import net.igng.mcstatus.feature.chat.ChatSessionListScreen
import net.igng.mcstatus.feature.chat.CommunityChannelsScreen
import net.igng.mcstatus.feature.explore.CommunityHallScreen
import net.igng.mcstatus.feature.explore.ExploreScreen
import net.igng.mcstatus.feature.explore.GlobalSearchScreen
import net.igng.mcstatus.feature.explore.McCharacterPublicScreen
import net.igng.mcstatus.feature.explore.PerfectRosterScreen
import net.igng.mcstatus.feature.explore.PlayerPassportScreen
import net.igng.mcstatus.feature.explore.ServerFamilyDetailScreen
import net.igng.mcstatus.feature.map.WorldMapScreen
import net.igng.mcstatus.feature.player.CharactersScreen
import net.igng.mcstatus.feature.player.LoginActivityScreen
import net.igng.mcstatus.feature.player.MyFakePlayersScreen
import net.igng.mcstatus.feature.player.MyLandsScreen
import net.igng.mcstatus.feature.player.MyMedalsScreen
import net.igng.mcstatus.feature.player.MyTeleportsScreen
import net.igng.mcstatus.feature.player.PermissionsScreen
import net.igng.mcstatus.feature.player.PersonalPerfectScreen
import net.igng.mcstatus.feature.player.PlayerCenterScreen
import net.igng.mcstatus.feature.player.PlayerDestination
import net.igng.mcstatus.feature.player.VanillaStatsScreen
import net.igng.mcstatus.feature.player.VaultScreen
import net.igng.mcstatus.feature.status.StatusWallScreen
import net.igng.mcstatus.feature.ticket.CreateTicketScreen
import net.igng.mcstatus.feature.ticket.TicketDetailScreen
import net.igng.mcstatus.feature.ticket.TicketsScreen
import net.igng.mcstatus.feature.wiki.WikiArticleScreen
import net.igng.mcstatus.feature.wiki.WikiCatalogScreen

@Composable
fun IgngMcApp(
    container: IgngAppContainer,
    incomingShortcuts: Flow<String>,
) {
    val preferences by container.settingsRepository.preferences.collectAsState(initial = AppPreferences())
    val activeAccount by container.activeAccount.collectAsState()
    val accounts by container.accounts.collectAsState()
    val bootstrap by container.bootstrap.collectAsState()
    val sessionEpoch by container.sessionEpoch.collectAsState()
    val sessionAuthStatus by container.sessionAuthStatus.collectAsState()
    val familyAccounts by container.familyAccounts.collectAsState()
    val authUiState by container.authUiState.collectAsState()
    val initialFamilySyncDone by container.initialFamilySyncDone.collectAsState()
    val updateSnapshot by container.appUpdateManager.snapshot.collectAsState()

    IgngTheme(
        themeMode = preferences.themeMode,
        motionIntensity = preferences.motionIntensity,
        hapticsEnabled = preferences.hapticsEnabled,
    ) {
        val navController = rememberNavController()
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val scope = rememberCoroutineScope()
        val colors = IgngTheme.colors
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            container.syncFamilyAccountsAndAutoSignIn()
        }
        // Silent cold-start update check (24h cooldown, skips family-sync-only pushes)
        LaunchedEffect(Unit) {
            delay(2500L)
            container.appUpdateManager.checkForUpdatesIfDue()
        }
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    container.syncFamilyAccountsAndAutoSignIn()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        var sessionExpiredPrompt by remember { mutableStateOf(false) }
        var promptedLegacyUserId by remember { mutableStateOf<Int?>(null) }

        fun startOAuth(forceLogin: Boolean) {
            container.sessionManager.launchLogin(
                context = context,
                isDark = colors.isDark,
                forceSwitchAccount = forceLogin,
            )
        }

        fun syncWithFamilyAccount(hint: FamilySharedAccountHint) {
            container.signInWithFamilyAccount(
                context = context,
                hint = hint,
                isDark = colors.isDark,
            )
        }

        // Unified OAuth & family handoff toast messages
        LaunchedEffect(Unit) {
            container.authEvents.collect { msg ->
                snackbarHostState.showSnackbar(msg)
            }
        }

        // Handle App Shortcut intents
        LaunchedEffect(Unit) {
            incomingShortcuts.collect { action ->
                when (action) {
                    "status" -> navController.navigate(AppRoute.StatusWall) { launchSingleTop = true }
                    "map" -> navController.navigate(AppRoute.WorldMap()) { launchSingleTop = true }
                    "chat" -> navController.navigate(AppRoute.Chat) { launchSingleTop = true }
                    "create_ticket" -> {
                        if (container.activeAccount.value != null && !container.sessionAuthStatus.value.requiresReauth) {
                            navController.navigate(AppRoute.CreateTicket()) { launchSingleTop = true }
                        } else {
                            navController.navigate(AppRoute.PlayerCenter) { launchSingleTop = true }
                        }
                    }
                }
            }
        }

        suspend fun refreshBootstrap() {
            val boot = runCatching { container.fetchBootstrapForCurrentSession() }.getOrNull()
            if (boot != null) {
                container.updateBootstrap(boot)
                val current = container.activeAccount.value
                val viewerUser = boot.viewer.user
                if (current != null && boot.viewer.authenticated && viewerUser != null) {
                    val updated = current.copy(
                        username = viewerUser.username.ifBlank { current.username },
                        nickname = viewerUser.nickname ?: current.nickname,
                        avatarUrl = viewerUser.avatarUrl ?: current.avatarUrl,
                        adminRoleWire = boot.viewer.adminRole ?: current.adminRoleWire,
                    )
                    if (updated != current) {
                        container.saveAndActivateAccount(updated, bumpEpoch = false)
                    }
                }
            }
        }

        // Refresh bootstrap state, user metadata & admin role whenever active session changes
        LaunchedEffect(activeAccount?.userId, sessionEpoch) {
            refreshBootstrap()
        }

        // Idle low-priority prefetch for sibling top-level tabs after first frame settles
        LaunchedEffect(activeAccount?.userId, sessionEpoch, sessionAuthStatus) {
            delay(750L)
            container.prefetchIdleTabs()
        }

        // Silent bootstrap refresh when returning from background after >= 30s
        LaunchedAppForegroundResumeEffect(minBackgroundMs = 30_000L) {
            refreshBootstrap()
        }

        // Prompt once for pre-v2.1.0 legacy sessions after initial family sync check completes
        LaunchedEffect(activeAccount?.userId, sessionAuthStatus, initialFamilySyncDone) {
            if (sessionAuthStatus == SessionAuthStatus.ACTIVE) {
                sessionExpiredPrompt = false
                return@LaunchedEffect
            }
            val uid = activeAccount?.userIdInt
            if (
                initialFamilySyncDone &&
                uid != null &&
                sessionAuthStatus == SessionAuthStatus.LEGACY_UPGRADE_REQUIRED &&
                promptedLegacyUserId != uid
            ) {
                promptedLegacyUserId = uid
                sessionExpiredPrompt = true
            }
        }

        // Global 401 listener: offer seamless re-auth without destroying user's current screen/draft
        LaunchedEffect(Unit) {
            container.sessionExpiredNotifier.events.collect {
                if (container.activeAccount.value != null) {
                    sessionExpiredPrompt = true
                }
            }
        }

        fun NavDestination?.resolveTabId(): String? = when {
            this?.hasRoute<AppRoute.Explore>() == true -> "explore"
            this?.hasRoute<AppRoute.Chat>() == true -> "chat"
            this?.hasRoute<AppRoute.Wiki>() == true -> "wiki"
            this?.hasRoute<AppRoute.PlayerCenter>() == true -> "player"
            else -> null
        }

        fun NavDestination?.isTopLevelTab(): Boolean = resolveTabId() != null

        val backStackEntry by navController.currentBackStackEntryAsState()
        val dest = backStackEntry?.destination
        val activeTabId = dest.resolveTabId()

        val navItems = remember {
            listOf(
                IgngNavTabItem("explore", "游玩", Icons.Rounded.SportsEsports, Icons.Outlined.SportsEsports),
                IgngNavTabItem("chat", "聊天", Icons.Rounded.Forum, Icons.Outlined.Forum),
                IgngNavTabItem("wiki", "百科", Icons.Rounded.AutoStories, Icons.Outlined.AutoStories),
                IgngNavTabItem("player", "我的", Icons.Rounded.Person, Icons.Outlined.Person),
            )
        }

        val bottomBarPadding = rememberIgngDockBottomPadding(dockVisible = activeTabId != null)
        val topLevelBottomBarPadding = rememberIgngDockBottomPadding(dockVisible = true)
        val predictiveBackState = rememberIgngPredictiveBackState()

        CompositionLocalProvider(
            LocalIgngDockBottomPadding provides bottomBarPadding,
            LocalIgngPredictiveBackState provides predictiveBackState,
        ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = colors.canvas,
                bottomBar = {},
            ) { _ ->
                IgngPredictiveNavHost(
                    navController = navController,
                    startDestination = AppRoute.Explore,
                    isTopLevelTab = { it.isTopLevelTab() },
                    resolveTabId = { it.resolveTabId() },
                    predictiveBackState = predictiveBackState,
                ) {
                    igngComposable<AppRoute.Explore> {
                        ExploreScreen(
                            endpoints = container.endpoints,
                            exploreRepository = container.exploreRepository,
                            statusRepository = container.statusRepository,
                            chatRepository = container.chatRepository,
                            playerRepository = container.playerRepository,
                            settingsRepository = container.settingsRepository,
                            activeAccount = activeAccount,
                            sessionAuthStatus = sessionAuthStatus,
                            bottomPadding = topLevelBottomBarPadding,
                            onSignIn = { startOAuth(forceLogin = false) },
                            onOpenSearch = { navController.navigate(AppRoute.GlobalSearch) },
                            onOpenStatusWall = { navController.navigate(AppRoute.StatusWall) },
                            onOpenMap = { fam, season -> navController.navigate(AppRoute.WorldMap(fam, season)) },
                            onOpenFamilyDetail = { fam ->
                                navController.navigate(AppRoute.FamilyDetail(fam.slug))
                            },
                            onOpenCommunityHall = { serverId, tab ->
                                navController.navigate(AppRoute.CommunityHall(initialServerId = serverId, initialTab = tab))
                            },
                            onOpenPerfectWall = { navController.navigate(AppRoute.PerfectRoster) },
                            onOpenMyProfile = {
                                navController.navigate(AppRoute.PlayerCenter) {
                                    popUpTo(AppRoute.Explore) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onOpenServerChat = { serverId, title ->
                                navController.navigate(
                                    AppRoute.ChatConversation(
                                        source = "server",
                                        targetId = serverId.toString(),
                                        platform = "mc",
                                        title = title,
                                    ),
                                )
                            },
                            onOpenMyLands = { serverId -> navController.navigate(AppRoute.Lands(serverId)) },
                            onOpenMyTeleports = { serverId -> navController.navigate(AppRoute.Teleports(serverId)) },
                            onOpenMyFakePlayers = { serverId -> navController.navigate(AppRoute.FakePlayers(serverId)) },
                            onOpenVanillaStats = { serverId -> navController.navigate(AppRoute.VanillaStats(serverId)) },
                            onOpenCreateTicket = { serverId ->
                                if (activeAccount != null && sessionAuthStatus == SessionAuthStatus.ACTIVE) {
                                    navController.navigate(AppRoute.CreateTicket(serverId))
                                } else {
                                    startOAuth(forceLogin = false)
                                }
                            },
                        )
                    }

                    igngComposable<AppRoute.Chat> {
                        ChatSessionListScreen(
                            endpoints = container.endpoints,
                            chatRepository = container.chatRepository,
                            activeAccount = activeAccount,
                            sessionAuthStatus = sessionAuthStatus,
                            sessionEpoch = sessionEpoch,
                            bottomPadding = topLevelBottomBarPadding,
                            onSignIn = { startOAuth(forceLogin = false) },
                            onOpenSession = { session ->
                                navController.navigate(
                                    AppRoute.ChatConversation(
                                        source = session.apiSource,
                                        targetId = session.targetId,
                                        platform = session.platformKey,
                                        isPrivate = session.isPrivate,
                                        title = session.name,
                                        avatarUrl = session.avatarUrl,
                                        isAdminGroup = session.isAdminGroup,
                                        isTechGroup = session.isTechGroup,
                                        personaLabel = session.persona?.label,
                                        personaDescription = session.persona?.description,
                                    ),
                                )
                            },
                            onOpenCommunityChannels = { navController.navigate(AppRoute.CommunityChannels) },
                        )
                    }

                    igngComposable<AppRoute.CommunityChannels> {
                        CommunityChannelsScreen(
                            onBack = { navController.popBackStack() },
                        )
                    }

                    igngComposable<AppRoute.ChatConversation> { entry ->
                        val route = entry.toRoute<AppRoute.ChatConversation>()
                        val boundMcUsernames = remember(bootstrap) {
                            bootstrap?.viewer?.boundMcAccounts?.map { it.username }.orEmpty()
                        }
                        ChatConversationScreen(
                            source = route.source,
                            targetId = route.targetId,
                            platform = route.platform,
                            isPrivate = route.isPrivate,
                            title = route.title,
                            sessionAvatarUrl = route.avatarUrl,
                            isAdminGroup = route.isAdminGroup,
                            isTechGroup = route.isTechGroup,
                            initialPersonaLabel = route.personaLabel,
                            initialPersonaDescription = route.personaDescription,
                            initialFocusMessageId = route.initialFocusMessageId,
                            endpoints = container.endpoints,
                            chatRepository = container.chatRepository,
                            activeAccount = activeAccount,
                            boundMcUsernames = boundMcUsernames,
                            sessionEpoch = sessionEpoch,
                            onBack = { navController.popBackStack() },
                            onOpenPlayerPassport = { id -> navController.navigate(AppRoute.PlayerPassport(id)) },
                            onOpenMcCharacter = { name -> navController.navigate(AppRoute.McCharacter(name)) },
                        )
                    }

                    igngComposable<AppRoute.Wiki> {
                        WikiCatalogScreen(
                            wikiRepository = container.wikiRepository,
                            settingsRepository = container.settingsRepository,
                            bottomPadding = topLevelBottomBarPadding,
                            onOpenArticle = { pageId, resume ->
                                navController.navigate(AppRoute.WikiArticle(pageId, resumeProgress = resume))
                            },
                        )
                    }

                    igngComposable<AppRoute.PlayerCenter> {
                        PlayerCenterScreen(
                            activeAccount = activeAccount,
                            sessionAuthStatus = sessionAuthStatus,
                            sessionEpoch = sessionEpoch,
                            familyAccounts = familyAccounts,
                            hasAppUpdate = updateSnapshot.hasBadgeAlert,
                            playerRepository = container.playerRepository,
                            ticketRepository = container.ticketRepository,
                            bottomPadding = topLevelBottomBarPadding,
                            onSignIn = { startOAuth(forceLogin = false) },
                            onSyncFamilyAccount = ::syncWithFamilyAccount,
                            onOpenPassport = { id -> navController.navigate(AppRoute.PlayerPassport(id)) },
                            onNavigate = { target ->
                                when (target) {
                                    PlayerDestination.CHARACTERS -> navController.navigate(AppRoute.Characters)
                                    PlayerDestination.TICKETS -> navController.navigate(AppRoute.Tickets)
                                    PlayerDestination.CREATE_TICKET -> navController.navigate(AppRoute.CreateTicket())
                                    PlayerDestination.VAULT -> navController.navigate(AppRoute.Vault)
                                    PlayerDestination.ACTIVITY -> navController.navigate(AppRoute.LoginActivity)
                                    PlayerDestination.LANDS -> navController.navigate(AppRoute.Lands())
                                    PlayerDestination.TELEPORTS -> navController.navigate(AppRoute.Teleports())
                                    PlayerDestination.FAKE_PLAYERS -> navController.navigate(AppRoute.FakePlayers())
                                    PlayerDestination.STATS -> navController.navigate(AppRoute.VanillaStats())
                                    PlayerDestination.MEDALS -> navController.navigate(AppRoute.Medals)
                                    PlayerDestination.PERFECT -> navController.navigate(AppRoute.PersonalPerfect)
                                    PlayerDestination.PERMISSIONS -> navController.navigate(AppRoute.Permissions)
                                    PlayerDestination.ADMIN_CONSOLE -> navController.navigate(AppRoute.AdminConsole)
                                    PlayerDestination.SETTINGS -> navController.navigate(AppRoute.Settings)
                                }
                            },
                        )
                    }

                    igngComposable<AppRoute.GlobalSearch> {
                        GlobalSearchScreen(
                            exploreRepository = container.exploreRepository,
                            onBack = { navController.popBackStack() },
                            onOpenPlayerPassport = { id -> navController.navigate(AppRoute.PlayerPassport(id)) },
                            onOpenMcCharacter = { name -> navController.navigate(AppRoute.McCharacter(name)) },
                        )
                    }

                    igngComposable<AppRoute.StatusWall> {
                        StatusWallScreen(
                            statusRepository = container.statusRepository,
                            onBack = { navController.popBackStack() },
                        )
                    }

                    igngComposable<AppRoute.WorldMap>(
                        predictiveBackProfile = IgngPredictiveBackProfile.SurfaceSlide,
                    ) { entry ->
                        val route = entry.toRoute<AppRoute.WorldMap>()
                        WorldMapScreen(
                            endpoints = container.endpoints,
                            exploreRepository = container.exploreRepository,
                            initialFamilySlug = route.familySlug,
                            initialSeasonSlug = route.seasonSlug,
                            onBack = { navController.popBackStack() },
                        )
                    }

                    igngComposable<AppRoute.FamilyDetail> { entry ->
                        val route = entry.toRoute<AppRoute.FamilyDetail>()
                        ServerFamilyDetailScreen(
                            familySlug = route.familySlug,
                            exploreRepository = container.exploreRepository,
                            onBack = { navController.popBackStack() },
                            onOpenMap = { fSlug, sSlug -> navController.navigate(AppRoute.WorldMap(fSlug, sSlug)) },
                        )
                    }

                    igngComposable<AppRoute.PlayerPassport> { entry ->
                        val route = entry.toRoute<AppRoute.PlayerPassport>()
                        PlayerPassportScreen(
                            igngId = route.igngId,
                            exploreRepository = container.exploreRepository,
                            onBack = { navController.popBackStack() },
                            onOpenMcCharacter = { name -> navController.navigate(AppRoute.McCharacter(name)) },
                        )
                    }

                    igngComposable<AppRoute.McCharacter> { entry ->
                        val route = entry.toRoute<AppRoute.McCharacter>()
                        McCharacterPublicScreen(
                            username = route.username,
                            exploreRepository = container.exploreRepository,
                            onBack = { navController.popBackStack() },
                            onOpenPassport = { id -> navController.navigate(AppRoute.PlayerPassport(id)) },
                        )
                    }

                    igngComposable<AppRoute.CommunityHall> { entry ->
                        val route = entry.toRoute<AppRoute.CommunityHall>()
                        CommunityHallScreen(
                            exploreRepository = container.exploreRepository,
                            initialServerId = route.initialServerId,
                            initialTab = route.initialTab,
                            onBack = { navController.popBackStack() },
                        )
                    }

                    igngComposable<AppRoute.PerfectRoster> {
                        PerfectRosterScreen(
                            exploreRepository = container.exploreRepository,
                            onBack = { navController.popBackStack() },
                            onOpenPassport = { id -> navController.navigate(AppRoute.PlayerPassport(id)) },
                        )
                    }

                    igngComposable<AppRoute.WikiArticle> { entry ->
                        val route = entry.toRoute<AppRoute.WikiArticle>()
                        WikiArticleScreen(
                            pageId = route.pageId,
                            resumeProgress = route.resumeProgress,
                            wikiRepository = container.wikiRepository,
                            settingsRepository = container.settingsRepository,
                            onBack = { navController.popBackStack() },
                            onOpenLinkedArticle = { targetPageId ->
                                navController.navigate(AppRoute.WikiArticle(targetPageId))
                            },
                        )
                    }

                    igngComposable<AppRoute.Characters> {
                        CharactersScreen(container.endpoints, container.playerRepository) { navController.popBackStack() }
                    }
                    igngComposable<AppRoute.Vault> {
                        VaultScreen(container.playerRepository) { navController.popBackStack() }
                    }
                    igngComposable<AppRoute.Lands> { entry ->
                        val route = entry.toRoute<AppRoute.Lands>()
                        MyLandsScreen(container.playerRepository, initialServerId = route.initialServerId) { navController.popBackStack() }
                    }
                    igngComposable<AppRoute.Teleports> { entry ->
                        val route = entry.toRoute<AppRoute.Teleports>()
                        MyTeleportsScreen(container.playerRepository, initialServerId = route.initialServerId) { navController.popBackStack() }
                    }
                    igngComposable<AppRoute.FakePlayers> { entry ->
                        val route = entry.toRoute<AppRoute.FakePlayers>()
                        MyFakePlayersScreen(container.playerRepository, initialServerId = route.initialServerId) { navController.popBackStack() }
                    }
                    igngComposable<AppRoute.VanillaStats> { entry ->
                        val route = entry.toRoute<AppRoute.VanillaStats>()
                        VanillaStatsScreen(container.playerRepository, initialServerId = route.initialServerId) { navController.popBackStack() }
                    }
                    igngComposable<AppRoute.Medals> {
                        MyMedalsScreen(container.playerRepository) { navController.popBackStack() }
                    }
                    igngComposable<AppRoute.PersonalPerfect> {
                        PersonalPerfectScreen(container.playerRepository) { navController.popBackStack() }
                    }
                    igngComposable<AppRoute.Permissions> {
                        PermissionsScreen(container.playerRepository) { navController.popBackStack() }
                    }
                    igngComposable<AppRoute.LoginActivity> {
                        LoginActivityScreen(
                            bootstrap = bootstrap,
                            endpoints = container.endpoints,
                            playerRepository = container.playerRepository,
                            onBack = { navController.popBackStack() },
                        )
                    }

                    igngComposable<AppRoute.Tickets> {
                        TicketsScreen(
                            ticketRepository = container.ticketRepository,
                            onBack = { navController.popBackStack() },
                            onOpenDetail = { id -> navController.navigate(AppRoute.TicketDetail(id)) },
                            onCreateTicket = { navController.navigate(AppRoute.CreateTicket()) },
                        )
                    }

                    igngComposable<AppRoute.TicketDetail> { entry ->
                        val route = entry.toRoute<AppRoute.TicketDetail>()
                        TicketDetailScreen(
                            ticketId = route.ticketId,
                            endpoints = container.endpoints,
                            ticketRepository = container.ticketRepository,
                            onBack = { navController.popBackStack() },
                        )
                    }

                    igngComposable<AppRoute.CreateTicket> { entry ->
                        val route = entry.toRoute<AppRoute.CreateTicket>()
                        CreateTicketScreen(
                            endpoints = container.endpoints,
                            ticketRepository = container.ticketRepository,
                            initialServerId = route.initialServerId,
                            onBack = { navController.popBackStack() },
                            onCreated = { id ->
                                navController.popBackStack()
                                navController.navigate(AppRoute.TicketDetail(id))
                            },
                        )
                    }

                    igngComposable<AppRoute.AdminConsole> {
                        val role = activeAccount?.adminRole
                        if (role != null) {
                            AdminPocketConsoleScreen(
                                role = role,
                                endpoints = container.endpoints,
                                adminRepository = container.adminRepository,
                                onBack = { navController.popBackStack() },
                            )
                        } else {
                            LaunchedEffect(Unit) { navController.popBackStack() }
                        }
                    }

                    igngComposable<AppRoute.Settings> {
                        SettingsScreen(
                            container = container,
                            preferences = preferences,
                            activeAccount = activeAccount,
                            sessionAuthStatus = sessionAuthStatus,
                            accounts = accounts,
                            familyAccounts = familyAccounts,
                            onBack = { navController.popBackStack() },
                            onOpenLoginDevices = { navController.navigate(AppRoute.LoginDevices) },
                            onStartOAuthLogin = { force -> startOAuth(forceLogin = force) },
                            onSyncFamilyAccount = ::syncWithFamilyAccount,
                            onSwitchAccount = { uid -> container.switchAccount(uid) },
                            onRemoveAccount = { uid -> container.removeAccount(uid) },
                            onLogoutActive = {
                                scope.launch {
                                    container.logoutActive()
                                    snackbarHostState.showSnackbar("已退出当前账户")
                                }
                            },
                            onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                        )
                    }

                    igngComposable<AppRoute.LoginDevices> {
                        LoginDevicesScreen(
                            container = container,
                            activeAccount = activeAccount,
                            sessionAuthStatus = sessionAuthStatus,
                            onBack = { navController.popBackStack() },
                            onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                        )
                    }
                }
            }

            // Unified Floating Compact Expanding-Pill Dock (visible only on the 4 top-level tabs)
            IgngDockHost(
                visible = activeTabId != null,
                items = navItems,
                selectedId = activeTabId,
                onSelect = { id ->
                    val route: AppRoute = when (id) {
                        "chat" -> AppRoute.Chat
                        "wiki" -> AppRoute.Wiki
                        "player" -> AppRoute.PlayerCenter
                        else -> AppRoute.Explore
                    }
                    navController.navigate(route) {
                        popUpTo(AppRoute.Explore) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomBarPadding.calculateBottomPadding() + 8.dp),
            )

            // Unified OAuth exchange veil, auth error & non-destructive session re-auth / family handoff overlay
            IgngAuthOverlayHost(
                isExchanging = authUiState is AuthUiState.Busy,
                authError = (authUiState as? AuthUiState.Error)?.message,
                onDismissAuthError = { container.sessionManager.clearAuthError() },
                showSessionReauthSheet = sessionExpiredPrompt,
                sessionAuthStatus = sessionAuthStatus,
                activeAccount = activeAccount,
                familyAccounts = familyAccounts,
                onDismissSessionReauth = { sessionExpiredPrompt = false },
                onSignInWithBrowser = { startOAuth(forceLogin = false) },
                onSyncWithFamilyAccount = ::syncWithFamilyAccount,
            )
        }
        }
    }
}

const val EXTRA_SHORTCUT_ACTION = "igng_shortcut_action"
