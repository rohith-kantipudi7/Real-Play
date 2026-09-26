package com.cognex.realplay.ai

import com.cognex.realplay.challenge.SafetyFilter

/**
 * The composer's hard boundary (Architecture §4.1, §6.5, §20 invariants 18–20). Pure JVM — testable.
 *
 * Parses raw model text into a [ComposerProposal] and validates it against the feasible skill set.
 * The model may only:
 *   - pick a [ComposerProposal.generatorId] that is one of the feasible skills (score > 0), and
 *   - reword the instruction / hints within a safe length and vocabulary.
 * Everything else — rule ids, thresholds, actor bindings, step counts — is produced by the
 * deterministic generator, so there is nothing here for the model to corrupt. A rejected proposal
 * is discarded and the deterministic spec is used, with zero user-visible difference.
 */
object SchemaValidator {

    private const val MAX_INSTRUCTION = 140
    private const val MAX_HINT = 120
    private const val MAX_HINTS = 3

    /** Verbs the closed rule set can't express and we never want spoken to a child (§11). */
    private val DENY_ACTIONS = listOf(
        "climb", "jump", "throw", "run out", "hit", "aim", "swallow", "eat", "bite", "kick"
    )

    sealed interface Result {
        data class Accepted(val proposal: ComposerProposal) : Result
        data class Rejected(val reason: String) : Result
    }

    /**
     * Validates [raw] model text against the [feasibleIds] (the skill ids with score > 0).
     * Returns [Result.Accepted] with a sanitised proposal, or [Result.Rejected] with a reason.
     */
    fun validate(raw: String?, feasibleIds: Set<String>): Result {
        if (raw.isNullOrBlank()) return Result.Rejected("empty proposal")
        val obj = extractJsonObject(raw) ?: return Result.Rejected("no JSON object")

        val id = stringField(obj, "generatorId")?.trim()
            ?: return Result.Rejected("missing generatorId")
        if (id !in feasibleIds) return Result.Rejected("unknown/infeasible skill: $id")

        val instruction = stringField(obj, "instruction")?.trim()?.takeIf { it.isNotEmpty() }
        if (instruction != null) {
            if (instruction.length > MAX_INSTRUCTION) return Result.Rejected("instruction too long")
            deniedTerm(instruction)?.let { return Result.Rejected("unsafe instruction: $it") }
        }

        val hints = arrayField(obj, "hints")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.take(MAX_HINTS)
            ?.also { list ->
                list.firstOrNull { it.length > MAX_HINT }?.let { return Result.Rejected("hint too long") }
                list.firstNotNullOfOrNull { deniedTerm(it) }?.let { return Result.Rejected("unsafe hint: $it") }
            }
            ?.takeIf { it.isNotEmpty() }

        return Result.Accepted(ComposerProposal(id, instruction, hints))
    }

    /** The first denied word/phrase found in [text] (case-insensitive), or null when clean. */
    private fun deniedTerm(text: String): String? {
        val lower = text.lowercase()
        SafetyFilter.DENY_ALWAYS.firstOrNull { lower.contains(it) }?.let { return it }
        DENY_ACTIONS.firstOrNull { lower.contains(it) }?.let { return it }
        return null
    }

    // ── minimal, dependency-free JSON field extraction (pure JVM, unit-testable) ──────────────
    // We only need three top-level fields from a flat object, so a tolerant scanner is simpler and
    // more portable than pulling in org.json (which is stubbed in local unit tests).

    private fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return raw.substring(start, end + 1)
    }

    private fun stringField(json: String, key: String): String? {
        val m = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(json)
            ?: return null
        return unescape(m.groupValues[1])
    }

    private fun arrayField(json: String, key: String): List<String>? {
        val idx = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\\[").find(json)?.range?.last ?: return null
        val close = json.indexOf(']', idx)
        if (close < 0) return null
        val body = json.substring(idx + 1, close)
        return Regex("\"((?:\\\\.|[^\"\\\\])*)\"").findAll(body).map { unescape(it.groupValues[1]) }.toList()
    }

    private fun unescape(s: String): String =
        s.replace("\\\"", "\"").replace("\\n", " ").replace("\\t", " ").replace("\\\\", "\\").trim()
}
