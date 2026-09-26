package com.cognex.realplay.present

import com.cognex.realplay.engine.PlayStatus
import com.cognex.realplay.verify.Evidence

/**
 * The device-independent HUD description (Architecture §3.6, §27) — the non-spatial part of a frame:
 * instruction, play status, step tracker, score/streak, timer, coaching and the measured evidence.
 * Pure JVM. Mirrors the presentation-relevant fields of `GameUiState`; a target renders only these,
 * never the engine's internals (§20 invariant 21).
 */
data class Hud(
    val instruction: String,
    val status: PlayStatus,
    val stepIndex: Int,
    val stepCount: Int,
    val completedSteps: Int,
    val stepProgress: Float,
    val score: Int,
    val streak: Int,
    val challengeIndex: Int,
    val lastGain: Int,
    val perfect: Boolean,
    val coachingHint: String?,
    val evidence: List<Evidence>,
    val timeRemainingMs: Long?,
    val timeFraction: Float?,
    val retriesLeft: Int
)

/**
 * The complete, device-independent description of what to show for one frame (Architecture §3.6,
 * §27). Pure JVM (§20 invariant 22). The engine emits a `StateFlow<RenderModel>`; a
 * [PresentationTarget] renders it. Both [MobileTarget] (ships now) and any future `VrTarget` consume
 * the identical model, which is exactly the scalability story — a game verified on mobile behaves
 * identically on VR because there is one scene truth.
 *
 * [cues] is the visual-first COACHING track (a subset of [Overlay] cue types) — empty until v3.6-B's
 * `CuePlanner` populates it; it changes only presentation, never a spec or verdict (invariant 23).
 */
data class RenderModel(
    val scene: SceneGraph,
    val hud: Hud,
    val cues: List<Overlay> = emptyList()
)
