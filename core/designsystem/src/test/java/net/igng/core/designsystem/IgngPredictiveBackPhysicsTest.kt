package net.igng.core.designsystem

import net.igng.core.model.MotionIntensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IgngPredictiveBackPhysicsTest {

    private val widthPx = 1080f
    private val heightPx = 2400f
    private val density = 3f

    @Test
    fun leftAndRightSwipesMoveWithThumbAndMirrorSymmetrically() {
        val left = IgngPredictiveBackPhysics.computeCardTransform(
            edge = IgngPredictiveBackEdge.LEFT,
            rawProgress = 0.5f,
            commitProgress = 0f,
            startTouchY = heightPx * 0.5f,
            currentTouchY = heightPx * 0.5f,
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            intensity = MotionIntensity.FULL,
        )
        val right = IgngPredictiveBackPhysics.computeCardTransform(
            edge = IgngPredictiveBackEdge.RIGHT,
            rawProgress = 0.5f,
            commitProgress = 0f,
            startTouchY = heightPx * 0.5f,
            currentTouchY = heightPx * 0.5f,
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            intensity = MotionIntensity.FULL,
        )

        assertTrue("Left swipe should peel rightward", left.translationX > 0f)
        assertTrue("Right swipe should peel leftward", right.translationX < 0f)
        assertEquals(left.translationX, -right.translationX, 0.01f)
        assertEquals(left.scale, right.scale, 0.0001f)
        assertEquals(1f - left.pivotX, right.pivotX, 0.0001f)
        assertEquals(1f, left.alpha, 0.0001f)
        assertEquals(1f, right.alpha, 0.0001f)
        assertEquals(0f, left.rotationZ, 0.001f)
        assertEquals(0f, right.rotationZ, 0.001f)
    }

    @Test
    fun touchHeightProducesPhysicallyCorrectTorqueAndVerticalPivot() {
        val topLeft = IgngPredictiveBackPhysics.computeCardTransform(
            edge = IgngPredictiveBackEdge.LEFT,
            rawProgress = 0.6f,
            commitProgress = 0f,
            startTouchY = heightPx * 0.18f,
            currentTouchY = heightPx * 0.18f,
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            intensity = MotionIntensity.FULL,
        )
        val midLeft = IgngPredictiveBackPhysics.computeCardTransform(
            edge = IgngPredictiveBackEdge.LEFT,
            rawProgress = 0.6f,
            commitProgress = 0f,
            startTouchY = heightPx * 0.50f,
            currentTouchY = heightPx * 0.50f,
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            intensity = MotionIntensity.FULL,
        )
        val bottomLeft = IgngPredictiveBackPhysics.computeCardTransform(
            edge = IgngPredictiveBackEdge.LEFT,
            rawProgress = 0.6f,
            commitProgress = 0f,
            startTouchY = heightPx * 0.82f,
            currentTouchY = heightPx * 0.82f,
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            intensity = MotionIntensity.FULL,
        )
        val topRight = IgngPredictiveBackPhysics.computeCardTransform(
            edge = IgngPredictiveBackEdge.RIGHT,
            rawProgress = 0.6f,
            commitProgress = 0f,
            startTouchY = heightPx * 0.18f,
            currentTouchY = heightPx * 0.18f,
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            intensity = MotionIntensity.FULL,
        )
        val bottomRight = IgngPredictiveBackPhysics.computeCardTransform(
            edge = IgngPredictiveBackEdge.RIGHT,
            rawProgress = 0.6f,
            commitProgress = 0f,
            startTouchY = heightPx * 0.82f,
            currentTouchY = heightPx * 0.82f,
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            intensity = MotionIntensity.FULL,
        )

        // Rigid-body torque signs:
        // Pushing upper-left right => clockwise (+); lower-left right => counter-clockwise (-)
        // Pushing upper-right left => counter-clockwise (-); lower-right left => clockwise (+)
        assertTrue("Top-left should tilt clockwise (+)", topLeft.rotationZ > 0.5f)
        assertTrue("Bottom-left should tilt counter-clockwise (-)", bottomLeft.rotationZ < -0.5f)
        assertEquals(0f, midLeft.rotationZ, 0.001f)
        assertTrue("Top-right should tilt counter-clockwise (-)", topRight.rotationZ < -0.5f)
        assertTrue("Bottom-right should tilt clockwise (+)", bottomRight.rotationZ > 0.5f)

        // Taste-capped amplitude: noticeable yet never dizzying
        assertTrue(topLeft.rotationZ <= 3.0f)
        assertTrue(bottomLeft.rotationZ >= -3.0f)

        // Vertical pivot tracks grip height
        assertTrue(topLeft.pivotY < midLeft.pivotY)
        assertTrue(midLeft.pivotY < bottomLeft.pivotY)
    }

    @Test
    fun asymmetricCornerPeelPrioritizesCornerClosestToFingertip() {
        val bottomLeft = IgngPredictiveBackPhysics.computeCardTransform(
            edge = IgngPredictiveBackEdge.LEFT,
            rawProgress = 0.7f,
            commitProgress = 0f,
            startTouchY = heightPx * 0.85f,
            currentTouchY = heightPx * 0.85f,
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            intensity = MotionIntensity.FULL,
        )
        val topRight = IgngPredictiveBackPhysics.computeCardTransform(
            edge = IgngPredictiveBackEdge.RIGHT,
            rawProgress = 0.7f,
            commitProgress = 0f,
            startTouchY = heightPx * 0.15f,
            currentTouchY = heightPx * 0.15f,
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            intensity = MotionIntensity.FULL,
        )

        assertTrue("Bottom-left touch should round bottomStart most", bottomLeft.bottomStartRadiusPx > bottomLeft.topStartRadiusPx)
        assertTrue("Bottom-left touch should round near edge deeper than far edge", bottomLeft.bottomStartRadiusPx > bottomLeft.bottomEndRadiusPx)

        assertTrue("Top-right touch should round topEnd most", topRight.topEndRadiusPx > topRight.bottomEndRadiusPx)
        assertTrue("Top-right touch should round near edge deeper than far edge", topRight.topEndRadiusPx > topRight.topStartRadiusPx)
    }

    @Test
    fun nanAndEdgeNoneInputsAreSafelySanitized() {
        val edgeNone = IgngPredictiveBackPhysics.computeCardTransform(
            edge = IgngPredictiveBackEdge.NONE,
            rawProgress = 0.5f,
            commitProgress = 0f,
            startTouchY = Float.NaN,
            currentTouchY = Float.NaN,
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            intensity = MotionIntensity.FULL,
        )
        assertFalse(edgeNone.active)

        val nanTouchY = IgngPredictiveBackPhysics.computeCardTransform(
            edge = IgngPredictiveBackEdge.LEFT,
            rawProgress = 0.5f,
            commitProgress = 0f,
            startTouchY = Float.NaN,
            currentTouchY = Float.POSITIVE_INFINITY,
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            intensity = MotionIntensity.FULL,
        )
        assertTrue(nanTouchY.active)
        assertTrue(nanTouchY.scale.isFinite())
        assertTrue(nanTouchY.translationX.isFinite())
        assertTrue(nanTouchY.translationY.isFinite())
        assertTrue(nanTouchY.rotationZ.isFinite())
        assertEquals(0.5f, nanTouchY.pivotY, 0.001f)
        assertEquals(0f, nanTouchY.rotationZ, 0.001f)
    }

    @Test
    fun calmReducedAndSurfaceSlideProfilesRespectContracts() {
        val calm = IgngPredictiveBackPhysics.computeCardTransform(
            edge = IgngPredictiveBackEdge.RIGHT,
            rawProgress = 0.6f,
            commitProgress = 0f,
            startTouchY = heightPx * 0.2f,
            currentTouchY = heightPx * 0.2f,
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            intensity = MotionIntensity.CALM,
        )
        assertEquals("CALM mode must suppress tilt", 0f, calm.rotationZ, 0.0001f)
        assertEquals("CALM mode uses symmetric corners", calm.topStartRadiusPx, calm.bottomEndRadiusPx, 0.01f)
        assertTrue("CALM mode still honors right-to-left direction", calm.translationX < 0f)

        val reduced = IgngPredictiveBackPhysics.computeCardTransform(
            edge = IgngPredictiveBackEdge.LEFT,
            rawProgress = 0.6f,
            commitProgress = 0f,
            startTouchY = heightPx * 0.2f,
            currentTouchY = heightPx * 0.2f,
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            intensity = MotionIntensity.REDUCED,
        )
        assertFalse("REDUCED mode must disable 2D card peel", reduced.active)

        val webViewProfile = IgngPredictiveBackPhysics.computeCardTransform(
            edge = IgngPredictiveBackEdge.LEFT,
            rawProgress = 0.6f,
            commitProgress = 0f,
            startTouchY = heightPx * 0.2f,
            currentTouchY = heightPx * 0.2f,
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            intensity = MotionIntensity.FULL,
            profile = IgngPredictiveBackProfile.SurfaceSlide,
        )
        assertTrue(webViewProfile.active)
        assertFalse("SurfaceSlide must disable rounded clipping for WebView stability", webViewProfile.clipEnabled)
        assertEquals("SurfaceSlide must disable rotation", 0f, webViewProfile.rotationZ, 0.0001f)
        assertTrue("SurfaceSlide preserves directional slide", webViewProfile.translationX > 0f)
    }

    @Test
    fun commitWindowAlwaysFinishesBeforeNavHostHoldAliveEnds() {
        for (p in listOf(0f, 0.15f, 0.5f, 0.85f, 1f)) {
            val childProgress = IgngPredictiveBackPhysics.toRelayChildProgress(p)
            val remainingHoldMs = ((1f - childProgress) * IgngPredictiveBackPhysics.HOLD_ALIVE_DURATION_MS).toInt()
            val commitDurationMs = IgngPredictiveBackPhysics.computeCommitDurationMs(
                releaseProgress = p,
                velocityPerSec = 0f,
                intensity = MotionIntensity.FULL,
            )
            assertTrue(
                "Commit ($commitDurationMs ms) must finish before hold-alive ($remainingHoldMs ms) at p=$p",
                commitDurationMs < remainingHoldMs,
            )
        }

        val endCard = IgngPredictiveBackPhysics.computeCardTransform(
            edge = IgngPredictiveBackEdge.LEFT,
            rawProgress = 0.5f,
            commitProgress = 1f,
            startTouchY = heightPx * 0.5f,
            currentTouchY = heightPx * 0.5f,
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            intensity = MotionIntensity.FULL,
        )
        assertEquals(0f, endCard.alpha, 0.0001f)
    }

    @Test
    fun hapticDetentUsesHysteresisToPreventChatter() {
        var armed = false

        val r1 = IgngPredictiveBackPhysics.evaluateHapticDetent(0.20f, armed)
        armed = r1.first
        assertFalse(r1.second)

        val r2 = IgngPredictiveBackPhysics.evaluateHapticDetent(0.30f, armed)
        armed = r2.first
        assertTrue("Crossing threshold upward fires tick once", r2.second)

        val r3 = IgngPredictiveBackPhysics.evaluateHapticDetent(0.24f, armed)
        armed = r3.first
        assertFalse("Jitter inside hysteresis band must not re-arm or fire", r3.second)
        assertTrue(armed)

        val r4 = IgngPredictiveBackPhysics.evaluateHapticDetent(0.12f, armed)
        armed = r4.first
        assertFalse(r4.second)
        assertFalse("Dropping below reset threshold re-arms detent", armed)

        val r5 = IgngPredictiveBackPhysics.evaluateHapticDetent(0.32f, armed)
        assertTrue("Re-crossing threshold after reset fires tick again", r5.second)
    }

    @Test
    fun underlayScrimAndParallaxSettleToRestAtCommitEnd() {
        val mid = IgngPredictiveBackPhysics.computeUnderlayTransform(
            edge = IgngPredictiveBackEdge.LEFT,
            rawProgress = 0.25f,
            commitProgress = 0f,
            density = density,
            isDark = false,
            intensity = MotionIntensity.FULL,
        )
        assertTrue(mid.active)
        assertTrue("Underlay starts slightly scaled down", mid.scale < 1f)
        assertTrue("Underlay enters from left on left swipe", mid.translationX < 0f)
        assertTrue("Underlay carries subtle scrim", mid.scrimAlpha > 0f)

        val settled = IgngPredictiveBackPhysics.computeUnderlayTransform(
            edge = IgngPredictiveBackEdge.LEFT,
            rawProgress = 0.5f,
            commitProgress = 1f,
            density = density,
            isDark = false,
            intensity = MotionIntensity.FULL,
        )
        assertEquals(1f, settled.scale, 0.0001f)
        assertEquals(0f, settled.translationX, 0.001f)
        assertEquals(0f, settled.scrimAlpha, 0.0001f)
    }

    @Test
    fun dockRevealPeeksProgressivelyAndSettlesAtOneOnCommit() {
        val start = IgngPredictiveBackPhysics.computeDockRevealProgress(0f, 0f, MotionIntensity.FULL)
        val mid = IgngPredictiveBackPhysics.computeDockRevealProgress(0.45f, 0f, MotionIntensity.FULL)
        val deep = IgngPredictiveBackPhysics.computeDockRevealProgress(0.90f, 0f, MotionIntensity.FULL)
        val committed = IgngPredictiveBackPhysics.computeDockRevealProgress(0.45f, 1f, MotionIntensity.FULL)

        assertEquals(0f, start, 0.0001f)
        assertTrue("Dock peeks monotonically with drag", mid > start && deep > mid)
        assertTrue("Dock leaves subtle headroom before commit", deep < 0.9f)
        assertEquals("Dock reaches full rest on commit completion", 1f, committed, 0.0001f)
    }
}
