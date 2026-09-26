package com.cognex.realplay.party

import com.cognex.realplay.world.ColorTag

/**
 * One participant in a PARTY session (Architecture §25) — a player or a team. Reuses the §5
 * colour-band identity convention; no new tracking. [memberNames] is non-empty only for a team
 * entrant (TEAM_VS_TEAM), letting members alternate turns under one shared score.
 */
data class Entrant(
    val id: String,
    val name: String,
    val colorTag: ColorTag,
    val memberNames: List<String> = emptyList()
)

/**
 * The roster for one PARTY session (Architecture §25): 2–8 players, or 2–4 teams. Pure JVM.
 * Construction fails closed on an invalid size or duplicate id — a bad roster can never start a
 * confusing rotation.
 */
data class Roster(val entrants: List<Entrant>, val kind: EntrantKind) {
    init {
        val bounds = when (kind) {
            EntrantKind.PLAYER -> MIN_PLAYERS..MAX_PLAYERS
            EntrantKind.TEAM -> MIN_TEAMS..MAX_TEAMS
        }
        require(entrants.size in bounds) {
            "Roster of ${entrants.size} $kind entrants outside allowed range $bounds (§25)"
        }
        require(entrants.map { it.id }.distinct().size == entrants.size) {
            "Roster entrant ids must be unique"
        }
    }

    enum class EntrantKind { PLAYER, TEAM }

    companion object {
        const val MIN_PLAYERS = 2
        const val MAX_PLAYERS = 8
        const val MIN_TEAMS = 2
        const val MAX_TEAMS = 4
    }
}
