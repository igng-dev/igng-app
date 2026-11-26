package net.igng.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.Home
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Quiet Craft shared design-system contract tests.
 * Locks in stroke hairlines, disciplined radii, CJK-friendly typography hierarchy,
 * subtle divider alpha, WCAG contrast, and fixed-slot bottom nav icon pairing.
 */
class IgngDesignContractTest {

    @Test
    fun strokesAndRadiiFollowQuietCraftBounds() {
        assertTrue("Hairline stroke must be sub-dp for high-DPI delicacy", IgngStroke.hairline < 1.dp)
        assertTrue("Hairline stroke must stay positive", IgngStroke.hairline >= 0.5.dp)
        assertTrue("Focus stroke must exceed hairline", IgngStroke.focus > IgngStroke.hairline)

        assertTrue("Tile/card radius must be restrained (<= 16dp)", IgngRadius.card <= 16.dp)
        assertEquals(IgngRadius.tile, IgngRadius.card)
        assertTrue("Hero radius must be restrained (<= 20dp)", IgngRadius.hero <= 20.dp)
        assertTrue("Badge radius must be crisp square-rounded (<= 8dp)", IgngRadius.badge <= 8.dp)
        assertTrue("Control radius must sit between badge and card", IgngRadius.control > IgngRadius.badge && IgngRadius.control < IgngRadius.card)
    }

    @Test
    fun cjkTypographyHierarchyAndLineHeightAreErgonomic() {
        val type = IgngTextStyles()

        // Neutral (non-negative) tracking on CJK headings prevents cramped Chinese strokes
        assertTrue("heroTitle tracking must be >= 0sp", type.heroTitle.letterSpacing >= 0.sp)
        assertTrue("pageTitle tracking must be >= 0sp", type.pageTitle.letterSpacing >= 0.sp)
        assertTrue("subpageTitle tracking must be >= 0sp", type.subpageTitle.letterSpacing >= 0.sp)
        assertTrue("sectionTitle tracking must be >= 0sp", type.sectionTitle.letterSpacing >= 0.sp)
        assertTrue("cardTitle tracking must be >= 0sp", type.cardTitle.letterSpacing >= 0.sp)

        // Clear size ladder: badge < meta <= caption <= sectionLabel < body < cardTitle < subpageTitle < pageTitle < heroTitle
        assertTrue(type.badge.fontSize < type.meta.fontSize)
        assertTrue(type.sectionLabel.fontSize < type.cardTitle.fontSize)
        assertTrue(type.cardTitle.fontSize < type.subpageTitle.fontSize)
        assertTrue(type.subpageTitle.fontSize < type.pageTitle.fontSize)
        assertTrue(type.pageTitle.fontSize < type.heroTitle.fontSize)

        // Generous CJK reading cadence (>= 1.5x body line height)
        val bodyRatio = type.body.lineHeight.value / type.body.fontSize.value
        assertTrue("Body line-height ratio should be >= 1.5x (actual=$bodyRatio)", bodyRatio >= 1.5f)

        // Tabular figures prevent jitter in counters and metadata
        assertEquals("tnum", type.metricLarge.fontFeatureSettings)
        assertEquals("tnum", type.metricMedium.fontFeatureSettings)
        assertEquals("tnum", type.meta.fontFeatureSettings)
        assertEquals("tnum", type.mono.fontFeatureSettings)
    }

    @Test
    fun colorTokensSatisfySubtleBordersAndWcagContrast() {
        listOf("light" to IgngLightColors, "dark" to IgngDarkColors).forEach { (mode, palette) ->
            assertTrue("$mode line alpha must remain subtle (< 0.15)", palette.line.alpha < 0.15f)
            assertTrue("$mode lineStrong must be stronger than line", palette.lineStrong.alpha > palette.line.alpha)

            val primaryOnCanvas = contrastRatio(palette.textPrimary, palette.canvas)
            val primaryOnTile = contrastRatio(palette.textPrimary, palette.tile)
            val secondaryOnTile = contrastRatio(palette.textSecondary, palette.tile)

            assertTrue("$mode textPrimary on canvas must meet WCAG AAA (>= 7.0, actual=$primaryOnCanvas)", primaryOnCanvas >= 7.0)
            assertTrue("$mode textPrimary on tile must meet WCAG AAA (>= 7.0, actual=$primaryOnTile)", primaryOnTile >= 7.0)
            assertTrue("$mode textSecondary on tile must meet WCAG AA (>= 4.5, actual=$secondaryOnTile)", secondaryOnTile >= 4.5)
        }
    }

    @Test
    fun navTabItemSupportsOutlinedAndFilledIconPairing() {
        val paired = IgngNavTabItem(
            id = "home",
            label = "首页",
            icon = Icons.Rounded.Home,
            unselectedIcon = Icons.Outlined.Home,
        )
        assertEquals(Icons.Rounded.Home, paired.icon)
        assertEquals(Icons.Outlined.Home, paired.unselectedIcon)

        val fallback = IgngNavTabItem(
            id = "single",
            label = "单项",
            icon = Icons.Rounded.Home,
        )
        assertEquals(Icons.Rounded.Home, fallback.unselectedIcon)
    }

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(c: Color): Double {
        fun channel(v: Float): Double =
            if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }
}
