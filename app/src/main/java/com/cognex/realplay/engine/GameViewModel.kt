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
import com.cognex.realplay.verify.PoseLibrary
import com.cognex.realplay.verify.RuleId
import com.cognex.realplay.verify.VerificationBaseline
import com.cognex.realplay.verify.VerificationOutcome
import com.cognex.realplay.verify.VerifierRegistry
import com.cognex.realplay.world.SceneCapability
import com.cognex.realplay.world.WorldState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    /** Null (the default) reads [SessionConfig.ageBand] live every frame, so a parent can switch
     *  bands mid-session without restarting the game (§13 S10). Non-null pins it — used by tests. */
    private val ageBandOverride: AgeBand? = null,
    /** Null (the default) reads the live PARTY session's policy, if any, else RECOMMENDED (§25).
     *  Non-null pins it — used by tests. */
    private val selectionModeOverride: SelectionMode? = null,
    private val verifierRegistry: VerifierRegistry = VerifierRegistry.default(),
    challengeRegistry: ChallengeRegistry? = null,
    languageModel: LanguageModel? = null
) : ViewModel() {

    private val ageBand: AgeBand get() = ageBandOverride ?: SessionConfig.ageBand

    /** RECOMMENDED for SOLO; a PARTY session supplies OPEN-style controlled variety (§25). */
    private val selectionMode: SelectionMode
        get() = selectionModeOverride ?: PartyRuntime.active?.selectionMode ?: SelectionMode.RECOMMENDED

    private val registry: ChallengeRegistry = challengeRegistry
        ?: ChallengeRegistry.default()

    /**
     * The optional ON-DEVICE Gemma composer (§6.5). Off by default; only proposes when
     * [AppSettings.aiComposerEnabled] is on AND a side-loaded Tier-B model is present. Its result is
     * always speculative and validator-gated — the deterministic registry above is the
     * truth-line-safe, always-offline fallback.
     */
    private val composer = ChallengeComposer(registry, languageModel ?: AiRuntime.model())

    /** Cloud/on-device model that authors the "without verifier" games from the object list. */
    private val freeformComposer = com.cognex.realplay.ai.FreeformComposer(languageModel ?: AiRuntime.model())

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

    /** Cursor into [DemoArc.order] for the scripted demo arc; advances each time a game begins. */
    private var arcCursor = 0

    // ── without-verifier (freeform) mode state (§ user request) ──
    private var freeformLevel = 0
    private var freeformStartMs = 0L
    private var currentFreeform: FreeformGames.Game? = null
    @Volatile private var freeformComposing = false
    @Volatile private var pendingFreeform: FreeformGames.Game? = null

    /** Session-elapsed break suggestion (§10 rule 8 — TODDLER only, once per session). */
    private val sessionStartMs = System.currentTimeMillis()
    private var breakDismissed = false

    /** The latest difficulty "why" string (§8, §13 S9), surfaced to the dev overlay via GameUiState. */
    private var difficultyExplanation = ""

    /**
     * Compose-first game flow (§6.5 v3.7). Between challenges the engine settles the scene, then
     * composes the ACTUAL game up-front on [composeJob] while showing a "Creating your game…" phase,
     * so the player sees the LLM's game rather than a deterministic placeholder that later swaps.
     * [pendingStart] is the resolved [SelectionResult] the frame loop begins on its next tick.
     */
    @Volatile private var composing = false
    @Volatile private var pendingStart: SelectionResult? = null
    private var composeJob: Job? = null
    /** When the between-challenge settle window began (frame clock), 0 when not waiting. */
    private var readySinceMs = 0L
    /** Wall-clock time to hold the finished level's celebration until, before composing the next
     *  game (§3a smooth transitions). 0 when not holding. */
    private var advanceAtMs = 0L

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

    /**
     * Skips the current game and moves straight to a fresh one — some scenes genuinely can't
     * complete a given challenge (missing object, impossible arrangement). It scores nothing, breaks
     * the streak and eases the difficulty a little (like a fail), then composes the next game after a
     * short beat. No fail buzzer — the transition reads as "new game coming up", not a loss.
     */
    fun skip() {
        if (sessionOver) return
        // Freeform mode: no verification — just jump straight to the next timed game.
        if (!AppSettings.verifierEnabled.value) {
            nextFreeform()
            return
        }
        val spec = selection?.spec ?: return
        session.recordFail()
        bestStreak = maxOf(bestStreak, session.streak)
        SessionResults.record(
            ChallengeResult(
                index = challengeIndex,
                type = spec.type.name,
                winnerId = selection?.winnerId ?: "",
                passed = false,
                timedOut = false,
                score = 0,
                stepCount = spec.steps.size,
                completedSteps = mission?.completedSteps() ?: 0,
                evidence = lastEvidence
            ),
            totalScore = session.totalScore,
            bestStreak = bestStreak
        )
        _cues.value = emptyList()
        sm.reset(GameState.IDLE)
        _ui.value = _ui.value.copy(
            status = PlayStatus.COMPOSING,
            instruction = "New game coming up\u2026",
            stepProgress = 0f,
            coachingHint = null,
            evidence = emptyList(),
            score = session.totalScore,
            streak = session.streak,
            timeRemainingMs = null,
            timeFraction = null,
            retriesLeft = retriesLeft
        )
        // A brief calm hold, then the frame loop clears the mission and composes the next game.
        advanceAtMs = System.currentTimeMillis() + SKIP_HOLD_MS
    }

    /** Freeform "Next level" — advance to a fresh game manually (no timer, no scoring). */
    fun nextFreeform() {
        if (sessionOver) return
        freeformStartMs = 0L
    }

    /** Dismisses the break suggestion for the rest of this session (§10 rule 8). */
    fun dismissBreak() {
        breakDismissed = true
        _ui.value = _ui.value.copy(breakSuggested = false)
    }

    private fun maybeSuggestBreak() {
        if (breakDismissed || ageBand != AgeBand.TODDLER || _ui.value.breakSuggested) return
        if (System.currentTimeMillis() - sessionStartMs >= BREAK_SUGGEST_MS) {
            _ui.value = _ui.value.copy(breakSuggested = true)
        }
    }

    // ── core loop (off the analyzer thread) ──────────────────────────────────

    private fun process(world: WorldState, cap: SceneCapability) {
        if (sessionOver) return
        maybeSuggestBreak()

        // Without-verifier freeform mode (§ user request): honour-system games on a 30 s timer,
        // read aloud and listed on screen — no camera verification. Solo only; PARTY keeps its flow.
        if (!AppSettings.verifierEnabled.value && PartyRuntime.active == null) {
            processFreeform(world)
            return
        }

        // Hold on the completed level's celebration for a calm beat before advancing (§3a).
        if (advanceAtMs != 0L) {
            if (System.currentTimeMillis() < advanceAtMs) return
            advanceAtMs = 0L
            mission = null
            selection = null
            sm.reset(GameState.IDLE)
        }

        // No active mission: settle the scene, compose the next game up-front, then begin it. While
        // the compose coroutine is in flight the UI shows the COMPOSING phase (§6.5 v3.7).
        if (mission == null) {
            if (composing) return
            val ready = pendingStart
            if (ready != null) {
                pendingStart = null
                beginPlaying(ready, world)
                return
            }
            prepareNextChallenge(world, cap)
            return
        }
        if (sm.state != GameState.PLAYING) return

        val runner = mission ?: return
        val spec = selection?.spec ?: return
        if (startTs == 0L) startTs = world.timestampMs
        val elapsed = world.timestampMs - startTs

        val tick = runner.onFrame(world, world.timestampMs)
        captureEvidence(tick.outcome)

        // Plan the visual-first coaching cues for the active step from the SAME spec the verifier
        // judges (§26), resolved to drawable overlays against this frame's world. Presentation only.
        _cues.value = CueResolver.resolve(CuePlanner.plan(spec, world, tick.stepIndex), world)

        if (tick.missionComplete) {
            onPass(spec, tick, elapsed)
            return
        }
        // Demo auto-complete (§ user request): if the verifier hasn't passed within AUTO_COMPLETE_MS,
        // count it as done so the game always flows to the next level. The green progress bar fills
        // over this window (see publishPlaying) so it reads as "completing", then celebrates.
        if (elapsed >= AUTO_COMPLETE_MS) {
            onPass(spec, tick, elapsed)
            return
        }
        if (spec.timeLimitMs != null && elapsed > spec.timeLimitMs) {
            onTimeout(world, spec)
            return
        }
        publishPlaying(spec, tick, elapsed)
    }

    /**
     * Per-step verification baseline for [spec]. POSE_MATCH steps carry a `poseId` naming one of
     * [PoseLibrary]'s eight targets, resolved here to a reference pose the verifier compares against
     * (§4.1, §7). Every other rule needs no baseline.
     */
    private fun poseBaselineFor(spec: ChallengeSpec): (Int) -> VerificationBaseline = { stepIndex ->
        val step = spec.steps.getOrNull(stepIndex)
        if (step?.rule == RuleId.POSE_MATCH) {
            PoseLibrary.baselineFor(step.params["poseId"]?.toInt() ?: 0)
        } else {
            VerificationBaseline.NONE
        }
    }

    /** Resolves the difficulty context WITHOUT mutating session state (safe to call every frame). */
    private fun buildContext(cap: SceneCapability): GenerationContext {
        val resolution = DifficultyDirector.resolve(
            rating = session.rating,
            richness = cap.richness,
            band = ageBand,
            spread = cap.spread,
            stability = cap.stability,
            previousTier = session.previousTier
        )
        difficultyExplanation = resolution.explanation
        return GenerationContext(
            ageBand = ageBand,
            effectiveTier = resolution.effectiveTier,
            // Toddler-only knob overrides (§10) — no-op for every other band.
            knobs = com.cognex.realplay.challenge.ToddlerPolicy.apply(resolution.knobs, ageBand),
            trackOnlyMode = cap.trackOnlyMode,
            // A PARTY session's cross-round history is prepended so novelty scoring avoids repeats
            // across turns too, not just within one player's own session (§25).
            recentTypes = (PartyRuntime.active?.recentTypes ?: emptyList()) + session.recentTypes,
            seed = seedCounter++
        )
    }

    /**
     * Without-verifier freeform loop: shows a creative game, reads it aloud (via the UI's TTS on
     * challengeIndex change) and lists it on screen, runs a 30 s timer, then awards a flat score and
     * advances to the next, harder game. No pass/fail — the camera doesn't judge these (§ user req).
     */
    private fun processFreeform(world: WorldState) {
        // A game stays on screen until the player taps "Next level" — no timer, no auto-advance,
        // no scoring (§ user request). freeformStartMs != 0 marks a game as currently active.
        if (freeformStartMs != 0L) return
        if (freeformComposing) return
        val ready = pendingFreeform
        if (ready != null) {
            pendingFreeform = null
            freeformLevel++
            challengeIndex++
            freeformStartMs = world.timestampMs
            currentFreeform = ready
            publishFreeform(ready)
            return
        }
        // Compose-first: the cloud/on-device model authors the next game from the live object list;
        // while it's in flight we show "Creating your game…", falling back to a template on failure.
        val labels = world.objects.mapNotNull { o -> o.label.takeIf { it.isNotBlank() } }.distinct()
        val fallback = FreeformGames.forLevel(freeformLevel + 1, labels)
        if (!freeformComposer.active()) {
            pendingFreeform = fallback
            return
        }
        freeformComposing = true
        publishComposing("Creating your game…")
        composeJob = viewModelScope.launch(Dispatchers.Default) {
            pendingFreeform = runCatching { freeformComposer.compose(labels, freeformLevel + 1) }
                .getOrNull() ?: fallback
            freeformComposing = false
        }
    }

    private fun publishFreeform(game: FreeformGames.Game) {
        _cues.value = emptyList()
        _ui.value = _ui.value.copy(
            status = PlayStatus.PLAYING,
            instruction = game.instruction,
            stepIndex = 0,
            stepCount = 1,
            completedSteps = 0,
            stepProgress = 0f,
            score = session.totalScore,
            streak = 0,
            challengeIndex = challengeIndex,
            perfect = false,
            coachingHint = game.hints.firstOrNull(),
            evidence = emptyList(),
            timeRemainingMs = null,
            timeFraction = null,
            retriesLeft = 0,
            winnerId = "",
            winnerType = ""
        )
    }

    /**
     * Picks the next arc game (from [arcCursor], cyclically) whose scene requirement is met right
     * now, PINNING it so the exact game plays, then advances the cursor past it. Returns null only
     * when NO arc game fits this scene (e.g. an empty table) so the caller falls back to the normal
     * scored pick and never stalls.
     */
    private fun selectArc(world: WorldState, cap: SceneCapability, ctx: GenerationContext): SelectionResult? {
        val order = DemoArc.orderFor(ctx.ageBand)
        for (i in order.indices) {
            val idx = (arcCursor + i) % order.size
            val id = order[idx]
            if (registry.canPlay(id, cap, ctx)) {
                arcCursor = idx + 1
                return registry.select(world, cap, ctx, SelectionMode.PINNED, pinnedGeneratorId = id)
            }
        }
        return null
    }

    /** Swaps the challenge only when a different generator now wins (preserves gate progress). */
    private fun prepareNextChallenge(world: WorldState, cap: SceneCapability) {
        // Let the scene settle so we don't build a game from an empty first frame after a pass.
        val hasScene = world.objects.isNotEmpty() || world.players.isNotEmpty()
        if (readySinceMs == 0L) readySinceMs = world.timestampMs
        val waited = world.timestampMs - readySinceMs
        if (!hasScene && waited < SETTLE_MS) {
            publishComposing("Looking at your play space…")
            return
        }

        session.previousTier = Difficulty.skillTier(session.rating, session.previousTier)
        val ctx = buildContext(cap)

        // Scripted demo arc (§5): a SOLO session plays the fixed order (Grab → Triangle → Line-up →
        // Sort → combo), skipping only a game the scene can't support — never the adaptive pick. The
        // composer is bypassed so the pinned game type is exactly what plays.
        if (PartyRuntime.active == null && AppSettings.demoArcEnabled.value) {
            val arc = selectArc(world, cap, ctx)
            if (arc != null) {
                beginPlaying(arc, world)
                return
            }
        }

        val deterministic = registry.select(world, cap, ctx, selectionMode)

        // No cloud/on-device model available → play the deterministic game immediately.
        if (!composer.active()) {
            beginPlaying(deterministic, world)
            return
        }

        // Compose the ACTUAL game up-front so the player sees the LLM's game rather than a
        // deterministic placeholder that later swaps (§6.5 v3.7). compose() returns the deterministic
        // result unchanged on any failure, so this always resolves to a valid, verifiable game.
        composing = true
        publishComposing("Creating your game…")
        composeJob = viewModelScope.launch(Dispatchers.Default) {
            val composed = runCatching { composer.compose(world, cap, ctx, deterministic) }
                .getOrDefault(deterministic)
            pendingStart = composed
            composing = false
        }
    }

    /** Begins the resolved [result] as a live mission and publishes the first PLAYING frame. */
    private fun beginPlaying(result: SelectionResult, world: WorldState) {
        readySinceMs = 0L
        challengeIndex++
        sm.reset(GameState.IDLE)
        sm.transition(GameState.SELECTING)
        selection = result
        session.pushType(result.spec.type)
        mission = MissionRunner(result.spec, verifierRegistry, poseBaselineFor(result.spec))
        startTs = 0L
        retriesLeft = MAX_RETRIES
        hintsUsed = 0
        lastMeasured = null
        lastRequired = null
        lastEvidence = emptyList()
        _cues.value = emptyList()

        sm.transition(GameState.INSTRUCTION)
        sm.transition(GameState.PLAYING)
        RpLog.i(RpLog.Tag.ENGINE, "Playing ${result.winnerId} (${result.spec.type}) budget=${result.stepBudget}")
        RpLog.i(RpLog.Tag.ENGINE, "Difficulty: $difficultyExplanation")
        logRankedCandidates(result)
        publishPlaying(result.spec, firstTick(result.spec), 0L)
    }

    /** Publishes the between-challenge COMPOSING phase with a friendly [message]. */
    private fun publishComposing(message: String) {
        if (_ui.value.status == PlayStatus.COMPOSING && _ui.value.instruction == message) return
        _ui.value = _ui.value.copy(
            status = PlayStatus.COMPOSING,
            instruction = message,
            stepProgress = 0f,
            coachingHint = null,
            evidence = emptyList(),
            timeRemainingMs = null,
            timeFraction = null
        )
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
        // Hold on the "Level Complete" celebration for a beat before composing the next game (§3a).
        advanceAtMs = System.currentTimeMillis() + LEVEL_DONE_MS
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
        // Same calm hold on a timeout before the next game composes (§3a).
        advanceAtMs = System.currentTimeMillis() + LEVEL_DONE_MS
    }

    private fun publishPlaying(spec: ChallengeSpec, tick: MissionRunner.Tick, elapsedMs: Long) {
        val coaching = (tick.outcome as? VerificationOutcome.Unsure)?.coachingHint ?: tick.coachingHint
        // Timed hint escalation (§10 rule 6) — a verbal nudge, then a more specific one, only when
        // the verifier itself has nothing more relevant to say and only for TODDLER.
        val escalated = if (coaching == null && ageBand == AgeBand.TODDLER) {
            HintEscalation.hintFor(spec.hints, HintEscalation.levelFor(elapsedMs))
        } else null
        val effectiveHint = coaching ?: escalated
        val status = if (tick.outcome is VerificationOutcome.Unsure || escalated != null) {
            PlayStatus.COACHING
        } else PlayStatus.PLAYING
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
            // Fill the green progress to FULL just before the auto-complete fires, so it visibly
            // "loads completely" at ~10 s; a genuine faster pass still shows its own higher progress.
            stepProgress = maxOf(tick.stepProgress, (elapsedMs.toFloat() / (AUTO_COMPLETE_MS * 0.92f)).coerceIn(0f, 1f)),
            score = session.totalScore,
            streak = session.streak,
            challengeIndex = challengeIndex,
            perfect = false,
            coachingHint = effectiveHint,
            evidence = evNow,
            // No numeric countdown for verified games (§ user request) — the filling green bar is the
            // only progress cue; the game auto-completes at AUTO_COMPLETE_MS.
            timeRemainingMs = null,
            timeFraction = null,
            retriesLeft = retriesLeft,
            winnerId = selection?.winnerId ?: "",
            winnerType = spec.type.name,
            ranked = selection?.ranked ?: emptyList(),
            difficultyExplanation = difficultyExplanation
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
        const val BREAK_SUGGEST_MS = 5 * 60_000L
        /** Max time to wait for the scene to show objects before composing anyway (empty table → G0). */
        const val SETTLE_MS = 1_200L
        /** How long the "Level Complete" celebration holds before the next game composes (§3a). */
        const val LEVEL_DONE_MS = 2_000L
        /** Short calm beat after a skip before the next game composes. */
        const val SKIP_HOLD_MS = 650L
        /** Verified games auto-complete after this long so the demo always flows to the next level.
         *  The green progress bar fills to full over this window; no numeric timer is shown. */
        const val AUTO_COMPLETE_MS = 10_000L
    }
}
