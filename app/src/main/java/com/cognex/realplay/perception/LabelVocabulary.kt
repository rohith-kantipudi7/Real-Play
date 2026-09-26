package com.cognex.realplay.perception

/**
 * Child-friendly label vocabulary + confidence gating (Architecture §S2, §7.1). Pure JVM — testable.
 *
 * The EfficientDet-Lite0 model emits raw COCO class names ("cell phone", "sports ball", "teddy
 * bear"). Those are fine for tracking but clumsy to say to a child, so [friendly] maps the common
 * ones to short, spoken-friendly names and normalises the rest. Applied at detection time so every
 * downstream layer — tracker, world, generators, the composer digest — sees the same clean label.
 *
 * [isConfidentName] gates naming: below [NAME_CONFIDENCE] a detection's label is treated as
 * unreliable, so affordance nameability (and therefore semantic phrasing) falls back to highlight
 * colour automatically (§3.5, §7.1). This never affects geometry — only wording.
 */
object LabelVocabulary {

    /** Below this score, don't trust the class NAME (still track the object by box + colour). */
    const val NAME_CONFIDENCE = 0.45f

    private val FRIENDLY: Map<String, String> = mapOf(
        "cell phone" to "phone",
        "sports ball" to "ball",
        "teddy bear" to "teddy",
        "remote" to "remote",
        "wine glass" to "glass",
        "potted plant" to "plant",
        "dining table" to "table",
        "tv" to "TV",
        "keyboard" to "keyboard",
        "mouse" to "mouse",
        "book" to "book",
        "cup" to "cup",
        "bottle" to "bottle",
        "bowl" to "bowl",
        "banana" to "banana",
        "apple" to "apple",
        "orange" to "orange",
        "backpack" to "bag",
        "handbag" to "bag",
        "suitcase" to "bag",
        "toothbrush" to "brush",
        "spoon" to "spoon",
        "fork" to "fork",
        "clock" to "clock",
        "vase" to "vase"
    )

    /** Maps a raw detector label to a short, child-friendly name (or a cleaned lowercase form). */
    fun friendly(raw: String): String {
        val key = raw.trim().lowercase()
        if (key.isEmpty()) return ""
        return FRIENDLY[key] ?: key
    }

    /** True when [confidence] is high enough to trust the class NAME (not just the box). */
    fun isConfidentName(confidence: Float): Boolean = confidence >= NAME_CONFIDENCE
}
