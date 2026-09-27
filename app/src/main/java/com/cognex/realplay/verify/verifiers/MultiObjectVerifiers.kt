package com.cognex.realplay.verify.verifiers

import com.cognex.realplay.challenge.ChallengeSpec
import com.cognex.realplay.challenge.VerificationStep
import com.cognex.realplay.verify.Resolution
import com.cognex.realplay.verify.RuleId
import com.cognex.realplay.verify.StepEvaluation
import com.cognex.realplay.verify.VerificationBaseline
import com.cognex.realplay.verify.VerifyGeometry
import com.cognex.realplay.verify.Verifier
import com.cognex.realplay.verify.actorAt
import com.cognex.realplay.verify.distanceTo
import com.cognex.realplay.verify.evidence
import com.cognex.realplay.verify.p
import com.cognex.realplay.world.NormPoint
import com.cognex.realplay.world.TrackedObject
import com.cognex.realplay.world.WorldState

/**
 * Shared actor resolution for the multi-object rules (Architecture §S4). Resolves EVERY actor of
 * [spec] in order; the first missing/low-confidence/ambiguous actor short-circuits to its Unsure
 * evaluation (§20 invariant 2), so the caller only ever sees a fully-resolved list. Fewer than
 * [min] resolvable objects → an Unsure carrying [tooFew].
 */
private fun resolveAll(
    spec: ChallengeSpec,
    world: WorldState,
    min: Int,
    tooFew: String
): Pair<List<TrackedObject>, StepEvaluation?> {
    val objs = ArrayList<TrackedObject>(spec.actors.size)
    for (i in spec.actors.indices) {
        when (val r = Resolution.requireObject(spec.actorAt(i), world, "piece ${i + 1}")) {
            is Resolution.ObjectResolution.Found -> objs.add(r.obj)
            is Resolution.ObjectResolution.Missing -> return objs to r.eval
        }
    }
    if (objs.size < min) return objs to Resolution.unsure(tooFew)
    return objs to null
}

/**
 * COLLINEAR (Architecture §4.1). actors = the objects that must form one straight row. Passes when
 * every centre lies within `tolerance` (default 0.06) of the best-fit line AND the objects span at
 * least `minSpread` (default 0.25) ALONG that line — so a tight blob (small spread) FAILS even
 * though it is trivially "collinear". Rotation-invariant: a straight row at any angle passes. Fewer
 * than three objects → Unsure (a line needs at least three to be a meaningful arrangement).
 */
internal class CollinearVerifier : Verifier {
    override val rule = RuleId.COLLINEAR
    override fun evaluate(
        step: VerificationStep, spec: ChallengeSpec, world: WorldState, baseline: VerificationBaseline
    ): StepEvaluation {
        Resolution.frameGuard(world)?.let { return it }
        val (objs, short) = resolveAll(spec, world, min = 3, tooFew = "Show me all the pieces")
        short?.let { return it }

        val fit = VerifyGeometry.lineFit(objs.map { it.center })
        val tolerance = step.p("tolerance", 0.06f)
        val minSpread = step.p("minSpread", 0.25f)
        val straight = fit.maxPerpendicular <= tolerance
        val spreadOk = fit.spread >= minSpread
        val satisfied = straight && spreadOk
        val conf = objs.minOf { it.confidence }
        val ev = listOf(
            evidence("maxOffset", fit.maxPerpendicular, tolerance, "<=", straight),
            evidence("rowLength", fit.spread, minSpread, ">=", spreadOk)
        )
        return if (satisfied) Resolution.pass(conf, ev)
        else if (!spreadOk) Resolution.fail("Spread them out into a longer row", ev)
        else Resolution.fail("Straighten the row — line them up", ev)
    }
}

/**
 * SIZE_ORDER (Architecture §4.1). actors = the objects to be ordered by size, left→right. `direction`
 * param: +1 (default) = smallest→biggest, −1 = biggest→smallest. Sorts the resolved objects by their
 * centre x, then checks each adjacent box area strictly follows the asked direction by at least
 * `margin` (default 0.002 of frame area) so near-equal sizes don't accidentally pass. Fewer than
 * three objects → Unsure.
 */
internal class SizeOrderVerifier : Verifier {
    override val rule = RuleId.SIZE_ORDER
    override fun evaluate(
        step: VerificationStep, spec: ChallengeSpec, world: WorldState, baseline: VerificationBaseline
    ): StepEvaluation {
        Resolution.frameGuard(world)?.let { return it }
        val (objs, short) = resolveAll(spec, world, min = 3, tooFew = "Show me all the pieces")
        short?.let { return it }

        val ascending = step.p("direction", 1f) >= 0f
        val margin = step.p("margin", 0.002f)
        val byX = objs.sortedBy { it.center.x }
        var correct = 0
        val pairs = byX.size - 1
        for (i in 0 until pairs) {
            val a = byX[i].box.area
            val b = byX[i + 1].box.area
            val ok = if (ascending) b - a >= margin else a - b >= margin
            if (ok) correct++
        }
        val satisfied = correct == pairs
        val conf = objs.minOf { it.confidence }
        val ev = listOf(evidence("orderedPairs", correct.toFloat(), pairs.toFloat(), "==", satisfied))
        val hint = if (ascending) "Order them small to big, left to right" else "Order them big to small, left to right"
        return if (satisfied) Resolution.pass(conf, ev) else Resolution.fail(hint, ev)
    }
}

/**
 * GROUP_CLUSTERED (Architecture §4.1). actors = the members of ONE group (same colour or same kind)
 * that must be brought together and kept apart from everything else. Passes when the group's widest
 * pairwise distance (its diameter) is within `clusterRadius` (default 0.28) AND every OTHER tracked
 * object stays at least `separation` (default 0.3) from the group centroid. When no other objects
 * are in view the separation term passes trivially. Fewer than two members → Unsure.
 */
internal class GroupClusteredVerifier : Verifier {
    override val rule = RuleId.GROUP_CLUSTERED
    override fun evaluate(
        step: VerificationStep, spec: ChallengeSpec, world: WorldState, baseline: VerificationBaseline
    ): StepEvaluation {
        Resolution.frameGuard(world)?.let { return it }
        val (group, short) = resolveAll(spec, world, min = 2, tooFew = "Show me the whole group")
        short?.let { return it }

        // Group diameter — the widest gap between any two members.
        var diameter = 0f
        for (i in group.indices) for (j in i + 1 until group.size) {
            val d = group[i].center.distanceTo(group[j].center)
            if (d > diameter) diameter = d
        }
        val clusterRadius = step.p("clusterRadius", 0.28f)
        val clustered = diameter <= clusterRadius

        // Separation — the nearest NON-member object to the group centroid.
        val ids = group.map { it.trackId }.toHashSet()
        val centroid = NormPoint(
            group.map { it.center.x }.average().toFloat(),
            group.map { it.center.y }.average().toFloat()
        )
        val separation = step.p("separation", 0.3f)
        val others = world.objects.filter { it.trackId !in ids && it.confidence >= Resolution.MIN_ACTOR_CONFIDENCE }
        val nearestOther = others.minOfOrNull { it.center.distanceTo(centroid) } ?: Float.MAX_VALUE
        val separated = nearestOther >= separation

        val satisfied = clustered && separated
        val conf = group.minOf { it.confidence }
        val ev = listOf(
            evidence("groupWidth", diameter, clusterRadius, "<=", clustered),
            evidence("separation", if (nearestOther == Float.MAX_VALUE) separation else nearestOther, separation, ">=", separated)
        )
        return if (satisfied) Resolution.pass(conf, ev)
        else if (!clustered) Resolution.fail("Bring the group closer together", ev)
        else Resolution.fail("Move the group away from the others", ev)
    }
}
