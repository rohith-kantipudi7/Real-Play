package com.cognex.realplay.coach

import com.cognex.realplay.challenge.ActorRef
import com.cognex.realplay.challenge.AgeBand
import com.cognex.realplay.challenge.CFix
import com.cognex.realplay.challenge.ChallengeSpec
import com.cognex.realplay.challenge.ChallengeType
import com.cognex.realplay.challenge.Tier
import com.cognex.realplay.challenge.VerificationStep
import com.cognex.realplay.verify.RuleId
import com.cognex.realplay.world.ColorTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for deterministic visual-first cue planning from a spec (Architecture §26). */
class CuePlannerTest {

    private fun spec(
        type: ChallengeType,
        actors: List<ActorRef>,
        rule: RuleId,
        params: Map<String, Float> = emptyMap(),
        ageBand: AgeBand = AgeBand.EARLY
    ) = ChallengeSpec(
        id = "T",
        type = type,
        tier = Tier.EASY,
        ageBand = ageBand,
        actors = actors,
        instruction = "do it",
        steps = listOf(VerificationStep(rule, params, holdMs = 300L)),
        timeLimitMs = null,
        baseScore = 10,
        hints = emptyList()
    )

    @Test
    fun `point in zone plans zone pulse, target highlight and a path arrow to the zone`() {
        val subject = CFix.obj(trackId = 1, cx = 0.2f, cy = 0.2f)
        val zone = CFix.zone(zoneId = "zA", cx = 0.8f, cy = 0.8f, half = 0.1f)
        val world = CFix.world(objects = listOf(subject), zones = listOf(zone))
        val cues = CuePlanner.plan(
            spec(ChallengeType.DROP_ZONE, listOf(ActorRef.ByTrackId(1), ActorRef.ByZone("zA")), RuleId.POINT_IN_ZONE),
            world
        )

        // Order: context (zone) first, primary target last, arrow after.
        assertEquals(VisualCue.ZonePulse("zA"), cues[0])
        assertEquals(VisualCue.Highlight(1, Pulse.STRONG), cues[1])
        val arrow = cues[2] as VisualCue.PathArrow
        assertEquals(0.2f, arrow.from.x, 1e-4f)
        assertEquals(0.8f, arrow.to.x, 1e-4f) // centroid of the square zone
        assertEquals(0.8f, arrow.to.y, 1e-4f)
    }

    @Test
    fun `move-close highlights both actors strong-then-gentle with an arrow between them`() {
        val subject = CFix.obj(trackId = 1, cx = 0.1f, cy = 0.5f)
        val target = CFix.obj(trackId = 2, cx = 0.9f, cy = 0.5f)
        val world = CFix.world(objects = listOf(subject, target))
        val cues = CuePlanner.plan(
            spec(ChallengeType.MOVE_CLOSE, listOf(ActorRef.ByTrackId(1), ActorRef.ByTrackId(2)), RuleId.DISTANCE_LESS_THAN),
            world
        )
        assertEquals(VisualCue.Highlight(2, Pulse.GENTLE), cues[0])
        assertEquals(VisualCue.Highlight(1, Pulse.STRONG), cues[1])
        val arrow = cues[2] as VisualCue.PathArrow
        assertEquals(0.1f, arrow.from.x, 1e-4f)
        assertEquals(0.9f, arrow.to.x, 1e-4f)
    }

    @Test
    fun `object present highlights the subject and shows the target pictograph`() {
        val world = CFix.world(objects = listOf(CFix.obj(trackId = 7)))
        val cues = CuePlanner.plan(
            spec(ChallengeType.LAST_RESORT, listOf(ActorRef.ByTrackId(7)), RuleId.OBJECT_PRESENT),
            world
        )
        assertTrue(cues.contains(VisualCue.Highlight(7, Pulse.STRONG)))
        assertTrue(cues.contains(VisualCue.Pictograph(Symbol.Target)))
    }

    @Test
    fun `color match shows a colour swatch matching the param ordinal`() {
        val world = CFix.world(objects = listOf(CFix.obj(trackId = 3)))
        val cues = CuePlanner.plan(
            spec(
                ChallengeType.FIND_COLOR,
                listOf(ActorRef.ByTrackId(3)),
                RuleId.COLOR_MATCH,
                params = mapOf("color" to ColorTag.GREEN.ordinal.toFloat())
            ),
            world
        )
        assertTrue(cues.contains(VisualCue.Highlight(3, Pulse.STRONG)))
        assertTrue(cues.contains(VisualCue.Pictograph(Symbol.ColorSwatch(ColorTag.GREEN))))
    }

    @Test
    fun `MIDDLE and OLDER bands keep only supporting highlight and arrow`() {
        val subject = CFix.obj(trackId = 1, cx = 0.2f, cy = 0.2f)
        val zone = CFix.zone(zoneId = "zA", cx = 0.8f, cy = 0.8f)
        val world = CFix.world(objects = listOf(subject), zones = listOf(zone))
        val cues = CuePlanner.plan(
            spec(
                ChallengeType.DROP_ZONE,
                listOf(ActorRef.ByTrackId(1), ActorRef.ByZone("zA")),
                RuleId.POINT_IN_ZONE,
                ageBand = AgeBand.MIDDLE
            ),
            world
        )
        assertTrue(cues.none { it is VisualCue.ZonePulse })
        assertTrue(cues.any { it is VisualCue.Highlight })
        assertTrue(cues.any { it is VisualCue.PathArrow })
    }

    @Test
    fun `toddler color game keeps the pictograph but middle drops it`() {
        val world = CFix.world(objects = listOf(CFix.obj(trackId = 3)))
        fun cuesFor(band: AgeBand) = CuePlanner.plan(
            spec(
                ChallengeType.FIND_COLOR, listOf(ActorRef.ByTrackId(3)), RuleId.COLOR_MATCH,
                params = mapOf("color" to ColorTag.RED.ordinal.toFloat()), ageBand = band
            ),
            world
        )
        assertTrue(cuesFor(AgeBand.TODDLER).any { it is VisualCue.Pictograph })
        assertTrue(cuesFor(AgeBand.OLDER).none { it is VisualCue.Pictograph })
    }

    @Test
    fun `an unresolvable actor yields no highlight and never crashes`() {
        val world = CFix.world(objects = emptyList())
        val cues = CuePlanner.plan(
            spec(ChallengeType.MOVE_CLOSE, listOf(ActorRef.ByTrackId(1), ActorRef.ByTrackId(2)), RuleId.DISTANCE_LESS_THAN),
            world
        )
        assertTrue(cues.none { it is VisualCue.Highlight })
        assertTrue(cues.none { it is VisualCue.PathArrow })
    }

    @Test
    fun `planning is deterministic for identical inputs`() {
        val subject = CFix.obj(trackId = 1, cx = 0.2f, cy = 0.2f)
        val zone = CFix.zone(zoneId = "zA", cx = 0.8f, cy = 0.8f)
        val world = CFix.world(objects = listOf(subject), zones = listOf(zone))
        val s = spec(ChallengeType.DROP_ZONE, listOf(ActorRef.ByTrackId(1), ActorRef.ByZone("zA")), RuleId.POINT_IN_ZONE)
        assertEquals(CuePlanner.plan(s, world), CuePlanner.plan(s, world))
    }

    @Test
    fun `an out of range step index yields no cues`() {
        val world = CFix.world(objects = listOf(CFix.obj(trackId = 1)))
        val cues = CuePlanner.plan(
            spec(ChallengeType.LAST_RESORT, listOf(ActorRef.ByTrackId(1)), RuleId.OBJECT_PRESENT),
            world,
            stepIndex = 5
        )
        assertTrue(cues.isEmpty())
    }
}
