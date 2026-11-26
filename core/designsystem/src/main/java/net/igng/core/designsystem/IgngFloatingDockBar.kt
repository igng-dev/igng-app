package net.igng.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import net.igng.core.model.MotionIntensity

/**
 * Provides real bottom clearance (docked navigation bar height + system navigation bar inset)
 * to top-level screens so scrolled content never hides behind the bottom bar.
 */
val LocalIgngDockBottomPadding = compositionLocalOf { PaddingValues(bottom = 64.dp) }

@Composable
fun rememberIgngDockBottomPadding(dockVisible: Boolean = true): PaddingValues {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return if (dockVisible) {
        PaddingValues(bottom = navBottom + 62.dp)
    } else {
        PaddingValues(bottom = navBottom)
    }
}

/**
 * Animated visibility host for [IgngFloatingDockBar] (docked fixed-slot navigation bar).
 * Remembers the last active tab id while exiting so the active indicator never snaps back
 * to tab 0 when pushing a detail screen, and peeks smoothly upward during predictive-back
 * gestures returning to a top-level tab.
 */
@Composable
fun IgngDockHost(
    visible: Boolean,
    items: List<IgngNavTabItem>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val intensity = IgngTheme.motionIntensity
    val predictiveBack = LocalIgngPredictiveBackState.current

    val isPredictivePeeking = predictiveBack != null &&
        predictiveBack.willRevealDock &&
        predictiveBack.phase != IgngPredictiveBackPhase.Idle &&
        intensity != MotionIntensity.REDUCED

    val targetId = selectedId
        ?: predictiveBack?.targetTabId?.takeIf { isPredictivePeeking }
    var lastStableId by remember { mutableStateOf(targetId ?: items.firstOrNull()?.id.orEmpty()) }
    if (!targetId.isNullOrBlank()) {
        lastStableId = targetId
    }

    val dockVisibility = remember { Animatable(if (visible) 1f else 0f) }

    LaunchedEffect(isPredictivePeeking, visible, intensity, predictiveBack) {
        if (isPredictivePeeking && predictiveBack != null) {
            snapshotFlow { predictiveBack.dockRevealProgress }.collectLatest { peek ->
                dockVisibility.snapTo(peek.coerceIn(0f, 1f))
            }
        } else {
            val target = if (visible) 1f else 0f
            if (intensity == MotionIntensity.REDUCED) {
                dockVisibility.snapTo(target)
            } else {
                dockVisibility.animateTo(
                    targetValue = target,
                    animationSpec = IgngMotion.dockSpring(intensity),
                )
            }
        }
    }

    val progress = dockVisibility.value
    if (progress <= 0.005f) return

    Box(
        modifier = modifier.graphicsLayer {
            val clamped = progress.coerceIn(0f, 1f)
            alpha = if (intensity == MotionIntensity.REDUCED) {
                if (visible) 1f else 0f
            } else {
                (clamped * 1.25f).coerceIn(0f, 1f)
            }
            translationY = if (intensity == MotionIntensity.REDUCED) {
                0f
            } else {
                (1f - clamped) * 64.dp.toPx()
            }
        },
    ) {
        IgngFloatingDockBar(
            items = items,
            selectedId = lastStableId,
            onSelect = onSelect,
        )
    }
}

/**
 * Quiet Craft docked fixed-slot bottom navigation bar (Decision 1A).
 *
 * Ergonomic & aesthetic guarantees:
 * - Equal-width fixed slots: icon & label coordinates NEVER shift when switching tabs, preserving
 *   one-handed thumb muscle memory.
 * - Always-visible crisp labels (`10.5.sp`) + outlined/filled icon pairing for instant glanceability.
 * - Subtle critically-damped active pill glides cleanly behind the selected icon without moving text.
 * - Flush bottom dock with a single 0.5dp top hairline divider eliminates visual collision with scrolled lists.
 */
@Composable
fun IgngFloatingDockBar(
    items: List<IgngNavTabItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val colors = IgngTheme.colors
    val intensity = IgngTheme.motionIntensity
    val haptic = rememberIgngHaptics()
    val selectedIndex = items.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)

    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = IgngMotion.dockSpring(intensity),
        label = "dockIndicatorIndex",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.glassBar),
    ) {
        IgngHairlineDivider()
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(58.dp)
                .padding(horizontal = 8.dp)
                .drawBehind {
                    val slotWidth = size.width / items.size
                    val pillWidth = 50.dp.toPx().coerceAtMost(slotWidth - 12.dp.toPx())
                    val pillHeight = 28.dp.toPx()
                    val centerX = (animatedIndex + 0.5f) * slotWidth
                    val left = centerX - pillWidth / 2f
                    val top = 6.dp.toPx()
                    drawRoundRect(
                        color = colors.accentSoft,
                        topLeft = Offset(left, top),
                        size = Size(pillWidth, pillHeight),
                        cornerRadius = CornerRadius(pillHeight / 2f, pillHeight / 2f),
                    )
                },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { idx, item ->
                    val isSelected = idx == selectedIndex
                    val tint by animateColorAsState(
                        targetValue = if (isSelected) colors.accent else colors.textMuted,
                        animationSpec = IgngMotion.microTween(intensity),
                        label = "dockTint_${item.id}",
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember(item.id) { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab,
                                onClick = {
                                    if (!isSelected) {
                                        haptic(IgngHapticKind.Tick)
                                        onSelect(item.id)
                                    }
                                },
                            )
                            .padding(top = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Top),
                    ) {
                        Box(
                            modifier = Modifier.height(28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (isSelected) item.icon else item.unselectedIcon,
                                contentDescription = item.label,
                                tint = tint,
                                modifier = Modifier.size(21.dp),
                            )
                            if (item.badgeCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 5.dp, y = 2.dp)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(colors.danger),
                                )
                            }
                        }
                        Text(
                            text = item.label,
                            style = IgngTheme.type.badge.copy(
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            ),
                            color = tint,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Semantic alias for [IgngFloatingDockBar] emphasizing the docked fixed-slot layout.
 */
@Composable
fun IgngDockedNavBar(
    items: List<IgngNavTabItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) = IgngFloatingDockBar(
    items = items,
    selectedId = selectedId,
    onSelect = onSelect,
    modifier = modifier,
)
