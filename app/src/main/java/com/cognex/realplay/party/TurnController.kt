package com.cognex.realplay.party

/**
 * Whose turn it is and the rotation order (Architecture §25). Pure JVM. Sequential "pass the
 * phone" / tripod hot-seat handoff — every format rotates through [Roster.entrants] in order;
 * HEAD_TO_HEAD additionally names the current [opponent] so the UI can show "P1 vs P2".
 */
class TurnController(private val roster: Roster, private val format: PartyFormat) {

    private var index = 0

    val current: Entrant get() = roster.entrants[index]

    /** The other participant for HEAD_TO_HEAD; null for every other format (§25). */
    val opponent: Entrant?
        get() = if (format == PartyFormat.HEAD_TO_HEAD && roster.entrants.size >= 2) {
            roster.entrants[(index + 1) % roster.entrants.size]
        } else null

    /** Advances to the next entrant in rotation order, wrapping at the end of the roster. */
    fun advance(): Entrant {
        index = (index + 1) % roster.entrants.size
        return current
    }

    /** 1-based lap number for [turnsPlayed] completed turns (a "round" = one lap of the roster). */
    fun roundNumber(turnsPlayed: Int): Int = turnsPlayed / roster.entrants.size + 1
}
