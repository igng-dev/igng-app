package net.igng.mcstatus.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import net.igng.core.designsystem.IgngMotion
import net.igng.core.designsystem.IgngTheme
import net.igng.mcstatus.core.model.McTextParser
import net.igng.mcstatus.core.model.ServerThemeKey

@Immutable
data class ServerFamilyVisual(
    val key: ServerThemeKey,
    val accent: Color,
    val secondaryGlow: Color,
    val softTint: Color,
    val borderGlow: Color,
)

fun serverFamilyVisual(key: ServerThemeKey, isDark: Boolean): ServerFamilyVisual {
    val alphaSoft = if (isDark) 0.18f else 0.11f
    val alphaBorder = if (isDark) 0.36f else 0.28f
    return when (key) {
        ServerThemeKey.CLEAR -> ServerFamilyVisual(
            key = key,
            accent = Color(0xFF10B981),
            secondaryGlow = Color(0xFFF59E0B),
            softTint = Color(0xFF10B981).copy(alpha = alphaSoft),
            borderGlow = Color(0xFF10B981).copy(alpha = alphaBorder),
        )
        ServerThemeKey.LITE_SERVER -> ServerFamilyVisual(
            key = key,
            accent = Color(0xFFF59E0B),
            secondaryGlow = Color(0xFFD97706),
            softTint = Color(0xFFF59E0B).copy(alpha = alphaSoft),
            borderGlow = Color(0xFFF59E0B).copy(alpha = alphaBorder),
        )
        ServerThemeKey.MODS -> ServerFamilyVisual(
            key = key,
            accent = Color(0xFF8B5CF6),
            secondaryGlow = Color(0xFF6366F1),
            softTint = Color(0xFF8B5CF6).copy(alpha = alphaSoft),
            borderGlow = Color(0xFF8B5CF6).copy(alpha = alphaBorder),
        )
        ServerThemeKey.BAN -> ServerFamilyVisual(
            key = key,
            accent = Color(0xFFEF4444),
            secondaryGlow = Color(0xFFB91C1C),
            softTint = Color(0xFFEF4444).copy(alpha = alphaSoft),
            borderGlow = Color(0xFFEF4444).copy(alpha = alphaBorder),
        )
        ServerThemeKey.MCSERVERS -> ServerFamilyVisual(
            key = key,
            accent = Color(0xFF06B6D4),
            secondaryGlow = Color(0xFF0E7490),
            softTint = Color(0xFF06B6D4).copy(alpha = alphaSoft),
            borderGlow = Color(0xFF06B6D4).copy(alpha = alphaBorder),
        )
    }
}

/**
 * Procedural Canvas texture matching `packages/minecraft/lib/server-backgrounds.js`
 * for all 5 IGNG server families. Zero bitmap bloat, crisp at every DPI.
 */
@Composable
fun IgngServerBackdropCanvas(
    themeKey: ServerThemeKey,
    modifier: Modifier = Modifier,
) {
    val isDark = IgngTheme.colors.isDark
    val visual = serverFamilyVisual(themeKey, isDark)
    val accent by animateColorAsState(
        targetValue = visual.accent,
        animationSpec = tween(if (IgngTheme.motionReduced) 0 else IgngMotion.DURATION_AMBIENT, easing = IgngMotion.Ease),
        label = "serverAccent",
    )
    val secondary by animateColorAsState(
        targetValue = visual.secondaryGlow,
        animationSpec = tween(if (IgngTheme.motionReduced) 0 else IgngMotion.DURATION_AMBIENT, easing = IgngMotion.Ease),
        label = "serverSecondary",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        // Primary atmospheric glow in upper-right
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accent.copy(alpha = if (isDark) 0.24f else 0.17f),
                    secondary.copy(alpha = if (isDark) 0.10f else 0.08f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.82f, size.height * 0.18f),
                radius = size.maxDimension * 0.85f,
            ),
            center = Offset(size.width * 0.82f, size.height * 0.18f),
            radius = size.maxDimension * 0.85f,
        )

        val patternAlpha = if (isDark) 0.09f else 0.07f
        val strokeColor = accent.copy(alpha = patternAlpha)

        when (themeKey) {
            ServerThemeKey.CLEAR -> {
                // Subtle diamond grid + warm sunlight halo
                val step = 28.dp.toPx()
                var x = -size.height
                while (x < size.width + size.height) {
                    drawLine(strokeColor, Offset(x, 0f), Offset(x + size.height, size.height), strokeWidth = 1f)
                    drawLine(strokeColor, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1f)
                    x += step
                }
            }
            ServerThemeKey.LITE_SERVER -> {
                // Warm angled stripes
                val step = 18.dp.toPx()
                var x = -size.height
                while (x < size.width + size.height) {
                    drawLine(strokeColor, Offset(x, size.height), Offset(x + size.height * 0.7f, 0f), strokeWidth = 2f)
                    x += step
                }
            }
            ServerThemeKey.MODS -> {
                // Engineering coordinate grid
                val step = 24.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(strokeColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    x += step
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(strokeColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += step
                }
            }
            ServerThemeKey.BAN -> {
                // Bold diagonal hazard bands
                val step = 22.dp.toPx()
                var x = -size.height
                while (x < size.width + size.height) {
                    drawLine(strokeColor, Offset(x, 0f), Offset(x + size.height, size.height), strokeWidth = 6f)
                    x += step
                }
            }
            ServerThemeKey.MCSERVERS -> {
                // Star atlas dot matrix
                val step = 22.dp.toPx()
                var y = step / 2f
                while (y < size.height) {
                    var x = step / 2f
                    while (x < size.width) {
                        drawCircle(accent.copy(alpha = patternAlpha * 1.4f), radius = 1.6f, center = Offset(x, y))
                        x += step
                    }
                    y += step
                }
            }
        }
    }
}

@Composable
fun IgngMcFormattedText(
    raw: String,
    modifier: Modifier = Modifier,
    style: TextStyle = IgngTheme.type.body,
    defaultColor: Color = IgngTheme.colors.textPrimary,
    maxLines: Int = Int.MAX_VALUE,
) {
    val isDark = IgngTheme.colors.isDark
    val spans = remember(raw) { McTextParser.parse(raw) }
    val annotated = remember(spans, isDark, defaultColor) {
        buildAnnotatedString {
            spans.forEach { span ->
                val resolvedColor = span.colorHex?.let { hex ->
                    // Ensure dark/light legibility for white/black Minecraft color codes
                    when {
                        !isDark && hex == 0xFFFFFFFFL -> defaultColor
                        !isDark && hex == 0xFFFFFF55L -> Color(0xFFB45309)
                        isDark && hex == 0xFF000000L -> defaultColor
                        else -> Color(hex)
                    }
                } ?: defaultColor
                withStyle(
                    SpanStyle(
                        color = resolvedColor,
                        fontWeight = if (span.bold) FontWeight.Bold else null,
                        fontStyle = if (span.italic) FontStyle.Italic else null,
                        textDecoration = when {
                            span.underline && span.strikethrough -> TextDecoration.combine(
                                listOf(TextDecoration.Underline, TextDecoration.LineThrough),
                            )
                            span.underline -> TextDecoration.Underline
                            span.strikethrough -> TextDecoration.LineThrough
                            else -> null
                        },
                    ),
                ) {
                    append(span.text)
                }
            }
        }
    }
    Text(
        text = annotated,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
