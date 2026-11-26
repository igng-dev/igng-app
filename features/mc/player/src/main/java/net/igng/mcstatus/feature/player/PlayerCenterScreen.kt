package net.igng.mcstatus.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.igng.core.data.IgngCacheScope
import net.igng.core.data.rememberSwrResource
import net.igng.core.data.runSuspendCatching
import net.igng.core.designsystem.IgngAmbientCanvas
import net.igng.core.designsystem.IgngAvatar
import net.igng.core.designsystem.IgngBadge
import net.igng.core.designsystem.IgngBadgeTone
import net.igng.core.designsystem.IgngButton
import net.igng.core.designsystem.IgngButtonStyle
import net.igng.core.designsystem.IgngFamilySsoBanner
import net.igng.core.designsystem.IgngGroupedSection
import net.igng.core.designsystem.IgngListRow
import net.igng.core.designsystem.IgngPullToRefreshBox
import net.igng.core.designsystem.IgngTheme
import net.igng.core.designsystem.IgngTile
import net.igng.core.designsystem.IgngTopBar
import net.igng.core.designsystem.IgngUpdateDotBadge
import net.igng.core.model.FamilySharedAccountHint
import net.igng.core.model.SavedAccount
import net.igng.core.model.SessionAuthStatus
import net.igng.mcstatus.core.data.BoundMcAccountDto
import net.igng.mcstatus.core.data.McCacheKeys
import net.igng.mcstatus.core.data.McCachePolicies
import net.igng.mcstatus.core.data.McCacheTags
import net.igng.mcstatus.core.data.PlayerRepository
import net.igng.mcstatus.core.data.TicketListResponse
import net.igng.mcstatus.core.data.TicketRepository
import net.igng.mcstatus.core.model.DailyExpStatus
import net.igng.mcstatus.core.model.adminRole

enum class PlayerDestination {
    CHARACTERS,
    TICKETS,
    CREATE_TICKET,
    VAULT,
    ACTIVITY,
    LANDS,
    TELEPORTS,
    FAKE_PLAYERS,
    STATS,
    MEDALS,
    PERFECT,
    PERMISSIONS,
    ADMIN_CONSOLE,
    SETTINGS,
}

@Composable
fun PlayerCenterScreen(
    activeAccount: SavedAccount?,
    sessionAuthStatus: SessionAuthStatus = if (activeAccount != null) SessionAuthStatus.ACTIVE else SessionAuthStatus.SIGNED_OUT,
    sessionEpoch: Int = 0,
    dailyExp: DailyExpStatus?,
    familyAccounts: List<FamilySharedAccountHint> = emptyList(),
    hasAppUpdate: Boolean = false,
    playerRepository: PlayerRepository,
    ticketRepository: TicketRepository,
    bottomPadding: PaddingValues,
    onSignIn: () -> Unit,
    onOpenPassport: (Int) -> Unit,
    onNavigate: (PlayerDestination) -> Unit,
) {
    val colors = IgngTheme.colors
    val sessionExpired = activeAccount != null && sessionAuthStatus.requiresReauth
    val isActiveSession = activeAccount != null && sessionAuthStatus == SessionAuthStatus.ACTIVE

    val (ticketsState, ticketsHandle) = rememberSwrResource<TicketListResponse>(
        cache = ticketRepository.swrCache,
        cacheKey = McCacheKeys.ticketsList(relation = "all", includeClosed = false, query = ""),
        scope = IgngCacheScope.USER_ONLY,
        policy = McCachePolicies.Standard,
        tags = setOf(McCacheTags.TICKETS),
        enabled = isActiveSession,
        slotKey = "player_center_tickets",
    ) {
        runSuspendCatching { ticketRepository.fetchList(relation = "all", includeClosed = false, page = 1) }
    }

    val (accountsState, accountsHandle) = rememberSwrResource<List<BoundMcAccountDto>>(
        cache = playerRepository.swrCache,
        cacheKey = McCacheKeys.PLAYER_ACCOUNTS,
        scope = IgngCacheScope.USER_ONLY,
        policy = McCachePolicies.Standard,
        tags = setOf(McCacheTags.PLAYER_ACCOUNTS),
        enabled = isActiveSession,
        slotKey = "player_center_accounts",
    ) {
        runSuspendCatching { playerRepository.fetchBoundAccounts() }
    }

    val openTicketCount = ticketsState.data?.let { it.total.coerceAtLeast(it.resolvedTickets.size) } ?: 0
    val boundCount = accountsState.data?.size
    val refreshing = ticketsState.isUserRefreshing || accountsState.isUserRefreshing

    IgngAmbientCanvas {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            IgngTopBar(
                title = "我的",
                subtitle = if (sessionExpired) "登录会话已失效 · 仅展示本地缓存" else null,
                actions = {
                    Box {
                        IconButton(
                            onClick = { onNavigate(PlayerDestination.SETTINGS) },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "设置",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        if (hasAppUpdate) {
                            IgngUpdateDotBadge(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-6).dp, y = 6.dp),
                            )
                        }
                    }
                },
            )

            IgngPullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    if (isActiveSession) {
                        ticketsHandle.refresh()
                        accountsHandle.refresh()
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 6.dp,
                        bottom = bottomPadding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (activeAccount == null) {
                        if (familyAccounts.isNotEmpty()) {
                            item(key = "familySsoHero") {
                                IgngFamilySsoBanner(
                                    familyAccounts = familyAccounts,
                                    onContinueWithFamilyAccount = { onSignIn() },
                                )
                            }
                        }
                        item(key = "signedOutHero") {
                            IgngTile(modifier = Modifier.fillMaxWidth()) {
                                IgngBadge("OAuth 2.1 · PKCE 安全登录", tone = IgngBadgeTone.Accent)
                                Spacer(Modifier.height(10.dp))
                                Text("登录你的 IGNG 账户", style = IgngTheme.type.pageTitle, color = colors.textPrimary)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "通过官方 SSO 页面完成授权，客户端不接触明文密码。若本机其他 IGNG 应用或浏览器已登录，可一键完成授权并自动同步每日登录经验。",
                                    style = IgngTheme.type.body,
                                    color = colors.textSecondary,
                                )
                                Spacer(Modifier.height(14.dp))
                                IgngButton(
                                    text = "通过 IGNG SSO 登录",
                                    onClick = onSignIn,
                                    style = if (familyAccounts.isNotEmpty()) IgngButtonStyle.Secondary else IgngButtonStyle.Primary,
                                    icon = Icons.AutoMirrored.Rounded.Login,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        return@LazyColumn
                    }

                    if (sessionExpired) {
                        item(key = "sessionExpiredBanner") {
                            val isLegacy = sessionAuthStatus == SessionAuthStatus.LEGACY_UPGRADE_REQUIRED
                            IgngTile(
                                borderColor = colors.warning.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                IgngBadge(
                                    text = if (isLegacy) "需升级 OAuth 2.1 授权" else "登录会话已失效",
                                    tone = IgngBadgeTone.Warning,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = if (isLegacy) {
                                        "当前账户仍保留旧版登录凭证，新版本需通过 IGNG SSO 重新授权以恢复角色管理、工单与跨服仓库等个人数据。"
                                    } else {
                                        "当前仅展示本地缓存的账户标识与公开主页入口；角色管理、工单、仓库等个人数据需重新授权后同步。"
                                    },
                                    style = IgngTheme.type.caption,
                                    color = colors.textSecondary,
                                )
                                Spacer(Modifier.height(12.dp))
                                IgngButton(
                                    text = "立即重新授权",
                                    onClick = onSignIn,
                                    style = IgngButtonStyle.Primary,
                                    icon = Icons.AutoMirrored.Rounded.Login,
                                    compact = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    // 1. Player Passport Header Card
                    item(key = "passport") {
                        IgngTile(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                IgngAvatar(
                                    name = activeAccount.displayName,
                                    avatarUrl = activeAccount.avatarUrl,
                                    size = 52.dp,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(activeAccount.displayName, style = IgngTheme.type.sectionTitle, color = colors.textPrimary)
                                        if (sessionExpired) {
                                            IgngBadge("已失效", tone = IgngBadgeTone.Warning)
                                        } else {
                                            activeAccount.adminRole?.let { role ->
                                                IgngBadge(role.label, tone = IgngBadgeTone.Accent)
                                            }
                                        }
                                    }
                                    Text(
                                        text = "账户 #${activeAccount.userId} · @${activeAccount.username}",
                                        style = IgngTheme.type.meta,
                                        color = colors.textSecondary,
                                    )
                                    if (!sessionExpired && dailyExp != null && dailyExp.loggedIn) {
                                        Spacer(Modifier.height(6.dp))
                                        val expText = buildString {
                                            if (dailyExp.awarded) append("今日登录经验已入账 · ")
                                            dailyExp.level?.let { append("Lv.$it ") }
                                            dailyExp.exp?.let { append("(${it} EXP)") }
                                        }.ifBlank { "今日登录经验已结算" }
                                        IgngBadge(expText, tone = IgngBadgeTone.Success)
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IgngButton(
                                    text = "我的公开主页",
                                    onClick = { onOpenPassport(activeAccount.userIdInt) },
                                    style = IgngButtonStyle.Secondary,
                                    icon = Icons.Rounded.AccountCircle,
                                    compact = true,
                                    modifier = Modifier.weight(1f),
                                )
                                IgngButton(
                                    text = "创建工单",
                                    onClick = { onNavigate(PlayerDestination.CREATE_TICKET) },
                                    style = IgngButtonStyle.Primary,
                                    icon = Icons.Rounded.ConfirmationNumber,
                                    compact = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    // 2. Core Service Group (single canonical ticket entry — zero duplication)
                    item(key = "coreGroup") {
                        IgngGroupedSection(title = "角色与服务") {
                            IgngListRow(
                                title = "我的工单",
                                subtitle = when {
                                    sessionExpired -> "重新授权后查看进行中工单"
                                    openTicketCount > 0 -> "有 $openTicketCount 张进行中的工单等待跟进"
                                    else -> "反馈、附件与处理进度"
                                },
                                icon = Icons.Outlined.ConfirmationNumber,
                                trailing = if (openTicketCount > 0) {
                                    { IgngBadge("$openTicketCount 进行中", tone = IgngBadgeTone.Accent) }
                                } else {
                                    null
                                },
                                onClick = { onNavigate(PlayerDestination.TICKETS) },
                                showDivider = true,
                            )
                            IgngListRow(
                                title = "我的角色",
                                subtitle = "改密、隐私与处罚记录",
                                value = if (!sessionExpired && boundCount != null) "$boundCount 个" else null,
                                icon = Icons.Outlined.Person,
                                onClick = { onNavigate(PlayerDestination.CHARACTERS) },
                                showDivider = true,
                            )
                            IgngListRow(
                                title = "跨服仓库",
                                subtitle = "物品附魔与流转追踪",
                                icon = Icons.Outlined.Inventory2,
                                onClick = { onNavigate(PlayerDestination.VAULT) },
                                showDivider = true,
                            )
                            IgngListRow(
                                title = "登录与活动",
                                subtitle = "进服记录与安全回溯",
                                icon = Icons.Outlined.History,
                                onClick = { onNavigate(PlayerDestination.ACTIVITY) },
                                showDivider = false,
                            )
                        }
                    }

                    // 3. World & Building Group
                    item(key = "worldGroup") {
                        IgngGroupedSection(title = "世界与建造") {
                            IgngListRow(
                                title = "我的领地",
                                subtitle = "公开设置与描述编辑",
                                icon = Icons.Outlined.Map,
                                onClick = { onNavigate(PlayerDestination.LANDS) },
                                showDivider = true,
                            )
                            IgngListRow(
                                title = "传送点",
                                subtitle = "跨服坐标一键复制",
                                icon = Icons.Outlined.Explore,
                                onClick = { onNavigate(PlayerDestination.TELEPORTS) },
                                showDivider = true,
                            )
                            IgngListRow(
                                title = "我的假人",
                                subtitle = "实时血量、饥饿度与挂机位置",
                                icon = Icons.Outlined.SmartToy,
                                onClick = { onNavigate(PlayerDestination.FAKE_PLAYERS) },
                                showDivider = false,
                            )
                        }
                    }

                    // 4. Identity & Honors Group
                    item(key = "identityGroup") {
                        IgngGroupedSection(title = "身份与荣誉") {
                            IgngListRow(
                                title = "游戏统计",
                                subtitle = "游玩、挖掘与生物图鉴",
                                icon = Icons.Outlined.BarChart,
                                onClick = { onNavigate(PlayerDestination.STATS) },
                                showDivider = true,
                            )
                            IgngListRow(
                                title = "我的勋章",
                                subtitle = "荣誉陈列与自定义称号",
                                icon = Icons.Outlined.MilitaryTech,
                                onClick = { onNavigate(PlayerDestination.MEDALS) },
                                showDivider = true,
                            )
                            IgngListRow(
                                title = "完美之人",
                                subtitle = "资格状态与权益额度",
                                icon = Icons.Outlined.WorkspacePremium,
                                onClick = { onNavigate(PlayerDestination.PERFECT) },
                                showDivider = true,
                            )
                            IgngListRow(
                                title = "游戏权限",
                                subtitle = "LuckPerms 组与洞察",
                                icon = Icons.Outlined.Key,
                                onClick = { onNavigate(PlayerDestination.PERMISSIONS) },
                                showDivider = false,
                            )
                        }
                    }

                    // 5. Role-Gated Admin Pocket Console
                    if (!sessionExpired && activeAccount.adminRole != null) {
                        item(key = "adminEntry") {
                            IgngGroupedSection(title = "站点管理") {
                                IgngListRow(
                                    title = "管理员口袋控制台",
                                    subtitle = "全站工单处理、玩家审计与监控",
                                    icon = Icons.Outlined.AdminPanelSettings,
                                    iconTint = colors.warning,
                                    trailing = { IgngBadge(activeAccount.adminRole!!.label, tone = IgngBadgeTone.Warning) },
                                    onClick = { onNavigate(PlayerDestination.ADMIN_CONSOLE) },
                                    showDivider = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
