package net.igng.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.igng.core.model.CoreDeviceSessionDto
import net.igng.core.model.FamilySharedAccountHint
import net.igng.core.model.IgngCoreFormatters
import net.igng.core.model.SavedAccount
import net.igng.core.model.SessionAuthStatus

/**
 * Microsoft-style cross-app family SSO prompt card shown when a sibling IGNG official app
 * (e.g. IGNG BBS <-> IGNGmc <-> IGNG Ink) is already signed in on the same device.
 */
@Composable
fun IgngFamilySsoBanner(
    familyAccounts: List<FamilySharedAccountHint>,
    onContinueWithFamilyAccount: (FamilySharedAccountHint) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "检测到本机 IGNG 家族已登录账号",
    subtitle: String = "通过官方 SSO 共享会话一键完成当前应用授权，无需重复输入密码。",
) {
    if (familyAccounts.isEmpty()) return
    val colors = IgngTheme.colors
    IgngTile(
        modifier = modifier.fillMaxWidth(),
        borderColor = colors.accent.copy(alpha = 0.42f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Link,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(18.dp),
            )
            IgngBadge(text = "IGNG 跨应用共享登录", tone = IgngBadgeTone.Accent)
        }
        Spacer(Modifier.height(10.dp))
        Text(text = title, style = IgngTheme.type.sectionTitle, color = colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(text = subtitle, style = IgngTheme.type.caption, color = colors.textSecondary)
        Spacer(Modifier.height(12.dp))

        familyAccounts.forEachIndexed { index, hint ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            IgngTile(
                soft = true,
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IgngAvatar(
                        name = hint.displayName,
                        avatarUrl = hint.avatarUrl,
                        size = 42.dp,
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = hint.displayName,
                                style = IgngTheme.type.cardTitle,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IgngBadge(text = "来自 ${hint.sourceAppName}", tone = IgngBadgeTone.Success)
                        }
                        Text(
                            text = "@${hint.username} · #${hint.userId}",
                            style = IgngTheme.type.caption,
                            color = colors.textSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                IgngButton(
                    text = "以「${hint.displayName}」一键同步登录",
                    onClick = { onContinueWithFamilyAccount(hint) },
                    style = IgngButtonStyle.Primary,
                    icon = Icons.AutoMirrored.Rounded.Login,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Unified multi-account vault + cross-app family SSO discovery card shared by IGNG BBS and IGNGmc.
 */
@Composable
fun IgngAccountVaultCard(
    activeAccount: SavedAccount?,
    accounts: List<SavedAccount>,
    sessionAuthStatus: SessionAuthStatus,
    familyAccounts: List<FamilySharedAccountHint>,
    onSignIn: () -> Unit,
    onAddAccount: () -> Unit,
    onSwitchAccount: (Long) -> Unit,
    onLogoutActive: () -> Unit,
    modifier: Modifier = Modifier,
    onRemoveAccount: ((Long) -> Unit)? = null,
) {
    val colors = IgngTheme.colors
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<SavedAccount?>(null) }

    val savedUserIds = remember(accounts) { accounts.map { it.userId }.toSet() }
    val unlinkedFamilyAccounts = remember(familyAccounts, savedUserIds) {
        familyAccounts.filterNot { it.userId in savedUserIds }
    }

    IgngTile(modifier = modifier.fillMaxWidth()) {
        IgngSectionHeader(
            title = "IGNG 账户与多端切换",
            subtitle = "令牌由 Android Keystore AES-256-GCM 硬件加密保护 · 支持家族应用共享登录发现",
        )
        Spacer(Modifier.height(12.dp))

        if (activeAccount == null) {
            if (familyAccounts.isNotEmpty()) {
                val primaryHint = familyAccounts.first()
                IgngTile(
                    soft = true,
                    borderColor = colors.accent.copy(alpha = 0.35f),
                    contentPadding = PaddingValues(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IgngAvatar(
                            name = primaryHint.displayName,
                            avatarUrl = primaryHint.avatarUrl,
                            size = 40.dp,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(primaryHint.displayName, style = IgngTheme.type.cardTitle, color = colors.textPrimary)
                                IgngBadge("来自 ${primaryHint.sourceAppName}", tone = IgngBadgeTone.Accent)
                            }
                            Text("@${primaryHint.username} · 已在本机其他 IGNG 应用登录", style = IgngTheme.type.caption, color = colors.textSecondary)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    IgngButton(
                        text = "以「${primaryHint.displayName}」一键登录",
                        onClick = onSignIn,
                        style = IgngButtonStyle.Primary,
                        icon = Icons.AutoMirrored.Rounded.Login,
                        compact = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                IgngButton(
                    text = "使用其他 IGNG 账户登录",
                    onClick = onAddAccount,
                    style = IgngButtonStyle.Secondary,
                    icon = Icons.Rounded.PersonAdd,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                IgngButton(
                    text = "通过 IGNG SSO 登录",
                    onClick = onSignIn,
                    style = IgngButtonStyle.Primary,
                    icon = Icons.AutoMirrored.Rounded.Login,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            val sessionExpired = sessionAuthStatus.requiresReauth
            val isLegacyUpgrade = sessionAuthStatus == SessionAuthStatus.LEGACY_UPGRADE_REQUIRED
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IgngAvatar(
                    name = activeAccount.displayName,
                    avatarUrl = activeAccount.avatarUrl,
                    size = 48.dp,
                )
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = activeAccount.displayName,
                            style = IgngTheme.type.cardTitle,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (sessionExpired) {
                            IgngBadge(
                                text = if (isLegacyUpgrade) "需升级授权" else "会话已失效",
                                tone = IgngBadgeTone.Warning,
                            )
                        } else {
                            IgngBadge(text = "当前使用", tone = IgngBadgeTone.Success)
                        }
                    }
                    Text(
                        text = if (sessionExpired) {
                            "@${activeAccount.username} · 会话已失效，仅保留本地记录"
                        } else {
                            buildString {
                                append("@${activeAccount.username} · #${activeAccount.userId}")
                                (activeAccount.refreshExpiresAt ?: activeAccount.expiresAt)?.let {
                                    append(" · 有效至 ")
                                    append(IgngCoreFormatters.shortDateTime(it))
                                }
                            }
                        },
                        style = IgngTheme.type.caption,
                        color = if (sessionExpired) colors.warning else colors.textSecondary,
                    )
                }
            }

            if (sessionExpired) {
                Spacer(Modifier.height(12.dp))
                IgngButton(
                    text = "重新授权当前账户",
                    onClick = onSignIn,
                    style = IgngButtonStyle.Primary,
                    icon = Icons.AutoMirrored.Rounded.Login,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IgngButton(
                    text = "添加其他账户",
                    onClick = onAddAccount,
                    style = IgngButtonStyle.Secondary,
                    icon = Icons.Rounded.PersonAdd,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                IgngButton(
                    text = "退出当前账户",
                    onClick = { showLogoutConfirm = true },
                    style = IgngButtonStyle.Danger,
                    icon = Icons.AutoMirrored.Rounded.Logout,
                    compact = true,
                )
            }

            if (accounts.size > 1) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "已保存的账户（${accounts.size}，点击切换）",
                    style = IgngTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(6.dp))
                accounts.forEach { acc ->
                    val isCurrent = acc.userId == activeAccount.userId
                    IgngInteractiveTile(
                        onClick = { if (!isCurrent) onSwitchAccount(acc.userId) },
                        soft = true,
                        accentBorder = if (isCurrent) colors.accent else null,
                        contentPadding = PaddingValues(11.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IgngAvatar(name = acc.displayName, avatarUrl = acc.avatarUrl, size = 34.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(acc.displayName, style = IgngTheme.type.bodyMedium.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                                Text("#${acc.userId} · @${acc.username}", style = IgngTheme.type.badge, color = colors.textSecondary)
                            }
                            if (isCurrent) {
                                IgngBadge("当前", tone = IgngBadgeTone.Accent)
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    IgngBadge("切换", tone = IgngBadgeTone.Neutral)
                                    if (onRemoveAccount != null) {
                                        IgngTagChip(
                                            text = "移除",
                                            tone = IgngTone.DANGER,
                                            onClick = { removeTarget = acc },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (unlinkedFamilyAccounts.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "本机其他 IGNG 应用已登录账号",
                    style = IgngTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(6.dp))
                unlinkedFamilyAccounts.forEach { hint ->
                    IgngTile(
                        soft = true,
                        borderColor = colors.accent.copy(alpha = 0.28f),
                        contentPadding = PaddingValues(11.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            IgngAvatar(name = hint.displayName, avatarUrl = hint.avatarUrl, size = 34.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(hint.displayName, style = IgngTheme.type.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                                Text("来自 ${hint.sourceAppName} · @${hint.username}", style = IgngTheme.type.badge, color = colors.textSecondary)
                            }
                            IgngTagChip(
                                text = "一键同步",
                                tone = IgngTone.ACCENT,
                                onClick = onSignIn,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLogoutConfirm && activeAccount != null) {
        IgngConfirmDialog(
            title = "退出账户 ${activeAccount.displayName}",
            message = "将吊销本机当前应用会话令牌并从本地安全存储移除；不会影响你的站点数据或其他设备的登录状态。",
            confirmLabel = "退出登录",
            danger = true,
            onDismiss = { showLogoutConfirm = false },
            onConfirm = {
                showLogoutConfirm = false
                onLogoutActive()
            },
        )
    }

    removeTarget?.let { acc ->
        IgngConfirmDialog(
            title = "移除已保存账户 ${acc.displayName}",
            message = "将从本机移除该账户的离线登录记录，之后可随时通过 IGNG SSO 重新添加。",
            confirmLabel = "移除",
            danger = true,
            onDismiss = { removeTarget = null },
            onConfirm = {
                val id = acc.userId
                removeTarget = null
                onRemoveAccount?.invoke(id)
            },
        )
    }
}

/**
 * Unified profile identity editor card (avatar upload + username update via `/api/v1/users/me`).
 */
@Composable
fun IgngProfileIdentityCard(
    activeAccount: SavedAccount,
    usernameInput: String,
    onUsernameChange: (String) -> Unit,
    uploadingAvatar: Boolean,
    savingUsername: Boolean,
    onPickAvatar: () -> Unit,
    onSaveUsername: () -> Unit,
    modifier: Modifier = Modifier,
    avatarUrlOverride: String? = null,
) {
    val colors = IgngTheme.colors
    val trimmed = usernameInput.trim()
    val canSave = trimmed.isNotBlank() && trimmed != activeAccount.username && !savingUsername

    IgngTile(modifier = modifier.fillMaxWidth()) {
        IgngSectionHeader(
            title = "头像与用户名",
            subtitle = "修改后将同步至 IGNG 全系站点与客户端",
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IgngAvatar(
                name = activeAccount.displayName,
                avatarUrl = avatarUrlOverride ?: activeAccount.avatarUrl,
                size = 52.dp,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(activeAccount.displayName, style = IgngTheme.type.cardTitle, color = colors.textPrimary)
                Text("支持 JPG / PNG / WebP / GIF", style = IgngTheme.type.caption, color = colors.textSecondary)
            }
            IgngButton(
                text = "更换头像",
                onClick = onPickAvatar,
                style = IgngButtonStyle.Secondary,
                icon = Icons.Rounded.PhotoCamera,
                loading = uploadingAvatar,
                compact = true,
            )
        }
        Spacer(Modifier.height(14.dp))
        IgngTextField(
            value = usernameInput,
            onValueChange = onUsernameChange,
            label = "IGNG 用户名",
            placeholder = "输入新用户名",
            singleLine = true,
        )
        Spacer(Modifier.height(10.dp))
        IgngButton(
            text = "保存用户名",
            onClick = onSaveUsername,
            style = IgngButtonStyle.Primary,
            icon = Icons.Rounded.Save,
            enabled = canSave,
            loading = savingUsername,
            compact = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Unified active login devices management card (`/api/v1/devices`) shared by BBS & MC.
 */
@Composable
fun IgngDevicesSecurityCard(
    devices: List<CoreDeviceSessionDto>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onRenameDevice: (deviceId: String, newName: String) -> Unit,
    onRevokeDevice: (deviceId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IgngTheme.colors
    var renameTarget by remember { mutableStateOf<CoreDeviceSessionDto?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var revokeTarget by remember { mutableStateOf<CoreDeviceSessionDto?>(null) }

    IgngTile(modifier = modifier.fillMaxWidth()) {
        IgngSectionHeader(
            title = "登录设备管理（${devices.size}）",
            subtitle = "查看并远程吊销各端 IGNG 登录会话",
            trailing = {
                IgngButton(
                    text = "刷新",
                    onClick = onRefresh,
                    style = IgngButtonStyle.Ghost,
                    icon = Icons.Rounded.Refresh,
                    compact = true,
                )
            },
        )
        Spacer(Modifier.height(12.dp))

        when {
            loading && devices.isEmpty() -> IgngSkeletonRows(count = 2)
            error != null && devices.isEmpty() -> IgngStateBlock(
                title = "未能读取登录设备",
                description = error,
                isError = true,
                actionLabel = "重试",
                onAction = onRefresh,
            )
            devices.isEmpty() -> Text(
                text = "暂无可展示的活跃设备会话。",
                style = IgngTheme.type.caption,
                color = colors.textSecondary,
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                devices.forEach { dev ->
                    IgngTile(
                        soft = true,
                        borderColor = if (dev.isCurrent) colors.success.copy(alpha = 0.35f) else colors.line,
                        contentPadding = PaddingValues(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Devices,
                                contentDescription = null,
                                tint = if (dev.isCurrent) colors.success else colors.textSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = dev.deviceName.ifBlank { "未命名设备" },
                                        style = IgngTheme.type.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (dev.isCurrent) {
                                        IgngBadge("本机", tone = IgngBadgeTone.Success)
                                    }
                                }
                                Text(
                                    text = buildString {
                                        append(dev.clientLabel ?: "IGNG 会话")
                                        append(" · 最近活跃 ")
                                        append(IgngCoreFormatters.relativeTime(dev.lastActiveAt))
                                        dev.ipAddress?.takeIf { it.isNotBlank() }?.let {
                                            append(" · ")
                                            append(it)
                                        }
                                    },
                                    style = IgngTheme.type.caption,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IgngTagChip(
                                text = "重命名",
                                onClick = {
                                    renameTarget = dev
                                    renameDraft = dev.deviceName
                                },
                            )
                            if (!dev.isCurrent) {
                                IgngTagChip(
                                    text = "吊销会话",
                                    tone = IgngTone.DANGER,
                                    onClick = { revokeTarget = dev },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { dev ->
        IgngDetailSheet(
            onDismiss = { renameTarget = null },
            title = "重命名登录设备",
            subtitle = "自定义该设备在 IGNG 安全中心中的显示名称",
        ) {
            IgngTextField(
                value = renameDraft,
                onValueChange = { renameDraft = it },
                label = "设备名称",
                placeholder = "例如：我的主力手机",
                singleLine = true,
            )
            Spacer(Modifier.height(14.dp))
            IgngButton(
                text = "保存名称",
                icon = Icons.Rounded.Edit,
                enabled = renameDraft.trim().isNotEmpty(),
                onClick = {
                    val trimmed = renameDraft.trim()
                    val id = dev.id
                    renameTarget = null
                    onRenameDevice(id, trimmed)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    revokeTarget?.let { dev ->
        IgngConfirmDialog(
            title = "吊销设备「${dev.deviceName.ifBlank { "未知设备" }}」",
            message = "吊销后该设备上的 IGNG 会话将立即失效，需重新通过 SSO 登录。",
            confirmLabel = "吊销会话",
            danger = true,
            onDismiss = { revokeTarget = null },
            onConfirm = {
                val id = dev.id
                revokeTarget = null
                onRevokeDevice(id)
            },
        )
    }
}

/**
 * Unified local offline + image cache card shared across IGNG apps.
 */
@Composable
fun IgngCacheCard(
    cacheBytes: Long,
    subtitlePrefix: String,
    clearing: Boolean,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberIgngHaptics()
    IgngTile(modifier = modifier.fillMaxWidth()) {
        IgngSectionHeader(
            title = "本地离线与图片缓存",
            subtitle = "$subtitlePrefix：${IgngCoreFormatters.formatBytes(cacheBytes)}",
        )
        Spacer(Modifier.height(10.dp))
        IgngButton(
            text = if (clearing) "清理中…" else "清理本地缓存",
            onClick = {
                haptic(IgngHapticKind.Confirm)
                onClearCache()
            },
            style = IgngButtonStyle.Secondary,
            icon = Icons.Rounded.DeleteOutline,
            loading = clearing,
            compact = true,
        )
    }
}

/**
 * Unified About & Client API v1 diagnostic card shared across IGNG apps.
 */
@Composable
fun IgngAboutAppCard(
    appName: String,
    packageName: String,
    versionName: String,
    versionCode: Int,
    gatewaySummary: String = "统一网关：www.igng.net/api/v1 · OAuth 2.1 PKCE：sso.igng.net",
    modifier: Modifier = Modifier,
) {
    val colors = IgngTheme.colors
    IgngTile(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(appName, style = IgngTheme.type.sectionTitle, color = colors.textPrimary)
            IgngBadge("v$versionName", tone = IgngBadgeTone.Accent)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "构建号 $versionCode · 包名 $packageName · Kotlin + Jetpack Compose 原生构建",
            style = IgngTheme.type.caption,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = gatewaySummary,
            style = IgngTheme.type.mono,
            color = colors.textMuted,
        )
        Spacer(Modifier.height(10.dp))
        IgngCopyPill(
            value = "$appName v$versionName ($versionCode) [$packageName]",
            label = "复制版本与客户端诊断信息",
            mono = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
