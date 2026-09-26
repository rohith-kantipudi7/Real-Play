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
import com.cognex.realplay.world.TrackedPlayer
import com.cognex.realplay.world.WorldState
import kotlin.math.min

/**
 * G5 · Red Light / Green Light (Architecture §7, S8 prompt step 6). Pure JVM.
 *
 * The active player moves on green and must FREEZE on red: a single `MOTION_BELOW` step held for
 * the red window. Kids-safe (§26): no elimination — a wiggle only costs points, coached with
 * "Ooh, you wiggled!" rather than "out".
 *
 * Feasibility is the deliberate v3.4 value **0.70 + 0.15·min(players,2)/2** (§7): 0.775 at
 * single-player, 0.85 at two players. This keeps a single-player human-only scene from collapsing to
 * Statue Match every time under argmax while still scaling up with a second player.
 *
 * [maxStepsForTier] stays 1 at every tier — the difficulty lives in the red-window length and the
 * motion threshold, not in step count.
 */
class G5RedLightGreenLightGenerator : ChallengeGenerator {

    override val type = ChallengeType.RED_LIGHT_GREEN_LIGHT
    override val id = "G5"
    override val proven = true
    override val requires = Requirement(minPlayers = 1)

    /** 0.70 + 0.15·min(players,2)/2 (§7) — genuinely competes with G4's flat 0.85. */
    override fun feasibility(cap: SceneCapability, ctx: GenerationContext): Float =
        0.70f + 0.15f * (min(cap.playerCount, 2) / 2f)

    /** Never chains — the red window length carries the difficulty (§8.1b). */
    override fun maxStepsForTier(tier: Tier): Int = 1

    /** Offered from EARLY upward when pose is on (§10); never a toddler game. */
    override fun ageGate(band: AgeBand): Float = if (band == AgeBand.TODDLER) 0f else 1f

    override fun generate(
        world: WorldState,
        aff: List<Affordance>,
        ctx: GenerationContext,
        stepBudget: Int
    ): ChallengeSpec {
        val player = activePlayer(world)
        // The red window (freeze duration) is the tier hold; harder tiers demand a longer freeze.
        val redWindowMs = ctx.knobs.holdMs
        val threshold = motionThreshold(ctx.effectiveTier)

        val steps = listOf(
            VerificationStep(
                rule = RuleId.MOTION_BELOW,
                params = mapOf("threshold" to threshold),
                holdMs = redWindowMs
            )
        )

        return ChallengeSpec(
            id = "G5-red",
            type = type,
            tier = ctx.effectiveTier,
            ageBand = ctx.ageBand,
            actors = listOf(ActorRef.ByPlayer(player?.playerId ?: 0)),
            instruction = "Green light — dance! RED LIGHT — freeze and hold perfectly still.",
            steps = steps,
            timeLimitMs = ctx.knobs.timeLimitMs,
            baseScore = 35,
            hints = listOf("Freeze the instant the light turns red", "Ooh, you wiggled! Try to be a statue")
        )
    }

    /** Highest-confidence, unambiguous player, or null when none is yet confirmed. */
    private fun activePlayer(world: WorldState): TrackedPlayer? =
        world.players.filter { !it.ambiguous }.maxByOrNull { it.confidence }

    /** A stricter freeze is demanded at higher tiers. */
    private fun motionThreshold(tier: Tier): Float = when (tier) {
        Tier.EASY -> 0.08f
        Tier.MEDIUM -> 0.05f
        Tier.HARD -> 0.03f
    }
}
