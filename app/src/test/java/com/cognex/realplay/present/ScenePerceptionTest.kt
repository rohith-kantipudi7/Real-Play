package com.cognex.realplay.present

import com.cognex.realplay.world.ColorTag
import com.cognex.realplay.world.FrameQuality
import com.cognex.realplay.world.NormPoint
import com.cognex.realplay.world.NormRect
import com.cognex.realplay.world.SceneCapability
import com.cognex.realplay.world.TrackedObject
import com.cognex.realplay.world.WorldState
import com.cognex.realplay.world.Zone
import com.cognex.realplay.world.ZoneSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for the pure WorldState → overlays projection (Architecture §3.6, §27). */
class ScenePerceptionTest {

    private fun obj(id: Int, label: String, color: ColorTag?) = TrackedObject(
        trackId = id,
        label = label,
        confidence = 0.9f,
        box = NormRect.fromCenter(0.5f, 0.5f, 0.2f, 0.2f),
        center = NormPoint(0.5f, 0.5f),
        color = color,
        ageFrames = 10,
        lastSeenMs = 0L,
        velocity = NormPoint(0f, 0f),
        stable = true,
        stale = false,
        ambiguous = false
    )

    private fun zone(id: String, color: ColorTag) = Zone(
        zoneId = id,
        polygon = listOf(NormPoint(0f, 0f), NormPoint(0.3f, 0f), NormPoint(0.3f, 0.3f)),
        color = color,
        source = ZoneSource.DETECTED
    )

    private fun world(objects: List<TrackedObject>, zones: List<Zone>) = WorldState(
        frameId = 1L,
        timestampMs = 0L,
        objects = objects,
        players = emptyList(),
        zones = zones,
        quality = FrameQuality(good = true, reason = null),
        affordances = emptyList(),
        capability = SceneCapability.EMPTY
    )

    @Test
    fun `empty world yields empty perception`() {
        val p = ScenePerception.fromWorld(WorldState.EMPTY)
        assertTrue(p.highlights.isEmpty())
        assertTrue(p.zones.isEmpty())
        assertTrue(p.skeletons.isEmpty())
    }

    @Test
    fun `objects become highlights and zones become zone shapes`() {
        val p = ScenePerception.fromWorld(
            world(listOf(obj(7, "cup", ColorTag.RED)), listOf(zone("z1", ColorTag.BLUE)))
        )
        assertEquals(1, p.highlights.size)
        assertEquals(1, p.zones.size)

        val h = p.highlights.first()
        assertEquals(7, h.trackId)
        assertEquals(ColorTag.RED, h.color)
        assertTrue(h.label!!.contains("cup"))
        assertTrue(h.label!!.contains("#7"))

        val z = p.zones.first()
        assertEquals("z1", z.zoneId)
        assertEquals(ColorTag.BLUE, z.color)
        assertEquals(3, z.polygon.size)
    }

    @Test
    fun `target track ids get TARGET emphasis, others NORMAL`() {
        val p = ScenePerception.fromWorld(
            world(listOf(obj(1, "a", ColorTag.RED), obj(2, "b", ColorTag.GREEN)), emptyList()),
            targetTrackIds = setOf(2)
        )
        val byId = p.highlights.associateBy { it.trackId }
        assertEquals(Emphasis.NORMAL, byId[1]!!.emphasis)
        assertEquals(Emphasis.TARGET, byId[2]!!.emphasis)
    }

    @Test
    fun `null object color is preserved`() {
        val p = ScenePerception.fromWorld(world(listOf(obj(3, "x", null)), emptyList()))
        assertNull(p.highlights.first().color)
    }
}
