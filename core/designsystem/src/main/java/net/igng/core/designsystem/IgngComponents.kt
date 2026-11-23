package net.igng.core.designsystem
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * Subtle ambient radial blue/violet background matching `packages/theme/globals.css`.
 */
@Composable
fun IgngAmbientCanvas(
    modifier: Modifier = Modifier,
    accentOverride: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = IgngTheme.colors
    val intensity = IgngTheme.motionIntensity
    val topLeftColor by animateColorAsState(
        targetValue = (accentOverride ?: colors.accent).copy(alpha = if (colors.isDark) 0.11f else 0.06f),
        animationSpec = IgngMotion.ambientTween(intensity),
        label = "ambientTopGlow",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(topLeftColor, Color.Transparent),
                    center = Offset(size.width * 0.12f, size.height * 0.08f),
                    radius = size.maxDimension * 0.55f,
                ),
                center = Offset(size.width * 0.12f, size.height * 0.08f),
                radius = size.maxDimension * 0.55f,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.auraViolet, Color.Transparent),
                    center = Offset(size.width * 0.9f, size.height * 0.88f),
                    radius = size.maxDimension * 0.5f,
                ),
                center = Offset(size.width * 0.9f, size.height * 0.88f),
                radius = size.maxDimension * 0.5f,
            )
        }
        content()
    }
}


/**
 * Standard Bento 2.0 Tile card: crisp 1dp border, clean surface, zero visual noise.
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
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = if (colors.isDark) 0.dp else 0.5.dp,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

/**
 * Interactive Bento Tile with press micro-scale, subtle accent border highlight,
 * and haptic tap feedback — the mobile counterpart of IGNG Web's `AnimatedBlock`.
 */
@Composable
fun IgngInteractiveTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = IgngTheme.radii.lg,
    soft: Boolean = false,
    accentBorder: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = IgngTheme.colors
    val intensity = IgngTheme.motionIntensity
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptic = rememberIgngHaptics()

    val border by animateColorAsState(
        targetValue = when {
            pressed -> (accentBorder ?: colors.accent).copy(alpha = 0.55f)
            accentBorder != null -> accentBorder.copy(alpha = 0.28f)
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

    Surface(
        modifier = modifier
            .igngPressScale(interactionSource)
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
        border = BorderStroke(1.dp, border),
        shadowElevation = if (colors.isDark) 0.dp else 0.5.dp,
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
        IgngButtonStyle.Secondary -> BorderStroke(1.dp, colors.lineStrong)
        IgngButtonStyle.Danger -> BorderStroke(1.dp, colors.danger.copy(alpha = 0.35f))
        else -> null
    }

    val effectiveAlpha = if (enabled) 1f else 0.45f
    Surface(
        modifier = modifier
            .igngPressScale(interactionSource, pressedScale = 0.97f)
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
        customColor != null -> customColor.copy(alpha = if (colors.isDark) 0.18f else 0.11f)
        tone == IgngBadgeTone.Neutral -> colors.tileSoft
        tone == IgngBadgeTone.Accent -> colors.accentSoft
        tone == IgngBadgeTone.Success -> colors.successBg
        tone == IgngBadgeTone.Warning -> colors.warningBg
        else -> colors.dangerBg
    }

    Row(
        modifier = modifier
            .clip(IgngTheme.radii.pill)
            .background(bg)
            .border(1.dp, fg.copy(alpha = if (tone == IgngBadgeTone.Neutral) 0.16f else 0.24f), IgngTheme.radii.pill)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (leadingDot) {
            IgngLivePulseDot(color = fg, active = pulse)
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
    size: Dp = 7.dp,
) {
    val reduced = IgngTheme.motionReduced
    val pulseAlpha = if (active && !reduced) {
        val transition = rememberInfiniteTransition(label = "livePulse")
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.7f,
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
                    .background(color.copy(alpha = pulseAlpha * 0.45f)),
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
 * Interactive copy pill with animated Copy -> Check morph + success glow.
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
        targetValue = if (copied) colors.success else colors.lineStrong,
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
            .igngPressScale(interaction)
            .clip(IgngTheme.radii.sm)
            .background(bg)
            .border(1.dp, border, IgngTheme.radii.sm)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    copyTextToClipboard(context, label ?: "IGNG", value)
                    haptic(IgngHapticKind.Confirm)
                    copied = true
                },
            )
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (copied) "已复制" else (label ?: value),
            style = if (mono && !copied) IgngTheme.type.mono else IgngTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(IgngTheme.radii.md)
            .background(colors.tileSoft)
            .border(1.dp, colors.line, IgngTheme.radii.md)
            .padding(4.dp),
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
                .height(34.dp)
                .clip(IgngTheme.radii.sm)
                .background(colors.tile)
                .border(1.dp, colors.lineStrong, IgngTheme.radii.sm),
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
                        .height(34.dp)
                        .clip(IgngTheme.radii.sm)
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
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
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
    val badgeCount: Int = 0,
)

/**
 * Legacy alias forwarding to unified zero-jitter [IgngFloatingDockBar].
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onBack != null) {
            Surface(
                shape = IgngTheme.radii.sm,
                color = colors.tile,
                border = BorderStroke(1.dp, colors.line),
                modifier = Modifier
                    .size(38.dp)
                    .clip(IgngTheme.radii.sm)
                    .clickable {
                        haptic(IgngHapticKind.Tap)
                        onBack()
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = IgngTheme.type.pageTitle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = IgngTheme.type.caption,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            actions()
        }
    }
}

@Composable
fun IgngSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
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
                style = IgngTheme.type.sectionTitle,
                color = colors.textPrimary,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = IgngTheme.type.caption,
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
                style = MaterialTheme.typography.labelLarge,
                color = colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(IgngRadius.pill))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
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
) {
    val colors = IgngTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(IgngTheme.radii.md)
            .background(colors.tile)
            .border(1.dp, colors.lineStrong, IgngTheme.radii.md)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = colors.textSecondary,
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
                keyboardOptions = KeyboardOptions.Default,
                keyboardActions = KeyboardActions(onDone = { onSubmit?.invoke() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(
                onClick = { onValueChange("") },
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
        Text(
            text = label,
            style = IgngTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = if (isError) colors.danger else colors.textSecondary,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(IgngTheme.radii.sm)
                .background(colors.tileSoft)
                .border(
                    width = 1.dp,
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
                style = IgngTheme.type.caption,
                color = if (isError) colors.danger else colors.textSecondary,
            )
        }
    }
}

/**
 * Calm skeleton rows per `docs/console-design.md`: no flashy gradient sweep,
 * only gentle low-amplitude opacity breathing to preview layout height without CLS.
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

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(count) {
            IgngTile(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .height(14.dp)
                        .clip(IgngTheme.radii.sm)
                        .background(colors.lineStrong.copy(alpha = alpha)),
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(11.dp)
                        .clip(IgngTheme.radii.sm)
                        .background(colors.line.copy(alpha = alpha)),
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .height(11.dp)
                        .clip(IgngTheme.radii.sm)
                        .background(colors.line.copy(alpha = alpha)),
                )
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
        borderColor = if (isError) colors.danger.copy(alpha = 0.3f) else colors.line,
        contentPadding = PaddingValues(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
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
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            if (!title.isNullOrBlank()) {
                Text(text = title, style = IgngTheme.type.pageTitle, color = colors.textPrimary)
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
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.accentSoft)
            .border(1.dp, colors.lineStrong, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = IgngTheme.type.cardTitle,
            color = colors.accent,
        )
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// =============================================================================
// Unified Bento 2.0 convenience components & overloads (shared across BBS / MC / Ink)
// =============================================================================

@Composable
fun IgngTile(
    modifier: Modifier = Modifier,
    soft: Boolean = false,
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = IgngTheme.colors
    val shape = RoundedCornerShape(IgngRadius.tile)
    val baseModifier = if (onClick != null) {
        modifier.igngPressScale(onClick = onClick)
    } else {
        modifier
    }
    Column(
        modifier = baseModifier
            .clip(shape)
            .background(if (soft) colors.tileSoft else colors.tile)
            .border(1.dp, colors.line, shape)
            .padding(padding),
        content = content,
    )
}

enum class IgngTone {
    NEUTRAL,
    ACCENT,
    SUCCESS,
    WARNING,
    DANGER,
}

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
    val targetBg = when {
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
    val bg by animateColorAsState(targetBg, IgngMotion.easeTween(160, intensity), label = "chipBg")
    val fg by animateColorAsState(targetFg, IgngMotion.easeTween(160, intensity), label = "chipFg")
    val shape = RoundedCornerShape(IgngRadius.pill)
    val mod = if (onClick != null) modifier.igngPressScale(haptic = true, onClick = onClick) else modifier

    Box(
        modifier = mod
            .clip(shape)
            .background(bg)
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else colors.line,
                shape = shape,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
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
    val colors = IgngTheme.colors
    val initial = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "I"
    val mod = (if (onClick != null) modifier.igngPressScale(onClick = onClick) else modifier)
        .size(size)
        .clip(CircleShape)
        .background(colors.accentLow)
        .border(1.dp, colors.line, CircleShape)

    Box(modifier = mod, contentAlignment = Alignment.Center) {
        Text(
            text = initial,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = colors.accent,
        )
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
fun IgngPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = IgngTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(IgngRadius.control),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = colors.onAccent,
            disabledContainerColor = colors.lineStrong,
            disabledContentColor = colors.textMuted,
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun IgngSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = IgngTheme.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(IgngRadius.control),
        border = BorderStroke(1.dp, colors.lineStrong),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.tile,
            contentColor = colors.textPrimary,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun IgngErrorTile(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "暂时无法获取数据",
) {
    val colors = IgngTheme.colors
    IgngTile(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = colors.warning,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(14.dp))
        IgngSecondaryButton(
            text = "重新加载",
            icon = Icons.Rounded.Refresh,
            onClick = onRetry,
        )
    }
}

@Composable
fun IgngEmptyTile(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = IgngTheme.colors
    IgngTile(modifier = modifier.fillMaxWidth(), soft = true) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            Spacer(Modifier.height(14.dp))
            IgngPrimaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

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
            style = MaterialTheme.typography.labelMedium,
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
                style = MaterialTheme.typography.bodySmall,
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
    val shape = RoundedCornerShape(IgngRadius.card)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.25f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = fg)
            Spacer(Modifier.height(4.dp))
            Text(text = message, style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = fg,
                    modifier = Modifier
                        .clip(RoundedCornerShape(IgngRadius.pill))
                        .clickable(onClick = onAction)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}
