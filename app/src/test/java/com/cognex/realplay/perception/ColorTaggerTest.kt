package com.cognex.realplay.perception

import com.cognex.realplay.world.ColorTag
import org.junit.Assert.assertEquals
import org.junit.Test

/** JVM unit tests for the pure classification core of [ColorTagger] (Architecture §S2). */
class ColorTaggerTest {

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    private fun fill(count: Int, r: Int, g: Int, b: Int): IntArray =
        IntArray(count) { argb(r, g, b) }

    @Test
    fun classify_pureRed_isRed() {
        assertEquals(ColorTag.RED, ColorTagger.classify(255, 0, 0))
    }

    @Test
    fun classify_pureGreen_isGreen() {
        assertEquals(ColorTag.GREEN, ColorTagger.classify(0, 255, 0))
    }

    @Test
    fun classify_pureBlue_isBlue() {
        assertEquals(ColorTag.BLUE, ColorTagger.classify(0, 0, 255))
    }

    @Test
    fun classify_white_isWhite() {
        assertEquals(ColorTag.WHITE, ColorTagger.classify(255, 255, 255))
    }

    @Test
    fun classify_black_isBlack() {
        assertEquals(ColorTag.BLACK, ColorTagger.classify(0, 0, 0))
    }

    @Test
    fun classify_midGray_isGray() {
        assertEquals(ColorTag.GRAY, ColorTagger.classify(128, 128, 128))
    }

    @Test
    fun classify_nearBlack_isBlack() {
        // v = 40/255 ≈ 0.16 < 0.20 → BLACK regardless of hue.
        assertEquals(ColorTag.BLACK, ColorTagger.classify(40, 10, 10))
    }

    @Test
    fun classify_yellow_isYellow() {
        assertEquals(ColorTag.YELLOW, ColorTagger.classify(255, 255, 0))
    }

    @Test
    fun classify_cyan_isCyan() {
        assertEquals(ColorTag.CYAN, ColorTagger.classify(0, 255, 255))
    }

    @Test
    fun dominant_empty_isUnknown() {
        assertEquals(ColorTag.UNKNOWN, ColorTagger.dominant(IntArray(0)))
    }

    @Test
    fun dominant_allRed_isRed() {
        assertEquals(ColorTag.RED, ColorTagger.dominant(fill(100, 255, 0, 0)))
    }

    @Test
    fun dominant_evenThirds_isUnknown() {
        // 33% red + 33% green + 33% blue → no tag reaches 45% dominance.
        val pixels = fill(30, 255, 0, 0) + fill(30, 0, 255, 0) + fill(30, 0, 0, 255)
        assertEquals(ColorTag.UNKNOWN, ColorTagger.dominant(pixels))
    }

    @Test
    fun dominant_majorityRed_isRed() {
        // 60% red + 40% green → red clears the 45% threshold.
        val pixels = fill(60, 255, 0, 0) + fill(40, 0, 255, 0)
        assertEquals(ColorTag.RED, ColorTagger.dominant(pixels))
    }

    @Test
    fun rgbToHsv_pureRed_hueZero() {
        val (h, s, v) = ColorTagger.rgbToHsv(255, 0, 0)
        assertEquals(0f, h, 0.001f)
        assertEquals(1f, s, 0.001f)
        assertEquals(1f, v, 0.001f)
    }
}
