package net.igng.mcstatus

import net.igng.mcstatus.core.model.adminRole

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.igng.core.auth.OAuthCallbackResult
import net.igng.core.data.LaunchedAppForegroundResumeEffect
import net.igng.mcstatus.core.data.IgngAppContainer
import net.igng.core.designsystem.IgngBadge
import net.igng.core.designsystem.IgngBadgeTone
import net.igng.core.designsystem.IgngButton
import net.igng.core.designsystem.IgngButtonStyle
import net.igng.core.designsystem.IgngDetailSheet
import net.igng.core.designsystem.IgngDockHost
import net.igng.core.designsystem.IgngLoadingVeil
import net.igng.core.designsystem.IgngNavTabItem
import net.igng.core.designsystem.IgngPredictiveBackProfile
import net.igng.core.designsystem.IgngPredictiveNavHost
import net.igng.core.designsystem.IgngTheme
import net.igng.core.designsystem.LocalIgngDockBottomPadding
import net.igng.core.designsystem.LocalIgngPredictiveBackState
import net.igng.core.designsystem.igngComposable
import net.igng.core.designsystem.rememberIgngDockBottomPadding
import net.igng.core.designsystem.rememberIgngPredictiveBackState
import net.igng.mcstatus.core.model.AppPreferences
import net.igng.core.model.SessionAuthStatus
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
    incomingIntentFlow: StateFlow<Intent?>,
    onIntentConsumed: () -> Unit,
) {
    val preferences by container.settingsRepository.preferences.collectAsState(initial = AppPreferences())
    val activeAccount by container.activeAccount.collectAsState()
    val accounts by container.accounts.collectAsState()
    val dailyExp by container.dailyExp.collectAsState()
    val bootstrap by container.bootstrap.collectAsState()
    val sessionEpoch by container.sessionEpoch.collectAsState()
    val sessionAuthStatus by container.sessionAuthStatus.collectAsState()
    val familyAccounts by container.familyAccounts.collectAsState()
    val incomingIntent by incomingIntentFlow.collectAsState()

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
        val intensity = IgngTheme.motionIntensity
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            container.refreshFamilyAccounts()
        }
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    scope.launch { container.refreshFamilyAccounts() }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        var authExchanging by remember { mutableStateOf(false) }
        var authError by remember { mutableStateOf<String?>(null) }
        var sessionExpiredPrompt by remember { mutableStateOf(false) }
        var promptedLegacyUserId by remember { mutableStateOf<Int?>(null) }

        fun startOAuth(forceLogin: Boolean) {
            authError = null
            container.sessionManager.launchLogin(
                context = context,
                isDark = colors.isDark,
                forceSwitchAccount = forceLogin,
            )
        }

        // Handle OAuth callbacks & App Shortcut intents via unified IgngSessionManager
        LaunchedEffect(incomingIntent) {
            val intent = incomingIntent ?: return@LaunchedEffect
            onIntentConsumed()
            val data = intent.data
            if (container.sessionManager.isMatchingCallback(data)) {
                authExchanging = true
                when (val result = container.handleOAuthCallback(data)) {
                    is OAuthCallbackResult.Success -> {
                        sessionExpiredPrompt = false
                        snackbarHostState.showSnackbar("欢迎回来，${result.account.displayName}")
                    }
                    is OAuthCallbackResult.Error -> {
                        authError = result.message
                    }
                    OAuthCallbackResult.Ignored -> Unit
                }
                authExchanging = false
                return@LaunchedEffect
            }

            when (intent.getStringExtra(EXTRA_SHORTCUT_ACTION)) {
                "status" -> navController.navigate(AppRoute.StatusWall) { launchSingleTop = true }
                "map" -> navController.navigate(AppRoute.WorldMap()) { launchSingleTop = true }
                "chat" -> navController.navigate(AppRoute.Chat) { launchSingleTop = true }
                "create_ticket" -> {
                    if (container.activeAccount.value != null) {
                        navController.navigate(AppRoute.CreateTicket) { launchSingleTop = true }
                    } else {
                        navController.navigate(AppRoute.PlayerCenter) { launchSingleTop = true }
                    }
                }
            }
        }

        suspend fun refreshBootstrapAndDailyExp() {
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
                        container.saveAndActivateAccount(updated, resetDailyExp = false)
                    }
                }
            }
            if (boot?.viewer?.authenticated == true) {
                val exp = container.settleDailyLoginForCurrentSession()
                container.updateDailyExp(exp)
            } else if (boot != null) {
                container.updateDailyExp(null)
            }
        }

        // Refresh bootstrap state, user metadata, admin role & settle daily login EXP whenever active session changes
        LaunchedEffect(activeAccount?.userId, sessionEpoch) {
            refreshBootstrapAndDailyExp()
        }

        // Idle low-priority prefetch for sibling top-level tabs after first frame settles
        LaunchedEffect(activeAccount?.userId, sessionEpoch, sessionAuthStatus) {
            delay(750L)
            container.prefetchIdleTabs()
        }

        // Silent bootstrap refresh when returning from background after >= 30s
        LaunchedAppForegroundResumeEffect(minBackgroundMs = 30_000L) {
            refreshBootstrapAndDailyExp()
        }

        // Prompt once for pre-v2.1.0 legacy sessions whose tokens are suppressed before hitting 401
        LaunchedEffect(activeAccount?.userId, sessionAuthStatus) {
            val uid = activeAccount?.userIdInt
            if (uid != null && sessionAuthStatus == SessionAuthStatus.LEGACY_UPGRADE_REQUIRED && promptedLegacyUserId != uid) {
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
                IgngNavTabItem("explore", "探索", Icons.Rounded.Explore),
                IgngNavTabItem("chat", "聊天", Icons.Rounded.Forum),
                IgngNavTabItem("wiki", "百科", Icons.Rounded.AutoStories),
                IgngNavTabItem("player", "我的", Icons.Rounded.Person),
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
                            exploreRepository = container.exploreRepository,
                            statusRepository = container.statusRepository,
                            activeAccount = activeAccount,
                            sessionAuthStatus = sessionAuthStatus,
                            bottomPadding = topLevelBottomBarPadding,
                            onOpenSearch = { navController.navigate(AppRoute.GlobalSearch) },
                            onOpenStatusWall = { navController.navigate(AppRoute.StatusWall) },
                            onOpenMap = { fam, season -> navController.navigate(AppRoute.WorldMap(fam, season)) },
                            onOpenFamilyDetail = { fam ->
                                navController.navigate(AppRoute.FamilyDetail(fam.slug))
                            },
                            onOpenCommunityHall = { navController.navigate(AppRoute.CommunityHall) },
                            onOpenPerfectWall = { navController.navigate(AppRoute.PerfectRoster) },
                            onOpenMyProfile = {
                                navController.navigate(AppRoute.PlayerCenter) {
                                    popUpTo(AppRoute.Explore) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
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
                            onOpenArticle = { pageId -> navController.navigate(AppRoute.WikiArticle(pageId)) },
                        )
                    }

                    igngComposable<AppRoute.PlayerCenter> {
                        PlayerCenterScreen(
                            activeAccount = activeAccount,
                            sessionAuthStatus = sessionAuthStatus,
                            sessionEpoch = sessionEpoch,
                            dailyExp = dailyExp,
                            familyAccounts = familyAccounts,
                            playerRepository = container.playerRepository,
                            ticketRepository = container.ticketRepository,
                            bottomPadding = topLevelBottomBarPadding,
                            onSignIn = { startOAuth(forceLogin = false) },
                            onOpenPassport = { id -> navController.navigate(AppRoute.PlayerPassport(id)) },
                            onNavigate = { target ->
                                when (target) {
                                    PlayerDestination.CHARACTERS -> navController.navigate(AppRoute.Characters)
                                    PlayerDestination.TICKETS -> navController.navigate(AppRoute.Tickets)
                                    PlayerDestination.CREATE_TICKET -> navController.navigate(AppRoute.CreateTicket)
                                    PlayerDestination.VAULT -> navController.navigate(AppRoute.Vault)
                                    PlayerDestination.ACTIVITY -> navController.navigate(AppRoute.LoginActivity)
                                    PlayerDestination.LANDS -> navController.navigate(AppRoute.Lands)
                                    PlayerDestination.TELEPORTS -> navController.navigate(AppRoute.Teleports)
                                    PlayerDestination.FAKE_PLAYERS -> navController.navigate(AppRoute.FakePlayers)
                                    PlayerDestination.STATS -> navController.navigate(AppRoute.VanillaStats)
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

                    igngComposable<AppRoute.CommunityHall> {
                        CommunityHallScreen(
                            exploreRepository = container.exploreRepository,
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
                            wikiRepository = container.wikiRepository,
                            settingsRepository = container.settingsRepository,
                            onBack = { navController.popBackStack() },
                        )
                    }

                    igngComposable<AppRoute.Characters> {
                        CharactersScreen(container.endpoints, container.playerRepository) { navController.popBackStack() }
                    }
                    igngComposable<AppRoute.Vault> {
                        VaultScreen(container.playerRepository) { navController.popBackStack() }
                    }
                    igngComposable<AppRoute.Lands> {
                        MyLandsScreen(container.playerRepository) { navController.popBackStack() }
                    }
                    igngComposable<AppRoute.Teleports> {
                        MyTeleportsScreen(container.playerRepository) { navController.popBackStack() }
                    }
                    igngComposable<AppRoute.FakePlayers> {
                        MyFakePlayersScreen(container.playerRepository) { navController.popBackStack() }
                    }
                    igngComposable<AppRoute.VanillaStats> {
                        VanillaStatsScreen(container.playerRepository) { navController.popBackStack() }
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
                            onCreateTicket = { navController.navigate(AppRoute.CreateTicket) },
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

                    igngComposable<AppRoute.CreateTicket> {
                        CreateTicketScreen(
                            endpoints = container.endpoints,
                            ticketRepository = container.ticketRepository,
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
                            onStartOAuthLogin = { force -> startOAuth(forceLogin = force) },
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

            // Full-screen Folded Core veil during OAuth PKCE token exchange
            IgngLoadingVeil(visible = authExchanging)

            if (authError != null) {
                IgngDetailSheet(
                    onDismiss = { authError = null },
                    title = "登录未完成",
                ) {
                    IgngBadge(authError!!, tone = IgngBadgeTone.Danger)
                    Spacer(Modifier.height(14.dp))
                    IgngButton(
                        text = "重新通过 IGNG SSO 登录",
                        onClick = {
                            authError = null
                            startOAuth(forceLogin = false)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (sessionExpiredPrompt) {
                val isLegacyUpgrade = sessionAuthStatus == SessionAuthStatus.LEGACY_UPGRADE_REQUIRED
                IgngDetailSheet(
                    onDismiss = { sessionExpiredPrompt = false },
                    title = if (isLegacyUpgrade) "需要升级登录授权" else "登录会话已失效",
                    subtitle = "重新授权后即可继续，你正在编辑的内容不会被清除。",
                ) {
                    Text(
                        text = if (isLegacyUpgrade) {
                            "本版本已升级至 OAuth 2.1 安全认证架构，旧版登录凭证已停用。重新授权前仅可浏览公开内容与本地缓存的账户标识。"
                        } else {
                            "当前设备的 IGNG 登录会话可能已过期或在账户中心被撤销。重新授权前仅可浏览公开内容与本地缓存的账户标识。"
                        },
                        style = IgngTheme.type.body,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(14.dp))
                    IgngButton(
                        text = "重新授权登录",
                        onClick = {
                            sessionExpiredPrompt = false
                            startOAuth(forceLogin = false)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    IgngButton(
                        text = "稍后再说",
                        onClick = { sessionExpiredPrompt = false },
                        style = IgngButtonStyle.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        }
    }
}

const val EXTRA_SHORTCUT_ACTION = "igng_shortcut_action"
