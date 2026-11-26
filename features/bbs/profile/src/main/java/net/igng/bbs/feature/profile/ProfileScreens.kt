package net.igng.bbs.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import net.igng.bbs.common.BbsContinueReadingCard
import net.igng.bbs.common.BbsPostRow
import net.igng.bbs.core.data.BbsAppContainer
import net.igng.bbs.core.data.BbsCacheKeys
import net.igng.bbs.core.data.BbsCachePolicies
import net.igng.bbs.core.data.BbsCacheTags
import net.igng.bbs.core.data.UpsertBlockRuleRequest
import net.igng.bbs.core.model.BbsFormatters
import net.igng.bbs.core.model.FavoriteItemDto
import net.igng.bbs.core.model.FollowingItemDto
import net.igng.bbs.core.model.PaginatedResponseDto
import net.igng.bbs.core.model.UserProfileDto
import net.igng.core.data.IgngCacheScope
import net.igng.core.data.rememberScreenHolder
import net.igng.core.data.rememberSwrResource
import net.igng.core.data.toKotlinResult
import net.igng.core.designsystem.IgngAvatar
import net.igng.core.designsystem.IgngBadge
import net.igng.core.designsystem.IgngBadgeTone
import net.igng.core.designsystem.IgngButton
import net.igng.core.designsystem.IgngButtonStyle
import net.igng.core.designsystem.IgngConfirmDialog
import net.igng.core.designsystem.IgngEmptyTile
import net.igng.core.designsystem.IgngErrorTile
import net.igng.core.designsystem.IgngFamilySsoBanner
import net.igng.core.designsystem.IgngGroupedSection
import net.igng.core.designsystem.IgngHairlineDivider
import net.igng.core.designsystem.IgngInfoBanner
import net.igng.core.designsystem.IgngListRow
import net.igng.core.designsystem.IgngLoadingCenter
import net.igng.core.designsystem.IgngPrimaryButton
import net.igng.core.designsystem.IgngPullToRefreshBox
import net.igng.core.designsystem.IgngSecondaryButton
import net.igng.core.designsystem.IgngTagChip
import net.igng.core.designsystem.IgngTheme
import net.igng.core.designsystem.IgngTile
import net.igng.core.designsystem.IgngTone
import net.igng.core.designsystem.IgngTopBar
import net.igng.core.designsystem.IgngUpdateDotBadge
import net.igng.core.model.SessionAuthStatus
import net.igng.core.network.ApiResult

@Composable
fun ProfileCenterScreen(
    container: BbsAppContainer,
    onLogin: () -> Unit,
    onOpenMyProfile: (String) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCreator: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenAccountSecurity: () -> Unit,
    onOpenPrivacyPreferences: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenRules: () -> Unit,
    bottomInsetPadding: PaddingValues = PaddingValues(bottom = 64.dp),
) {
    val colors = IgngTheme.colors
    val scope = rememberCoroutineScope()
    val activeAccount by container.activeAccount.collectAsState()
    val sessionAuthStatus by container.sessionAuthStatus.collectAsState()
    val familyAccounts by container.familyAccounts.collectAsState()
    val bootstrap by container.bootstrap.collectAsState()
    val updateSnapshot by container.appUpdateManager.snapshot.collectAsState()
    var refreshing by remember { mutableStateOf(false) }
    val sessionExpired = activeAccount != null && sessionAuthStatus.requiresReauth

    LifecycleResumeEffect(activeAccount?.userId) {
        scope.launch { container.refreshBootstrapIfStale() }
        onPauseOrDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding(),
    ) {
        IgngTopBar(
            title = "我的",
            subtitle = if (sessionExpired) "登录会话已失效 · 仅展示本地缓存" else null,
        )
        IgngPullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    container.refreshFamilyAccounts()
                    container.refreshBootstrap(force = true)
                    refreshing = false
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 6.dp,
                    bottom = bottomInsetPadding.calculateBottomPadding() + 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Cross-app family SSO discovery card when signed out
                if (activeAccount == null && familyAccounts.isNotEmpty()) {
                    item(key = "family_sso") {
                        IgngFamilySsoBanner(
                            familyAccounts = familyAccounts,
                            onContinueWithFamilyAccount = { onLogin() },
                        )
                    }
                }

                // Non-destructive session expired banner
                if (sessionExpired) {
                    item(key = "session_expired") {
                        val isLegacy = sessionAuthStatus == SessionAuthStatus.LEGACY_UPGRADE_REQUIRED
                        IgngTile(
                            borderColor = colors.warning.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            IgngBadge(
                                text = if (isLegacy) "需升级 OAuth 2.1 授权" else "登录会话已失效",
                                tone = IgngBadgeTone.Warning,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (isLegacy) {
                                    "旧版凭证已停用，重新通过 IGNG SSO 授权即可恢复收藏、消息与草稿同步。"
                                } else {
                                    "当前仅展示本地缓存标识，重新授权后恢复互动、创作同步与通知。"
                                },
                                style = IgngTheme.type.caption,
                                color = colors.textSecondary,
                            )
                            Spacer(Modifier.height(12.dp))
                            IgngButton(
                                text = "立即重新授权",
                                onClick = onLogin,
                                style = IgngButtonStyle.Primary,
                                icon = Icons.AutoMirrored.Rounded.Login,
                                compact = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                // Identity card
                item(key = "identity") {
                    if (activeAccount == null) {
                        IgngTile {
                            IgngBadge("IGNG SSO 安全登录", tone = IgngBadgeTone.Accent)
                            Spacer(Modifier.height(10.dp))
                            Text("登录你的 IGNG 账户", style = IgngTheme.type.pageTitle, color = colors.textPrimary)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "登录后解锁点赞收藏、关注互动、云端草稿创作与实时通知。",
                                style = IgngTheme.type.body,
                                color = colors.textSecondary,
                            )
                            Spacer(Modifier.height(14.dp))
                            IgngButton(
                                text = "通过 IGNG SSO 登录",
                                icon = Icons.AutoMirrored.Rounded.Login,
                                style = if (familyAccounts.isNotEmpty()) IgngButtonStyle.Secondary else IgngButtonStyle.Primary,
                                onClick = onLogin,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        val acc = activeAccount!!
                        val viewer = bootstrap?.viewer
                        IgngTile(onClick = { onOpenMyProfile(acc.publicUserId) }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                IgngAvatar(avatarUrl = viewer?.user?.avatarUrl ?: acc.avatarUrl, displayName = acc.displayName, size = 54.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(acc.displayName, style = IgngTheme.type.subpageTitle, color = colors.textPrimary)
                                        if (sessionExpired) {
                                            IgngBadge("已失效", tone = IgngBadgeTone.Warning)
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text("@${acc.username} · #${acc.userId}", style = IgngTheme.type.meta, color = colors.textMuted)
                                    val hasBadges = viewer?.level != null || viewer?.risk?.article?.active == true || viewer?.risk?.comment?.active == true
                                    if (hasBadges) {
                                        Spacer(Modifier.height(6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            viewer?.level?.let { lvl ->
                                                IgngBadge("Lv.$lvl · ${viewer.exp ?: 0} EXP", tone = IgngBadgeTone.Accent)
                                            }
                                            if (viewer?.risk?.article?.active == true || viewer?.risk?.comment?.active == true) {
                                                IgngBadge("风控观察中", tone = IgngBadgeTone.Warning)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Group 1: Content & Creation (consolidated grouped list, zero card clutter)
                if (activeAccount != null) {
                    val isAdmin = bootstrap?.viewer?.admin?.isBbsAdmin == true
                    item(key = "social_shortcuts") {
                        IgngGroupedSection(title = "内容与创作") {
                            IgngListRow(
                                title = "我的收藏",
                                icon = Icons.Outlined.BookmarkBorder,
                                onClick = onOpenFavorites,
                                showDivider = true,
                            )
                            IgngListRow(
                                title = "关注与粉丝",
                                icon = Icons.Outlined.Group,
                                onClick = onOpenFollowing,
                                showDivider = true,
                            )
                            IgngListRow(
                                title = "阅读记录",
                                icon = Icons.Outlined.History,
                                onClick = onOpenHistory,
                                showDivider = true,
                            )
                            IgngListRow(
                                title = "创作中心",
                                icon = Icons.Outlined.Create,
                                onClick = onOpenCreator,
                                showDivider = isAdmin,
                            )
                            if (isAdmin) {
                                IgngListRow(
                                    title = "社区治理控制台",
                                    icon = Icons.Outlined.AdminPanelSettings,
                                    iconTint = colors.warning,
                                    onClick = onOpenAdmin,
                                )
                            }
                        }
                    }

                    // Group 2: Account & Privacy
                    item(key = "account_shortcuts") {
                        IgngGroupedSection(title = "账户与隐私") {
                            IgngListRow(
                                title = "账户与安全",
                                icon = Icons.Outlined.ManageAccounts,
                                onClick = onOpenAccountSecurity,
                                showDivider = true,
                            )
                            IgngListRow(
                                title = "隐私与内容过滤",
                                icon = Icons.Outlined.Lock,
                                onClick = onOpenPrivacyPreferences,
                            )
                        }
                    }
                } else {
                    item(key = "guest_history") {
                        IgngGroupedSection(title = "阅读") {
                            IgngListRow(
                                title = "本地阅读记录",
                                icon = Icons.Outlined.History,
                                onClick = onOpenHistory,
                            )
                        }
                    }
                }

                // Group 3: App
                item(key = "app_shortcuts") {
                    IgngGroupedSection(title = "应用") {
                        IgngListRow(
                            title = "应用设置",
                            icon = Icons.Outlined.Settings,
                            onClick = onOpenAppSettings,
                            trailing = if (updateSnapshot.hasBadgeAlert) {
                                { IgngUpdateDotBadge() }
                            } else {
                                null
                            },
                            showDivider = true,
                        )
                        IgngListRow(
                            title = "社区规范与申诉准则",
                            icon = Icons.Outlined.Gavel,
                            onClick = onOpenRules,
                        )
                    }
                }
            }
        }
    }
}

private class UserProfileUiHolder {
    val tabState = mutableIntStateOf(0)
}

@Composable
fun UserProfileScreen(
    container: BbsAppContainer,
    userIdOrUsername: String,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onRequireLogin: () -> Unit,
) {
    val colors = IgngTheme.colors
    val scope = rememberCoroutineScope()
    val activeAccount by container.sessionStore.activeAccount.collectAsState()
    val holder = rememberScreenHolder("bbs_user_profile_ui_$userIdOrUsername") { UserProfileUiHolder() }
    var tab by holder.tabState // 0=posts, 1=collab, 2=activities

    val (profileState, profileHandle) = rememberSwrResource<UserProfileDto>(
        cache = container.swrCache,
        cacheKey = BbsCacheKeys.userProfile(userIdOrUsername),
        scope = IgngCacheScope.SESSION_AWARE,
        policy = BbsCachePolicies.Detail,
        tags = setOf(BbsCacheTags.USER_PROFILES),
        slotKey = "bbs_user_profile_$userIdOrUsername",
    ) {
        container.profileRepository.getUserProfile(userIdOrUsername).toKotlinResult()
    }

    val profile = profileState.data
    var notice by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(colors.canvas).statusBarsPadding()) {
        IgngTopBar(title = profile?.user?.displayName ?: "用户主页", onBack = onBack)
        IgngPullToRefreshBox(
            isRefreshing = profileState.isUserRefreshing,
            isRevalidating = profileState.isRevalidating,
            onRefresh = { profileHandle.refresh() },
            modifier = Modifier.weight(1f),
        ) {
            when {
                profileState.isInitialLoading && profile == null -> IgngLoadingCenter(loading = true)
                profileState.error != null && profile == null -> Box(Modifier.padding(16.dp)) {
                    IgngErrorTile(message = profileState.error!!, onRetry = { profileHandle.refresh() })
                }
                profile != null -> {
                    val p = profile
                    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    LazyColumn(
                        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp + navBottom),
                    ) {
                        notice?.let { msg ->
                            item(key = "notice") {
                                Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                                    IgngInfoBanner("提示", msg, IgngTone.ACCENT)
                                }
                            }
                        }
                        item(key = "profile_card") {
                            Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                                IgngTile {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        IgngAvatar(p.user.avatarUrl, p.user.displayName, size = 54.dp)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(p.user.displayName, style = IgngTheme.type.subpageTitle, color = colors.textPrimary)
                                            Spacer(Modifier.height(2.dp))
                                            Text("@${p.user.username} · Lv.${p.level}", style = IgngTheme.type.meta, color = colors.textMuted)
                                        }
                                    }
                                    p.user.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                                        Spacer(Modifier.height(10.dp))
                                        Text(bio, style = IgngTheme.type.body, color = colors.textSecondary)
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Text("${p.stats.posts} 文章", style = IgngTheme.type.meta.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary)
                                        Text("${p.stats.followers} 粉丝", style = IgngTheme.type.meta.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary)
                                        Text("${p.stats.following} 关注", style = IgngTheme.type.meta.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary)
                                        Text("${BbsFormatters.compactCount(p.stats.totalLikes)} 获赞", style = IgngTheme.type.meta, color = colors.textMuted)
                                    }
                                    if (!p.viewerState.isSelf) {
                                        Spacer(Modifier.height(12.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            IgngPrimaryButton(
                                                text = if (p.viewerState.isFollowing) "已关注" else "关注",
                                                onClick = {
                                                    if (activeAccount == null) { onRequireLogin(); return@IgngPrimaryButton }
                                                    scope.launch {
                                                        when (val r = container.profileRepository.toggleFollow(p.user.id)) {
                                                            is ApiResult.Success -> {
                                                                profileHandle.mutateLocal { cur ->
                                                                    cur.copy(
                                                                        viewerState = cur.viewerState.copy(isFollowing = r.data.following),
                                                                        stats = cur.stats.copy(followers = r.data.followerCount),
                                                                    )
                                                                }
                                                                profileHandle.silentRefresh()
                                                            }
                                                            is ApiResult.Failure -> notice = r.error.message
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                            )
                                            IgngSecondaryButton(
                                                text = if (p.viewerState.blockFollow || p.viewerState.blockView) "取消屏蔽" else "屏蔽",
                                                onClick = {
                                                    if (activeAccount == null) { onRequireLogin(); return@IgngSecondaryButton }
                                                    scope.launch {
                                                        val res = if (p.viewerState.blockFollow || p.viewerState.blockView) {
                                                            container.profileRepository.deleteBlockRule(p.user.id)
                                                        } else {
                                                            container.profileRepository.upsertBlockRule(UpsertBlockRuleRequest(p.user.id, blockFollow = true, blockView = true))
                                                        }
                                                        when (res) {
                                                            is ApiResult.Success -> { notice = "屏蔽设置已更新"; profileHandle.silentRefresh() }
                                                            is ApiResult.Failure -> notice = res.error.message
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Pure BBS 3-tab strip
                        item(key = "tabs") {
                            Row(
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                IgngTagChip("已发布（${p.posts.size}）", selected = tab == 0, onClick = { tab = 0 })
                                IgngTagChip("参与协作（${p.collaboratedPosts.size}）", selected = tab == 1, onClick = { tab = 1 })
                                IgngTagChip("近7天动态（${p.recentActivities.size}）", selected = tab == 2, onClick = { tab = 2 })
                            }
                        }

                        when (tab) {
                            0 -> if (p.posts.isEmpty()) {
                                item { Box(Modifier.padding(horizontal = 16.dp)) { IgngEmptyTile("暂无公开文章", "这位作者还没有发布公开的 BBS 文章。") } }
                            } else {
                                item(key = "posts_top") { IgngHairlineDivider() }
                                itemsIndexed(p.posts, key = { _, it -> it.id }) { idx, post ->
                                    BbsPostRow(
                                        post = post,
                                        onClick = { onOpenPost(post.id) },
                                        showDivider = idx < p.posts.lastIndex,
                                    )
                                }
                                item(key = "posts_bottom") { IgngHairlineDivider() }
                            }
                            1 -> if (p.collaboratedPosts.isEmpty()) {
                                item { Box(Modifier.padding(horizontal = 16.dp)) { IgngEmptyTile("暂无协作文章", "暂无公开的协作署名记录。") } }
                            } else {
                                item(key = "collab_top") { IgngHairlineDivider() }
                                itemsIndexed(p.collaboratedPosts, key = { _, it -> "${it.post.id}_${it.roleTitle}" }) { idx, item ->
                                    Column(modifier = Modifier.fillMaxWidth().background(colors.tile)) {
                                        Box(modifier = Modifier.padding(start = 16.dp, top = 12.dp)) {
                                            IgngBadge("署名角色：${item.roleTitle}", tone = IgngBadgeTone.Accent)
                                        }
                                        BbsPostRow(
                                            post = item.post,
                                            onClick = { onOpenPost(item.post.id) },
                                            showDivider = idx < p.collaboratedPosts.lastIndex,
                                        )
                                    }
                                }
                                item(key = "collab_bottom") { IgngHairlineDivider() }
                            }
                            else -> if (p.recentActivities.isEmpty()) {
                                item { Box(Modifier.padding(horizontal = 16.dp)) { IgngEmptyTile("近 7 天暂无动态", "近期没有新的发布、评论或点赞互动。") } }
                            } else {
                                item(key = "act_group") {
                                    Box(Modifier.padding(horizontal = 16.dp)) {
                                        IgngGroupedSection {
                                            p.recentActivities.forEachIndexed { idx, act ->
                                                val headline = when (act.kind) {
                                                    "post" -> "发布了文章《${act.title}》"
                                                    "comment" -> "评论了《${act.title}》"
                                                    else -> "赞了文章《${act.title}》"
                                                }
                                                IgngListRow(
                                                    title = headline,
                                                    subtitle = act.excerpt?.takeIf { it.isNotBlank() } ?: BbsFormatters.relativeTime(act.createdAt),
                                                    value = if (!act.excerpt.isNullOrBlank()) BbsFormatters.relativeTime(act.createdAt) else null,
                                                    showChevron = act.targetId != null,
                                                    showDivider = idx < p.recentActivities.lastIndex,
                                                    onClick = { act.targetId?.let(onOpenPost) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FavoritesScreen(
    container: BbsAppContainer,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
) {
    val colors = IgngTheme.colors

    val (favState, favHandle) = rememberSwrResource<PaginatedResponseDto<FavoriteItemDto>>(
        cache = container.swrCache,
        cacheKey = BbsCacheKeys.FAVORITES,
        scope = IgngCacheScope.USER_ONLY,
        policy = BbsCachePolicies.Standard,
        tags = setOf(BbsCacheTags.FAVORITES),
        slotKey = "bbs_favorites_slot",
    ) {
        container.profileRepository.listFavorites(pageSize = 40).toKotlinResult()
    }

    val items = favState.data?.data.orEmpty()

    Column(modifier = Modifier.fillMaxSize().background(colors.canvas).statusBarsPadding()) {
        IgngTopBar(title = "我的收藏", onBack = onBack)
        IgngPullToRefreshBox(
            isRefreshing = favState.isUserRefreshing,
            isRevalidating = favState.isRevalidating,
            onRefresh = { favHandle.refresh() },
            modifier = Modifier.weight(1f),
        ) {
            when {
                favState.isInitialLoading && items.isEmpty() -> IgngLoadingCenter(loading = true)
                favState.error != null && items.isEmpty() -> Box(Modifier.padding(16.dp)) {
                    IgngErrorTile(favState.error!!, onRetry = { favHandle.refresh() })
                }
                items.isEmpty() -> Box(Modifier.padding(16.dp)) {
                    IgngEmptyTile("收藏夹还是空的", "在阅读文章时点击底部书签图标即可收藏。")
                }
                else -> {
                    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    LazyColumn(
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp + navBottom),
                    ) {
                        item(key = "fav_top") { IgngHairlineDivider() }
                        itemsIndexed(items, key = { _, it -> it.post.id }) { idx, fav ->
                            BbsPostRow(
                                post = fav.post,
                                onClick = { onOpenPost(fav.post.id) },
                                showDivider = idx < items.lastIndex,
                            )
                        }
                        item(key = "fav_bottom") { IgngHairlineDivider() }
                    }
                }
            }
        }
    }
}

private class FollowingUiHolder {
    val tabState = mutableStateOf("following")
}

@Composable
fun FollowingScreen(
    container: BbsAppContainer,
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
) {
    val colors = IgngTheme.colors
    val scope = rememberCoroutineScope()
    val holder = rememberScreenHolder("bbs_following_ui") { FollowingUiHolder() }
    var tab by holder.tabState

    val (followingState, followingHandle) = rememberSwrResource<PaginatedResponseDto<FollowingItemDto>>(
        cache = container.swrCache,
        cacheKey = BbsCacheKeys.following(tab),
        scope = IgngCacheScope.USER_ONLY,
        policy = BbsCachePolicies.Standard,
        tags = setOf(BbsCacheTags.FOLLOWING),
        slotKey = "bbs_following_slot",
    ) {
        container.profileRepository.listFollowing(tab = tab, pageSize = 50).toKotlinResult()
    }

    val list = followingState.data?.data.orEmpty()

    Column(modifier = Modifier.fillMaxSize().background(colors.canvas).statusBarsPadding()) {
        IgngTopBar(
            title = "关注与粉丝",
            onBack = onBack,
            actions = {
                IgngTagChip("我关注的", selected = tab == "following", onClick = { tab = "following" })
                Spacer(Modifier.width(4.dp))
                IgngTagChip("关注我的", selected = tab == "followers", onClick = { tab = "followers" })
            },
        )
        IgngPullToRefreshBox(
            isRefreshing = followingState.isUserRefreshing,
            isRevalidating = followingState.isRevalidating,
            onRefresh = { followingHandle.refresh() },
            modifier = Modifier.weight(1f),
        ) {
            when {
                followingState.isInitialLoading && list.isEmpty() -> IgngLoadingCenter(loading = true)
                followingState.error != null && list.isEmpty() -> Box(Modifier.padding(16.dp)) {
                    IgngErrorTile(followingState.error!!, onRetry = { followingHandle.refresh() })
                }
                list.isEmpty() -> Box(Modifier.padding(16.dp)) {
                    IgngEmptyTile("列表为空", "暂时还没有相关用户记录。")
                }
                else -> {
                    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp + navBottom),
                    ) {
                        item(key = "following_group") {
                            IgngGroupedSection {
                                list.forEachIndexed { idx, row ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        IgngAvatar(
                                            avatarUrl = row.user.avatarUrl,
                                            displayName = row.user.displayName,
                                            size = 38.dp,
                                            onClick = { onOpenUser(row.user.id) },
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(row.user.displayName, style = IgngTheme.type.bodyMedium, color = colors.textPrimary)
                                            Text("@${row.user.username}", style = IgngTheme.type.meta, color = colors.textMuted)
                                        }
                                        IgngTagChip(
                                            text = if (row.isFollowing) "已关注" else "+ 回关",
                                            selected = row.isFollowing,
                                            tone = IgngTone.ACCENT,
                                            onClick = {
                                                scope.launch {
                                                    container.profileRepository.toggleFollow(row.user.id)
                                                    followingHandle.silentRefresh()
                                                }
                                            },
                                        )
                                    }
                                    if (idx < list.lastIndex) {
                                        IgngHairlineDivider(startIndent = 66.dp, endIndent = 16.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReadingHistoryScreen(
    container: BbsAppContainer,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
) {
    val colors = IgngTheme.colors
    val scope = rememberCoroutineScope()
    val history by container.settingsRepository.readingHistory.collectAsState(initial = emptyList())
    var confirmClear by remember { mutableStateOf(false) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(modifier = Modifier.fillMaxSize().background(colors.canvas).statusBarsPadding()) {
        IgngTopBar(
            title = "阅读记录",
            onBack = onBack,
            actions = {
                if (history.isNotEmpty()) {
                    IgngTagChip("清空记录", tone = IgngTone.DANGER, onClick = { confirmClear = true })
                }
            },
        )
        if (history.isEmpty()) {
            Box(Modifier.padding(16.dp)) { IgngEmptyTile("暂无阅读记录", "你浏览过的文章进度会自动保存在本机，方便下次继续阅读。") }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp + navBottom),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(history, key = { _, it -> it.postId }) { _, entry ->
                    BbsContinueReadingCard(entry = entry, onResume = { onOpenPost(entry.postId) })
                }
            }
        }
    }

    if (confirmClear) {
        IgngConfirmDialog(
            title = "清空本地阅读记录",
            message = "将清除本机保存的所有阅读历史与断点进度，不会影响你的云端收藏。",
            confirmLabel = "清空记录",
            danger = true,
            onDismiss = { confirmClear = false },
            onConfirm = {
                confirmClear = false
                scope.launch { container.settingsRepository.clearReadingHistory() }
            },
        )
    }
}
