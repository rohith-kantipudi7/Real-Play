package com.cognex.realplay.challenge

/**
 * How the registry picks a winner among feasible generators (Architecture §6.1, §6.3). Pure JVM.
 *
 *  - [RECOMMENDED] — deterministic argmax with a stable tie-break by generator id. The same scene
 *    always yields the same pick (§20 invariant 11). Rehearsed reliability without hard-coded levels.
 *  - [OPEN] — weighted-random among candidates with score > 0. Varied; uses a seeded RNG so a given
 *    (scene, seed) is still reproducible.
 *  - [PINNED] — a forced generator id, still fully verified (§20 invariant 11 — cannot fake).
 */
enum class SelectionMode { RECOMMENDED, OPEN, PINNED }
