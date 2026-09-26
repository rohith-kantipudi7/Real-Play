package com.cognex.realplay.coach

import com.cognex.realplay.world.ColorTag
import com.cognex.realplay.world.NormPoint

/**
 * Visual-first coaching — "show, don't tell" (Architecture §26). Pure JVM, zero Android (§20
 * invariant 22). A [VisualCue] is a typed, ordered instruction for the presentation target to
 * DEMONSTRATE the goal (pulse the target, animate the route, loop a ghost, show a pictograph)
 * instead of describing it in words.
 *
 * Cues are derived DETERMINISTICALLY by [CuePlanner] from the same `ChallengeSpec` the verifier
 * judges — they mark the ACTUAL target actor, end at the ACTUAL goal, and never change a spec,
 * threshold, step count or the PASS/FAIL/UNSURE verdict (§20 invariant 23). They are the
 * presentation layer only; a target renders them and nothing else reads them.
 */
sealed interface VisualCue {

    /** "This one" — pulse/glow the target actor's live box (resolved by track id). */
    data class Highlight(val trackId: Int, val style: Pulse) : VisualCue

    /** An animated route for a move/drop, in NORMALIZED analysis-image space. */
    data class PathArrow(val from: NormPoint, val to: NormPoint) : VisualCue

    /** A looping mimic of the target pose/action (populated for pose games in S8). */
    data class GhostDemo(val kind: DemoKind, val loop: Boolean = true) : VisualCue

    /** The goal as a language-free icon (colour swatch, shape, bullseye…). */
    data class Pictograph(val symbol: Symbol) : VisualCue

    /** The target zone breathes. */
    data class ZonePulse(val zoneId: String) : VisualCue

    /**
     * The live triangle for G7 (Architecture §7, §7.1) — the demo centrepiece. [corners] are the
     * three objects' current centres; [edgesOk][i] is true when the edge from corner i→(i+1) meets
     * its constraint (drawn green, else amber). [satisfied] is the whole-triangle verdict preview and
     * [area]/[minAngleDeg] are printed live. Presentation only — the verifier still decides the pass
     * (§20 invariant 23).
     */
    data class TriangleGuide(
        val corners: List<NormPoint>,
        val edgesOk: List<Boolean>,
        val satisfied: Boolean,
        val area: Float,
        val minAngleDeg: Float
    ) : VisualCue
}

/** How strongly a [VisualCue.Highlight] pulses. The primary actor is [STRONG]; context is [GENTLE]. */
enum class Pulse { GENTLE, STRONG }

/** The action a [VisualCue.GhostDemo] loops (pose/motion games, S8). */
enum class DemoKind { HOLD_STILL, RAISE_LIMB, MATCH_POSE, MOVE }

/**
 * A language-free goal icon for a [VisualCue.Pictograph] (Architecture §26). Pure JVM. The
 * presentation target maps each symbol to a drawable glyph; the planner only names the meaning.
 */
sealed interface Symbol {
    /** Find/produce this colour. */
    data class ColorSwatch(val color: ColorTag) : Symbol

    /** Match this shape. */
    data class Shape(val kind: ShapeKind) : Symbol

    /** Present the actor to the camera / bring it here. */
    data object Target : Symbol
}

/** Shape families for a [Symbol.Shape]. */
enum class ShapeKind { CIRCLE, SQUARE, TRIANGLE }
