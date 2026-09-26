package com.cognex.realplay.challenge.generators

import com.cognex.realplay.challenge.ActorRef
import com.cognex.realplay.challenge.AgeBand
import com.cognex.realplay.challenge.ChallengeGenerator
import com.cognex.realplay.challenge.ChallengeSpec
import com.cognex.realplay.challenge.ChallengeType
import com.cognex.realplay.challenge.GenerationContext
import com.cognex.realplay.challenge.Requirement
import com.cognex.realplay.challenge.Tier
import com.cognex.realplay.challenge.VerificationStep
import com.cognex.realplay.verify.RuleId
import com.cognex.realplay.world.Affordance
import com.cognex.realplay.world.SceneCapability
import com.cognex.realplay.world.TrackedObject
import com.cognex.realplay.world.TrackedPlayer
import com.cognex.realplay.world.WorldState

/**
 * G0 · Last Resort (Architecture §6.4, §20 invariant 13). Pure JVM.
 *
 * The generator that makes the registry TOTAL. It requires NOTHING — no zone, no semantic label, no
 * player — so it is always selectable, and always loses to a real game (feasibility 0.05 < every
 * other generator). This is what makes the OPEN-mode finale on an unknown table safe. **Do not add
 * requirements to G0.**
 *
 * generate() picks the safest available variant:
 *   - a confirmed player present  → "Wave at me!"                    (LIMB_RAISED + MOTION_ABOVE)
 *   - else a tracked object present → "Bring the glowing object close" (OBJECT_PRESENT, area ≥ 6%)
 *   - else (empty scene)          → the wave variant anyway, which simply coaches "step into frame"
 *     via Unsure — keeping the registry total without ever crashing.
 */
class G0LastResortGenerator : ChallengeGenerator {

    override val type = ChallengeType.LAST_RESORT
    override val id = "G0"
    override val proven = true
    override val requires = Requirement.NONE

    /** 0.05 flat whenever anything is present; 0 only on a truly empty scene. */
    override fun feasibility(cap: SceneCapability, ctx: GenerationContext): Float =
        if (cap.playerCount >= 1 || cap.movableCount >= 1 || cap.nameableCount >= 1) 0.05f
        else 0.05f  // still selectable even when counts are unknown — G0 must never gate itself out

    override fun maxStepsForTier(tier: Tier): Int = 1

    override fun ageGate(band: AgeBand): Float = 1f  // offered in every band (§10)

    override fun generate(
        world: WorldState,
        aff: List<Affordance>,
        ctx: GenerationContext,
        stepBudget: Int
    ): ChallengeSpec {
        val player = bestPlayer(world)
        if (player != null) return playerVariant(player, ctx)

        val obj = bestObject(world)
        if (obj != null) return objectVariant(obj, ctx)

        // Nothing usable — return the wave variant referencing whatever player slot exists (or 0).
        // At verification time this yields Unsure ("step into the frame"), never a crash.
        return playerVariant(null, ctx)
    }

    private fun playerVariant(player: TrackedPlayer?, ctx: GenerationContext): ChallengeSpec {
        val playerRef = ActorRef.ByPlayer(player?.playerId ?: 0)
        val hold = ctx.knobs.holdMs
        return ChallengeSpec(
            id = "G0-wave",
            type = type,
            tier = ctx.effectiveTier,
            ageBand = ctx.ageBand,
            actors = listOf(playerRef),
            instruction = "Wave at me!",
            steps = listOf(
                VerificationStep(RuleId.LIMB_RAISED, mapOf("limb" to 0f, "margin" to 0.04f), holdMs = hold),
                VerificationStep(RuleId.MOTION_ABOVE, mapOf("threshold" to 0.12f), holdMs = hold)
            ),
            timeLimitMs = null,
            baseScore = 10,
            hints = listOf("Lift your hand up high", "Give me a big wave")
        )
    }

    private fun objectVariant(obj: TrackedObject, ctx: GenerationContext): ChallengeSpec {
        return ChallengeSpec(
            id = "G0-bring-${obj.trackId}",
            type = type,
            tier = ctx.effectiveTier,
            ageBand = ctx.ageBand,
            actors = listOf(ActorRef.ByTrackId(obj.trackId)),
            instruction = "Bring the glowing object close to the camera.",
            steps = listOf(
                VerificationStep(RuleId.OBJECT_PRESENT, mapOf("minArea" to 0.06f), holdMs = ctx.knobs.holdMs)
            ),
            timeLimitMs = null,
            baseScore = 10,
            hints = listOf("Hold it up toward the camera", "A little closer")
        )
    }

    private fun bestPlayer(world: WorldState): TrackedPlayer? =
        world.players.filter { it.confidence >= MIN_CONF && !it.ambiguous }.maxByOrNull { it.confidence }

    private fun bestObject(world: WorldState): TrackedObject? =
        world.objects.filter { it.confidence >= MIN_CONF && !it.ambiguous }.maxByOrNull { it.box.area }

    private companion object {
        const val MIN_CONF = 0.45f
    }
}
