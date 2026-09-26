package com.cognex.realplay.present

/**
 * VrTarget (Architecture v3.6-D — roadmap, designed not built as a real stereoscopic renderer).
 * This is the one piece of genuine logic a VR target would need: turning a detected object's
 * screen-space size into a stereo parallax offset, so two eye views of the SAME scene show close
 * objects with more separation than far ones. `ui/present/VrPreviewScreen` uses it to render an
 * honest on-phone CONCEPT preview of the idea — not real headset rendering. Pure JVM.
 */
object VrTarget {
    private const val MIN_PARALLAX = 0.004f
    private const val MAX_PARALLAX = 0.035f

    /** Normalized-space parallax half-offset for a highlight box of [normalizedArea] (0..1). */
    fun parallaxFor(normalizedArea: Float): Float {
        val area = normalizedArea.coerceIn(0f, 1f)
        return MIN_PARALLAX + (MAX_PARALLAX - MIN_PARALLAX) * area
    }
}
