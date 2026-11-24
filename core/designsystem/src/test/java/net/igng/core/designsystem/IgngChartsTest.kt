package net.igng.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IgngChartsTest {

    @Test
    fun aggregateHealthBucketsReducesDenseBucketsWithWorstSeverityWins() {
        val raw = List(144) { idx ->
            val level = when (idx) {
                10 -> HealthBucketLevel.Degraded
                75 -> HealthBucketLevel.Down
                else -> HealthBucketLevel.Healthy
            }
            HealthBucketItem(
                level = level,
                title = "11-24 %02d:%02d".format(idx / 12, (idx % 12) * 5),
                detail = if (level == HealthBucketLevel.Healthy) "正常 · 24ms" else "异常",
            )
        }

        val aggregated = aggregateHealthBuckets(raw, targetCount = 36)
        assertEquals(36, aggregated.size)
        // Bucket covering index 10 (10 * 36 / 144 = 2) must preserve Degraded
        assertEquals(HealthBucketLevel.Degraded, aggregated[2].level)
        // Bucket covering index 75 (75 * 36 / 144 = 18) must preserve Down
        assertEquals(HealthBucketLevel.Down, aggregated[18].level)
        // Healthy ranges stay Healthy and merge time span
        assertEquals(HealthBucketLevel.Healthy, aggregated[0].level)
        assertTrue(aggregated[0].title.contains("–"))
    }

    @Test
    fun aggregateHealthBucketsReturnsOriginalWhenAlreadyCompact() {
        val raw = List(12) { idx ->
            HealthBucketItem(HealthBucketLevel.Healthy, "t$idx", "正常")
        }
        assertEquals(12, aggregateHealthBuckets(raw, targetCount = 36).size)
    }

    @Test
    fun computeHealthAvailabilityExcludesNoData() {
        assertNull(computeHealthAvailabilityPercent(listOf(HealthBucketItem(HealthBucketLevel.NoData, "t", "无数据"))))
        val mixed = listOf(
            HealthBucketItem(HealthBucketLevel.Healthy, "t1", "正常"),
            HealthBucketItem(HealthBucketLevel.Healthy, "t2", "正常"),
            HealthBucketItem(HealthBucketLevel.Healthy, "t3", "正常"),
            HealthBucketItem(HealthBucketLevel.Down, "t4", "异常"),
            HealthBucketItem(HealthBucketLevel.NoData, "t5", "无数据"),
        )
        assertEquals(75.0, computeHealthAvailabilityPercent(mixed) ?: 0.0, 0.001)
    }

    @Test
    fun resolveChartStrokeWidthThinsOutForDenseTelemetrySeries() {
        assertEquals(1.65f, resolveChartStrokeWidthDp(14), 0.001f)
        assertEquals(1.4f, resolveChartStrokeWidthDp(60), 0.001f)
        assertEquals(1.2f, resolveChartStrokeWidthDp(289), 0.001f)
        assertEquals(1.8f, resolveChartStrokeWidthDp(289, overrideDp = 1.8f), 0.001f)
    }
}
