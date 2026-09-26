package com.cognex.realplay.ai

import com.cognex.realplay.world.SceneCapability

/**
 * A stable, cheap fingerprint of a [SceneCapability] (Architecture v3.7). Pure JVM.
 *
 * Two frames with the same counts/colours/flags are "the same scene" for composer purposes, so
 * repeated proposals for the identical (skill, scene, tier) triple can be served from
 * [ComposerCache] instead of calling the model again — this is what makes speculative
 * regeneration cheap when the room hasn't materially changed between challenges.
 */
fun capabilityDigest(cap: SceneCapability): String = buildString {
    append(cap.movableCount).append('-')
    append(cap.handheldCount).append('-')
    append(cap.containerCount).append('-')
    append(cap.landmarkCount).append('-')
    append(cap.playerCount).append('-')
    append(cap.zoneCount).append('-')
    append(cap.distinctColors.map { it.name }.sorted().joinToString(","))
    append('-').append(cap.trackOnlyMode)
}

/** The cache key: which skill, over what scene, at what difficulty tier (§6.5). */
data class ComposerCacheKey(val generatorId: String, val capabilityDigest: String, val tier: String)

/**
 * A small bounded cache of raw model proposal text, keyed by (skill, capabilityDigest, tier)
 * (Architecture v3.7). Pure JVM, thread-safe. Avoids a redundant cloud/on-device call when the
 * scene hasn't changed since the last proposal for the same skill and tier — purely a latency/cost
 * optimisation on the composition line; it never changes what gets validated or how it's judged.
 */
class ComposerCache(private val maxEntries: Int = DEFAULT_MAX_ENTRIES) {
    private val map = LinkedHashMap<ComposerCacheKey, String>()

    @Synchronized
    fun get(key: ComposerCacheKey): String? = map[key]

    @Synchronized
    fun put(key: ComposerCacheKey, rawProposal: String) {
        map[key] = rawProposal
        if (map.size > maxEntries) {
            val oldest = map.keys.firstOrNull() ?: return
            map.remove(oldest)
        }
    }

    @Synchronized
    fun clear() = map.clear()

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 32
    }
}
