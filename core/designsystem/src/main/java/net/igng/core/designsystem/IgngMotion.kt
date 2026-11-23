package net.igng.core.designsystem

import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.igng.core.model.MotionIntensity

/**
 * Unified IGNG Bento 2.0 motion grammar synced with `packages/theme/dashboard.css` & `loading.css`.
 * Every animation respects all 3 tiers of [MotionIntensity]: FULL, CALM, and REDUCED.
 */
object IgngMotion {
    val Ease = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    val LoadEase = CubicBezierEasing(0.33f, 0f, 0.2f, 1f)

    const val DURATION_MICRO = 150
    const val DURATION_NORMAL = 220
    const val DURATION_MACRO = 300
    const val DURATION_AMBIENT = 450

    const val QuickMs = 150
    const val StandardMs = 240
    const val SpatialMs = 340
    const val StaggerStepMs = 32
    const val MaxStaggerIndex = 5

    const val LOADING_DELAY_MS = 140L
    const val LOADING_FADE_IN_MS = 160
    const val LOADING_MIN_SHOW_MS = 240L
    const val LOADING_FADE_OUT_MS = 180

    // --- 3-tier MotionIntensity primary API ---

    fun <T> microTween(intensity: MotionIntensity): FiniteAnimationSpec<T> = when (intensity) {
        MotionIntensity.REDUCED -> snap()
        MotionIntensity.CALM -> tween(durationMillis = 120, easing = Ease)
        MotionIntensity.FULL -> tween(durationMillis = DURATION_MICRO, easing = Ease)
    }

    fun <T> normalTween(intensity: MotionIntensity): FiniteAnimationSpec<T> = when (intensity) {
        MotionIntensity.REDUCED -> snap()
        MotionIntensity.CALM -> tween(durationMillis = 180, easing = Ease)
        MotionIntensity.FULL -> tween(durationMillis = DURATION_NORMAL, easing = Ease)
    }

    fun <T> macroTween(intensity: MotionIntensity): FiniteAnimationSpec<T> = when (intensity) {
        MotionIntensity.REDUCED -> snap()
        MotionIntensity.CALM -> tween(durationMillis = 220, easing = Ease)
        MotionIntensity.FULL -> tween(durationMillis = DURATION_MACRO, easing = Ease)
    }

    fun <T> ambientTween(intensity: MotionIntensity): FiniteAnimationSpec<T> = when (intensity) {
        MotionIntensity.REDUCED -> snap()
        MotionIntensity.CALM -> tween(durationMillis = 280, easing = Ease)
        MotionIntensity.FULL -> tween(durationMillis = DURATION_AMBIENT, easing = Ease)
    }

    fun <T> quickSpring(intensity: MotionIntensity): FiniteAnimationSpec<T> = when (intensity) {
        MotionIntensity.REDUCED -> snap()
        MotionIntensity.CALM -> tween(QuickMs, easing = Ease)
        MotionIntensity.FULL -> spring(dampingRatio = 0.86f, stiffness = 620f)
    }

    fun <T> standardSpring(intensity: MotionIntensity): FiniteAnimationSpec<T> = when (intensity) {
        MotionIntensity.REDUCED -> snap()
        MotionIntensity.CALM -> tween(StandardMs, easing = Ease)
        MotionIntensity.FULL -> spring(dampingRatio = 0.88f, stiffness = 420f)
    }

    fun <T> spatialSpring(intensity: MotionIntensity): FiniteAnimationSpec<T> = when (intensity) {
        MotionIntensity.REDUCED -> snap()
        MotionIntensity.CALM -> tween(SpatialMs, easing = Ease)
        MotionIntensity.FULL -> spring(dampingRatio = 0.90f, stiffness = 360f)
    }

    /**
     * Critically-damped monotonic spring for layout-coupled dock & capsule geometry:
     * zero overshoot and symmetric 0->1 / 1->0 trajectories so pill total width never twitches.
     */
    fun <T> dockSpring(intensity: MotionIntensity): FiniteAnimationSpec<T> = when (intensity) {
        MotionIntensity.REDUCED -> snap()
        MotionIntensity.CALM -> tween(durationMillis = 210, easing = Ease)
        MotionIntensity.FULL -> spring(dampingRatio = 1.0f, stiffness = 460f)
    }

    fun <T> easeTween(durationMs: Int = StandardMs, intensity: MotionIntensity = MotionIntensity.FULL): FiniteAnimationSpec<T> = when (intensity) {
        MotionIntensity.REDUCED -> snap()
        MotionIntensity.CALM -> tween((durationMs * 0.8f).toInt().coerceAtLeast(80), easing = Ease)
        MotionIntensity.FULL -> tween(durationMs, easing = Ease)
    }

    // --- Navigation transitions (top-level tab switch vs detail push/pop) ---

    fun tabEnter(intensity: MotionIntensity): EnterTransition = when (intensity) {
        MotionIntensity.REDUCED -> fadeIn(snap())
        MotionIntensity.CALM -> fadeIn(tween(160, easing = Ease))
        MotionIntensity.FULL -> fadeIn(tween(200, easing = Ease)) + scaleIn(tween(220, easing = Ease), initialScale = 0.99f)
    }

    fun tabExit(intensity: MotionIntensity): ExitTransition = when (intensity) {
        MotionIntensity.REDUCED -> fadeOut(snap())
        MotionIntensity.CALM -> fadeOut(tween(120, easing = Ease))
        MotionIntensity.FULL -> fadeOut(tween(140, easing = Ease))
    }

    fun tabTransition(intensity: MotionIntensity): ContentTransform =
        tabEnter(intensity) togetherWith tabExit(intensity)

    fun pushEnter(intensity: MotionIntensity): EnterTransition = when (intensity) {
        MotionIntensity.REDUCED -> fadeIn(snap())
        MotionIntensity.CALM -> fadeIn(tween(200, easing = Ease))
        MotionIntensity.FULL -> slideInHorizontally(tween(280, easing = Ease)) { (it * 0.12f).toInt() } +
            fadeIn(tween(220, easing = Ease))
    }

    fun pushExit(intensity: MotionIntensity): ExitTransition = when (intensity) {
        MotionIntensity.REDUCED -> fadeOut(snap())
        MotionIntensity.CALM -> fadeOut(tween(160, easing = Ease))
        MotionIntensity.FULL -> slideOutHorizontally(tween(240, easing = Ease)) { -(it * 0.04f).toInt() } +
            fadeOut(tween(170, easing = Ease))
    }

    fun popEnter(intensity: MotionIntensity): EnterTransition = when (intensity) {
        MotionIntensity.REDUCED -> fadeIn(snap())
        MotionIntensity.CALM -> fadeIn(tween(180, easing = Ease))
        MotionIntensity.FULL -> slideInHorizontally(tween(240, easing = Ease)) { -(it * 0.04f).toInt() } +
            fadeIn(tween(190, easing = Ease))
    }

    fun popExit(intensity: MotionIntensity): ExitTransition = when (intensity) {
        MotionIntensity.REDUCED -> fadeOut(snap())
        MotionIntensity.CALM -> fadeOut(tween(160, easing = Ease))
        MotionIntensity.FULL -> slideOutHorizontally(tween(260, easing = Ease)) { (it * 0.12f).toInt() } +
            fadeOut(tween(180, easing = Ease))
    }

    // --- Legacy boolean overloads retained for compatibility ---

    fun <T> microTween(reduced: Boolean = false): TweenSpec<T> =
        tween(durationMillis = if (reduced) 0 else DURATION_MICRO, easing = Ease)

    fun <T> normalTween(reduced: Boolean = false): TweenSpec<T> =
        tween(durationMillis = if (reduced) 0 else DURATION_NORMAL, easing = Ease)

    fun <T> macroTween(reduced: Boolean = false): TweenSpec<T> =
        tween(durationMillis = if (reduced) 0 else DURATION_MACRO, easing = Ease)

    fun <T> standardSpring(reduced: Boolean = false): SpringSpec<T> =
        if (reduced) spring(dampingRatio = 1f, stiffness = 10_000f)
        else spring(dampingRatio = 0.86f, stiffness = 420f)

    fun <T> spatialSpring(reduced: Boolean = false): SpringSpec<T> =
        if (reduced) spring(dampingRatio = 1f, stiffness = 10_000f)
        else spring(dampingRatio = 0.88f, stiffness = 360f)

    fun <T> quickSpring(reduced: Boolean = false): SpringSpec<T> =
        if (reduced) spring(dampingRatio = 1f, stiffness = 10_000f)
        else spring(dampingRatio = 0.84f, stiffness = 620f)
}

@Composable
fun rememberSystemReducedMotion(): Boolean {
    val context = LocalContext.current
    fun readScaleZero(): Boolean = runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }.getOrDefault(false)

    var reduced by remember(context) { mutableStateOf(readScaleZero()) }

    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = readScaleZero()
            }
        }
        runCatching {
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                observer,
            )
        }
        onDispose {
            runCatching { resolver.unregisterContentObserver(observer) }
        }
    }
    return reduced
}

enum class IgngHapticKind {
    Tap,
    Tick,
    Confirm,
    Warning,
}

/**
 * Single unified haptic engine shared by all IGNG apps.
 * Includes graceful API 26–29 fallbacks for CONFIRM / REJECT (introduced in API 30).
 */
@Composable
fun rememberIgngHaptics(): (IgngHapticKind) -> Unit {
    val view = LocalView.current
    val enabled = LocalHapticsEnabled.current
    return remember(view, enabled) {
        { kind ->
            if (enabled) {
                val constant = when (kind) {
                    IgngHapticKind.Tap -> HapticFeedbackConstants.KEYBOARD_TAP
                    IgngHapticKind.Tick -> HapticFeedbackConstants.CLOCK_TICK
                    IgngHapticKind.Confirm -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        HapticFeedbackConstants.CONFIRM
                    } else {
                        HapticFeedbackConstants.CONTEXT_CLICK
                    }
                    IgngHapticKind.Warning -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        HapticFeedbackConstants.REJECT
                    } else {
                        HapticFeedbackConstants.LONG_PRESS
                    }
                }
                view.performHapticFeedback(constant)
            }
        }
    }
}

fun Modifier.igngPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.982f,
): Modifier = composed {
    val intensity = IgngTheme.motionIntensity
    val pressed by interactionSource.collectIsPressedAsState()
    val effectiveScale = when {
        !pressed || intensity == MotionIntensity.REDUCED -> 1f
        intensity == MotionIntensity.CALM -> 1f - (1f - pressedScale) * 0.55f
        else -> pressedScale
    }
    val scale by animateFloatAsState(
        targetValue = effectiveScale,
        animationSpec = IgngMotion.quickSpring(intensity),
        label = "igngPressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

fun Modifier.igngPressScale(
    enabled: Boolean = true,
    scaleDown: Float = 0.982f,
    haptic: Boolean = false,
    hapticKind: IgngHapticKind = IgngHapticKind.Tap,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val intensity = IgngTheme.motionIntensity
    val fireHaptic = rememberIgngHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val effectiveScale = when {
        !pressed || intensity == MotionIntensity.REDUCED -> 1f
        intensity == MotionIntensity.CALM -> 1f - (1f - scaleDown) * 0.55f
        else -> scaleDown
    }
    val scale by animateFloatAsState(
        targetValue = effectiveScale,
        animationSpec = IgngMotion.quickSpring(intensity),
        label = "igngPressScaleClick",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
        ) {
            if (haptic) fireHaptic(hapticKind)
            onClick()
        }
}

/**
 * Unified first-screen staggered list entrance modifier.
 * Only animates indices 0..[IgngMotion.MaxStaggerIndex] once per [key], preventing scrolled-in
 * items from waiting invisible or replaying on LazyColumn recycling.
 */
fun Modifier.igngStaggeredEnter(
    index: Int,
    key: Any? = Unit,
): Modifier = composed {
    val intensity = IgngTheme.motionIntensity
    if (intensity == MotionIntensity.REDUCED || index > IgngMotion.MaxStaggerIndex) {
        return@composed this
    }
    var played by rememberSaveable(key, index) { mutableStateOf(false) }
    if (played) return@composed this

    val startOffset = if (intensity == MotionIntensity.FULL) 16f else 8f
    val alpha = remember(key, index) { Animatable(0f) }
    val offsetY = remember(key, index) { Animatable(startOffset) }
    LaunchedEffect(key, index, intensity) {
        if (index > 0) delay(index * IgngMotion.StaggerStepMs.toLong())
        val durAlpha = if (intensity == MotionIntensity.FULL) 220 else 160
        val durMove = if (intensity == MotionIntensity.FULL) 260 else 180
        launch { alpha.animateTo(1f, tween(durAlpha, easing = IgngMotion.Ease)) }
        launch { offsetY.animateTo(0f, tween(durMove, easing = IgngMotion.Ease)) }.join()
        played = true
    }
    graphicsLayer {
        this.alpha = alpha.value
        translationY = offsetY.value
    }
}

fun Modifier.igngStaggeredReveal(
    index: Int,
    key: Any? = Unit,
): Modifier = igngStaggeredEnter(index = index, key = key)

fun Modifier.igngBentoEnter(index: Int = 0): Modifier = igngStaggeredEnter(index = index)

/**
 * Hysteresis-gated NestedScroll visibility controller for floating reader/action bars.
 * Eliminates index-based jitter caused by micro-rebounds, overscroll, or async image height reflow.
 */
@Stable
class IgngScrollBarVisibilityState(
    private val hideThresholdPx: Float,
    private val showThresholdPx: Float,
) {
    var visible by mutableStateOf(true)
        private set

    private var accumulatedDeltaPx = 0f

    fun forceShow() {
        accumulatedDeltaPx = 0f
        visible = true
    }

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy == 0f) return Offset.Zero
            // Reset accumulator on direction reversal so tiny jitters never cross thresholds
            if ((dy < 0f && accumulatedDeltaPx > 0f) || (dy > 0f && accumulatedDeltaPx < 0f)) {
                accumulatedDeltaPx = 0f
            }
            accumulatedDeltaPx += dy
            if (accumulatedDeltaPx <= -hideThresholdPx && visible) {
                visible = false
                accumulatedDeltaPx = 0f
            } else if (accumulatedDeltaPx >= showThresholdPx && !visible) {
                visible = true
                accumulatedDeltaPx = 0f
            }
            return Offset.Zero
        }
    }
}

@Composable
fun rememberIgngScrollBarVisibility(
    hideThreshold: Dp = 32.dp,
    showThreshold: Dp = 20.dp,
): IgngScrollBarVisibilityState {
    val density = LocalDensity.current
    val hidePx = with(density) { hideThreshold.toPx() }
    val showPx = with(density) { showThreshold.toPx() }
    return remember(hidePx, showPx) {
        IgngScrollBarVisibilityState(hideThresholdPx = hidePx, showThresholdPx = showPx)
    }
}
