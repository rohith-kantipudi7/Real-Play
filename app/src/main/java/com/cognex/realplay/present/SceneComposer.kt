package com.cognex.realplay.present

import com.cognex.realplay.engine.GameUiState

/**
 * Builds the device-independent [RenderModel] from the engine's presentation-agnostic
 * [GameUiState] plus the frame's [ScenePerception] (Architecture §3.6, §27). Pure JVM — this is the
 * seam that keeps the engine render-agnostic: it emits state, the composer describes the scene, and
 * a [PresentationTarget] draws it. Fully unit-testable without Android.
 *
 * The composer NEVER reads perception raw, runs a verifier, or changes a verdict (§20 invariant 21);
 * it only arranges what the engine already decided into a drawable description.
 */
object SceneComposer {

    /**
     * Composes one frame. Overlays are ordered back-to-front: zones underneath, then skeletons, then
     * object highlights, then the coaching [cues] on top. HUD fields map straight from [ui].
     */
    fun compose(
        ui: GameUiState,
        perception: ScenePerception,
        cues: List<Overlay> = emptyList()
    ): RenderModel {
        val overlays = buildList {
            addAll(perception.zones)
            addAll(perception.skeletons)
            addAll(perception.highlights)
            addAll(cues)
        }
        return RenderModel(
            scene = SceneGraph(PassthroughLayer.CAMERA, overlays),
            hud = Hud(
                instruction = ui.instruction,
                status = ui.status,
                stepIndex = ui.stepIndex,
                stepCount = ui.stepCount,
                completedSteps = ui.completedSteps,
                stepProgress = ui.stepProgress,
                score = ui.score,
                streak = ui.streak,
                challengeIndex = ui.challengeIndex,
                lastGain = ui.lastGain,
                perfect = ui.perfect,
                coachingHint = ui.coachingHint,
                evidence = ui.evidence,
                timeRemainingMs = ui.timeRemainingMs,
                timeFraction = ui.timeFraction,
                retriesLeft = ui.retriesLeft
            ),
            cues = cues
        )
    }

    /** The idle model shown before the first frame — an empty scene over the camera. */
    val EMPTY: RenderModel = compose(GameUiState.INITIAL, ScenePerception.EMPTY)
}
