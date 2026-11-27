package net.igng.core.designsystem

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.decode.DataSource
import kotlinx.coroutines.delay

/**
 * Quiet Craft root canvas.
 * Renders a serene solid canvas by default so daily reading/utility screens stay visually calm.
 * Set [showAmbientGlow] = true (or pass [accentOverride]) only on deliberate brand hero surfaces.
 */
@Composable
fun IgngAmbientCanvas(
    modifier: Modifier = Modifier,
    accentOverride: Color? = null,
    showAmbientGlow: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = IgngTheme.colors
    val intensity = IgngTheme.motionIntensity
    val enableGlow = showAmbientGlow || accentOverride != null

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        if (enableGlow) {
            val topTint by animateColorAsState(
                targetValue = (accentOverride ?: colors.accent).copy(alpha = if (colors.isDark) 0.08f else 0.045f),
                animationSpec = IgngMotion.ambientTween(intensity),
                label = "ambientTopWash",
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(topTint, Color.Transparent),
                        center = Offset(size.width * 0.5f, -size.height * 0.08f),
                        radius = size.maxDimension * 0.55f,
                    ),
                    center = Offset(size.width * 0.5f, -size.height * 0.08f),
                    radius = size.maxDimension * 0.55f,
                )
            }
        }
        content()
    }
}

/**
 * Crisp sub-dp hairline divider matching IGNG Web's `1px solid var(--bbs-line)` delicacy on 3x screens.
 */
@Composable
fun IgngHairlineDivider(
    modifier: Modifier = Modifier,
    startIndent: Dp = 0.dp,
    endIndent: Dp = 0.dp,
) {
    HorizontalDivider(
        modifier = modifier.padding(start = startIndent, end = endIndent),
        thickness = IgngStroke.hairline,
        color = IgngTheme.colors.line,
    )
}

/**
 * Tier-1 Quiet Craft grouped surface: consolidates related menu entries, settings, or structured rows
 * into a single clean hairline-bordered panel instead of scattering standalone cards across the screen.
 */
@Composable
fun IgngGroupedSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = IgngTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        if (!title.isNullOrBlank()) {
            IgngSectionHeader(
                title = title,
                actionLabel = actionLabel,
                onAction = onAction,
                trailing = trailing,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = IgngTheme.radii.lg,
            color = colors.tile,
            border = BorderStroke(IgngStroke.hairline, colors.line),
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content,
            )
        }
    }
}

/**
 * Quiet Craft grouped list row designed to live inside [IgngGroupedSection].
 * Uses serene press tint (zero bouncing scale), monochrome icons by default, and indented hairlines.
 */
@Composable
fun IgngListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = IgngTheme.colors.textSecondary,
    value: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = true,
    showDivider: Boolean = false,
) {
    val colors = IgngTheme.colors
    val intensity = IgngTheme.motionIntensity
    val haptic = rememberIgngHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val rowBg by animateColorAsState(
        targetValue = if (pressed) colors.tilePressed else Color.Transparent,
        animationSpec = IgngMotion.microTween(intensity),
        label = "listRowBg",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(rowBg)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        haptic(IgngHapticKind.Tick)
                        onClick()
                    },
                )
                .defaultMinSize(minHeight = 52.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = IgngTheme.type.bodyMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = IgngTheme.type.meta,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!value.isNullOrBlank()) {
                Text(
                    text = value,
                    style = IgngTheme.type.meta,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (trailing != null) {
                trailing()
            }
            if (showChevron) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (showDivider) {
            IgngHairlineDivider(
                startIndent = if (icon != null) 48.dp else 16.dp,
                endIndent = 16.dp,
            )
        }
    }
}

/**
 * Editorial full-bleed surface container for continuous hairline-separated feeds
 * (mobile counterpart of IGNG Web's `.bbs-tile--flush > .bbs-rows`).
 */
@Composable
fun IgngFeedSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = IgngTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.tile),
    ) {
        IgngHairlineDivider()
        content()
        IgngHairlineDivider()
    }
}

/**
 * Tier-2 Quiet Craft Tile card: crisp 0.5dp hairline border, clean flat surface, zero muddy shadow.
 */
@Composable
fun IgngTile(
    modifier: Modifier = Modifier,
    shape: Shape = IgngTheme.radii.lg,
    soft: Boolean = false,
    borderColor: Color = IgngTheme.colors.line,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = IgngTheme.colors
    Surface(
        modifier = modifier,
        shape = shape,
        color = if (soft) colors.tileSoft else colors.tile,
        border = BorderStroke(IgngStroke.hairline, borderColor),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

/**
 * Interactive Quiet Craft Tile with calm surface press transition and subtle haptics.
 * Avoids distracting border color flashes and exaggerated bounce on routine cards.
 */
@Composable
fun IgngInteractiveTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = IgngTheme.radii.lg,
    soft: Boolean = false,
    accentBorder: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    pressScale: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = IgngTheme.colors
    val intensity = IgngTheme.motionIntensity
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptic = rememberIgngHaptics()

    val border by animateColorAsState(
        targetValue = when {
            accentBorder != null && pressed -> accentBorder.copy(alpha = 0.42f)
            accentBorder != null -> accentBorder.copy(alpha = 0.24f)
            pressed -> colors.lineStrong
            else -> colors.line
        },
        animationSpec = IgngMotion.microTween(intensity),
        label = "tileBorder",
    )
    val bg by animateColorAsState(
        targetValue = when {
            pressed -> colors.tilePressed
            soft -> colors.tileSoft
            else -> colors.tile
        },
        animationSpec = IgngMotion.microTween(intensity),
        label = "tileBg",
    )

    val interactiveMod = if (pressScale) {
        modifier.igngPressScale(interactionSource)
    } else {
        modifier
    }

    Surface(
        modifier = interactiveMod
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic(IgngHapticKind.Tap)
                    onClick()
                },
            ),
        shape = shape,
        color = bg,
        border = BorderStroke(IgngStroke.hairline, border),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

enum class IgngButtonStyle {
    Primary,
    Secondary,
    Ghost,
    Danger,
}

@Composable
fun IgngButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: IgngButtonStyle = IgngButtonStyle.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    compact: Boolean = false,
) {
    val colors = IgngTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptic = rememberIgngHaptics()

    val containerColor = when (style) {
        IgngButtonStyle.Primary -> if (pressed) colors.accentHover else colors.accent
        IgngButtonStyle.Secondary -> if (pressed) colors.tilePressed else colors.tile
        IgngButtonStyle.Ghost -> if (pressed) colors.accentSoft else Color.Transparent
        IgngButtonStyle.Danger -> if (pressed) colors.danger else colors.dangerBg
    }
    val contentColor = when (style) {
        IgngButtonStyle.Primary -> colors.onAccent
        IgngButtonStyle.Secondary -> colors.textPrimary
        IgngButtonStyle.Ghost -> colors.accent
        IgngButtonStyle.Danger -> if (pressed) colors.onAccent else colors.danger
    }
    val border = when (style) {
        IgngButtonStyle.Secondary -> BorderStroke(IgngStroke.hairline, colors.lineStrong)
        IgngButtonStyle.Danger -> BorderStroke(IgngStroke.hairline, colors.danger.copy(alpha = 0.30f))
        else -> null
    }

    val effectiveAlpha = if (enabled) 1f else 0.45f
    Surface(
        modifier = modifier
            .igngPressScale(interactionSource, pressedScale = 0.985f)
            .clip(IgngTheme.radii.sm)
            .clickable(
                enabled = enabled && !loading,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic(if (style == IgngButtonStyle.Danger) IgngHapticKind.Warning else IgngHapticKind.Tap)
                    onClick()
                },
            ),
        shape = IgngTheme.radii.sm,
        color = containerColor.copy(alpha = containerColor.alpha * effectiveAlpha),
        border = border,
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = if (compact) 12.dp else 16.dp,
                vertical = if (compact) 8.dp else 11.dp,
            ),
            contentAlignment = Alignment.Center,
        ) {
            // Keep intrinsic width locked while showing Folded Core loader (per console-design.md)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.offset(y = 0.dp),
            ) {
                val textAlpha = if (loading) 0f else 1f
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor.copy(alpha = textAlpha),
                        modifier = Modifier.size(if (compact) 15.dp else 17.dp),
                    )
                }
                Text(
                    text = text,
                    style = if (compact) IgngTheme.type.caption.copy(fontWeight = FontWeight.SemiBold)
                    else IgngTheme.type.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = contentColor.copy(alpha = textAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (loading) {
                IgngFoldedCoreGlyph(
                    size = FoldedCoreSize.Sm,
                    color = contentColor,
                )
            }
        }
    }
}

enum class IgngBadgeTone {
    Neutral,
    Accent,
    Success,
    Warning,
    Danger,
}

/**
 * Quiet Craft status badge matching IGNG Web's `.cs-badge` / `.bbs-pill`:
 * crisp 6dp square-rounded corners, soft semantic tint, no heavy colored borders.
 */
@Composable
fun IgngBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: IgngBadgeTone = IgngBadgeTone.Neutral,
    customColor: Color? = null,
    leadingDot: Boolean = false,
    pulse: Boolean = false,
) {
    val colors = IgngTheme.colors
    val fg = customColor ?: when (tone) {
        IgngBadgeTone.Neutral -> colors.textSecondary
        IgngBadgeTone.Accent -> colors.accent
        IgngBadgeTone.Success -> colors.success
        IgngBadgeTone.Warning -> colors.warning
        IgngBadgeTone.Danger -> colors.danger
    }
    val bg = when {
        customColor != null -> customColor.copy(alpha = if (colors.isDark) 0.14f else 0.10f)
        tone == IgngBadgeTone.Neutral -> colors.tileSoft
        tone == IgngBadgeTone.Accent -> colors.accentSoft
        tone == IgngBadgeTone.Success -> colors.successBg
        tone == IgngBadgeTone.Warning -> colors.warningBg
        else -> colors.dangerBg
    }
    val shape = IgngTheme.radii.badge
    val borderMod = if (tone == IgngBadgeTone.Neutral && customColor == null) {
        Modifier.border(IgngStroke.hairline, colors.line, shape)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .then(borderMod)
            .padding(horizontal = 7.dp, vertical = 2.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (leadingDot) {
            IgngLivePulseDot(color = fg, active = pulse, size = 6.dp)
        }
        Text(
            text = text,
            style = IgngTheme.type.badge,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun IgngLivePulseDot(
    color: Color,
    active: Boolean = true,
    size: Dp = 6.dp,
) {
    val reduced = IgngTheme.motionReduced
    val pulseAlpha = if (active && !reduced) {
        val transition = rememberInfiniteTransition(label = "livePulse")
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.65f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "livePulseAlpha",
        ).value
    } else {
        0f
    }
    Box(modifier = Modifier.size(size + 4.dp), contentAlignment = Alignment.Center) {
        if (active && !reduced) {
            Box(
                modifier = Modifier
                    .size(size + 4.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = pulseAlpha * 0.42f)),
            )
        }
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(color),
        )
    }
}

/**
 * Interactive copy pill with animated Copy -> Check morph + success feedback.
 */
@Composable
fun IgngCopyPill(
    value: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    mono: Boolean = true,
) {
    val context = LocalContext.current
    val colors = IgngTheme.colors
    val intensity = IgngTheme.motionIntensity
    val haptic = rememberIgngHaptics()
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800L)
            copied = false
        }
    }

    val border by animateColorAsState(
        targetValue = if (copied) colors.success else colors.line,
        animationSpec = IgngMotion.microTween(intensity),
        label = "copyBorder",
    )
    val bg by animateColorAsState(
        targetValue = if (copied) colors.successBg else colors.tileSoft,
        animationSpec = IgngMotion.microTween(intensity),
        label = "copyBg",
    )
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .clip(IgngTheme.radii.sm)
            .background(bg)
            .border(IgngStroke.hairline, border, IgngTheme.radii.sm)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    copyTextToClipboard(context, label ?: "IGNG", value)
                    haptic(IgngHapticKind.Confirm)
                    copied = true
                },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (copied) "已复制" else (label ?: value),
            style = if (mono && !copied) IgngTheme.type.mono else IgngTheme.type.caption.copy(fontWeight = FontWeight.Medium),
            color = if (copied) colors.success else colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        AnimatedContent(
            targetState = copied,
            transitionSpec = {
                if (intensity == net.igng.core.model.MotionIntensity.REDUCED) {
                    fadeIn(IgngMotion.microTween(intensity)) togetherWith fadeOut(IgngMotion.microTween(intensity))
                } else {
                    (fadeIn(IgngMotion.microTween(intensity)) + scaleIn(IgngMotion.microTween(intensity), initialScale = 0.72f)) togetherWith
                        (fadeOut(IgngMotion.microTween(intensity)) + scaleOut(IgngMotion.microTween(intensity), targetScale = 0.72f))
                }
            },
            label = "copyIconMorph",
        ) { isCopied ->
            Icon(
                imageVector = if (isCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                contentDescription = if (isCopied) "已复制" else "复制",
                tint = if (isCopied) colors.success else colors.textSecondary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

fun copyTextToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

/**
 * Vertical rolling numeric text with tabular numerals (`tnum`) so live metrics glide without jitter.
 */
@Composable
fun IgngAnimatedCounter(
    text: String,
    style: TextStyle = IgngTheme.type.metricLarge,
    color: Color = IgngTheme.colors.textPrimary,
    modifier: Modifier = Modifier,
) {
    val intensity = IgngTheme.motionIntensity
    if (intensity == net.igng.core.model.MotionIntensity.REDUCED) {
        Text(text = text, style = style, color = color, modifier = modifier, maxLines = 1)
        return
    }
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(IgngMotion.standardSpring(intensity)) { it / 3 } + fadeIn(IgngMotion.microTween(intensity))) togetherWith
                (slideOutVertically(IgngMotion.standardSpring(intensity)) { -it / 3 } + fadeOut(IgngMotion.microTween(intensity)))
        },
        label = "igngCounter",
        modifier = modifier,
    ) { value ->
        Text(text = value, style = style, color = color, maxLines = 1)
    }
}

/**
 * Spring-sliding segmented control matching IGNG Console's `Segmented`.
 */
@Composable
fun <T> IgngSegmentedTabs(
    items: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val colors = IgngTheme.colors
    val intensity = IgngTheme.motionIntensity
    val haptic = rememberIgngHaptics()
    val selectedIndex = items.indexOf(selected).coerceAtLeast(0)
    val thumbShape = RoundedCornerShape(8.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(IgngTheme.radii.sm)
            .background(colors.tileSoft)
            .border(IgngStroke.hairline, colors.line, IgngTheme.radii.sm)
            .padding(3.dp),
    ) {
        val segmentWidth = maxWidth / items.size
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = IgngMotion.dockSpring(intensity),
            label = "segmentIndicator",
        )

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .height(32.dp)
                .clip(thumbShape)
                .background(colors.tile)
                .border(IgngStroke.hairline, colors.line, thumbShape),
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            items.forEach { item ->
                val isSelected = item == selected
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) colors.textPrimary else colors.textSecondary,
                    animationSpec = IgngMotion.microTween(intensity),
                    label = "segmentText",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(thumbShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (!isSelected) {
                                    haptic(IgngHapticKind.Tick)
                                    onSelect(item)
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = labelOf(item),
                        style = IgngTheme.type.caption.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

data class IgngNavTabItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val unselectedIcon: ImageVector = icon,
    val badgeCount: Int = 0,
)

/**
 * Legacy alias forwarding to unified fixed-slot [IgngFloatingDockBar].
 */
@Composable
fun IgngFloatingNavBar(
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

/**
 * Unified Quiet Craft top bar:
 * - Top-level tab header (`onBack == null`): bold 21sp page title + clean trailing actions.
 * - Secondary detail header (`onBack != null`): lightweight borderless 44dp back touch target +
 *   refined 17sp SemiBold title without heavy boxed back buttons.
 */
@Composable
fun IgngTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val colors = IgngTheme.colors
    val haptic = rememberIgngHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (onBack != null) 6.dp else 16.dp,
                end = 12.dp,
                top = if (onBack != null) 6.dp else 10.dp,
                bottom = if (onBack != null) 6.dp else 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onBack != null) {
            IconButton(
                onClick = {
                    haptic(IgngHapticKind.Tap)
                    onBack()
                },
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (onBack != null) IgngTheme.type.subpageTitle else IgngTheme.type.pageTitle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = IgngTheme.type.meta,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            actions()
        }
    }
}

/**
 * Quiet Craft section eyebrow header.
 * Defaults to a calm 12.5sp secondary eyebrow (`sectionLabel`) so page content titles take visual priority;
 * pass [prominent] = true for primary headings inside standalone cards or sheets.
 */
@Composable
fun IgngSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    prominent: Boolean = false,
) {
    val colors = IgngTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (prominent) IgngTheme.type.sectionTitle else IgngTheme.type.sectionLabel,
                color = if (prominent) colors.textPrimary else colors.textSecondary,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = IgngTheme.type.meta,
                    color = colors.textSecondary,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        } else if (!actionLabel.isNullOrBlank() && onAction != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = actionLabel,
                style = IgngTheme.type.caption.copy(fontWeight = FontWeight.Medium),
                color = colors.accent,
                modifier = Modifier
                    .clip(IgngTheme.radii.xs)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun IgngSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSubmit: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
) {
    val colors = IgngTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(IgngTheme.radii.sm)
            .background(colors.tile)
            .border(IgngStroke.hairline, colors.lineStrong, IgngTheme.radii.sm)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(18.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = IgngTheme.type.body,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = IgngTheme.type.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(
                    imeAction = if (onSubmit != null) {
                        androidx.compose.ui.text.input.ImeAction.Search
                    } else {
                        androidx.compose.ui.text.input.ImeAction.Done
                    },
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { onSubmit?.invoke() },
                    onDone = { onSubmit?.invoke() },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(
                onClick = {
                    if (onClear != null) onClear() else onValueChange("")
                },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "清空",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
fun IgngTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    isPassword: Boolean = false,
    supportingText: String? = null,
    isError: Boolean = false,
) {
    val colors = IgngTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = IgngTheme.type.caption.copy(fontWeight = FontWeight.Medium),
                color = if (isError) colors.danger else colors.textSecondary,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(IgngTheme.radii.sm)
                .background(colors.tileSoft)
                .border(
                    width = if (isError) IgngStroke.focus else IgngStroke.hairline,
                    color = if (isError) colors.danger else colors.lineStrong,
                    shape = IgngTheme.radii.sm,
                )
                .padding(horizontal = 12.dp, vertical = 11.dp),
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    style = IgngTheme.type.body,
                    color = colors.textMuted,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                minLines = minLines,
                textStyle = IgngTheme.type.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                visualTransformation = if (isPassword) {
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                } else {
                    androidx.compose.ui.text.input.VisualTransformation.None
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!supportingText.isNullOrBlank()) {
            Text(
                text = supportingText,
                style = IgngTheme.type.meta,
                color = if (isError) colors.danger else colors.textSecondary,
            )
        }
    }
}

/**
 * Calm skeleton rows per `docs/console-design.md`: no flashy gradient sweep,
 * only gentle low-amplitude opacity breathing inside a single grouped container.
 */
@Composable
fun IgngSkeletonRows(
    count: Int = 4,
    modifier: Modifier = Modifier,
) {
    val colors = IgngTheme.colors
    val reduced = IgngTheme.motionReduced
    val alpha = if (reduced) {
        0.55f
    } else {
        val transition = rememberInfiniteTransition(label = "skeletonPulse")
        transition.animateFloat(
            initialValue = 0.42f,
            targetValue = 0.78f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100, easing = IgngMotion.LoadEase),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "skeletonAlpha",
        ).value
    }

    IgngGroupedSection(modifier = modifier) {
        repeat(count) { idx ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .height(14.dp)
                        .clip(IgngTheme.radii.xs)
                        .background(colors.lineStrong.scaleAlpha(alpha)),
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(11.dp)
                        .clip(IgngTheme.radii.xs)
                        .background(colors.line.scaleAlpha(alpha)),
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .height(11.dp)
                        .clip(IgngTheme.radii.xs)
                        .background(colors.line.scaleAlpha(alpha)),
                )
            }
            if (idx < count - 1) {
                IgngHairlineDivider(startIndent = 16.dp, endIndent = 16.dp)
            }
        }
    }
}

@Composable
fun IgngStateBlock(
    title: String,
    description: String? = null,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = IgngTheme.colors
    IgngTile(
        modifier = modifier.fillMaxWidth(),
        borderColor = if (isError) colors.danger.copy(alpha = 0.30f) else colors.line,
        contentPadding = PaddingValues(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(IgngTheme.radii.md)
                    .background(if (isError) colors.dangerBg else colors.tileSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.Inbox,
                    contentDescription = null,
                    tint = if (isError) colors.danger else colors.textSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = title,
                style = IgngTheme.type.cardTitle,
                color = colors.textPrimary,
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = IgngTheme.type.caption,
                    color = colors.textSecondary,
                )
            }
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                Spacer(Modifier.height(4.dp))
                IgngButton(
                    text = actionLabel,
                    onClick = onAction,
                    style = if (isError) IgngButtonStyle.Primary else IgngButtonStyle.Secondary,
                    icon = if (isError) Icons.Rounded.Refresh else null,
                    compact = true,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IgngDetailSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    subtitle: String? = null,
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = IgngTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.tile,
        contentColor = colors.textPrimary,
        scrimColor = colors.scrim,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        val scrollMod = if (scrollable) {
            Modifier.verticalScroll(rememberScrollState())
        } else {
            Modifier
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .then(scrollMod)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            if (!title.isNullOrBlank()) {
                Text(text = title, style = IgngTheme.type.subpageTitle, color = colors.textPrimary)
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(text = subtitle, style = IgngTheme.type.caption, color = colors.textSecondary)
                }
                Spacer(Modifier.height(16.dp))
            }
            content()
        }
    }
}

@Composable
fun IgngConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
) {
    val colors = IgngTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.tile,
        shape = IgngTheme.radii.lg,
        title = { Text(title, style = IgngTheme.type.sectionTitle, color = colors.textPrimary) },
        text = { Text(message, style = IgngTheme.type.body, color = colors.textSecondary) },
        confirmButton = {
            IgngButton(
                text = confirmLabel,
                onClick = onConfirm,
                style = if (danger) IgngButtonStyle.Danger else IgngButtonStyle.Primary,
                compact = true,
            )
        },
        dismissButton = {
            IgngButton(
                text = "取消",
                onClick = onDismiss,
                style = IgngButtonStyle.Secondary,
                compact = true,
            )
        },
    )
}

@Composable
fun IgngAvatar(
    name: String,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val colors = IgngTheme.colors
    val intensity = IgngTheme.motionIntensity
    val reduced = IgngTheme.motionReduced
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    var loaded by remember(avatarUrl) { mutableStateOf(false) }
    var fromMemory by remember(avatarUrl) { mutableStateOf(false) }
    val avatarAlpha by animateFloatAsState(
        targetValue = if (loaded) 1f else 0f,
        animationSpec = if (fromMemory || reduced || !loaded) {
            androidx.compose.animation.core.snap()
        } else {
            IgngMotion.microTween(intensity)
        },
        label = "igngAvatarAlpha",
    )
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.accentSoft)
            .border(IgngStroke.hairline, colors.line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = if (size <= 26.dp) IgngTheme.type.badge else IgngTheme.type.cardTitle,
            color = colors.accent,
        )
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                onState = { state ->
                    when (state) {
                        is AsyncImagePainter.State.Success -> {
                            fromMemory = state.result.dataSource == DataSource.MEMORY_CACHE ||
                                state.result.dataSource == DataSource.MEMORY
                            loaded = true
                        }
                        is AsyncImagePainter.State.Error -> {
                            fromMemory = false
                            loaded = false
                        }
                        else -> Unit
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = avatarAlpha },
            )
        }
    }
}

// =============================================================================
// Unified Quiet Craft convenience components & overloads (shared across all apps)
// =============================================================================

@Composable
fun IgngTile(
    modifier: Modifier = Modifier,
    soft: Boolean = false,
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onClick != null) {
        IgngInteractiveTile(
            onClick = onClick,
            modifier = modifier,
            shape = IgngTheme.radii.lg,
            soft = soft,
            contentPadding = padding,
            content = content,
        )
    } else {
        IgngTile(
            modifier = modifier,
            shape = IgngTheme.radii.lg,
            soft = soft,
            contentPadding = padding,
            content = content,
        )
    }
}

enum class IgngTone {
    NEUTRAL,
    ACCENT,
    SUCCESS,
    WARNING,
    DANGER,
}

/**
 * Quiet Craft chip: crisp 8dp rounded rect (not inflated 999dp pill), subtle hairline border,
 * and calm tonal hierarchy for filter bars and lightweight actions.
 */
@Composable
fun IgngTagChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: IgngTone = IgngTone.NEUTRAL,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = IgngTheme.colors
    val intensity = IgngTheme.motionIntensity
    val haptic = rememberIgngHaptics()
    val targetBg = when {
        selected && tone == IgngTone.DANGER -> colors.danger
        selected && tone == IgngTone.WARNING -> colors.warning
        selected && tone == IgngTone.SUCCESS -> colors.success
        selected -> colors.accent
        tone == IgngTone.ACCENT -> colors.accentLow
        tone == IgngTone.SUCCESS -> colors.successLow
        tone == IgngTone.WARNING -> colors.warningLow
        tone == IgngTone.DANGER -> colors.dangerLow
        else -> colors.tileSoft
    }
    val targetFg = when {
        selected -> colors.onAccent
        tone == IgngTone.ACCENT -> colors.accent
        tone == IgngTone.SUCCESS -> colors.success
        tone == IgngTone.WARNING -> colors.warning
        tone == IgngTone.DANGER -> colors.danger
        else -> colors.textSecondary
    }
    val bg by animateColorAsState(targetBg, IgngMotion.microTween(intensity), label = "chipBg")
    val fg by animateColorAsState(targetFg, IgngMotion.microTween(intensity), label = "chipFg")
    val shape = RoundedCornerShape(8.dp)
    val needsBorder = !selected && tone == IgngTone.NEUTRAL
    val clickMod = if (onClick != null) {
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {
                haptic(IgngHapticKind.Tick)
                onClick()
            },
        )
    } else {
        modifier
    }

    Box(
        modifier = clickMod
            .clip(shape)
            .background(bg)
            .then(
                if (needsBorder) Modifier.border(IgngStroke.hairline, colors.line, shape) else Modifier,
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = IgngTheme.type.badge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun IgngAvatar(
    avatarUrl: String?,
    displayName: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    onClick: (() -> Unit)? = null,
) {
    val mod = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }
    IgngAvatar(
        name = displayName,
        avatarUrl = avatarUrl,
        modifier = mod,
        size = size,
    )
}

@Composable
fun IgngPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) = IgngButton(
    text = text,
    onClick = onClick,
    modifier = modifier,
    style = IgngButtonStyle.Primary,
    icon = icon,
    enabled = enabled,
    loading = loading,
)

@Composable
fun IgngSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) = IgngButton(
    text = text,
    onClick = onClick,
    modifier = modifier,
    style = IgngButtonStyle.Secondary,
    icon = icon,
    enabled = enabled,
    loading = loading,
)

@Composable
fun IgngErrorTile(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "暂时无法获取数据",
) = IgngStateBlock(
    title = title,
    description = message,
    isError = true,
    actionLabel = "重新加载",
    onAction = onRetry,
    modifier = modifier,
)

@Composable
fun IgngEmptyTile(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) = IgngStateBlock(
    title = title,
    description = subtitle,
    isError = false,
    actionLabel = actionLabel,
    onAction = onAction,
    modifier = modifier,
)

@Composable
fun IgngStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    tone: IgngTone = IgngTone.NEUTRAL,
) {
    val colors = IgngTheme.colors
    val accentColor = when (tone) {
        IgngTone.ACCENT -> colors.accent
        IgngTone.SUCCESS -> colors.success
        IgngTone.WARNING -> colors.warning
        IgngTone.DANGER -> colors.danger
        IgngTone.NEUTRAL -> colors.textPrimary
    }
    IgngTile(modifier = modifier, padding = PaddingValues(14.dp)) {
        Text(
            text = label,
            style = IgngTheme.type.meta,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = IgngTheme.type.metricLarge,
            color = accentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!hint.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = hint,
                style = IgngTheme.type.meta,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun IgngInfoBanner(
    title: String,
    message: String,
    tone: IgngTone = IgngTone.ACCENT,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = IgngTheme.colors
    val (bg, fg) = when (tone) {
        IgngTone.ACCENT -> colors.accentLow to colors.accent
        IgngTone.SUCCESS -> colors.successLow to colors.success
        IgngTone.WARNING -> colors.warningLow to colors.warning
        IgngTone.DANGER -> colors.dangerLow to colors.danger
        IgngTone.NEUTRAL -> colors.tileSoft to colors.textPrimary
    }
    val shape = IgngTheme.radii.md
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = bg,
        border = BorderStroke(IgngStroke.hairline, fg.copy(alpha = 0.22f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = title, style = IgngTheme.type.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = fg)
            Spacer(Modifier.height(4.dp))
            Text(text = message, style = IgngTheme.type.caption, color = colors.textPrimary)
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = actionLabel,
                    style = IgngTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = fg,
                    modifier = Modifier
                        .clip(IgngTheme.radii.xs)
                        .clickable(onClick = onAction)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
    }
}
