package com.cognex.realplay.challenge

import com.cognex.realplay.world.SceneCapability

/**
 * The scene requirements a generator needs before it can even be considered (Architecture §6.3).
 * Pure JVM. Integer minimums are checked against [SceneCapability] counts; the two boolean flags
 * are checked against the §3.5 capability flags. G0 keeps this EMPTY so the registry stays total
 * (§20 invariant 13).
 */
data class Requirement(
    val minMovable: Int = 0,
    val minHandheld: Int = 0,
    val minContainerOrZone: Int = 0,
    val minLandmark: Int = 0,
    val minPlayers: Int = 0,
    val minDistinctColors: Int = 0,
    val needsNameable: Boolean = false,          // requires semanticLabelsAvailable
    val needsPlanarSurface: Boolean = false      // requires planarSurfaceAvailable
) {
    /**
     * Returns the FIRST unmet requirement as a human-readable reason (rendered by the UI, §6.3),
     * or null when every requirement is satisfied. Each zero carries a specific "why".
     */
    fun unmetReason(cap: SceneCapability): String? {
        if (cap.movableCount < minMovable) return "needs $minMovable movable, found ${cap.movableCount}"
        if (cap.handheldCount < minHandheld) return "needs $minHandheld handheld, found ${cap.handheldCount}"
        val containerOrZone = cap.containerCount + cap.zoneCount
        if (containerOrZone < minContainerOrZone)
            return "needs $minContainerOrZone container/zone, found $containerOrZone"
        if (cap.landmarkCount < minLandmark) return "needs $minLandmark landmark, found ${cap.landmarkCount}"
        if (cap.playerCount < minPlayers) return "no player detected"
        if (cap.distinctColors.size < minDistinctColors)
            return "needs $minDistinctColors colours, found ${cap.distinctColors.size}"
        if (needsNameable && !cap.semanticLabelsAvailable) return "labels unreliable"
        if (needsPlanarSurface && !cap.planarSurfaceAvailable) return "surface not calibrated"
        return null
    }

    companion object {
        /** G0's requirement — nothing at all (§6.4, §20 invariant 13). */
        val NONE = Requirement()
    }
}
