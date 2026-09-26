# RealPlay — Master Build TODO (S0 → S13 + v3.6)

The single source of truth for what is **done** and what is **pending**, in build order.
We complete one item at a time and check its gate before advancing.
Cross-references: Architecture §13 (stages/gates), §7 (generators), §25–§27 (v3.6).

**Legend:** ✅ done & verified · 🟡 partial · ⬜ not started · 🧊 deferred (roadmap only)

---

## Status at a glance

> **Current position (2026-09-27):** S0–S10 complete + DQ + v3.6-A + v3.6-B + v3.6-C + UX design
> system pass (all core screens incl. Calibration/Game) + a VR concept preview (v3.6-D) + v3.7
> hybrid cloud/on-device composer (cloud → Gemma → deterministic, cache-assisted). **NEXT:**
> **S12 hardening/freeze, then P2/real VR headset renderer if time remains.**
> Full engineering context for a fresh machine/agent: **docs/CONTEXT_HANDOFF.md**.

| Stage | Title | Status |
|---|---|---|
| S0 | Foundation (repo, nav, theme, Tier-A bundling) | ✅ |
| S1 | Camera (CameraX, mapper, overlay) | ✅ |
| S2 | Detection (EfficientDet, ColorTagger, pipeline) | ✅ |
| S3 | World + tracker | ✅ |
| S3.5 | Affordances + richness + SceneCapability | ✅ |
| S4 | Verification (24 rules, TemporalGate, PoseMath) | ✅ |
| S5 | Engine + first playable (G0, G1) | ✅ |
| S6 | UI & feel | ✅ |
| **DQ** | **Detection quality (better model + tuning)** | 🟡 lite2 float+GPU done; props fine-tune + live fps gate pending |
| S7 | Zones + G2/G3 + SceneCapabilityCard | ✅ |
| S8 | Pose + identity + G4/G5 | ✅ (P1; on-device in-game gate pending) |
| S9 | Difficulty director + G6/G7 + structural axis | ✅ (on-device in-game gate pending) |
| S10 | Toddler + TTS (visual-first, §26) | ✅ (device TTS/dialog manual gate pending) |
| S11 | SLM / on-device LLM composer | ✅ (needs Tier-B model on device to activate) |
| S12 | Hardening + freeze | ⬜ |
| S13 | P2 bonuses | 🧊 |
| v3.6-A | Presentation seam (present/, RenderModel, MobileTarget) | ✅ |
| v3.6-B | Visual-first coaching (coach/, VisualCue) | ✅ |
| v3.6-C | Party / Event mode (party/) | ✅ (device manual gate pending; TODDLER removed from PARTY) |
| **v3.6-D** | **VrTarget (spatial renderer)** | 🟡 concept preview shipped; real headset renderer still 🧊 |
| **UX** | **Full UI/UX overhaul (design system + all screens)** | 🟡 all core screens done; Toddler skin variant pending |
| **v3.7** | **Hybrid cloud + on-device composer (Azure AI Foundry → Gemma → deterministic)** | ✅ implemented; cloud path untested against a live endpoint by the agent (security hold) |

**Remaining work, in suggested order:** S12 → (S13 / real VR headset renderer deferred).
S11 already shipped (activates only with a side-loaded Tier-B model). DQ has a model-side props
fine-tune + a live-device fps/accuracy gate still open (can be done opportunistically).

---

## ✅ DONE (for reference — do not redo)

### S0 · Foundation ✅
Repo, Compose project, nav graph, theme, `RpLog`, device probe, Tier-A models bundled via
`fetchTierAModels`/`verifyTierAModels`, APK on the iQOO. Build is offline-clean on JDK 21.

### S1 · Camera ✅
`camera/CameraController` (Preview + ImageAnalysis 640×480, KEEP_ONLY_LATEST), `FrameAnalyzer`
(FPS StateFlow, RGBA decode), `CoordinateMapper` (pure; rotate→mirror→FILL_CENTER). Overlay +
calibration rect verified square. ~30 fps analyzer.

### S2 · Detection ✅
`MediaPipeObjectDetector` (**CPU delegate** for int8), `ColorTagger`, `ObjectDetectionPipeline`,
`LabelVocabulary` (friendly kid-names + confidence gate), `AssetModelResolver`. Real detector
~30 fps.

### S3 · World + tracker ✅
`perception/Tracker` (IoU-greedy + centroid, promote 3, coast 8, EMA, ambiguity flags, label
smoothing), `world/*` (WorldState, TrackedObject/Player, SpatialRelations, StabilityDetector,
WorldStateBuilder), `FrameQualityAnalyzer`. Synthetic-sequence tests pass.

### S3.5 · Affordances ✅
`world/Affordances` (8 flags), `world/Richness` (Branch A/B symmetric renormalisation),
`world/SceneCapabilityBuilder`. All affordance + both-branch tests pass.

### S4 · Verification ✅ (pure JVM)
`verify/*`: 24 `RuleId`s, `VerificationOutcome` sealed interface, `TemporalGate` (0.8 ratio,
single-frame-never-fires), `PoseMath` (camera-distance-invariant), `VerifyGeometry`, `Resolution`
policy, 24 verifiers, `VerifierRegistry`. 50 tests <5 s.

### S5 · Engine + first playable ✅
`engine/*` (GameStateMachine, MissionRunner multi-step-ready, ScoreEngine w/ stepBonus,
GameSession), `challenge/*` (Requirement, SelectionMode, DifficultyKnobs, Difficulty,
GenerationContext, ChallengeGenerator, SafetyFilter, ChallengeRegistry), generators **G0 + G1**.
G0-lock bug fixed (speculative upgrade). Full loop verified. 157 tests green.

### S6 · UI & feel ✅
Game screens (HudBar, ProgressRing, StepTracker, EvidencePanel, CoachingToast, BriefingOverlay,
SuccessBurst, SoundManager), real ModeSelect + Result screens, modern-playful palette. Fake
detector removed. Success feedback <200 ms.

### S11 · SLM / on-device LLM composer ✅
`ai/*`: LanguageModel, MockModel, ComposerModel (digests), PromptBuilder, SchemaValidator (pure),
**GemmaModel** (local MediaPipe `tasks-genai`, Tier-B, 3 s timeout, offline), AiRuntime,
ChallengeComposer (deterministic fallback on any failure). Wired into GameViewModel speculatively.
25 AI/detection tests green. **AI-OFF gate proven.** Activates only when a Tier-B `.task` is on
the phone (see docs/MODELS.md).

### v3.7 · Hybrid cloud + on-device challenge composer ✅  *(spec extension — §2/§6.5, invariants 18–20 preserved)*
**Goal:** add an optional CLOUD proposal source (Azure AI Foundry, GPT-5.5) ABOVE the existing
on-device Gemma model, as the first link in the same fallback chain — never a replacement for the
deterministic composer, never a new trust boundary (still validator-gated, still only rewords /
arranges REGISTERED skills, AI-OFF gate still passes fully offline).

- [x] `ai/AzureChatRequest.kt` (pure JVM) — builds the completions URL (override or
      endpoint+deployment+api-version), the request body (JSON-schema structured output
      constrained to `{generatorId, instruction, hints}`, no free-form prose), and extracts
      `choices[0].message.content` from the response. Dependency-free (hand-built/parsed JSON,
      mirrors `SchemaValidator`'s convention) so it runs in local JVM unit tests untouched.
- [x] `ai/CloudModel.kt` — a `LanguageModel` over `HttpURLConnection`, IO dispatcher, ~2 s hard
      timeout, sends ONLY the text scene digest (`PromptBuilder`'s prompt — object labels,
      colours, counts), **never a camera frame**. Any timeout/network/HTTP error returns null and
      falls straight through — zero user-visible difference from cloud being absent.
- [x] `ai/CompositeModel.kt` — chain-of-responsibility `LanguageModel` wrapping
      `[CloudModel, GemmaModel]`; tries each in order, returns the first non-null proposal;
      `isAvailable` true if any child is available. `ChallengeComposer` needed ZERO changes to
      accommodate this — it already only knows about the `LanguageModel` interface.
- [x] `ai/ComposerCache.kt` — a small bounded (32-entry, LRU-ish) cache keyed by
      `ComposerCacheKey(generatorId, capabilityDigest, tier)`, plus a pure `capabilityDigest(cap)`
      fingerprint (counts + sorted colours + track-only flag). `ChallengeComposer.compose()` checks
      the cache before calling `model.propose(...)` and stores the raw text after a successful
      `Accepted` validation — an unchanged scene skips a second cloud/on-device call entirely.
- [x] `AiRuntime.init` now builds `CompositeModel(listOf(CloudModel(BuildConfig.AZURE_*), GemmaModel(...)))`.
- [x] `app/build.gradle.kts` — `buildConfig = true` + `BuildConfig.AZURE_ENDPOINT/API_KEY/DEPLOYMENT/
      API_VERSION/CHAT_COMPLETIONS_URL` read from **`local.properties`** (gitignored, never
      committed) — mirrors the existing `realplay.models.dir` pattern in the same file.
- [x] `AndroidManifest.xml` — added `INTERNET` permission (commented: optional cloud path only,
      app stays offline-first) + `android:usesCleartextTraffic="false"` (HTTPS-only at the OS
      level, defence-in-depth even though the code only ever builds `https://` URLs).
- [x] `SettingsScreen.kt` — toggle label/explainer updated to describe the cloud → on-device →
      deterministic chain and reconfirm full offline playability with the switch off.
- [x] **Tests (all green):** `AzureChatRequestTest` (URL construction incl. override, body
      contains prompt + schema, escaping, content extraction incl. garbage input),
      `CompositeModelTest` (order-of-trial fallback, skips unavailable children, `isAvailable`
      aggregation, all-null → null), `ComposerCacheTest` (get/put/eviction,
      `capabilityDigest` stability/sensitivity), plus a new `ChallengeComposerTest` case proving a
      repeated identical scene calls the model only once (cache hit on the second call).
      343/343 total unit tests green; `assembleDebug` + install + launch smoke-tested on device.
- [x] **Live endpoint VERIFIED WORKING (2026-09-27).** The classic path returned 400 "API version
      not supported" until two fixes: (1) the endpoint must be the RESOURCE ROOT
      `https://poc-agenticextraction.cognitiveservices.azure.com` (the portal also exposes the
      `.services.ai.azure.com` alias) — NOT the `.../api/projects/proj-default` project path, which
      always 400s; (2) `gpt-5.5` is a reasoning deployment, so the body must use
      `max_completion_tokens` (not `max_tokens`) and omit `temperature`. With
      `api-version=2024-08-01-preview` the call now returns 200 with valid structured JSON, e.g.
      G1 → "Scoot the cup over so it can cozy up beside the book…". `AzureChatRequest.buildBody`
      updated accordingly; `CloudModel` timeout raised to 5 s / budget 800 tokens for reasoning.
- [x] **Object-aware prompt:** `PromptBuilder` now tells the model to name the ACTUAL detected
      objects/colours and write one specific, playful sentence — so games read as real games, not
      generic commands. Still validator-gated and re-bound to a registered skill (invariants 18–20).
- [x] **Capability card redesigned:** the "I looked at your table" card no longer shows raw counts /
      "K of N possible" / the ranked score table to players. It now lists the detected objects in
      plain language ("Cup · Book · Ball → Let's make a game out of these!") with a clear "Tap to
      start". The raw ranked scores are gated behind the developer overlay (Settings → Developer).
- [x] **PARTY uses the LLM too:** party already routes through the same `GameViewModel` composer
      (gated only on `composer.active()`), so once the cloud works, party games are LLM-composed —
      the earlier "hardcoded party" feel was the same 400 root cause. **Rotate the pasted key** —
      see `/memories/repo/realplay.md`.

---

## ⬜ PENDING — complete these one by one

### DQ · Detection quality 🟡  *(model-side + tuning)*
**Goal:** fix "detection is very bad." Root cause was the int8 `efficientdet_lite0` (weakest COCO
model, CPU-only). Improving the model is the highest-leverage perception fix.

- [x] **Swap to `efficientdet_lite2` float32** (~22 MB) as the primary object model — much more
      accurate; runs on the **GPU delegate**. Kept int8 lite0 as CPU fallback. (build.gradle +
      `MediaPipeObjectDetector`: GPU-first → CPU float → int8 CPU; threshold 0.4→0.3; maxResults 10→15.)
- [ ] Verify on device: more props detected, ID stability holds, fps still ≥10 (lite2 is heavier —
      if fps drops below 10, fall back to `efficientdet_lite0` **float32** ~13 MB as the middle ground).
- [ ] Tune tracker for the new recall (promote/coast thresholds) if false tracks appear.
- [ ] **Real accuracy fix — `realplay_props.tflite` fine-tune (§1.5):** 200 photos of the actual
      props (40×5 classes) → Roboflow → MediaPipe Model Maker (EfficientDet-Lite0) → loads behind
      the same interface via the Tier-B path (already wired). Biggest real-world gain on your kit.
- [ ] Consider `setScoreThreshold` per-band (lower for OLDER, higher for TODDLER safety).
- **GATE:** your 5 props detected ≥0.5 conf on 3 tables; ≥10 fps; no false-positive floods.

### S7 · Zones + G2/G3 + SceneCapabilityCard ✅ DONE
**Goal:** detect taped colour zones, ship two more object games, and add the 2-second capability card.

- [x] `perception/zone/ZoneRegions.kt` (pure JVM HSV connected-components, quad-from-extremes,
      area 1.5%–40%, saturation/value minimums reject shadows) + `ZoneTracker.kt` (confirm 5 frames /
      survive 15, EMA-smoothed vertices, stable zoneId) + `ZoneDetector.kt` (Android 160×120 wrapper,
      thread-safe snapshot, run every 3rd frame). Emits `world.Zone` (polygon, ColorTag, DETECTED).
- [x] Wired zones into `WorldStateBuilder.onFrame` (populate `WorldState.zones`, flows into
      `SceneCapability.zoneCount`) + `ObjectDetectionPipeline` (detect every 3rd frame) + overlay
      render via `drawZoneShape`/`OverlayZone`. *(PerceptionScheduler spec-gating deferred to S8.)*
- [x] `challenge/generators/G2DropZoneGenerator.kt` — Requirement(movable≥1, container/zone≥1);
      feasibility 0.9 zone / 0.6 object-container; **source trackId ≠ target trackId** (cup-in-itself
      guard); prefers a detected zone; never targets a subject already inside; `POINT_IN_ZONE` (zone)
      / `OVERLAP_RATIO_ABOVE 0.5` (container); maxSteps 2 @HARD (single-step form now, chain in S9).
- [x] `challenge/generators/G3FindColorGenerator.kt` — Requirement(distinctColors≥2);
      feasibility 0.4 + 0.15·n cap 0.95; picks a colour present but NOT on the largest object;
      `COLOR_MATCH` (presence + area folded into selection); maxSteps 2 @HARD.
- [x] Registered G2, G3 via `ChallengeRegistry.default()` (single source of the shipped set).
- [x] `ui/calibration/SceneCapabilityCard.kt` + `SceneCapabilityCardModel.kt` — "I looked at your
      table…" card, counts tick 0→value (600 ms, staggered), tap to skip; denominator from the
      **live registry** (`ChallengeRegistry.rank`/`generators.size`), never hard-coded; long-press
      shows the monospace ranked list with **zero-reasons**; sparse (<0.3) → confident branch;
      human-only scenes show people/movement rows.
- [x] Ranked candidate logging (scores to 2dp + zero-reasons + winner ★) to `RpLog` on every select.
- [x] **Tests (all green):** ZoneRegions region logic + shadow rejection, ZoneTracker confirm/survive/
      stable-id, G2 source≠target guard, G2 zone-preferred + subject-not-inside, G2 cup-in-itself
      degrade, G3 colour-not-on-largest, capability-card denominator from live registry.
- **GATE (§13 S7):** zone stable under 3 lightings no flicker *(EMA + confirm/survive — verify live)* ·
      G2 fails when cup just outside *(PointInZoneVerifier, tested)* · G2 never same trackId source+target ✓ ·
      ranked list logged ✓ · removing an object changes feasibility ✓ · card denominator live ✓ ·
      card numbers match reality on 3 tables *(verify live)*.


### S8 · Pose + identity + G4/G5 ✅ DONE  *(P1; on-device in-game gate still to run)*
**Goal:** add body tracking and the two flagship human games.

- [x] `perception/PoseDetector.kt` — MediaPipe Pose Landmarker on GPU (lite), emits landmarks.
- [x] `perception/PlayerIdentity.kt` — zone-anchored, colour-band tiebreak.
- [x] `engine/PlayerRegistry.kt` — 1 primary / 2 turn-based; identity survives a round.
- [x] `PlayerDynamics` (poseVariety, motionRange) fed into `SceneCapabilityBuilder` (Branch B live).
- [x] `challenge/generators/G4StatueMatchGenerator.kt` — `POSE_MATCH` hold, 2 poses @HARD.
- [x] `challenge/generators/G5RedLightGreenLightGenerator.kt` — `MOTION_BELOW` red window.
- [x] Registered G4, G5. Pose-off still leaves a complete object-only game.
- [x] `PerceptionScheduler` — detector/pose/LLM never concurrent.
- [x] **Tests:** pose extraction, identity across a round, G4/G5 reachable human-only — all green.
- **GATE (§13 S8):** unit + build + install + launch (both detectors GPU, no crash) passed.
      Remaining: in-game 2-player halo / identity-survives / fps device walkthrough (Compose UI
      not uiautomator-drivable on the vivo — manual visual check only).

### S9 · Difficulty director + G6/G7 + structural axis ✅ DONE
**Goal:** adaptive difficulty over time, the structural (multi-step) axis on the phone, and the
last two object games.

- [x] `engine/DifficultyDirector.kt` — pure resolver: skill/scene/age binding ceiling + knobs +
      human-readable explanation string. (§8 rating deltas stay in GameSession.)
- [x] `challenge/generators/G6FetchRaceGenerator.kt` — turn-based fetch race, attribute chosen from
      present objects; ordered `PLAYER_HOLDS_OBJECT`→`COLOR/SHAPE_MATCH`→`PLAYER_IN_ZONE`.
- [x] `challenge/generators/G7TriangleBuildGenerator.kt` — `NON_DEGENERATE_TRIANGLE`, movable≥3,
      **live triangle overlay** (per-edge green/amber, fill when satisfied). Demo centrepiece.
- [x] **Structural axis mechanism:** `VerificationStep.actorIndices` + `ChallengeSpec.scopedTo(step)`
      projection (zero verifier changes; null = backward-compatible). HARD 2-step chaining wired for
      G1 (near→far), G2 (two zones), G3 (two colours).
- [x] Registered G6, G7 (registry now 8 generators).
- [x] **Tests:** StructuralAxisTest (15) + DifficultyDirectorTest (5); all 286 unit tests green.
- **GATE (§13 S9):** unit + build + install + launch passed. Remaining: on-device visual check that
      the triangle overlay turns green only on a real triangle (manual — Compose not uiautomator-drivable).

### S10 · Toddler mode + TTS + visual-first ✅ DONE  *(device manual gate pending)*
**Goal:** the youngest-child path — no timers, no failure, spoken + **visual-first** coaching.

- [x] `ai/Narrator.kt` — Android `TextToSpeech`, prefers en-IN → en-US → default, offline,
      fire-and-forget; speaks every instruction/hint for TODDLER (`GameScreen` observes `ui.instruction`
      / `ui.coachingHint` and calls it — presentation-only, never touches the engine).
- [x] `challenge/ToddlerPolicy.kt` — holdMs ×1.7 capped at 2000 ms, distance/pose tolerances ×2,
      `timeLimitMs` forced null; applied on top of the generic scene-scaled knobs in
      `GameViewModel.buildContext`. No-op for every other band.
- [x] `engine/HintEscalation.kt` — verbal hint at 8 s, a more specific one at 16 s, then the
      always-on §26 visual cue carries it from 24 s; wired into `GameViewModel.publishPlaying`
      (TODDLER only, only when the verifier itself has nothing more relevant to say).
- [x] `engine/ToddlerSupervision.kt` — SharedPreferences-backed, shown once per install; the
      `ModeSelectScreen` supervision dialog blocks Continue for TODDLER until acknowledged.
- [x] Break suggestion after 5 continuous minutes (TODDLER only) — `GameViewModel.maybeSuggestBreak`
      + `dismissBreak()`, rendered as a calm dismissible `BreakSuggestionBanner` in `GameScreen`
      (never a timer or a failure state).
- [x] `GameViewModel.ageBand` now reads `SessionConfig.ageBand` LIVE every frame (an optional
      constructor override remains for tests) — a parent can switch bands mid-session without
      restarting the game.
- [x] Confirmed structurally already true and covered by tests: `stepBudget` is always 1 in
      TODDLER/EARLY (`Difficulty.ageStepCap`), `Fail` already renders identically to `Unsure`
      (the `TemporalGate` never distinguishes them — no engine change needed), max-2-actor
      instructions hold for every TODDLER-eligible generator (G0, G3).
- [x] **Tests (all green):** `ToddlerPolicyTest` (hold/tolerance scaling, 2000 ms cap, timeLimitMs
      stripped), `HintEscalationTest` (8/16/24 s thresholds + fallback), `ToddlerConstraintsTest`
      (registry-level: stepBudget=1, actors≤2, timeLimitMs=null across object-only/human-only/empty
      scenes). 294 unit tests green (was 286).
- **GATE (§13 S10):** unit + build + install + launch passed. Remaining, device-only and manual
      (Compose not uiautomator-drivable on the vivo): TTS actually audible in airplane mode, the
      supervision dialog appears once and persists across restarts, hint escalation timing feels
      right at the table, break banner appears after 5 real minutes.

### S12 · Hardening + freeze ⬜
- [ ] OPEN mode on ≥6 unfamiliar surfaces; registry never null across all 6.
- [ ] RECOMMENDED identical 10/10 on the prop table.
- [ ] Sparse self-narration below richness 0.3; PINNED reachable <2 s.
- [ ] 10 min continuous play, thermal OK; airplane-mode full run.
- [ ] Kill + relaunch resumes from `SessionSnapshot` (add `engine/SessionStore.kt`).
- [ ] APK on both phones + both laptops + USB; Office Kit rehearsed.
- [ ] Repo pushed, docs committed, **no model binaries** in Git.
- **GATE:** the full §21 freeze gate passes on the real device.

### S13 · P2 bonuses 🧊  *(only if S12 fully green)*
- [ ] Kokoro TTS · VLM scene enrichment · **Office Kit deep integration (10% of grade)** ·
      Qualcomm NPU (never claimed unless measured). See docs/MODELS.md §3.

---

## v3.6 EXTENSIONS — presentation & engagement (Architecture §25–§27)

> All three are **presentation/engagement only** — they never touch perception or the verifier
> (invariants 21–25). Build order: seam → cues → party → (VR later). Each is deletable.

### v3.6-A · Presentation seam ✅ DONE  *(low risk, pure JVM)*
- [x] `present/RenderModel.kt` — pure JVM scene description. Zero Android imports.
- [x] `present/SceneGraph.kt`, `present/Overlay.kt` — building blocks of RenderModel.
- [x] `present/PresentationTarget.kt` — `fun render(model)` + input callback.
- [x] `ui/present/MobileTarget.kt` — adapts RenderModel → the Compose overlay.
- [x] GameViewModel emits RenderModel (engine render-agnostic).
- [x] **Tests:** RenderModel purity + MobileTarget mapping — green.
- **Invariants:** 21, 22 upheld.

### v3.6-B · Visual-first coaching ✅ DONE
- [x] `coach/VisualCue.kt` — sealed cues incl. `Highlight`, `PathArrow`, `GhostDemo`, `ZonePulse`,
      `TriangleGuide` (added in S9).
- [x] `coach/CuePlanner.kt` — pure; derives the cue track deterministically from the ChallengeSpec
      (projected via `scopedTo(step)`). Never affects the verdict.
- [x] `coach/CueResolver.kt` / CoachTrack — ordered cues per active step.
- [x] Cues rendered in the Compose overlay via MobileTarget → `ui/overlay/CueCanvas.kt`.
- [x] Age-band policy: TODDLER/EARLY cues PRIMARY, MID/OLDER support.
- [x] **Tests:** cue targeting + no spec/verdict change — green.
- **Invariant:** 23 upheld.

### v3.6-C · Party / Event mode ✅ DONE  *(device manual gate pending)*
**Goal:** turn the room into players. Fast, energetic, fair multi-player rounds over the SAME
verifiable skills — no new way to win. This is the crowd-engagement centrepiece.

**Core orchestration (`party/`, pure JVM):**
- [x] `party/SessionMode.kt` — `SessionMode` (SOLO/PARTY) + `PartyFormat` (RELAY, HEAD_TO_HEAD,
      TEAM_VS_TEAM, CO_OP_STREAK).
- [x] `party/Roster.kt` — `Entrant` + `Roster` (2–8 players OR 2–4 teams; fails closed on bad size
      or duplicate id). Reuses §5 colour-band identity via `ColorTag`, no new tracking.
- [x] `party/RoundPacer.kt` — 20–40 s round budget; TODDLER returns null (no clock, no elimination).
- [x] `party/TurnController.kt` — rotation order, HEAD_TO_HEAD opponent naming, lap/round number.
- [x] `party/Leaderboard.kt` — cumulative score/streak; a team IS one entrant so aggregation is
      free; `CO_OP_STREAK` pools into one shared bucket (`Leaderboard.SHARED_ID`).
- [x] `party/PartyOrchestrator.kt` — every format selects `SelectionMode.OPEN` for controlled
      variety (§16) over the SAME registered skills; `isFeasibilityComparable` is a fairness
      monitoring signal; history trimmed to a 3-round window (mirrors `GameSession`).
- [x] `party/PartySession.kt` — glue: composes the above into one live rotation; ends after every
      entrant has played one lap.
- [x] `engine/PartyRuntime.kt` — process-lifetime holder (mirrors `SessionConfig`); `GameViewModel`
      reads `ageBand`/`selectionMode` LIVE from it (same pattern as the S10 mid-session age read),
      so entering/leaving PARTY needs no ViewModel factory. Party history feeds into
      `GenerationContext.recentTypes` alongside the existing per-session novelty memory.
- [x] `GameScreen` reads `PartyRuntime.active` for a visible round countdown (`PartyRoundBadge`,
      auto-calls `vm.finish()` at zero; TODDLER has none) and, on session end, folds
      `SessionResults` into `PartySession.completeRound(...)` — zero engine/verifier changes.

**Formats (all built from shipped skills, one PartySession model serves all four):**
- [x] Relay/hot-seat, head-to-head (opponent named, still sequential — honest single-camera
      simplification), team vs team (a team is one entrant), co-op streak (shared bucket).

**Party UI (`ui/party/`):**
- [x] `PartyRosterScreen.kt` — format chips, add/remove 2–8 players or 2–4 teams, age band
      (reuses the §10 supervision dialog for TODDLER), Start.
- [x] `PartyHandoffScreen.kt` — whose-turn (name + colour swatch, opponent for HEAD_TO_HEAD),
      live leaderboard, round number + format, Ready.
- [x] `PartyPodiumScreen.kt` — final ranked leaderboard with medal styling, Play Again / Home.
- [x] Home screen PARTY entry point; PARTY skips per-turn Calibration by design (fast pacing over a
      per-turn capability re-read — documented in the nav host, not a missing feature).
- [x] Nav routes `PARTY_ROSTER` / `PARTY_HANDOFF` / `PARTY_PODIUM`; `GAME`'s `onFinish` branches to
      SOLO's Result screen or PARTY's Handoff/Podium based on `PartyRuntime.active`.

- [x] **Tests (all green):** `RosterTest` (bounds + duplicate ids), `RoundPacerTest` (toddler null,
      bounds), `TurnControllerTest` (rotation wraps, opponent, round numbering),
      `LeaderboardTest` (accumulation, streak reset on fail, stable ranking, team + co-op
      aggregation), `PartyOrchestratorTest` (OPEN for every format, history trim, fairness signal),
      `PartySessionTest` (full-lap lifecycle, per-entrant vs shared scoring, live selectionMode).
      322 unit tests green (was 294). No new `RuleId`, no new verifier, no new PASS path.
- **GATE (§13 v3.6-C):** unit + build + install + launch passed. Remaining, device-only and manual:
      a real multi-player rotation on the table, the round countdown feels right, TEAM_VS_TEAM member
      alternation reads clearly. **PARTY never offers TODDLER** (removed 2026-09-27, product decision
      — the roster screen clamps a carried-over TODDLER `SessionConfig.ageBand` up to EARLY); SOLO
      still offers TODDLER via ModeSelect, unaffected.

### v3.6-D · VrTarget 🟡  *(concept preview shipped 2026-09-27 — still NOT a real stereoscopic renderer)*
- [x] `present/VrTarget.kt` — pure JVM: the one real piece of logic a VR target needs, an
      object-size-to-parallax depth proxy (`parallaxFor`). Tested (`VrTargetTest`).
- [x] `ui/present/VrPreviewScreen.kt` — an honest ON-PHONE demo: live camera + live detections
      shown normally on top, and the SAME live detections split into two lens-shaped panes below
      with a `VrTarget` parallax offset between them — answers "what would this look like in a
      headset" without needing OpenXR/headset SDK integration. Entry point: Home → "VR Preview
      (concept)". Presentation only — zero perception/verifier changes (invariant 21).
- [ ] A REAL stereoscopic/headset renderer (OpenXR or similar) — still roadmap-only; this pass
      intentionally did not attempt it (out of scope for a phone-only build).
- Honest Q&A: "We ship the seam AND a same-phone concept preview of the idea; the real headset
      renderer is still roadmap — that needs actual VR hardware/SDK integration."

---

## UX · Full UI/UX overhaul 🟡  *(design system + first re-skin wave done 2026-09-27)*

**Goal:** the app grew across S5–S6 + AI + v3.6; the UI is now inconsistent. Rebuild a single
design system and re-skin every screen so it looks intentional, modern and playful, and so the new
surfaces (AI composer, PARTY multiplayer, visual-first coaching) feel first-class. **UI/quality is
~30% of the grade (§13 S13, §19).** Presentation only — never touches perception/verifier.

**Design system (`ui/theme/` + `ui/common/`):**
- [x] `ui/theme/Dimens.kt` — `RpSpace` / `RpRadius` spacing + corner-radius scale.
- [x] Palette v2 (`ui/theme/Color.kt`) — deeper near-black base (`RpNavyDeep`/`RpNavy`), a
      dialled-back teal primary and warm amber secondary instead of neon candy colours; kept every
      existing colour NAME stable so nothing else needed touching.
- [x] `ui/common/RpComponents.kt` — `RpScaffold` (gradient background + title + scroll), `RpButton` /
      `RpOutlinedButton` / `RpTextButton` (moderate rounded-rect shape + elevation, not a full pill),
      `RpCard` (selectable or plain container, shadow + border), `RpChip`, `RpStatCard`, `RpBadge`,
      `RpToast`, plus `RpHeroBackground` (radial glow) and `RpLogoMark` (a drawn aperture/lens mark —
      no image asset needed) for the Home hero.
- [ ] Icon set beyond the drawn logo mark; full motion-helper library. *(deferred)*

**Screen-by-screen re-skin:**
- [x] **Home** — hero gradient + aperture logo mark, rebalanced vertical rhythm (no more dead
      space), PLAY / PARTY / Settings / VR Preview on the shared components, footer credit line.
- [x] **ModeSelect** — mode cards and age/player chips now `RpCard`/`RpChip`; supervision dialog
      unchanged.
- [x] **Settings** — grouped into `RpCard` sections (Device / Sound / AI composer / Developer) with
      the AI explainer text kept.
- [x] **Result** — `RpScaffold` + `RpStatCard` trio + shared buttons; per-challenge rows unchanged
      (already bespoke and information-dense).
- [x] **Party screens** — Roster/Handoff/Podium re-skinned on the same components; TODDLER removed.
- [x] **Calibration** — top/bottom scrims for legibility, dev readouts on theme tokens, Calibration/
      Capability toggles now `RpChip`, Back/Ready now `RpOutlinedButton`/`RpButton`.
- [x] **Game** — `HudBar`/`StepTracker`/`EvidencePanel`/`BreakSuggestionBanner`/`PartyRoundBadge` on
      theme tokens + subtle shadows; `CoachingToast` now delegates to the shared `RpToast` (killed a
      duplicate implementation); bottom Back/Retry/Finish now shared buttons. `ProgressRing`,
      `BriefingOverlay`'s countdown, `OverlayCanvas`/`CueCanvas` coordinate math untouched (working,
      camera-tied — lower value, higher risk to restyle further).
- [ ] **Toddler skin** — no dedicated visual variant yet beyond existing supervision dialog + TTS.

**Polish:**
- [x] Verified on-device: Home renders with real depth (gradient + shadows), no more flat dead
      space; `assembleDebug` + all 322 unit tests green after the visual pass.
- [ ] Consistent transitions between screens; loading/empty/error states. *(deferred)*
- [ ] Haptics unified. *(deferred — sound cues already respect the mute setting)*
- [ ] Accessibility pass (contrast, touch target sizes, TalkBack labels). *(deferred)*
- **GATE:** partially met — all core screens (Home/ModeSelect/Settings/Result/Party/Calibration/
      Game) use the design system consistently and read as one product; only the Toddler-specific
      visual variant remains for a follow-up pass.

---

## Suggested completion order (one at a time)

1. **DQ** detection quality — verify lite2 on device (+ props fine-tune when you can). *(model done)*
2. **S7** (zones + G2/G3 + capability card) — biggest single jump in demo richness.
3. **v3.6-A** presentation seam — pure, low-risk, unblocks cues + VR story.
4. **v3.6-B** visual-first coaching — high impact for the toddler pitch.
5. **S8** pose + identity + G4/G5 — unlocks human games (feeds PARTY).
6. **S9** difficulty director + G6/G7 — structural axis + triangle centrepiece.
7. **S10** toddler + TTS — wires visual-first as primary for young kids.
8. **v3.6-C** party mode — crowd engagement / multiplayer over everything above.
9. **UX** full UI overhaul — unify + polish all screens incl. PARTY & coaching (do late so it
   covers every surface; pull earlier if the current UI blocks demos).
10. **S12** hardening + freeze.
9. **S13 / v3.6-D** P2 + VR — only if everything above is green.

> After each item: run `:app:testDebugUnitTest`, then `:app:installDebug`, verify its gate on the
> device, update repo memory, and commit. Do not advance on a red gate.
