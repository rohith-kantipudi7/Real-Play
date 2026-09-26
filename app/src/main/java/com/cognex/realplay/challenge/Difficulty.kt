package com.cognex.realplay.challenge

/**
 * Difficulty resolution (Architecture §8). Pure JVM.
 *
 *   effectiveTier = min( skillTier(rating), sceneTier(richness), ageCap(ageBand) )
 *
 * "min" is by tier ordinal (EASY < MEDIUM < HARD). A sparse scene can never produce a hard
 * challenge regardless of skill; a young age band caps it further. The structural step budget is
 * additionally clamped for TODDLER/EARLY (§20 invariant 17) via [ageStepCap].
 */
object Difficulty {

    private const val LOWER = 33f
    private const val UPPER = 66f
    private const val HYST = 5f

    /** richness < 0.35 → EASY · < 0.65 → MEDIUM · else HARD. */
    fun sceneTier(richness: Float): Tier = when {
        richness < 0.35f -> Tier.EASY
        richness < 0.65f -> Tier.MEDIUM
        else -> Tier.HARD
    }

    /**
     * rating 0–100 with boundaries 33/66 and ±5 hysteresis around the previous tier so a rating
     * hovering on a boundary does not flip every round. With no [previous] tier the raw boundaries
     * are used.
     */
    fun skillTier(rating: Float, previous: Tier? = null): Tier = when (previous) {
        null -> when {
            rating < LOWER -> Tier.EASY
            rating < UPPER -> Tier.MEDIUM
            else -> Tier.HARD
        }
        Tier.EASY -> when {
            rating >= UPPER + HYST -> Tier.HARD
            rating >= LOWER + HYST -> Tier.MEDIUM
            else -> Tier.EASY
        }
        Tier.MEDIUM -> when {
            rating < LOWER - HYST -> Tier.EASY
            rating >= UPPER + HYST -> Tier.HARD
            else -> Tier.MEDIUM
        }
        Tier.HARD -> when {
            rating < LOWER - HYST -> Tier.EASY
            rating < UPPER - HYST -> Tier.MEDIUM
            else -> Tier.HARD
        }
    }

    /** The tier ceiling per age band (§10). */
    fun ageCapTier(band: AgeBand): Tier = when (band) {
        AgeBand.TODDLER -> Tier.EASY
        AgeBand.EARLY -> Tier.MEDIUM
        AgeBand.MIDDLE -> Tier.HARD
        AgeBand.OLDER -> Tier.HARD
    }

    /** The structural step ceiling per age band (§10, §20 invariant 17). Always 1 for TODDLER/EARLY. */
    fun ageStepCap(band: AgeBand): Int = when (band) {
        AgeBand.TODDLER -> 1
        AgeBand.EARLY -> 1
        AgeBand.MIDDLE -> 2
        AgeBand.OLDER -> 2
    }

    fun effectiveTier(rating: Float, richness: Float, band: AgeBand, previous: Tier? = null): Tier =
        minOf(skillTier(rating, previous), sceneTier(richness), ageCapTier(band))
}
