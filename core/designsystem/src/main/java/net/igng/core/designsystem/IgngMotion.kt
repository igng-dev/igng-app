package net.igng.core.designsystem

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.igng.core.model.MotionIntensity

/**
 * Unified IGNG Bento 2.0 motion grammar synced with `packages/theme/dashboard.css` & `loading.css`.
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
    const val StaggerStepMs = 36
    const val MaxStaggerIndex = 5

    const val LOADING_DELAY_MS = 140L
    const val LOADING_FADE_IN_MS = 160
    const val LOADING_MIN_SHOW_MS = 240L
    const val LOADING_FADE_OUT_MS = 180

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

    fun <T> easeTween(durationMs: Int = StandardMs, intensity: MotionIntensity = MotionIntensity.FULL): FiniteAnimationSpec<T> =
        if (intensity == MotionIntensity.REDUCED) snap() else tween(durationMs, easing = Ease)

    fun tabTransition(intensity: MotionIntensity): ContentTransform = when (intensity) {
        MotionIntensity.REDUCED -> fadeIn(snap()) togetherWith fadeOut(snap())
        MotionIntensity.CALM -> fadeIn(tween(180, easing = Ease)) togetherWith fadeOut(tween(140, easing = Ease))
        MotionIntensity.FULL -> (fadeIn(tween(220, easing = Ease)) + scaleIn(tween(240, easing = Ease), initialScale = 0.988f))
            .togetherWith(fadeOut(tween(150, easing = Ease)))
    }

    fun pushEnter(intensity: MotionIntensity): EnterTransition = when (intensity) {
        MotionIntensity.REDUCED -> fadeIn(snap())
        MotionIntensity.CALM -> fadeIn(tween(200, easing = Ease))
        MotionIntensity.FULL -> slideInHorizontally(tween(300, easing = Ease)) { (it * 0.14f).toInt() } +
            fadeIn(tween(240, easing = Ease))
    }

    fun pushExit(intensity: MotionIntensity): ExitTransition = when (intensity) {
        MotionIntensity.REDUCED -> fadeOut(snap())
        MotionIntensity.CALM -> fadeOut(tween(160, easing = Ease))
        MotionIntensity.FULL -> slideOutHorizontally(tween(260, easing = Ease)) { -(it * 0.05f).toInt() } +
            fadeOut(tween(180, easing = Ease))
    }

    fun popEnter(intensity: MotionIntensity): EnterTransition = when (intensity) {
        MotionIntensity.REDUCED -> fadeIn(snap())
        MotionIntensity.CALM -> fadeIn(tween(180, easing = Ease))
        MotionIntensity.FULL -> slideInHorizontally(tween(260, easing = Ease)) { -(it * 0.05f).toInt() } +
            fadeIn(tween(200, easing = Ease))
    }

    fun popExit(intensity: MotionIntensity): ExitTransition = when (intensity) {
        MotionIntensity.REDUCED -> fadeOut(snap())
        MotionIntensity.CALM -> fadeOut(tween(160, easing = Ease))
        MotionIntensity.FULL -> slideOutHorizontally(tween(280, easing = Ease)) { (it * 0.14f).toInt() } +
            fadeOut(tween(200, easing = Ease))
    }
}

@Composable
fun rememberSystemReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}

enum class IgngHapticKind {
    Tap,
    Tick,
    Confirm,
    Warning,
}

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
                    IgngHapticKind.Confirm -> HapticFeedbackConstants.CONFIRM
                    IgngHapticKind.Warning -> HapticFeedbackConstants.REJECT
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
    val reduced = IgngTheme.motionReduced
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) pressedScale else 1f,
        animationSpec = IgngMotion.quickSpring(reduced),
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
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val intensity = IgngTheme.motionIntensity
    val hapticsAllowed = IgngTheme.hapticsEnabled
    val hapticFeedback = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && intensity != MotionIntensity.REDUCED) scaleDown else 1f,
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
            if (haptic && hapticsAllowed) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            onClick()
        }
}

fun Modifier.igngStaggeredReveal(
    index: Int,
    key: Any? = Unit,
): Modifier = composed {
    val reduced = IgngTheme.motionReduced
    if (reduced || index > 5) return@composed this
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(index * 28L)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = IgngMotion.DURATION_MACRO, easing = IgngMotion.Ease),
        )
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 20f
    }
}

fun Modifier.igngBentoEnter(index: Int = 0): Modifier = composed {
    val intensity = IgngTheme.motionIntensity
    if (intensity == MotionIntensity.REDUCED) return@composed this
    val clamped = index.coerceIn(0, IgngMotion.MaxStaggerIndex)
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(if (intensity == MotionIntensity.FULL) 18f else 8f) }
    LaunchedEffect(clamped, intensity) {
        if (clamped > 0) delay((clamped * IgngMotion.StaggerStepMs).toLong())
        launch { alpha.animateTo(1f, tween(220, easing = IgngMotion.Ease)) }
        launch { offsetY.animateTo(0f, tween(280, easing = IgngMotion.Ease)) }
    }
    graphicsLayer {
        this.alpha = alpha.value
        translationY = offsetY.value
    }
}
