package com.cognex.realplay.world

import kotlin.math.min

/**
 * Session-accumulated player dynamics that a single [WorldState] frame cannot compute on its own
 * (Architecture §6.2). Populated by the pose pipeline (S4+); [EMPTY] until then, which keeps a
 * player-less or pose-less scene on Branch A.
 *
 *  - [poseVariety] — distinct pose-feature clusters this session, already normalised to 0..1 (/5).
 *  - [motionRange] — 95th-percentile landmark displacement over a 3 s window, normalised 0..1.
 *
 * [frameCoverage] is NOT here because it is computable from the current frame's torso box.
 */
data class PlayerDynamics(val poseVariety: Float, val motionRange: Float) {
    companion object {
        val EMPTY = PlayerDynamics(0f, 0f)
    }
}

/** Which §6.2 richness weighting is active. */
enum class RichnessBranch { BASE, A_NO_PLAYERS, B_NO_MOVABLE }

/**
 * The richness score with every weighted term kept separate (Architecture §6.2). The dev overlay
 * renders [terms] and [branch] so the weighting can be watched live.
 */
data class RichnessBreakdown(
    val total: Float,
    val branch: RichnessBranch,
    val terms: LinkedHashMap<String, Float>
)

/**
 * Scene richness with SYMMETRIC renormalisation (Architecture §6.2). Pure JVM.
 *
 * Base weighting assumes an object-rich scene with players. Two mandatory, mirror-image branches
 * fix the degenerate cases:
 *  - BRANCH A (no players): the 0.15 player weight moves into movable (+0.10) and colours (+0.05),
 *    so an object-only scene still exceeds 0.85 richness (essential — pose is P1 and may be cut).
 *  - BRANCH B (no movable objects, ≥1 player): the 0.30 movable + 0.15 container weight moves into
 *    the player-derived terms, so an actively-moving, well-framed person exceeds 0.70 richness and
 *    can reach MEDIUM/HARD (essential — without it a human-only scene is capped at ~0.20 forever).
 */
object Richness {

    fun compute(
        movableCount: Int,
        distinctColorCount: Int,
        playerCount: Int,
        containerOrZoneCount: Int,
        spread: Float,
        stability: Float,
        poseVariety: Float,
        motionRange: Float,
        frameCoverage: Float
    ): RichnessBreakdown {
        val movableN = min(movableCount, 4) / 4f
        val colorsN = min(distinctColorCount, 4) / 4f
        val playersN = min(playerCount, 2) / 2f
        val hasContainer = if (containerOrZoneCount > 0) 1f else 0f
        val terms = LinkedHashMap<String, Float>()

        val branch = when {
            playerCount == 0 -> RichnessBranch.A_NO_PLAYERS
            movableCount == 0 && playerCount >= 1 -> RichnessBranch.B_NO_MOVABLE
            else -> RichnessBranch.BASE
        }

        when (branch) {
            RichnessBranch.BASE -> {
                terms["movable"] = 0.30f * movableN
                terms["colors"] = 0.20f * colorsN
                terms["players"] = 0.15f * playersN
                terms["container"] = 0.15f * hasContainer
                terms["spread"] = 0.10f * spread.coerceIn(0f, 1f)
                terms["stable"] = 0.10f * stability.coerceIn(0f, 1f)
            }
            RichnessBranch.A_NO_PLAYERS -> {
                // 0.15 player weight → +0.10 movable, +0.05 colours.
                terms["movable"] = 0.40f * movableN
                terms["colors"] = 0.25f * colorsN
                terms["container"] = 0.15f * hasContainer
                terms["spread"] = 0.10f * spread.coerceIn(0f, 1f)
                terms["stable"] = 0.10f * stability.coerceIn(0f, 1f)
            }
            RichnessBranch.B_NO_MOVABLE -> {
                // 0.30 movable + 0.15 container weight → player-derived terms.
                terms["players"] = 0.20f * playersN
                terms["poseVar"] = 0.20f * poseVariety.coerceIn(0f, 1f)
                terms["motion"] = 0.15f * motionRange.coerceIn(0f, 1f)
                terms["coverage"] = 0.15f * frameCoverage.coerceIn(0f, 1f)
                terms["colors"] = 0.10f * colorsN
                terms["spread"] = 0.10f * spread.coerceIn(0f, 1f)
                terms["stable"] = 0.10f * stability.coerceIn(0f, 1f)
            }
        }

        val total = terms.values.sum().coerceIn(0f, 1f)
        return RichnessBreakdown(total, branch, terms)
    }
}
