package com.cognex.realplay.ui.calibration

import com.cognex.realplay.challenge.ChallengeRegistry
import com.cognex.realplay.challenge.ChallengeType
import com.cognex.realplay.challenge.GenerationContext
import com.cognex.realplay.challenge.RankedCandidate
import com.cognex.realplay.world.SceneCapability

/**
 * The pure, testable model behind the [SceneCapabilityCard] (Architecture §15). No Compose, no
 * Android — so the "K of N" arithmetic and the row selection are unit-tested on the JVM.
 *
 * INVARIANT 14: [total] is read from the LIVE registry size, never hard-coded. [possible] is the
 * count of generators that score above zero in this scene. [rows] omits every zero row. [sparse] is
 * the §15 "this spot is a bit bare" branch (richness < 0.3). [ranked] is the long-press list with
 * every zero-reason.
 */
data class SceneCapabilityCardModel(
    val rows: List<Row>,
    val possible: Int,
    val total: Int,
    val playingType: ChallengeType?,
    val sparse: Boolean,
    val humanOnly: Boolean,
    val ranked: List<RankedCandidate>
) {
    /** One animated count row: a value ticking 0→[value] with a [label]. */
    data class Row(val value: Int, val label: String)

    companion object {
        const val SPARSE_RICHNESS = 0.3f

        /**
         * Builds the card model from the live [cap], the difficulty [ctx], and the LIVE [registry].
         * The winner is the top-ranked PROVEN generator (what RECOMMENDED would pick).
         */
        fun from(
            cap: SceneCapability,
            ctx: GenerationContext,
            registry: ChallengeRegistry
        ): SceneCapabilityCardModel {
            val ranked = registry.rank(cap, ctx)
            val possible = ranked.count { it.score > 0f }
            val total = registry.generators.size
            val winner = ranked.firstOrNull { it.score > 0f }?.type

            val humanOnly = cap.playerCount >= 1 && cap.movableCount == 0
            val rows = if (humanOnly) humanRows(cap) else objectRows(cap)

            return SceneCapabilityCardModel(
                rows = rows.filter { it.value > 0 },
                possible = possible,
                total = total,
                playingType = winner,
                sparse = cap.richness < SPARSE_RICHNESS,
                humanOnly = humanOnly,
                ranked = ranked
            )
        }

        /** Object-scene rows (§15). Pluralised, zero rows removed by the caller. */
        private fun objectRows(cap: SceneCapability): List<Row> = listOf(
            Row(cap.movableCount, plural(cap.movableCount, "thing you can move", "things you can move")),
            Row(cap.containerCount + cap.zoneCount, plural(cap.containerCount + cap.zoneCount, "container", "containers")),
            Row(cap.distinctColors.size, plural(cap.distinctColors.size, "colour", "colours"))
        )

        /** Human-only rows so the card still reads sensibly when the table is empty (§S7 prompt). */
        private fun humanRows(cap: SceneCapability): List<Row> = listOf(
            Row(cap.playerCount, plural(cap.playerCount, "person", "people")),
            Row(if (cap.motionRange > 0.15f) 1 else 0, "lots of movement")
        )

        private fun plural(n: Int, one: String, many: String): String = if (n == 1) one else many
    }
}
