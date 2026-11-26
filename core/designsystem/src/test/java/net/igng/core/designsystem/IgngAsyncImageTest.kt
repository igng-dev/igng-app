package net.igng.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IgngAsyncImageTest {

    @Before
    fun setUp() {
        IgngImageAspectRatioCache.clear()
    }

    @Test
    fun aspectRatioCacheStoresSanitizesAndEvictsLruEntries() {
        assertNull(IgngImageAspectRatioCache.get(null))
        assertNull(IgngImageAspectRatioCache.get("   "))
        assertNull(IgngImageAspectRatioCache.sanitizeContentRatio(0, 100))
        assertNull(IgngImageAspectRatioCache.sanitizeContentRatio(100, -1))
        assertNull(IgngImageAspectRatioCache.sanitizeContentRatio(Float.NaN))
        assertNull(IgngImageAspectRatioCache.sanitizeContentRatio(Float.POSITIVE_INFINITY))

        val landscape = IgngImageAspectRatioCache.sanitizeContentRatio(1600, 900)!!
        assertEquals(1600f / 900f, landscape, 0.0001f)
        IgngImageAspectRatioCache.put("https://www.igng.net/api/v1/media/med_a/content", landscape)
        assertEquals(landscape, IgngImageAspectRatioCache.get("https://www.igng.net/api/v1/media/med_a/content")!!, 0.0001f)

        // Clamping extreme ratios keeps layout safe
        assertEquals(
            IgngImageAspectRatioCache.MIN_CONTENT_RATIO,
            IgngImageAspectRatioCache.sanitizeContentRatio(100, 5000)!!,
            0.0001f,
        )
        assertEquals(
            IgngImageAspectRatioCache.MAX_CONTENT_RATIO,
            IgngImageAspectRatioCache.sanitizeContentRatio(9000, 100)!!,
            0.0001f,
        )

        // LRU eviction capped at 256 entries
        for (i in 0 until 270) {
            IgngImageAspectRatioCache.put("https://img.example/$i.webp", 1.5f)
        }
        assertNull(IgngImageAspectRatioCache.get("https://img.example/0.webp"))
        assertEquals(1.5f, IgngImageAspectRatioCache.get("https://img.example/269.webp")!!, 0.0001f)
    }

    @Test
    fun placeholderRatioResolutionPrefersExplicitThenCachedThenDefault() {
        val explicit = IgngImageAspectRatioCache.resolvePlaceholderRatio(
            explicitRatio = 4f / 3f,
            cachedRatio = 0.75f,
        )
        assertEquals(4f / 3f, explicit, 0.0001f)

        val cached = IgngImageAspectRatioCache.resolvePlaceholderRatio(
            explicitRatio = null,
            cachedRatio = 0.75f,
        )
        assertEquals(0.75f, cached, 0.0001f)

        val fallback = IgngImageAspectRatioCache.resolvePlaceholderRatio(
            explicitRatio = null,
            cachedRatio = null,
        )
        assertEquals(IgngImageAspectRatioCache.DEFAULT_CONTENT_RATIO, fallback, 0.0001f)

        // Extreme tall/wide hints are clamped for placeholder sanity
        val tallClamped = IgngImageAspectRatioCache.resolvePlaceholderRatio(
            explicitRatio = 0.1f,
            cachedRatio = null,
        )
        assertEquals(IgngImageAspectRatioCache.MIN_KNOWN_PLACEHOLDER_RATIO, tallClamped, 0.0001f)
    }

    @Test
    fun placeholderPaletteStaysDistinctFromCanvasAcrossAllFourReadingSurfaces() {
        val warmLight = IgngLightColors.copy(
            canvas = IgngLightColors.warmPaperCanvas,
            textPrimary = IgngLightColors.warmPaperText,
            textSecondary = IgngLightColors.warmPaperSecondary,
            tile = Color(0xFFFFFDF8),
            tileSoft = Color(0xFFF5EFE3),
            codeBackground = Color(0xFFF0E8DA),
        )
        val warmDark = IgngDarkColors.copy(
            canvas = IgngDarkColors.warmPaperCanvas,
            textPrimary = IgngDarkColors.warmPaperText,
            textSecondary = IgngDarkColors.warmPaperSecondary,
            tile = Color(0xFF211C16),
            tileSoft = Color(0xFF28221A),
            codeBackground = Color(0xFF1A1611),
        )

        listOf(
            "light" to IgngLightColors,
            "dark" to IgngDarkColors,
            "warmLight" to warmLight,
            "warmDark" to warmDark,
        ).forEach { (label, surface) ->
            val palette = resolveIgngImagePlaceholderPalette(surface)
            assertNotEquals("$label placeholder base must contrast with canvas", surface.canvas, palette.base)
            assertTrue("$label pulse alpha bounds must stay restrained", palette.pulseMinAlpha < palette.pulseMaxAlpha)
            assertTrue("$label border must remain subtle (< 0.20 alpha)", palette.border.alpha < 0.20f)
        }

        // Warm paper must use warm parchment tone rather than cool gray tilePressed
        val warmPalette = resolveIgngImagePlaceholderPalette(warmLight)
        assertEquals(warmLight.codeBackground, warmPalette.base)
        assertNotEquals(warmLight.tilePressed, warmPalette.base)
    }
}
