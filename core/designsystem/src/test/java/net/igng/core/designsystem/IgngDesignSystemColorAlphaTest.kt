package net.igng.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IgngDesignSystemColorAlphaTest {

    // Compose sRGB Color quantizes alpha into 8-bit steps (1/255 ≈ 0.00392)
    private val srgbAlphaDelta = 1f / 255f

    @Test
    fun scaleAlphaMultipliesExistingAlphaInsteadOfOverwriting() {
        val darkLine = IgngDarkColors.line
        val lightLine = IgngLightColors.line
        val darkLineStrong = IgngDarkColors.lineStrong
        val lightLineStrong = IgngLightColors.lineStrong

        assertTrue(darkLine.alpha < 0.15f)
        assertTrue(lightLine.alpha < 0.15f)

        assertEquals(darkLine.alpha, darkLine.scaleAlpha(1f).alpha, srgbAlphaDelta)
        assertEquals(lightLine.alpha, lightLine.scaleAlpha(1f).alpha, srgbAlphaDelta)
        assertEquals(darkLine.alpha * 0.5f, darkLine.scaleAlpha(0.5f).alpha, srgbAlphaDelta)
        assertEquals(darkLineStrong.alpha * 0.78f, darkLineStrong.scaleAlpha(0.78f).alpha, srgbAlphaDelta)
        assertEquals(lightLineStrong.alpha * 0.42f, lightLineStrong.scaleAlpha(0.42f).alpha, srgbAlphaDelta)

        // Clamps out-of-range factors safely
        assertEquals(0f, darkLine.scaleAlpha(-0.5f).alpha, srgbAlphaDelta)
        assertEquals(darkLine.alpha, darkLine.scaleAlpha(2.0f).alpha, srgbAlphaDelta)

        // Opaque colors preserve exact factor
        val opaque = Color(0xFF58A6FF)
        assertEquals(0.72f, opaque.scaleAlpha(0.72f).alpha, srgbAlphaDelta)
    }

    @Test
    fun topRefreshIdleDividerNeverBlowsOutToOpaqueWhiteOrBlack() {
        val darkIdle = resolveTopRefreshIdleDividerColor(
            trackColor = IgngDarkColors.line,
            barAlpha = 0f,
        )
        val lightIdle = resolveTopRefreshIdleDividerColor(
            trackColor = IgngLightColors.line,
            barAlpha = 0f,
        )

        assertEquals(IgngDarkColors.line.alpha, darkIdle.alpha, srgbAlphaDelta)
        assertEquals(IgngLightColors.line.alpha, lightIdle.alpha, srgbAlphaDelta)
        assertTrue("Dark idle divider must remain subtle, not opaque white", darkIdle.alpha < 0.15f)
        assertTrue("Light idle divider must remain subtle, not opaque black", lightIdle.alpha < 0.15f)

        val midFade = resolveTopRefreshIdleDividerColor(
            trackColor = IgngDarkColors.line,
            barAlpha = 0.5f,
        )
        assertEquals(IgngDarkColors.line.alpha * 0.5f, midFade.alpha, srgbAlphaDelta)

        val fullyActive = resolveTopRefreshIdleDividerColor(
            trackColor = IgngDarkColors.line,
            barAlpha = 1f,
        )
        assertEquals(0f, fullyActive.alpha, srgbAlphaDelta)
    }
}
