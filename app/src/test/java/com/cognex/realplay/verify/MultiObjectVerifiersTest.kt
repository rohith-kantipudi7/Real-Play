package com.cognex.realplay.verify

import com.cognex.realplay.verify.Fixtures.obj
import com.cognex.realplay.verify.Fixtures.spec
import com.cognex.realplay.verify.Fixtures.step
import com.cognex.realplay.verify.Fixtures.world
import com.cognex.realplay.challenge.ActorRef
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour of the multi-object demo primitives (COLLINEAR, SIZE_ORDER, GROUP_CLUSTERED). Each is
 * exercised for a clear PASS, a clear FAIL, missing-actor → Unsure, and poor-frame → Unsure so it
 * honours the §4.2 gate like every other rule.
 */
class MultiObjectVerifiersTest {

    private val registry = VerifierRegistry.default()

    private fun ev(
        step: com.cognex.realplay.challenge.VerificationStep,
        spec: com.cognex.realplay.challenge.ChallengeSpec,
        world: com.cognex.realplay.world.WorldState
    ) = registry.evaluate(step, spec, world).outcome

    private fun assertPass(o: VerificationOutcome) = assertTrue("expected Pass but was $o", o is VerificationOutcome.Pass)
    private fun assertFail(o: VerificationOutcome) = assertTrue("expected Fail but was $o", o is VerificationOutcome.Fail)
    private fun assertUnsure(o: VerificationOutcome) = assertTrue("expected Unsure but was $o", o is VerificationOutcome.Unsure)

    private val a = ActorRef.ByTrackId(1)
    private val b = ActorRef.ByTrackId(2)
    private val c = ActorRef.ByTrackId(3)

    // ── COLLINEAR ─────────────────────────────────────────────────────────────

    @Test fun collinear_passesForAStraightSpreadRow() {
        val s = spec(a, b, c)
        val st = step(RuleId.COLLINEAR, "tolerance" to 0.06f, "minSpread" to 0.25f)
        assertPass(ev(st, s, world(listOf(obj(1, cx = 0.2f, cy = 0.5f), obj(2, cx = 0.5f, cy = 0.5f), obj(3, cx = 0.8f, cy = 0.5f)))))
    }

    @Test fun collinear_failsWhenNotStraight() {
        val s = spec(a, b, c)
        val st = step(RuleId.COLLINEAR, "tolerance" to 0.06f, "minSpread" to 0.25f)
        // Middle object shoved well off the line.
        assertFail(ev(st, s, world(listOf(obj(1, cx = 0.2f, cy = 0.5f), obj(2, cx = 0.5f, cy = 0.85f), obj(3, cx = 0.8f, cy = 0.5f)))))
    }

    @Test fun collinear_failsWhenBlobbedTogether() {
        val s = spec(a, b, c)
        val st = step(RuleId.COLLINEAR, "tolerance" to 0.06f, "minSpread" to 0.25f)
        // Collinear but tiny spread — a blob, not a row.
        assertFail(ev(st, s, world(listOf(obj(1, cx = 0.48f, cy = 0.5f), obj(2, cx = 0.5f, cy = 0.5f), obj(3, cx = 0.52f, cy = 0.5f)))))
    }

    @Test fun collinear_missingAndPoorFrame_areUnsure() {
        val s = spec(a, b, c)
        val st = step(RuleId.COLLINEAR)
        assertUnsure(ev(st, s, world(listOf(obj(1, cx = 0.2f), obj(2, cx = 0.5f)))))          // only two present
        assertUnsure(ev(st, s, world(listOf(obj(1), obj(2), obj(3)), good = false)))          // poor frame
    }

    // ── SIZE_ORDER ────────────────────────────────────────────────────────────

    @Test fun sizeOrder_ascendingPasses() {
        val s = spec(a, b, c)
        val st = step(RuleId.SIZE_ORDER, "direction" to 1f)
        // Left→right: small, medium, large.
        assertPass(ev(st, s, world(listOf(
            obj(1, cx = 0.2f, w = 0.05f, h = 0.05f),
            obj(2, cx = 0.5f, w = 0.12f, h = 0.12f),
            obj(3, cx = 0.8f, w = 0.22f, h = 0.22f)
        ))))
    }

    @Test fun sizeOrder_wrongDirectionFails() {
        val s = spec(a, b, c)
        val st = step(RuleId.SIZE_ORDER, "direction" to 1f)  // asked ascending
        // But they descend left→right.
        assertFail(ev(st, s, world(listOf(
            obj(1, cx = 0.2f, w = 0.22f, h = 0.22f),
            obj(2, cx = 0.5f, w = 0.12f, h = 0.12f),
            obj(3, cx = 0.8f, w = 0.05f, h = 0.05f)
        ))))
    }

    @Test fun sizeOrder_descendingPasses() {
        val s = spec(a, b, c)
        val st = step(RuleId.SIZE_ORDER, "direction" to -1f)
        assertPass(ev(st, s, world(listOf(
            obj(1, cx = 0.2f, w = 0.22f, h = 0.22f),
            obj(2, cx = 0.5f, w = 0.12f, h = 0.12f),
            obj(3, cx = 0.8f, w = 0.05f, h = 0.05f)
        ))))
    }

    @Test fun sizeOrder_poorFrameUnsure() {
        val s = spec(a, b, c)
        val st = step(RuleId.SIZE_ORDER, "direction" to 1f)
        assertUnsure(ev(st, s, world(listOf(obj(1), obj(2), obj(3)), good = false)))
    }

    // ── GROUP_CLUSTERED ───────────────────────────────────────────────────────

    @Test fun groupClustered_passesWhenTightAndSeparated() {
        val s = spec(a, b)
        val st = step(RuleId.GROUP_CLUSTERED, "clusterRadius" to 0.28f, "separation" to 0.3f)
        // Group (1,2) close together on the left; a far-off other object (9) on the right.
        assertPass(ev(st, s, world(listOf(
            obj(1, cx = 0.2f, cy = 0.5f), obj(2, cx = 0.28f, cy = 0.5f), obj(9, cx = 0.85f, cy = 0.5f)
        ))))
    }

    @Test fun groupClustered_failsWhenGroupSpreadOut() {
        val s = spec(a, b)
        val st = step(RuleId.GROUP_CLUSTERED, "clusterRadius" to 0.28f, "separation" to 0.3f)
        assertFail(ev(st, s, world(listOf(
            obj(1, cx = 0.1f, cy = 0.5f), obj(2, cx = 0.9f, cy = 0.5f)
        ))))
    }

    @Test fun groupClustered_failsWhenOthersTooClose() {
        val s = spec(a, b)
        val st = step(RuleId.GROUP_CLUSTERED, "clusterRadius" to 0.28f, "separation" to 0.3f)
        // Group tight, but an other object sits right on top of the group centroid.
        assertFail(ev(st, s, world(listOf(
            obj(1, cx = 0.4f, cy = 0.5f), obj(2, cx = 0.5f, cy = 0.5f), obj(9, cx = 0.45f, cy = 0.5f)
        ))))
    }

    @Test fun groupClustered_missingAndPoorFrame_areUnsure() {
        val s = spec(a, b)
        val st = step(RuleId.GROUP_CLUSTERED)
        assertUnsure(ev(st, s, world(listOf(obj(1)))))                       // only one member
        assertUnsure(ev(st, s, world(listOf(obj(1), obj(2)), good = false))) // poor frame
    }
}
