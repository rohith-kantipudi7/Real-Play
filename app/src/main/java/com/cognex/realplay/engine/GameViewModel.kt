package com.cognex.realplay.engine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cognex.realplay.ai.AiRuntime
import com.cognex.realplay.ai.ChallengeComposer
import com.cognex.realplay.ai.LanguageModel
import com.cognex.realplay.challenge.AgeBand
import com.cognex.realplay.challenge.ChallengeRegistry
import com.cognex.realplay.challenge.ChallengeSpec
import com.cognex.realplay.challenge.Difficulty
import com.cognex.realplay.challenge.DifficultyKnobs
import com.cognex.realplay.challenge.GenerationContext
import com.cognex.realplay.challenge.SelectionMode
import com.cognex.realplay.challenge.SelectionResult
import com.cognex.realplay.coach.CuePlanner
import com.cognex.realplay.coach.CueResolver
import com.cognex.realplay.present.Overlay
import com.cognex.realplay.present.RenderModel
import com.cognex.realplay.present.ScenePerception
import com.cognex.realplay.present.SceneComposer
import com.cognex.realplay.util.RpLog
import com.cognex.realplay.verify.Evidence
import com.cognex.realplay.verify.VerificationOutcome
import com.cognex.realplay.verify.VerifierRegistry
import com.cognex.realplay.world.SceneCapability
import com.cognex.realplay.world.WorldState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Orchestrates one game session (Architecture §13 S5). Plain [ViewModel] — no Hilt. Exposes exactly
 * ONE [StateFlow] of [GameUiState]; the UI is a pure function of it (§3.3).
 *
 * Threading contract (§3.3, §20): frames arrive from the analyzer/detector thread via
 * [submitFrame]; all verification runs on [Dispatchers.Default] inside a single collector, never on
 * the analyzer thread. The flow drops stale frames (conflated) so a slow verify can't back up the
 * camera.
 *
 * Loop: select (scored registry) → run mission (per-step gates) → PASS advances the score and the
 * next challenge; a timed challenge that runs out RETRIES (max 2) then counts as a fail; an Unsure
 * frame only COACHES — it consumes no retry and never changes difficulty (§8, §20 invariant 2).
 */
class GameViewModel(
    private val ageBand: AgeBand = SessionConfig.ageBand,
    private val selectionMode: SelectionMode = SelectionMode.RECOMMENDED,
    private val verifierRegistry: VerifierRegistry = VerifierRegistry.default(),
    challengeRegistry: ChallengeRegistry? = null,
    languageModel: LanguageModel? = null
) : ViewModel() {

    private val registry: ChallengeRegistry = challengeRegistry
        ?: ChallengeRegistry.default()

    /**
     * The optional ON-DEVICE Gemma composer (§6.5). Off by default; only proposes when
     * [AppSettings.aiComposerEnabled] is on AND a side-loaded Tier-B model is present. Its result is
     * always speculative and validator-gated — the deterministic registry above is the
     * truth-line-safe, always-offline fallback.
     */
    private val composer = ChallengeComposer(registry, languageModel ?: AiRuntime.model())

    private val session = GameSession()
    private val sm = GameStateMachine()

    private var mission: MissionRunner? = null
    private var selection: SelectionResult? = null
    private var startTs = 0L
    private var retriesLeft = MAX_RETRIES
    private var hintsUsed = 0
    private var seedCounter = 0L
    private var lastMeasured: Float? = null
    private var lastRequired: Float? = null
    private var lastEvidence: List<Evidence> = emptyList()
    private var challengeIndex = 0
    private var bestStreak = 0
    private var sessionOver = false

    /** True once the player has made real progress on the current mission (a gate has samples). */
    private var started = false

    /** Speculative composer plumbing (§6.5). Applied on the frame thread only, so no data races. */
    private data class Composed(val index: Int, val key: String, val result: SelectionResult)
    @Volatile private var pendingComposed: Composed? = null
    private var composedForKey: String? = null
    private var composerApplied = false

    private val _ui = MutableStateFlow(GameUiState.INITIAL)
    val ui: StateFlow<GameUiState> = _ui.asStateFlow()

    /**
     * The frame's perception-derived overlays (Architecture §3.6). Updated on every [submitFrame] so
     * the scene overlay tracks the camera at frame rate, independently of the (off-thread) verdict
     * updates to [_ui].
     */
    private val _scene = MutableStateFlow(ScenePerception.EMPTY)

    /**
     * The active step's visual-first coaching cue track (Architecture §26). Planned deterministically
     * by [CuePlanner] from the SAME spec the verifier judges and resolved to drawable [Overlay]s by
     * [CueResolver]; empty whenever no mission is being played. Presentation only — it never changes a
     * verdict (§20 invariant 23).
     */
    private val _cues = MutableStateFlow<List<Overlay>>(emptyList())

    /**
     * The device-independent [RenderModel] the presentation layer renders (Architecture §3.6, §27).
     * Derived purely from [_ui] (engine state) + [_scene] (perception overlays) + [_cues] (coaching)
     * via [SceneComposer]; the engine itself never draws (§20 invariant 21). `MobileTarget` adapts
     * this for the phone; a future `VrTarget` would consume the identical model.
     */
    val renderModel: StateFlow<RenderModel> =
        combine(_ui, _scene, _cues) { ui, scene, cues -> SceneComposer.compose(ui, scene, cues) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, SceneComposer.EMPTY)

    private data class FrameInput(val world: WorldState, val cap: SceneCapability)

    private val frames = MutableSharedFlow<FrameInput>(
        replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        SessionResults.reset()
        viewModelScope.launch(Dispatchers.Default) {
            frames.collect { process(it.world, it.cap) }
        }
    }

    /** Called per world update from the UI collector. Non-blocking; verification is off-thread. */
    fun submitFrame(world: WorldState, cap: SceneCapability) {
        _scene.value = ScenePerception.fromWorld(world)
        frames.tryEmit(FrameInput(world, cap))
    }

    /** Manual retry (the retry button). Resets the current mission if any retries remain. */
    fun retry() {
        val runner = mission ?: return
        if (retriesLeft <= 0) return
        retriesLeft--
        runner.reset()
        startTs = 0L
        sm.reset(GameState.PLAYING)
    }

    /** Ends the session — the screen navigates to the result. */
    fun finish() {
        sessionOver = true
        _cues.value = emptyList()
        _ui.value = _ui.value.copy(sessionOver = true)
    }

    // ── core loop (off the analyzer thread) ──────────────────────────────────

    private fun process(world: WorldState, cap: SceneCapability) {
        if (sessionOver) return

        if (mission == null) {
            startNewChallenge(world, cap)
            return
        }
        if (sm.state != GameState.PLAYING) return

        // Before the player has done anything, keep the pick current as the scene populates — this
        // upgrades the first-frame G0 fallback to a real game once objects/players are detected, and
        // adapts if the scene changes (§13 S5 "removing an object mid-scan adapts"). Once real
        // progress exists we lock the choice so the gate can accumulate.
        if (!started) {
            consumePendingComposed()
            if (!composerApplied) maybeUpgrade(world, cap)
        }

        val runner = mission ?: return
        val spec = selection?.spec ?: return
        if (startTs == 0L) startTs = world.timestampMs
        val elapsed = world.timestampMs - startTs

        val tick = runner.onFrame(world, world.timestampMs)
        captureEvidence(tick.outcome)
        if (tick.stepFired || tick.stepProgress > 0f) started = true

        // Plan the visual-first coaching cues for the active step from the SAME spec the verifier
        // judges (§26), resolved to drawable overlays against this frame's world. Presentation only.
        _cues.value = CueResolver.resolve(CuePlanner.plan(spec, world, tick.stepIndex), world)

        if (tick.missionComplete) {
            onPass(spec, tick, elapsed)
            return
        }
        if (spec.timeLimitMs != null && elapsed > spec.timeLimitMs) {
            onTimeout(world, spec)
            return
        }
        publishPlaying(spec, tick, elapsed)
    }

    /** Resolves the difficulty context WITHOUT mutating session state (safe to call every frame). */
    private fun buildContext(cap: SceneCapability): GenerationContext {
        val effectiveTier = Difficulty.effectiveTier(session.rating, cap.richness, ageBand, session.previousTier)
        val knobs = DifficultyKnobs.forTier(effectiveTier, cap.spread, cap.stability)
        return GenerationContext(
            ageBand = ageBand,
            effectiveTier = effectiveTier,
            knobs = knobs,
            trackOnlyMode = cap.trackOnlyMode,
            recentTypes = session.recentTypes,
            seed = seedCounter++
        )
    }

    /** Swaps the challenge only when a different generator now wins (preserves gate progress). */
    private fun maybeUpgrade(world: WorldState, cap: SceneCapability) {
        val ctx = buildContext(cap)
        val result = registry.select(world, cap, ctx, selectionMode)
        if (result.winnerId != selection?.winnerId) {
            selection = result
            mission = MissionRunner(result.spec, verifierRegistry)
            startTs = 0L
            RpLog.i(RpLog.Tag.ENGINE, "Upgraded to ${result.winnerId} (${result.spec.type}) budget=${result.stepBudget}")
        }
        requestComposeIfNeeded(world, cap, ctx, result)
    }

    /**
     * Fires ONE speculative composer request per (challenge, winner) on its own coroutine (§3.3).
     * Never blocks the frame loop; the result is picked up on a later frame by [consumePendingComposed].
     */
    private fun requestComposeIfNeeded(
        world: WorldState,
        cap: SceneCapability,
        ctx: GenerationContext,
        deterministic: SelectionResult
    ) {
        if (!composer.active()) return
        val key = "$challengeIndex:${deterministic.winnerId}"
        if (key == composedForKey) return
        composedForKey = key
        val idx = challengeIndex
        viewModelScope.launch(Dispatchers.Default) {
            val composed = composer.compose(world, cap, ctx, deterministic)
            if (composed !== deterministic) pendingComposed = Composed(idx, key, composed)
        }
    }

    /** Applies the latest validated composer result, on the frame thread, before the player starts. */
    private fun consumePendingComposed() {
        val p = pendingComposed ?: return
        pendingComposed = null
        if (sessionOver || started) return
        if (p.index != challengeIndex || p.key != composedForKey) return
        selection = p.result
        mission = MissionRunner(p.result.spec, verifierRegistry)
        startTs = 0L
        composerApplied = true
        RpLog.i(RpLog.Tag.AI, "composer applied ${p.result.winnerId} (${p.result.spec.type})")
        _ui.value = _ui.value.copy(
            instruction = p.result.spec.instruction,
            winnerId = p.result.winnerId,
            winnerType = p.result.spec.type.name
        )
    }

    private fun startNewChallenge(world: WorldState, cap: SceneCapability) {
        // Update the previous-tier memory once per challenge (for skill-tier hysteresis, §8).
        session.previousTier = Difficulty.skillTier(session.rating, session.previousTier)
        val ctx = buildContext(cap)

        challengeIndex++
        sm.reset(GameState.IDLE)
        sm.transition(GameState.SELECTING)
        val result = registry.select(world, cap, ctx, selectionMode)
        selection = result
        session.pushType(result.spec.type)
        mission = MissionRunner(result.spec, verifierRegistry)
        startTs = 0L
        retriesLeft = MAX_RETRIES
        hintsUsed = 0
        started = false
        lastMeasured = null
        lastRequired = null
        lastEvidence = emptyList()
        pendingComposed = null
        composedForKey = null
        composerApplied = false

        _cues.value = emptyList()

        sm.transition(GameState.INSTRUCTION)
        sm.transition(GameState.PLAYING)
        RpLog.i(RpLog.Tag.ENGINE, "Selected ${result.winnerId} (${result.spec.type}) tier=${ctx.effectiveTier} budget=${result.stepBudget}")
        logRankedCandidates(result)
        publishPlaying(result.spec, firstTick(result.spec), 0L)
        requestComposeIfNeeded(world, cap, ctx, result)
    }

    /**
     * Logs the full ranked candidate list for this selection (Architecture §13 S7 gate — "ranked
     * list logged"). Every generator appears with its score to 2dp; a zero always carries its
     * reason, which is exactly what makes an empty-scene demo convincing.
     */
    private fun logRankedCandidates(result: SelectionResult) {
        val line = result.ranked
            .sortedWith(compareByDescending<com.cognex.realplay.challenge.RankedCandidate> { it.score }.thenBy { it.generatorId })
            .joinToString(" · ") { c ->
                val marker = if (c.generatorId == result.winnerId) "*" else ""
                val tail = c.zeroReason?.let { " (${it})" } ?: ""
                "$marker${c.generatorId}=${"%.2f".format(c.score)}$tail"
            }
        RpLog.i(RpLog.Tag.ENGINE, "Ranked: $line")
    }

    private fun onPass(spec: ChallengeSpec, tick: MissionRunner.Tick, elapsedMs: Long) {
        if (!sm.transition(GameState.PASSED)) return
        val fast = spec.timeLimitMs?.let { elapsedMs <= 0.3f * it } ?: false
        session.recordPass(fast)
        val completed = tick.completedSteps.coerceAtLeast(spec.steps.size)
        val breakdown = ScoreEngine.compute(
            baseScore = spec.baseScore,
            passed = true,
            completedSteps = completed,
            stepCount = spec.steps.size,
            elapsedMs = elapsedMs,
            timeLimitMs = spec.timeLimitMs,
            streak = session.streak,
            hintsUsed = hintsUsed,
            precisionMeasured = lastMeasured,
            precisionRequired = lastRequired
        )
        session.addScore(breakdown.total)
        sm.transition(GameState.RESULT)

        val perfect = fast || session.streak >= 3
        bestStreak = maxOf(bestStreak, session.streak)
        SessionResults.record(
            ChallengeResult(
                index = challengeIndex,
                type = spec.type.name,
                winnerId = selection?.winnerId ?: "",
                passed = true,
                timedOut = false,
                score = breakdown.total,
                stepCount = spec.steps.size,
                completedSteps = completed,
                evidence = lastEvidence
            ),
            totalScore = session.totalScore,
            bestStreak = bestStreak
        )

        _ui.value = _ui.value.copy(
            status = PlayStatus.PASSED,
            stepIndex = spec.steps.size - 1,
            stepCount = spec.steps.size,
            completedSteps = completed,
            stepProgress = 1f,
            score = session.totalScore,
            streak = session.streak,
            challengeIndex = challengeIndex,
            lastGain = breakdown.total,
            perfect = perfect,
            coachingHint = null,
            evidence = lastEvidence,
            timeRemainingMs = null,
            timeFraction = null,
            retriesLeft = retriesLeft
        )
        // Advance to the next challenge on the following frame.
        mission = null
        selection = null
        sm.reset(GameState.IDLE)
    }

    private fun onTimeout(world: WorldState, spec: ChallengeSpec) {
        if (retriesLeft > 0) {
            retriesLeft--
            mission?.reset()
            startTs = world.timestampMs
            _ui.value = _ui.value.copy(
                status = PlayStatus.PLAYING,
                coachingHint = "Time! Try again.",
                retriesLeft = retriesLeft,
                timeRemainingMs = spec.timeLimitMs,
                timeFraction = 1f
            )
            return
        }
        if (!sm.transition(GameState.TIMED_OUT)) return
        session.recordFail()
        sm.transition(GameState.RESULT)
        bestStreak = maxOf(bestStreak, session.streak)
        SessionResults.record(
            ChallengeResult(
                index = challengeIndex,
                type = spec.type.name,
                winnerId = selection?.winnerId ?: "",
                passed = false,
                timedOut = true,
                score = 0,
                stepCount = spec.steps.size,
                completedSteps = mission?.completedSteps() ?: 0,
                evidence = lastEvidence
            ),
            totalScore = session.totalScore,
            bestStreak = bestStreak
        )
        _ui.value = _ui.value.copy(
            status = PlayStatus.TIMED_OUT,
            perfect = false,
            coachingHint = "Out of time — new challenge coming up.",
            score = session.totalScore,
            streak = session.streak,
            timeRemainingMs = null,
            timeFraction = null,
            retriesLeft = retriesLeft
        )
        mission = null
        selection = null
        sm.reset(GameState.IDLE)
    }

    private fun publishPlaying(spec: ChallengeSpec, tick: MissionRunner.Tick, elapsedMs: Long) {
        val coaching = (tick.outcome as? VerificationOutcome.Unsure)?.coachingHint ?: tick.coachingHint
        val status = if (tick.outcome is VerificationOutcome.Unsure) PlayStatus.COACHING else PlayStatus.PLAYING
        val timeRemaining = spec.timeLimitMs?.let { (it - elapsedMs).coerceAtLeast(0L) }
        val timeFraction = spec.timeLimitMs?.let { (timeRemaining!!.toFloat() / it).coerceIn(0f, 1f) }
        val evNow = when (val o = tick.outcome) {
            is VerificationOutcome.Pass -> o.evidence
            is VerificationOutcome.Fail -> o.evidence
            is VerificationOutcome.Unsure -> lastEvidence
        }
        _ui.value = _ui.value.copy(
            status = status,
            instruction = spec.instruction,
            stepIndex = tick.stepIndex,
            stepCount = spec.steps.size,
            completedSteps = tick.completedSteps,
            stepProgress = tick.stepProgress,
            score = session.totalScore,
            streak = session.streak,
            challengeIndex = challengeIndex,
            perfect = false,
            coachingHint = coaching,
            evidence = evNow,
            timeRemainingMs = timeRemaining,
            timeFraction = timeFraction,
            retriesLeft = retriesLeft,
            winnerId = selection?.winnerId ?: "",
            winnerType = spec.type.name,
            ranked = selection?.ranked ?: emptyList()
        )
    }

    private fun firstTick(spec: ChallengeSpec): MissionRunner.Tick = MissionRunner.Tick(
        stepIndex = 0,
        stepProgress = 0f,
        outcome = VerificationOutcome.Unsure(spec.hints.firstOrNull() ?: "Let's go!"),
        stepFired = false,
        missionComplete = false,
        completedSteps = 0,
        coachingHint = spec.hints.firstOrNull()
    )

    private fun captureEvidence(outcome: VerificationOutcome) {
        val list = when (outcome) {
            is VerificationOutcome.Pass -> outcome.evidence
            is VerificationOutcome.Fail -> outcome.evidence
            is VerificationOutcome.Unsure -> emptyList()
        }
        if (list.isNotEmpty()) {
            lastEvidence = list
            val ev = list.first()
            lastMeasured = ev.measured
            lastRequired = ev.required
        }
    }

    private companion object {
        const val MAX_RETRIES = 2
    }
}
