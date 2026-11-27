package net.igng.bbs.feature.creator

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.igng.bbs.common.BbsPostRow
import net.igng.bbs.common.BbsRiskMeterCard
import net.igng.bbs.core.data.BbsAppContainer
import net.igng.bbs.core.data.BbsCacheKeys
import net.igng.bbs.core.data.BbsCachePolicies
import net.igng.bbs.core.data.BbsCacheTags
import net.igng.bbs.core.model.BbsFormatters
import net.igng.bbs.core.model.CreatorOverviewDto
import net.igng.core.data.IgngCacheScope
import net.igng.core.data.rememberSwrResource
import net.igng.core.data.toKotlinResult
import net.igng.core.designsystem.ChartPoint
import net.igng.core.designsystem.IgngButton
import net.igng.core.designsystem.IgngButtonStyle
import net.igng.core.designsystem.IgngEmptyTile
import net.igng.core.designsystem.IgngErrorTile
import net.igng.core.designsystem.IgngGroupedSection
import net.igng.core.designsystem.IgngLineChart
import net.igng.core.designsystem.IgngListRow
import net.igng.core.designsystem.IgngLoadingCenter
import net.igng.core.designsystem.IgngPullToRefreshBox
import net.igng.core.designsystem.IgngRadius
import net.igng.core.designsystem.IgngStatTile
import net.igng.core.designsystem.IgngTagChip
import net.igng.core.designsystem.IgngTheme
import net.igng.core.designsystem.IgngTile
import net.igng.core.designsystem.IgngTone
import net.igng.core.designsystem.IgngTopBar

@Composable
fun CreatorHubScreen(
    container: BbsAppContainer,
    onNewArticle: () -> Unit,
    onEditPost: (String) -> Unit,
    onOpenPosts: () -> Unit,
    onOpenColumns: () -> Unit,
    onOpenSeries: () -> Unit,
    onOpenComments: () -> Unit,
    onOpenReviews: () -> Unit,
    onOpenMedia: () -> Unit,
    onRequireLogin: () -> Unit,
    bottomInsetPadding: PaddingValues = PaddingValues(bottom = 96.dp),
) {
    val colors = IgngTheme.colors
    val activeAccount by container.activeAccount.collectAsState()
    val sessionAuthStatus by container.sessionAuthStatus.collectAsState()
    val requiresReauth = activeAccount != null && sessionAuthStatus.requiresReauth
    val isAuthed = activeAccount != null && !sessionAuthStatus.requiresReauth

    val (overviewState, overviewHandle) = rememberSwrResource<CreatorOverviewDto>(
        cache = container.swrCache,
        cacheKey = BbsCacheKeys.CREATOR_OVERVIEW,
        scope = IgngCacheScope.USER_ONLY,
        policy = BbsCachePolicies.Standard,
        tags = setOf(BbsCacheTags.CREATOR),
        enabled = isAuthed,
        slotKey = "bbs_creator_overview",
    ) {
        container.creatorRepository.getOverview().toKotlinResult()
    }

    val overview = overviewState.data

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding(),
    ) {
        IgngTopBar(
            title = "创作中心",
            actions = {
                if (isAuthed) {
                    IgngButton(
                        text = "写文章",
                        icon = Icons.Rounded.Add,
                        style = IgngButtonStyle.Primary,
                        compact = true,
                        onClick = onNewArticle,
                    )
                }
            },
        )

        if (!isAuthed) {
            Box(Modifier.padding(16.dp)) {
                IgngEmptyTile(
                    title = if (requiresReauth) "重新授权以继续创作" else "登录开启 BBS 创作空间",
                    subtitle = if (requiresReauth) {
                        "当前登录会话已失效，重新通过 IGNG SSO 授权即可恢复草稿编辑、专栏管理与阅读趋势统计。"
                    } else {
                        "使用原生所见即所得块级编辑器撰写长文、管理专栏与系列合集、查看 30 天阅读趋势与审核申诉状态。"
                    },
                    actionLabel = if (requiresReauth) "重新授权登录" else "登录 IGNG 账户",
                    onAction = onRequireLogin,
                )
            }
            return
        }

        IgngPullToRefreshBox(
            isRefreshing = overviewState.isUserRefreshing,
            isRevalidating = overviewState.isRevalidating,
            onRefresh = { overviewHandle.refresh() },
            modifier = Modifier.weight(1f),
        ) {
            when {
                overviewState.isInitialLoading && overview == null -> IgngLoadingCenter(loading = true)
                overviewState.error != null && overview == null -> Box(Modifier.padding(16.dp)) {
                    IgngErrorTile(message = overviewState.error!!, onRetry = { overviewHandle.refresh() })
                }
                overview != null -> {
                    val data = overview
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = bottomInsetPadding.calculateBottomPadding() + 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // Totals 2-column summary
                        item(key = "stats_row_1") {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                IgngStatTile(
                                    label = "已发布文章",
                                    value = data.totals.published.toString(),
                                    hint = "草稿 ${data.totals.drafts} · 审核中 ${data.totals.pending}",
                                    tone = IgngTone.ACCENT,
                                    modifier = Modifier.weight(1f),
                                )
                                IgngStatTile(
                                    label = "累计阅读",
                                    value = BbsFormatters.compactCount(data.totals.views),
                                    hint = "获赞 ${BbsFormatters.compactCount(data.totals.likes)} · 评论 ${BbsFormatters.compactCount(data.totals.comments)}",
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        // Daily Submission Budget
                        item(key = "daily_budget") {
                            val budget = data.dailyBudget
                            val ratio = if (budget.limit > 0) (budget.used.toFloat() / budget.limit.toFloat()).coerceIn(0f, 1f) else 0f
                            IgngTile(padding = PaddingValues(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("今日投稿额度（Lv.${budget.level}）", style = IgngTheme.type.bodyMedium, color = colors.textPrimary)
                                    Text(
                                        text = "${budget.used} / ${budget.limit}",
                                        style = IgngTheme.type.meta.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (budget.allowed) colors.accent else colors.danger,
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { ratio },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(IgngRadius.pill)),
                                    color = if (budget.allowed) colors.accent else colors.danger,
                                    trackColor = colors.line,
                                )
                            }
                        }

                        // Pending Todos Banner
                        val todos = data.todos
                        val totalTodos = todos.pendingPostInvites + todos.pendingColumnEvents + todos.pendingSubscriptions + todos.rejectedPosts
                        if (totalTodos > 0) {
                            item(key = "todos") {
                                IgngTile(soft = true) {
                                    Text("待处理事项（$totalTodos）", style = IgngTheme.type.bodyMedium, color = colors.warning)
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (todos.pendingPostInvites > 0) {
                                            IgngTagChip(text = "协作邀请 ${todos.pendingPostInvites}", tone = IgngTone.ACCENT, onClick = onOpenPosts)
                                        }
                                        if (todos.pendingColumnEvents + todos.pendingSubscriptions > 0) {
                                            IgngTagChip(text = "专栏待审 ${todos.pendingColumnEvents + todos.pendingSubscriptions}", tone = IgngTone.WARNING, onClick = onOpenColumns)
                                        }
                                        if (todos.rejectedPosts > 0) {
                                            IgngTagChip(text = "未通过 ${todos.rejectedPosts}", tone = IgngTone.DANGER, onClick = onOpenReviews)
                                        }
                                    }
                                }
                            }
                        }

                        // 30-day Views Trend
                        item(key = "views_chart") {
                            IgngTile {
                                Text("近 30 天阅读趋势", style = IgngTheme.type.sectionTitle, color = colors.textPrimary)
                                Spacer(Modifier.height(10.dp))
                                IgngLineChart(
                                    points = data.viewsTrend.map { ChartPoint(it.date, it.views) },
                                )
                            }
                        }

                        // Quiet Craft grouped management list (replacing 6 boxed tiles)
                        item(key = "quick_grid") {
                            IgngGroupedSection(title = "创作管理工具") {
                                IgngListRow(
                                    title = "文章与草稿",
                                    subtitle = "版本 · 协作 · 数据",
                                    icon = Icons.AutoMirrored.Outlined.Article,
                                    onClick = onOpenPosts,
                                    showDivider = true,
                                )
                                IgngListRow(
                                    title = "专栏管理",
                                    subtitle = "成员 · 订阅 · 置顶",
                                    icon = Icons.Outlined.FolderShared,
                                    onClick = onOpenColumns,
                                    showDivider = true,
                                )
                                IgngListRow(
                                    title = "系列合集",
                                    subtitle = "章节编排 · 趋势",
                                    icon = Icons.Outlined.AutoStories,
                                    onClick = onOpenSeries,
                                    showDivider = true,
                                )
                                IgngListRow(
                                    title = "评论管理",
                                    subtitle = "收到的文章讨论",
                                    icon = Icons.AutoMirrored.Outlined.Comment,
                                    onClick = onOpenComments,
                                    showDivider = true,
                                )
                                IgngListRow(
                                    title = "审核与申诉",
                                    subtitle = "进度 · 复核申诉",
                                    icon = Icons.Outlined.Gavel,
                                    onClick = onOpenReviews,
                                    showDivider = true,
                                )
                                IgngListRow(
                                    title = "媒体资产",
                                    subtitle = "配额 · 图床上传",
                                    icon = Icons.Outlined.PermMedia,
                                    onClick = onOpenMedia,
                                    showDivider = false,
                                )
                            }
                        }

                        // Dual-domain Risk Meter
                        item(key = "risk_card") {
                            BbsRiskMeterCard(risk = data.risk)
                        }

                        // Continue Writing (hairline rows inside one clean surface)
                        if (data.continueWriting.isNotEmpty()) {
                            item(key = "continue_group") {
                                IgngGroupedSection(
                                    title = "最近编辑",
                                    actionLabel = "全部文章",
                                    onAction = onOpenPosts,
                                ) {
                                    data.continueWriting.forEachIndexed { idx, post ->
                                        BbsPostRow(
                                            post = post,
                                            onClick = { onEditPost(post.id) },
                                            showModerationBadge = true,
                                            showDivider = idx < data.continueWriting.lastIndex,
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
