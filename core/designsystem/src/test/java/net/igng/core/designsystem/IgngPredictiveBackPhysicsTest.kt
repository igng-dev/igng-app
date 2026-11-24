package net.igng.core.designsystem

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.igng.core.model.MotionIntensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IgngPredictiveBackPhysicsTest {

    private class VirtualFrameClock(
        private val frameStepMs: Long = 16L,
    ) : MonotonicFrameClock {
        private var nowNanos: Long = 0L

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(frameStepMs)
            nowNanos += frameStepMs * 1_000_000L
            return onFrame(nowNanos)
        }
    }

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

    @Test
    fun asyncNavHostPopCommitsCleanlyAndHoldsExitingHiddenUntilDispose() = runTest {
        val frameClock = VirtualFrameClock()
        val state = IgngPredictiveBackState()
        val scope = kotlinx.coroutines.CoroutineScope(coroutineContext + frameClock)

        state.startDrag(
            exitingId = "entry-B",
            enteringId = "entry-A",
            swipeEdge = IgngPredictiveBackEdge.LEFT,
            touchY = heightPx * 0.5f,
            rawProgress = 0.05f,
            frameTimeMs = 100L,
            revealDock = true,
            dockTargetTabId = "feed",
            intensity = MotionIntensity.FULL,
        )
        state.updateDrag(
            touchY = heightPx * 0.5f,
            rawProgress = 0.15f,
            frameTimeMs = 130L,
            intensity = MotionIntensity.FULL,
            onHapticTick = {},
        )

        // Simulate NavHost's PredictiveBackHandler: back stack does NOT pop synchronously inside
        // childDispatcher.onBackPressed(); it pops asynchronously in a coroutine right afterwards.
        var currentEntryId = "entry-B"
        var exitingStillInAnimatedContent = true

        state.enterCommittingSync()
        assertEquals(IgngPredictiveBackPhase.Committing, state.phase)

        // Async pop queued ahead of commit coroutine (mirrors Channel.close -> NavHost coroutine)
        scope.launch { currentEntryId = "entry-A" }

        state.runCommitAnimation(
            scope = scope,
            intensity = MotionIntensity.FULL,
            hasPopped = { currentEntryId != "entry-B" },
            isEntryStillVisible = { exitingStillInAnimatedContent },
            awaitNextFrame = { frameClock.withFrameNanos {} },
        )

        // Advance past commit fly-out duration (<= 230ms) + old broken 95ms grace window,
        // while AnimatedContent has NOT yet disposed entry-B (e.g. fast flick or 2x anim scale).
        advanceTimeBy(400L)
        runCurrent()

        assertEquals(
            "State must remain Committing while exiting page is still composed",
            IgngPredictiveBackPhase.Committing,
            state.phase,
        )
        assertEquals(1f, state.visualCommitProgress, 0.0001f)
        assertEquals(1f, state.dockRevealProgress, 0.0001f)
        assertTrue(state.isSuppressedExiting("entry-B"))

        val hiddenCard = state.resolveCardTransform(
            entryId = "entry-B",
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            intensity = MotionIntensity.FULL,
            profile = IgngPredictiveBackProfile.CardPeel,
        )
        assertTrue("Exiting page must remain actively hidden, never snap to Identity", hiddenCard.active)
        assertEquals(0f, hiddenCard.alpha, 0.0001f)

        // Now AnimatedContent finishes its hold-alive transition and disposes entry-B
        exitingStillInAnimatedContent = false
        state.onSurfaceDisposed("entry-B")

        assertEquals(IgngPredictiveBackPhase.Idle, state.phase)
        assertFalse(state.isGestureSession)
        assertFalse(state.isSuppressedExiting("entry-B"))
    }

    @Test
    fun rapidSecondBackSwipeKeepsPreviousExitingEntrySuppressedUntilDisposed() = runTest {
        val frameClock = VirtualFrameClock()
        val state = IgngPredictiveBackState()
        val scope = kotlinx.coroutines.CoroutineScope(coroutineContext + frameClock)

        state.startDrag(
            exitingId = "entry-C",
            enteringId = "entry-B",
            swipeEdge = IgngPredictiveBackEdge.RIGHT,
            touchY = heightPx * 0.4f,
            rawProgress = 0.4f,
            frameTimeMs = 100L,
            revealDock = false,
            dockTargetTabId = null,
            intensity = MotionIntensity.FULL,
        )
        state.enterCommittingSync()
        state.runCommitAnimation(
            scope = scope,
            intensity = MotionIntensity.FULL,
            hasPopped = { true },
            isEntryStillVisible = { true },
            awaitNextFrame = { frameClock.withFrameNanos {} },
        )
        advanceTimeBy(120L)
        runCurrent()

        // User starts a 2nd back swipe from B -> A before entry-C has been disposed
        state.startDrag(
            exitingId = "entry-B",
            enteringId = "entry-A",
            swipeEdge = IgngPredictiveBackEdge.LEFT,
            touchY = heightPx * 0.5f,
            rawProgress = 0.25f,
            frameTimeMs = 250L,
            revealDock = true,
            dockTargetTabId = "feed",
            intensity = MotionIntensity.FULL,
            visibleEntryIds = setOf("entry-A", "entry-B", "entry-C"),
        )

        val priorCard = state.resolveCardTransform(
            entryId = "entry-C",
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            intensity = MotionIntensity.FULL,
            profile = IgngPredictiveBackProfile.CardPeel,
        )
        assertTrue(priorCard.active)
        assertEquals("Superseded committing entry must stay invisible (alpha=0)", 0f, priorCard.alpha, 0.0001f)

        // When entry-C finally disposes, it cleans itself up without interrupting B -> A drag
        state.onSurfaceDisposed("entry-C")
        assertFalse(state.isSuppressedExiting("entry-C"))
        assertEquals(IgngPredictiveBackPhase.Dragging, state.phase)
        assertEquals("entry-B", state.exitingEntryId)
    }

    @Test
    fun interceptedNestedBackHandlerReboundsToIdleCleanly() = runTest {
        val frameClock = VirtualFrameClock()
        val state = IgngPredictiveBackState()
        val scope = kotlinx.coroutines.CoroutineScope(coroutineContext + frameClock)

        state.startDrag(
            exitingId = "entry-B",
            enteringId = "entry-A",
            swipeEdge = IgngPredictiveBackEdge.LEFT,
            touchY = heightPx * 0.5f,
            rawProgress = 0.5f,
            frameTimeMs = 100L,
            revealDock = true,
            dockTargetTabId = "feed",
            intensity = MotionIntensity.CALM,
        )
        state.enterCommittingSync()
        state.runCommitAnimation(
            scope = scope,
            intensity = MotionIntensity.CALM,
            hasPopped = { false }, // Nested BackHandler consumed back without popping NavHost
            isEntryStillVisible = { false },
            awaitNextFrame = { frameClock.withFrameNanos {} },
        )

        // Wait past the 2-frame pop check + CALM 200ms rebound
        advanceTimeBy(350L)
        runCurrent()

        assertEquals(IgngPredictiveBackPhase.Idle, state.phase)
        assertEquals(0f, state.visualDragProgress, 0.0001f)
        assertEquals(0f, state.visualCommitProgress, 0.0001f)
        assertEquals(0f, state.dockRevealProgress, 0.0001f)
        assertFalse(state.isSuppressedExiting("entry-B"))
    }

    @Test
    fun cancelDragDefersChildCancelUntilReboundFinishesAndHidesUnderlayUntilDispose() = runTest {
        val frameClock = VirtualFrameClock()
        val state = IgngPredictiveBackState()
        val scope = kotlinx.coroutines.CoroutineScope(coroutineContext + frameClock)

        state.startDrag(
            exitingId = "entry-B",
            enteringId = "entry-A",
            swipeEdge = IgngPredictiveBackEdge.LEFT,
            touchY = heightPx * 0.5f,
            rawProgress = 0.45f,
            frameTimeMs = 100L,
            revealDock = true,
            dockTargetTabId = "feed",
            intensity = MotionIntensity.CALM,
        )

        var childCancelDispatched = false
        state.cancelDrag(
            scope = scope,
            intensity = MotionIntensity.CALM,
            onReboundFinished = { childCancelDispatched = true },
            isUnderlayStillVisible = { true },
        )

        // Mid-rebound: child cancel must NOT have fired yet so underlay A stays alive behind card B
        advanceTimeBy(80L)
        runCurrent()
        assertFalse("Child cancel must be deferred during rebound", childCancelDispatched)
        assertEquals(IgngPredictiveBackPhase.Cancelling, state.phase)
        assertFalse(state.suppressUnderlayDuringCancelSettle)

        // After rebound finishes (200ms CALM): child cancel fires and underlay is suppressed until dispose
        advanceTimeBy(180L)
        runCurrent()
        assertTrue("Child cancel must fire once rebound reaches 0f", childCancelDispatched)
        assertTrue(state.suppressUnderlayDuringCancelSettle)
        assertEquals(0f, state.visualDragProgress, 0.0001f)

        // Once NavHost snaps back and disposes underlay A, state resets cleanly
        state.onSurfaceDisposed("entry-A")
        assertEquals(IgngPredictiveBackPhase.Idle, state.phase)
        assertFalse(state.suppressUnderlayDuringCancelSettle)
    }
}
