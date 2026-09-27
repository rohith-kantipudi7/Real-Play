package com.cognex.realplay.engine

import com.cognex.realplay.challenge.AgeBand

/** The three play modes offered on the mode-select screen (Architecture §13 S6, §10). */
enum class PlayMode { OBJECTS, BODY, MIXED }

/**
 * The player-facing difficulty tier (§ audience control). Each maps to an age band + starting
 * difficulty; TODDLER is restricted to basic games (find + colour) by the registry.
 */
enum class Audience(val label: String, val band: AgeBand) {
    TODDLER("Toddler", AgeBand.TODDLER),
    KIDS("Kids", AgeBand.EARLY),
    PLAYER("Player", AgeBand.MIDDLE),
    PRO("Pro", AgeBand.OLDER)
}

/**
 * The choices the parent makes on [com.cognex.realplay.ui.modeselect.ModeSelectScreen] (mode, age
 * band, player count), read by the [GameViewModel] when a session starts. A tiny process-lifetime
 * holder so the selection survives the navigation hop into the game without threading it through
 * every route argument. Pure config — it carries no game logic.
 */
object SessionConfig {
    @Volatile
    var ageBand: AgeBand = AgeBand.MIDDLE

    @Volatile
    var mode: PlayMode = PlayMode.OBJECTS

    @Volatile
    var playerCount: Int = 1

    /** The selected tier. Setting it also updates [ageBand], the band the engine reads. */
    @Volatile
    var audience: Audience = Audience.PLAYER
        set(value) {
            field = value
            ageBand = value.band
        }
}
