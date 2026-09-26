package com.cognex.realplay.engine

import com.cognex.realplay.party.PartySession

/**
 * Process-lifetime holder for the active PARTY session (Architecture §25), mirroring
 * [SessionConfig]. Null means SOLO. [GameViewModel] reads it live, the same way it reads
 * [SessionConfig.ageBand] — so entering/leaving PARTY never requires a ViewModel factory.
 */
object PartyRuntime {
    @Volatile
    var active: PartySession? = null

    fun clear() { active = null }
}
