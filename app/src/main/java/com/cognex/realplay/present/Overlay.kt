package com.cognex.realplay.present

import com.cognex.realplay.world.ColorTag
import com.cognex.realplay.world.NormPoint
import com.cognex.realplay.world.NormRect

/**
 * Visual emphasis for an overlay element (Architecture §3.6, §27). The engine flags WHICH element
 * matters (the active challenge's target), and each [PresentationTarget] renders that emphasis in
 * its own way — a pulse on mobile, a world-anchored glow on VR. Pure JVM.
 */
enum class Emphasis { NORMAL, TARGET, SECONDARY }

/**
 * One device-independent element of the scene overlay (Architecture §3.6, §27). Pure JVM — no
 * Android, no Compose — so both [MobileTarget] (ships now) and a future `VrTarget` render the
 * identical description (§20 invariant 22). Coordinates are NORMALIZED analysis-image space, exactly
 * like `world/` and `verify/`.
 *
 * [Highlight], [ZoneShape] and [Skeleton] are perception-derived (what the camera sees).
 * [PathArrow], [Ghost] and [Pictograph] are the visual-first COACHING cue track — declared here so
 * the render model is stable, populated by `coach/CuePlanner` in v3.6-B. A target renders only what
 * the engine put here; it never reads perception or a verdict (invariant 21).
 */
sealed interface Overlay {

    /** A tracked object's box, optionally emphasised as the active target. */
    data class Highlight(
        val box: NormRect,
        val label: String?,
        val color: ColorTag?,
        val emphasis: Emphasis = Emphasis.NORMAL,
        val trackId: Int? = null
    ) : Overlay

    /** A detected/derived zone polygon. */
    data class ZoneShape(
        val polygon: List<NormPoint>,
        val color: ColorTag,
        val zoneId: String? = null
    ) : Overlay

    /** A player skeleton: joints + a connection list (populated in S8). */
    data class Skeleton(
        val joints: List<NormPoint>,
        val connections: List<Pair<Int, Int>>,
        val color: ColorTag? = null
    ) : Overlay

    /**
     * A tracked player's presence marker (S8): a coloured halo around the torso [box] with a large
     * "P{[playerId]}" label above it. [color] is the player's colour band (null → a default hue by
     * id); [ambiguous] is true while identity is uncertain (rendered dimmer, §5 / §20 invariant 2).
     */
    data class PlayerHalo(
        val box: NormRect,
        val playerId: Int,
        val color: ColorTag? = null,
        val ambiguous: Boolean = false
    ) : Overlay

    /** COACHING cue — an animated arrow from → to showing the motion (v3.6-B). */
    data class PathArrow(val from: NormPoint, val to: NormPoint, val color: ColorTag) : Overlay

    /** COACHING cue — a looping ghost/mimic demonstration inside [box] (v3.6-B). */
    data class Ghost(val box: NormRect, val kind: String) : Overlay

    /** COACHING cue — a big pictograph of the goal inside [box] (v3.6-B). */
    data class Pictograph(val box: NormRect, val glyph: String) : Overlay

    /**
     * COACHING cue — the live G7 triangle (§7, §7.1): [corners] are the three objects' centres,
     * [edgesOk] the per-edge (i→i+1) constraint status, [satisfied] the whole-triangle preview, and
     * [area]/[minAngleDeg] the live geometry printed beside it. Presentation only (invariant 23).
     */
    data class TriangleGuide(
        val corners: List<NormPoint>,
        val edgesOk: List<Boolean>,
        val satisfied: Boolean,
        val area: Float,
        val minAngleDeg: Float
    ) : Overlay
}
