package net.igng.core.designsystem

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.reflect.KType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.igng.core.model.MotionIntensity

/**
 * Swipe origin edge normalized from [BackEventCompat].
 */
enum class IgngPredictiveBackEdge {
    LEFT,
    RIGHT,
    NONE,
    ;

    val directionSign: Float
        get() = when (this) {
            LEFT -> 1f
            RIGHT -> -1f
            NONE -> 0f
        }

    companion object {
        fun fromCompat(swipeEdge: Int): IgngPredictiveBackEdge = when (swipeEdge) {
            BackEventCompat.EDGE_LEFT -> LEFT
            BackEventCompat.EDGE_RIGHT -> RIGHT
            else -> NONE
        }
    }
}

/**
 * Per-destination rendering profile for predictive back gestures.
 * - [CardPeel]: Full Bento card peel with asymmetric corner rounding, height-coupled torque tilt,
 *   edge highlight glow, and hardware shadow elevation.
 * - [SurfaceSlide]: Robust directional slide + subtle scale without clipping or rotation, ideal for
 *   embedded `AndroidView` / `WebView` surfaces where rotated clipping can flicker on some GPUs.
 */
enum class IgngPredictiveBackProfile {
    CardPeel,
    SurfaceSlide,
}

enum class IgngPredictiveBackPhase {
    Idle,
    Dragging,
    Cancelling,
    Committing,
}

@Immutable
data class IgngPredictiveCardTransform(
    val active: Boolean = false,
    val scale: Float = 1f,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val rotationZ: Float = 0f,
    val alpha: Float = 1f,
    val pivotX: Float = 0.5f,
    val pivotY: Float = 0.5f,
    val topStartRadiusPx: Float = 0f,
    val topEndRadiusPx: Float = 0f,
    val bottomEndRadiusPx: Float = 0f,
    val bottomStartRadiusPx: Float = 0f,
    val shadowElevationPx: Float = 0f,
    val backdropFillAlpha: Float = 0f,
    val borderAlpha: Float = 0f,
    val edgeGlowAlpha: Float = 0f,
    val edgeGlowCenterYFraction: Float = 0.5f,
    val isLeftEdge: Boolean = true,
    val clipEnabled: Boolean = false,
) {
    companion object {
        val Identity = IgngPredictiveCardTransform()
    }
}

@Immutable
data class IgngPredictiveUnderlayTransform(
    val active: Boolean = false,
    val scale: Float = 1f,
    val translationX: Float = 0f,
    val scrimAlpha: Float = 0f,
) {
    companion object {
        val Identity = IgngPredictiveUnderlayTransform()
    }
}

/**
 * Pure, deterministic physics model for IGNG's "Bento Peel" predictive back gesture.
 *
 * Design principles:
 * - Directional honesty: left-edge swipes peel rightward (`pivotX` anchored toward right);
 *   right-edge swipes peel leftward (`pivotX` anchored toward left) — never fights thumb motion.
 * - Continuous touch-height (`touchY`) field:
 *   - Rigid-body torque rotation (`rotationZ`, capped at ~2.6° in FULL mode): pushing upper-left
 *     rotates clockwise, lower-left rotates counter-clockwise, center translates cleanly; mirrored
 *     for right edge.
 *   - Asymmetric corner peel: the corner closest to the fingertip rounds first (up to 32dp) while
 *     the far edge settles gently to 18dp.
 *   - Vertical pivot & clamped vertical drift follow both initial grip height and live diagonal drag.
 * - Strict 3-tier [MotionIntensity] adherence (FULL / CALM / REDUCED) and full `NaN` sanitization
 *   for 3-button / hardware / API 36 `EDGE_NONE` back invocations.
 */
object IgngPredictiveBackPhysics {
    const val HAPTIC_TRIGGER_THRESHOLD = 0.28f
    const val HAPTIC_RESET_THRESHOLD = 0.16f
    const val HOLD_ALIVE_DURATION_MS = 320
    const val CHILD_PROGRESS_MIN = 0.02f
    const val CHILD_PROGRESS_MAX = 0.22f

    private const val MAX_TORQUE_DEGREES = 2.8f
    private const val COMMIT_EXTRA_TORQUE_DEGREES = 1.15f
    private val CommitOutEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    /**
     * Soft power-resistance curve: responsive right from touch-down without the sudden "lunge"
     * of steep cubic-bezier curves, gradually building elastic tension near `progress = 1f`.
     */
    fun easeDragProgress(rawProgress: Float): Float {
        val p = sanitizeUnit(rawProgress)
        return 1f - (1f - p).pow(1.65f)
    }

    /**
     * Compressed progress relayed to the child `NavHost`'s `SeekableTransitionState`.
     * Keeping it in `[0.02f, 0.22f]` guarantees:
     * 1. The underlying previous destination stays composed and alive from the very first frame.
     * 2. On commit from any drag depth (even `0.95f`), `NavHost` retains at least ~250ms of
     *    hold-alive window so our physics fly-out finishes cleanly with zero mid-flight pop/flash.
     */
    fun toRelayChildProgress(rawProgress: Float): Float {
        val p = sanitizeUnit(rawProgress)
        return (CHILD_PROGRESS_MIN + p * (CHILD_PROGRESS_MAX - CHILD_PROGRESS_MIN))
            .coerceIn(CHILD_PROGRESS_MIN, CHILD_PROGRESS_MAX)
    }

    /**
     * Computes commit fly-out duration (ms), always strictly shorter than the child `NavHost`
     * remaining hold-alive window (`>= ~250ms`) so the exiting card finishes fading out before
     * composition removal.
     */
    fun computeCommitDurationMs(
        releaseProgress: Float,
        velocityPerSec: Float,
        intensity: MotionIntensity,
    ): Int {
        if (intensity == MotionIntensity.REDUCED) return 0
        val p = sanitizeUnit(releaseProgress)
        val v = velocityPerSec.takeIf { it.isFinite() }?.coerceIn(0f, 4f) ?: 0f
        val base = if (intensity == MotionIntensity.CALM) {
            210f - p * 35f - v * 14f
        } else {
            235f - p * 40f - v * 18f
        }
        return base.toInt().coerceIn(155, 230)
    }

    /**
     * Hysteresis-gated haptic tick evaluation for crossing the back-commit detent.
     * Returns `nextArmed to shouldFireTick`.
     */
    fun evaluateHapticDetent(
        rawProgress: Float,
        alreadyTriggered: Boolean,
    ): Pair<Boolean, Boolean> {
        val p = sanitizeUnit(rawProgress)
        return when {
            !alreadyTriggered && p >= HAPTIC_TRIGGER_THRESHOLD -> true to true
            alreadyTriggered && p <= HAPTIC_RESET_THRESHOLD -> false to false
            else -> alreadyTriggered to false
        }
    }

    /**
     * Normalizes a screen-space Y coordinate into `[minFraction, maxFraction]`, safely falling
     * back to `0.5f` when `touchY` is `NaN`, non-finite, negative, or layout height is unavailable.
     */
    fun normalizeTouchY(
        touchY: Float,
        heightPx: Float,
        minFraction: Float = 0.06f,
        maxFraction: Float = 0.94f,
    ): Float {
        if (!touchY.isFinite() || !heightPx.isFinite() || heightPx <= 1f || touchY < 0f) {
            return 0.5f
        }
        return (touchY / heightPx).coerceIn(minFraction, maxFraction)
    }

    fun computeCardTransform(
        edge: IgngPredictiveBackEdge,
        rawProgress: Float,
        commitProgress: Float,
        startTouchY: Float,
        currentTouchY: Float,
        widthPx: Float,
        heightPx: Float,
        density: Float,
        intensity: MotionIntensity,
        profile: IgngPredictiveBackProfile = IgngPredictiveBackProfile.CardPeel,
        thresholdCrossed: Boolean = false,
    ): IgngPredictiveCardTransform {
        if (intensity == MotionIntensity.REDUCED || edge == IgngPredictiveBackEdge.NONE) {
            return IgngPredictiveCardTransform.Identity
        }
        val safeW = widthPx.takeIf { it.isFinite() && it > 1f } ?: return IgngPredictiveCardTransform.Identity
        val safeH = heightPx.takeIf { it.isFinite() && it > 1f } ?: return IgngPredictiveCardTransform.Identity
        val safeDensity = density.takeIf { it.isFinite() && it > 0.1f } ?: 1f

        val dragRaw = sanitizeUnit(rawProgress)
        val commitRaw = sanitizeUnit(commitProgress)
        if (dragRaw <= 0.0005f && commitRaw <= 0.0005f) {
            return IgngPredictiveCardTransform.Identity
        }

        val easedDrag = easeDragProgress(dragRaw)
        val easedCommit = CommitOutEasing.transform(commitRaw)
        val dir = edge.directionSign
        val isLeft = edge == IgngPredictiveBackEdge.LEFT
        val isFull = intensity == MotionIntensity.FULL
        val useCardPeel = profile == IgngPredictiveBackProfile.CardPeel

        val y0 = normalizeTouchY(startTouchY, safeH, minFraction = 0.08f, maxFraction = 0.92f)
        val yc = normalizeTouchY(currentTouchY, safeH, minFraction = 0.05f, maxFraction = 0.95f)
        val deltaYFromCenter = yc - 0.5f // [-0.45, +0.45]

        val rawDragDyPx = if (startTouchY.isFinite() && currentTouchY.isFinite() && startTouchY >= 0f && currentTouchY >= 0f) {
            currentTouchY - startTouchY
        } else {
            0f
        }

        // Dynamic scale pivot: anchored away from the swiped edge and vertically aligned to grip height
        val pivotX = if (isLeft) 0.84f else 0.16f
        val pivotY = if (isFull) {
            (0.5f + (y0 - 0.5f) * 0.55f).coerceIn(0.24f, 0.76f)
        } else {
            0.5f
        }

        // Scale shrinks into floating card during drag, then softly recedes on commit fly-out
        val maxDragShrink = when {
            !isFull -> 0.055f
            !useCardPeel -> 0.07f
            else -> 0.105f
        }
        val commitExtraShrink = if (isFull) 0.045f else 0.025f
        val scale = (1f - maxDragShrink * easedDrag - commitExtraShrink * easedCommit)
            .coerceIn(0.80f, 1f)

        // Directional horizontal translation: moves WITH the swipe direction (never fights thumb)
        val maxDragTxPx = min(safeW * if (isFull) 0.068f else 0.05f, (if (isFull) 28f else 20f) * safeDensity)
        val commitFlyOutTxPx = safeW * (if (isFull) 0.30f else 0.22f)
        val translationX = dir * (easedDrag * maxDragTxPx + easedCommit * commitFlyOutTxPx)

        // Height-coupled vertical arc + bounded live vertical drag follow
        val arcDriftPx = if (isFull) {
            -deltaYFromCenter * easedDrag * (15f * safeDensity)
        } else {
            -deltaYFromCenter * easedDrag * (6f * safeDensity)
        }
        val liveDragDriftPx = if (isFull) {
            tanh((rawDragDyPx / (120f * safeDensity)).toDouble()).toFloat() *
                (22f * safeDensity) * sqrt(easedDrag)
        } else {
            tanh((rawDragDyPx / (160f * safeDensity)).toDouble()).toFloat() *
                (10f * safeDensity) * sqrt(easedDrag)
        }
        val commitVerticalCarryPx = -deltaYFromCenter * easedCommit * (18f * safeDensity)
        val maxTyPx = (if (isFull) 30f else 14f) * safeDensity
        val translationY = (arcDriftPx + liveDragDriftPx + commitVerticalCarryPx)
            .coerceIn(-maxTyPx, maxTyPx)

        // Rigid-body fingertip torque rotation (FULL + CardPeel only, capped gently for comfort)
        val rotationZ = if (isFull && useCardPeel) {
            val normalizedOffset = (deltaYFromCenter * 2f).coerceIn(-0.92f, 0.92f)
            val dragTilt = -dir * normalizedOffset * easedDrag * MAX_TORQUE_DEGREES
            val commitTilt = -dir * normalizedOffset * easedCommit * COMMIT_EXTRA_TORQUE_DEGREES
            (dragTilt + commitTilt).coerceIn(-4.0f, 4.0f)
        } else {
            0f
        }

        // Asymmetric corner peel: corner nearest fingertip rounds first & deepest
        val cornerFactor = (easedDrag * 1.45f + easedCommit * 0.35f).coerceIn(0f, 1f)
        val topStartRadiusPx: Float
        val topEndRadiusPx: Float
        val bottomEndRadiusPx: Float
        val bottomStartRadiusPx: Float
        if (!useCardPeel) {
            topStartRadiusPx = 0f
            topEndRadiusPx = 0f
            bottomEndRadiusPx = 0f
            bottomStartRadiusPx = 0f
        } else if (!isFull) {
            val uniformPx = 20f * safeDensity * cornerFactor
            topStartRadiusPx = uniformPx
            topEndRadiusPx = uniformPx
            bottomEndRadiusPx = uniformPx
            bottomStartRadiusPx = uniformPx
        } else {
            val topWeight = (1f - yc).coerceIn(0f, 1f)
            val bottomWeight = yc.coerceIn(0f, 1f)
            val nearTopPx = (18f + 14f * topWeight) * safeDensity * cornerFactor
            val nearBottomPx = (18f + 14f * bottomWeight) * safeDensity * cornerFactor
            val farPx = 18f * safeDensity * cornerFactor
            if (isLeft) {
                topStartRadiusPx = nearTopPx
                bottomStartRadiusPx = nearBottomPx
                topEndRadiusPx = farPx
                bottomEndRadiusPx = farPx
            } else {
                topEndRadiusPx = nearTopPx
                bottomEndRadiusPx = nearBottomPx
                topStartRadiusPx = farPx
                bottomStartRadiusPx = farPx
            }
        }

        // Keep card completely solid (alpha = 1f) during drag; fade smoothly only during final 75% of commit
        val alpha = if (commitRaw <= 0.0005f) {
            1f
        } else {
            val fadeT = ((commitRaw - 0.18f) / 0.82f).coerceIn(0f, 1f)
            (1f - fadeT * fadeT).coerceIn(0f, 1f)
        }

        val shadowFactor = (easedDrag * (1f - easedCommit * 0.7f)).coerceIn(0f, 1f)
        val shadowElevationPx = if (useCardPeel) {
            (if (isFull) 16f else 10f) * safeDensity * shadowFactor
        } else {
            0f
        }

        // Solid canvas backdrop behind card ensures transparent screens never bleed through when rounded
        val backdropFillAlpha = (easedDrag * 4f + easedCommit * 4f).coerceIn(0f, 1f)

        val borderAlpha = if (useCardPeel) {
            (0.68f * cornerFactor * (1f - easedCommit * 0.85f)).coerceIn(0f, 1f)
        } else {
            0f
        }
        val edgeGlowBase = if (thresholdCrossed) 0.52f else 0.30f
        val edgeGlowAlpha = if (isFull && useCardPeel) {
            (edgeGlowBase * cornerFactor * (1f - easedCommit)).coerceIn(0f, 1f)
        } else {
            0f
        }

        return IgngPredictiveCardTransform(
            active = true,
            scale = scale,
            translationX = translationX,
            translationY = translationY,
            rotationZ = rotationZ,
            alpha = alpha,
            pivotX = pivotX,
            pivotY = pivotY,
            topStartRadiusPx = topStartRadiusPx,
            topEndRadiusPx = topEndRadiusPx,
            bottomEndRadiusPx = bottomEndRadiusPx,
            bottomStartRadiusPx = bottomStartRadiusPx,
            shadowElevationPx = shadowElevationPx,
            backdropFillAlpha = backdropFillAlpha,
            borderAlpha = borderAlpha,
            edgeGlowAlpha = edgeGlowAlpha,
            edgeGlowCenterYFraction = yc,
            isLeftEdge = isLeft,
            clipEnabled = useCardPeel && cornerFactor > 0.01f,
        )
    }

    fun computeUnderlayTransform(
        edge: IgngPredictiveBackEdge,
        rawProgress: Float,
        commitProgress: Float,
        density: Float,
        isDark: Boolean,
        intensity: MotionIntensity,
    ): IgngPredictiveUnderlayTransform {
        if (intensity == MotionIntensity.REDUCED || edge == IgngPredictiveBackEdge.NONE) {
            return IgngPredictiveUnderlayTransform.Identity
        }
        val safeDensity = density.takeIf { it.isFinite() && it > 0.1f } ?: 1f
        val dragRaw = sanitizeUnit(rawProgress)
        val commitRaw = sanitizeUnit(commitProgress)
        if (dragRaw <= 0.0005f && commitRaw <= 0.0005f) {
            return IgngPredictiveUnderlayTransform.Identity
        }

        val easedDrag = easeDragProgress(dragRaw)
        val easedCommit = CommitOutEasing.transform(commitRaw)
        // Reveal combines drag depth with commit completion toward 1f
        val reveal = (easedDrag * 0.65f + easedCommit * (1f - easedDrag * 0.65f)).coerceIn(0f, 1f)
        val remaining = 1f - reveal

        val isFull = intensity == MotionIntensity.FULL
        val minScale = if (isFull) 0.948f else 0.975f
        val scale = minScale + (1f - minScale) * reveal

        val maxParallaxPx = (if (isFull) 22f else 10f) * safeDensity
        // Underlay enters gently from the swipe edge side toward center
        val translationX = -edge.directionSign * remaining * maxParallaxPx

        val maxScrim = if (isDark) 0.34f else 0.18f
        val scrimAlpha = (maxScrim * remaining).coerceIn(0f, maxScrim)

        return IgngPredictiveUnderlayTransform(
            active = true,
            scale = scale,
            translationX = translationX,
            scrimAlpha = scrimAlpha,
        )
    }

    /**
     * Dock peek progress `[0f, 1f]` when a secondary destination is sliding back into a top-level
     * tab destination that displays [IgngDockHost].
     */
    fun computeDockRevealProgress(
        rawProgress: Float,
        commitProgress: Float,
        intensity: MotionIntensity,
    ): Float {
        if (intensity == MotionIntensity.REDUCED) return if (commitProgress > 0f) 1f else 0f
        val easedDrag = easeDragProgress(rawProgress)
        val easedCommit = CommitOutEasing.transform(commitProgress)
        val dragContribution = ((easedDrag - 0.08f) / 0.92f).coerceIn(0f, 1f) * 0.82f
        return (dragContribution + easedCommit * (1f - dragContribution)).coerceIn(0f, 1f)
    }

    private fun sanitizeUnit(value: Float): Float =
        if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
}

/**
 * Shared live gesture state observed by exiting/entering pages and [IgngDockHost] without causing
 * tree recomposition (animated properties are read inside `graphicsLayer` and `drawWithContent`).
 */
@Stable
class IgngPredictiveBackState {
    var phase by mutableStateOf(IgngPredictiveBackPhase.Idle)
        internal set

    /** True while the active NavHost pop transition is driven by our 2D gesture engine. */
    var isGestureSession by mutableStateOf(false)
        internal set

    var exitingEntryId by mutableStateOf<String?>(null)
        internal set

    var enteringEntryId by mutableStateOf<String?>(null)
        internal set

    var edge by mutableStateOf(IgngPredictiveBackEdge.NONE)
        internal set

    var startTouchY by mutableFloatStateOf(Float.NaN)
        internal set

    var currentTouchY by mutableFloatStateOf(Float.NaN)
        internal set

    var willRevealDock by mutableStateOf(false)
        internal set

    var targetTabId by mutableStateOf<String?>(null)
        internal set

    var thresholdCrossed by mutableStateOf(false)
        internal set

    var visualDragProgress by mutableFloatStateOf(0f)
        internal set

    var visualCommitProgress by mutableFloatStateOf(0f)
        internal set

    var dockRevealProgress by mutableFloatStateOf(0f)
        internal set

    private var lastFrameTimeMs: Long = 0L
    private var lastRawProgress: Float = 0f
    private var smoothedVelocity: Float = 0f
    private var activeAnimJob: Job? = null

    internal fun startDrag(
        exitingId: String?,
        enteringId: String?,
        swipeEdge: IgngPredictiveBackEdge,
        touchY: Float,
        rawProgress: Float,
        frameTimeMs: Long,
        revealDock: Boolean,
        dockTargetTabId: String?,
        intensity: MotionIntensity,
    ) {
        activeAnimJob?.cancel()
        activeAnimJob = null

        val safeProgress = if (rawProgress.isFinite()) rawProgress.coerceIn(0f, 1f) else 0f
        exitingEntryId = exitingId
        enteringEntryId = enteringId
        edge = swipeEdge
        startTouchY = touchY
        currentTouchY = touchY
        willRevealDock = revealDock
        targetTabId = dockTargetTabId
        thresholdCrossed = false
        lastFrameTimeMs = frameTimeMs
        lastRawProgress = safeProgress
        smoothedVelocity = 0f
        visualCommitProgress = 0f
        visualDragProgress = safeProgress
        dockRevealProgress = if (revealDock) {
            IgngPredictiveBackPhysics.computeDockRevealProgress(safeProgress, 0f, intensity)
        } else {
            0f
        }
        isGestureSession = true
        phase = IgngPredictiveBackPhase.Dragging
    }

    internal fun updateDrag(
        touchY: Float,
        rawProgress: Float,
        frameTimeMs: Long,
        intensity: MotionIntensity,
        onHapticTick: () -> Unit,
    ) {
        if (phase != IgngPredictiveBackPhase.Dragging) return
        val safeProgress = if (rawProgress.isFinite()) rawProgress.coerceIn(0f, 1f) else lastRawProgress

        if (touchY.isFinite() && touchY >= 0f) {
            currentTouchY = if (currentTouchY.isFinite() && currentTouchY >= 0f) {
                currentTouchY * 0.35f + touchY * 0.65f
            } else {
                touchY
            }
            if (!startTouchY.isFinite() || startTouchY < 0f) {
                startTouchY = touchY
            }
        }

        if (frameTimeMs > lastFrameTimeMs && lastFrameTimeMs > 0L) {
            val dtSec = ((frameTimeMs - lastFrameTimeMs).coerceAtLeast(1L)) / 1000f
            val instVel = ((safeProgress - lastRawProgress) / dtSec).coerceIn(0f, 5f)
            smoothedVelocity = smoothedVelocity * 0.6f + instVel * 0.4f
        }
        lastFrameTimeMs = frameTimeMs
        lastRawProgress = safeProgress

        val (nextCrossed, shouldFire) = IgngPredictiveBackPhysics.evaluateHapticDetent(
            rawProgress = safeProgress,
            alreadyTriggered = thresholdCrossed,
        )
        thresholdCrossed = nextCrossed
        if (shouldFire && intensity != MotionIntensity.REDUCED) {
            onHapticTick()
        }

        visualDragProgress = safeProgress
        if (willRevealDock) {
            dockRevealProgress = IgngPredictiveBackPhysics.computeDockRevealProgress(
                rawProgress = safeProgress,
                commitProgress = 0f,
                intensity = intensity,
            )
        }
    }

    internal fun cancelDrag(
        scope: CoroutineScope,
        intensity: MotionIntensity,
    ) {
        if (phase == IgngPredictiveBackPhase.Idle) return
        activeAnimJob?.cancel()
        phase = IgngPredictiveBackPhase.Cancelling
        thresholdCrossed = false

        val startVal = visualDragProgress
        activeAnimJob = scope.launch {
            val anim = Animatable(startVal)
            val spec = when (intensity) {
                MotionIntensity.REDUCED -> tween<Float>(0)
                MotionIntensity.CALM -> tween(durationMillis = 200, easing = IgngMotion.Ease)
                MotionIntensity.FULL -> spring(dampingRatio = 0.80f, stiffness = 520f)
            }
            anim.animateTo(
                targetValue = 0f,
                animationSpec = spec,
            ) {
                visualDragProgress = value
                if (willRevealDock) {
                    dockRevealProgress = IgngPredictiveBackPhysics.computeDockRevealProgress(
                        rawProgress = value,
                        commitProgress = 0f,
                        intensity = intensity,
                    )
                }
            }
            resetImmediate()
        }
    }

    internal fun commitDrag(
        scope: CoroutineScope,
        intensity: MotionIntensity,
    ) {
        if (phase == IgngPredictiveBackPhase.Idle) return
        activeAnimJob?.cancel()
        phase = IgngPredictiveBackPhase.Committing

        val releaseProgress = visualDragProgress
        val durationMs = IgngPredictiveBackPhysics.computeCommitDurationMs(
            releaseProgress = releaseProgress,
            velocityPerSec = smoothedVelocity,
            intensity = intensity,
        )

        activeAnimJob = scope.launch {
            val anim = Animatable(0f)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = durationMs, easing = LinearEasing),
            ) {
                visualCommitProgress = value
                if (willRevealDock) {
                    dockRevealProgress = IgngPredictiveBackPhysics.computeDockRevealProgress(
                        rawProgress = releaseProgress,
                        commitProgress = value,
                        intensity = intensity,
                    )
                }
            }
            // Brief tail grace so NavHost finishes removing the exiting entry while alpha is already 0f
            delay(95L)
            resetImmediate()
        }
    }

    internal fun resetImmediate() {
        activeAnimJob?.cancel()
        activeAnimJob = null
        phase = IgngPredictiveBackPhase.Idle
        isGestureSession = false
        exitingEntryId = null
        enteringEntryId = null
        edge = IgngPredictiveBackEdge.NONE
        startTouchY = Float.NaN
        currentTouchY = Float.NaN
        willRevealDock = false
        targetTabId = null
        thresholdCrossed = false
        visualDragProgress = 0f
        visualCommitProgress = 0f
        dockRevealProgress = 0f
        smoothedVelocity = 0f
        lastFrameTimeMs = 0L
        lastRawProgress = 0f
    }
}

val LocalIgngPredictiveBackState = compositionLocalOf<IgngPredictiveBackState?> { null }

@Composable
fun rememberIgngPredictiveBackState(): IgngPredictiveBackState = remember { IgngPredictiveBackState() }

/**
 * Reusable asymmetric rounded shape constructed purely in Draw/GraphicsLayer phase.
 */
private class AsymmetricRoundedShape(
    private val topStartPx: Float,
    private val topEndPx: Float,
    private val bottomEndPx: Float,
    private val bottomStartPx: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val tl = if (layoutDirection == LayoutDirection.Ltr) topStartPx else topEndPx
        val tr = if (layoutDirection == LayoutDirection.Ltr) topEndPx else topStartPx
        val br = if (layoutDirection == LayoutDirection.Ltr) bottomEndPx else bottomStartPx
        val bl = if (layoutDirection == LayoutDirection.Ltr) bottomStartPx else bottomEndPx
        return Outline.Rounded(
            RoundRect(
                rect = Rect(Offset.Zero, size),
                topLeft = CornerRadius(tl, tl),
                topRight = CornerRadius(tr, tr),
                bottomRight = CornerRadius(br, br),
                bottomLeft = CornerRadius(bl, bl),
            ),
        )
    }
}

/**
 * Shared GPU-accelerated Bento Peel surface decorator used by both [igngComposable] pages
 * and [IgngImageLightbox]. Draws the dynamic asymmetric corner clip, hardware shadow, solid
 * canvas backing, crisp 1dp border, and fingertip-tracked edge highlight glow with zero
 * recomposition during active gestures.
 */
internal fun Modifier.igngPredictiveCardLayer(
    transformProvider: (widthPx: Float, heightPx: Float, density: Float) -> IgngPredictiveCardTransform,
    canvasColor: Color,
    borderColor: Color,
    accentColor: Color,
    drawCardChrome: Boolean = true,
): Modifier = this
    .graphicsLayer {
        val t = transformProvider(size.width, size.height, density)
        if (!t.active) {
            scaleX = 1f
            scaleY = 1f
            translationX = 0f
            translationY = 0f
            rotationZ = 0f
            alpha = 1f
            shadowElevation = 0f
            clip = false
            return@graphicsLayer
        }
        transformOrigin = TransformOrigin(t.pivotX, t.pivotY)
        scaleX = t.scale
        scaleY = t.scale
        translationX = t.translationX
        translationY = t.translationY
        rotationZ = t.rotationZ
        alpha = t.alpha
        shadowElevation = t.shadowElevationPx
        if (t.clipEnabled) {
            shape = AsymmetricRoundedShape(
                topStartPx = t.topStartRadiusPx,
                topEndPx = t.topEndRadiusPx,
                bottomEndPx = t.bottomEndRadiusPx,
                bottomStartPx = t.bottomStartRadiusPx,
            )
            clip = true
        } else {
            clip = false
        }
    }
    .drawWithContent {
        val t = transformProvider(size.width, size.height, density)
        if (t.active && drawCardChrome && t.backdropFillAlpha > 0.005f) {
            // Solid backing prevents transparent screens from showing ghost bleed-through
            drawRect(color = canvasColor.copy(alpha = t.backdropFillAlpha))
        }
        drawContent()
        if (t.active && drawCardChrome && (t.borderAlpha > 0.005f || t.edgeGlowAlpha > 0.005f)) {
            val strokePx = 1.dp.toPx()
            val inset = strokePx * 0.5f
            val isLtr = layoutDirection == LayoutDirection.Ltr
            val tl = if (isLtr) t.topStartRadiusPx else t.topEndRadiusPx
            val tr = if (isLtr) t.topEndRadiusPx else t.topStartRadiusPx
            val br = if (isLtr) t.bottomEndRadiusPx else t.bottomStartRadiusPx
            val bl = if (isLtr) t.bottomStartRadiusPx else t.bottomEndRadiusPx

            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(inset, inset, size.width - inset, size.height - inset),
                        topLeft = CornerRadius(tl, tl),
                        topRight = CornerRadius(tr, tr),
                        bottomRight = CornerRadius(br, br),
                        bottomLeft = CornerRadius(bl, bl),
                    ),
                )
            }
            if (t.borderAlpha > 0.005f) {
                drawPath(
                    path = path,
                    color = borderColor.copy(alpha = t.borderAlpha),
                    style = Stroke(width = strokePx),
                )
            }
            if (t.edgeGlowAlpha > 0.005f) {
                val glowX = if (t.isLeftEdge) 0f else size.width
                val glowY = size.height * t.edgeGlowCenterYFraction
                val glowRadius = (size.height * 0.36f).coerceAtLeast(140.dp.toPx())
                drawPath(
                    path = path,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = t.edgeGlowAlpha),
                            accentColor.copy(alpha = t.edgeGlowAlpha * 0.25f),
                            Color.Transparent,
                        ),
                        center = Offset(glowX, glowY),
                        radius = glowRadius,
                    ),
                    style = Stroke(width = strokePx * 1.6f),
                )
            }
        }
    }

/**
 * Per-page surface wrapper installed automatically by [igngComposable].
 * Applies exiting Bento-card physics when this entry is being peeled back, or underlay parallax +
 * scrim reveal when this entry is the destination surfacing underneath.
 */
@Composable
fun IgngPredictivePageSurface(
    entryId: String,
    profile: IgngPredictiveBackProfile = IgngPredictiveBackProfile.CardPeel,
    content: @Composable () -> Unit,
) {
    val state = LocalIgngPredictiveBackState.current
    val colors = IgngTheme.colors
    val intensity = IgngTheme.motionIntensity

    if (state == null) {
        content()
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .igngPredictiveCardLayer(
                transformProvider = { widthPx, heightPx, density ->
                    if (state.phase != IgngPredictiveBackPhase.Idle && state.exitingEntryId == entryId) {
                        IgngPredictiveBackPhysics.computeCardTransform(
                            edge = state.edge,
                            rawProgress = state.visualDragProgress,
                            commitProgress = state.visualCommitProgress,
                            startTouchY = state.startTouchY,
                            currentTouchY = state.currentTouchY,
                            widthPx = widthPx,
                            heightPx = heightPx,
                            density = density,
                            intensity = intensity,
                            profile = profile,
                            thresholdCrossed = state.thresholdCrossed,
                        )
                    } else {
                        IgngPredictiveCardTransform.Identity
                    }
                },
                canvasColor = colors.canvas,
                borderColor = colors.lineStrong,
                accentColor = colors.accent,
                drawCardChrome = profile == IgngPredictiveBackProfile.CardPeel,
            )
            .graphicsLayer {
                if (state.phase != IgngPredictiveBackPhase.Idle && state.enteringEntryId == entryId) {
                    val u = IgngPredictiveBackPhysics.computeUnderlayTransform(
                        edge = state.edge,
                        rawProgress = state.visualDragProgress,
                        commitProgress = state.visualCommitProgress,
                        density = density,
                        isDark = colors.isDark,
                        intensity = intensity,
                    )
                    if (u.active) {
                        scaleX = u.scale
                        scaleY = u.scale
                        translationX = u.translationX
                        return@graphicsLayer
                    }
                }
                scaleX = 1f
                scaleY = 1f
                translationX = 0f
            }
            .drawWithContent {
                drawContent()
                if (state.phase != IgngPredictiveBackPhase.Idle && state.enteringEntryId == entryId) {
                    val u = IgngPredictiveBackPhysics.computeUnderlayTransform(
                        edge = state.edge,
                        rawProgress = state.visualDragProgress,
                        commitProgress = state.visualCommitProgress,
                        density = density,
                        isDark = colors.isDark,
                        intensity = intensity,
                    )
                    if (u.active && u.scrimAlpha > 0.005f) {
                        drawRect(color = colors.scrim.copy(alpha = u.scrimAlpha))
                    }
                }
            },
    ) {
        content()
    }
}

/**
 * Drop-in replacement for Navigation Compose `NavHost` that installs a zero-hack
 * [OnBackPressedDispatcher] relay capturing full 2D [BackEventCompat] coordinates (`swipeEdge`,
 * `touchY`, `progress`, `frameTimeMillis`) while letting the official `NavHost` keep the previous
 * destination composed and manage ViewModel / SavedState / lifecycle semantics natively.
 *
 * When on a root destination with no back stack entries to pop, the relay disables itself so the
 * platform's native predictive back-to-home animation runs untouched.
 */
@Composable
fun IgngPredictiveNavHost(
    navController: NavHostController,
    startDestination: Any,
    isTopLevelTab: (NavDestination?) -> Boolean,
    resolveTabId: (NavDestination?) -> String?,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    predictiveBackState: IgngPredictiveBackState = LocalIgngPredictiveBackState.current ?: rememberIgngPredictiveBackState(),
    builder: NavGraphBuilder.() -> Unit,
) {
    val intensity = IgngTheme.motionIntensity
    val haptic = rememberIgngHaptics()
    val scope = rememberCoroutineScope()
    val parentOwner = LocalOnBackPressedDispatcherOwner.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentIntensity by rememberUpdatedState(intensity)
    val currentHaptic by rememberUpdatedState(haptic)
    val currentIsTopLevelTab by rememberUpdatedState(isTopLevelTab)
    val currentResolveTabId by rememberUpdatedState(resolveTabId)

    // Keep state sane if external deep links or programmatic pops mutate the stack unexpectedly
    val activeEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(activeEntry?.id) {
        val currentId = activeEntry?.id
        if (
            (predictiveBackState.phase == IgngPredictiveBackPhase.Dragging ||
                predictiveBackState.phase == IgngPredictiveBackPhase.Cancelling) &&
            predictiveBackState.exitingEntryId != null &&
            currentId != predictiveBackState.exitingEntryId
        ) {
            predictiveBackState.resetImmediate()
        }
    }

    // Relay callback wired between host Activity dispatcher and child NavHost dispatcher
    val relayHolder = remember(navController, predictiveBackState) {
        lateinit var childDispatcher: OnBackPressedDispatcher
        lateinit var relayCallback: OnBackPressedCallback

        relayCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                val edge = IgngPredictiveBackEdge.fromCompat(backEvent.swipeEdge)
                val exiting = navController.currentBackStackEntry
                val entering = navController.previousBackStackEntry
                val effIntensity = currentIntensity
                val canAnimate2D = effIntensity != MotionIntensity.REDUCED &&
                    edge != IgngPredictiveBackEdge.NONE &&
                    exiting != null &&
                    entering != null &&
                    exiting.id != entering.id

                if (canAnimate2D) {
                    val revealDock = !currentIsTopLevelTab(exiting.destination) &&
                        currentIsTopLevelTab(entering.destination)
                    val targetTab = if (revealDock) currentResolveTabId(entering.destination) else null

                    predictiveBackState.startDrag(
                        exitingId = exiting.id,
                        enteringId = entering.id,
                        swipeEdge = edge,
                        touchY = backEvent.touchY,
                        rawProgress = backEvent.progress,
                        frameTimeMs = backEvent.frameTimeMillis,
                        revealDock = revealDock,
                        dockTargetTabId = targetTab,
                        intensity = effIntensity,
                    )
                    val relayed = BackEventCompat(
                        touchX = backEvent.touchX,
                        touchY = backEvent.touchY,
                        progress = IgngPredictiveBackPhysics.toRelayChildProgress(backEvent.progress),
                        swipeEdge = backEvent.swipeEdge,
                        frameTimeMillis = backEvent.frameTimeMillis,
                    )
                    childDispatcher.dispatchOnBackStarted(relayed)
                } else {
                    predictiveBackState.resetImmediate()
                    childDispatcher.dispatchOnBackStarted(backEvent)
                }
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                if (predictiveBackState.isGestureSession && predictiveBackState.phase == IgngPredictiveBackPhase.Dragging) {
                    val effIntensity = currentIntensity
                    predictiveBackState.updateDrag(
                        touchY = backEvent.touchY,
                        rawProgress = backEvent.progress,
                        frameTimeMs = backEvent.frameTimeMillis,
                        intensity = effIntensity,
                        onHapticTick = { currentHaptic(IgngHapticKind.Tick) },
                    )
                    val relayed = BackEventCompat(
                        touchX = backEvent.touchX,
                        touchY = backEvent.touchY,
                        progress = IgngPredictiveBackPhysics.toRelayChildProgress(backEvent.progress),
                        swipeEdge = backEvent.swipeEdge,
                        frameTimeMillis = backEvent.frameTimeMillis,
                    )
                    childDispatcher.dispatchOnBackProgressed(relayed)
                } else {
                    childDispatcher.dispatchOnBackProgressed(backEvent)
                }
            }

            override fun handleOnBackPressed() {
                if (predictiveBackState.isGestureSession && predictiveBackState.phase == IgngPredictiveBackPhase.Dragging) {
                    val exitingId = predictiveBackState.exitingEntryId
                    childDispatcher.onBackPressed()
                    // If a nested BackHandler intercepted the back press without popping, bounce back cleanly
                    if (exitingId != null && navController.currentBackStackEntry?.id == exitingId) {
                        predictiveBackState.cancelDrag(scope, currentIntensity)
                    } else {
                        predictiveBackState.commitDrag(scope, currentIntensity)
                    }
                } else {
                    predictiveBackState.resetImmediate()
                    childDispatcher.onBackPressed()
                }
            }

            override fun handleOnBackCancelled() {
                if (predictiveBackState.isGestureSession) {
                    predictiveBackState.cancelDrag(scope, currentIntensity)
                }
                childDispatcher.dispatchOnBackCancelled()
            }
        }

        childDispatcher = OnBackPressedDispatcher(
            fallbackOnBackPressed = null,
            onHasEnabledCallbacksChanged = Consumer { hasEnabled ->
                relayCallback.isEnabled = hasEnabled
            },
        )

        childDispatcher to relayCallback
    }

    val (childDispatcher, relayCallback) = relayHolder

    DisposableEffect(parentOwner, lifecycleOwner, childDispatcher, relayCallback) {
        val parentDispatcher = parentOwner?.onBackPressedDispatcher
        relayCallback.isEnabled = childDispatcher.hasEnabledCallbacks()
        parentDispatcher?.addCallback(lifecycleOwner, relayCallback)
        onDispose {
            relayCallback.remove()
            predictiveBackState.resetImmediate()
        }
    }

    val childOwner = remember(lifecycleOwner, childDispatcher) {
        object : OnBackPressedDispatcherOwner {
            override val lifecycle: Lifecycle
                get() = lifecycleOwner.lifecycle
            override val onBackPressedDispatcher: OnBackPressedDispatcher
                get() = childDispatcher
        }
    }

    val enterSpec: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (isTopLevelTab(initialState.destination) && isTopLevelTab(targetState.destination)) {
            IgngMotion.tabEnter(intensity)
        } else {
            IgngMotion.pushEnter(intensity)
        }
    }
    val exitSpec: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (isTopLevelTab(initialState.destination) && isTopLevelTab(targetState.destination)) {
            IgngMotion.tabExit(intensity)
        } else {
            IgngMotion.pushExit(intensity)
        }
    }
    val popEnterSpec: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        val useGestureHold = predictiveBackState.isGestureSession &&
            (predictiveBackState.phase == IgngPredictiveBackPhase.Dragging ||
                predictiveBackState.phase == IgngPredictiveBackPhase.Committing) &&
            intensity != MotionIntensity.REDUCED
        when {
            useGestureHold -> IgngMotion.predictiveHoldEnter()
            isTopLevelTab(initialState.destination) && isTopLevelTab(targetState.destination) ->
                IgngMotion.tabEnter(intensity)
            else -> IgngMotion.popEnter(intensity)
        }
    }
    val popExitSpec: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        val useGestureHold = predictiveBackState.isGestureSession &&
            (predictiveBackState.phase == IgngPredictiveBackPhase.Dragging ||
                predictiveBackState.phase == IgngPredictiveBackPhase.Committing) &&
            intensity != MotionIntensity.REDUCED
        when {
            useGestureHold -> IgngMotion.predictiveHoldExit()
            isTopLevelTab(initialState.destination) && isTopLevelTab(targetState.destination) ->
                IgngMotion.tabExit(intensity)
            else -> IgngMotion.popExit(intensity)
        }
    }

    CompositionLocalProvider(
        LocalIgngPredictiveBackState provides predictiveBackState,
        LocalOnBackPressedDispatcherOwner provides childOwner,
    ) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = modifier,
            contentAlignment = contentAlignment,
            enterTransition = enterSpec,
            exitTransition = exitSpec,
            popEnterTransition = popEnterSpec,
            popExitTransition = popExitSpec,
            builder = builder,
        )
    }
}

/**
 * Registers a type-safe destination wrapped in [IgngPredictivePageSurface] so every screen
 * automatically participates in IGNG's directional, height-aware Bento Peel predictive back.
 */
inline fun <reified T : Any> NavGraphBuilder.igngComposable(
    typeMap: Map<KType, @JvmSuppressWildcards NavType<*>> = emptyMap(),
    deepLinks: List<NavDeepLink> = emptyList(),
    predictiveBackProfile: IgngPredictiveBackProfile = IgngPredictiveBackProfile.CardPeel,
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable<T>(
        typeMap = typeMap,
        deepLinks = deepLinks,
    ) { entry ->
        IgngPredictivePageSurface(
            entryId = entry.id,
            profile = predictiveBackProfile,
        ) {
            content(entry)
        }
    }
}
