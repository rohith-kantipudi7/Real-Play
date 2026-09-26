# RealPlay — Final Architecture
**Team Cognex · iQOO Hackathon 2026 · Hyderabad**
**v3.4 — FREEZE CANDIDATE. Supersedes v3, v3.1, v3.2, v3.3, v3.3.1. This is the only file to attach as context.**

> **v3.5 amendment (composition line only):** the Challenge Registry is a *library of verifiable skills*, and an optional AI *composer* arranges those skills — bound to the live WorldState and affordances — into `ChallengeSpec`s. The truth line, data model and freeze gate are unchanged. See §0.1 and §6.5.

Device: iQOO 15 · SD 8 Elite Gen 5 · 16 GB · Android 16
Build: ~15 **critical-path** hours · 2 people · 2 laptops · offline-first

> **Governing law:** AI composes the challenge from registered, verifiable skills. Physics decides the result.
> No model output ever reaches PASS / FAIL.

> **Core claim:** RealPlay has no predefined levels and no fixed games. It reads what is actually in front of it and **composes a mission from a library of verifiable skills** — arranging proven, measurable primitives to fit what the scene can support.

---

## 0. v3.4 changelog

v3.3 resolved four conflicts (C1–C4), four high issues (H1–H4) and seven medium (M1–M7). v3.3.1 added agent-discipline rules to the prompt playbook. **v3.4 closes the two remaining gaps and one spec conflict that v3.3.1 introduced.**

| # | Was | Now |
|---|---|---|
| **G1** | §6.2 renormalised richness only when `playerCount == 0`. A human-only scene (no objects) scored ~0.15–0.20 richness → `sceneTier` pinned to **EASY permanently**, making G4's HARD 12° tolerance unreachable | **Symmetric renormalisation (§6.2).** When `movableCount == 0 && playerCount ≥ 1`, the 0.30 movable + 0.15 container weight redistributes into player-derived terms (`poseVariety`, `motionRange`, `frameCoverage`). Human-only scenes can now reach HARD |
| **G2** | Difficulty had only a numeric axis — every tier of every game was the same mission with tighter thresholds. `MissionRunner` supported ordered multi-step but no shipped generator used it | **`maxStepsForTier` on `ChallengeGenerator` (§6.3, §8.1).** At HARD a generator may emit 2 chained steps of its own primitive. No new verifier, no new generator — `MissionRunner` already runs it |
| **G3** | G5 feasibility `0.5 + 0.3·min(p,2)/2` → 0.50 at single-player, always losing to G4's flat 0.85 under argmax. Human-only RECOMMENDED was effectively a Statue Match generator | **G5 base raised to `0.70 + 0.15·min(p,2)/2`** → 0.775 single-player, 0.85 two-player. Genuinely competes with G4 |
| **C5** | v3.3.1's S4 prompt described `VerificationOutcome` as an **enum**; §4 defines it as a **sealed interface** with per-variant payloads. Under the new SOURCE-OF-TRUTH rule this halts the agent at the highest-risk stage | **Clarified in §4.** `VerificationOutcome` is a sealed interface. The prompt playbook now matches |
| **M8** | Prompt playbook heading said "Fourteen rules", list contained 16; rule 3 ("test on the phone, every prompt") contradicted the new JVM-only exemption | Both corrected in the playbook |

**Kept unchanged from v3.3:** §12 measurement domains · §20 invariants · §21 freeze gate · AI-OFF gate · §3.2 dev-AI vs runtime-AI · §4.2 TemporalGate policy · track-only downgrade semantics · G0 totality · the §18 hallucination answer.

## 0.1 v3.5 — the registry becomes a skill library

v3.4 froze the truth line. **v3.5 reframes the composition line without touching it.** The Challenge Registry is now described as what it always structurally was: a **library of verifiable skills**, not a catalogue of fixed games. Two changes follow.

| # | Was | Now |
|---|---|---|
| **S1** | The optional model only **re-ranked** the deterministic registry's pick and reworded it | The model is a **composer** (§6.5): a fully **on-device, offline** Gemma (`tasks-genai`, side-loaded Tier-B `.task`) that, given the feasible skill list + a live WorldState digest, selects registered skills, binds them to present actors, orders them into a 1–2 step mission and writes the wording. It still emits a *proposal* that must clear `SchemaValidator` |
| **S2** | Generators were "the 8 games" | Generators are **skill templates** over the closed primitive set (§4.1). The deterministic composer builds from them and remains the always-present floor |

**Nothing on the truth line moves.** The composer may only arrange primitives the verifier already knows how to measure; every proposal passes the same validator and safety gate; a rejected or absent proposal falls back to the deterministic composer with zero user-visible difference. Invariants 18–20 (§20) make this binding. The §3.4 package layout, §4 data model, §4.1 closed rule set and §7 generators are unchanged in shape — only their **framing, and the composer's authority to arrange them,** are new.

---

# PART I — PREPARATION

## 1. Downloads — do this week

### 1.1 Tooling, both laptops, identical
```
[ ] Android Studio stable + SDK 35/36 + the JDK pinned in BUILD_MATRIX.md
[ ] VS Code + Kotlin, Gradle, GitHub Copilot, Copilot Chat
[ ] Git + GitHub login on both
[ ] adb on PATH, `adb devices` verified on a real phone
[ ] Vivo/iQOO Office Kit installed, laptop↔phone tested
[ ] ⚠ ONE successful OFFLINE `./gradlew assembleDebug` with the §1.3 deps
[ ] docs/BUILD_MATRIX.md filled with the exact versions that build
```
The last two are non-negotiable. A cold Gradle cache on venue Wi-Fi costs 45 minutes.

### 1.2 Models — two-tier delivery rule

**Tier A — P0, BUNDLED in `src/main/assets/models/`. ~20 MB total. No adb dependency.**

| Asset | Source | Size |
|---|---|---|
| `efficientdet_lite0.tflite` | MediaPipe Object Detector model page | ~5 MB |
| `pose_landmarker_lite.task` | MediaPipe Pose Landmarker page | ~6 MB |
| `pose_landmarker_full.task` | same (fallback) | ~9 MB |
| Android offline TTS voice | on-device: Settings → System → Languages → TTS → install **en-IN + en-US** | — |

> Bundling Tier A satisfies the §21 "no adb-only runtime dependency" gate. Do it at S0.
> **Binaries are never committed.** S0 must implement a reproducible acquisition/copy step that places them into `assets/models/` before assemble, and the build must **fail clearly** if one is missing. Verify the final APK actually contains each asset and log its byte size at startup.

**Tier B — P1/P2, OPTIONAL, side-loaded to `/sdcard/realplay/models/`.**
Because Tier B is optional by definition, `adb push` is *not* a runtime dependency. The app must run identically with the directory empty.

- `gemma3-1b-it-int4.task` MediaPipe LLM bundle (LiteRT community / Kaggle), 0.5–1.5 GB. Must be a `.task` bundle, **not GGUF**. Backup: Qwen2.5-1.5B-Instruct LiteRT bundle.
- `realplay_props.tflite` (your fine-tune, §1.5) — resolution order prefers it when present.
- P2: MoveNet MultiPose · SmolVLM-256M / Qwen2-VL-2B · Kokoro-82M ONNX + onnxruntime-android · Qualcomm AI Hub account (register now, compile on-site).

`.gitignore`: `*.tflite`, `*.task`, `*.onnx`.

### 1.3 Dependencies to pre-cache — pin exact versions into BUILD_MATRIX.md
```kotlin
// P0
androidx.camera:camera-core / camera-camera2 / camera-lifecycle / camera-view
com.google.mediapipe:tasks-vision
androidx.navigation:navigation-compose
androidx.compose:compose-bom
androidx.lifecycle:lifecycle-viewmodel-compose
// P1
com.google.mediapipe:tasks-genai
// P2
com.microsoft.onnxruntime:onnxruntime-android
```
`BUILD_MATRIX.md` pins: Android Studio · AGP · Gradle · Kotlin · Compose BOM · compileSdk/targetSdk/minSdk · JDK · and every version above. **No floating versions.** Clean offline build on both laptops before you travel.

### 1.4 Physical kit — bring it, don't trust the venue
```
[ ] bottle (matte, solid colour)   [ ] hardcover book
[ ] plain cup                      [ ] OPEN tray / box lid (never opaque + closed)
[ ] soft toy                       [ ] coloured tape + A4 coloured sheets (zones)
[ ] 2 coloured wristbands (player-ID tiebreak)
[ ] phone tripod            ← most underrated item here
[ ] plain light tablecloth  ← kills background clutter instantly
[ ] clip-on LED light
```
Tripod + tablecloth + lamp buy more demo reliability than any model choice.

### 1.5 Optional T4 fine-tune — free Colab, ~40 min, high ROI
200 photos of **your actual props** (40 × 5 classes, varied angle/light/background) → label in Roboflow → MediaPipe Model Maker, EfficientDet-Lite0 backbone → `realplay_props.tflite`. Loads behind the same interface. A legitimate "we trained a model" talking point **and** a real accuracy gain. The asset is pre-built; integration code is written during the event.

---

# PART II — ARCHITECTURE

## 2. Locked decisions
| | |
|---|---|
| Player identity | **1 player primary · 2 turn-based secondary · 3–4 deferred.** Zone-anchored, re-anchored every round, colour-band tiebreak |
| Pose | **P1, not required for the core demo.** MediaPipe Pose Lite → MoveNet backup → YOLO-pose/NPU P2 |
| Detection | EfficientDet-Lite0 → `realplay_props.tflite` → **label-free track-ID mode** as ultimate fallback |
| Containment | Opaque box **removed**. Drop Zone (taped target) / Open Tray / Occlusion-as-signal |
| Voice | Android `TextToSpeech` P0 · Kokoro P2 |
| SLM | Gemma 3 1B, **on-device / offline** (side-loaded Tier-B `.task`), P1, **composes `ChallengeSpec`s from registered skills + wording** (§6.5), lazy-loaded. No network, no key. Never on the truth line |
| Selection | **Affordance-scored skill library** (§6), arranged by a deterministic or AI composer — the core idea |
| Difficulty | **Two axes — numeric knobs AND step count** (§8.1) |
| Generators | **8 skill templates shipped** (G0–G7). G8/G9 deferred (§24) |
| Demo | RECOMMENDED / OPEN / PINNED. **No forced script** |
| Build | Single `:app` module. No Hilt, no Room, no multi-module |

## 3. System diagram

```
┌─────────────── PRESENTATION (Compose) ────────────────┐
│ Home · ModeSelect · Calibration · SceneCapabilityCard │
│ Game (preview + OverlayCanvas + HUD) · Result         │
└───────────────────────┬───────────────────────────────┘
                        │ StateFlow<GameUiState>
┌───────────────────────▼───────────────────────────────┐
│ GAME ENGINE                                           │
│ GameStateMachine · MissionRunner · ScoreEngine        │
│ DifficultyDirector · PlayerRegistry · DemoController  │
└──────┬────────────────────────────────┬───────────────┘
       │                                │
┌──────▼──────────────┐      ┌──────────▼───────────────┐
│ SKILL LIBRARY       │      │ VERIFICATION             │
│ Registry of skills  │─spec→│ VerifierRegistry         │
│ 8 skill templates   │      │ TemporalGate             │
│ Requirement + score │      │ Evidence                 │
│ SafetyFilter        │      │ → Pass / Fail / Unsure   │
└──────┬──────────────┘      └──────────▲───────────────┘
       │ optional                       │
┌──────▼──────────────┐      ┌──────────┴───────────────┐
│ AI COMPOSER (opt.)  │      │ WORLD MODEL              │
│ Composer · Validator│─────→│ WorldState               │
│ TTS Narrator        │ scene│ + Affordances            │
└─────────────────────┘      │ + SceneCapability        │
                             └──────────▲───────────────┘
┌───────────────────────────────────────┴───────────────┐
│ PERCEPTION — CameraX ImageAnalysis, KEEP_ONLY_LATEST  │
│ ObjectDetector · PoseDetector · ZoneDetector          │
│ Tracker · PlayerIdentity · FrameQuality               │
│ PerceptionScheduler (enables only what the game needs)│
└───────────────────────────────────────────────────────┘
```

### 3.1 The two lines
```
TRUTH LINE  : Perception → WorldState → Verifier(geometry + time) → PASS / FAIL / UNSURE
COMPOSITION : WorldState → Affordances → Skill Library → (deterministic ∥ LLM composer) → ChallengeSpec
```
The LLM touches only the composition line, and only by arranging registered, verifiable skills (§6.5). Delete it and the deterministic composer still produces a valid game — the truth line is unchanged either way.

### 3.2 Development AI vs Runtime AI
```
DEVELOPMENT AI : Copilot / Claude / Gemini — writes code, tests, docs
RUNTIME AI     : optional small model — composes a spec by arranging registered
                 skills + wording (§6.5); never judges the result
```
Separate systems. Neither is on the truth path. Runtime AI is never required for the core loop. Say this out loud in Q&A; judges conflate them.

### 3.3 Threading contract — violating this kills the demo
```
Main             → Compose only
CameraX analyzer → single-thread executor, < 40 ms, NEVER suspends
Verification     → Dispatchers.Default, pure functions
LLM              → own dispatcher, 2 s target / 3 s hard timeout, never awaited by analyzer
TTS              → fire-and-forget
```
Speculative LLM generation runs on its own dispatcher and must never block or interfere with CameraX analysis. **No LLM inference may execute concurrently with object + pose perception when both are required by the active challenge.**

### 3.4 Package layout
```
com.cognex.realplay
├── ui/          theme · home · modeselect · calibration · game · result · settings · overlay
├── camera/      CameraController · FrameAnalyzer · CoordinateMapper
├── perception/  ObjectDetector · PoseDetector · ZoneDetector · Tracker
│                PlayerIdentity · ColorTagger · FrameQualityAnalyzer · PerceptionScheduler
├── world/       WorldState · TrackedObject · TrackedPlayer · Zone
│                Affordances · SceneCapability · SpatialRelations · StabilityDetector
├── challenge/   ChallengeSpec · Requirement · ChallengeGenerator · ChallengeRegistry
│                SafetyFilter · DifficultyKnobs · generators/
├── verify/      Verifier · VerifierRegistry · TemporalGate · Evidence · Geometry · PoseMath
├── engine/      GameStateMachine · MissionRunner · ScoreEngine · DifficultyDirector
│                PlayerRegistry · GameViewModel · DemoController · SessionStore
├── ai/          LanguageModel · GemmaModel · CloudModel · MockModel · Composer
│                PromptBuilder · SchemaValidator · Narrator
└── device/      DeviceCapabilities · PerformanceProfile · ThermalManager
```
`world/`, `challenge/`, `verify/` contain **zero Android imports** → fully JVM-testable.

### 3.5 Capability modes — the perception→challenge boundary

The challenge layer must never assume a capability perception did not prove. Flags on `SceneCapability` carry this, and the registry pre-filter enforces it:

```
semanticLabelsAvailable = false  → label-dependent generators excluded
                                   (reason: "labels unreliable")
trackOnlyMode           = true   → objects referenced by track ID + highlight colour only
playerCount             = 0      → player generators excluded
planarSurfaceAvailable  = false  → metric evidence forbidden; camera-space only
```

**Downgrade is one-way within a round and re-evaluated each round.** If labels degrade mid-session, `trackOnlyMode` flips on, semantic generators drop out with an explicit reason, and the UI switches to highlight-colour phrasing. Track-only must never silently become semantic again inside the same challenge.

## 4. Data model

```kotlin
// ── WORLD ───────────────────────────────────────────────
data class WorldState(
    val frameId: Long, val timestampMs: Long,
    val objects: List<TrackedObject>, val players: List<TrackedPlayer>,
    val zones: List<Zone>, val quality: FrameQuality,
    val affordances: List<Affordance>, val capability: SceneCapability
)

data class TrackedObject(
    val trackId: Int, val label: String, val confidence: Float,
    val box: NormRect, val center: NormPoint, val color: ColorTag?,
    val ageFrames: Int, val lastSeenMs: Long, val velocity: NormPoint,
    val stable: Boolean, val stale: Boolean, val ambiguous: Boolean
)

data class TrackedPlayer(
    val playerId: Int, val landmarks: List<Landmark>, val torsoBox: NormRect,
    val colorBand: ColorTag?, val motionEnergy: Float,
    val confidence: Float, val ambiguous: Boolean
)

data class Zone(val zoneId: String, val polygon: List<NormPoint>,
                val color: ColorTag, val source: ZoneSource)

data class FrameQuality(val good: Boolean, val reason: String?)   // "Move to better light"

// ── AFFORDANCE — the core idea, §6 ──────────────────────
data class Affordance(
    val trackId: Int,
    val movable: Boolean,    // area < 25% && label ∉ NEVER_MOVE
    val handheld: Boolean,   // area < 8%  && movable
    val container: Boolean,  // label ∈ CONTAINER || encloses a zone
    val landmark: Boolean,   // area > 20% && stable ≥ 3 s
    val colorful: Boolean,   // ColorTag chromatic
    val distinct: Boolean,   // unique (label, color) in scene
    val nameable: Boolean    // label != UNKNOWN && conf > 0.55
)

data class SceneCapability(
    val movableCount: Int, val handheldCount: Int, val containerCount: Int,
    val landmarkCount: Int, val nameableCount: Int,
    val distinctColors: Set<ColorTag>, val playerCount: Int, val zoneCount: Int,
    val spread: Float,          // mean pairwise centroid distance
    val stability: Float,       // fraction of tracks stable
    // ── player-derived terms, used by §6.2 human-only renormalisation ──
    val poseVariety: Float,     // 0..1 — distinct pose clusters observed this session
    val motionRange: Float,     // 0..1 — observed landmark motion span
    val frameCoverage: Float,   // 0..1 — fraction of frame the player body occupies
    val richness: Float,        // §6.2 — renormalises for BOTH empty-player and empty-object
    // ── capability mode flags, §3.5 ──
    val semanticLabelsAvailable: Boolean,
    val trackOnlyMode: Boolean,
    val planarSurfaceAvailable: Boolean
)

// ── CHALLENGE ───────────────────────────────────────────
data class ChallengeSpec(
    val id: String, val type: ChallengeType, val tier: Tier, val ageBand: AgeBand,
    val actors: List<ActorRef>,            // by label | by trackId | zone | player
    val instruction: String, val steps: List<VerificationStep>,
    val timeLimitMs: Long?, val baseScore: Int, val hints: List<String>
)

data class VerificationStep(
    val rule: RuleId, val params: Map<String, Float>,
    val holdMs: Long, val mustFollowPreviousStep: Boolean
)

// ── VERIFICATION ────────────────────────────────────────
// ⚠ VerificationOutcome is a SEALED INTERFACE, not an enum.
// Each variant carries its own payload. Unsure is structurally
// incapable of becoming Pass.
sealed interface VerificationOutcome {
    data class Pass(val confidence: Float, val evidence: List<Evidence>) : VerificationOutcome
    data class Fail(val reason: String, val evidence: List<Evidence>)   : VerificationOutcome
    data class Unsure(val coachingHint: String)                         : VerificationOutcome
}

data class StepEvaluation(
    val outcome: VerificationOutcome,
    val confidence: Float,
    val evidence: List<Evidence>,
    val coachingHint: String? = null
)

data class Evidence(
    val label: String, val measured: Float, val required: Float,
    val comparator: String, val satisfied: Boolean,
    val domain: MeasurementDomain          // NORMALIZED | PIXEL | METRIC — §12
)

// ── SESSION RECOVERY — owned by S12 ─────────────────────
data class SessionSnapshot(
    val sessionId: String, val gameState: String, val score: Int,
    val streak: Int, val difficultyRating: Int, val challengeHistory: List<String>,
    val demoMode: String, val demoPosition: Int
)
```

**`Unsure` is the most important type in the codebase.** Low confidence, missing actor, poor frame, ambiguous identity, insufficient evidence → `Unsure`: coach, never pass, never punish. It's what makes the product feel intelligent rather than broken.

### 4.1 Closed rule set — the entire anti-hallucination story
```
DISTANCE_LESS_THAN   DISTANCE_GREATER_THAN
LEFT_OF  RIGHT_OF  ABOVE  BELOW
POINT_IN_ZONE   OVERLAP_RATIO_ABOVE   OBJECT_VANISHED_IN_ZONE
OBJECT_PRESENT  OBJECT_ABSENT  COLOR_MATCH  SHAPE_MATCH  COUNT_EQUALS
NON_DEGENERATE_TRIANGLE   ARRANGEMENT_MATCH
POSE_MATCH  JOINT_ANGLE_WITHIN  LIMB_RAISED
MOTION_BELOW  MOTION_ABOVE
PLAYER_NEAR_OBJECT  PLAYER_HOLDS_OBJECT  PLAYER_IN_ZONE
```
This closed set **is the skill library's primitive vocabulary** (§6.5) — every rule here is something the deterministic verifier can measure over time, and it is the *only* vocabulary either composer may draw from. The composer (deterministic or LLM) may emit only these enum values. The validator additionally rejects: unresolvable actor references · out-of-range parameters · unsafe verbs · generator types that scored zero · **rules whose capability flag is false** (§3.5) · **step counts exceeding the generator's `maxStepsForTier`** (§8.1).

### 4.2 TemporalGate policy
```
1. FrameQuality must be GOOD.
2. Required actors must be present and unambiguous.
3. ≥ 80% of eligible frames in the window must satisfy the rule.
4. A minimum continuous hold is required where holdMs is defined.
5. A single positive frame can NEVER produce PASS.
6. Missing or ambiguous evidence produces UNSURE, not FAIL.
7. PASS evidence records measured, required, comparator, domain, and time window.
```

## 5. Player identity

**Competition posture:** single-player is the primary, fully-supported path. Two-player turn-based is secondary. 3–4 simultaneous is deferred (§24) and never required for the demo.

**Anchor** at round start — freeze 500 ms, sort torsos by centre X, assign P1..PN left→right into vertical bands, sample each upper-torso HSV as their band colour, render a coloured halo + large "P1" label, TTS *"Player 1 on the left…"*.

**Hold** during round — nearest-centroid match; reassignment only if a challenger scores >1.6× better for 5 consecutive frames.

**Ambiguity** when two torso centres come within 0.08 — resolve by colour band; if unavailable, freeze identities and set `ambiguous` → all player rules return `Unsure`.

**Re-anchor every round.** Identity error can never accumulate.

---

# PART III — THE CORE IDEA

## 6. Affordance-driven selection — the skill library

The Challenge Registry is a **library of verifiable skills**, not a list of fixed games (§6.5). Selection scores which skills the current scene can support; a composer — deterministic by default, optionally an LLM — then arranges the winners into a mission bound to real actors.

Affordances are **derived, never detected** — ~1 ms, no extra model.

```
FURNITURE  = chair, couch, bed, dining table, tv, refrigerator, bench
CONTAINER  = bowl, cup, vase, suitcase, handbag, backpack, sink, box*, tray*
NEVER_MOVE = FURNITURE ∪ { person, tv, oven, microwave }
```
Every flag has a **geometric justification** (area, stability) so the system still works when labels are unreliable — the same degradation path as track-only mode.

### 6.1 Selection pipeline
```
WorldState
  → SafetyFilter(ageBand)                what's allowed
  → AffordanceEngine                     what each thing can do
  → SceneCapability                      what the scene can support
  → Registry pre-filter                  Requirement ints + §3.5 capability flags
  → feasibility(cap) per generator       0.0 = impossible
  → score = feasibility × tierFit × noveltyBonus × ageGate
  → SelectionMode:
        RECOMMENDED → argmax, stable tie-break by generator ID   (deterministic)
        OPEN        → weighted-random among score > 0            (varied)
        PINNED      → forced generator, still fully verified
  → COMPOSE (§6.5):
        deterministic → winning skill's template.generate(...)  (always present)
        LLM optional  → arrange feasible skills into a mission, validator-gated,
                        else fall back to the deterministic spec
      binds REAL track IDs, labels, colours → ChallengeSpec
```

`SelectionMode` gives rehearsed reliability without hard-coded levels: determinism on an unchanged scene, while the ranking remains entirely scene-derived, so moving the props still changes the game.

### 6.2 Richness — symmetric renormalisation ⭐ v3.4

```
BASE TERMS (object-rich scene with players):
  wMovable  = 0.30 · min(movable, 4)/4
  wColors   = 0.20 · min(|distinctColors|, 4)/4
  wPlayers  = 0.15 · min(players, 2)/2
  wContain  = 0.15 · (containers + zones > 0 ? 1 : 0)
  wSpread   = 0.10 · spread
  wStable   = 0.10 · stability

BRANCH A — no players (object-only scene, or pose cut):
  playerCount == 0
    → redistribute the 0.15 player weight:
        wMovable += 0.10   (→ 0.40 · min(movable,4)/4)
        wColors  += 0.05   (→ 0.25 · min(colors,4)/4)
    ⇒ an object-only scene can still exceed 0.85 richness

BRANCH B — no movable objects (human-only scene):    ⭐ NEW in v3.4
  movableCount == 0 && playerCount >= 1
    → redistribute the 0.30 movable + 0.15 container weight into
      player-derived terms:
        wPlayers  = 0.20 · min(players, 2)/2
        wPoseVar  = 0.20 · poseVariety
        wMotion   = 0.15 · motionRange
        wCoverage = 0.15 · frameCoverage
        wColors   = 0.10 · min(|distinctColors|, 4)/4   (clothing/background)
        wSpread   = 0.10 · spread    (multi-player separation; 0 at single-player)
        wStable   = 0.10 · stability
    ⇒ a person actively moving in a well-framed shot reaches richness > 0.70,
      unlocking MEDIUM and HARD on the human-only path

richness = clamp01(Σ)
```

**Why Branch B matters.** Without it, `movableCount == 0` zeroes 0.45 of the weight, capping a human-only scene at ~0.20 richness → `sceneTier = EASY` forever → G4's HARD 12° tolerance and 2000 ms hold are permanently unreachable. That's a defined game mode that can never be demonstrated. Branch B is the symmetric mirror of Branch A.

**Player-derived term definitions (cheap, no new model):**
- `poseVariety` — count of distinct pose-feature clusters observed this session, normalised against 5. Rewards a player who has actually moved through different shapes.
- `motionRange` — 95th-percentile landmark displacement over a 3 s window, normalised. Distinguishes an engaged player from someone standing still.
- `frameCoverage` — torso box area ÷ frame area, normalised against 0.25. Penalises a player too far from the camera for reliable joint angles.

All three are computed in `SceneCapability.from()` from data the pose detector already produces.

Richness remains a **first-class input to difficulty** (§8). A sparse scene cannot produce a hard challenge regardless of skill — but "sparse" is now correctly defined for both object scenes and human scenes.

### 6.3 Skill / generator interface ⭐ v3.4 adds `maxStepsForTier`

A `ChallengeGenerator` is a **skill template**: it pairs one verifiable primitive (§4.1) with the logic to bind it to a real scene. Both the deterministic composer and the LLM composer (§6.5) draw from the same set of these — neither can reach outside it.

```kotlin
enum class SelectionMode { RECOMMENDED, OPEN, PINNED }

interface ChallengeGenerator {
    val type: ChallengeType
    val proven: Boolean                 // eligible in RECOMMENDED
    val requires: Requirement

    fun feasibility(cap: SceneCapability, ctx: GenerationContext): Float

    /**
     * v3.4 — the STRUCTURAL difficulty axis (§8.1).
     * How many chained steps of this generator's own primitive may be emitted
     * at the given tier. Default 1 at every tier. A generator opts in by
     * returning 2 at HARD. Never exceeds 3. The validator rejects any spec
     * whose step count exceeds this.
     */
    fun maxStepsForTier(tier: Tier): Int = 1

    fun generate(
        world: WorldState,
        aff: List<Affordance>,
        ctx: GenerationContext,
        stepBudget: Int                 // = maxStepsForTier(effectiveTier)
    ): ChallengeSpec
}

data class Requirement(
    val minMovable: Int = 0, val minHandheld: Int = 0, val minContainerOrZone: Int = 0,
    val minLandmark: Int = 0, val minPlayers: Int = 0, val minDistinctColors: Int = 0,
    val needsNameable: Boolean = false,          // requires semanticLabelsAvailable
    val needsPlanarSurface: Boolean = false      // requires planarSurfaceAvailable
)
```

`select()` returns `SelectionResult(spec, rankedCandidates, mode)` where **every zero carries a reason string** — `"needs 3 movable, found 2"`, `"labels unreliable"`, `"no player detected"`. The UI renders this. Showing a judge a zero *with a reason* is more convincing than any passing case.

### 6.4 Registry totality — G0 Last Resort
```
Requirement  : EMPTY. No zone. No label. No player.
feasibility  : 0.05 flat, whenever (objects ≥ 1 OR players ≥ 1)
maxSteps     : 1 at every tier — G0 never chains
generate()   : chooses the safest available variant —
                 player present → player variant
                 else object present → object variant
Object variant : "Bring the glowing object close to the camera."
                 → OBJECT_PRESENT, minimum box area 6%
Player variant : "Wave at me!"
                 → LIMB_RAISED + MOTION_ABOVE
```
G0 always loses to a real game (0.05 < everything) but makes the registry **total** — it can never return null. **This is what makes the OPEN-mode finale on an unknown table safe.** The generator itself must never require a player, zone, semantic label, or any other capability in order to remain selectable. **Do not add requirements to G0.**

### 6.5 The skill library and the composer ⭐ v3.5

The registry is not a menu of fixed games — it is a **library of verifiable skills**. A *skill* is the smallest composable unit the system can both *generate* and *prove*:

```
skill = ( RuleId primitive from §4.1        // what the verifier can measure
        + Requirement                        // what the scene must contain
        + feasibility(cap)                   // how well THIS scene suits it
        + parameter ranges                   // legal, safety-bounded knobs
        + maxStepsForTier )                  // how far it may chain itself
```

The eight shipped generators (§7) are the **deterministic composer templates** over this library: each one knows how to bind a skill to real track IDs, labels and colours and emit a valid `ChallengeSpec`. They are the floor — the game is fully playable using them alone, with no model present.

**Two composers, one contract.** Both emit the *same* `ChallengeSpec` type through the *same* `SchemaValidator` gate:

```
DETERMINISTIC COMPOSER (always present)
  Registry argmax over feasible skills → template.generate() → ChallengeSpec
  Deterministic, reproducible; the RECOMMENDED-mode default.

**LLM COMPOSER (optional, on-device, off by default)**
  Backend: Gemma via MediaPipe `tasks-genai`, loaded from a side-loaded Tier-B `.task`
         bundle (/sdcard/realplay/models). Fully offline — no network, no API key.
  Given: the feasible skill list (id, RuleId, requirement, param ranges,
         maxSteps, feasibility, zeroReasons) + a live WorldState digest
         (present actors, affordances, capability flags).
  Task:  choose skills, bind them to present actors, order them into a
         1–2 step mission, and write child-friendly wording.
  Output: a candidate ChallengeSpec — a PROPOSAL, never an authority.
```

**What the composer may compose.** Only registered skills. It selects from the closed §4.1 primitive set, references only actors present in the current WorldState (§20.4), sets parameters only within each skill's published range, respects every §3.5 capability flag, and never exceeds `maxStepsForTier` (§8.1). It composes *structure and language*; it never invents a rule, a measurement, a threshold, or an actor the verifier cannot check.

**What the validator rejects** (unchanged from §4.1, now the composer's hard boundary): unknown RuleId · unresolvable actor · out-of-range parameter · a skill that scored zero for this scene · a rule whose capability flag is false (§3.5) · step count over budget (§8.1) · anything `SafetyFilter` denied (§11). A rejected proposal is discarded silently and the deterministic composer's `ChallengeSpec` is used instead — **zero user-visible difference** (the AI-OFF gate, §S11).

**Engine execution is unchanged.** The `GameStateMachine` and `MissionRunner` execute whichever `ChallengeSpec` wins, step by step, exactly as they do for a hand-written generator spec. They never know or care which composer produced it. The deterministic **Verifier remains the final and only authority** on PASS / FAIL / UNSURE (§4, §20.1).

**Why this is safe.** The composer's freedom is bounded by construction: every primitive it can name is one the verifier already knows how to measure over time. The composer changes *which* verifiable game you play and *how it is worded* — it can never change *how a game is judged*.

> **Governing law, restated for the composer:** the AI arranges proven skills into a mission; the physics engine, not the AI, decides whether the mission was completed.

---

# PART IV — GAMES

## 7. The eight shipped generators

| # | Game | Requirement | feasibility | maxSteps@HARD | Verification |
|---|---|---|---|---|---|
| **G0** | **Last Resort** | none | `0.05` always | 1 | `OBJECT_PRESENT` (area ≥6%) / `LIMB_RAISED` |
| **G1** | **Move it Close** | movable ≥2 | `0.5 + 0.2·min(mv,4)/4 + 0.3·spread` | **2** | `DISTANCE_LESS_THAN` |
| **G2** | **Drop Zone** | movable ≥1, container/zone ≥1 | `0.9` zone · `0.6` object-container | **2** | `POINT_IN_ZONE` + `OVERLAP_RATIO_ABOVE 0.5` |
| **G3** | **Find the Colour** | distinctColors ≥2 | `0.4 + 0.15·n`, cap 0.95 | **2** | `COLOR_MATCH` + `OBJECT_PRESENT` (area ≥4%) |
| **G4** | **Statue Match** | players ≥1 | `0.85` flat | **2** | `POSE_MATCH`, hold 1500 ms |
| **G5** | **Red Light / Green Light** | players ≥1 | **`0.70 + 0.15·min(p,2)/2`** ⭐ | 1 | `MOTION_BELOW` through red window |
| **G6** | **Fetch Race** | players ≥1, handheld ≥2, nameable | `0.6 + 0.3·min(hh,3)/3` | 1 | `PLAYER_HOLDS_OBJECT` → `SHAPE/COLOR_MATCH` → `PLAYER_IN_ZONE` |
| **G7** | **Triangle Build** | movable ≥3 | `0 if <3; else 0.5 + 0.3·spread + 0.2·distinctRatio` | 1 | `NON_DEGENERATE_TRIANGLE` |

**Object-only path (pose disabled): G0, G1, G2, G3, G7 — five games.** The guaranteed floor and the basis of the §22 acceptance split.
**Human-only path (no objects): G0, G4, G5 — three games**, now genuinely competitive with each other thanks to the G5 feasibility change.

### 7.1 Notes that matter

- **G1** selects objects currently separated by >1.8× the target threshold, so the player must genuinely move something. If either object is not `nameable`, it **automatically** switches to highlight-colour phrasing ("the glowing blue one"). Automatic, not a separate mode.
  **HARD, 2 steps:** *"Move the bottle next to the book, then move the cup away from both."* Step 2 is `DISTANCE_GREATER_THAN` with `mustFollowPreviousStep = true`.
- **G2** — **source `trackId` must differ from target `trackId`** (a cup is both `handheld` and `container`; without this guard the game can ask you to put the cup inside itself). Prefer a detected zone over an object-container when both exist. Never target an object already inside.
  **HARD, 2 steps:** two different objects into two different zones, in order.
- **G3** picks a colour that is present but **not** on the largest object in frame.
  **HARD, 2 steps:** *"Show me something red, then something blue."*
- **G4** — **HARD, 2 steps:** two poses in sequence, each held, the second only counted after the first fires. This is the strongest visual demonstration of the structural axis.
- **G5** feasibility raised in v3.4 so a single-player human-only scene doesn't collapse to Statue Match every time under argmax. Still scales with player count.
- **G6** requests only attributes that **some present object actually satisfies**. Turn-based. Already multi-step by nature — does not chain further.
- **G7** is explicitly **2D camera-space geometry** unless `planarSurfaceAvailable`. Rejects collinear points, tiny-area triangles, and points outside the playable surface. Renders the triangle live — each edge green when its constraint holds, amber when not, with area and min-angle printed. Demo centrepiece. Already geometrically hard — does not chain.

---

## 8. Difficulty — two ceilings, two axes

```
effectiveTier = min( skillTier(rating), sceneTier(richness), ageCap(ageBand) )

skillTier : rating 0–100, boundaries 33 / 66, ±5 hysteresis
sceneTier : richness < 0.35 EASY · < 0.65 MEDIUM · else HARD
```
Rating: +12 two consecutive passes · +6 fast pass · −10 fail · −18 two fails · **Unsure = 0 change** (perception noise must never move difficulty).

### 8.1 Axis 1 — numeric knobs

Scene-scaled:
```
distanceThreshold ×= (1 + 0.3·(1 − spread))      // clustered scene → be forgiving
holdMs            ×= (1 + 0.4·(1 − stability))   // jittery scene → demand a longer hold
```

| Knob | Easy | Medium | Hard |
|---|---|---|---|
| distance | 0.30 | 0.20 | 0.12 |
| hold | 500 ms | 1000 ms | 2000 ms |
| time limit | none / 60 s | 30 s | 20 s |
| pose tolerance | 30° | 20° | 12° |
| players | 1 | 1–2 | 2 turn-based |

### 8.1b Axis 2 — structural steps ⭐ v3.4

```
stepBudget = generator.maxStepsForTier(effectiveTier)
```

| Tier | Step budget | Shape |
|---|---|---|
| EASY | 1 | single condition |
| MEDIUM | 1 | single condition, tighter |
| HARD | **1 or 2** — generator's choice | chained, ordered, one shared clock |

Chained steps use `mustFollowPreviousStep = true`. `MissionRunner` already supports this — no new verifier, no new generator, no new UI state. Partial credit is awarded per completed step.

**Why this exists.** Without it, "hard" means only "the number is smaller", and a judge asking *"what does hard actually look like?"* gets a weak answer. With it, the answer is: *"At hard, a generator may compose two of its own primitives into an ordered mission on one clock — and the mission runner enforces the ordering."* That's a real difficulty axis, demonstrable in 15 seconds.

Constraints:
- Never more than 2 steps in the shipped build (`maxStepsForTier` caps at 3 for future use; no generator returns 3).
- Toddler and EARLY bands are **hard-capped to 1 step** regardless of tier.
- The `SchemaValidator` rejects any LLM-proposed spec whose step count exceeds the generator's budget (§4.1).

> *"Difficulty isn't a menu setting. It's the intersection of how good you are and how much your room can support — and at the top it changes the shape of the mission, not just the number."*

## 9. Scoring
```
score = ( base
        + speedBonus(≤30% of limit → +50, ≤60% → +25)
        + precisionBonus(measured ≤ 0.6 × required → +25)
        + stepBonus(+40 per completed step beyond the first) )
        × streakMultiplier(1.0 / 1.1 / 1.2 / 1.3 …, cap 1.5)
        − hintPenalty(10 each, floor 0)
```
`stepBonus` is new in v3.4 — a 2-step HARD mission is worth meaningfully more, and partial completion still scores.

Toddler mode: computed internally, UI shows **stars only** — never a number, never negative.

## 10. Age bands

| Band | Age | Language | Timers | Failure | Max steps | Games |
|---|---|---|---|---|---|---|
| **TODDLER** | 2–4 | 3–5 words, TTS mandatory | none | **none** | **1** | G0, G3, ShowMeTheObject, TouchTheBigOne |
| **EARLY** | 4–6 | one sentence | visual only | gentle retry | **1** | G1, G2, G3 (+G4 if pose on) |
| **MID** | 6–8 | two sentences | countdown | retry + hints | 2 | G1, G2, G3, G7 (+G4, G5, G6 if pose on) |
| **OLDER** | 8+ | multi-clause | strict | scored | 2 | all shipped, Tier 3 unlocked |

Pose-dependent entries are parenthesised because pose is P1 — every band remains playable with pose disabled.

**Toddler hard constraints — enforced in the engine, not the theme:**
```
1. No timers. No clock anywhere. Strip timeLimitMs from every spec.
2. No failure. Fail renders identically to Unsure. The challenge continues.
3. Every instruction spoken. Text is decorative.
4. Max 2 actors per instruction.
5. holdMs × 1.7 and tolerances × 2.0, but ⚠ effective holdMs CAPPED AT 2000 ms
   (§8's stability multiplier can otherwise compound to ~2.4 s — impossible for a toddler)
6. Hints escalate: 8 s verbal → 16 s specific → 24 s on-screen arrow.
7. SafetyFilter STRICT: excludes UNKNOWN labels and any object under 1.5% area.
8. Break suggestion after 5 minutes.
9. Adult-supervision notice shown once on entering Toddler mode, acknowledged
   before play begins. Persisted so it appears once per install.
10. ⚠ stepBudget hard-capped at 1 regardless of tier (v3.4).
```

## 11. Safety
Runs **before** generation; the LLM cannot override it.
```
DENY_ALWAYS   : knife, scissors, glass, chemicals, lighter, socket, cable, hot drink, medicine
DENY_ACTIONS  : climb, jump from height, run out of frame, throw, anything aimed at a head
TODDLER_EXTRA : objects under 1.5% area (choking heuristic), anything UNKNOWN
```
Fail closed.

---

# PART V — PERCEPTION DETAIL

## 12. The things that decide whether this works

**Coordinates.** Every coordinate in `world/`, `challenge/`, `verify/` is **normalized 0..1 in analysis-image space**. Pixel conversion happens **only** in `CoordinateMapper`, which handles rotation, mirroring and the FILL_CENTER crop. If boxes are offset or mirrored, the bug is here — never in the detector.

**Measurement domains are explicit and typed.**
```kotlin
enum class MeasurementDomain { NORMALIZED, PIXEL, METRIC }
```
**Never display centimetres unless `planarSurfaceAvailable` is true** and a calibration pass has established a stable pixel-to-metric mapping for the active surface and camera position. If the phone leaves that setup, fall back to camera-space evidence automatically.

**Evidence wording follows the domain:**
```
NORMALIZED → "0.18 — needed under 0.30"  or  "closer than the target ✓"
METRIC     → "18 cm — needed under 30 cm ✓"   (only after calibration)
```
This is the single most likely place to get caught overstating to a judge. Honest camera-space wording is *more* impressive than a fake centimetre.

**Tracker.** IoU-greedy with centroid fallback · 3 consecutive detections to promote · 8 frames coasting before deletion · EMA α = 0.6 · appearance-histogram tiebreak with incumbent hysteresis for same-label crossings → sets `ambiguous`.

**Frame quality.** Mean luminance + Laplacian-variance blur proxy, every 5th frame. POOR → verifier returns `Unsure` with *"Move to better light"* / *"Hold steady"*. **Never verify on a bad frame.**

**PerceptionScheduler.** Enables the object detector and pose detector **per frame, based on what the active spec references**. Both only when the challenge needs both. Never detector + pose + LLM concurrently.

**Targets on iQOO 15 — measure, do not assume:**
```
preview        30 fps
analysis       640 × 480   (do not raise)
detection      target ≥15 fps · acceptable ≥10 · FAIL below 8
verify latency target <100 ms
LLM            async, 2 s target, 3 s hard timeout
```

**Thermal.** MODERATE → halve detection cadence, pose off unless required. SEVERE → drop resolution, LLM off, reduce overlay. Idle in menus.

---

# PART VI — BUILD PLAN

## 13. Stages, gates, models

Stage windows are **work windows on a critical path, not a serial sum.** S3.5/S4 run in parallel with S1–S3; S6/S7 overlap. If a dependency slips, **cut S11 and P2 before extending the core path.**

> **Models:** GPT-5-Codex = multi-file implementation & build loops · GPT-5 (high) = algorithmic/geometry · Opus 4.1 = architecture, cross-cutting design, audits · Sonnet 4.5 = Compose, tests, docs, speed

### S0 · Foundation — 0:00–1:00 — **GPT-5-Codex**
Repo, Compose project, nav, theme, `RpLog`, device probe, **Tier-A models bundled into `assets/models/` via a reproducible, uncommitted acquisition step**, APK on the iQOO.
```
GATE [ ] offline assembleDebug OK       [ ] launches on iQOO (not emulator)
     [ ] nav works
     [ ] Tier-A assets present IN THE APK, size logged at startup
     [ ] build FAILS CLEARLY when a required Tier-A model is absent
     [ ] no model binaries committed to Git
     [ ] git push from BOTH laptops     [ ] Office Kit mirrors
     [ ] BUILD_MATRIX.md committed with real pinned versions
```

### S1 · Camera — 1:00–2:30 — **GPT-5-Codex** · mapper: **GPT-5 (high)**
```
GATE [ ] permission grant / deny / re-grant, no crash    [ ] ≥25 fps analyzer
     [ ] ⚠ calibration rect (0.25,0.25)–(0.75,0.75) PERFECTLY centred and square
     [ ] still aligned after rotation   [ ] resumes cleanly from background
```
**Do not advance until the rectangle is perfect.** Nearly every later "model bug" turns out to be this.

### S2 · Detection — 2:30–4:00 — **GPT-5-Codex**
```
GATE [ ] YOUR props detected ≥0.5 confidence
     [ ] ≥10 fps (target 15)            [ ] Fake detector runs the whole app
     [ ] no frame-queue buildup         [ ] tested in venue-like light AND clutter
```
**DECISION POINT, 30-minute hard cap:** props unreliable → flip `trackOnlyMode` and/or load `realplay_props.tflite`. Do not fight the detector.

### S3 · World + tracker — 4:00–5:00 — **GPT-5 (high)**
```
GATE [ ] IDs persist through slow movement   [ ] hand occlusion doesn't change ID
     [ ] two similar objects keep distinct IDs, both flagged ambiguous when crossing
     [ ] stable ~500 ms after stopping       [ ] lens covered → POOR
     [ ] synthetic-sequence unit tests pass
```

### S3.5 · Affordances — 5:00–5:30 — **Opus 4.1** ⭐ core idea
```
GATE [ ] <2 ms per frame                     [ ] chair = landmark, not movable
     [ ] cup = handheld AND container        [ ] two identical cups → both distinct=false
     [ ] richness RISES as objects are added, FALLS as the table is cleared
     [ ] ⚠ BRANCH A: playerCount==0 on a rich table → richness > 0.85
     [ ] ⚠ BRANCH B (v3.4): movableCount==0 with one active player
         → richness > 0.70, and sceneTier reaches MEDIUM/HARD
     [ ] poseVariety / motionRange / frameCoverage all move as expected
     [ ] capability flags flip correctly when labels are forced unreliable
     [ ] unit tests for all 8 affordance flags + both renormalisation branches
```

### S4 · Verification — 5:00–6:30, parallel — **GPT-5 (high)** · tests **Sonnet 4.5** ⭐
Pure JVM. Zero camera.
```
GATE [ ] every RuleId: Pass + Fail + both boundaries + missing-actor → Unsure
     [ ] insufficient evidence → Unsure
     [ ] ⚠ a single true frame in a false stream → NO Pass
     [ ] 80% over the window → Pass          [ ] triangle rejects collinear points
     [ ] ARRANGEMENT_MATCH invariant to 2× scale + translation
     [ ] POSE_MATCH invariant to camera distance
     [ ] Evidence carries the correct MeasurementDomain
     [ ] VerificationOutcome is a sealed interface; Unsure cannot become Pass
     [ ] suite runs on JVM in <5 s
```
**Person 2 builds S3.5 + S4 on the JVM while Person 1 does S1–S3.** Biggest speed win available. **No phone required for this stage.**

### S5 · Engine + first playable — 6:30–8:00 — **Opus 4.1**
State machine, `MissionRunner` (multi-step ready), scoring with `stepBonus`, scored registry with `SelectionMode` and `stepBudget`, G0, G1, full loop.
```
GATE [ ] G1 end-to-end, 10 consecutive successes     [ ] timeout ≠ crash
     [ ] retry works        [ ] Unsure coaches, no retry consumed, no difficulty change
     [ ] removing an object mid-scan adapts, no crash
     [ ] ⚠ registry NEVER returns null — property test over random WorldStates
     [ ] ⚠ G0 selects AND generates with ONE object, no zone, no label
     [ ] RECOMMENDED gives the same pick 10/10 on an identical scene
     [ ] MissionRunner runs a synthetic 2-step spec in the correct order
     [ ] no illegal state transitions
```
🎉 **Tag `v0.1-demoable` and build a release APK now.**

### S6 · UI & feel — 8:00–9:30 — **Sonnet 4.5**
```
GATE [ ] readable at arm's length          [ ] ring visibly fills while the condition holds
     [ ] success feedback <200 ms after Pass
     [ ] EvidencePanel shows measured vs required IN THE ACTIVE DOMAIN
         (normalized wording unless calibrated — §12)
     [ ] multi-step missions show "Step 1 of 2" and per-step progress
     [ ] no jank, zero allocation in DrawScope
```

### S7 · Zones + G2/G3 + **SceneCapabilityCard** — 9:30–11:00 — **GPT-5 (high)** · card **Sonnet 4.5** ⭐
```
GATE [ ] zone detected under 3 lighting conditions, no flicker
     [ ] G2 correctly FAILS when the cup is just outside the edge
     [ ] ⚠ G2 never selects the same trackId as both source and target
     [ ] ranked candidate list logged with scores AND zero-reasons
     [ ] removing an object visibly changes which games are feasible
     [ ] card denominator read from the LIVE REGISTRY (8), never hard-coded
     [ ] card numbers match reality on 3 different tables
```

### S8 · Pose + identity + G4/G5 — 11:00–12:30 — **GPT-5-Codex** + **GPT-5 (high)** — **P1, cuttable**
```
GATE [ ] skeleton aligned                  [ ] 2 people labelled P1/P2 with halos
     [ ] identity survives a full round     [ ] crossing → ambiguous → resolved by colour band
     [ ] POSE_MATCH works at 1.5 m AND 3 m  [ ] MOTION_BELOW separates a freeze from natural sway
     [ ] detector + pose ≥10 fps, no overheat after 5 min
     [ ] ⚠ human-only scene: G4 and G5 BOTH reachable under RECOMMENDED
         (verifies the v3.4 G5 feasibility change)
     [ ] ⚠ disabling pose entirely leaves a complete playable game (G0,G1,G2,G3,G7)
```
**DECISION POINT, 20-minute cap:** too slow → MoveNet, or turn-based single-person, or cut S8 entirely.

### S9 · Difficulty + G6/G7 — 12:30–13:30 — **Opus 4.1**
```
GATE [ ] 3 fast wins tighten thresholds    [ ] 2 losses loosen them
     [ ] Unsure changes nothing
     [ ] ⚠ a sparse scene (2 objects) CANNOT produce HARD
     [ ] adding a 3rd well-spread object unlocks Triangle
     [ ] ⚠ v3.4 STRUCTURAL AXIS: at HARD, G1 emits a 2-step ordered mission
         and MissionRunner enforces the ordering
     [ ] ⚠ toddler and EARLY bands are capped at 1 step regardless of tier
     [ ] stepBonus awarded correctly, partial credit on incomplete missions
     [ ] empty table + 1 active player → G4 or G5, richness > 0.70, MEDIUM+ reachable
     [ ] triangle renders live and passes only on a real triangle
```

### S10 · Toddler + TTS — 13:30–14:30 — **Sonnet 4.5**
```
GATE [ ] TTS speaks everything in AIRPLANE MODE
     [ ] no timer, clock or red-X anywhere in toddler mode
     [ ] a wrong action produces encouragement, never failure
     [ ] hints escalate at 8 / 16 / 24 s
     [ ] ⚠ effective holdMs never exceeds 2000 ms even on a jittery scene
     [ ] ⚠ stepBudget is 1 in toddler and EARLY, always
     [ ] adult-supervision notice shown once and acknowledged
     [ ] STRICT safety excludes UNKNOWN and sub-1.5% objects
     [ ] mid-session toggle doesn't crash or leak state
```

### S11 · SLM — off critical path, **60-minute hard cap** — **Opus 4.1** + **GPT-5-Codex**
```
GATE [ ] ⚠ AI-OFF GATE FIRST: with the model disabled, scan → select → play → verify
         → score completes normally. If this fails, S11 can never become a dependency.
     [ ] loads from Tier-B path without crashing, absent file = clean isAvailable=false
     [ ] valid schema-conforming JSON ≥8/10 attempts
     [ ] validator rejects step counts exceeding the generator's budget
     [ ] invalid → fallback → game continues with zero user-visible difference
     [ ] ⚠ never blocks the camera (check fps during generation)
     [ ] speculative generation on its own dispatcher, never concurrent with both perceivers
     [ ] 3 s timeout enforced
```

### S12 · Hardening + freeze — 15:00–16:00 — **Opus 4.1**
```
GATE [ ] OPEN mode succeeds on 6 unfamiliar surfaces   [ ] registry never null across all 6
     [ ] RECOMMENDED picks identically 10/10 on the prop table
     [ ] sparse self-narration fires below richness 0.3
     [ ] PINNED reachable in <2 s                      [ ] 3 lighting conditions
     [ ] 10 min continuous play, thermal OK            [ ] airplane-mode full run
     [ ] kill + relaunch resumes from SessionSnapshot
     [ ] APK on both phones + both laptops + USB       [ ] Office Kit rehearsed
     [ ] repo pushed, docs committed, NO model binaries committed
```

### S13 · P2 bonuses — only if S12 is fully green
Kokoro 2 h (Codex) · VLM enrich 1.5 h (Opus) · **Office Kit deep 1 h (Sonnet) ← 10% of your grade, do not skip** · Qualcomm NPU 2 h+ (GPT-5 high), **never claimed unless measured**.

## 14. Parallel split — single authoritative timeline

| Hour | Person 1 — Android / perception | Person 2 — engine / UI / pitch |
|---|---|---|
| 0–1 | **S0 together** | **S0 together** |
| 1–5 | S1 · S2 · S3 | **S3.5 + S4 on JVM — no phone needed** |
| 5–6.5 | S3 finish, integrate | S4 finish, tests green |
| 6.5–8 | S5 integration | S5 state machine + registry |
| 8–9.5 | S7 zones | S6 UI & feel |
| 9.5–11 | S7 finish | S7 CapabilityCard |
| 11–12.5 | **S8 pose (cuttable)** | S6 polish, sound, result screen |
| 12.5–13.5 | S9 G6/G7 | S9 difficulty + step budget |
| 13.5–14.5 | integration + device testing | S10 toddler + TTS |
| 14.5–15 | **S11 only if everything above is green** | deck + demo script |
| 15–16 | S12 harden, APKs, submit | S12 rehearsal ×5 |

**Stagger sleep.** P1 sleeps h16–21, P2 h21–26.

---

# PART VII — DEMO

## 15. SceneCapabilityCard — your 2 seconds

Shown once after CALIBRATING, counts ticking up, tap to skip.
```
┌──────────────────────────────┐
│  I looked at your table.     │
│                              │
│   4  things you can move     │
│   1  container               │
│   3  colours                 │
│                              │
│   → 5 of 8 games possible    │
│     here.                    │
│                              │
│   Playing:  DROP ZONE        │
└──────────────────────────────┘
```
Zero rows omitted. The denominator is read from the **live registry**, never hard-coded.

If `richness < 0.3`: *"This spot is a bit bare — let's play a simple one."* — **confident, never apologetic.**

**Long-press** reveals the ranked list:
```
DropZone      0.81  ✓ picked
MoveNear      0.74
FindColor     0.70
Triangle      0.00  ✗ needs 3 movable, found 2
StatueMatch   0.00  ✗ no player detected
RedLight      0.00  ✗ no player detected
FetchRace     0.00  ✗ no player detected
LastResort    0.05
```
A zero **with a reason** convinces harder than any passing case.

## 16. Demo modes — no forced scripts

| Mode | Behaviour | Use |
|---|---|---|
| **RECOMMENDED** | argmax, stable tie-break, `proven` only, noveltyBonus off. **Fully scene-derived, but deterministic.** | Opening 2 games |
| **OPEN** | Full registry, weighted-random | The finale |
| **PINNED** | Force one generator | Presenter emergency only |

**Forbidden in every mode, including PINNED:** faked detection, faked verification, hard-coded PASS. A judge will be invited to deliberately fail a challenge and it must genuinely fail.

## 17. The 3-minute run
```
0:00–0:20  CAPABILITY CARD on your prop table
           "It just read this table. Four movable objects, one container,
            three colours. Five of eight games are possible here. It picked this one."

0:20–0:50  G1 MOVE IT CLOSE  (RECOMMENDED)
           Ring fills. Pass. Evidence shows measured vs required in the active domain.
           "That's not a model saying it looks right. The verifier measured it."

0:50–1:30  G2 DROP ZONE, or G4 STATUE MATCH if pose shipped
           If difficulty has reached HARD, show the 2-step mission here —
           "Step 1 of 2" on screen is the structural axis, visible in 5 seconds.

1:30–2:15  ⭐ FINALE — OPEN MODE, THE JUDGES' OWN TABLE
           Walk over. Point. New card, different numbers, different game. Play. Pass.
           "I've never seen that table before. Neither has the app."

2:15–2:40  TODDLER TOGGLE — one tap, same room
           "Find something red!" (spoken). Star burst.
           "Same engine. Three-year-old."

2:40–3:00  "Airplane mode the whole time. Everything ran on this phone.
            AI composed the challenge. Physics decided the result."
```

**De-risking the finale**
```
[ ] Rehearse OPEN on ≥6 unfamiliar surfaces around the venue
[ ] ⚠ G0 verified with ONE object, no zone, no label
[ ] Object-only path verified end-to-end with pose disabled
[ ] Human-only path verified: stand in an empty frame, confirm G4/G5 both reachable
[ ] PINNED reachable in <2 s
[ ] Sparse self-narration rehearsed out loud
```

## 18. Judge Q&A

**"How does it decide which game?"**
> Every generator declares what the scene must contain and scores how well this scene suits it. We derive affordances — movable, container, handheld, landmark — then rank. *[long-press]* Here are the live scores, including why four scored zero.

**"What does 'hard' actually mean?"** ⭐ new answer in v3.4
> Two things. The numbers tighten — the distance threshold halves, the hold time quadruples, the pose tolerance drops from 30° to 12°. And the *shape* changes: at hard, a generator can compose two of its own primitives into an ordered mission on one clock. The mission runner enforces the ordering, and you get partial credit per step.

**"Is the AI picking — or composing?"**
> Both, on the composition line only. The deterministic composer always has a valid answer built from proven skills. The optional model can go further — it arranges those same registered skills into the mission and writes the wording — but every proposal must pass the validator, and the physics still judges it. If the model is wrong or absent, we fall back to the deterministic composer and don't notice. It never touches pass/fail.

**"What if the room is empty?"**
> Object games score zero and the last-resort generator takes over — it needs one object of any kind, no label, no zone. If a person is present, body games open up instead, and the scene-richness model renormalises around what the *person* is doing rather than what's on the table. The registry is total; it cannot return nothing.

**"What if there are no objects at all, just people?"** ⭐ new
> Then richness is computed from pose variety, motion range and how well the player fills the frame — not from object count. A person actively moving in a well-framed shot reaches high richness, so the hard tier is genuinely reachable on the body-only path.

**"Could it hallucinate a win?"**
> The model cannot decide the result. It can only propose a spec from a closed rule set, and the validator rejects anything outside it — including a step count exceeding what the generator allows. The verifier measures and returns Pass, Fail or Unsure. Put it in the wrong place and it fails — try it. *[Let them.]*

**"Is that 18 cm real?"**
> Only when the surface is calibrated. Otherwise we show camera-space units, and we say so. We'd rather show an honest normalized number than a fake centimetre.

**"Why this phone?"**
> It's simultaneously the camera, vision system, game master, physics referee and scoreboard — concurrently, at interactive latency. Remove it and there's no product.

## 19. Failure ladder — tape this to the table
```
Detector misses props   → trackOnlyMode · realplay_props.tflite
                          · tablecloth + lamp (fixes ~70% of cases)
Pose too slow / broken  → MoveNet · turn-based single player · CUT S8 entirely
Zones flicker           → bigger, brighter paper · widen HSV · temporal smoothing
Verification flaky      → wider tolerance · longer hold · bigger objects · TRIPOD
Everything scores 0     → area thresholds wrong for FOV → recalibrate in Settings
Human-only stuck EASY   → check §6.2 Branch B is firing; verify poseVariety/motionRange
2-step missions confuse → set every maxStepsForTier to 1, ship single-step only
LLM won't load          → deterministic generator, no user-visible break
NPU won't compile       → drop it, never mention it
Gradle hell             → pull the other laptop's cached build, don't debug
Git conflicts           → P1 becomes sole integration owner
UI unfinished at h14    → STOP features, polish 3 screens (quality = 30% of the grade)
```

## 20. Implementation invariants — do not weaken these

```
1.  PASS is produced only by deterministic verification code.
2.  FAIL only when required evidence is confidently false; perception uncertainty is UNSURE.
3.  A single frame can never PASS a temporal rule.
4.  A ChallengeSpec may reference only actors and capabilities present in the current WorldState.
5.  Track-only perception cannot silently become semantic perception.
6.  Physical distance is never claimed without validated calibration.
7.  The runtime SLM can be deleted without changing the truth line; the deterministic
    composer keeps composing valid ChallengeSpecs in its absence.
8.  Model loading is lazy and never blocks CameraX analysis.
9.  SafetyFilter runs before generation and cannot be overridden by the LLM.
10. SessionSnapshot is best-effort recovery, never evidence for PASS/FAIL.
11. RECOMMENDED is deterministic; OPEN may use controlled randomness; PINNED cannot fake verification.
12. Single-player object-only remains a complete playable path even with pose disabled.
13. The registry is total — G0 has no requirements and must stay that way.
14. The capability-card denominator is read from the live registry, never hard-coded.
15. VerificationOutcome is a sealed interface; UNSURE is structurally distinct
    from FAIL and can never be converted to PASS.                        [v3.4]
16. Richness renormalises symmetrically — neither an object-only scene nor a
    human-only scene may be permanently capped below its natural tier.   [v3.4]
17. Step count never exceeds the generator's maxStepsForTier, and is always 1
    in TODDLER and EARLY bands.                                          [v3.4]
18. The registry is a library of verifiable skills; a composer may only arrange
    registered primitives — it can neither invent a RuleId, a threshold, nor an
    actor the verifier cannot measure.                                   [v3.5]
19. Both composers (deterministic and LLM) emit the same ChallengeSpec through the
    same SchemaValidator and SafetyFilter; an LLM proposal that fails validation is
    discarded and the deterministic composer's spec is used, with no user-visible
    difference.                                                          [v3.5]
20. The composer decides WHICH verifiable game is played and HOW it is worded,
    never HOW it is judged. PASS/FAIL/UNSURE remains §4 verification code alone. [v3.5]
```

## 21. Architecture freeze gate

v3.4 is frozen only when these pass **on the actual device and demo setup**:
```
BUILD       clean offline build on both laptops · versions in BUILD_MATRIX
            · Tier-A models IN THE APK · build fails if absent · no binaries in Git
            · no adb dependency for the core game
CAMERA      rotation / mirror / crop verified · overlay aligned
PERCEPTION  detection · tracking · occlusion · low light
            · trackOnlyMode correctly disables semantic generators
VERIFY      every RuleId Pass/Fail/boundary/missing-actor/insufficient-evidence
            · single-frame false positives rejected · domains correct
            · VerificationOutcome sealed, Unsure never becomes Pass
CAPABILITY  richness Branch A (no players) AND Branch B (no objects) both verified
DIFFICULTY  numeric axis AND step axis both demonstrable
            · toddler/EARLY capped at 1 step
AI          AI-OFF passes · invalid/timeout/missing model falls back
            · validator rejects over-budget step counts · lazy loading
GAME        registry never null · G0 selects AND generates with one unlabelled object
            · no illegal transitions · single-player object-only path complete
            · human-only path reaches MEDIUM+
SAFETY      fails closed · UNKNOWN excluded in toddler · supervision notice shown
DEVICE      cold start · fps · latency · thermal all measured and recorded
DEMO        deliberate failure fails · bad frame gives UNSURE
            · airplane mode works · OPEN works on ≥6 surfaces
```

## 22. Acceptance — split by pose

**Core, required — object-only path, pose disabled:**
```
[ ] Cold start <3 s on iQOO 15           [ ] camera + overlay perfectly aligned
[ ] G0, G1, G2, G3, G7 all playable      [ ] ≥3 of them rock solid
[ ] Selection visibly scene-driven       [ ] capability card accurate on 3 tables
[ ] 3 difficulty tiers demonstrably different, INCLUDING a 2-step HARD mission
[ ] Toddler toggle works + supervision notice + 1-step cap
[ ] Deliberate failure actually fails    [ ] fully offline, TTS offline
[ ] No crash in 15 min continuous play   [ ] OPEN mode works on 6 surfaces
[ ] APK in 4 places                      [ ] submitted BEFORE the cutoff
[ ] Office Kit used visibly in the pitch
```

**Extended, if pose shipped:**
```
[ ] G4, G5, G6 playable                  [ ] 2-player turn-based works
[ ] identity survives a full round
[ ] human-only scene reaches richness > 0.70 and MEDIUM+ tier
[ ] both G4 and G5 reachable under RECOMMENDED on a human-only scene
```

## 23. One sentence
> **RealPlay is an offline-first, phone-native physical game engine: on-device perception builds a live world model, an affordance and capability layer determines what the current scene can actually support, a registry of verifiable skills is composed into a mission — by a deterministic composer or an optional AI composer that only arranges registered, measurable primitives — tightening thresholds and, at the top tier, chaining primitives into ordered multi-step missions, the game engine executes the resulting spec, and deterministic geometry plus temporal evidence remain the sole authority that decides PASS, FAIL or UNSURE.**

## 24. Deferred — designed, not built

Mention in Q&A as roadmap; **do not build during the event.**
- **G8 Memory Rebuild** — `ARRANGEMENT_MATCH`, Procrustes-normalised. Verifier exists in S4; no generator.
- **G9 Sequence Sprint** — cross-generator missions (a step from G2 + a step from G4 on one clock). Distinct from v3.4's `maxStepsForTier`, which chains a generator's *own* primitive. `MissionRunner` supports both; only the same-primitive case ships.
- **3–4 simultaneous players** — identity design supports it; only 1–2 validated.
- **Metric calibration** — `planarSurfaceAvailable` and `MeasurementDomain.METRIC` wired; calibration pass deferred.
- **VLM scene enrichment · Kokoro voice · Qualcomm NPU** — P2, §S13.

*"The verifier for Memory Rebuild is already written and unit-tested; we just didn't ship the generator"* is a strong answer. Claiming they work is not.