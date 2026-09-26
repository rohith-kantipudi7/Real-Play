package com.cognex.realplay.challenge

/**
 * Everything a generator needs beyond the raw [com.cognex.realplay.world.WorldState] to score and
 * build a spec (Architecture §6.1, §6.3). Pure JVM.
 *
 *  - [ageBand] / [effectiveTier] — the resolved difficulty context (§8).
 *  - [knobs] — the scene-scaled numeric knobs for [effectiveTier] (§8.1).
 *  - [trackOnlyMode] — when true, generators must reference objects by highlight colour, never by
 *    semantic label (§3.5, §7.1). Automatic, not a separate mode.
 *  - [recentTypes] — the last few challenge types played this session, so the registry can apply a
 *    novelty bonus and avoid repeating the same game (deterministically).
 *  - [seed] — RNG seed for [SelectionMode.OPEN] weighted-random, kept explicit for reproducibility.
 */
data class GenerationContext(
    val ageBand: AgeBand,
    val effectiveTier: Tier,
    val knobs: DifficultyKnobs,
    val trackOnlyMode: Boolean,
    val recentTypes: List<ChallengeType> = emptyList(),
    val seed: Long = 0L
)
