package net.igng.bbs.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.ThumbUpOffAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.igng.bbs.core.model.BbsFormatters
import net.igng.bbs.core.model.LocalReadingHistoryEntry
import net.igng.bbs.core.model.PostSummaryDto
import net.igng.bbs.core.model.RecommendationSlotDto
import net.igng.bbs.core.model.RiskSummaryDto
import net.igng.core.designsystem.*

/**
 * Forwarding wrapper onto the unified docked [IgngFloatingDockBar] in `:core:designsystem`.
 */
data class BbsNavTabItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val unselectedIcon: ImageVector = icon,
    val badgeCount: Int = 0,
)

@Composable
fun BbsFloatingNavBar(
    tabs: List<BbsNavTabItem>,
    selectedRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    IgngFloatingDockBar(
        items = tabs.map {
            IgngNavTabItem(
                id = it.route,
                label = it.label,
                icon = it.icon,
                unselectedIcon = it.unselectedIcon,
                badgeCount = it.badgeCount,
            )
        },
        selectedId = selectedRoute,
        onSelect = onSelect,
        modifier = modifier,
    )
}

/**
 * Calm editorial recommendations rail ("社区精选") aligned with IGNG Web's `RecommendSection`:
 * plain-text kicker eyebrow (no inflated capsule badges), crisp 2-line title, quiet author byline.
 */
@Composable
fun BbsRecommendRail(
    slots: List<RecommendationSlotDto>,
    onPostClick: (PostSummaryDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IgngTheme.colors
    if (slots.isEmpty()) return

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(slots, key = { _, slot -> "rec_${slot.post.id}_${slot.type}" }) { _, slot ->
            val typeLabel = when (slot.type) {
                "hot" -> "社区热议"
                "trending" -> "本周升温"
                "discussed" -> "讨论正热"
                "updated" -> "刚刚更新"
                "newcomer" -> "新作者首作"
                else -> "最新发布"
            }
            val kickerColor = when (slot.type) {
                "hot" -> colors.accent
                "trending" -> colors.success
                "discussed" -> colors.warning
                else -> colors.textSecondary
            }
            IgngTile(
                modifier = Modifier.width(256.dp),
                padding = PaddingValues(14.dp),
                onClick = { onPostClick(slot.post) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = typeLabel,
                        style = IgngTheme.type.badge.copy(fontWeight = FontWeight.SemiBold),
                        color = kickerColor,
                        maxLines = 1,
                    )
                    if (slot.reason.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = slot.reason,
                            style = IgngTheme.type.meta,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = slot.post.title,
                    style = IgngTheme.type.cardTitle,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.height(44.dp),
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    IgngAvatar(
                        avatarUrl = slot.post.author?.avatarUrl,
                        displayName = slot.post.author?.displayName ?: "作者",
                        size = 18.dp,
                    )
                    Text(
                        text = slot.post.author?.displayName ?: "匿名用户",
                        style = IgngTheme.type.meta,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    slot.post.category?.let { cat ->
                        Text(
                            text = BbsFormatters.categoryDisplayName(cat),
                            style = IgngTheme.type.meta,
                            color = colors.textMuted,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Slim "继续阅读" resume strip with compact progress bar and 1-tap jump.
 */
@Composable
fun BbsContinueReadingCard(
    entry: LocalReadingHistoryEntry,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IgngTheme.colors
    IgngTile(
        modifier = modifier.fillMaxWidth(),
        padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = onResume,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "继续阅读",
                        style = IgngTheme.type.meta.copy(fontWeight = FontWeight.Medium),
                        color = colors.accent,
                    )
                    Text(
                        text = "· ${(entry.progressRatio * 100).toInt().coerceIn(0, 100)}%",
                        style = IgngTheme.type.meta,
                        color = colors.textMuted,
                    )
                }
                Text(
                    text = entry.title,
                    style = IgngTheme.type.bodyMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { entry.progressRatio.coerceIn(0.03f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .clip(RoundedCornerShape(IgngRadius.pill)),
            color = colors.accent,
            trackColor = colors.line,
        )
    }
}

/**
 * Editorial hairline-separated post row aligned 1:1 with IGNG Web's `PostRow.js` / `feed.css` (Decision 2).
 * Content-first visual hierarchy: Title -> 2-line excerpt -> single quiet plain-text metadata & stats row
 * (no capsule-tag inflation, no card-per-item visual clutter).
 */
@Composable
fun BbsPostRow(
    post: PostSummaryDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onAuthorClick: (() -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null,
    showCategory: Boolean = true,
    showModerationBadge: Boolean = false,
    showSeriesBadge: Boolean = true,
    showDivider: Boolean = false,
) {
    val colors = IgngTheme.colors
    val intensity = IgngTheme.motionIntensity
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val rowBg by animateColorAsState(
        targetValue = if (pressed) colors.tilePressed else colors.tile,
        animationSpec = IgngMotion.microTween(intensity),
        label = "postRowBg",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(rowBg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            // Optional status eyebrow (only rendered when pinned or showing moderation state)
            if (post.isPinned || showModerationBadge) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 5.dp),
                ) {
                    if (post.isPinned) {
                        IgngBadge(text = "置顶", tone = IgngBadgeTone.Accent)
                    }
                    if (showModerationBadge) {
                        val badgeTone = when {
                            post.status == 1 -> IgngBadgeTone.Neutral
                            post.moderationStatus == "pending" -> IgngBadgeTone.Warning
                            post.moderationStatus == "rejected" || post.banStatus in listOf(2, 4) -> IgngBadgeTone.Danger
                            else -> IgngBadgeTone.Success
                        }
                        IgngBadge(
                            text = BbsFormatters.postStatusLabel(post.status, post.banStatus, post.moderationStatus),
                            tone = badgeTone,
                        )
                    }
                }
            }

            // 1. Title first (primary focal anchor)
            Text(
                text = post.title,
                style = IgngTheme.type.cardTitle,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // 2. Quiet 2-line excerpt
            val snippet = post.summary?.takeIf { it.isNotBlank() } ?: post.excerpt
            if (snippet.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = snippet,
                    style = IgngTheme.type.body,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // 3. Single plain-text metadata + tabular stats footer (zero pill bloat)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    IgngAvatar(
                        avatarUrl = post.author?.avatarUrl,
                        displayName = post.author?.displayName ?: "作者",
                        size = 18.dp,
                        onClick = onAuthorClick,
                    )
                    Text(
                        text = post.author?.displayName ?: "匿名用户",
                        style = IgngTheme.type.meta.copy(fontWeight = FontWeight.Medium),
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .let { if (onAuthorClick != null) it.clickable(onClick = onAuthorClick) else it },
                    )
                    Text(
                        text = "· ${BbsFormatters.relativeTime(post.createdAt)}",
                        style = IgngTheme.type.meta,
                        color = colors.textMuted,
                        maxLines = 1,
                    )
                    if (showCategory) {
                        post.category?.let { cat ->
                            Text(
                                text = "· ${BbsFormatters.categoryDisplayName(cat)}",
                                style = IgngTheme.type.meta,
                                color = colors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    val firstTag = BbsFormatters.visiblePostTags(post.tags, post.category).firstOrNull()
                    if (firstTag != null && onTagClick != null) {
                        Text(
                            text = BbsFormatters.tagDisplayLabel(firstTag),
                            style = IgngTheme.type.meta,
                            color = colors.accent.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { onTagClick(firstTag) },
                        )
                    } else if (showSeriesBadge && post.series != null) {
                        Text(
                            text = "· ${post.series!!.name}",
                            style = IgngTheme.type.meta,
                            color = colors.accent.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Compact tabular stats (likes + comments; matching web mobile row density)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricMini(icon = Icons.Rounded.ThumbUpOffAlt, count = post.likeCount)
                    MetricMini(icon = Icons.AutoMirrored.Rounded.Comment, count = post.commentCount)
                }
            }
        }

        if (showDivider) {
            IgngHairlineDivider(startIndent = 16.dp, endIndent = 16.dp)
        }
    }
}

/**
 * Compatibility entry point rendering [BbsPostRow] inside a clean hairline card or directly as a row.
 */
@Composable
fun BbsPostRowCard(
    post: PostSummaryDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onAuthorClick: (() -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null,
    showModerationBadge: Boolean = false,
    showSeriesBadge: Boolean = true,
) {
    IgngTile(
        modifier = modifier.fillMaxWidth(),
        padding = PaddingValues(0.dp),
    ) {
        BbsPostRow(
            post = post,
            onClick = onClick,
            onAuthorClick = onAuthorClick,
            onTagClick = onTagClick,
            showModerationBadge = showModerationBadge,
            showSeriesBadge = showSeriesBadge,
            showDivider = false,
        )
    }
}

@Composable
private fun MetricMini(icon: ImageVector, count: Int) {
    val colors = IgngTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(13.dp))
        Text(
            text = BbsFormatters.compactCount(count),
            style = IgngTheme.type.meta,
            color = colors.textMuted,
        )
    }
}

/**
 * Auto-hiding floating article action bar with smooth spring heart/bookmark tactile animations.
 */
@Composable
fun BbsFloatingArticleBar(
    visible: Boolean,
    liked: Boolean,
    likeCount: Int,
    favorited: Boolean,
    commentCount: Int,
    onLike: () -> Unit,
    onFavorite: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IgngTheme.colors
    val intensity = IgngTheme.motionIntensity
    val shape = RoundedCornerShape(IgngRadius.pill)

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(IgngMotion.dockSpring(intensity)) { it } + fadeIn(IgngMotion.microTween(intensity)),
        exit = slideOutVertically(IgngMotion.dockSpring(intensity)) { it } + fadeOut(IgngMotion.microTween(intensity)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .clip(shape)
                    .background(colors.glassBar)
                    .border(IgngStroke.hairline, colors.lineStrong, shape)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val likeScale by animateFloatAsState(
                    targetValue = if (liked) 1.12f else 1f,
                    animationSpec = IgngMotion.quickSpring(intensity),
                    label = "likeScale",
                )
                val favScale by animateFloatAsState(
                    targetValue = if (favorited) 1.12f else 1f,
                    animationSpec = IgngMotion.quickSpring(intensity),
                    label = "favScale",
                )

                ActionPillItem(
                    icon = if (liked) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbUpOffAlt,
                    label = BbsFormatters.compactCount(likeCount),
                    tint = if (liked) colors.accent else colors.textSecondary,
                    iconScale = likeScale,
                    onClick = onLike,
                )
                ActionPillItem(
                    icon = if (favorited) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    label = if (favorited) "已收藏" else "收藏",
                    tint = if (favorited) colors.warning else colors.textSecondary,
                    iconScale = favScale,
                    onClick = onFavorite,
                )
                ActionPillItem(
                    icon = Icons.AutoMirrored.Rounded.Comment,
                    label = BbsFormatters.compactCount(commentCount),
                    tint = colors.textSecondary,
                    onClick = onComment,
                )
                ActionPillItem(
                    icon = Icons.Rounded.Share,
                    label = "分享",
                    tint = colors.textSecondary,
                    onClick = onShare,
                )
            }
        }
    }
}

@Composable
private fun ActionPillItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    iconScale: Float = 1f,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(IgngRadius.pill))
            .igngPressScale(haptic = true, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
        )
        Text(
            text = label,
            style = IgngTheme.type.meta.copy(fontWeight = FontWeight.Medium),
            color = tint,
        )
    }
}

/**
 * Dual-domain risk control visual meter (ARTICLE 30% rejection ratio + COMMENT 3-strike meter).
 */
@Composable
fun BbsRiskMeterCard(
    risk: RiskSummaryDto,
    modifier: Modifier = Modifier,
) {
    val colors = IgngTheme.colors
    IgngTile(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "双域内容风控状态",
            style = IgngTheme.type.sectionTitle,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(10.dp))

        // Article domain
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "文章域（近 ${risk.article.windowDays} 天）", style = IgngTheme.type.body, color = colors.textSecondary)
            IgngBadge(
                text = if (risk.article.active) "风控中（先审后发）" else "正常（先发后审）",
                tone = if (risk.article.active) IgngBadgeTone.Warning else IgngBadgeTone.Success,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { risk.article.rejectionRate.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(IgngRadius.pill)),
            color = if (risk.article.active) colors.warning else colors.success,
            trackColor = colors.line,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "通过 ${risk.article.approved} · 未通过 ${risk.article.rejected} · 拒绝率 ${(risk.article.rejectionRate * 100).toInt()}%（≥30% 且样本≥3 触发）",
            style = IgngTheme.type.meta,
            color = colors.textMuted,
        )

        Spacer(Modifier.height(14.dp))

        // Comment domain
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "评论域（近 ${risk.comment.windowDays} 天）", style = IgngTheme.type.body, color = colors.textSecondary)
            IgngBadge(
                text = if (risk.comment.active) "风控中（先审后发）" else "正常（即发即显）",
                tone = if (risk.comment.active) IgngBadgeTone.Warning else IgngBadgeTone.Success,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (risk.comment.strikes / 3f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(IgngRadius.pill)),
            color = if (risk.comment.active) colors.warning else colors.success,
            trackColor = colors.line,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "近 30 天举报成立：${risk.comment.strikes} / 3 次",
            style = IgngTheme.type.meta,
            color = colors.textMuted,
        )
    }
}
