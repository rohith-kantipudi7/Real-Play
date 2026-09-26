package com.cognex.realplay.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exhaustive JVM tests for [CoordinateMapper] — the highest-risk class in the project.
 *
 * Two invariants are checked across all four rotations and both cameras:
 *   1. the image centre (0.5,0.5) always maps to the view centre;
 *   2. the four unit-square corners map to the four view corners (as a set — rotation/mirror may
 *      permute which corner goes where, but the set is exactly the corners).
 *
 * A square view over a square image is used so FILL_CENTER introduces no crop, letting corner
 * positions be asserted exactly.
 */
class CoordinateMapperTest {

    private val eps = 0.5f
    private val view = 1000

    private fun mapper(rotation: Int, front: Boolean) = CoordinateMapper(
        imageW = 100, imageH = 100,
        rotationDegrees = rotation,
        isFrontCamera = front,
        viewW = view, viewH = view,
        scaleType = ScaleType.FILL_CENTER
    )

    @Test
    fun centreMapsToCentreForAllRotationsAndCameras() {
        for (rotation in listOf(0, 90, 180, 270)) {
            for (front in listOf(false, true)) {
                val p = mapper(rotation, front).mapPoint(0.5f, 0.5f)
                assertEquals("cx rot=$rotation front=$front", view / 2f, p.x, eps)
                assertEquals("cy rot=$rotation front=$front", view / 2f, p.y, eps)
            }
        }
    }

    @Test
    fun cornersMapToCornersForAllRotationsAndCameras() {
        val expected = setOf(
            0f to 0f, view.toFloat() to 0f,
            0f to view.toFloat(), view.toFloat() to view.toFloat()
        )
        for (rotation in listOf(0, 90, 180, 270)) {
            for (front in listOf(false, true)) {
                val m = mapper(rotation, front)
                val got = listOf(
                    m.mapPoint(0f, 0f), m.mapPoint(1f, 0f),
                    m.mapPoint(1f, 1f), m.mapPoint(0f, 1f)
                ).map { round(it.x) to round(it.y) }.toSet()
                assertEquals("corners rot=$rotation front=$front", expected, got)
            }
        }
    }

    @Test
    fun rotation0BackIsIdentityInScreenSpace() {
        val m = mapper(0, false)
        assertPoint(0f, 0f, m.mapPoint(0f, 0f))
        assertPoint(1000f, 1000f, m.mapPoint(1f, 1f))
        assertPoint(250f, 250f, m.mapPoint(0.25f, 0.25f))
    }

    @Test
    fun frontCameraMirrorsHorizontallyOnly() {
        val m = mapper(0, true)
        // x is flipped, y unchanged.
        assertPoint(1000f, 0f, m.mapPoint(0f, 0f))
        assertPoint(0f, 1000f, m.mapPoint(1f, 1f))
        assertPoint(750f, 250f, m.mapPoint(0.25f, 0.25f))
    }

    @Test
    fun rotation90BackMovesTopLeftToTopRight() {
        // CW90: normalized (0,0) -> (1,0) in upright space -> view top-right.
        val m = mapper(90, false)
        assertPoint(1000f, 0f, m.mapPoint(0f, 0f))
    }

    @Test
    fun quarterTurnSwapsUprightDimensions() {
        val m = CoordinateMapper(640, 480, 90, false, 1000, 1000)
        assertEquals(480, m.uprightW)
        assertEquals(640, m.uprightH)
    }

    @Test
    fun fillCenterCropsWidthForPortraitViewOverLandscapeImage() {
        // 640x480 landscape image, rotation 0, into a 1000x1000 view.
        // scale = max(1000/640, 1000/480) = 1000/480 -> width overflows and is cropped.
        val m = CoordinateMapper(640, 480, 0, false, 1000, 1000, ScaleType.FILL_CENTER)
        val centre = m.mapPoint(0.5f, 0.5f)
        assertEquals(500f, centre.x, eps)
        assertEquals(500f, centre.y, eps)
        // Left edge maps to a negative x (cropped off-screen), proving horizontal overflow.
        assertTrue(m.mapPoint(0f, 0.5f).x < 0f)
    }

    @Test
    fun mapRectReturnsAxisAlignedBoundsAfterRotation() {
        val m = mapper(90, false)
        val r = m.mapRect(0.25f, 0.25f, 0.75f, 0.75f)
        // Symmetric box stays centred and 500px on a 1000px square view.
        assertEquals(250f, r.left, eps)
        assertEquals(250f, r.top, eps)
        assertEquals(750f, r.right, eps)
        assertEquals(750f, r.bottom, eps)
    }

    private fun assertPoint(x: Float, y: Float, p: ViewPoint) {
        assertEquals(x, p.x, eps)
        assertEquals(y, p.y, eps)
    }

    private fun round(v: Float): Float = Math.round(v).toFloat()
}
