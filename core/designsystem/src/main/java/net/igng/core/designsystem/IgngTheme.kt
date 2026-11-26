package net.igng.core.designsystem

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import net.igng.core.model.MotionIntensity
import net.igng.core.model.ReaderCanvasTone
import net.igng.core.model.ThemeMode

@Immutable
data class IgngColors(
    val isDark: Boolean,
    val canvas: Color,
    val tile: Color,
    val tileSoft: Color,
    val tilePressed: Color,
    val line: Color,
    val lineStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentHover: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val accentGlow: Color,
    val auraBlue: Color,
    val auraViolet: Color,
    val success: Color,
    val successBg: Color,
    val warning: Color,
    val warningBg: Color,
    val danger: Color,
    val dangerBg: Color,
    val glassBar: Color,
    val scrim: Color,
    val codeBackground: Color,
    val quoteBar: Color,
    val warmPaperCanvas: Color,
    val warmPaperText: Color,
    val warmPaperSecondary: Color,
) {
    // Semantic soft-tint aliases for readability across apps
    val accentLow: Color get() = accentSoft
    val successLow: Color get() = successBg
    val warningLow: Color get() = warningBg
    val dangerLow: Color get() = dangerBg
}

val IgngLightColors = IgngColors(
    isDark = false,
    canvas = Color(0xFFF5F6F8),
    tile = Color(0xFFFFFFFF),
    tileSoft = Color(0xFFF7F8FA),
    tilePressed = Color(0xFFEEF1F5),
    line = Color(0x120F172A),
    lineStrong = Color(0x240F172A),
    textPrimary = Color(0xFF1F2328),
    textSecondary = Color(0xFF57606A),
    textMuted = Color(0xFF8C959F),
    accent = Color(0xFF2563EB),
    accentHover = Color(0xFF1D4ED8),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0x142563EB),
    accentGlow = Color(0x2E2563EB),
    auraBlue = Color(0x0D2563EB),
    auraViolet = Color(0x088B5CF6),
    success = Color(0xFF116329),
    successBg = Color(0x12116329),
    warning = Color(0xFF9A6700),
    warningBg = Color(0x169A6700),
    danger = Color(0xFFB42318),
    dangerBg = Color(0x12B42318),
    glassBar = Color(0xF7FFFFFF),
    scrim = Color(0x660B0F16),
    codeBackground = Color(0xFFF3F5F8),
    quoteBar = Color(0xFFCBD5E1),
    warmPaperCanvas = Color(0xFFFAF6EE),
    warmPaperText = Color(0xFF2C261F),
    warmPaperSecondary = Color(0xFF6B6053),
)

val IgngDarkColors = IgngColors(
    isDark = true,
    canvas = Color(0xFF0D1117),
    tile = Color(0xFF151B23),
    tileSoft = Color(0xFF1B222C),
    tilePressed = Color(0xFF212936),
    line = Color(0x14FFFFFF),
    lineStrong = Color(0x26FFFFFF),
    textPrimary = Color(0xFFE6EDF3),
    textSecondary = Color(0xFF8B949E),
    textMuted = Color(0xFF6E7681),
    accent = Color(0xFF58A6FF),
    accentHover = Color(0xFF79B8FF),
    onAccent = Color(0xFF0D1117),
    accentSoft = Color(0x1F58A6FF),
    accentGlow = Color(0x3D58A6FF),
    auraBlue = Color(0x1058A6FF),
    auraViolet = Color(0x0A8B5CF6),
    success = Color(0xFF56D364),
    successBg = Color(0x1856D364),
    warning = Color(0xFFE3B341),
    warningBg = Color(0x1CE3B341),
    danger = Color(0xFFFF7B72),
    dangerBg = Color(0x18FF7B72),
    glassBar = Color(0xF512171F),
    scrim = Color(0x99000000),
    codeBackground = Color(0xFF11161E),
    quoteBar = Color(0xFF334155),
    warmPaperCanvas = Color(0xFF171410),
    warmPaperText = Color(0xFFEAE2D3),
    warmPaperSecondary = Color(0xFFA89C89),
)

/**
 * Quiet Craft stroke scale: crisp sub-dp hairline borders on high-DPI mobile screens
 * so cards and dividers match the delicacy of web `1px` rules without wireframe heaviness.
 */
object IgngStroke {
    val hairline = 0.5.dp
    val focus = 1.dp
}

/**
 * Crisp geometric radius scale aligned with IGNG Web's `console.css` & `bento.css`.
 */
object IgngRadius {
    val hero = 18.dp
    val tile = 14.dp
    val card = 14.dp
    val inner = 10.dp
    val control = 10.dp
    val badge = 6.dp
    val pill = 999.dp
}

@Immutable
data class IgngRadii(
    val xs: RoundedCornerShape = RoundedCornerShape(6.dp),
    val sm: RoundedCornerShape = RoundedCornerShape(10.dp),
    val md: RoundedCornerShape = RoundedCornerShape(12.dp),
    val lg: RoundedCornerShape = RoundedCornerShape(14.dp),
    val xl: RoundedCornerShape = RoundedCornerShape(18.dp),
    val hero: RoundedCornerShape = RoundedCornerShape(18.dp),
    val badge: RoundedCornerShape = RoundedCornerShape(6.dp),
    val pill: RoundedCornerShape = RoundedCornerShape(999.dp),
)

/**
 * Unified Quiet Craft typography scale — the single source of truth across every IGNG Android app.
 * Tuned for CJK readability: neutral tracking on Chinese headings, generous 1.5x+ body line height,
 * calm secondary section eyebrows, and tabular numerals (`tnum`) on metrics and metadata.
 */
@Immutable
data class IgngTextStyles(
    val heroTitle: TextStyle = TextStyle(
        fontSize = 24.sp,
        lineHeight = 31.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
    ),
    val pageTitle: TextStyle = TextStyle(
        fontSize = 21.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
    ),
    val subpageTitle: TextStyle = TextStyle(
        fontSize = 17.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    val sectionLabel: TextStyle = TextStyle(
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    ),
    val sectionTitle: TextStyle = TextStyle(
        fontSize = 15.5.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    val cardTitle: TextStyle = TextStyle(
        fontSize = 15.5.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    val body: TextStyle = TextStyle(
        fontSize = 14.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
    ),
    val bodyMedium: TextStyle = TextStyle(
        fontSize = 14.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Medium,
    ),
    val caption: TextStyle = TextStyle(
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    ),
    val meta: TextStyle = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        fontFeatureSettings = "tnum",
    ),
    val badge: TextStyle = TextStyle(
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
    ),
    val metricLarge: TextStyle = TextStyle(
        fontSize = 24.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
        letterSpacing = (-0.3).sp,
    ),
    val metricMedium: TextStyle = TextStyle(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
        letterSpacing = (-0.2).sp,
    ),
    val mono: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum",
    ),
)

val LocalIgngColors = staticCompositionLocalOf { IgngLightColors }
val LocalIgngRadii = staticCompositionLocalOf { IgngRadii() }
val LocalIgngTypography = staticCompositionLocalOf { IgngTextStyles() }
val LocalMotionIntensity = staticCompositionLocalOf { MotionIntensity.FULL }
val LocalMotionReduced = staticCompositionLocalOf { false }
val LocalHapticsEnabled = staticCompositionLocalOf { true }

object IgngTheme {
    val colors: IgngColors
        @Composable
        @ReadOnlyComposable
        get() = LocalIgngColors.current

    val radii: IgngRadii
        @Composable
        @ReadOnlyComposable
        get() = LocalIgngRadii.current

    val type: IgngTextStyles
        @Composable
        @ReadOnlyComposable
        get() = LocalIgngTypography.current

    val motionIntensity: MotionIntensity
        @Composable
        @ReadOnlyComposable
        get() = LocalMotionIntensity.current

    val motionReduced: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalMotionReduced.current

    val hapticsEnabled: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalHapticsEnabled.current
}

/**
 * Scales a color's existing alpha by [factor] (`0f..1f`) instead of overwriting it.
 * Essential for semi-transparent design tokens such as [IgngColors.line] and [IgngColors.lineStrong]
 * where calling [Color.copy] with an absolute alpha would blow out into solid white/black.
 */
internal fun Color.scaleAlpha(factor: Float): Color =
    copy(alpha = (alpha * factor.coerceIn(0f, 1f)).coerceIn(0f, 1f))

fun IgngColors.readerSurfaceColors(tone: ReaderCanvasTone): Triple<Color, Color, Color> = when (tone) {
    ReaderCanvasTone.DEFAULT -> Triple(canvas, textPrimary, textSecondary)
    ReaderCanvasTone.WARM_PAPER -> Triple(warmPaperCanvas, warmPaperText, warmPaperSecondary)
    ReaderCanvasTone.INK_DARK -> Triple(IgngDarkColors.canvas, IgngDarkColors.textPrimary, IgngDarkColors.textSecondary)
}

/**
 * Provides coherent theme tokens when a reader surface overrides the canvas tone (for example
 * [ReaderCanvasTone.INK_DARK] while the host app is in light mode, or [ReaderCanvasTone.WARM_PAPER]).
 */
@Composable
fun ProvideIgngReaderSurface(
    tone: ReaderCanvasTone,
    content: @Composable () -> Unit,
) {
    val base = IgngTheme.colors
    val effective = when (tone) {
        ReaderCanvasTone.DEFAULT -> base
        ReaderCanvasTone.INK_DARK -> if (base.isDark) base else IgngDarkColors
        ReaderCanvasTone.WARM_PAPER -> base.copy(
            canvas = base.warmPaperCanvas,
            textPrimary = base.warmPaperText,
            textSecondary = base.warmPaperSecondary,
            tile = if (base.isDark) Color(0xFF211C16) else Color(0xFFFFFDF8),
            tileSoft = if (base.isDark) Color(0xFF28221A) else Color(0xFFF5EFE3),
            codeBackground = if (base.isDark) Color(0xFF1A1611) else Color(0xFFF0E8DA),
        )
    }
    CompositionLocalProvider(LocalIgngColors provides effective, content = content)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun IgngTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    motionIntensity: MotionIntensity = MotionIntensity.FULL,
    hapticsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) IgngDarkColors else IgngLightColors
    val systemReduced = rememberSystemReducedMotion()
    val effectiveIntensity = if (systemReduced) MotionIntensity.REDUCED else motionIntensity
    val reduced = effectiveIntensity == MotionIntensity.REDUCED

    // Keep system status/navigation bar icon contrast in sync even when themeMode overrides system
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    val m3Scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            primaryContainer = colors.accentSoft,
            onPrimaryContainer = colors.accent,
            secondary = colors.textSecondary,
            onSecondary = colors.onAccent,
            secondaryContainer = colors.tileSoft,
            onSecondaryContainer = colors.textPrimary,
            background = colors.canvas,
            onBackground = colors.textPrimary,
            surface = colors.tile,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.tileSoft,
            onSurfaceVariant = colors.textSecondary,
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = colors.canvas,
            surfaceContainerLow = colors.tile,
            surfaceContainer = colors.tile,
            surfaceContainerHigh = colors.tileSoft,
            surfaceContainerHighest = colors.tilePressed,
            outline = colors.line,
            outlineVariant = colors.lineStrong,
            error = colors.danger,
            onError = colors.onAccent,
            errorContainer = colors.dangerBg,
            onErrorContainer = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            primaryContainer = colors.accentSoft,
            onPrimaryContainer = colors.accent,
            secondary = colors.textSecondary,
            onSecondary = colors.onAccent,
            secondaryContainer = colors.tileSoft,
            onSecondaryContainer = colors.textPrimary,
            background = colors.canvas,
            onBackground = colors.textPrimary,
            surface = colors.tile,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.tileSoft,
            onSurfaceVariant = colors.textSecondary,
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = colors.tile,
            surfaceContainerLow = colors.tileSoft,
            surfaceContainer = colors.tile,
            surfaceContainerHigh = colors.tileSoft,
            surfaceContainerHighest = colors.tilePressed,
            outline = colors.line,
            outlineVariant = colors.lineStrong,
            error = colors.danger,
            onError = colors.onAccent,
            errorContainer = colors.dangerBg,
            onErrorContainer = colors.danger,
        )
    }

    val radii = IgngRadii()
    val type = IgngTextStyles()

    CompositionLocalProvider(
        LocalIgngColors provides colors,
        LocalIgngRadii provides radii,
        LocalIgngTypography provides type,
        LocalMotionIntensity provides effectiveIntensity,
        LocalMotionReduced provides reduced,
        LocalHapticsEnabled provides hapticsEnabled,
    ) {
        MaterialTheme(
            colorScheme = m3Scheme,
            shapes = Shapes(
                small = radii.sm,
                medium = radii.md,
                large = radii.lg,
            ),
            typography = Typography(
                headlineMedium = type.heroTitle,
                headlineSmall = type.pageTitle,
                titleLarge = type.pageTitle,
                titleMedium = type.sectionTitle,
                titleSmall = type.cardTitle,
                bodyLarge = type.bodyMedium,
                bodyMedium = type.body,
                bodySmall = type.caption,
                labelLarge = type.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                labelMedium = type.badge,
                labelSmall = type.meta,
            ),
            content = content,
        )
    }
}
