package net.igng.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.igng.core.model.MotionIntensity
import net.igng.core.model.ReaderCanvasTone
import net.igng.core.model.ReaderTypographyPrefs
import net.igng.core.model.ThemeMode

/**
 * Standard edge-to-edge list content padding for secondary screens without the floating dock.
 * Always reserves clearance above the system gesture / 3-button navigation bar.
 */
@Composable
fun rememberIgngPageContentPadding(
    horizontal: Dp = 16.dp,
    top: Dp = 16.dp,
    bottom: Dp = 24.dp,
): PaddingValues {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(
        start = horizontal,
        end = horizontal,
        top = top,
        bottom = bottom + navBottom,
    )
}

/**
 * Unified Bento 2.0 switch control using IGNG semantic tokens (zero M3 purple bleed)
 * and consistent Tick haptic feedback.
 */
@Composable
fun IgngSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = IgngTheme.colors
    val haptic = rememberIgngHaptics()
    Switch(
        checked = checked,
        enabled = enabled,
        onCheckedChange = { next ->
            haptic(IgngHapticKind.Tick)
            onCheckedChange(next)
        },
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.onAccent,
            checkedTrackColor = colors.accent,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = colors.textSecondary,
            uncheckedTrackColor = colors.tileSoft,
            uncheckedBorderColor = colors.lineStrong,
        ),
    )
}

/**
 * Unified Bento 2.0 switch row with full-row tap target, IGNG color tokens (no M3 purple),
 * and consistent Tick haptic feedback.
 */
@Composable
fun IgngSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val colors = IgngTheme.colors
    val haptic = rememberIgngHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(IgngTheme.radii.sm)
            .clickable(enabled = enabled, role = Role.Switch) {
                haptic(IgngHapticKind.Tick)
                onCheckedChange(!checked)
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = IgngTheme.type.bodyMedium,
                color = if (enabled) colors.textPrimary else colors.textMuted,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = IgngTheme.type.caption,
                    color = colors.textSecondary,
                )
            }
        }
        IgngSwitch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

/**
 * Unified navigation/action row tile used in Profile / Player Center / Settings hubs.
 */
@Composable
fun IgngNavRowTile(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = IgngTheme.colors.accent,
    trailing: (@Composable () -> Unit)? = null,
    accentBorder: Color? = null,
) {
    val colors = IgngTheme.colors
    IgngInteractiveTile(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        accentBorder = accentBorder,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(21.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = IgngTheme.type.cardTitle, color = colors.textPrimary)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = IgngTheme.type.caption, color = colors.textSecondary)
                }
            }
            if (trailing != null) {
                trailing()
            } else {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Unified appearance, 3-tier motion, and haptics settings card shared by BBS, MC, and Ink.
 */
@Composable
fun IgngAppearanceMotionSettingsCard(
    themeMode: ThemeMode,
    motionIntensity: MotionIntensity,
    hapticsEnabled: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onMotionIntensityChange: (MotionIntensity) -> Unit,
    onHapticsEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    readerTypography: ReaderTypographyPrefs? = null,
    onReaderToneChange: ((ReaderCanvasTone) -> Unit)? = null,
) {
    val colors = IgngTheme.colors
    IgngTile(modifier = modifier.fillMaxWidth()) {
        IgngSectionHeader(
            title = "画布、动效与触感",
            subtitle = "IGNG 全系应用统一的主题、三档物理动效与微触感反馈",
        )
        Spacer(Modifier.height(14.dp))

        Text("界面主题", style = IgngTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.textSecondary)
        Spacer(Modifier.height(6.dp))
        IgngSegmentedTabs(
            items = ThemeMode.entries,
            selected = themeMode,
            labelOf = { it.label },
            onSelect = onThemeModeChange,
        )

        Spacer(Modifier.height(14.dp))
        Text("动效强度", style = IgngTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.textSecondary)
        Spacer(Modifier.height(6.dp))
        IgngSegmentedTabs(
            items = MotionIntensity.entries,
            selected = motionIntensity,
            labelOf = { it.label },
            onSelect = onMotionIntensityChange,
        )

        if (readerTypography != null && onReaderToneChange != null) {
            Spacer(Modifier.height(14.dp))
            Text("阅读画布", style = IgngTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.textSecondary)
            Spacer(Modifier.height(6.dp))
            IgngSegmentedTabs(
                items = ReaderCanvasTone.entries,
                selected = readerTypography.canvasTone,
                labelOf = {
                    when (it) {
                        ReaderCanvasTone.DEFAULT -> "跟随主题"
                        ReaderCanvasTone.WARM_PAPER -> "暖纸护眼"
                        ReaderCanvasTone.INK_DARK -> "深墨夜间"
                    }
                },
                onSelect = onReaderToneChange,
            )
        }

        Spacer(Modifier.height(10.dp))
        IgngSwitchRow(
            title = "交互触感反馈",
            subtitle = "Dock 切换、分段控件、下拉折角刷新、按钮与复制微震动",
            checked = hapticsEnabled,
            onCheckedChange = onHapticsEnabledChange,
        )
    }
}

/**
 * Shared reader typography & canvas tone bottom sheet used by both BBS PostReader and MC WikiArticle.
 */
@Composable
fun IgngReaderTypographySheet(
    prefs: ReaderTypographyPrefs,
    onUpdate: ((ReaderTypographyPrefs) -> ReaderTypographyPrefs) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = IgngTheme.colors
    IgngDetailSheet(
        onDismiss = onDismiss,
        title = "阅读排版与画布",
        subtitle = "调整正文大小、行距与护眼画布",
        scrollable = true,
    ) {
        Text(
            text = "字号：${prefs.fontSizeSp.toInt()} sp",
            style = IgngTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textSecondary,
        )
        Slider(
            value = prefs.fontSizeSp,
            onValueChange = { v -> onUpdate { it.copy(fontSizeSp = v) } },
            valueRange = 14f..22f,
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.lineStrong,
            ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "行距：${String.format(java.util.Locale.US, "%.2f", prefs.lineHeightMultiplier)}x",
            style = IgngTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textSecondary,
        )
        Slider(
            value = prefs.lineHeightMultiplier,
            onValueChange = { v -> onUpdate { it.copy(lineHeightMultiplier = v) } },
            valueRange = 1.45f..2.0f,
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.lineStrong,
            ),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "阅读画布",
            style = IgngTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(6.dp))
        IgngSegmentedTabs(
            items = ReaderCanvasTone.entries,
            selected = prefs.canvasTone,
            labelOf = {
                when (it) {
                    ReaderCanvasTone.DEFAULT -> "跟随主题"
                    ReaderCanvasTone.WARM_PAPER -> "暖纸护眼"
                    ReaderCanvasTone.INK_DARK -> "深墨夜间"
                }
            },
            onSelect = { tone -> onUpdate { it.copy(canvasTone = tone) } },
        )
        Spacer(Modifier.height(10.dp))
        IgngSwitchRow(
            title = "正文衬线字体",
            subtitle = "长文阅读时使用更柔和的衬线字形",
            checked = prefs.preferSerif,
            onCheckedChange = { checked -> onUpdate { it.copy(preferSerif = checked) } },
        )
    }
}
