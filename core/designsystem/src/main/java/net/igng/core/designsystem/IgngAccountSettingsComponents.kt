package net.igng.core.designsystem

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.igng.core.model.CoreDeviceSessionDto
import net.igng.core.model.FamilySharedAccountHint
import net.igng.core.model.IgngCoreFormatters
import net.igng.core.model.SavedAccount
import net.igng.core.model.SessionAuthStatus

/**
 * Unified root overlay host for OAuth 2.1 callback exchange, native family handoff progress,
 * auth error feedback, and non-destructive session re-auth / legacy protocol upgrade sheets.
 * Shared identically by all IGNG official Android apps so login UX never diverges.
 */
@Composable
fun IgngAuthOverlayHost(
    isExchanging: Boolean,
    authError: String?,
    onDismissAuthError: () -> Unit,
    showSessionReauthSheet: Boolean,
    sessionAuthStatus: SessionAuthStatus,
    activeAccount: SavedAccount?,
    familyAccounts: List<FamilySharedAccountHint>,
    onDismissSessionReauth: () -> Unit,
    onSignInWithBrowser: () -> Unit,
    onSyncWithFamilyAccount: (FamilySharedAccountHint) -> Unit,
    sessionReauthReasonOverride: String? = null,
) {
    val colors = IgngTheme.colors

    // Full-screen Folded Core veil during OAuth PKCE token exchange or native family handoff
    IgngLoadingVeil(visible = isExchanging)

    if (!authError.isNullOrBlank()) {
        IgngDetailSheet(
            onDismiss = onDismissAuthError,
            title = "登录未完成",
            subtitle = "授权未能完成，你可以立即重试登录。",
        ) {
            IgngBadge(authError, tone = IgngBadgeTone.Danger)
            Spacer(Modifier.height(14.dp))
            IgngButton(
                text = "重新通过 IGNG SSO 登录",
                icon = Icons.AutoMirrored.Rounded.Login,
                onClick = {
                    onDismissAuthError()
                    onSignInWithBrowser()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            IgngButton(
                text = "关闭",
                style = IgngButtonStyle.Secondary,
                onClick = onDismissAuthError,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showSessionReauthSheet) {
        val isLegacyUpgrade = sessionAuthStatus == SessionAuthStatus.LEGACY_UPGRADE_REQUIRED
        val matchingSibling = remember(familyAccounts, activeAccount?.userId) {
            val uid = activeAccount?.userId
            if (uid != null) {
                familyAccounts.firstOrNull { it.userId == uid } ?: familyAccounts.firstOrNull()
            } else {
                familyAccounts.firstOrNull()
            }
        }
        IgngDetailSheet(
            onDismiss = onDismissSessionReauth,
            title = if (isLegacyUpgrade) "需要升级登录授权" else "登录会话已失效",
            subtitle = "重新授权后即可继续，你正在编辑的内容不会被清除。",
        ) {
            Text(
                text = sessionReauthReasonOverride?.takeIf { it.isNotBlank() } ?: if (isLegacyUpgrade) {
                    "本版本已升级至统一 OAuth 2.1 安全认证架构，旧版登录凭证已停用。重新授权前可继续浏览公开内容与本地记录。"
                } else {
                    "当前设备的 IGNG 登录会话可能已过期或在账户中心被撤销。重新授权前仅可浏览公开内容与本地缓存的账户标识。"
                },
                style = IgngTheme.type.body,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(14.dp))
            if (matchingSibling != null) {
                IgngButton(
                    text = "从 ${matchingSibling.sourceAppName} 一键同步授权（${matchingSibling.displayName}）",
                    icon = Icons.Rounded.Link,
                    onClick = {
                        onDismissSessionReauth()
                        onSyncWithFamilyAccount(matchingSibling)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                IgngButton(
                    text = "通过浏览器 IGNG SSO 授权",
                    icon = Icons.AutoMirrored.Rounded.Login,
                    style = IgngButtonStyle.Secondary,
                    onClick = {
                        onDismissSessionReauth()
                        onSignInWithBrowser()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                IgngButton(
                    text = "通过 IGNG SSO 重新授权",
                    icon = Icons.AutoMirrored.Rounded.Login,
                    onClick = {
                        onDismissSessionReauth()
                        onSignInWithBrowser()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))
            IgngButton(
                text = "稍后再说",
                onClick = onDismissSessionReauth,
                style = IgngButtonStyle.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Unified signed-out sign-in hero card shared by the "我的" tab across all IGNG Android apps.
 */
@Composable
fun IgngSignInHeroCard(
    description: String,
    hasFamilyAccounts: Boolean,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IgngTheme.colors
    IgngTile(modifier = modifier.fillMaxWidth()) {
        IgngBadge("IGNG SSO · OAuth 2.1 安全登录", tone = IgngBadgeTone.Accent)
        Spacer(Modifier.height(10.dp))
        Text("登录你的 IGNG 账户", style = IgngTheme.type.pageTitle, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            text = description,
            style = IgngTheme.type.body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(14.dp))
        IgngButton(
            text = "通过 IGNG SSO 登录",
            onClick = onSignIn,
            style = if (hasFamilyAccounts) IgngButtonStyle.Secondary else IgngButtonStyle.Primary,
            icon = Icons.AutoMirrored.Rounded.Login,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Unified non-destructive session-expired / legacy-protocol-upgrade warning banner shared by all
 * IGNG official Android apps. Surfaces 1-tap native sibling handoff when a verified sibling app
 * already holds a live OAuth 2.1 session.
 */
@Composable
fun IgngSessionExpiredBanner(
    sessionAuthStatus: SessionAuthStatus,
    activeAccount: SavedAccount?,
    familyAccounts: List<FamilySharedAccountHint>,
    legacyDescription: String,
    expiredDescription: String,
    onSignInWithBrowser: () -> Unit,
    onSyncWithFamilyAccount: (FamilySharedAccountHint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IgngTheme.colors
    val isLegacy = sessionAuthStatus == SessionAuthStatus.LEGACY_UPGRADE_REQUIRED
    val matchingSibling = remember(familyAccounts, activeAccount?.userId) {
        val uid = activeAccount?.userId
        if (uid != null) familyAccounts.firstOrNull { it.userId == uid } else familyAccounts.firstOrNull()
    }
    IgngTile(
        borderColor = colors.warning.copy(alpha = 0.55f),
        modifier = modifier.fillMaxWidth(),
    ) {
        IgngBadge(
            text = if (isLegacy) "需升级 OAuth 2.1 授权" else "登录会话已失效",
            tone = IgngBadgeTone.Warning,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isLegacy) legacyDescription else expiredDescription,
            style = IgngTheme.type.caption,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(12.dp))
        if (matchingSibling != null) {
            IgngButton(
                text = "从 ${matchingSibling.sourceAppName} 一键同步授权",
                onClick = { onSyncWithFamilyAccount(matchingSibling) },
                style = IgngButtonStyle.Primary,
                icon = Icons.Rounded.Link,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            IgngButton(
                text = "通过浏览器 IGNG SSO 授权",
                onClick = onSignInWithBrowser,
                style = IgngButtonStyle.Secondary,
                icon = Icons.AutoMirrored.Rounded.Login,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            IgngButton(
                text = "立即重新授权",
                onClick = onSignInWithBrowser,
                style = IgngButtonStyle.Primary,
                icon = Icons.AutoMirrored.Rounded.Login,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Cross-app family SSO prompt card shown when a certificate-verified sibling IGNG official app
 * already holds an active OAuth 2.1 session on the same device.
 */
@Composable
fun IgngFamilySsoBanner(
    familyAccounts: List<FamilySharedAccountHint>,
    onContinueWithFamilyAccount: (FamilySharedAccountHint) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "检测到本机 IGNG 家族已登录账号",
    subtitle: String = "通过官方应用间安全交接一键同步授权，无需跳转网页或重复输入密码。",
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
 * Unified multi-account vault + cross-app family SSO discovery card shared by all IGNG Android apps.
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
    onSyncFamilyAccount: ((FamilySharedAccountHint) -> Unit)? = null,
    onRemoveAccount: ((Long) -> Unit)? = null,
) {
    val colors = IgngTheme.colors
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<SavedAccount?>(null) }

    val triggerFamilySync: (FamilySharedAccountHint) -> Unit = { hint ->
        if (onSyncFamilyAccount != null) onSyncFamilyAccount(hint) else onSignIn()
    }

    // Only treat accounts with live OAuth 2.1 refresh tokens (and not invalidated active session) as already synced
    val activeUserId = activeAccount?.userId
    val syncedUserIds = remember(accounts, activeUserId, sessionAuthStatus) {
        accounts
            .filter { acc ->
                !acc.refreshToken.isNullOrBlank() &&
                    !(acc.userId == activeUserId && sessionAuthStatus.requiresReauth)
            }
            .map { it.userId }
            .toSet()
    }
    val syncableFamilyAccounts = remember(familyAccounts, syncedUserIds) {
        familyAccounts.filterNot { it.userId in syncedUserIds }
    }

    IgngTile(modifier = modifier.fillMaxWidth()) {
        IgngSectionHeader(
            title = "IGNG 账户与多端切换",
            subtitle = "本机安全存储 · 支持家族应用无感同步",
            prominent = true,
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
                        text = "以「${primaryHint.displayName}」一键同步登录",
                        onClick = { triggerFamilySync(primaryHint) },
                        style = IgngButtonStyle.Primary,
                        icon = Icons.Rounded.Link,
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
            val matchingFamilyHint = remember(familyAccounts, activeAccount.userId) {
                familyAccounts.firstOrNull { it.userId == activeAccount.userId }
            }
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
                            if (isLegacyUpgrade) {
                                "@${activeAccount.username} · 旧版凭证待升级，仅保留本地标识"
                            } else {
                                "@${activeAccount.username} · 会话已失效，仅保留本地记录"
                            }
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
                if (matchingFamilyHint != null) {
                    IgngButton(
                        text = "从 ${matchingFamilyHint.sourceAppName} 一键同步授权",
                        onClick = { triggerFamilySync(matchingFamilyHint) },
                        style = IgngButtonStyle.Primary,
                        icon = Icons.Rounded.Link,
                        compact = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    IgngButton(
                        text = "通过浏览器重新授权",
                        onClick = onSignIn,
                        style = IgngButtonStyle.Secondary,
                        icon = Icons.AutoMirrored.Rounded.Login,
                        compact = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    IgngButton(
                        text = "重新授权当前账户",
                        onClick = onSignIn,
                        style = IgngButtonStyle.Primary,
                        icon = Icons.AutoMirrored.Rounded.Login,
                        compact = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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

            val extraFamilyHints = remember(syncableFamilyAccounts, matchingFamilyHint, sessionExpired) {
                if (sessionExpired && matchingFamilyHint != null) {
                    syncableFamilyAccounts.filterNot { it.userId == matchingFamilyHint.userId }
                } else {
                    syncableFamilyAccounts
                }
            }
            if (extraFamilyHints.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "本机其他 IGNG 应用已登录账号",
                    style = IgngTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(6.dp))
                extraFamilyHints.forEach { hint ->
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
                                onClick = { triggerFamilySync(hint) },
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
            subtitle = "同步至 IGNG 全系站点与客户端",
            prominent = true,
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
 * Pure presentation & filtering helpers shared by [IgngDevicesSecurityEntryCard] and
 * [IgngDevicesSecurityScreen].
 */
object IgngDevicesUiLogic {
    const val SEARCH_BAR_THRESHOLD: Int = 6

    data class PartitionedDevices(
        val currentDevices: List<CoreDeviceSessionDto>,
        val otherDevices: List<CoreDeviceSessionDto>,
    ) {
        val totalVisible: Int get() = currentDevices.size + otherDevices.size
        val isEmpty: Boolean get() = currentDevices.isEmpty() && otherDevices.isEmpty()
    }

    fun stableDeviceKey(device: CoreDeviceSessionDto): String =
        device.id.ifBlank { device.sessionId }

    fun resolveTotalCount(
        devices: List<CoreDeviceSessionDto>,
        reportedTotal: Int? = null,
    ): Int = maxOf(reportedTotal ?: 0, devices.size)

    fun shouldShowSearchBar(
        devices: List<CoreDeviceSessionDto>,
        hasMore: Boolean = false,
    ): Boolean = hasMore || devices.size >= SEARCH_BAR_THRESHOLD

    fun buildEntrySummary(
        devices: List<CoreDeviceSessionDto>,
        totalCount: Int,
        loading: Boolean,
        error: String?,
    ): String {
        val resolvedTotal = resolveTotalCount(devices, totalCount)
        if (loading && resolvedTotal == 0) return "正在同步活跃会话…"
        if (error != null && resolvedTotal == 0) return "未能读取登录设备，点击进入重试"
        if (resolvedTotal == 0) return "暂无可展示的活跃设备会话"

        val currentDevice = devices.firstOrNull { it.isCurrent }
        return if (currentDevice != null) {
            val currentName = currentDevice.deviceName.ifBlank { "当前设备" }
            val otherCount = (resolvedTotal - 1).coerceAtLeast(0)
            if (otherCount > 0) {
                "本机：$currentName · 另有 $otherCount 台设备"
            } else {
                "本机：$currentName · 暂无其他活跃设备"
            }
        } else {
            "共 $resolvedTotal 台活跃设备 · 点击查看与管理"
        }
    }

    fun buildEntryBadgeText(
        devices: List<CoreDeviceSessionDto>,
        totalCount: Int,
        loading: Boolean,
        error: String?,
    ): String? {
        val resolvedTotal = resolveTotalCount(devices, totalCount)
        if (loading && resolvedTotal == 0) return null
        if (error != null && resolvedTotal == 0) return "重试"
        return "$resolvedTotal 台"
    }

    fun buildScreenSubtitle(
        devices: List<CoreDeviceSessionDto>,
        totalCount: Int,
        loading: Boolean,
        error: String?,
    ): String {
        val resolvedTotal = resolveTotalCount(devices, totalCount)
        return when {
            loading && resolvedTotal == 0 -> "正在同步活跃会话…"
            error != null && resolvedTotal == 0 -> "未能读取登录设备"
            resolvedTotal > 0 -> "共 $resolvedTotal 台活跃设备 · 支持重命名与远程吊销"
            else -> "查看并远程吊销活跃会话"
        }
    }

    fun partitionAndFilterDevices(
        devices: List<CoreDeviceSessionDto>,
        query: String = "",
    ): PartitionedDevices {
        val seenKeys = HashSet<String>(devices.size)
        val deduplicated = ArrayList<CoreDeviceSessionDto>(devices.size)
        for (dev in devices) {
            val key = stableDeviceKey(dev)
            if (key.isEmpty() || seenKeys.add(key)) {
                deduplicated.add(dev)
            }
        }

        val normalizedQuery = query.trim()
        val filtered = if (normalizedQuery.isEmpty()) {
            deduplicated
        } else {
            deduplicated.filter { dev ->
                dev.deviceName.contains(normalizedQuery, ignoreCase = true) ||
                    (dev.clientLabel?.contains(normalizedQuery, ignoreCase = true) == true) ||
                    (dev.ipAddress?.contains(normalizedQuery, ignoreCase = true) == true)
            }
        }

        val current = ArrayList<CoreDeviceSessionDto>()
        val others = ArrayList<CoreDeviceSessionDto>()
        for (dev in filtered) {
            if (dev.isCurrent) current.add(dev) else others.add(dev)
        }
        return PartitionedDevices(currentDevices = current, otherDevices = others)
    }
}

/**
 * Compact, fixed-height Settings entry card for active login devices (`/api/v1/devices`).
 * Prevents long multi-device lists from inflating the primary Settings screen by delegating
 * full inspection, search, rename, and revocation to [IgngDevicesSecurityScreen].
 */
@Composable
fun IgngDevicesSecurityEntryCard(
    devices: List<CoreDeviceSessionDto>,
    loading: Boolean,
    error: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    totalCount: Int = devices.size,
) {
    val colors = IgngTheme.colors
    val resolvedTotal = IgngDevicesUiLogic.resolveTotalCount(devices, totalCount)
    val summary = IgngDevicesUiLogic.buildEntrySummary(
        devices = devices,
        totalCount = resolvedTotal,
        loading = loading,
        error = error,
    )
    val badgeText = IgngDevicesUiLogic.buildEntryBadgeText(
        devices = devices,
        totalCount = resolvedTotal,
        loading = loading,
        error = error,
    )
    val isErrorBadge = error != null && resolvedTotal == 0

    IgngInteractiveTile(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(IgngTheme.radii.sm)
                    .background(colors.tileSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Devices,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "登录设备管理",
                    style = IgngTheme.type.sectionTitle,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = summary,
                    style = IgngTheme.type.meta,
                    color = if (isErrorBadge) colors.warning else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (badgeText != null) {
                IgngBadge(
                    text = badgeText,
                    tone = if (isErrorBadge) IgngBadgeTone.Warning else IgngBadgeTone.Neutral,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "进入登录设备管理",
                tint = colors.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Dedicated secondary screen for managing active login device sessions (`/api/v1/devices`).
 * Shared identically by all IGNG official Android apps.
 */
@Composable
fun IgngDevicesSecurityScreen(
    devices: List<CoreDeviceSessionDto>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRenameDevice: (deviceId: String, newName: String) -> Unit,
    onRevokeDevice: (deviceId: String) -> Unit,
    modifier: Modifier = Modifier,
    authenticated: Boolean = true,
    totalCount: Int = devices.size,
    refreshing: Boolean = false,
    revalidating: Boolean = false,
    hasMore: Boolean = false,
    loadingMore: Boolean = false,
    busyDeviceIds: Set<String> = emptySet(),
    onLoadMore: (() -> Unit)? = null,
) {
    val colors = IgngTheme.colors
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<CoreDeviceSessionDto?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var revokeTarget by remember { mutableStateOf<CoreDeviceSessionDto?>(null) }

    val resolvedTotal = IgngDevicesUiLogic.resolveTotalCount(devices, totalCount)
    val showSearchBar = IgngDevicesUiLogic.shouldShowSearchBar(devices, hasMore)
    val effectiveQuery = if (showSearchBar) searchQuery else ""
    val partitioned = remember(devices, effectiveQuery) {
        IgngDevicesUiLogic.partitionAndFilterDevices(devices, effectiveQuery)
    }

    IgngAmbientCanvas(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            IgngTopBar(
                title = "登录设备管理",
                subtitle = if (authenticated) {
                    IgngDevicesUiLogic.buildScreenSubtitle(
                        devices = devices,
                        totalCount = resolvedTotal,
                        loading = loading,
                        error = error,
                    )
                } else {
                    "请先登录或重新授权账户"
                },
                onBack = onBack,
                actions = {
                    if (authenticated) {
                        IconButton(
                            onClick = onRefresh,
                            enabled = !refreshing,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "刷新登录设备",
                                tint = if (refreshing) colors.textMuted else colors.textPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                },
            )

            if (!authenticated) {
                Box(modifier = Modifier.padding(16.dp)) {
                    IgngStateBlock(
                        title = "登录会话不可用",
                        description = "当前账号未登录或会话已失效，返回设置完成登录后即可管理活跃设备。",
                        actionLabel = "返回设置",
                        onAction = onBack,
                    )
                }
            } else {
                IgngPullToRefreshBox(
                    isRefreshing = refreshing,
                    isRevalidating = revalidating,
                    onRefresh = onRefresh,
                    modifier = Modifier.weight(1f),
                ) {
                    when {
                        loading && devices.isEmpty() -> Box(modifier = Modifier.padding(16.dp)) {
                            IgngSkeletonRows(count = 4)
                        }
                        error != null && devices.isEmpty() -> Box(modifier = Modifier.padding(16.dp)) {
                            IgngStateBlock(
                                title = "未能读取登录设备",
                                description = error,
                                isError = true,
                                actionLabel = "重试",
                                onAction = onRefresh,
                            )
                        }
                        devices.isEmpty() -> Box(modifier = Modifier.padding(16.dp)) {
                            IgngEmptyTile(
                                title = "暂无活跃设备会话",
                                subtitle = "当前账号没有可展示的活跃登录设备。",
                                actionLabel = "重新加载",
                                onAction = onRefresh,
                            )
                        }
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = rememberIgngPageContentPadding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (showSearchBar) {
                                item(key = "device_search") {
                                    IgngSearchField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = "搜索设备名称、客户端或 IP",
                                    )
                                }
                            }

                            if (partitioned.isEmpty) {
                                item(key = "device_search_empty") {
                                    IgngEmptyTile(
                                        title = "未找到匹配的设备",
                                        subtitle = "当前已加载的设备中不包含「${effectiveQuery.trim()}」。",
                                        actionLabel = "清空搜索",
                                        onAction = { searchQuery = "" },
                                    )
                                }
                            } else {
                                if (partitioned.currentDevices.isNotEmpty()) {
                                    item(key = "header_current") {
                                        IgngSectionHeader(title = "当前设备")
                                    }
                                    itemsIndexed(
                                        items = partitioned.currentDevices,
                                        key = { idx, dev -> "cur_${IgngDevicesUiLogic.stableDeviceKey(dev)}_$idx" },
                                    ) { _, dev ->
                                        IgngDeviceSessionCard(
                                            device = dev,
                                            busy = dev.id in busyDeviceIds,
                                            onRename = {
                                                renameTarget = dev
                                                renameDraft = dev.deviceName
                                            },
                                            onRevoke = { revokeTarget = dev },
                                        )
                                    }
                                }

                                if (partitioned.otherDevices.isNotEmpty()) {
                                    val otherCount = if (effectiveQuery.isNotBlank()) {
                                        partitioned.otherDevices.size
                                    } else {
                                        (resolvedTotal - partitioned.currentDevices.size)
                                            .coerceAtLeast(partitioned.otherDevices.size)
                                    }
                                    item(key = "header_others") {
                                        IgngSectionHeader(
                                            title = if (effectiveQuery.isNotBlank()) {
                                                "其他活跃设备（匹配 $otherCount 台）"
                                            } else {
                                                "其他活跃设备（$otherCount）"
                                            },
                                            subtitle = if (effectiveQuery.isBlank()) {
                                                "发现陌生设备时可随时远程吊销其会话"
                                            } else {
                                                null
                                            },
                                        )
                                    }
                                    itemsIndexed(
                                        items = partitioned.otherDevices,
                                        key = { idx, dev -> "other_${IgngDevicesUiLogic.stableDeviceKey(dev)}_$idx" },
                                    ) { _, dev ->
                                        IgngDeviceSessionCard(
                                            device = dev,
                                            busy = dev.id in busyDeviceIds,
                                            onRename = {
                                                renameTarget = dev
                                                renameDraft = dev.deviceName
                                            },
                                            onRevoke = { revokeTarget = dev },
                                        )
                                    }
                                } else if (effectiveQuery.isBlank() && !hasMore && partitioned.currentDevices.isNotEmpty()) {
                                    item(key = "no_other_devices") {
                                        IgngTile(
                                            soft = true,
                                            contentPadding = PaddingValues(14.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(
                                                text = "当前仅在本设备保持活跃登录，未检测到其他活跃设备。",
                                                style = IgngTheme.type.caption,
                                                color = colors.textSecondary,
                                            )
                                        }
                                    }
                                }
                            }

                            if (hasMore && onLoadMore != null) {
                                item(key = "load_more") {
                                    IgngButton(
                                        text = if (loadingMore) {
                                            "正在加载更多…"
                                        } else {
                                            "加载更多设备（已加载 ${devices.size} / $resolvedTotal）"
                                        },
                                        onClick = onLoadMore,
                                        style = IgngButtonStyle.Secondary,
                                        loading = loadingMore,
                                        enabled = !loadingMore,
                                        compact = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
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

@Composable
private fun IgngDeviceSessionCard(
    device: CoreDeviceSessionDto,
    busy: Boolean,
    onRename: () -> Unit,
    onRevoke: () -> Unit,
) {
    val colors = IgngTheme.colors
    val relativeActive = IgngCoreFormatters.relativeTime(device.lastActiveAt)
    val primaryMeta = buildString {
        append(device.clientLabel?.takeIf { it.isNotBlank() } ?: "IGNG 会话")
        if (relativeActive.isNotBlank()) {
            append(" · 最近活跃 ")
            append(relativeActive)
        }
    }
    val secondaryMeta = listOfNotNull(
        device.ipAddress?.takeIf { it.isNotBlank() }?.let { "IP $it" },
        device.expiresAt?.takeIf { it.isNotBlank() }?.let { "有效至 ${IgngCoreFormatters.shortDateTime(it)}" },
    ).joinToString(" · ")

    IgngTile(
        borderColor = if (device.isCurrent) colors.success.copy(alpha = 0.35f) else colors.line,
        contentPadding = PaddingValues(14.dp),
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
                tint = if (device.isCurrent) colors.success else colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = device.deviceName.ifBlank { "未命名设备" },
                        style = IgngTheme.type.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (device.isCurrent) {
                        IgngBadge("本机", tone = IgngBadgeTone.Success)
                    }
                }
                Text(
                    text = primaryMeta,
                    style = IgngTheme.type.caption,
                    color = colors.textSecondary,
                )
                if (secondaryMeta.isNotBlank()) {
                    Text(
                        text = secondaryMeta,
                        style = IgngTheme.type.meta,
                        color = colors.textMuted,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) {
                IgngBadge("处理中…", tone = IgngBadgeTone.Accent)
            } else {
                IgngTagChip(
                    text = "重命名",
                    onClick = onRename,
                )
                if (!device.isCurrent) {
                    IgngTagChip(
                        text = "吊销会话",
                        tone = IgngTone.DANGER,
                        onClick = onRevoke,
                    )
                }
            }
        }
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
            prominent = true,
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
