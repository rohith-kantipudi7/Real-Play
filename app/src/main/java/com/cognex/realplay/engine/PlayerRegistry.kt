package com.cognex.realplay.engine

import com.cognex.realplay.world.TrackedPlayer

/**
 * Tracks the registered players, the ACTIVE player and turn rotation for a session (Architecture
 * §5, §7). Pure JVM — no Android, fully testable.
 *
 * Competition posture (§7): **1 primary player, 2 turn-based secondary; 3–4 is deferred** and is
 * enforced by [maxPlayers]. Registration persists for the session (a turn-based player who steps
 * briefly out of frame keeps their slot); it is cleared only by [reset] at session start.
 *
 * Identity numbers come from [PlayerTracker][com.cognex.realplay.perception.pose.PlayerTracker];
 * this registry only decides who is *registered* and whose *turn* it is. Ambiguous players never
 * earn a slot (their rules return Unsure per §20 invariant 2).
 */
class PlayerRegistry(val maxPlayers: Int = 2) {

    private val registeredIds = LinkedHashSet<Int>()

    /** The player whose turn it is, or null before anyone is registered. */
    var activePlayerId: Int? = null
        private set

    /** Registered player ids in registration order. */
    fun registered(): List<Int> = registeredIds.toList()

    /** Whether [id] holds a registered slot. */
    fun isRegistered(id: Int): Boolean = id in registeredIds

    /**
     * Folds the current tracked players into the registry: newly confirmed, non-ambiguous players
     * claim a free slot (up to [maxPlayers]) in first-seen order. The first registrant becomes the
     * active player. Returns the ids registered this call.
     */
    fun sync(players: List<TrackedPlayer>): List<Int> {
        val added = ArrayList<Int>()
        for (p in players) {
            if (p.ambiguous) continue
            if (p.playerId in registeredIds) continue
            if (registeredIds.size >= maxPlayers) continue
            registeredIds.add(p.playerId)
            added.add(p.playerId)
            if (activePlayerId == null) activePlayerId = p.playerId
        }
        return added
    }

    /** Makes [id] the active player if it is registered; otherwise leaves the active player unchanged. */
    fun setActive(id: Int) {
        if (id in registeredIds) activePlayerId = id
    }

    /**
     * Advances the turn to the next registered player in order (wrapping around). With a single
     * registered player the active player is unchanged. Returns the new active player, or null if
     * nobody is registered.
     */
    fun advanceTurn(): Int? {
        val ids = registered()
        if (ids.isEmpty()) return null
        val current = activePlayerId
        val idx = ids.indexOf(current)
        activePlayerId = ids[(idx + 1) % ids.size]
        return activePlayerId
    }

    /** Clears all registration and turn state — call at session start (§5). */
    fun reset() {
        registeredIds.clear()
        activePlayerId = null
    }
}
