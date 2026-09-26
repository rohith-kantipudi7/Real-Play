package com.cognex.realplay.engine

import com.cognex.realplay.challenge.AgeBand

/** The three play modes offered on the mode-select screen (Architecture §13 S6, §10). */
enum class PlayMode { OBJECTS, BODY, MIXED }

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
}
