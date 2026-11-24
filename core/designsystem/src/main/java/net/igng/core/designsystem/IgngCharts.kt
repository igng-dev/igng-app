package net.igng.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class IgngChartPoint(
    val label: String,
    val value: Double,
    val detail: String = "",
)

/**
 * Resolves an adaptive, crisp stroke width (in dp) based on sample density so dense telemetry
 * series (e.g. 150–300 points) stay razor-clear while sparse charts keep comfortable visual weight.
 */
fun resolveChartStrokeWidthDp(pointCount: Int, overrideDp: Float? = null): Float {
    if (overrideDp != null && overrideDp > 0f) return overrideDp
    return when {
        pointCount > 80 -> 1.2f
        pointCount > 32 -> 1.4f
        else -> 1.65f
    }
}

/**
 * Interactive line chart with density-adaptive fine strokes, optional Y/X axes, reference baseline,
 * and tap-to-lock / drag-to-scrub inspection.
 */
@Composable
fun IgngLineChart(
    points: List<IgngChartPoint>,
    lineColor: Color,
    valueFormatter: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    minY: Double? = null,
    maxY: Double? = null,
    strokeWidth: Dp? = null,
    smooth: Boolean? = null,
    referenceValue: Double? = null,
    referenceLabel: String? = null,
    axisValueFormatter: ((Double) -> String)? = null,
    showAxes: Boolean = true,
) {
    val colors = IgngTheme.colors
    val intensity = IgngTheme.motionIntensity
    val haptic = rememberIgngHaptics()
    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }

    val drawProgress = remember(points) { Animatable(if (intensity == net.igng.core.model.MotionIntensity.REDUCED) 1f else 0f) }
    LaunchedEffect(points, intensity) {
        if (intensity != net.igng.core.model.MotionIntensity.REDUCED) {
            drawProgress.snapTo(0f)
            drawProgress.animateTo(1f, IgngMotion.easeTween(340, intensity))
        } else {
            drawProgress.snapTo(1f)
        }
    }

    if (points.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .clip(IgngTheme.radii.md)
                .background(colors.tileSoft)
                .border(0.5.dp, colors.line, IgngTheme.radii.md),
            contentAlignment = Alignment.Center,
        ) {
            Text("暂无采样点", style = IgngTheme.type.caption, color = colors.textMuted)
        }
        return
    }

    val rawMin = minY ?: points.minOf { it.value }
    val rawMax = maxY ?: points.maxOf { it.value }
    val paddedMax = if (rawMax <= rawMin) rawMin + 1.0 else rawMax
    val paddedMin = if (minY != null) rawMin else (rawMin - (paddedMax - rawMin) * 0.1).coerceAtLeast(0.0)
    val span = (paddedMax - paddedMin).coerceAtLeast(0.001)
    val midY = paddedMin + span * 0.5
    val formatAxis: (Double) -> String = axisValueFormatter ?: { v ->
        if (abs(v - v.roundToInt()) < 0.05) {
            v.roundToInt().toString()
        } else {
            String.format(Locale.US, "%.1f", v)
        }
    }

    val resolvedStrokeDp = resolveChartStrokeWidthDp(points.size, strokeWidth?.value).dp
    val useSmooth = smooth ?: (points.size <= 32)
    val active = selectedIndex?.let { points.getOrNull(it) } ?: points.last()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = valueFormatter(active.value),
                    style = IgngTheme.type.metricMedium,
                    color = lineColor,
                )
                if (selectedIndex != null) {
                    Text(
                        text = "复位",
                        style = IgngTheme.type.badge.copy(fontSize = 10.sp),
                        color = colors.textSecondary,
                        modifier = Modifier
                            .clip(IgngTheme.radii.pill)
                            .background(colors.tileSoft)
                            .border(0.5.dp, colors.line, IgngTheme.radii.pill)
                            .clickable { selectedIndex = null }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = if (active.detail.isNotBlank()) "${active.label} · ${active.detail}" else active.label,
                style = IgngTheme.type.caption,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(IgngTheme.radii.md)
                .background(colors.tileSoft)
                .border(0.5.dp, colors.line, IgngTheme.radii.md),
        ) {
            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                if (showAxes) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End,
                    ) {
                        val axisStyle = IgngTheme.type.badge.copy(
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(formatAxis(paddedMax), style = axisStyle, color = colors.textMuted, maxLines = 1)
                        Text(formatAxis(midY), style = axisStyle, color = colors.textMuted, maxLines = 1)
                        Text(formatAxis(paddedMin), style = axisStyle, color = colors.textMuted, maxLines = 1)
                    }
                }

                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clipToBounds()
                        .pointerInput(points) {
                            detectTapGestures { offset ->
                                if (points.size > 1) {
                                    val padX = 8.dp.toPx()
                                    val usable = (size.width - padX * 2f).coerceAtLeast(1f)
                                    val idx = (((offset.x - padX) / usable) * (points.size - 1)).roundToInt()
                                        .coerceIn(0, points.lastIndex)
                                    selectedIndex = if (selectedIndex == idx) null else idx
                                    haptic(IgngHapticKind.Tick)
                                }
                            }
                        }
                        .pointerInput(points) {
                            detectDragGestures { change, _ ->
                                if (points.size > 1) {
                                    val padX = 8.dp.toPx()
                                    val usable = (size.width - padX * 2f).coerceAtLeast(1f)
                                    val idx = (((change.position.x - padX) / usable) * (points.size - 1)).roundToInt()
                                        .coerceIn(0, points.lastIndex)
                                    if (selectedIndex != idx) {
                                        selectedIndex = idx
                                        haptic(IgngHapticKind.Tick)
                                    }
                                }
                            }
                        },
                ) {
                    val padX = 8.dp.toPx()
                    val padY = 12.dp.toPx()
                    val w = (size.width - padX * 2f).coerceAtLeast(1f)
                    val h = (size.height - padY * 2f).coerceAtLeast(1f)

                    // Subtle horizontal gridlines
                    val gridDash = PathEffect.dashPathEffect(floatArrayOf(5f, 6f))
                    repeat(3) { step ->
                        val y = padY + h * (step / 2f)
                        drawLine(
                            color = colors.line.copy(alpha = 0.72f),
                            start = Offset(padX, y),
                            end = Offset(size.width - padX, y),
                            strokeWidth = 0.8.dp.toPx(),
                            pathEffect = gridDash,
                        )
                    }

                    // Optional reference baseline (e.g., TPS 20.0 / avg latency)
                    if (referenceValue != null && referenceValue in paddedMin..paddedMax) {
                        val refNorm = ((referenceValue - paddedMin) / span).toFloat().coerceIn(0f, 1f)
                        val refY = padY + h * (1f - refNorm)
                        drawLine(
                            color = lineColor.copy(alpha = 0.36f),
                            start = Offset(padX, refY),
                            end = Offset(size.width - padX, refY),
                            strokeWidth = 0.9.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f)),
                        )
                    }

                    val coords = points.mapIndexed { idx, pt ->
                        val x = if (points.size == 1) padX + w / 2f else padX + w * (idx.toFloat() / (points.size - 1))
                        val norm = ((pt.value - paddedMin) / span).toFloat().coerceIn(0f, 1f)
                        val animatedNorm = norm * drawProgress.value
                        val y = padY + h * (1f - animatedNorm)
                        Offset(x, y)
                    }

                    val linePath = Path()
                    val fillPath = Path()
                    if (coords.isNotEmpty()) {
                        val first = coords.first()
                        linePath.moveTo(first.x, first.y)
                        fillPath.moveTo(first.x, padY + h)
                        fillPath.lineTo(first.x, first.y)

                        if (useSmooth && coords.size >= 3) {
                            // Fritsch–Carlson monotone cubic spline (zero overshoot)
                            val n = coords.size
                            val dx = FloatArray(n - 1) { i -> (coords[i + 1].x - coords[i].x).coerceAtLeast(0.001f) }
                            val slopes = FloatArray(n - 1) { i -> (coords[i + 1].y - coords[i].y) / dx[i] }
                            val tangents = FloatArray(n)
                            tangents[0] = slopes[0]
                            tangents[n - 1] = slopes[n - 2]
                            for (i in 1 until n - 1) {
                                tangents[i] = if (slopes[i - 1] * slopes[i] <= 0f) {
                                    0f
                                } else {
                                    (slopes[i - 1] + slopes[i]) * 0.5f
                                }
                            }
                            for (i in 0 until n - 1) {
                                if (abs(slopes[i]) < 1e-6f) {
                                    tangents[i] = 0f
                                    tangents[i + 1] = 0f
                                } else {
                                    val a = tangents[i] / slopes[i]
                                    val b = tangents[i + 1] / slopes[i]
                                    val mag = a * a + b * b
                                    if (mag > 9f) {
                                        val tau = 3f / kotlin.math.sqrt(mag)
                                        tangents[i] = tau * a * slopes[i]
                                        tangents[i + 1] = tau * b * slopes[i]
                                    }
                                }
                            }
                            for (i in 0 until n - 1) {
                                val p0 = coords[i]
                                val p1 = coords[i + 1]
                                val hSeg = dx[i] / 3f
                                val c1x = p0.x + hSeg
                                val c1y = (p0.y + tangents[i] * hSeg).coerceIn(padY, padY + h)
                                val c2x = p1.x - hSeg
                                val c2y = (p1.y - tangents[i + 1] * hSeg).coerceIn(padY, padY + h)
                                linePath.cubicTo(c1x, c1y, c2x, c2y, p1.x, p1.y)
                                fillPath.cubicTo(c1x, c1y, c2x, c2y, p1.x, p1.y)
                            }
                        } else {
                            // Crisp straight-segment polyline for dense telemetry series
                            for (i in 1 until coords.size) {
                                val pt = coords[i]
                                linePath.lineTo(pt.x, pt.y)
                                fillPath.lineTo(pt.x, pt.y)
                            }
                        }

                        val last = coords.last()
                        fillPath.lineTo(last.x, padY + h)
                        fillPath.close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(lineColor.copy(alpha = 0.13f), Color.Transparent),
                            startY = padY,
                            endY = padY + h,
                        ),
                    )
                    drawPath(
                        path = linePath,
                        color = lineColor,
                        style = Stroke(
                            width = resolvedStrokeDp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )

                    val focusIndex = selectedIndex
                    if (focusIndex != null) {
                        coords.getOrNull(focusIndex)?.let { pt ->
                            drawLine(
                                color = lineColor.copy(alpha = 0.5f),
                                start = Offset(pt.x, padY),
                                end = Offset(pt.x, padY + h),
                                strokeWidth = 0.9.dp.toPx(),
                                pathEffect = gridDash,
                            )
                            drawCircle(color = colors.tile, radius = 4.2.dp.toPx(), center = pt)
                            drawCircle(color = lineColor, radius = 2.6.dp.toPx(), center = pt)
                        }
                    } else {
                        coords.lastOrNull()?.let { pt ->
                            drawCircle(color = colors.tile, radius = 3.dp.toPx(), center = pt)
                            drawCircle(color = lineColor, radius = 1.8.dp.toPx(), center = pt)
                        }
                    }
                }
            }

            if (!referenceLabel.isNullOrBlank() && referenceValue != null && referenceValue in paddedMin..paddedMax) {
                Text(
                    text = referenceLabel,
                    style = IgngTheme.type.badge.copy(fontSize = 9.sp, fontFamily = FontFamily.Monospace),
                    color = lineColor.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 8.dp),
                )
            }
        }

        if (showAxes && points.size >= 2) {
            val axisTimeStyle = IgngTheme.type.badge.copy(
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(points.first().label, style = axisTimeStyle, color = colors.textMuted, maxLines = 1)
                if (points.size >= 5) {
                    Text(points[points.size / 2].label, style = axisTimeStyle, color = colors.textMuted, maxLines = 1)
                }
                Text(points.last().label, style = axisTimeStyle, color = colors.textMuted, maxLines = 1)
            }
        }
    }
}

enum class HealthBucketLevel {
    Healthy,
    Degraded,
    Down,
    NoData,
}

data class HealthBucketItem(
    val level: HealthBucketLevel,
    val title: String,
    val detail: String,
)

private fun HealthBucketLevel.severityRank(): Int = when (this) {
    HealthBucketLevel.Down -> 3
    HealthBucketLevel.Degraded -> 2
    HealthBucketLevel.Healthy -> 1
    HealthBucketLevel.NoData -> 0
}

/**
 * Downsamples a high-frequency status bucket stream (e.g. 144 buckets) into [targetCount] compact
 * visual blocks using worst-severity-wins semantics so no outage or degradation blip is lost.
 */
fun aggregateHealthBuckets(
    buckets: List<HealthBucketItem>,
    targetCount: Int,
): List<HealthBucketItem> {
    if (buckets.isEmpty() || targetCount <= 0 || buckets.size <= targetCount) return buckets
    val n = buckets.size
    return List(targetCount) { i ->
        val start = (i * n) / targetCount
        val end = (((i + 1) * n) / targetCount).coerceAtLeast(start + 1).coerceAtMost(n)
        val slice = buckets.subList(start, end)
        val worst = slice.maxBy { it.level.severityRank() }
        val firstTitle = slice.first().title
        val lastTitle = slice.last().title
        val mergedTitle = if (slice.size == 1 || firstTitle == lastTitle) {
            firstTitle
        } else {
            val tail = lastTitle.substringAfter(' ', lastTitle)
            "$firstTitle – $tail"
        }
        val issueCount = slice.count { it.level == HealthBucketLevel.Down || it.level == HealthBucketLevel.Degraded }
        val mergedDetail = if (slice.size > 1 && issueCount > 0 && worst.level != HealthBucketLevel.Healthy) {
            "${worst.detail}（$issueCount/${slice.size} 异常）"
        } else {
            worst.detail
        }
        HealthBucketItem(
            level = worst.level,
            title = mergedTitle,
            detail = mergedDetail,
        )
    }
}

/**
 * Computes availability percentage across known (non-NoData) buckets.
 */
fun computeHealthAvailabilityPercent(buckets: List<HealthBucketItem>): Double? {
    val known = buckets.filter { it.level != HealthBucketLevel.NoData }
    if (known.isEmpty()) return null
    val healthy = known.count { it.level == HealthBucketLevel.Healthy }
    return (healthy.toDouble() / known.size.toDouble()) * 100.0
}

/**
 * Adaptive, strictly-bounded Bento uptime health strip that aggregates raw buckets to fit the
 * card width without ever overflowing or collapsing into hairline slits.
 */
@Composable
fun IgngStatusHealthStrip(
    buckets: List<HealthBucketItem>,
    modifier: Modifier = Modifier,
    startLabel: String? = null,
    endLabel: String? = "现在",
    windowLabel: String? = null,
    minBarWidth: Dp = 4.5.dp,
    barGap: Dp = 2.dp,
    barHeight: Dp = 18.dp,
) {
    val colors = IgngTheme.colors
    val haptic = rememberIgngHaptics()

    if (buckets.isEmpty()) return

    val availability = remember(buckets) { computeHealthAvailabilityPercent(buckets) }
    val availabilityColor = when {
        availability == null -> colors.textMuted
        availability >= 99.0 -> colors.success
        availability >= 95.0 -> colors.warning
        else -> colors.danger
    }
    val availabilityText = remember(availability, windowLabel) {
        availability?.let { pct ->
            val pctStr = if (pct >= 99.95) "100%" else String.format(Locale.US, "%.1f%%", pct)
            if (windowLabel.isNullOrBlank()) "可用率 $pctStr" else "$windowLabel 可用率 $pctStr"
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val innerWidthDp = (maxWidth - 16.dp).coerceAtLeast(48.dp)
        val targetBars = remember(innerWidthDp, minBarWidth, barGap) {
            ((innerWidthDp.value + barGap.value) / (minBarWidth.value + barGap.value))
                .toInt()
                .coerceIn(12, 42)
        }
        val displayBuckets = remember(buckets, targetBars) {
            aggregateHealthBuckets(buckets, targetBars)
        }
        var inspected by remember(displayBuckets) { mutableStateOf<Int?>(null) }

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(IgngTheme.radii.sm)
                    .background(colors.tileSoft)
                    .border(0.6.dp, colors.line, IgngTheme.radii.sm)
                    .padding(horizontal = 8.dp, vertical = 7.dp),
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .clip(RoundedCornerShape(4.dp))
                        .pointerInput(displayBuckets) {
                            detectTapGestures { offset ->
                                val idx = ((offset.x / size.width.coerceAtLeast(1)) * displayBuckets.size)
                                    .toInt()
                                    .coerceIn(0, displayBuckets.lastIndex)
                                inspected = if (inspected == idx) null else idx
                                haptic(IgngHapticKind.Tick)
                            }
                        }
                        .pointerInput(displayBuckets) {
                            detectDragGestures(
                                onDragEnd = { inspected = null },
                                onDragCancel = { inspected = null },
                            ) { change, _ ->
                                val idx = ((change.position.x / size.width.coerceAtLeast(1)) * displayBuckets.size)
                                    .toInt()
                                    .coerceIn(0, displayBuckets.lastIndex)
                                if (inspected != idx) {
                                    inspected = idx
                                    haptic(IgngHapticKind.Tick)
                                }
                            }
                        },
                ) {
                    val count = displayBuckets.size
                    if (count <= 0) return@Canvas
                    val maxGap = if (count > 1) (size.width * 0.35f) / (count - 1) else 0f
                    val gapPx = if (count > 1) barGap.toPx().coerceAtMost(maxGap) else 0f
                    val barWidth = ((size.width - gapPx * (count - 1)) / count).coerceAtLeast(1f)
                    val radiusPx = (barWidth * 0.42f).coerceIn(1.5.dp.toPx(), 3.dp.toPx())

                    displayBuckets.forEachIndexed { idx, bucket ->
                        val x = idx * (barWidth + gapPx)
                        val active = inspected == idx
                        val barColor = when (bucket.level) {
                            HealthBucketLevel.Healthy -> colors.success
                            HealthBucketLevel.Degraded -> colors.warning
                            HealthBucketLevel.Down -> colors.danger
                            HealthBucketLevel.NoData -> colors.lineStrong
                        }
                        val vInset = if (active) 0f else 1.5.dp.toPx()
                        drawRoundRect(
                            color = if (inspected == null || active) barColor else barColor.copy(alpha = 0.36f),
                            topLeft = Offset(x, vInset),
                            size = Size(barWidth, (size.height - vInset * 2f).coerceAtLeast(2f)),
                            cornerRadius = CornerRadius(radiusPx, radiusPx),
                        )
                    }
                }
            }

            val current = inspected?.let { displayBuckets.getOrNull(it) }
            if (current != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(IgngTheme.radii.sm)
                        .background(colors.tileSoft)
                        .border(0.6.dp, colors.line, IgngTheme.radii.sm)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = current.title,
                        style = IgngTheme.type.badge.copy(fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace),
                        color = colors.textPrimary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = current.detail,
                        style = IgngTheme.type.badge,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                val leftText = startLabel?.takeIf { it.isNotBlank() } ?: buckets.first().title
                val rightText = endLabel?.takeIf { it.isNotBlank() } ?: buckets.last().title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = leftText,
                        style = IgngTheme.type.badge.copy(fontSize = 10.sp),
                        color = colors.textMuted,
                        maxLines = 1,
                    )
                    if (!availabilityText.isNullOrBlank()) {
                        Text(
                            text = availabilityText,
                            style = IgngTheme.type.badge.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                            color = availabilityColor,
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = rightText,
                        style = IgngTheme.type.badge.copy(fontSize = 10.sp),
                        color = colors.textMuted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

data class IgngBarMetric(
    val label: String,
    val formattedValue: String,
    val ratio: Float,
    val tint: Color? = null,
)

/**
 * Clean horizontal distribution bar chart shared across IGNG apps.
 */
@Composable
fun IgngHorizontalBarList(
    items: List<IgngBarMetric>,
    modifier: Modifier = Modifier,
) {
    val colors = IgngTheme.colors
    val intensity = IgngTheme.motionIntensity
    val anim = remember(items) { Animatable(if (intensity == net.igng.core.model.MotionIntensity.REDUCED) 1f else 0f) }
    LaunchedEffect(items, intensity) {
        if (intensity != net.igng.core.model.MotionIntensity.REDUCED) {
            anim.snapTo(0f)
            anim.animateTo(1f, IgngMotion.easeTween(340, intensity))
        } else {
            anim.snapTo(1f)
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { item ->
            val barTint = item.tint ?: colors.accent
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.label,
                        style = IgngTheme.type.bodyMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = item.formattedValue,
                        style = IgngTheme.type.mono,
                        color = colors.textSecondary,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.tileSoft),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((item.ratio.coerceIn(0.02f, 1f) * anim.value))
                            .height(7.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(barTint),
                    )
                }
            }
        }
    }
}


data class ChartPoint(
    val label: String,
    val value: Int,
)

@Composable
fun IgngLineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = IgngTheme.colors.accent,
    unitLabel: String = "",
) {
    IgngLineChart(
        points = points.map { IgngChartPoint(label = it.label, value = it.value.toDouble()) },
        lineColor = lineColor,
        valueFormatter = { v ->
            val num = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)
            if (unitLabel.isBlank()) num else "$num$unitLabel"
        },
        modifier = modifier,
    )
}

@Composable
fun IgngHorizontalBarList(
    items: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    barColor: Color = IgngTheme.colors.accent,
    valueFormatter: (Int) -> String = { it.toString() },
) {
    val maxVal = (items.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1).toFloat()
    IgngHorizontalBarList(
        items = items.map { (label, value) ->
            IgngBarMetric(
                label = label,
                formattedValue = valueFormatter(value),
                ratio = (value / maxVal).coerceIn(0.02f, 1f),
                tint = barColor,
            )
        },
        modifier = modifier,
    )
}
