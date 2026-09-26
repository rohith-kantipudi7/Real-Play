package com.cognex.realplay.perception

import com.cognex.realplay.verify.RuleId
import org.junit.Assert.assertEquals
import org.junit.Test

/** JVM tests for the perceiver-set derivation (Architecture §12, §20 invariant 12). */
class PerceptionSchedulerTest {

    @Test
    fun `an object-only challenge enables only the object detector`() {
        assertEquals(
            setOf(Perceiver.OBJECT),
            PerceptionScheduler.perceiversFor(listOf(RuleId.DISTANCE_LESS_THAN, RuleId.OBJECT_PRESENT))
        )
    }

    @Test
    fun `a pure-pose challenge enables only the pose detector`() {
        assertEquals(
            setOf(Perceiver.POSE),
            PerceptionScheduler.perceiversFor(listOf(RuleId.POSE_MATCH))
        )
        assertEquals(
            setOf(Perceiver.POSE),
            PerceptionScheduler.perceiversFor(listOf(RuleId.MOTION_BELOW))
        )
    }

    @Test
    fun `a player-object challenge enables both detectors`() {
        assertEquals(
            setOf(Perceiver.POSE, Perceiver.OBJECT),
            PerceptionScheduler.perceiversFor(listOf(RuleId.PLAYER_HOLDS_OBJECT))
        )
    }

    @Test
    fun `a mixed rule set enables both detectors`() {
        assertEquals(
            setOf(Perceiver.POSE, Perceiver.OBJECT),
            PerceptionScheduler.perceiversFor(listOf(RuleId.POSE_MATCH, RuleId.OBJECT_PRESENT))
        )
    }

    @Test
    fun `an empty rule set defaults to the object detector`() {
        assertEquals(setOf(Perceiver.OBJECT), PerceptionScheduler.perceiversFor(emptyList()))
    }

    @Test
    fun `update tracks the current set across changes`() {
        val scheduler = PerceptionScheduler()
        assertEquals(setOf(Perceiver.OBJECT), scheduler.current)
        scheduler.update(listOf(RuleId.POSE_MATCH))
        assertEquals(setOf(Perceiver.POSE), scheduler.current)
        scheduler.update(listOf(RuleId.DISTANCE_LESS_THAN))
        assertEquals(setOf(Perceiver.OBJECT), scheduler.current)
    }
}
