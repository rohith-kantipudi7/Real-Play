package com.cognex.realplay.ai

import com.cognex.realplay.engine.AppSettings
import com.cognex.realplay.engine.FreeformGames

/**
 * Generates a "without verifier" game from the live object list using the cloud/on-device
 * [LanguageModel] (§ user request). Because these games are NOT camera-judged, the model is free to
 * invent richer, harder challenges than the verifiable skill set — it just returns one playful
 * instruction sentence about THESE objects. Any failure (disabled, unavailable, unsafe, empty)
 * returns null so the caller falls back to the deterministic [FreeformGames] templates.
 */
class FreeformComposer(
    private val model: LanguageModel = MockModel(),
    private val enabled: () -> Boolean = { AppSettings.aiComposerEnabled.value }
) {

    /** True when the model could actually produce a proposal. Cheap, non-suspending. */
    fun active(): Boolean = enabled() && model.isAvailable

    /** A model-authored game for [level] from [objectLabels], or null on any failure. */
    suspend fun compose(objectLabels: List<String>, level: Int): FreeformGames.Game? {
        if (!active()) return null
        val raw = runCatching { model.propose(buildPrompt(objectLabels, level)) }.getOrNull() ?: return null
        val instruction = sanitize(raw) ?: return null
        return FreeformGames.Game(
            title = "Challenge $level",
            instruction = instruction,
            hints = listOf("You've got 30 seconds — go!")
        )
    }

    private fun buildPrompt(labels: List<String>, level: Int): String {
        val objs = if (labels.isEmpty()) "whatever they can grab" else labels.joinToString(", ")
        val difficulty = when {
            level <= 2 -> "easy and gentle"
            level <= 5 -> "medium"
            else -> "hard and inventive"
        }
        return buildString {
            appendLine("You are the game master for RealPlay, a live camera game for children.")
            appendLine("The camera sees these objects on the table right now: $objs.")
            appendLine("Invent ONE fun, $difficulty 30-second physical challenge the child can do")
            appendLine("RIGHT NOW using these objects and their body. It is NOT camera-judged, so be")
            appendLine("imaginative — building, arranging, balancing, racing, sorting, posing.")
            appendLine("Name the real objects. Keep it safe: never ask them to climb, jump, throw,")
            appendLine("run around, hit, or eat anything.")
            appendLine("Reply with ONLY the instruction: one short, playful sentence, max 100")
            appendLine("characters, no quotes, no JSON, no preamble.")
        }.trim()
    }

    /** Reduces raw model text to one safe instruction sentence, or null if unusable. */
    private fun sanitize(raw: String): String? {
        // Pull an "instruction" field if the model wrapped it in JSON; else take the first line.
        val fromJson = Regex("\"instruction\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.getOrNull(1)
        var s = (fromJson ?: raw.lineSequence().firstOrNull { it.isNotBlank() } ?: return null).trim()
        s = s.trim('"', '\'', '`', ' ', '-', '*')
        if (s.length > MAX_LEN) s = s.take(MAX_LEN).trim()
        if (s.length < 6) return null
        val lower = s.lowercase()
        if (DENY.any { lower.contains(it) }) return null
        return s
    }

    private companion object {
        const val MAX_LEN = 140
        val DENY = listOf("climb", "jump", "throw", "run out", "hit ", "aim", "swallow", "eat", "bite", "kick")
    }
}
