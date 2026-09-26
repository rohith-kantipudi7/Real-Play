# RealPlay — Master Build TODO (S0 → S13 + v3.6)

The single source of truth for what is **done** and what is **pending**, in build order.
We complete one item at a time and check its gate before advancing.
Cross-references: Architecture §13 (stages/gates), §7 (generators), §25–§27 (v3.6).

**Legend:** ✅ done & verified · 🟡 partial · ⬜ not started · 🧊 deferred (roadmap only)

---

## Status at a glance

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
| **DQ** | **Detection quality (better model + tuning)** | 🟡 lite2 float+GPU done; props fine-tune pending |
| **S7** | **Zones + G2/G3 + SceneCapabilityCard** | ⬜ **NEXT** |
| S8 | Pose + identity + G4/G5 | ⬜ |
| S9 | Difficulty director + G6/G7 | 🟡 (knobs done; G6/G7 + adaptive loop pending) |
| S10 | Toddler + TTS (visual-first, §26) | ⬜ |
| S11 | SLM / on-device LLM composer | ✅ (needs Tier-B model on device to activate) |
| S12 | Hardening + freeze | ⬜ |
| S13 | P2 bonuses | 🧊 |
| **v3.6-A** | **Presentation seam (present/, RenderModel, MobileTarget)** | ⬜ |
| **v3.6-B** | **Visual-first coaching (coach/, VisualCue)** | ⬜ |
| **v3.6-C** | **Party / Event mode (party/)** | ⬜ |
| **v3.6-D** | **VrTarget (spatial renderer)** | 🧊 |
| **UX** | **Full UI/UX overhaul (design system + all screens)** | ⬜ |

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


### S8 · Pose + identity + G4/G5 ⬜  *(P1, cuttable)*
**Goal:** add body tracking and the two flagship human games.

- [ ] `perception/PoseDetector.kt` — MediaPipe Pose Landmarker (`pose_landmarker_lite.task`,
      full fallback), emits `world.TrackedPlayer` landmarks; MoveNet backup noted.
- [ ] `perception/PlayerIdentity.kt` — zone-anchored, re-anchored per round, colour-band tiebreak
      (reuse for both SOLO turn-based and later PARTY).
- [ ] `engine/PlayerRegistry.kt` — 1 primary / 2 turn-based; identity survives a round.
- [ ] Feed `PlayerDynamics` (poseVariety, motionRange) into `SceneCapabilityBuilder` (currently
      EMPTY) so Branch B richness becomes live.
- [ ] `challenge/generators/G4StatueMatchGenerator.kt` — players≥1, feasibility 0.85 flat,
      `POSE_MATCH` hold 1500 ms, maxSteps 2 @HARD (two poses in sequence).
- [ ] `challenge/generators/G5RedLightGreenLightGenerator.kt` — players≥1, feasibility
      0.70 + 0.15·min(p,2)/2, `MOTION_BELOW` through the red window.
- [ ] Register G4, G5. Update ModeSelect (enable Body/Mixed when pose available).
- [ ] `PerceptionScheduler` — never run detector + pose + LLM concurrently.
- [ ] **Tests:** pose feature extraction, identity across a round, G4/G5 both reachable human-only.
- **GATE (§13 S8):** skeleton aligned · 2 players P1/P2 halos · identity survives round · crossing→
      ambiguous→resolved by band · POSE_MATCH at 1.5 m & 3 m · MOTION_BELOW separates freeze from
      sway · detector+pose ≥10 fps no overheat · human-only G4 & G5 both reachable · disabling
      pose leaves a complete object-only game.
- **DECISION POINT (20 min):** too slow → MoveNet / turn-based single / cut S8 entirely.

### S9 · Difficulty director + G6/G7 ⬜  *(🟡 knobs exist)*
**Goal:** adaptive difficulty over time, the structural (multi-step) axis on the phone, and the
last two object games.

- [ ] `engine/DifficultyDirector.kt` — 3 fast wins tighten thresholds; 2 losses loosen; Unsure
      changes nothing; drives numeric axis AND step axis; toddler/EARLY capped at 1 step.
- [ ] `challenge/generators/G6FetchRaceGenerator.kt` — players≥1, handheld≥2, nameable;
      feasibility 0.6 + 0.3·min(hh,3)/3; `PLAYER_HOLDS_OBJECT`→`SHAPE/COLOR_MATCH`→`PLAYER_IN_ZONE`;
      turn-based; requests only attributes some present object satisfies. (Needs pose/identity.)
- [ ] `challenge/generators/G7TriangleBuildGenerator.kt` — movable≥3; feasibility
      0.5 + 0.3·spread + 0.2·distinctRatio; `NON_DEGENERATE_TRIANGLE`; 2D camera-space unless planar;
      renders the triangle live (edges green when constraint holds). Demo centrepiece.
- [ ] Wire HARD 2-step missions end-to-end (G1 already maxSteps 2; verify MissionRunner ordering
      on device with "Step 1 of 2").
- [ ] Register G6, G7.
- [ ] **Tests:** director tighten/loosen, sparse scene cannot HARD, 3rd object unlocks Triangle,
      G1 HARD emits ordered 2-step, toddler/EARLY capped at 1, stepBonus partial credit.
- **GATE (§13 S9):** as above + triangle renders live and passes only on a real triangle.

### S10 · Toddler mode + TTS + visual-first ⬜  *(ties into v3.6-B §26)*
**Goal:** the youngest-child path — no timers, no failure, spoken + **visual-first** coaching.

- [ ] `ai/Narrator.kt` (or `engine/Narrator`) — Android `TextToSpeech`, offline en-IN/en-US,
      fire-and-forget; speaks every instruction/hint.
- [ ] Toddler engine enforcement (§10): strip timers, Fail renders as Unsure, max 2 actors,
      holdMs×1.7 capped 2000 ms, tolerances×2, hints 8/16/24 s, STRICT SafetyFilter (UNKNOWN +
      sub-1.5% excluded), break at 5 min, supervision notice once per install, stepBudget=1.
- [ ] Wire the **VisualCue track** (v3.6-B) as the PRIMARY carrier for TODDLER/EARLY.
- [ ] **Tests:** TTS in airplane mode, no timer/clock/red-X in toddler, wrong action = encouragement,
      hint escalation, effective holdMs ≤2000, stepBudget=1, supervision notice once.
- **GATE (§13 S10):** all of the above on device.

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

### v3.6-A · Presentation seam ⬜  *(do first — low risk, pure JVM)*
- [ ] `present/RenderModel.kt` — pure JVM scene description (camera/passthrough layer, highlights,
      zones, skeleton, path arrows, ghost demos, HUD, cue track). Zero Android imports.
- [ ] `present/SceneGraph.kt`, `present/Overlay.kt` — building blocks of RenderModel.
- [ ] `present/PresentationTarget.kt` — `fun render(model: RenderModel)` + input callback.
- [ ] `present/MobileTarget.kt` — adapts RenderModel → the existing Compose overlay (ships now).
- [ ] Refactor GameViewModel to emit RenderModel (engine stays render-agnostic).
- [ ] **Tests:** RenderModel is pure (no Android), MobileTarget maps every element.
- **Invariants:** 21 (target renders only RenderModel), 22 (pure JVM).

### v3.6-B · Visual-first coaching ⬜
- [ ] `coach/VisualCue.kt` — sealed: `Highlight`, `PathArrow`, `GhostDemo`, `Pictograph`, `ZonePulse`.
- [ ] `coach/CuePlanner.kt` — pure; derives the cue track **deterministically from the ChallengeSpec**
      (highlight the actual target, arrow to the actual goal, ghost of the actual pose). Never
      authored by the model, never affects the verdict.
- [ ] `coach/CoachTrack.kt` — ordered cues per active step.
- [ ] Render cues in the Compose overlay (pulse, animated arrow, looping ghost silhouette, big
      pictograph) via MobileTarget.
- [ ] Age-band policy (§10): TODDLER/EARLY cues PRIMARY (words decorative); MID/OLDER cues support.
- [ ] **Tests:** cue targets the actual spec actor/goal, TODDLER path is visuals-primary, no spec
      or verdict changes.
- **Invariant:** 23 (presentation only).

### v3.6-C · Party / Event mode ⬜  *(multiplayer — functions & birthday parties)*
**Goal:** turn the room into players. Fast, energetic, fair multi-player rounds over the SAME
verifiable skills — no new way to win. This is the crowd-engagement centrepiece.

**Core orchestration (`party/`):**
- [ ] `party/SessionMode.kt` — SOLO | PARTY (SOLO is current flow).
- [ ] `party/Roster.kt` — **2–8 players or 2–4 teams**; add/name/colour each entrant; reuse §5
      identity (colour-band / hot-seat), no new tracking. Persist for the session.
- [ ] `party/RoundPacer.kt` — short capped rounds (20–40 s) + visible countdown; auto-advance so
      the room never stalls; toddler-safe pacing (no elimination / clock pressure when TODDLER).
- [ ] `party/PartyOrchestrator.kt` — picks the next challenge with OPEN-style controlled variety
      (consecutive players get DIFFERENT games from the same table); enforces **fairness**
      (comparable feasibility across a rotation); composes only registered skills via the same
      composer/validator/SafetyFilter.
- [ ] `party/Leaderboard.kt` — cumulative score/streaks; **team aggregation**; celebratory reveal.
- [ ] `party/TurnController.kt` — whose turn, rotation order, "pass the phone" / tripod hot-seat
      handoff prompts between turns.

**Formats (all built from shipped skills):**
- [ ] Relay / hot-seat — each entrant one quick round; highest score/streak wins.
- [ ] Head-to-head — two players, same challenge, first verified PASS wins.
- [ ] Team vs team — alternating members, aggregate score.
- [ ] Co-op streak — the whole room keeps one shared streak alive.

**Party UI (`ui/party/`, coordinate with the UX overhaul):**
- [ ] Roster setup screen (add players/teams, pick colours/avatars).
- [ ] "Whose turn" handoff screen with big name + colour + countdown.
- [ ] Live leaderboard (animated rank changes, streak flames, team bars).
- [ ] Between-round celebration + "next up" energy (reuse SuccessBurst/sound, bigger).
- [ ] Final podium / winner reveal.
- [ ] PARTY entry point on Home + mode config in ModeSelect (player/team count, format).

- [ ] **Tests:** orchestrator variety + fairness across a rotation, turn rotation correct,
      leaderboard/team aggregation, deliberate failure still fails, TODDLER pacing has no
      elimination, no new RuleId / no new PASS path.
- **Invariant:** 24 (same composer/validator/verifier; no new way to win).

### v3.6-D · VrTarget 🧊  *(roadmap — designed, not built)*
- [ ] `present/VrTarget.kt` — stereoscopic/spatial renderer consuming the identical RenderModel
      (world-anchored highlights/ghosts, floating leaderboard). No perception/verifier change.
- Honest Q&A: "We ship the seam; the engine is already presentation-agnostic — VR is another
  target over the same scene model." Do not claim it works until it does.

---

## UX · Full UI/UX overhaul ⬜  *(a lot changed — unify + polish every screen)*

**Goal:** the app grew across S5–S6 + AI + v3.6; the UI is now inconsistent. Rebuild a single
design system and re-skin every screen so it looks intentional, modern and playful, and so the new
surfaces (AI composer, PARTY multiplayer, visual-first coaching) feel first-class. **UI/quality is
~30% of the grade (§13 S13, §19).** Presentation only — never touches perception/verifier.

**Design system (`ui/theme/` + `ui/common/`):**
- [ ] Finalise the palette + semantic tokens (surface/primary/secondary/success/error/streak) in
      one place; kill remaining hardcoded hex in chrome. Keep ColorTag semantic maps accurate.
- [ ] Typography scale (display/title/body/label) + spacing/radius/elevation tokens.
- [ ] Reusable components: `RpButton`, `RpCard`, `RpChip`, `RpStatCard`, `RpBadge`, `RpProgressRing`,
      `RpToast`, `RpScaffold` (consistent top bar/background), motion helpers (enter/celebrate).
- [ ] Icon set (consistent), gradient/blur background treatment, dark-first.

**Screen-by-screen re-skin:**
- [ ] **Home** — hero, clear SOLO vs PARTY entry, Settings; energetic but calm.
- [ ] **ModeSelect** — mode cards (Objects/Body/Mixed), age chips, player/team + format config
      (feeds PARTY); better selection states.
- [ ] **Calibration** — friendlier "getting ready", the SceneCapabilityCard (S7) as a polished
      reveal, clean dev overlay behind a flag.
- [ ] **Game** — refined HUD, ProgressRing, StepTracker ("Step 1 of 2"), EvidencePanel wording,
      CoachingToast, BriefingOverlay, SuccessBurst; make room for VisualCue rendering (v3.6-B).
- [ ] **Result** — scorecard, per-challenge rows, streak highlights, Play Again / Home.
- [ ] **Settings** — grouped sections (AI composer, sound, developer); clear on/off + explainers.
- [ ] **Party screens** (with v3.6-C) — roster, turn handoff, live leaderboard, podium.
- [ ] **Toddler skin** (with S10) — bigger targets, visuals-first, no scary failure states.

**Polish:**
- [ ] Consistent transitions between screens; loading/empty/error states.
- [ ] Haptics + sound cues unified; respect the mute setting.
- [ ] Readable at arm's length; no jank; zero allocation in DrawScope (§S6 gate holds).
- [ ] Accessibility pass (contrast, touch target sizes, TalkBack labels on key controls).
- **GATE:** every screen uses the design system (no stray hex), looks consistent, readable at
      arm's length, success feedback <200 ms, no jank, PARTY + AI + coaching surfaces feel native.

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
