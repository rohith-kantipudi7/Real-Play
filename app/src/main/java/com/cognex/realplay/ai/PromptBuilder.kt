package com.cognex.realplay.ai

import com.cognex.realplay.challenge.ChallengeType
import com.cognex.realplay.challenge.GenerationContext
import com.cognex.realplay.challenge.RankedCandidate
import com.cognex.realplay.world.SceneCapability
import com.cognex.realplay.world.WorldState

/** The full prompt bundle handed to the composer (Architecture §6.5). Pure JVM. */
data class PromptBundle(
    val skills: List<SkillDigest>,
    val world: WorldDigest,
    val prompt: String
)

/**
 * Builds the composer prompt from registry output (Architecture §6.5). Pure JVM — fully testable.
 *
 * The prompt is deliberately minimal and closed: it lists ONLY the feasible skills (registry
 * candidates with score > 0) and a compact world summary, and it asks the model to return a strict
 * JSON object choosing one skill id and optionally rewording it. It never exposes rule ids,
 * thresholds or actor geometry — the model arranges skills, it does not measure (§20 invariant 20).
 */
object PromptBuilder {

    /** A one-line, child-facing description of each skill's verifiable primitive. */
    private fun primitiveOf(type: ChallengeType): String = when (type) {
        ChallengeType.LAST_RESORT -> "show or wave any one thing at the camera"
        ChallengeType.MOVE_CLOSE -> "move two objects close together"
        ChallengeType.DROP_ZONE -> "put an object into a target zone or container"
        ChallengeType.FIND_COLOR -> "show the camera an object of a named colour"
        ChallengeType.STATUE_MATCH -> "hold a body pose still"
        ChallengeType.RED_LIGHT_GREEN_LIGHT -> "freeze all motion during the red light"
        ChallengeType.FETCH_RACE -> "pick up a matching object and carry it to a zone"
        ChallengeType.TRIANGLE_BUILD -> "arrange three objects into a triangle"
        ChallengeType.GRAB -> "show the camera the named object"
        ChallengeType.LINE_UP -> "line all the objects up in one straight row"
        ChallengeType.SORT_SIZE -> "order the objects by size from left to right"
        ChallengeType.GROUP_COLOR -> "gather the same-coloured objects together, away from the rest"
        ChallengeType.GROUP_KIND -> "gather the same-kind objects together, away from the rest"
        ChallengeType.COMBO_POSE -> "hold an object and strike a body pose at once"
    }

    fun skillsFrom(ranked: List<RankedCandidate>): List<SkillDigest> =
        ranked.filter { it.score > 0f }
            .sortedWith(compareByDescending<RankedCandidate> { it.score }.thenBy { it.generatorId })
            .map { SkillDigest(it.generatorId, it.type.name, it.feasibility, primitiveOf(it.type)) }

    fun worldFrom(world: WorldState, cap: SceneCapability, ctx: GenerationContext): WorldDigest =
        WorldDigest(
            objectLabels = world.objects.map { it.label }.filter { it.isNotBlank() }.distinct().take(8),
            distinctColors = cap.distinctColors.map { it.name }.sorted(),
            playerCount = cap.playerCount,
            zoneCount = cap.zoneCount,
            ageBand = ctx.ageBand.name,
            tier = ctx.effectiveTier.name,
            trackOnlyMode = cap.trackOnlyMode
        )

    fun build(
        ranked: List<RankedCandidate>,
        world: WorldState,
        cap: SceneCapability,
        ctx: GenerationContext
    ): PromptBundle {
        val skills = skillsFrom(ranked)
        val digest = worldFrom(world, cap, ctx)
        val prompt = render(skills, digest)
        return PromptBundle(skills, digest, prompt)
    }

    private fun render(skills: List<SkillDigest>, w: WorldDigest): String {
        val skillLines = skills.joinToString("\n") { s ->
            "- id=${s.generatorId} type=${s.type} feasibility=${"%.2f".format(s.feasibility)} — ${s.primitive}"
        }
        val objects = if (w.objectLabels.isEmpty()) "(none)" else w.objectLabels.joinToString(", ")
        val colors = if (w.distinctColors.isEmpty()) "(none)" else w.distinctColors.joinToString(", ")
        val naming = if (w.trackOnlyMode) {
            "Object labels are UNRELIABLE — refer to objects by their highlight colour, never by name."
        } else {
            "Object labels are reliable — name the ACTUAL objects the camera sees."
        }
        return buildString {
            appendLine("You are the game composer for RealPlay, a live camera game for children.")
            appendLine("Choose exactly ONE skill id from the list, then write the game's on-screen")
            appendLine("instruction for a ${w.ageBand.lowercase()} child. Difficulty tier: ${w.tier}.")
            appendLine()
            appendLine("Make the instruction feel like a REAL, specific little game — not a generic")
            appendLine("command. Refer to the actual objects and colours the camera sees below, keep")
            appendLine("it to one short, playful, encouraging sentence a child can do right now.")
            appendLine("Give 1–2 tiny hints that help without giving it away.")
            appendLine()
            appendLine("HARD RULES: pick ONLY a listed skill id. Do NOT invent rules, thresholds,")
            appendLine("scoring, new objects, or new actions — a separate physics verifier judges the")
            appendLine("game, so the instruction must match the chosen skill's action exactly. $naming")
            appendLine()
            appendLine("SKILLS (pick one id):")
            appendLine(skillLines)
            appendLine()
            appendLine("SCENE the camera sees now: objects=[$objects] colors=[$colors] players=${w.playerCount} zones=${w.zoneCount}")
            appendLine()
            appendLine("Reply with ONLY a JSON object, no prose:")
            appendLine("{\"generatorId\":\"<one listed id>\",\"instruction\":\"<short fun sentence naming real objects>\",\"hints\":[\"<hint>\"]}")
        }.trim()
    }
}
