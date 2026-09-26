package com.cognex.realplay.ai

import com.cognex.realplay.challenge.CFix
import com.cognex.realplay.world.ColorTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** ComposerCache + capabilityDigest — the (skill, scene, tier) lookup key (Architecture v3.7). */
class ComposerCacheTest {

    @Test
    fun missReturnsNull_thenHitReturnsStoredValue() {
        val cache = ComposerCache()
        val key = ComposerCacheKey("G1", "digest-a", "MEDIUM")
        assertNull(cache.get(key))
        cache.put(key, """{"generatorId":"G1"}""")
        assertEquals("""{"generatorId":"G1"}""", cache.get(key))
    }

    @Test
    fun differentKeys_areIndependent() {
        val cache = ComposerCache()
        cache.put(ComposerCacheKey("G1", "digest-a", "MEDIUM"), "one")
        assertNull(cache.get(ComposerCacheKey("G1", "digest-b", "MEDIUM")))
        assertNull(cache.get(ComposerCacheKey("G2", "digest-a", "MEDIUM")))
        assertNull(cache.get(ComposerCacheKey("G1", "digest-a", "HARD")))
    }

    @Test
    fun evictsOldestEntry_pastMaxSize() {
        val cache = ComposerCache(maxEntries = 2)
        cache.put(ComposerCacheKey("G1", "a", "EASY"), "1")
        cache.put(ComposerCacheKey("G1", "b", "EASY"), "2")
        cache.put(ComposerCacheKey("G1", "c", "EASY"), "3")
        assertNull(cache.get(ComposerCacheKey("G1", "a", "EASY")))
        assertEquals("3", cache.get(ComposerCacheKey("G1", "c", "EASY")))
    }

    @Test
    fun capabilityDigest_sameCounts_sameDigest() {
        val cap = CFix.cap(movableCount = 2, playerCount = 1, distinctColors = setOf(ColorTag.RED, ColorTag.BLUE))
        val other = CFix.cap(movableCount = 2, playerCount = 1, distinctColors = setOf(ColorTag.BLUE, ColorTag.RED))
        assertEquals(capabilityDigest(cap), capabilityDigest(other))
    }

    @Test
    fun capabilityDigest_differentCounts_differentDigest() {
        val a = CFix.cap(movableCount = 2)
        val b = CFix.cap(movableCount = 3)
        assertNotEquals(capabilityDigest(a), capabilityDigest(b))
    }
}
