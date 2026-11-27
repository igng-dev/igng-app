package net.igng.bbs

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
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
import net.igng.bbs.core.data.BbsAppContainer
import net.igng.bbs.core.model.AppPreferences
import net.igng.bbs.feature.admin.AdminConsoleScreen
import net.igng.bbs.feature.column.ColumnDetailScreen
import net.igng.bbs.feature.column.ColumnSquareScreen
import net.igng.bbs.feature.creator.CreatorColumnsScreen
import net.igng.bbs.feature.creator.CreatorCommentsScreen
import net.igng.bbs.feature.creator.CreatorHubScreen
import net.igng.bbs.feature.creator.CreatorMediaScreen
import net.igng.bbs.feature.creator.CreatorPostsScreen
import net.igng.bbs.feature.creator.CreatorReviewsScreen
import net.igng.bbs.feature.creator.CreatorSeriesScreen
import net.igng.bbs.feature.creator.NativeWysiwygEditorScreen
import net.igng.bbs.feature.feed.CategoryFeedScreen
import net.igng.bbs.feature.feed.FeedHomeScreen
import net.igng.bbs.feature.feed.RulesScreen
import net.igng.bbs.feature.feed.SearchScreen
import net.igng.bbs.feature.feed.TagFeedScreen
import net.igng.bbs.feature.inbox.InboxScreen
import net.igng.bbs.feature.post.PostReaderScreen
import net.igng.bbs.feature.profile.AppSettingsScreen
import net.igng.bbs.feature.profile.FavoritesScreen
import net.igng.bbs.feature.profile.FollowingScreen
import net.igng.bbs.feature.profile.PrivacyPreferencesScreen
import net.igng.bbs.feature.profile.ProfileCenterScreen
import net.igng.bbs.feature.profile.ReadingHistoryScreen
import net.igng.bbs.feature.profile.UserProfileScreen
import net.igng.core.auth.AuthUiState
import net.igng.core.data.LaunchedAppForegroundResumeEffect
import net.igng.core.data.LaunchedForegroundPollingEffect
import net.igng.core.designsystem.IgngAmbientCanvas
import net.igng.core.designsystem.IgngAuthOverlayHost
import net.igng.core.designsystem.IgngBrowserLauncher
import net.igng.core.designsystem.IgngDockHost
import net.igng.core.designsystem.IgngNavTabItem
import net.igng.core.designsystem.IgngPredictiveNavHost
import net.igng.core.designsystem.IgngPullRefreshStyle
import net.igng.core.designsystem.IgngTheme
import net.igng.core.designsystem.LocalIgngDockBottomPadding
import net.igng.core.designsystem.LocalIgngPredictiveBackState
import net.igng.core.designsystem.LocalIgngPullRefreshStyle
import net.igng.core.designsystem.igngComposable
import net.igng.core.designsystem.rememberIgngDockBottomPadding
import net.igng.core.designsystem.rememberIgngPredictiveBackState
import net.igng.core.model.FamilySharedAccountHint
import net.igng.core.model.SessionAuthStatus

@Composable
fun BbsAppRoot(
    container: BbsAppContainer,
    incomingDeepLinks: Flow<BbsRoute>,
) {
    val prefs by container.settingsRepository.preferences.collectAsState(initial = AppPreferences())
    val activeAccount by container.activeAccount.collectAsState()
    val sessionAuthStatus by container.sessionAuthStatus.collectAsState()
    val sessionEpoch by container.sessionEpoch.collectAsState()
    val familyAccounts by container.familyAccounts.collectAsState()
    val authUiState by container.authUiState.collectAsState()
    val initialFamilySyncDone by container.initialFamilySyncDone.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    IgngTheme(
        themeMode = prefs.themeMode,
        motionIntensity = prefs.motionIntensity,
        hapticsEnabled = prefs.hapticsEnabled,
    ) {
        val colors = IgngTheme.colors
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }

        var sessionExpiredReason by remember { mutableStateOf<String?>(null) }
        var promptedLegacyUserId by remember { mutableStateOf<Long?>(null) }

        fun launchSso(forceSwitchAccount: Boolean = false) {
            container.oauthManager.launchLogin(
                context = context,
                isDark = colors.isDark,
                forceSwitchAccount = forceSwitchAccount,
            )
        }

        fun syncWithFamilyAccount(hint: FamilySharedAccountHint) {
            container.signInWithFamilyAccount(
                context = context,
                hint = hint,
                isDark = colors.isDark,
            )
        }

        // Cross-app family SSO discovery & silent handoff on launch & resume
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

        // Initial hydration + bootstrap refresh whenever session changes
        LaunchedEffect(activeAccount?.userId, sessionEpoch) {
            container.syncSessionStampNow()
            container.hydrateCachedBootstrap()
            container.refreshBootstrap(force = true)
        }

        // Silent foreground resume refresh after returning from >= 30s background
        LaunchedAppForegroundResumeEffect(minBackgroundMs = 30_000L) {
            container.refreshBootstrapIfStale()
        }

        // Keep unread inbox dot & viewer state silently up to date while app stays foregrounded
        LaunchedForegroundPollingEffect(
            intervalMs = 60_000L,
            key1 = activeAccount?.userId,
            key2 = sessionAuthStatus,
            enabled = activeAccount != null && !sessionAuthStatus.requiresReauth,
        ) {
            container.refreshBootstrap(force = true)
        }

        // Cold-start idle prefetch for sibling top-level tabs after first frame settles
        LaunchedEffect(activeAccount?.userId, sessionEpoch) {
            delay(750L)
            container.prefetchIdleTabs()
        }

        // Prompt once for legacy sessions requiring OAuth 2.1 re-auth (after initial family sync attempt)
        LaunchedEffect(activeAccount?.userId, sessionAuthStatus, initialFamilySyncDone) {
            if (sessionAuthStatus == SessionAuthStatus.ACTIVE) {
                sessionExpiredReason = null
                return@LaunchedEffect
            }
            val uid = activeAccount?.userId
            if (
                initialFamilySyncDone &&
                uid != null &&
                sessionAuthStatus == SessionAuthStatus.LEGACY_UPGRADE_REQUIRED &&
                promptedLegacyUserId != uid
            ) {
                promptedLegacyUserId = uid
                sessionExpiredReason = "本版本已升级至统一 OAuth 2.1 安全认证架构，旧版凭证已停用。重新授权前可继续浏览公开内容与本地记录。"
            }
        }

        // Toggle background worker based on preferences
        LaunchedEffect(prefs.backgroundNotificationsEnabled) {
            if (prefs.backgroundNotificationsEnabled) {
                NotificationSyncWorker.schedule(context)
            } else {
                NotificationSyncWorker.cancel(context)
            }
        }

        // Global 401 session expired observer (non-destructive)
        LaunchedEffect(Unit) {
            container.sessionExpiredNotifier.events.collect { event ->
                if (container.activeAccount.value != null) {
                    sessionExpiredReason = event.message
                }
            }
        }

        // Unified OAuth & family handoff toast messages
        LaunchedEffect(Unit) {
            container.authEvents.collect { msg ->
                snackbarHostState.showSnackbar(msg)
            }
        }

        // Incoming App Links & Shortcuts
        LaunchedEffect(Unit) {
            incomingDeepLinks.collect { route ->
                navController.navigate(route) {
                    launchSingleTop = true
                }
            }
        }

        fun openUrlOrDeepLink(url: String) {
            val uri = runCatching { Uri.parse(url) }.getOrNull()
            val internal = AppRoutes.resolveDeepLink(uri)
            if (internal != null) {
                navController.navigate(internal)
            } else if (uri != null && (uri.scheme == "https" || uri.scheme == "http")) {
                IgngBrowserLauncher.openExternalUrl(context, url, colors.isDark)
            }
        }

        fun NavDestination?.resolveTabId(): String? = when {
            this?.hasRoute<BbsRoute.Feed>() == true -> "feed"
            this?.hasRoute<BbsRoute.Columns>() == true -> "columns"
            this?.hasRoute<BbsRoute.Creator>() == true -> "creator"
            this?.hasRoute<BbsRoute.Profile>() == true -> "profile"
            else -> null
        }

        fun NavDestination?.isTopLevelTab(): Boolean = resolveTabId() != null

        val currentBackStack by navController.currentBackStackEntryAsState()
        val dest = currentBackStack?.destination
        val activeTabId = dest.resolveTabId()
        val showBottomBar = activeTabId != null
        val dockBottomPadding = rememberIgngDockBottomPadding(dockVisible = showBottomBar)
        val topLevelDockBottomPadding = rememberIgngDockBottomPadding(dockVisible = true)
        val predictiveBackState = rememberIgngPredictiveBackState()

        val tabs = remember {
            listOf(
                IgngNavTabItem("feed", "首页", Icons.Rounded.Home, Icons.Outlined.Home),
                IgngNavTabItem("columns", "专栏", Icons.Rounded.FolderShared, Icons.Outlined.FolderShared),
                IgngNavTabItem("creator", "创作", Icons.Rounded.Create, Icons.Outlined.Create),
                IgngNavTabItem("profile", "我的", Icons.Rounded.Person, Icons.Outlined.Person),
            )
        }

        CompositionLocalProvider(
            LocalIgngDockBottomPadding provides dockBottomPadding,
            LocalIgngPredictiveBackState provides predictiveBackState,
            LocalIgngPullRefreshStyle provides IgngPullRefreshStyle.TopBar,
        ) {
            IgngAmbientCanvas(modifier = Modifier.fillMaxSize()) {
                IgngPredictiveNavHost(
                    navController = navController,
                    startDestination = BbsRoute.Feed,
                    isTopLevelTab = { it.isTopLevelTab() },
                    resolveTabId = { it.resolveTabId() },
                    predictiveBackState = predictiveBackState,
                ) {
                    igngComposable<BbsRoute.Feed> {
                        FeedHomeScreen(
                            container = container,
                            onOpenPost = { navController.navigate(BbsRoute.PostDetail(it)) },
                            onOpenCategory = { slug, title -> navController.navigate(BbsRoute.Category(slug, title)) },
                            onOpenTag = { navController.navigate(BbsRoute.Tag(it)) },
                            onOpenUser = { navController.navigate(BbsRoute.UserProfile(it)) },
                            onOpenSearch = { navController.navigate(BbsRoute.Search) },
                            onOpenInbox = { navController.navigate(BbsRoute.Inbox) },
                            onOpenRules = { navController.navigate(BbsRoute.Rules) },
                            bottomInsetPadding = topLevelDockBottomPadding,
                        )
                    }

                    igngComposable<BbsRoute.Columns> {
                        ColumnSquareScreen(
                            container = container,
                            onOpenColumn = { navController.navigate(BbsRoute.ColumnDetail(it)) },
                            onOpenCreatorColumns = { navController.navigate(BbsRoute.CreatorColumns) },
                            bottomInsetPadding = topLevelDockBottomPadding,
                        )
                    }

                    igngComposable<BbsRoute.Creator> {
                        CreatorHubScreen(
                            container = container,
                            onNewArticle = { navController.navigate(BbsRoute.CreatorEditor()) },
                            onEditPost = { navController.navigate(BbsRoute.CreatorEditor(postId = it)) },
                            onOpenPosts = { navController.navigate(BbsRoute.CreatorPosts) },
                            onOpenColumns = { navController.navigate(BbsRoute.CreatorColumns) },
                            onOpenSeries = { navController.navigate(BbsRoute.CreatorSeries) },
                            onOpenComments = { navController.navigate(BbsRoute.CreatorComments) },
                            onOpenReviews = { navController.navigate(BbsRoute.CreatorReviews) },
                            onOpenMedia = { navController.navigate(BbsRoute.CreatorMedia) },
                            onRequireLogin = { launchSso() },
                            bottomInsetPadding = topLevelDockBottomPadding,
                        )
                    }

                    igngComposable<BbsRoute.Profile> {
                        ProfileCenterScreen(
                            container = container,
                            onLogin = { launchSso() },
                            onSyncFamilyAccount = ::syncWithFamilyAccount,
                            onOpenMyProfile = { navController.navigate(BbsRoute.UserProfile(it)) },
                            onOpenFavorites = { navController.navigate(BbsRoute.Favorites) },
                            onOpenFollowing = { navController.navigate(BbsRoute.Following) },
                            onOpenHistory = { navController.navigate(BbsRoute.History) },
                            onOpenCreator = {
                                navController.navigate(BbsRoute.Creator) {
                                    popUpTo(BbsRoute.Feed) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onOpenAdmin = { navController.navigate(BbsRoute.AdminConsole) },
                            onOpenPrivacyPreferences = { navController.navigate(BbsRoute.PrivacyPreferences) },
                            onOpenSettings = { navController.navigate(BbsRoute.AppSettings) },
                            onOpenRules = { navController.navigate(BbsRoute.Rules) },
                            bottomInsetPadding = topLevelDockBottomPadding,
                        )
                    }

                    igngComposable<BbsRoute.Search> {
                        SearchScreen(
                            container = container,
                            onBack = { navController.popBackStack() },
                            onOpenPost = { navController.navigate(BbsRoute.PostDetail(it)) },
                            onOpenColumn = { navController.navigate(BbsRoute.ColumnDetail(it)) },
                            onOpenUser = { navController.navigate(BbsRoute.UserProfile(it)) },
                            onOpenTag = { navController.navigate(BbsRoute.Tag(it)) },
                        )
                    }

                    igngComposable<BbsRoute.Rules> {
                        RulesScreen(container = container, onBack = { navController.popBackStack() })
                    }

                    igngComposable<BbsRoute.Inbox> {
                        InboxScreen(
                            container = container,
                            onBack = { navController.popBackStack() },
                            onOpenPost = { navController.navigate(BbsRoute.PostDetail(it)) },
                            onRequireLogin = { launchSso() },
                        )
                    }

                    igngComposable<BbsRoute.Category> { entry ->
                        val route = entry.toRoute<BbsRoute.Category>()
                        CategoryFeedScreen(
                            container = container,
                            categorySlug = route.slug,
                            categoryTitle = route.title,
                            onBack = { navController.popBackStack() },
                            onOpenPost = { navController.navigate(BbsRoute.PostDetail(it)) },
                            onOpenUser = { navController.navigate(BbsRoute.UserProfile(it)) },
                            onOpenTag = { navController.navigate(BbsRoute.Tag(it)) },
                        )
                    }

                    igngComposable<BbsRoute.Tag> { entry ->
                        val route = entry.toRoute<BbsRoute.Tag>()
                        TagFeedScreen(
                            container = container,
                            tag = route.tag,
                            onBack = { navController.popBackStack() },
                            onOpenPost = { navController.navigate(BbsRoute.PostDetail(it)) },
                            onOpenUser = { navController.navigate(BbsRoute.UserProfile(it)) },
                            onOpenTag = { navController.navigate(BbsRoute.Tag(it)) },
                        )
                    }

                    igngComposable<BbsRoute.ColumnDetail> { entry ->
                        val route = entry.toRoute<BbsRoute.ColumnDetail>()
                        ColumnDetailScreen(
                            container = container,
                            columnIdOrSlug = route.columnId,
                            onBack = { navController.popBackStack() },
                            onOpenPost = { navController.navigate(BbsRoute.PostDetail(it)) },
                            onOpenColumn = { navController.navigate(BbsRoute.ColumnDetail(it)) },
                            onOpenUser = { navController.navigate(BbsRoute.UserProfile(it)) },
                            onOpenEditorForColumn = { colId -> navController.navigate(BbsRoute.CreatorEditor(columnId = colId)) },
                            onRequireLogin = { launchSso() },
                        )
                    }

                    igngComposable<BbsRoute.PostDetail> { entry ->
                        val route = entry.toRoute<BbsRoute.PostDetail>()
                        PostReaderScreen(
                            container = container,
                            postId = route.postId,
                            onBack = { navController.popBackStack() },
                            onOpenPost = { navController.navigate(BbsRoute.PostDetail(it)) },
                            onOpenColumn = { navController.navigate(BbsRoute.ColumnDetail(it)) },
                            onOpenUser = { navController.navigate(BbsRoute.UserProfile(it)) },
                            onOpenTag = { navController.navigate(BbsRoute.Tag(it)) },
                            onOpenLink = ::openUrlOrDeepLink,
                            onEditPost = { navController.navigate(BbsRoute.CreatorEditor(postId = it)) },
                            onRequireLogin = { launchSso() },
                        )
                    }

                    igngComposable<BbsRoute.UserProfile> { entry ->
                        val route = entry.toRoute<BbsRoute.UserProfile>()
                        UserProfileScreen(
                            container = container,
                            userIdOrUsername = route.userId,
                            onBack = { navController.popBackStack() },
                            onOpenPost = { navController.navigate(BbsRoute.PostDetail(it)) },
                            onRequireLogin = { launchSso() },
                        )
                    }

                    igngComposable<BbsRoute.CreatorEditor> { entry ->
                        val route = entry.toRoute<BbsRoute.CreatorEditor>()
                        NativeWysiwygEditorScreen(
                            container = container,
                            postId = route.postId?.takeIf { it.isNotBlank() },
                            initialColumnId = route.columnId?.takeIf { it.isNotBlank() },
                            onBack = { navController.popBackStack() },
                            onPublished = { publishedId ->
                                navController.popBackStack()
                                navController.navigate(BbsRoute.PostDetail(publishedId))
                            },
                        )
                    }

                    igngComposable<BbsRoute.CreatorPosts> {
                        CreatorPostsScreen(
                            container = container,
                            onBack = { navController.popBackStack() },
                            onNewArticle = { navController.navigate(BbsRoute.CreatorEditor()) },
                            onEditPost = { navController.navigate(BbsRoute.CreatorEditor(postId = it)) },
                            onOpenPost = { navController.navigate(BbsRoute.PostDetail(it)) },
                        )
                    }

                    igngComposable<BbsRoute.CreatorColumns> {
                        CreatorColumnsScreen(
                            container = container,
                            onBack = { navController.popBackStack() },
                            onOpenColumn = { navController.navigate(BbsRoute.ColumnDetail(it)) },
                        )
                    }

                    igngComposable<BbsRoute.CreatorSeries> {
                        CreatorSeriesScreen(container = container, onBack = { navController.popBackStack() })
                    }

                    igngComposable<BbsRoute.CreatorComments> {
                        CreatorCommentsScreen(
                            container = container,
                            onBack = { navController.popBackStack() },
                            onOpenPost = { navController.navigate(BbsRoute.PostDetail(it)) },
                        )
                    }

                    igngComposable<BbsRoute.CreatorReviews> {
                        CreatorReviewsScreen(container = container, onBack = { navController.popBackStack() })
                    }

                    igngComposable<BbsRoute.CreatorMedia> {
                        CreatorMediaScreen(container = container, onBack = { navController.popBackStack() })
                    }

                    igngComposable<BbsRoute.Favorites> {
                        FavoritesScreen(
                            container = container,
                            onBack = { navController.popBackStack() },
                            onOpenPost = { navController.navigate(BbsRoute.PostDetail(it)) },
                        )
                    }

                    igngComposable<BbsRoute.Following> {
                        FollowingScreen(
                            container = container,
                            onBack = { navController.popBackStack() },
                            onOpenUser = { navController.navigate(BbsRoute.UserProfile(it)) },
                        )
                    }

                    igngComposable<BbsRoute.History> {
                        ReadingHistoryScreen(
                            container = container,
                            onBack = { navController.popBackStack() },
                            onOpenPost = { navController.navigate(BbsRoute.PostDetail(it)) },
                        )
                    }

                    igngComposable<BbsRoute.AccountSecurity> {
                        AppSettingsScreen(
                            container = container,
                            onBack = { navController.popBackStack() },
                            onSignIn = { launchSso(forceSwitchAccount = false) },
                            onAddAccount = { launchSso(forceSwitchAccount = true) },
                            onSyncFamilyAccount = ::syncWithFamilyAccount,
                            onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                        )
                    }

                    igngComposable<BbsRoute.PrivacyPreferences> {
                        PrivacyPreferencesScreen(container = container, onBack = { navController.popBackStack() })
                    }

                    igngComposable<BbsRoute.AppSettings> {
                        AppSettingsScreen(
                            container = container,
                            onBack = { navController.popBackStack() },
                            onSignIn = { launchSso(forceSwitchAccount = false) },
                            onAddAccount = { launchSso(forceSwitchAccount = true) },
                            onSyncFamilyAccount = ::syncWithFamilyAccount,
                            onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                        )
                    }

                    igngComposable<BbsRoute.AdminConsole> {
                        AdminConsoleScreen(
                            container = container,
                            onBack = { navController.popBackStack() },
                            onOpenPost = { navController.navigate(BbsRoute.PostDetail(it)) },
                            onOpenUser = { navController.navigate(BbsRoute.UserProfile(it)) },
                        )
                    }
                }

                // Unified Floating Compact Expanding-Pill Dock
                IgngDockHost(
                    visible = showBottomBar,
                    items = tabs,
                    selectedId = activeTabId,
                    onSelect = { target ->
                        if (target != activeTabId) {
                            val route: BbsRoute = when (target) {
                                "columns" -> BbsRoute.Columns
                                "creator" -> BbsRoute.Creator
                                "profile" -> BbsRoute.Profile
                                else -> BbsRoute.Feed
                            }
                            navController.navigate(route) {
                                popUpTo(BbsRoute.Feed) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = dockBottomPadding.calculateBottomPadding() + 8.dp),
                )

                // Unified OAuth exchange veil, auth error & non-destructive session re-auth / family handoff overlay
                IgngAuthOverlayHost(
                    isExchanging = authUiState is AuthUiState.Busy,
                    authError = (authUiState as? AuthUiState.Error)?.message,
                    onDismissAuthError = { container.sessionManager.clearAuthError() },
                    showSessionReauthSheet = sessionExpiredReason != null,
                    sessionAuthStatus = sessionAuthStatus,
                    activeAccount = activeAccount,
                    familyAccounts = familyAccounts,
                    onDismissSessionReauth = { sessionExpiredReason = null },
                    onSignInWithBrowser = { launchSso(forceSwitchAccount = false) },
                    onSyncWithFamilyAccount = ::syncWithFamilyAccount,
                    sessionReauthReasonOverride = sessionExpiredReason,
                )
            }
        }
    }
}
