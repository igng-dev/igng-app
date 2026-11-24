package net.igng.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Immutable
internal data class IgngVisibleItemSnapshot(
    val index: Int,
    val offset: Int,
    val size: Int,
)

@Immutable
internal data class IgngScrollMetrics(
    val scrollable: Boolean,
    val progress: Float,
    val thumbFraction: Float,
)

/**
 * Computes continuous scroll progress (`0f..1f`) and proportional thumb fraction for a
 * variable-height [LazyListState], clamping accurately at top and bottom bounds.
 */
internal fun computeLazyListScrollMetrics(
    totalItemsCount: Int,
    viewportStartOffset: Int,
    viewportEndOffset: Int,
    visibleItems: List<IgngVisibleItemSnapshot>,
): IgngScrollMetrics {
    if (totalItemsCount <= 0 || visibleItems.isEmpty()) {
        return IgngScrollMetrics(scrollable = false, progress = 0f, thumbFraction = 1f)
    }

    val viewportHeight = (viewportEndOffset - viewportStartOffset).coerceAtLeast(1)
    val first = visibleItems.first()
    val last = visibleItems.last()
    val atTop = first.index == 0 && first.offset >= viewportStartOffset
    val atBottom = last.index == totalItemsCount - 1 && (last.offset + last.size) <= viewportEndOffset

    if (visibleItems.size == totalItemsCount) {
        val contentSpan = (last.offset + last.size) - first.offset
        if (contentSpan <= viewportHeight) {
            return IgngScrollMetrics(scrollable = false, progress = 1f, thumbFraction = 1f)
        }
        val scrolledPx = (viewportStartOffset - first.offset).coerceAtLeast(0)
        val maxScrollPx = (contentSpan - viewportHeight).coerceAtLeast(1)
        val progress = when {
            atTop -> 0f
            atBottom -> 1f
            else -> (scrolledPx.toFloat() / maxScrollPx.toFloat()).coerceIn(0f, 1f)
        }
        val thumbFraction = (viewportHeight.toFloat() / contentSpan.toFloat()).coerceIn(0.08f, 0.85f)
        return IgngScrollMetrics(scrollable = true, progress = progress, thumbFraction = thumbFraction)
    }

    val avgItemSize = (visibleItems.sumOf { it.size.coerceAtLeast(1) }.toFloat() / visibleItems.size).coerceAtLeast(1f)
    val firstHiddenFraction = ((viewportStartOffset - first.offset).coerceAtLeast(0).toFloat() / first.size.coerceAtLeast(1))
        .coerceIn(0f, 1f)
    val topItemPos = first.index.toFloat() + firstHiddenFraction
    val visibleSpanItems = viewportHeight.toFloat() / avgItemSize
    val maxTopPos = (totalItemsCount.toFloat() - visibleSpanItems).coerceAtLeast(0.5f)

    val progress = when {
        atTop -> 0f
        atBottom -> 1f
        else -> (topItemPos / maxTopPos).coerceIn(0f, 1f)
    }
    val thumbFraction = (visibleSpanItems / totalItemsCount.toFloat()).coerceIn(0.08f, 0.85f)
    return IgngScrollMetrics(scrollable = true, progress = progress, thumbFraction = thumbFraction)
}

@Composable
internal fun rememberLazyListScrollMetrics(listState: LazyListState): State<IgngScrollMetrics> {
    return remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            computeLazyListScrollMetrics(
                totalItemsCount = info.totalItemsCount,
                viewportStartOffset = info.viewportStartOffset,
                viewportEndOffset = info.viewportEndOffset,
                visibleItems = info.visibleItemsInfo.map {
                    IgngVisibleItemSnapshot(index = it.index, offset = it.offset, size = it.size)
                },
            )
        }
    }
}

/**
 * Smooth continuous reading progress (`0f..1f`) backed by [computeLazyListScrollMetrics].
 */
@Composable
fun rememberIgngReadingProgress(listState: LazyListState): State<Float> {
    val metrics by rememberLazyListScrollMetrics(listState)
    return remember(listState) {
        derivedStateOf { metrics.progress }
    }
}

/**
 * Right-edge vertical scrollbar indicator for long-form reading surfaces.
 * - Visualizes continuous reading progress with proportional thumb length.
 * - Remains subtly glanceable at rest and brightens smoothly while scrolling or scrubbing.
 * - Supports vertical drag-to-scrub without intercepting horizontal predictive-back gestures.
 * - Automatically hides when content fits within the viewport.
 */
@Composable
fun IgngLazyColumnScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    thumbColor: Color = IgngTheme.colors.textMuted,
    activeThumbColor: Color = IgngTheme.colors.accent,
    topPadding: Dp = 8.dp,
    bottomPadding: Dp = 8.dp,
    minThumbHeight: Dp = 36.dp,
    restingAlpha: Float = 0.28f,
) {
    val intensity = IgngTheme.motionIntensity
    val haptic = rememberIgngHaptics()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val metrics by rememberLazyListScrollMetrics(listState)
    if (!metrics.scrollable) return

    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(metrics.progress) }
    var activeHighlight by remember { mutableStateOf(false) }
    var trackHeightPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(listState.isScrollInProgress, dragging) {
        if (listState.isScrollInProgress || dragging) {
            activeHighlight = true
        } else {
            delay(1300L)
            activeHighlight = false
        }
    }

    val displayedProgress = if (dragging) dragFraction else metrics.progress
    val thumbAlpha by animateFloatAsState(
        targetValue = when {
            dragging -> 0.95f
            activeHighlight -> 0.80f
            else -> restingAlpha.coerceIn(0f, 1f)
        },
        animationSpec = IgngMotion.normalTween(intensity),
        label = "scrollbarAlpha",
    )
    val thumbWidth by animateDpAsState(
        targetValue = if (dragging) 5.5.dp else 3.5.dp,
        animationSpec = IgngMotion.microTween(intensity),
        label = "scrollbarWidth",
    )
    val resolvedColor by animateColorAsState(
        targetValue = if (dragging || activeHighlight) activeThumbColor else thumbColor,
        animationSpec = IgngMotion.microTween(intensity),
        label = "scrollbarColor",
    )

    val minThumbPx = with(density) { minThumbHeight.toPx() }
    val endInsetPx = with(density) { 3.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(top = topPadding, bottom = bottomPadding)
            .width(18.dp)
            .onSizeChanged { trackHeightPx = it.height.toFloat() }
            .pointerInput(listState) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        val h = trackHeightPx
                        if (h <= 1f) return@detectVerticalDragGestures
                        dragging = true
                        haptic(IgngHapticKind.Tick)
                        val thumbH = (h * metrics.thumbFraction).coerceIn(minThumbPx, (h * 0.72f).coerceAtLeast(minThumbPx))
                        val travel = (h - thumbH).coerceAtLeast(1f)
                        val nextFrac = ((offset.y - thumbH * 0.5f) / travel).coerceIn(0f, 1f)
                        dragFraction = nextFrac
                        val total = listState.layoutInfo.totalItemsCount
                        if (total > 1) {
                            val targetIdx = (nextFrac * (total - 1)).roundToInt().coerceIn(0, total - 1)
                            scope.launch { listState.scrollToItem(targetIdx) }
                        }
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        val h = trackHeightPx
                        if (h <= 1f) return@detectVerticalDragGestures
                        val thumbH = (h * metrics.thumbFraction).coerceIn(minThumbPx, (h * 0.72f).coerceAtLeast(minThumbPx))
                        val travel = (h - thumbH).coerceAtLeast(1f)
                        val nextFrac = ((change.position.y - thumbH * 0.5f) / travel).coerceIn(0f, 1f)
                        dragFraction = nextFrac
                        val total = listState.layoutInfo.totalItemsCount
                        if (total > 1) {
                            val targetIdx = (nextFrac * (total - 1)).roundToInt().coerceIn(0, total - 1)
                            scope.launch { listState.scrollToItem(targetIdx) }
                        }
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val h = size.height
            if (h <= 1f || thumbAlpha <= 0.01f) return@Canvas
            val wPx = thumbWidth.toPx()
            val maxThumbH = (h * 0.72f).coerceAtLeast(minThumbPx.coerceAtMost(h))
            val thumbH = (h * metrics.thumbFraction).coerceIn(minThumbPx.coerceAtMost(maxThumbH), maxThumbH)
            val travel = (h - thumbH).coerceAtLeast(0f)
            val topY = travel * displayedProgress.coerceIn(0f, 1f)
            val leftX = (size.width - wPx - endInsetPx).coerceAtLeast(0f)
            val radius = CornerRadius(wPx * 0.5f, wPx * 0.5f)

            // Subtle track while actively scrolling or scrubbing
            if (dragging || activeHighlight) {
                drawRoundRect(
                    color = thumbColor.copy(alpha = 0.14f * thumbAlpha),
                    topLeft = Offset(leftX, 0f),
                    size = Size(wPx, h),
                    cornerRadius = radius,
                )
            }

            drawRoundRect(
                color = resolvedColor.copy(alpha = thumbAlpha),
                topLeft = Offset(leftX, topY),
                size = Size(wPx, thumbH),
                cornerRadius = radius,
            )
        }
    }
}
