package net.igng.bbs.feature.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.igng.bbs.core.data.BbsAppContainer
import net.igng.bbs.core.data.BbsCacheKeys
import net.igng.bbs.core.data.BbsCachePolicies
import net.igng.bbs.core.data.BbsCacheTags
import net.igng.bbs.core.data.UpdateMySettingsRequest
import net.igng.bbs.core.data.UpdateUserPreferencesPatch
import net.igng.bbs.core.model.AppPreferences
import net.igng.bbs.core.model.BbsFormatters
import net.igng.bbs.core.model.BbsUserSettingsDto
import net.igng.bbs.core.model.BlockRuleDto
import net.igng.core.data.IgngCacheScope
import net.igng.core.data.rememberScreenHolder
import net.igng.core.data.rememberSwrResource
import net.igng.core.data.toKotlinResult
import net.igng.core.designsystem.IgngAboutAppCard
import net.igng.core.designsystem.IgngAccountVaultCard
import net.igng.core.designsystem.IgngAmbientCanvas
import net.igng.core.designsystem.IgngAppUpdateCard
import net.igng.core.designsystem.IgngAppUpdateSheet
import net.igng.core.designsystem.IgngAppearanceMotionSettingsCard
import net.igng.core.designsystem.IgngBadge
import net.igng.core.designsystem.IgngBadgeTone
import net.igng.core.designsystem.IgngBrowserLauncher
import net.igng.core.designsystem.IgngCacheCard
import net.igng.core.designsystem.IgngConfirmDialog
import net.igng.core.designsystem.IgngDevicesSecurityCard
import net.igng.core.designsystem.IgngErrorTile
import net.igng.core.designsystem.IgngInfoBanner
import net.igng.core.designsystem.IgngLoadingCenter
import net.igng.core.designsystem.IgngPrimaryButton
import net.igng.core.designsystem.IgngProfileIdentityCard
import net.igng.core.designsystem.IgngPullToRefreshBox
import net.igng.core.designsystem.IgngSectionHeader
import net.igng.core.designsystem.IgngSwitchRow
import net.igng.core.designsystem.IgngTagChip
import net.igng.core.designsystem.IgngTextField
import net.igng.core.designsystem.IgngTheme
import net.igng.core.designsystem.IgngTile
import net.igng.core.designsystem.IgngTone
import net.igng.core.designsystem.IgngTopBar
import net.igng.core.model.CoreDeviceSessionDto
import net.igng.core.network.ApiResult

@Composable
fun AccountSecurityScreen(
    container: BbsAppContainer,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onAddAccount: () -> Unit,
    onShowMessage: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accounts by container.accounts.collectAsState()
    val activeAccount by container.activeAccount.collectAsState()
    val sessionAuthStatus by container.sessionAuthStatus.collectAsState()
    val familyAccounts by container.familyAccounts.collectAsState()
    val bootstrap by container.bootstrap.collectAsState()

    val authed = activeAccount != null && !sessionAuthStatus.requiresReauth
    val (devicesState, devicesHandle) = rememberSwrResource<List<CoreDeviceSessionDto>>(
        cache = container.swrCache,
        cacheKey = BbsCacheKeys.DEVICES,
        scope = IgngCacheScope.USER_ONLY,
        policy = BbsCachePolicies.Standard,
        tags = setOf(BbsCacheTags.DEVICES),
        enabled = authed,
        slotKey = "bbs_devices_slot",
    ) {
        container.accountRepository.listDevices().toKotlinResult().map { it.data }
    }

    val devices = devicesState.data.orEmpty()
    val devicesLoading = devicesState.isInitialLoading || devicesState.isUserRefreshing
    val devicesError = if (devicesState.data == null) devicesState.error else null

    var usernameInput by remember(activeAccount?.username) { mutableStateOf(activeAccount?.username.orEmpty()) }
    var uploadingAvatar by remember { mutableStateOf(false) }
    var savingUsername by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun report(msg: String) {
        notice = msg
        onShowMessage(msg)
    }

    LaunchedEffect(Unit) {
        container.refreshFamilyAccounts()
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
                when (val r = container.accountRepository.uploadAvatar(payload.third, payload.second, payload.first)) {
                    is ApiResult.Success -> {
                        container.sessionManager.updateActiveProfile(avatarUrl = r.data.avatarUrl)
                        container.refreshBootstrap(force = true)
                        report("头像已更新并同步")
                    }
                    is ApiResult.Failure -> report(r.error.message)
                }
            } else {
                report("无法读取所选图片")
            }
            uploadingAvatar = false
        }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    IgngAmbientCanvas {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            IgngTopBar(title = "账户与设备安全", onBack = onBack)
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 24.dp + navBottom,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                notice?.let { item(key = "notice") { IgngInfoBanner("提示", it, IgngTone.ACCENT) } }

                item(key = "accounts") {
                    IgngAccountVaultCard(
                        activeAccount = activeAccount,
                        accounts = accounts,
                        sessionAuthStatus = sessionAuthStatus,
                        familyAccounts = familyAccounts,
                        onSignIn = onSignIn,
                        onAddAccount = onAddAccount,
                        onSwitchAccount = { uid ->
                            scope.launch {
                                container.switchAccount(uid)
                                report("已切换当前账户")
                            }
                        },
                        onRemoveAccount = { uid ->
                            container.removeAccount(uid)
                            report("已移除该账户记录")
                        },
                        onLogoutActive = {
                            scope.launch {
                                container.logoutActive()
                                report("已退出当前账户")
                            }
                        },
                    )
                }

                if (authed) {
                    item(key = "profile_identity") {
                        IgngProfileIdentityCard(
                            activeAccount = activeAccount!!,
                            avatarUrlOverride = bootstrap?.viewer?.user?.avatarUrl ?: activeAccount?.avatarUrl,
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
                                    when (val r = container.accountRepository.updateUsername(next)) {
                                        is ApiResult.Success -> {
                                            container.sessionManager.updateActiveProfile(username = r.data.username)
                                            container.refreshBootstrap(force = true)
                                            report("用户名已保存")
                                        }
                                        is ApiResult.Failure -> report(r.error.message)
                                    }
                                    savingUsername = false
                                }
                            },
                        )
                    }

                    item(key = "devices") {
                        IgngDevicesSecurityCard(
                            devices = devices,
                            loading = devicesLoading,
                            error = devicesError,
                            onRefresh = { devicesHandle.refresh() },
                            onRenameDevice = { deviceId, newName ->
                                scope.launch {
                                    when (val r = container.accountRepository.renameDevice(deviceId, newName)) {
                                        is ApiResult.Success -> {
                                            report("设备名称已更新")
                                            devicesHandle.silentRefresh()
                                        }
                                        is ApiResult.Failure -> report(r.error.message)
                                    }
                                }
                            },
                            onRevokeDevice = { deviceId ->
                                scope.launch {
                                    when (val r = container.accountRepository.revokeDevice(deviceId)) {
                                        is ApiResult.Success -> {
                                            report("已吊销该设备登录状态")
                                            devicesHandle.silentRefresh()
                                        }
                                        is ApiResult.Failure -> report(r.error.message)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private class PrivacyFormUiHolder {
    val bioState = mutableStateOf("")
    val allowFollowersState = mutableStateOf(true)
    val allowFavoritesState = mutableStateOf(true)
    val hideAiState = mutableStateOf(false)
    val excludedCatsState = mutableStateOf<Set<String>>(emptySet())
    val excludedTagsTextState = mutableStateOf("")
    var isDirty by mutableStateOf(false)
    var lastSyncedSettings by mutableStateOf<BbsUserSettingsDto?>(null)

    fun syncFromServer(dto: BbsUserSettingsDto, force: Boolean = false) {
        if (!force && isDirty) return
        if (!force && lastSyncedSettings == dto) return
        bioState.value = dto.bio
        allowFollowersState.value = dto.allowFollowersVisibility
        allowFavoritesState.value = dto.allowFavoritesVisibility
        hideAiState.value = dto.preferences.hideAi
        excludedCatsState.value = dto.preferences.excludedCategoryIds.toSet()
        excludedTagsTextState.value = dto.preferences.excludedTags.joinToString(", ")
        lastSyncedSettings = dto
        isDirty = false
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrivacyPreferencesScreen(
    container: BbsAppContainer,
    onBack: () -> Unit,
) {
    val colors = IgngTheme.colors
    val scope = rememberCoroutineScope()
    val bootstrap by container.bootstrap.collectAsState()
    val formHolder = rememberScreenHolder("bbs_privacy_form_ui") { PrivacyFormUiHolder() }

    val (settingsState, settingsHandle) = rememberSwrResource<BbsUserSettingsDto>(
        cache = container.swrCache,
        cacheKey = BbsCacheKeys.MY_SETTINGS,
        scope = IgngCacheScope.USER_ONLY,
        policy = BbsCachePolicies.Settings,
        tags = setOf(BbsCacheTags.SETTINGS),
        slotKey = "bbs_my_settings_slot",
    ) {
        container.profileRepository.getMySettings().toKotlinResult()
    }

    val settings = settingsState.data
    LaunchedEffect(settings) {
        settings?.let { formHolder.syncFromServer(it) }
    }

    var bio by formHolder.bioState
    var allowFollowers by formHolder.allowFollowersState
    var allowFavorites by formHolder.allowFavoritesState
    var hideAi by formHolder.hideAiState
    var excludedCats by formHolder.excludedCatsState
    var excludedTagsText by formHolder.excludedTagsTextState

    var saving by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var unblockTarget by remember { mutableStateOf<BlockRuleDto?>(null) }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    IgngAmbientCanvas {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            IgngTopBar(title = "隐私与内容偏好", onBack = onBack)
            IgngPullToRefreshBox(
                isRefreshing = settingsState.isUserRefreshing,
                isRevalidating = settingsState.isRevalidating,
                onRefresh = { settingsHandle.refresh() },
                modifier = Modifier.weight(1f),
            ) {
                when {
                    settingsState.isInitialLoading && settings == null -> IgngLoadingCenter(loading = true)
                    settingsState.error != null && settings == null -> Box(Modifier.padding(16.dp)) {
                        IgngErrorTile(message = settingsState.error!!, onRetry = { settingsHandle.refresh() })
                    }
                    else -> LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = 24.dp + navBottom,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        notice?.let { item { IgngInfoBanner("提示", it, IgngTone.ACCENT) } }
                        item {
                            IgngTile {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("个人简介", style = IgngTheme.type.sectionTitle, color = colors.textPrimary)
                                    settings?.bioStatus?.let { st ->
                                        if (st == 1) IgngBadge(text = "简介审核中", tone = IgngBadgeTone.Warning)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                IgngTextField(
                                    value = bio,
                                    onValueChange = { bio = it; formHolder.isDirty = true },
                                    label = "向社区介绍你自己（修改后将进入审核）",
                                    singleLine = false,
                                    minLines = 2,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(12.dp))
                                IgngSwitchRow("公开我的粉丝与关注列表", allowFollowers, { allowFollowers = it; formHolder.isDirty = true })
                                IgngSwitchRow("公开我的收藏夹", allowFavorites, { allowFavorites = it; formHolder.isDirty = true })
                                IgngSwitchRow("在信息流隐藏 AI 辅助生成内容", hideAi, { hideAi = it; formHolder.isDirty = true })
                            }
                        }
                        item {
                            IgngTile {
                                Text("排除的分区与标签", style = IgngTheme.type.sectionTitle, color = colors.textPrimary)
                                Spacer(Modifier.height(8.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    bootstrap?.categories.orEmpty().forEach { cat ->
                                        val selected = cat.id in excludedCats
                                        IgngTagChip(
                                            text = BbsFormatters.categoryDisplayName(cat),
                                            selected = selected,
                                            tone = if (selected) IgngTone.DANGER else IgngTone.NEUTRAL,
                                            onClick = {
                                                excludedCats = if (selected) excludedCats - cat.id else excludedCats + cat.id
                                                formHolder.isDirty = true
                                            },
                                        )
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                IgngTextField(
                                    value = excludedTagsText,
                                    onValueChange = { excludedTagsText = it; formHolder.isDirty = true },
                                    label = "屏蔽标签（逗号分隔）",
                                    placeholder = "例如：剧透, 水贴",
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(12.dp))
                                IgngPrimaryButton(
                                    text = "保存隐私与偏好设置",
                                    loading = saving,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        val tags = excludedTagsText.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }
                                        scope.launch {
                                            saving = true
                                            when (
                                                val r = container.profileRepository.updateMySettings(
                                                    UpdateMySettingsRequest(
                                                        bio = bio,
                                                        allowFollowersVisibility = allowFollowers,
                                                        allowFavoritesVisibility = allowFavorites,
                                                        preferences = UpdateUserPreferencesPatch(
                                                            excludedCategoryIds = excludedCats.toList(),
                                                            excludedTags = tags,
                                                            hideAi = hideAi,
                                                        ),
                                                    ),
                                                )
                                            ) {
                                                is ApiResult.Success -> {
                                                    formHolder.syncFromServer(r.data, force = true)
                                                    notice = "隐私与偏好已同步到云端"
                                                }
                                                is ApiResult.Failure -> notice = r.error.message
                                            }
                                            saving = false
                                        }
                                    },
                                )
                            }
                        }
                        val rules = settings?.blockRules.orEmpty()
                        if (rules.isNotEmpty()) {
                            item { IgngSectionHeader("已屏蔽用户（${rules.size}）") }
                            items(rules, key = { it.id }) { rule ->
                                IgngTile(padding = PaddingValues(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            "${rule.targetUser.displayName} (@${rule.targetUser.username})",
                                            style = IgngTheme.type.bodyMedium,
                                            color = colors.textPrimary,
                                        )
                                        IgngTagChip("解除屏蔽", tone = IgngTone.DANGER, onClick = { unblockTarget = rule })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    unblockTarget?.let { rule ->
        IgngConfirmDialog(
            title = "解除对 ${rule.targetUser.displayName} 的屏蔽",
            message = "解除后该用户的帖子与互动将恢复正常展示。",
            confirmLabel = "解除屏蔽",
            danger = false,
            onDismiss = { unblockTarget = null },
            onConfirm = {
                val targetId = rule.targetUser.id
                unblockTarget = null
                scope.launch {
                    container.profileRepository.deleteBlockRule(targetId)
                    settingsHandle.silentRefresh()
                }
            },
        )
    }
}

@Composable
fun AppSettingsScreen(
    container: BbsAppContainer,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val colors = IgngTheme.colors
    val scope = rememberCoroutineScope()
    val prefs by container.settingsRepository.preferences.collectAsState(initial = AppPreferences())
    val updateSnapshot by container.appUpdateManager.snapshot.collectAsState()
    var cacheBytes by remember { mutableLongStateOf(0L) }
    var clearingCache by remember { mutableStateOf(false) }
    var showUpdateSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        cacheBytes = container.cacheSizeBytes()
    }

    LifecycleResumeEffect(Unit) {
        container.appUpdateManager.onResumeHost()
        onPauseOrDispose { }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    IgngAmbientCanvas {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            IgngTopBar(title = "应用设置", onBack = onBack)
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 24.dp + navBottom,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "appearance") {
                    IgngAppearanceMotionSettingsCard(
                        themeMode = prefs.themeMode,
                        motionIntensity = prefs.motionIntensity,
                        hapticsEnabled = prefs.hapticsEnabled,
                        onThemeModeChange = { scope.launch { container.settingsRepository.setThemeMode(it) } },
                        onMotionIntensityChange = { scope.launch { container.settingsRepository.setMotionIntensity(it) } },
                        onHapticsEnabledChange = { scope.launch { container.settingsRepository.setHapticsEnabled(it) } },
                        readerTypography = prefs.reader,
                        onReaderToneChange = { tone ->
                            scope.launch { container.settingsRepository.updateReaderPrefs { it.copy(canvasTone = tone) } }
                        },
                    )
                }

                item(key = "bbs_preferences") {
                    IgngTile(modifier = Modifier.fillMaxWidth()) {
                        IgngSectionHeader(
                            title = "社区与后台同步",
                            subtitle = "IGNG BBS 专属浏览与通知偏好",
                            prominent = true,
                        )
                        Spacer(Modifier.height(10.dp))
                        IgngSwitchRow(
                            title = "后台定期同步未读消息通知",
                            subtitle = "通过系统 WorkManager 低功耗轮询未读提醒",
                            checked = prefs.backgroundNotificationsEnabled,
                            onCheckedChange = { scope.launch { container.settingsRepository.setBackgroundNotificationsEnabled(it) } },
                        )
                    }
                }

                item(key = "cache") {
                    IgngCacheCard(
                        cacheBytes = cacheBytes,
                        subtitlePrefix = "社区快照、更新包与本地图片缓存",
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
                        appName = "IGNG BBS",
                        packageName = "net.igng.bbs",
                        versionName = container.appVersion,
                        versionCode = container.appVersionCode,
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
