package com.cognex.realplay.engine

import com.cognex.realplay.challenge.AgeBand
import com.cognex.realplay.challenge.Difficulty
import com.cognex.realplay.challenge.DifficultyKnobs
import com.cognex.realplay.challenge.Tier

/**
 * The single place difficulty is resolved for a challenge (Architecture §8, §13 S9). Pure JVM — no
 * Android, fully unit-tested.
 *
 * It composes the three independent ceilings into the one tier actually played and, crucially for
 * the demo, explains WHY in a sentence a judge can read off the dev overlay:
 *
 *   effectiveTier = min( skillTier(rating), sceneTier(richness), ageCap(ageBand) )
 *
 * The rating itself lives on [GameSession] (the §8 deltas: +12 two passes · +6 fast · −10 fail ·
 * −18 two fails · Unsure = 0); this director never mutates it. It only turns the current rating +
 * scene + age into a played tier, the scene-scaled numeric knobs (§8.1), and a human explanation.
 */
object DifficultyDirector {

    /**
     * The fully resolved difficulty for one challenge. [knobs] are already scene-scaled (§8.1).
     * [explanation] is the presentable "why" string (e.g. "skill=HARD but richness 0.41 → playing
     * MEDIUM") the dev overlay shows.
     */
    data class Resolution(
        val effectiveTier: Tier,
        val knobs: DifficultyKnobs,
        val skillTier: Tier,
        val sceneTier: Tier,
        val ageCap: Tier,
        val bindingCeiling: Ceiling,
        val explanation: String
    )

    /** Which of the three ceilings actually decided the played tier (the lowest one). */
    enum class Ceiling { SKILL, SCENE, AGE }

    /**
     * Resolves the played tier for [rating] (0..100) in a scene of [richness]/[spread]/[stability]
     * for age [band]. [previousTier] feeds the skill-tier ±5 hysteresis (§8) so a rating hovering on
     * a boundary does not flip every round.
     */
    fun resolve(
        rating: Float,
        richness: Float,
        band: AgeBand,
        spread: Float,
        stability: Float,
        previousTier: Tier? = null
    ): Resolution {
        val skill = Difficulty.skillTier(rating, previousTier)
        val scene = Difficulty.sceneTier(richness)
        val age = Difficulty.ageCapTier(band)
        val effective = minOf(skill, scene, age)
        val knobs = DifficultyKnobs.forTier(effective, spread, stability)

        // The binding ceiling is the lowest; on a tie, prefer SKILL, then SCENE, then AGE, so the
        // explanation names the most player-relevant reason first.
        val binding = when {
            skill.ordinal <= scene.ordinal && skill.ordinal <= age.ordinal -> Ceiling.SKILL
            scene.ordinal <= age.ordinal -> Ceiling.SCENE
            else -> Ceiling.AGE
        }

        return Resolution(
            effectiveTier = effective,
            knobs = knobs,
            skillTier = skill,
            sceneTier = scene,
            ageCap = age,
            bindingCeiling = binding,
            explanation = explain(skill, scene, age, effective, richness, band)
        )
    }

    private fun explain(
        skill: Tier,
        scene: Tier,
        age: Tier,
        effective: Tier,
        richness: Float,
        band: AgeBand
    ): String {
        // No ceiling below skill — skill is what you play.
        if (effective == skill) return "skill=$skill → playing $effective"
        // Scene is the (or a) binding ceiling below skill: name the richness that caused it.
        if (scene == effective && scene.ordinal <= age.ordinal) {
            return "skill=$skill but richness ${"%.2f".format(richness)} → playing $effective"
        }
        // Otherwise the age band is the binding ceiling.
        return "skill=$skill but age $band → playing $effective"
    }
}
