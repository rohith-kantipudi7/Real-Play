package com.cognex.realplay.perception

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM tests for child-friendly label mapping + confidence gating (Architecture §S2, §7.1). */
class LabelVocabularyTest {

    @Test fun maps_common_coco_labels_to_friendly_names() {
        assertEquals("phone", LabelVocabulary.friendly("cell phone"))
        assertEquals("ball", LabelVocabulary.friendly("sports ball"))
        assertEquals("teddy", LabelVocabulary.friendly("teddy bear"))
        assertEquals("bag", LabelVocabulary.friendly("backpack"))
    }

    @Test fun passes_through_unknown_labels_lowercased() {
        assertEquals("cup", LabelVocabulary.friendly("cup"))
        assertEquals("kite", LabelVocabulary.friendly("Kite"))
        assertEquals("", LabelVocabulary.friendly("   "))
    }

    @Test fun confidence_gate_trusts_only_high_scores() {
        assertTrue(LabelVocabulary.isConfidentName(0.9f))
        assertTrue(LabelVocabulary.isConfidentName(LabelVocabulary.NAME_CONFIDENCE))
        assertFalse(LabelVocabulary.isConfidentName(0.2f))
    }
}
