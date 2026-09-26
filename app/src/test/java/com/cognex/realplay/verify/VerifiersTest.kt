package com.cognex.realplay.verify

import com.cognex.realplay.challenge.ActorRef
import com.cognex.realplay.verify.Fixtures.boxZone
import com.cognex.realplay.verify.Fixtures.landmarks
import com.cognex.realplay.verify.Fixtures.obj
import com.cognex.realplay.verify.Fixtures.player
import com.cognex.realplay.verify.Fixtures.spec
import com.cognex.realplay.verify.Fixtures.step
import com.cognex.realplay.verify.Fixtures.world
import com.cognex.realplay.world.ColorTag
import com.cognex.realplay.world.Landmark
import com.cognex.realplay.world.NormPoint
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioural coverage for every [RuleId] (Architecture §4.1, S4). Each rule is exercised for a
 * clear PASS, a clear FAIL / UNSURE, missing-actor → Unsure, and poor-frame → Unsure. Together with
 * [TemporalGateTest] these are the S4 gate.
 */
class VerifiersTest {

    private val registry = VerifierRegistry.default()

    private fun ev(
        step: com.cognex.realplay.challenge.VerificationStep,
        spec: com.cognex.realplay.challenge.ChallengeSpec,
        world: com.cognex.realplay.world.WorldState,
        baseline: VerificationBaseline = VerificationBaseline.NONE
    ) = registry.evaluate(step, spec, world, baseline).outcome

    private fun assertPass(o: VerificationOutcome) =
        assertTrue("expected Pass but was $o", o is VerificationOutcome.Pass)

    private fun assertFail(o: VerificationOutcome) =
        assertTrue("expected Fail but was $o", o is VerificationOutcome.Fail)

    private fun assertUnsure(o: VerificationOutcome) =
        assertTrue("expected Unsure but was $o", o is VerificationOutcome.Unsure)

    private val a = ActorRef.ByTrackId(1)
    private val b = ActorRef.ByTrackId(2)

    // ── Distance ──────────────────────────────────────────────────────────────

    @Test fun distanceLessThan() {
        val s = spec(a, b); val st = step(RuleId.DISTANCE_LESS_THAN, "threshold" to 0.2f)
        assertPass(ev(st, s, world(listOf(obj(1, cx = 0.4f), obj(2, cx = 0.45f)))))
        assertFail(ev(st, s, world(listOf(obj(1, cx = 0.2f), obj(2, cx = 0.8f)))))
        assertUnsure(ev(st, s, world(listOf(obj(1)))))                       // missing reference
        assertUnsure(ev(st, s, world(listOf(obj(1), obj(2)), good = false))) // poor frame
    }

    @Test fun distanceGreaterThan() {
        val s = spec(a, b); val st = step(RuleId.DISTANCE_GREATER_THAN, "threshold" to 0.2f)
        assertPass(ev(st, s, world(listOf(obj(1, cx = 0.2f), obj(2, cx = 0.8f)))))
        assertFail(ev(st, s, world(listOf(obj(1, cx = 0.4f), obj(2, cx = 0.45f)))))
    }

    // ── Direction ───────────────────────────────────────────────────────────

    @Test fun leftOf() {
        val s = spec(a, b); val st = step(RuleId.LEFT_OF)
        assertPass(ev(st, s, world(listOf(obj(1, cx = 0.2f), obj(2, cx = 0.7f)))))
        assertFail(ev(st, s, world(listOf(obj(1, cx = 0.7f), obj(2, cx = 0.2f)))))
        assertUnsure(ev(st, s, world(listOf(obj(1)))))
    }

    @Test fun rightOf() {
        val s = spec(a, b); val st = step(RuleId.RIGHT_OF)
        assertPass(ev(st, s, world(listOf(obj(1, cx = 0.7f), obj(2, cx = 0.2f)))))
        assertFail(ev(st, s, world(listOf(obj(1, cx = 0.2f), obj(2, cx = 0.7f)))))
    }

    @Test fun above() {
        val s = spec(a, b); val st = step(RuleId.ABOVE)
        assertPass(ev(st, s, world(listOf(obj(1, cy = 0.2f), obj(2, cy = 0.7f)))))
        assertFail(ev(st, s, world(listOf(obj(1, cy = 0.7f), obj(2, cy = 0.2f)))))
    }

    @Test fun below() {
        val s = spec(a, b); val st = step(RuleId.BELOW)
        assertPass(ev(st, s, world(listOf(obj(1, cy = 0.7f), obj(2, cy = 0.2f)))))
        assertFail(ev(st, s, world(listOf(obj(1, cy = 0.2f), obj(2, cy = 0.7f)))))
    }

    @Test fun directionBoundary_isNotAPass() {
        // Centres identical → separation 0, below the tolerance → Fail, never a lucky Pass.
        val s = spec(a, b); val st = step(RuleId.LEFT_OF)
        assertFail(ev(st, s, world(listOf(obj(1, cx = 0.5f), obj(2, cx = 0.5f)))))
    }

    // ── Zones / overlap ─────────────────────────────────────────────────────

    @Test fun pointInZone() {
        val s = spec(a, ActorRef.ByZone("z")); val st = step(RuleId.POINT_IN_ZONE)
        val z = boxZone("z", 0.3f, 0.3f, 0.7f, 0.7f)
        assertPass(ev(st, s, world(listOf(obj(1, cx = 0.5f, cy = 0.5f)), zones = listOf(z))))
        assertFail(ev(st, s, world(listOf(obj(1, cx = 0.9f, cy = 0.9f)), zones = listOf(z))))
        assertUnsure(ev(st, s, world(listOf(obj(1)))))  // zone missing
    }

    @Test fun overlapRatioAbove() {
        val s = spec(a, b); val st = step(RuleId.OVERLAP_RATIO_ABOVE, "threshold" to 0.5f)
        assertPass(ev(st, s, world(listOf(obj(1, cx = 0.5f, w = 0.2f, h = 0.2f), obj(2, cx = 0.5f, w = 0.1f, h = 0.1f)))))
        assertFail(ev(st, s, world(listOf(obj(1, cx = 0.3f, w = 0.1f, h = 0.1f), obj(2, cx = 0.7f, w = 0.1f, h = 0.1f)))))
    }

    @Test fun objectVanishedInZone() {
        val s = spec(a, ActorRef.ByZone("z"), b)
        val st = step(RuleId.OBJECT_VANISHED_IN_ZONE, "frames" to 3f)
        val z = boxZone("z", 0.3f, 0.3f, 0.7f, 0.7f)
        val container = obj(2, cx = 0.5f, cy = 0.5f)
        val vanishedIn = VerificationBaseline(vanished = VanishedInfo(NormPoint(0.5f, 0.5f), 5, exitedAtEdge = false))
        // target gone, last seen in zone, container present → Pass
        assertPass(ev(st, s, world(listOf(container), zones = listOf(z)), vanishedIn))
        // target still present → Unsure (not vanished yet)
        assertUnsure(ev(st, s, world(listOf(obj(1), container), zones = listOf(z)), vanishedIn))
        // exited at frame edge → Unsure, never Fail
        val edge = VerificationBaseline(vanished = VanishedInfo(NormPoint(0.5f, 0.5f), 5, exitedAtEdge = true))
        assertUnsure(ev(st, s, world(listOf(container), zones = listOf(z)), edge))
        // container gone → Unsure
        assertUnsure(ev(st, s, world(emptyList(), zones = listOf(z)), vanishedIn))
    }

    // ── Presence / attributes ────────────────────────────────────────────────

    @Test fun objectPresent() {
        val s = spec(a); val st = step(RuleId.OBJECT_PRESENT)
        assertPass(ev(st, s, world(listOf(obj(1)))))
        assertUnsure(ev(st, s, world(emptyList())))                 // absent → Unsure, never Fail
        assertUnsure(ev(st, s, world(listOf(obj(1, confidence = 0.2f))))) // low confidence
    }

    @Test fun objectAbsent() {
        val s = spec(a); val st = step(RuleId.OBJECT_ABSENT)
        assertPass(ev(st, s, world(emptyList())))
        assertFail(ev(st, s, world(listOf(obj(1)))))
        assertUnsure(ev(st, s, world(emptyList(), good = false)))   // bad frame → Unsure
    }

    @Test fun colorMatch() {
        val s = spec(a)
        val st = step(RuleId.COLOR_MATCH, "color" to ColorTag.RED.ordinal.toFloat())
        assertPass(ev(st, s, world(listOf(obj(1, color = ColorTag.RED)))))
        assertFail(ev(st, s, world(listOf(obj(1, color = ColorTag.BLUE)))))
        assertUnsure(ev(st, s, world(listOf(obj(1, color = null)))))          // unknown colour
        assertUnsure(ev(st, s, world(listOf(obj(1, color = ColorTag.UNKNOWN)))))
    }

    @Test fun shapeMatch() {
        val s = spec(a)
        val st = step(RuleId.SHAPE_MATCH, "aspect" to 2f, "tolerance" to 0.35f)
        assertPass(ev(st, s, world(listOf(obj(1, w = 0.2f, h = 0.1f)))))       // aspect 2
        assertFail(ev(st, s, world(listOf(obj(1, w = 0.1f, h = 0.1f)))))       // aspect 1
    }

    @Test fun countEquals() {
        val s = spec(ActorRef.ByLabel("ball"))
        val st = step(RuleId.COUNT_EQUALS, "count" to 2f)
        val two = listOf(obj(1, label = "ball"), obj(2, label = "ball"))
        assertPass(ev(st, s, world(two)))
        assertFail(ev(st, s, world(listOf(obj(1, label = "ball")))))
    }

    // ── Multi-object geometry ─────────────────────────────────────────────────

    @Test fun nonDegenerateTriangle() {
        val s = spec(a, b, ActorRef.ByTrackId(3))
        val st = step(RuleId.NON_DEGENERATE_TRIANGLE, "minArea" to 0.02f, "minAngle" to 15f, "maxRatio" to 5f)
        val triangle = listOf(obj(1, cx = 0.2f, cy = 0.2f), obj(2, cx = 0.8f, cy = 0.2f), obj(3, cx = 0.5f, cy = 0.8f))
        assertPass(ev(st, s, world(triangle)))
        val collinear = listOf(obj(1, cx = 0.2f, cy = 0.2f), obj(2, cx = 0.4f, cy = 0.4f), obj(3, cx = 0.6f, cy = 0.6f))
        assertFail(ev(st, s, world(collinear)))
        assertUnsure(ev(st, s, world(listOf(obj(1), obj(2)))))  // only two corners
    }

    @Test fun arrangementMatch_invariantToScaleAndTranslation() {
        val reference = listOf(
            ReferenceObject("a", NormPoint(0.45f, 0.45f)),
            ReferenceObject("b", NormPoint(0.55f, 0.45f)),
            ReferenceObject("c", NormPoint(0.50f, 0.55f))
        )
        val baseline = VerificationBaseline(referenceObjects = reference)
        val s = spec(ActorRef.ByLabel("a"), ActorRef.ByLabel("b"), ActorRef.ByLabel("c"))
        val st = step(RuleId.ARRANGEMENT_MATCH, "tolerance" to 0.15f)

        // Same shape, scaled 2× about its centroid and shifted → must still Pass.
        val cx = 0.30f; val cy = 0.30f  // arbitrary translation of the (already 2× larger) copy
        val scaled = listOf(
            obj(1, label = "a", cx = cx + 2 * (0.45f - 0.50f) + 0.20f, cy = cy + 2 * (0.45f - 0.4833f) + 0.20f),
            obj(2, label = "b", cx = cx + 2 * (0.55f - 0.50f) + 0.20f, cy = cy + 2 * (0.45f - 0.4833f) + 0.20f),
            obj(3, label = "c", cx = cx + 2 * (0.50f - 0.50f) + 0.20f, cy = cy + 2 * (0.55f - 0.4833f) + 0.20f)
        )
        assertPass(ev(st, s, world(scaled), baseline))

        // A distorted arrangement → Fail.
        val distorted = listOf(
            obj(1, label = "a", cx = 0.10f, cy = 0.10f),
            obj(2, label = "b", cx = 0.90f, cy = 0.15f),
            obj(3, label = "c", cx = 0.50f, cy = 0.52f)
        )
        assertFail(ev(st, s, world(distorted), baseline))

        // Missing a piece → Unsure.
        assertUnsure(ev(st, s, world(listOf(obj(1, label = "a"), obj(2, label = "b"))), baseline))
    }

    // ── Pose ─────────────────────────────────────────────────────────────────

    private fun rightAngleArm(wristVis: Float = 1f): List<Landmark> = landmarks(
        11 to Landmark(NormPoint(0.4f, 0.3f), 1f),   // LEFT_SHOULDER
        13 to Landmark(NormPoint(0.4f, 0.4f), 1f),   // LEFT_ELBOW
        15 to Landmark(NormPoint(0.5f, 0.4f), wristVis) // LEFT_WRIST
    )

    @Test fun poseMatch() {
        val ref = PoseMath.poseFeatureVector(rightAngleArm())
        val baseline = VerificationBaseline(referencePose = ref)
        val s = spec(ActorRef.ByPlayer(1)); val st = step(RuleId.POSE_MATCH, "tolerance" to 0.3f)
        assertPass(ev(st, s, world(players = listOf(player(landmarks = rightAngleArm()))), baseline))

        val straightArm = landmarks(
            11 to Landmark(NormPoint(0.4f, 0.3f), 1f),
            13 to Landmark(NormPoint(0.4f, 0.4f), 1f),
            15 to Landmark(NormPoint(0.4f, 0.5f), 1f)
        )
        assertFail(ev(st, s, world(players = listOf(player(landmarks = straightArm))), baseline))
        assertUnsure(ev(st, s, world(players = listOf(player(landmarks = rightAngleArm())))))  // no reference
    }

    @Test fun jointAngleWithin() {
        val s = spec(ActorRef.ByPlayer(1))
        // joint 0 = leftElbow ≈ 90° = 1.5708 rad
        val pass = step(RuleId.JOINT_ANGLE_WITHIN, "joint" to 0f, "target" to 1.5708f, "tolerance" to 0.2f)
        assertPass(ev(pass, s, world(players = listOf(player(landmarks = rightAngleArm())))))
        val fail = step(RuleId.JOINT_ANGLE_WITHIN, "joint" to 0f, "target" to 3.14f, "tolerance" to 0.2f)
        assertFail(ev(fail, s, world(players = listOf(player(landmarks = rightAngleArm())))))
        assertUnsure(ev(pass, s, world(players = listOf(player(landmarks = rightAngleArm(wristVis = 0.1f)))))) // low vis
    }

    @Test fun limbRaised() {
        val s = spec(ActorRef.ByPlayer(1)); val st = step(RuleId.LIMB_RAISED, "limb" to 0f, "margin" to 0.05f)
        val raised = landmarks(
            11 to Landmark(NormPoint(0.4f, 0.5f), 1f),   // shoulder
            15 to Landmark(NormPoint(0.4f, 0.2f), 1f)    // wrist above shoulder
        )
        assertPass(ev(st, s, world(players = listOf(player(landmarks = raised)))))
        val lowered = landmarks(
            11 to Landmark(NormPoint(0.4f, 0.5f), 1f),
            15 to Landmark(NormPoint(0.4f, 0.7f), 1f)    // wrist below shoulder
        )
        assertFail(ev(st, s, world(players = listOf(player(landmarks = lowered)))))
        val hidden = landmarks(
            11 to Landmark(NormPoint(0.4f, 0.5f), 1f),
            15 to Landmark(NormPoint(0.4f, 0.2f), 0.1f)
        )
        assertUnsure(ev(st, s, world(players = listOf(player(landmarks = hidden)))))
    }

    // ── Motion ──────────────────────────────────────────────────────────────

    @Test fun motionBelow() {
        val s = spec(ActorRef.ByPlayer(1)); val st = step(RuleId.MOTION_BELOW, "threshold" to 0.05f)
        assertPass(ev(st, s, world(players = listOf(player(motionEnergy = 0.01f)))))
        assertFail(ev(st, s, world(players = listOf(player(motionEnergy = 0.30f)))))
        assertUnsure(ev(st, s, world(players = emptyList())))
    }

    @Test fun motionAbove() {
        val s = spec(ActorRef.ByPlayer(1)); val st = step(RuleId.MOTION_ABOVE, "threshold" to 0.15f)
        assertPass(ev(st, s, world(players = listOf(player(motionEnergy = 0.30f)))))
        assertFail(ev(st, s, world(players = listOf(player(motionEnergy = 0.01f)))))
    }

    // ── Player ↔ object / zone ────────────────────────────────────────────────

    @Test fun playerNearObject() {
        val s = spec(ActorRef.ByPlayer(1), a); val st = step(RuleId.PLAYER_NEAR_OBJECT, "threshold" to 0.25f)
        assertPass(ev(st, s, world(listOf(obj(1, cx = 0.55f, cy = 0.5f)), listOf(player(cx = 0.5f, cy = 0.5f)))))
        assertFail(ev(st, s, world(listOf(obj(1, cx = 0.9f, cy = 0.9f)), listOf(player(cx = 0.1f, cy = 0.1f)))))
        assertUnsure(ev(st, s, world(listOf(obj(1)), emptyList())))  // player missing
    }

    @Test fun playerHoldsObject() {
        val s = spec(ActorRef.ByPlayer(1), a); val st = step(RuleId.PLAYER_HOLDS_OBJECT, "threshold" to 0.12f)
        val hand = landmarks(15 to Landmark(NormPoint(0.5f, 0.5f), 1f))
        assertPass(ev(st, s, world(listOf(obj(1, cx = 0.52f, cy = 0.5f)), listOf(player(landmarks = hand)))))
        assertFail(ev(st, s, world(listOf(obj(1, cx = 0.9f, cy = 0.9f)), listOf(player(landmarks = hand)))))
        val hidden = landmarks(15 to Landmark(NormPoint(0.5f, 0.5f), 0.1f), 16 to Landmark(NormPoint(0.5f, 0.5f), 0.1f))
        assertUnsure(ev(st, s, world(listOf(obj(1, cx = 0.52f)), listOf(player(landmarks = hidden)))))
    }

    @Test fun playerInZone() {
        val s = spec(ActorRef.ByPlayer(1), ActorRef.ByZone("z")); val st = step(RuleId.PLAYER_IN_ZONE)
        val z = boxZone("z", 0.3f, 0.3f, 0.7f, 0.7f)
        assertPass(ev(st, s, world(players = listOf(player(cx = 0.5f, cy = 0.5f)), zones = listOf(z))))
        assertFail(ev(st, s, world(players = listOf(player(cx = 0.1f, cy = 0.1f)), zones = listOf(z))))
    }

    // ── Cross-cutting invariants ──────────────────────────────────────────────

    @Test fun everyRule_returnsUnsureOnPoorFrame() {
        // No rule may Pass or Fail on a bad frame (§4.2 rule 1). Empty world + bad quality.
        val s = spec(a, b, ActorRef.ByTrackId(3), ActorRef.ByZone("z"), ActorRef.ByPlayer(1))
        for (rule in RuleId.entries) {
            val outcome = ev(step(rule), s, world(good = false, reason = "Move to better light"))
            assertUnsure(outcome)
        }
    }

    @Test fun outcomesAreDeterministic() {
        val s = spec(a, b); val st = step(RuleId.DISTANCE_LESS_THAN, "threshold" to 0.2f)
        val w = world(listOf(obj(1, cx = 0.4f), obj(2, cx = 0.45f)))
        val first = ev(st, s, w)
        val second = ev(st, s, w)
        assertTrue(first is VerificationOutcome.Pass && second is VerificationOutcome.Pass)
    }
}
