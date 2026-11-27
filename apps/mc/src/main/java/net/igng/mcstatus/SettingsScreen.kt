package net.igng.mcstatus

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.igng.core.data.rememberIgngDevicesResource
import net.igng.core.designsystem.IgngAboutAppCard
import net.igng.core.designsystem.IgngAccountVaultCard
import net.igng.core.designsystem.IgngAmbientCanvas
import net.igng.core.designsystem.IgngAppUpdateCard
import net.igng.core.designsystem.IgngAppUpdateSheet
import net.igng.core.designsystem.IgngAppearanceMotionSettingsCard
import net.igng.core.designsystem.IgngBrowserLauncher
import net.igng.core.designsystem.IgngCacheCard
import net.igng.core.designsystem.IgngDevicesSecurityEntryCard
import net.igng.core.designsystem.IgngDevicesSecurityScreen
import net.igng.core.designsystem.IgngProfileIdentityCard
import net.igng.core.designsystem.IgngTheme
import net.igng.core.designsystem.IgngTopBar
import net.igng.core.model.FamilySharedAccountHint
import net.igng.core.model.SavedAccount
import net.igng.core.model.SessionAuthStatus
import net.igng.core.network.ApiResult
import net.igng.mcstatus.core.data.IgngAppContainer
import net.igng.mcstatus.core.data.McCacheKeys
import net.igng.mcstatus.core.data.McCachePolicies
import net.igng.mcstatus.core.data.McCacheTags
import net.igng.mcstatus.core.model.AppPreferences

@Composable
fun SettingsScreen(
    container: IgngAppContainer,
    preferences: AppPreferences,
    activeAccount: SavedAccount?,
    sessionAuthStatus: SessionAuthStatus = if (activeAccount != null) SessionAuthStatus.ACTIVE else SessionAuthStatus.SIGNED_OUT,
    accounts: List<SavedAccount>,
    familyAccounts: List<FamilySharedAccountHint> = emptyList(),
    onBack: () -> Unit,
    onOpenLoginDevices: () -> Unit,
    onStartOAuthLogin: (forceLogin: Boolean) -> Unit,
    onSyncFamilyAccount: (FamilySharedAccountHint) -> Unit,
    onSwitchAccount: (Long) -> Unit,
    onRemoveAccount: (Long) -> Unit,
    onLogoutActive: () -> Unit,
    onShowMessage: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val colors = IgngTheme.colors
    val scope = rememberCoroutineScope()
    val updateSnapshot by container.appUpdateManager.snapshot.collectAsState()

    var cacheBytes by remember { mutableLongStateOf(0L) }
    var clearingCache by remember { mutableStateOf(false) }
    var showUpdateSheet by remember { mutableStateOf(false) }

    var usernameInput by remember(activeAccount?.username) { mutableStateOf(activeAccount?.username.orEmpty()) }
    var uploadingAvatar by remember { mutableStateOf(false) }
    var savingUsername by remember { mutableStateOf(false) }

    val canLoadDevices = activeAccount != null && !sessionAuthStatus.requiresReauth
    val devicesController = rememberIgngDevicesResource(
        cache = container.swrCache,
        accountRepository = container.accountRepository,
        cacheKey = McCacheKeys.CORE_DEVICES,
        policy = McCachePolicies.Standard,
        tags = setOf(McCacheTags.DEVICES),
        enabled = canLoadDevices,
        slotKey = "mc_settings_devices",
        onShowMessage = onShowMessage,
    )

    LaunchedEffect(Unit) {
        cacheBytes = container.cacheSizeBytes()
        container.syncFamilyAccountsAndAutoSignIn()
    }

    LifecycleResumeEffect(Unit) {
        container.appUpdateManager.onResumeHost()
        onPauseOrDispose { }
    }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            uploadingAvatar = true
            val payload = withContext(Dispatchers.IO) {
                runCatching {
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
                    Triple(bytes, mime, "avatar_${System.currentTimeMillis()}.jpg")
                }.getOrNull()
            }
            if (payload != null) {
                when (val res = container.accountRepository.uploadAvatar(payload.third, payload.second, payload.first)) {
                    is ApiResult.Success -> {
                        container.sessionManager.updateActiveProfile(avatarUrl = res.data.avatarUrl)
                        onShowMessage("头像已更新并同步")
                    }
                    is ApiResult.Failure -> onShowMessage(res.error.message)
                }
            } else {
                onShowMessage("无法读取所选图片")
            }
            uploadingAvatar = false
        }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    IgngAmbientCanvas {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            IgngTopBar(title = "设置", onBack = onBack)
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 24.dp + navBottom,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 1. Unified Multi-Account Vault + Cross-App Family SSO
                item(key = "accounts") {
                    IgngAccountVaultCard(
                        activeAccount = activeAccount,
                        accounts = accounts,
                        sessionAuthStatus = sessionAuthStatus,
                        familyAccounts = familyAccounts,
                        onSignIn = { onStartOAuthLogin(false) },
                        onAddAccount = { onStartOAuthLogin(true) },
                        onSyncFamilyAccount = onSyncFamilyAccount,
                        onSwitchAccount = { uid ->
                            onSwitchAccount(uid)
                            onShowMessage("已切换当前账户")
                        },
                        onRemoveAccount = { uid ->
                            onRemoveAccount(uid)
                            onShowMessage("已移除该账户记录")
                        },
                        onLogoutActive = onLogoutActive,
                    )
                }

                // 2. Unified Profile Identity & Login Devices Security (shared Client API v1)
                if (activeAccount != null && !sessionAuthStatus.requiresReauth) {
                    item(key = "profile_identity") {
                        IgngProfileIdentityCard(
                            activeAccount = activeAccount,
                            usernameInput = usernameInput,
                            onUsernameChange = { usernameInput = it },
                            uploadingAvatar = uploadingAvatar,
                            savingUsername = savingUsername,
                            onPickAvatar = {
                                avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            onSaveUsername = {
                                val next = usernameInput.trim()
                                if (next.isEmpty()) return@IgngProfileIdentityCard
                                scope.launch {
                                    savingUsername = true
                                    when (val res = container.accountRepository.updateUsername(next)) {
                                        is ApiResult.Success -> {
                                            container.sessionManager.updateActiveProfile(username = res.data.username)
                                            onShowMessage("用户名已更新")
                                        }
                                        is ApiResult.Failure -> onShowMessage(res.error.message)
                                    }
                                    savingUsername = false
                                }
                            },
                        )
                    }

                    item(key = "devices") {
                        IgngDevicesSecurityEntryCard(
                            devices = devicesController.devices,
                            totalCount = devicesController.totalCount,
                            loading = devicesController.isInitialLoading,
                            error = devicesController.error,
                            onClick = onOpenLoginDevices,
                        )
                    }
                }

                // 3. Unified Appearance, 3-Tier Motion, Reader Canvas & Haptics
                item(key = "appearance") {
                    IgngAppearanceMotionSettingsCard(
                        themeMode = preferences.themeMode,
                        motionIntensity = preferences.motionIntensity,
                        hapticsEnabled = preferences.hapticsEnabled,
                        onThemeModeChange = { mode -> scope.launch { container.settingsRepository.setThemeMode(mode) } },
                        onMotionIntensityChange = { level -> scope.launch { container.settingsRepository.setMotionIntensity(level) } },
                        onHapticsEnabledChange = { enabled -> scope.launch { container.settingsRepository.setHapticsEnabled(enabled) } },
                        readerTypography = preferences.reader,
                        onReaderToneChange = { tone ->
                            scope.launch { container.settingsRepository.updateReaderPrefs { it.copy(canvasTone = tone) } }
                        },
                    )
                }

                // 4. Unified Cache, Self-Update & About
                item(key = "cache") {
                    IgngCacheCard(
                        cacheBytes = cacheBytes,
                        subtitlePrefix = "Wiki 离线文章、服务器目录、更新包与本地图片缓存",
                        clearing = clearingCache,
                        onClearCache = {
                            scope.launch {
                                clearingCache = true
                                container.clearAllCaches()
                                cacheBytes = container.cacheSizeBytes()
                                clearingCache = false
                                onShowMessage("本地缓存已清理")
                            }
                        },
                    )
                }

                item(key = "app_update") {
                    IgngAppUpdateCard(
                        snapshot = updateSnapshot,
                        onCheckNow = { container.appUpdateManager.checkForUpdatesAsync(manual = true) },
                        onStartUpdate = { container.appUpdateManager.startOneClickUpdate() },
                        onCancelDownload = { container.appUpdateManager.cancelDownload() },
                        onOpenInstallPermission = {
                            container.appUpdateManager.openInstallPermissionSettings().onFailure {
                                onShowMessage("无法打开系统安装权限设置，请在系统应用设置中手动开启")
                            }
                        },
                        onTogglePrerelease = { enabled -> container.appUpdateManager.setIncludePrerelease(enabled) },
                        onOpenReleasePage = { url -> IgngBrowserLauncher.openExternalUrl(context, url, colors.isDark) },
                        onOpenDetails = { showUpdateSheet = true },
                    )
                }

                item(key = "about") {
                    IgngAboutAppCard(
                        appName = "IGNGmc",
                        packageName = BuildConfig.APPLICATION_ID,
                        versionName = BuildConfig.VERSION_NAME,
                        versionCode = BuildConfig.VERSION_CODE,
                    )
                }
            }
        }
    }

    if (showUpdateSheet) {
        updateSnapshot.activeRelease?.let { release ->
            IgngAppUpdateSheet(
                snapshot = updateSnapshot,
                release = release,
                onDismiss = { showUpdateSheet = false },
                onStartUpdate = { container.appUpdateManager.startOneClickUpdate() },
                onCancelDownload = { container.appUpdateManager.cancelDownload() },
                onOpenInstallPermission = {
                    container.appUpdateManager.openInstallPermissionSettings().onFailure {
                        onShowMessage("无法打开系统安装权限设置，请在系统应用设置中手动开启")
                    }
                },
                onOpenReleasePage = { url -> IgngBrowserLauncher.openExternalUrl(context, url, colors.isDark) },
            )
        }
    }
}

@Composable
fun LoginDevicesScreen(
    container: IgngAppContainer,
    activeAccount: SavedAccount?,
    sessionAuthStatus: SessionAuthStatus = if (activeAccount != null) SessionAuthStatus.ACTIVE else SessionAuthStatus.SIGNED_OUT,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit = {},
) {
    val canLoadDevices = activeAccount != null && !sessionAuthStatus.requiresReauth
    val devicesController = rememberIgngDevicesResource(
        cache = container.swrCache,
        accountRepository = container.accountRepository,
        cacheKey = McCacheKeys.CORE_DEVICES,
        policy = McCachePolicies.Standard,
        tags = setOf(McCacheTags.DEVICES),
        enabled = canLoadDevices,
        slotKey = "mc_login_devices",
        onShowMessage = onShowMessage,
    )

    IgngDevicesSecurityScreen(
        devices = devicesController.devices,
        totalCount = devicesController.totalCount,
        loading = devicesController.isInitialLoading,
        refreshing = devicesController.isUserRefreshing,
        revalidating = devicesController.isRevalidating,
        error = devicesController.error,
        authenticated = canLoadDevices,
        hasMore = devicesController.hasMore,
        loadingMore = devicesController.loadingMore,
        busyDeviceIds = devicesController.busyDeviceIds,
        onBack = onBack,
        onRefresh = { devicesController.refresh() },
        onLoadMore = { devicesController.loadMore() },
        onRenameDevice = { deviceId, newName -> devicesController.renameDevice(deviceId, newName) },
        onRevokeDevice = { deviceId -> devicesController.revokeDevice(deviceId) },
    )
}
