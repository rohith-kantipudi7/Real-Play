package com.cognex.realplay.world

/**
 * Coarse colour classification of an object (Architecture §4). Pure JVM — no Android.
 *
 * [isChromatic] backs the `colorful` affordance (§6): white/gray/black and UNKNOWN are not
 * chromatic, everything else is.
 */
enum class ColorTag(val isChromatic: Boolean) {
    RED(true),
    ORANGE(true),
    YELLOW(true),
    GREEN(true),
    CYAN(true),
    BLUE(true),
    PURPLE(true),
    PINK(true),
    WHITE(false),
    GRAY(false),
    BLACK(false),
    UNKNOWN(false)
}
