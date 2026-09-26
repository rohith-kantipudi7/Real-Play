package com.cognex.realplay.coach

import com.cognex.realplay.challenge.ActorRef
import com.cognex.realplay.challenge.AgeBand
import com.cognex.realplay.challenge.ChallengeSpec
import com.cognex.realplay.verify.RuleId
import com.cognex.realplay.world.ColorTag
import com.cognex.realplay.world.NormPoint
import com.cognex.realplay.world.TrackedObject
import com.cognex.realplay.world.WorldState
import com.cognex.realplay.world.Zone

/**
 * Derives the visual-first coaching cue track for the active step (Architecture §26). Pure JVM,
 * fully deterministic: the same [ChallengeSpec] + [WorldState] always yields the same cues, and the
 * cues reference the ACTUAL actors the verifier will judge — the highlight marks the real target,
 * the arrow ends at the real goal (§26). Cues NEVER influence a verdict (§20 invariant 23).
 *
 * Actor conventions mirror the verifiers exactly (§4.1): for movement rules `actors[0]` is the
 * subject to move and `actors[1]` is the goal (object or zone); presence/attribute rules act on
 * `actors[0]`; pose/motion rules act on the player and produce a [VisualCue.GhostDemo].
 *
 * An actor that cannot be resolved this frame simply yields no cue for that role — never a crash.
 */
object CuePlanner {

    /**
     * Plans the ordered cue track for step [stepIndex] of [spec] against the live [world].
     *
     * Cues are ordered back-to-front (context first, primary target last). By age band (§26):
     * TODDLER/EARLY get the full expressive set (cues are PRIMARY); MIDDLE/OLDER get only the
     * supporting highlight + arrow (words are primary, cues fade in support).
     */
    fun plan(spec: ChallengeSpec, world: WorldState, stepIndex: Int = 0): List<VisualCue> {
        val step = spec.steps.getOrNull(stepIndex) ?: return emptyList()
        val full = buildCues(step.rule, step.params, spec, world)
        return when (spec.ageBand) {
            AgeBand.TODDLER, AgeBand.EARLY -> full
            AgeBand.MIDDLE, AgeBand.OLDER ->
                full.filter { it is VisualCue.Highlight || it is VisualCue.PathArrow }
        }
    }

    private fun buildCues(
        rule: RuleId,
        params: Map<String, Float>,
        spec: ChallengeSpec,
        world: WorldState
    ): List<VisualCue> = buildList {
        when (rule) {
            // Move the subject TOWARDS another object.
            RuleId.DISTANCE_LESS_THAN, RuleId.OVERLAP_RATIO_ABOVE -> {
                val subject = objectAt(spec, world, 0)
                val target = objectAt(spec, world, 1)
                if (target != null) add(VisualCue.Highlight(target.trackId, Pulse.GENTLE))
                if (subject != null) add(VisualCue.Highlight(subject.trackId, Pulse.STRONG))
                if (subject != null && target != null) {
                    add(VisualCue.PathArrow(subject.center, target.center))
                }
            }

            // Move the subject INTO a zone.
            RuleId.POINT_IN_ZONE, RuleId.OBJECT_VANISHED_IN_ZONE -> {
                val subject = objectAt(spec, world, 0)
                val zone = zoneAt(spec, world, 1)
                if (zone != null) add(VisualCue.ZonePulse(zone.zoneId))
                if (subject != null) add(VisualCue.Highlight(subject.trackId, Pulse.STRONG))
                if (subject != null && zone != null) {
                    add(VisualCue.PathArrow(subject.center, centroid(zone.polygon)))
                }
            }

            // Move the subject AWAY from the reference — highlight both, no directional arrow.
            RuleId.DISTANCE_GREATER_THAN -> {
                objectAt(spec, world, 1)?.let { add(VisualCue.Highlight(it.trackId, Pulse.GENTLE)) }
                objectAt(spec, world, 0)?.let { add(VisualCue.Highlight(it.trackId, Pulse.STRONG)) }
            }

            // Directional placement — highlight both actors.
            RuleId.LEFT_OF, RuleId.RIGHT_OF, RuleId.ABOVE, RuleId.BELOW -> {
                objectAt(spec, world, 1)?.let { add(VisualCue.Highlight(it.trackId, Pulse.GENTLE)) }
                objectAt(spec, world, 0)?.let { add(VisualCue.Highlight(it.trackId, Pulse.STRONG)) }
            }

            // Present the subject to the camera.
            RuleId.OBJECT_PRESENT -> {
                objectAt(spec, world, 0)?.let { add(VisualCue.Highlight(it.trackId, Pulse.STRONG)) }
                add(VisualCue.Pictograph(Symbol.Target))
            }

            // Remove the subject from view.
            RuleId.OBJECT_ABSENT -> {
                objectAt(spec, world, 0)?.let { add(VisualCue.Highlight(it.trackId, Pulse.STRONG)) }
            }

            // Find/produce a colour — highlight the actor and show the colour swatch.
            RuleId.COLOR_MATCH -> {
                objectAt(spec, world, 0)?.let { add(VisualCue.Highlight(it.trackId, Pulse.STRONG)) }
                colorFromParam(params)?.let { add(VisualCue.Pictograph(Symbol.ColorSwatch(it))) }
            }

            // Match a shape.
            RuleId.SHAPE_MATCH -> {
                objectAt(spec, world, 0)?.let { add(VisualCue.Highlight(it.trackId, Pulse.STRONG)) }
                add(VisualCue.Pictograph(Symbol.Shape(ShapeKind.SQUARE)))
            }

            // Multi-object geometry — highlight every resolvable actor.
            RuleId.NON_DEGENERATE_TRIANGLE -> {
                highlightAll(spec, world)
                add(VisualCue.Pictograph(Symbol.Shape(ShapeKind.TRIANGLE)))
            }

            RuleId.ARRANGEMENT_MATCH, RuleId.COUNT_EQUALS -> highlightAll(spec, world)

            // Pose / motion — loop a ghost demonstration of the target action (S8).
            RuleId.POSE_MATCH, RuleId.JOINT_ANGLE_WITHIN ->
                add(VisualCue.GhostDemo(DemoKind.MATCH_POSE))
            RuleId.LIMB_RAISED -> add(VisualCue.GhostDemo(DemoKind.RAISE_LIMB))
            RuleId.MOTION_BELOW -> add(VisualCue.GhostDemo(DemoKind.HOLD_STILL))
            RuleId.MOTION_ABOVE -> add(VisualCue.GhostDemo(DemoKind.MOVE))

            // Player ↔ object / zone (S8) — highlight the goal and demo the movement.
            RuleId.PLAYER_NEAR_OBJECT, RuleId.PLAYER_HOLDS_OBJECT -> {
                objectAt(spec, world, 1)?.let { add(VisualCue.Highlight(it.trackId, Pulse.STRONG)) }
                add(VisualCue.GhostDemo(DemoKind.MOVE))
            }
            RuleId.PLAYER_IN_ZONE -> {
                zoneAt(spec, world, 1)?.let { add(VisualCue.ZonePulse(it.zoneId)) }
                add(VisualCue.GhostDemo(DemoKind.MOVE))
            }
        }
    }

    private fun MutableList<VisualCue>.highlightAll(spec: ChallengeSpec, world: WorldState) {
        spec.actors.forEachIndexed { i, _ ->
            objectAt(spec, world, i)?.let {
                add(VisualCue.Highlight(it.trackId, if (i == 0) Pulse.STRONG else Pulse.GENTLE))
            }
        }
    }

    private fun objectAt(spec: ChallengeSpec, world: WorldState, index: Int): TrackedObject? =
        when (val a = spec.actors.getOrNull(index)) {
            is ActorRef.ByTrackId -> world.objects.firstOrNull { it.trackId == a.trackId }
            is ActorRef.ByLabel -> world.objects.firstOrNull { it.label == a.label }
            else -> null
        }

    private fun zoneAt(spec: ChallengeSpec, world: WorldState, index: Int): Zone? =
        when (val a = spec.actors.getOrNull(index)) {
            is ActorRef.ByZone -> world.zones.firstOrNull { it.zoneId == a.zoneId }
            else -> null
        }

    private fun colorFromParam(params: Map<String, Float>): ColorTag? {
        val ordinal = params["color"]?.toInt() ?: return null
        return ColorTag.entries.getOrNull(ordinal)
    }

    /** Simple polygon centroid (vertex average) in NORMALIZED space. */
    private fun centroid(polygon: List<NormPoint>): NormPoint {
        if (polygon.isEmpty()) return NormPoint(0.5f, 0.5f)
        var sx = 0f
        var sy = 0f
        for (p in polygon) {
            sx += p.x
            sy += p.y
        }
        return NormPoint(sx / polygon.size, sy / polygon.size)
    }
}
