package net.igng.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import net.igng.core.model.MotionIntensity

/**
 * Provides real bottom clearance (dock height + system navigation bar inset) to top-level screens.
 */
val LocalIgngDockBottomPadding = compositionLocalOf { PaddingValues(bottom = 88.dp) }

@Composable
fun rememberIgngDockBottomPadding(dockVisible: Boolean = true): PaddingValues {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return if (dockVisible) {
        PaddingValues(bottom = navBottom + 84.dp)
    } else {
        PaddingValues(bottom = navBottom)
    }
}

/**
 * Animated visibility host for [IgngFloatingDockBar].
 * Remembers the last active tab id while exiting so the active capsule never snaps back
 * to tab 0 when pushing a detail screen.
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
    var lastStableId by remember { mutableStateOf(selectedId ?: items.firstOrNull()?.id.orEmpty()) }
    if (!selectedId.isNullOrBlank()) {
        lastStableId = selectedId
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(IgngMotion.dockSpring(intensity)) { (it * 0.85f).roundToInt() } +
            fadeIn(IgngMotion.microTween(intensity)),
        exit = slideOutVertically(IgngMotion.dockSpring(intensity)) { (it * 0.85f).roundToInt() } +
            fadeOut(IgngMotion.microTween(intensity)),
        modifier = modifier,
    ) {
        IgngFloatingDockBar(
            items = items,
            selectedId = lastStableId,
            onSelect = onSelect,
        )
    }
}

/**
 * Unified IGNG floating compact expanding-pill Dock (BBS aesthetic + zero-jitter custom Layout
 * + continuous gliding active capsule).
 *
 * Frame-locked zero-jitter invariants:
 * - Every tab's expansion progress uses the same critically-damped spring (`dampingRatio = 1.0f`),
 *   so when switching between equal-length tabs, `sum(width_i(t))` is mathematically invariant
 *   frame-by-frame (0px outer pill jump).
 * - Padding, icon-to-label gap, and label slot width are linearly coupled to the same progress `t`.
 * - Labels are measured at full natural width and GPU-clipped to `[0, width * t]` so text never
 *   wraps, never bleeds into neighbors, and never double-snaps via `AnimatedVisibility`.
 * - The gliding active capsule is computed directly as the progress-weighted interpolation of
 *   each slot's `[left, right]` bounds on the exact same frame — zero extra recomposition pass.
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
    val density = LocalDensity.current
    val selectedIndex = items.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)

    val dockSpringFloat = remember(intensity) { IgngMotion.dockSpring<Float>(intensity) }
    val itemProgress = items.mapIndexed { idx, item ->
        val progress by animateFloatAsState(
            targetValue = if (idx == selectedIndex) 1f else 0f,
            animationSpec = dockSpringFloat,
            label = "dockTabExpand_${item.id}",
        )
        progress
    }

    // Non-state float holder updated synchronously during layout and read on the same frame's draw
    val capsuleBounds = remember { FloatArray(2) }

    val itemSpacingPx = with(density) { 4.dp.roundToPx() }
    val slotHeightPx = with(density) { 44.dp.roundToPx() }
    val collapsedPadPx = with(density) { 13.dp.roundToPx() }
    val expandedExtraPadPx = with(density) { 4.dp.roundToPx() }
    val iconLabelGapPx = with(density) { 6.dp.roundToPx() }
    val capsuleColor = colors.accentSoft

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = IgngTheme.radii.pill,
            color = colors.glassBar,
            border = BorderStroke(1.dp, colors.lineStrong),
            shadowElevation = if (colors.isDark) 8.dp else 10.dp,
        ) {
            Layout(
                modifier = Modifier
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .drawBehind {
                        val left = capsuleBounds[0]
                        val right = capsuleBounds[1]
                        val width = (right - left).coerceAtLeast(0f)
                        if (width > 1f) {
                            val radius = size.height / 2f
                            drawRoundRect(
                                color = capsuleColor,
                                topLeft = Offset(left, 0f),
                                size = Size(width, size.height),
                                cornerRadius = CornerRadius(radius, radius),
                            )
                        }
                    },
                content = {
                    items.forEachIndexed { idx, item ->
                        val isSelected = idx == selectedIndex
                        val progress = itemProgress[idx]
                        val interactionSource = remember(item.id) { MutableInteractionSource() }
                        val pressed by interactionSource.collectIsPressedAsState()
                        val pressScale by animateFloatAsState(
                            targetValue = if (pressed && intensity != MotionIntensity.REDUCED) 0.94f else 1f,
                            animationSpec = IgngMotion.quickSpring(intensity),
                            label = "dockPress_${item.id}",
                        )
                        val tint by animateColorAsState(
                            targetValue = if (isSelected) colors.accent else colors.textSecondary,
                            animationSpec = IgngMotion.microTween(intensity),
                            label = "dockTint_${item.id}",
                        )

                        // Child 0: interactive touch surface covering the entire slot
                        Box(
                            modifier = Modifier
                                .clip(IgngTheme.radii.pill)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    role = Role.Tab,
                                    onClick = {
                                        if (!isSelected) {
                                            haptic(IgngHapticKind.Tick)
                                            onSelect(item.id)
                                        }
                                    },
                                ),
                        )

                        // Child 1: icon + optional unread badge dot
                        Box(
                            modifier = Modifier.graphicsLayer {
                                scaleX = pressScale
                                scaleY = pressScale
                            },
                            contentAlignment = Alignment.TopEnd,
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = tint,
                                modifier = Modifier.size(20.dp),
                            )
                            if (item.badgeCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = 4.dp, y = (-2).dp)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(colors.danger),
                                )
                            }
                        }

                        // Child 2: single-line natural-width label, GPU-clipped to exact visible width
                        Box(
                            modifier = Modifier.graphicsLayer {
                                val clamped = progress.coerceIn(0f, 1f)
                                scaleX = pressScale
                                scaleY = pressScale
                                alpha = ((clamped - 0.18f) / 0.82f).coerceIn(0f, 1f)
                                translationX = (1f - clamped) * (-4.dp.toPx())
                                clip = true
                                shape = object : Shape {
                                    override fun createOutline(
                                        size: Size,
                                        layoutDirection: LayoutDirection,
                                        density: Density,
                                    ): Outline = Outline.Rectangle(
                                        Rect(0f, 0f, size.width * clamped, size.height),
                                    )
                                }
                            },
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = item.label,
                                style = IgngTheme.type.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = tint,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                },
            ) { measurables, constraints ->
                val count = items.size
                val loose = Constraints(
                    minWidth = 0,
                    maxWidth = Constraints.Infinity,
                    minHeight = 0,
                    maxHeight = slotHeightPx,
                )

                val iconPlaceables = Array(count) { i -> measurables[i * 3 + 1].measure(loose) }
                val labelPlaceables = Array(count) { i -> measurables[i * 3 + 2].measure(loose) }

                val collapsedWidths = IntArray(count) { i -> iconPlaceables[i].width + collapsedPadPx * 2 }
                val expandedExtras = IntArray(count) { i ->
                    expandedExtraPadPx * 2 + iconLabelGapPx + labelPlaceables[i].width
                }

                val rawSlotWidths = IntArray(count) { i ->
                    collapsedWidths[i] + (expandedExtras[i] * itemProgress[i]).roundToInt()
                }
                val rawTotal = rawSlotWidths.sum() + itemSpacingPx * (count - 1).coerceAtLeast(0)
                val maxAllowedWidth = constraints.maxWidth.coerceAtLeast(1)
                val frameScale = if (rawTotal > maxAllowedWidth) maxAllowedWidth.toFloat() / rawTotal else 1f
                val slotWidths = IntArray(count) { i -> (rawSlotWidths[i] * frameScale).roundToInt().coerceAtLeast(1) }
                val spacing = (itemSpacingPx * frameScale).roundToInt()
                val totalWidth = (slotWidths.sum() + spacing * (count - 1).coerceAtLeast(0))
                    .coerceIn(constraints.minWidth, maxAllowedWidth)

                // Compute progress-weighted gliding capsule [left, right] in the exact same frame
                var weightSum = 0f
                var weightedLeft = 0f
                var weightedRight = 0f
                var cursorX = 0f
                for (i in 0 until count) {
                    val w = slotWidths[i].toFloat()
                    val p = itemProgress[i].coerceIn(0f, 1f)
                    if (p > 0.0001f) {
                        weightSum += p
                        weightedLeft += cursorX * p
                        weightedRight += (cursorX + w) * p
                    }
                    cursorX += w + spacing
                }
                if (weightSum > 0.0001f) {
                    capsuleBounds[0] = weightedLeft / weightSum
                    capsuleBounds[1] = weightedRight / weightSum
                } else {
                    capsuleBounds[0] = 0f
                    capsuleBounds[1] = slotWidths[0].toFloat()
                }

                val touchPlaceables = Array(count) { i ->
                    measurables[i * 3].measure(Constraints.fixed(slotWidths[i], slotHeightPx))
                }

                layout(totalWidth, slotHeightPx) {
                    var x = 0
                    for (i in 0 until count) {
                        val slotW = slotWidths[i]
                        val p = itemProgress[i].coerceIn(0f, 1f)
                        val icon = iconPlaceables[i]
                        val label = labelPlaceables[i]

                        touchPlaceables[i].placeRelative(x, 0)

                        val gap = (iconLabelGapPx * p).roundToInt()
                        val visibleLabelW = (label.width * p).roundToInt()
                        val contentW = icon.width + gap + visibleLabelW
                        val startX = x + ((slotW - contentW) / 2f).roundToInt()

                        icon.placeRelative(
                            x = startX,
                            y = (slotHeightPx - icon.height) / 2,
                        )
                        if (p > 0.02f) {
                            label.placeRelative(
                                x = startX + icon.width + gap,
                                y = (slotHeightPx - label.height) / 2,
                            )
                        }

                        x += slotW + spacing
                    }
                }
            }
        }
    }
}
