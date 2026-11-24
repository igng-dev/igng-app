package net.igng.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IgngScrollbarMetricsTest {

    @Test
    fun emptyOrFittingContentIsNotScrollable() {
        val empty = computeLazyListScrollMetrics(
            totalItemsCount = 0,
            viewportStartOffset = 0,
            viewportEndOffset = 1000,
            visibleItems = emptyList(),
        )
        assertFalse(empty.scrollable)
        assertEquals(0f, empty.progress, 0.0001f)

        val fits = computeLazyListScrollMetrics(
            totalItemsCount = 2,
            viewportStartOffset = 0,
            viewportEndOffset = 1000,
            visibleItems = listOf(
                IgngVisibleItemSnapshot(index = 0, offset = 0, size = 300),
                IgngVisibleItemSnapshot(index = 1, offset = 300, size = 400),
            ),
        )
        assertFalse(fits.scrollable)
        assertEquals(1f, fits.progress, 0.0001f)
    }

    @Test
    fun longListClampsExactTopAndBottomAndIncreasesMonotonically() {
        val atTop = computeLazyListScrollMetrics(
            totalItemsCount = 20,
            viewportStartOffset = 0,
            viewportEndOffset = 1000,
            visibleItems = listOf(
                IgngVisibleItemSnapshot(index = 0, offset = 0, size = 250),
                IgngVisibleItemSnapshot(index = 1, offset = 250, size = 250),
                IgngVisibleItemSnapshot(index = 2, offset = 500, size = 250),
                IgngVisibleItemSnapshot(index = 3, offset = 750, size = 250),
            ),
        )
        assertTrue(atTop.scrollable)
        assertEquals(0f, atTop.progress, 0.0001f)
        assertEquals(0.2f, atTop.thumbFraction, 0.01f)

        val midEarly = computeLazyListScrollMetrics(
            totalItemsCount = 20,
            viewportStartOffset = 0,
            viewportEndOffset = 1000,
            visibleItems = listOf(
                IgngVisibleItemSnapshot(index = 5, offset = -125, size = 250),
                IgngVisibleItemSnapshot(index = 6, offset = 125, size = 250),
                IgngVisibleItemSnapshot(index = 7, offset = 375, size = 250),
                IgngVisibleItemSnapshot(index = 8, offset = 625, size = 250),
                IgngVisibleItemSnapshot(index = 9, offset = 875, size = 250),
            ),
        )
        val midLate = computeLazyListScrollMetrics(
            totalItemsCount = 20,
            viewportStartOffset = 0,
            viewportEndOffset = 1000,
            visibleItems = listOf(
                IgngVisibleItemSnapshot(index = 12, offset = 0, size = 250),
                IgngVisibleItemSnapshot(index = 13, offset = 250, size = 250),
                IgngVisibleItemSnapshot(index = 14, offset = 500, size = 250),
                IgngVisibleItemSnapshot(index = 15, offset = 750, size = 250),
            ),
        )
        assertTrue(midEarly.progress > 0f && midEarly.progress < midLate.progress)
        assertTrue(midLate.progress < 1f)

        val atBottom = computeLazyListScrollMetrics(
            totalItemsCount = 20,
            viewportStartOffset = 0,
            viewportEndOffset = 1000,
            visibleItems = listOf(
                IgngVisibleItemSnapshot(index = 16, offset = 0, size = 250),
                IgngVisibleItemSnapshot(index = 17, offset = 250, size = 250),
                IgngVisibleItemSnapshot(index = 18, offset = 500, size = 250),
                IgngVisibleItemSnapshot(index = 19, offset = 750, size = 250),
            ),
        )
        assertEquals(1f, atBottom.progress, 0.0001f)
    }

    @Test
    fun allItemsVisibleButOverflowingViewportComputesExactPixelRatio() {
        val halfScrolled = computeLazyListScrollMetrics(
            totalItemsCount = 2,
            viewportStartOffset = 0,
            viewportEndOffset = 1000,
            visibleItems = listOf(
                IgngVisibleItemSnapshot(index = 0, offset = -250, size = 750),
                IgngVisibleItemSnapshot(index = 1, offset = 500, size = 750),
            ),
        )
        assertTrue(halfScrolled.scrollable)
        assertEquals(0.5f, halfScrolled.progress, 0.0001f)
    }
}
