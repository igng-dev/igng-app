package net.igng.mcstatus.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt
import net.igng.core.designsystem.IgngDetailSheet
import net.igng.core.designsystem.IgngTheme
import net.igng.mcstatus.core.data.ChatPersonaDto
import net.igng.mcstatus.core.data.ChatYunyingActivityDto
import net.igng.mcstatus.core.data.ChatYunyingMoodDto
import net.igng.mcstatus.core.data.ChatYunyingStatusDto

/**
 * Collapsed-until-tapped detail sheet displaying Yunying's comprehensive runtime status
 * for the current IM conversation (base persona, orthogonal modes, group chat switch,
 * wake/activity telemetry, and mood state).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatYunyingStatusSheet(
    status: ChatYunyingStatusDto?,
    fallbackPersona: ChatPersonaDto?,
    channelTitle: String,
    isPrivate: Boolean,
    today: LocalDate,
    onDismiss: () -> Unit,
) {
    val colors = IgngTheme.colors
    val semantic = chatSemanticColors(colors.isDark)
    val effectiveBase = status?.base ?: fallbackPersona
    val headlineLabel = status?.summaryLabel?.takeIf { it.isNotBlank() }
        ?: effectiveBase?.label?.takeIf { it.isNotBlank() }
        ?: "云萤"

    IgngDetailSheet(
        onDismiss = onDismiss,
        title = "云萤状态",
        subtitle = buildString {
            append(channelTitle.ifBlank { if (isPrivate) "私聊会话" else "群聊会话" })
            append(" · ")
            append(headlineLabel)
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. Persona & Active Modes section
            StatusSectionCard(title = "人格与模式", icon = Icons.Rounded.AutoAwesome, iconTint = semantic.personaFg) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatusPill(
                        text = "基底：${effectiveBase?.label?.ifBlank { "云萤" } ?: "云萤"}",
                        bg = semantic.personaBg,
                        fg = semantic.personaFg,
                    )
                    if (status != null) {
                        if (status.catgirlMode) {
                            StatusPill(
                                text = "猫娘模式 · 开",
                                bg = semantic.relationshipBg,
                                fg = semantic.relationshipFg,
                            )
                        }
                        if (status.xingqingMode) {
                            StatusPill(
                                text = "性情模式 · 开",
                                bg = colors.accentSoft,
                                fg = colors.accent,
                            )
                        }
                        if (!status.catgirlMode && !status.xingqingMode) {
                            StatusPill(
                                text = "标准模式",
                                bg = colors.tileSoft,
                                fg = colors.textSecondary,
                            )
                        }
                        if (status.paused) {
                            StatusPill(
                                text = "已暂停回复",
                                bg = colors.warningBg,
                                fg = colors.warning,
                            )
                        }
                        val chatEnabled = status.chatModeEnabled
                        if (!isPrivate && chatEnabled != null) {
                            StatusPill(
                                text = if (chatEnabled) "群聊对话 · 开" else "群聊对话 · 关",
                                bg = if (chatEnabled) colors.successBg else colors.tileSoft,
                                fg = if (chatEnabled) colors.success else colors.textMuted,
                            )
                        }
                        val prov = status.provisioningStatus?.trim().orEmpty()
                        if (prov.isNotEmpty() && prov != "ready") {
                            StatusPill(
                                text = "初始化：$prov",
                                bg = colors.tileSoft,
                                fg = colors.textMuted,
                            )
                        }
                    }
                }

                val desc = effectiveBase?.description?.takeIf { it.isNotBlank() }
                    ?: "当前会话由云萤响应并维护上下文记忆。"
                Text(
                    text = desc,
                    style = IgngTheme.type.caption,
                    color = colors.textSecondary,
                )

                if (status?.paused == true) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.warningBg)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.PauseCircle, null, tint = colors.warning, modifier = Modifier.size(16.dp))
                        Text(
                            text = "云萤当前在此会话处于暂停状态，暂不会主动插话回复。",
                            style = IgngTheme.type.caption,
                            color = colors.warning,
                        )
                    }
                }
            }

            // 2. Wake & Activity section
            status?.activity?.let { act ->
                YunyingActivitySection(activity = act, today = today)
            }

            // 3. Mood & 7-dim breakdown section
            status?.mood?.let { mood ->
                YunyingMoodSection(mood = mood, semantic = semantic, today = today)
            }

            // Graceful hint when backend doesn't have a runtime status row yet
            if (status == null || !status.available) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.tileSoft)
                        .border(0.5.dp, colors.line, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    Text(
                        text = if (status == null) {
                            "暂未获取到该会话的实时运行状态，当前仅展示人设配置。"
                        } else {
                            "该会话尚未建立云萤实时状态快照（可能尚未开启对话或暂无近期交互）。"
                        },
                        style = IgngTheme.type.caption,
                        color = colors.textMuted,
                    )
                }
            }

            val updatedAt = status?.updatedAt ?: status?.mood?.updatedAt
            if (!updatedAt.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(Icons.Rounded.Schedule, null, tint = colors.textMuted, modifier = Modifier.size(13.dp))
                    Text(
                        text = "状态快照更新于 ${formatFullDateTime(updatedAt)}",
                        style = IgngTheme.type.badge.copy(fontFeatureSettings = "tnum"),
                        color = colors.textMuted,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun YunyingActivitySection(
    activity: ChatYunyingActivityDto,
    today: LocalDate,
) {
    val colors = IgngTheme.colors
    val wakeLabel = activity.wakeModeLabel?.takeIf { it.isNotBlank() }
        ?: activity.wakeMode?.takeIf { it.isNotBlank() }
        ?: "常规待机"
    val probPct = activity.wakeProbability?.let { "${(it * 100.0).roundToInt().coerceIn(0, 100)}%" }
    val isSleeping = activity.wakeMode.equals("sleep", ignoreCase = true) || !activity.sleepUntil.isNullOrBlank()

    StatusSectionCard(
        title = "活跃与唤醒",
        icon = if (isSleeping) Icons.Rounded.Bedtime else Icons.Rounded.Bolt,
        iconTint = if (isSleeping) colors.warning else colors.accent,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusPill(
                    text = wakeLabel,
                    bg = if (isSleeping) colors.warningBg else colors.accentSoft,
                    fg = if (isSleeping) colors.warning else colors.accent,
                )
                if (probPct != null) {
                    StatusPill(
                        text = "插话概率 $probPct",
                        bg = colors.tileSoft,
                        fg = colors.textSecondary,
                    )
                }
            }
        }

        if (!activity.sleepUntil.isNullOrBlank()) {
            Text(
                text = "小睡至 ${formatTimeDividerLabel(activity.sleepUntil, today = today)}",
                style = IgngTheme.type.caption,
                color = colors.warning,
            )
        }

        if (!activity.lastIncomingAt.isNullOrBlank() || !activity.lastAiReplyAt.isNullOrBlank()) {
            HorizontalDivider(thickness = 0.5.dp, color = colors.line)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricCell(
                    label = "最近收到消息",
                    value = activity.lastIncomingAt?.let { formatTimeDividerLabel(it, today = today) } ?: "暂无",
                    modifier = Modifier.weight(1f),
                )
                MetricCell(
                    label = "最近云萤回复",
                    value = activity.lastAiReplyAt?.let { formatTimeDividerLabel(it, today = today) } ?: "暂无",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YunyingMoodSection(
    mood: ChatYunyingMoodDto,
    semantic: ChatSemanticColors,
    today: LocalDate,
) {
    val colors = IgngTheme.colors
    StatusSectionCard(
        title = "情绪与状态",
        icon = Icons.Rounded.Favorite,
        iconTint = semantic.relationshipFg,
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (mood.modeLabel.isNotBlank()) {
                StatusPill(
                    text = mood.modeLabel,
                    bg = semantic.relationshipBg,
                    fg = semantic.relationshipFg,
                )
            }
            StatusPill(
                text = if (mood.profile == "drive") "情感档案" else "日常心情",
                bg = colors.tileSoft,
                fg = colors.textSecondary,
            )
            if (mood.isCoolingDown) {
                val until = mood.coolingDownUntil?.let { " · 至 ${formatMessageClock(it)}" }.orEmpty()
                StatusPill(
                    text = "冷却中$until",
                    bg = colors.warningBg,
                    fg = colors.warning,
                )
            }
            if (mood.isAftercare) {
                StatusPill(
                    text = "安抚余韵",
                    bg = semantic.personaBg,
                    fg = semantic.personaFg,
                )
            }
        }

        if (mood.dominantSummary.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.canvas.copy(alpha = if (colors.isDark) 0.55f else 0.72f))
                    .border(0.5.dp, colors.line, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = mood.dominantSummary,
                    style = IgngTheme.type.body,
                    color = colors.textPrimary,
                )
            }
        }

        if (!mood.scene.isNullOrBlank()) {
            Text(
                text = "当前场景：${mood.scene}",
                style = IgngTheme.type.caption,
                color = colors.textSecondary,
            )
        }

        if (mood.detailed && mood.dimensions.isNotEmpty()) {
            HorizontalDivider(thickness = 0.5.dp, color = colors.line)
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                mood.dimensions.forEach { dim ->
                    val barColor = parseHexColorOrNull(dim.color) ?: colors.accent
                    val fraction = (dim.value.coerceIn(0, 100) / 100f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = dim.label.ifBlank { dim.key },
                            style = IgngTheme.type.caption.copy(fontWeight = FontWeight.Medium),
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.24f),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(IgngTheme.radii.pill)
                                .background(colors.line.copy(alpha = 0.65f)),
                        ) {
                            if (fraction > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .height(6.dp)
                                        .clip(IgngTheme.radii.pill)
                                        .background(barColor),
                                )
                            }
                        }
                        Text(
                            text = dim.value.coerceIn(0, 100).toString(),
                            style = IgngTheme.type.badge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum",
                            ),
                            color = colors.textPrimary,
                        )
                    }
                }
            }
        }

        if (mood.detailed && mood.recentEvents.isNotEmpty()) {
            HorizontalDivider(thickness = 0.5.dp, color = colors.line)
            Text(
                text = "近期情绪触动",
                style = IgngTheme.type.badge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textMuted,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                mood.recentEvents.take(8).forEach { ev ->
                    val (bg, fg) = when (ev.tone.lowercase()) {
                        "good" -> colors.successBg to colors.success
                        "bad" -> colors.dangerBg to colors.danger
                        else -> colors.tileSoft to colors.textSecondary
                    }
                    val suffix = ev.at?.let { " · ${formatTimeDividerLabel(it, today = today)}" }.orEmpty()
                    StatusPill(
                        text = "${ev.label.ifBlank { ev.name }}$suffix",
                        bg = bg,
                        fg = fg,
                    )
                }
            }
        }

        if (mood.detailed && mood.activeGrudges.isNotEmpty()) {
            HorizontalDivider(thickness = 0.5.dp, color = colors.line)
            Text(
                text = "还在介意的事",
                style = IgngTheme.type.badge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.warning,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                mood.activeGrudges.take(6).forEach { g ->
                    val days = g.remainingDays?.let {
                        if (it >= 1.0) " · 余 ${String.format(Locale.US, "%.1f", it)} 天" else " · 即将消散"
                    }.orEmpty()
                    StatusPill(
                        text = "${g.label.ifBlank { g.name }}$days",
                        bg = colors.warningBg,
                        fg = colors.warning,
                    )
                }
            }
        }

        if (!mood.detailed) {
            Text(
                text = "当前仅展示公开状态摘要；管理员或在本人私聊中可查看完整情绪维度。",
                style = IgngTheme.type.badge,
                color = colors.textMuted,
            )
        }
    }
}

@Composable
private fun StatusSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    content: @Composable () -> Unit,
) {
    val colors = IgngTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.tileSoft)
            .border(0.5.dp, colors.line, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = if (colors.isDark) 0.22f else 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(13.dp))
            }
            Text(
                text = title,
                style = IgngTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
            )
        }
        content()
    }
}

@Composable
private fun MetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = IgngTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = IgngTheme.type.badge,
            color = colors.textMuted,
            maxLines = 1,
        )
        Text(
            text = value,
            style = IgngTheme.type.caption.copy(
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum",
            ),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusPill(text: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = IgngTheme.type.badge.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun parseHexColorOrNull(raw: String?): Color? {
    val clean = raw?.trim()?.removePrefix("#").orEmpty()
    if (clean.length != 6 && clean.length != 8) return null
    val parsed = clean.toLongOrNull(16) ?: return null
    return if (clean.length == 6) {
        Color((0xFF000000L or parsed).toInt())
    } else {
        Color(parsed.toInt())
    }
}
