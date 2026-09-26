package com.cognex.realplay.perception

import com.cognex.realplay.world.ColorTag
import com.cognex.realplay.world.NormRect
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies temporal label smoothing: a single flicker frame never flips the reported label (§12). */
class TrackerLabelSmoothingTest {

    private fun det(label: String, conf: Float = 0.9f) =
        RawDetection(label, conf, NormRect(0.4f, 0.4f, 0.6f, 0.6f), ColorTag.RED)

    @Test fun majority_label_survives_a_single_flicker() {
        val t = Tracker()
        // Promote the track on "cup" (3 confident cup frames).
        t.update(listOf(det("cup")), 1000)
        t.update(listOf(det("cup")), 1100)
        var out = t.update(listOf(det("cup")), 1200)
        assertEquals("cup", out[0].label)
        // One noisy "bottle" frame must not overturn the accumulated majority.
        out = t.update(listOf(det("bottle")), 1300)
        assertEquals("cup", out[0].label)
        // Back to cup — still cup.
        out = t.update(listOf(det("cup")), 1400)
        assertEquals("cup", out[0].label)
    }

    @Test fun sustained_relabel_eventually_wins() {
        val t = Tracker()
        t.update(listOf(det("cup")), 1000)
        t.update(listOf(det("cup")), 1100)
        t.update(listOf(det("cup")), 1200)
        // A sustained new label should take over after enough frames.
        var out = t.update(listOf(det("bottle")), 1300)
        repeat(6) { i -> out = t.update(listOf(det("bottle")), 1400L + i * 100) }
        assertEquals("bottle", out[0].label)
    }
}
