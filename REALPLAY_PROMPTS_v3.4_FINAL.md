# RealPlay — Prompt Playbook v3.4

**Companion to `REALPLAY_ARCHITECTURE_v3.4_FINAL.md`. Attach ONLY that file.**
Do not attach v3, v3.1, v3.2, v3.3 or v3.3.1 — they contain resolved contradictions that will confuse the agent.

---

## Workflow

1. VS Code → Copilot Chat → **Agent mode**
2. Attach `#REALPLAY_ARCHITECTURE_v3.4_FINAL.md`
3. **Fresh chat per stage.** Paste the preamble, then the stage prompt.
4. After every stage: **BUILD → run the stage GATE → DEPLOY to the target phone when the stage affects runtime behaviour → COMMIT.** Pure JVM-only stages (S4) do not require a phone deployment.
5. Gate fails → Part B.

### Preamble — once per stage
```
Attached REALPLAY_ARCHITECTURE_v3.4_FINAL.md is the complete and only spec.
Read it fully — including §20 Implementation Invariants — before writing code.
Implement exactly what it specifies. Do not invent alternatives. Do not
simplify away the affordance model.

SOURCE-OF-TRUTH RULE:
If this prompt conflicts with the attached architecture, STOP and report the
exact conflict. Do not silently choose an interpretation, invent a behavior,
or modify the architecture to resolve it.

DEPENDENCY/API RULE:
Before adding or using any dependency API, verify that the requested API exists
in the pinned version from docs/BUILD_MATRIX.md. Do not silently substitute a
different library, newer API, or incompatible version. If unavailable, stop and
report the mismatch before changing the design.

Non-negotiable:
- Kotlin + Compose, single :app module, no Hilt/Room/multi-module
- All coords in world/, challenge/, verify/ are NORMALIZED 0..1 in
  analysis-image space; pixels only inside CoordinateMapper
- The camera analyzer NEVER suspends on LLM inference
- No model output ever decides PASS/FAIL
- VerificationOutcome is the SEALED INTERFACE defined in §4 — Pass, Fail,
  Unsure — never an enum. Low confidence, missing actors, poor frames,
  ambiguous identity or insufficient evidence MUST return Unsure, and Unsure
  must be structurally incapable of becoming Pass
- Evidence must carry its MeasurementDomain; never print centimetres unless
  planarSurfaceAvailable is true (§12)
- Step count never exceeds the generator's maxStepsForTier (§6.3, §8.1b)
- world/, challenge/, verify/ contain zero Android imports (JVM-testable)
- Add no dependency outside §1.3
- Touch only files in scope for this prompt

I'm a web developer new to Android — one-line explanation when you introduce
an Android concept. End every response with my manual test steps.
```

---

# PART A — Build prompts

## S0 · Foundation — **GPT-5-Codex**
```
Implement §13 S0.

New Android project: package com.cognex.realplay, Kotlin + Compose +
Material3, Gradle KTS with a version catalog. Use the exact versions from
docs/BUILD_MATRIX.md — if that file is absent, create it and pin every
version you choose, with no floating versions anywhere.

Build the §3.4 package structure exactly, with a placeholder file in each
package. Add MainActivity (edge-to-edge), RealPlayTheme (dark navy,
electric cyan + warm amber), navigation-compose with routes
home/modeselect/calibration/game/result/settings, HomeScreen with the
wordmark, "Your world is the game.", a PLAY button, and placeholders.

TIER-A MODEL BUNDLING (§1.2) — do this now, not later:
Create src/main/assets/models/ and wire an AssetModelResolver that copies or
memory-maps bundled models. Tier-A model binaries MUST NOT be committed to
Git. Implement a documented, reproducible acquisition/copy step that places
the required binaries into src/main/assets/models/ BEFORE assemble/release.
The build MUST fail clearly if a required Tier-A model is missing. Verify the
final APK actually contains every required Tier-A asset, and log each asset's
byte size at runtime. The core game must have NO adb dependency (§21).

Add RpLog with the subsystem tags. Add device/ per §3.4: DeviceCapabilities
(Android version, model, RAM, cores, battery, thermal via PowerManager
guarded by API level), PerformanceProfile enum + its selection function,
logged at startup and shown in a Settings list.

.gitignore: Android defaults PLUS *.tflite, *.task, *.onnx.
README with the §23 one-liner.

Give me the build and install commands.
```

## S1 · Camera — **GPT-5-Codex** (switch to **GPT-5 high** for the mapper)
```
Implement §13 S1 and the "Coordinates" part of §12.

1. CameraX deps + CAMERA permission.
2. CameraPermissionGate: rationale screen, permanent-denial path opening app
   settings, content rendered only when granted.
3. CameraController: Preview + ImageAnalysis, KEEP_ONLY_LATEST, 640x480,
   single-thread executor, full try/catch logging around bind.
4. FrameAnalyzer: for now only a rolling 30-frame FPS StateFlow. MUST call
   imageProxy.close() in a finally block.
5. CoordinateMapper — the highest-risk class in the project. Pure, no Android
   imports, constructed from (imageW, imageH, rotationDegrees, isFrontCamera,
   viewW, viewH, scaleType). Derive the transform step by step in comments:
   image space → rotation → mirror → FILL_CENTER crop → view pixels.
   JVM unit tests for all four rotations and both cameras.
6. ui/overlay/OverlayCanvas + DetectionBox, ZoneShape, SkeletonShape (last
   two unused for now).
7. CALIBRATION: debug flag drawing a fixed rect from normalized (0.25,0.25)
   to (0.75,0.75) plus crosshairs at (0.5,0.5).

Tell me exactly what I should see if the mapper is correct.
```

## S2 · Detection — **GPT-5-Codex**
```
Implement §13 S2.

1. perception/: RawDetection(label, confidence, box: NormRect) and
   interface ObjectDetectorSource { detect(MPImage, timestampMs); close() }
2. MediaPipeObjectDetector: efficientdet_lite0.tflite, LIVE_STREAM,
   scoreThreshold 0.4, maxResults 10, GPU delegate with automatic CPU
   fallback logged loudly.
   MODEL RESOLUTION ORDER (§1.2): Tier-B /sdcard/realplay/models/ if present
   (lets me swap in realplay_props.tflite), else bundled assets/models/.
   Log which tier and path won. Convert MediaPipe pixel boxes to NormRect.
3. FakeObjectDetector replaying scripted detections so the app runs with no
   camera. Settings toggle to switch.
4. ColorTagger: sample the central 40% of each box, ≤200 pixels, RGB→HSV,
   classify to ColorTag, use saturation/value thresholds to split
   white/gray/black from chromatic, UNKNOWN below 45% dominance. JVM tests
   with synthetic pixel arrays including achromatic edge cases.
5. Wire into FrameAnalyzer. Explain your ImageProxy→MPImage conversion and
   why it's the fewest copies.
6. Render boxes with label, confidence and colour name.
7. Add a Settings dev toggle "force track-only mode" that I will use at S3.5
   to test the §3.5 capability downgrade.
```

## S3 · World + tracker — **GPT-5 (high reasoning)**
```
Implement §13 S3 and the "Tracker" part of §12.

1. perception/Tracker.kt: greedy IoU assignment (threshold 0.3) with centroid
   fallback (0.12 normalized) for fast movement. Prefer same-label matches;
   allow cross-label only above IoU 0.6. Promote after 3 consecutive
   detections. Coast 8 frames before deletion, extrapolating from velocity
   and flagging `stale`. EMA position alpha 0.6. Velocity in normalized
   units/sec from timestamp deltas.

   Appearance tiebreak: per-track dominant-colour histogram with slow EMA,
   included in the assignment cost. When two candidates are within 15% cost,
   keep the incumbent and flag BOTH tracks `ambiguous` — verifiers will read
   this and return Unsure (§20 invariant 2).

   Produce TrackedObject per §4.

2. world/: StabilityDetector (stable when all confirmed tracks moved <0.02
   over 500 ms; also per-object). FrameQualityAnalyzer (every 5th frame:
   mean luminance + Laplacian-variance blur proxy on a downscaled grid,
   returning a user-facing reason string). SpatialRelations (distance,
   leftOf/rightOf/above/below, overlapRatio over the smaller area,
   pointInPolygon — all pure). WorldStateBuilder exposing
   StateFlow<WorldState>.

JVM tests on synthetic sequences: steady state, linear motion, 4-frame
occlusion, two objects crossing (both must be flagged ambiguous), an object
appearing, an object leaving frame.
```

## S3.5 · Affordances ⭐ — **Claude Opus 4.1**
```
Implement §6 — the core idea of the product. Follow it exactly.

1. world/Affordances.kt: Affordance and SceneCapability per §4, and
   AffordanceEngine.derive(world). Use the §6 thresholds and the FURNITURE /
   CONTAINER / NEVER_MOVE sets as editable top-level constants.

   CRITICAL: every flag must have a geometric justification (area,
   stability) so the system still works when labels are unreliable. Never
   let a flag depend on a label alone where area or stability can decide it.

2. SceneCapability.from(world, affordances):
   - spread = mean pairwise normalized centroid distance
   - the three player-derived terms per §6.2:
       poseVariety   = distinct pose-feature clusters this session, /5, clamped
       motionRange   = 95th-percentile landmark displacement over 3 s, normalized
       frameCoverage = torso box area / frame area, /0.25, clamped
     All three are 0 when playerCount == 0.
   - richness per §6.2 with BOTH renormalisation branches:
       BRANCH A — playerCount == 0: redistribute the 0.15 player weight
         (+0.10 movable, +0.05 colours) so an object-only scene still reaches
         high richness. Essential because pose is P1 and may be cut.
       BRANCH B — movableCount == 0 && playerCount >= 1: redistribute the
         0.30 movable + 0.15 container weight into the player-derived terms
         using the exact weights in §6.2. Essential because without it a
         human-only scene is capped at ~0.20 richness and can NEVER reach
         the HARD tier, making G4's 12° tolerance unreachable.
     Both branches are mandatory. They are symmetric mirrors of each other.
   - the three §3.5 capability flags:
       semanticLabelsAvailable: false when the mean confidence of nameable
         objects drops below 0.5, or when the dev force-track-only toggle is on
       trackOnlyMode: the inverse
       planarSurfaceAvailable: false for now (metric calibration is deferred,
         §24) — wire the flag, do not implement calibration

3. Wire into WorldStateBuilder. Budget under 2 ms/frame.

4. Dev overlay printing live SceneCapability with EACH richness term shown
   separately, WHICH BRANCH is active (A, B, or neither), the three
   player-derived terms, and the three capability flags — so I can watch them
   change as I add/remove objects, step into frame, and toggle track-only.

JVM tests for all eight affordance flags PLUS:
 - chair (landmark, not movable); cup (handheld AND container)
 - two identical blue cups → both distinct=false
 - empty scene → richness ≈ 0
 - richness monotonically rising as objects are added
 - ⚠ BRANCH A: rich object-only scene, playerCount==0 → richness > 0.85
 - ⚠ BRANCH B: movableCount==0, one active player with good poseVariety,
   motionRange and frameCoverage → richness > 0.70
 - a still, distant, single-pose player → richness stays low (Branch B must
   reward engagement, not mere presence)
```

## S4 · Verification ⭐ — **GPT-5 (high)**; tests **Sonnet 4.5**
*Parallel with S1–S3. Pure JVM. No phone needed — do not deploy for this stage.*
```
Implement §13 S4. Pure, deterministic, fully unit tested, zero Android.

1. RuleId enum containing EXACTLY the §4.1 list, nothing else.
   MeasurementDomain enum per §12.

2. VerificationOutcome EXACTLY as defined in §4 — a SEALED INTERFACE with
   three variants carrying their own payloads: Pass(confidence, evidence),
   Fail(reason, evidence), Unsure(coachingHint). It is NOT an enum. Unsure is
   structurally distinct from Fail and must never be convertible to Pass by
   any downstream engine logic (§20 invariant 15).

3. StepEvaluation per §4:
   (outcome: VerificationOutcome, confidence: Float,
    evidence: List<Evidence>, coachingHint: String? = null)
   Evidence MUST carry its MeasurementDomain.

4. interface Verifier { rule; evaluate(step, spec, world, baseline): StepEvaluation }

5. TemporalGate implementing all seven §4.2 rules. Ring buffer over holdMs.
   Fires only when the window is fully populated in time AND satisfiedRatio
   >= 0.8 AND the latest sample is satisfied. Exposes 0..1 progress for the
   UI ring. Write these tests FIRST — especially: a single true frame in a
   stream of false frames must never pass.

6. VerifierRegistry: RuleId → Verifier, throwing at construction if any
   RuleId is unimplemented. Never silently skip.

7. Resolution policy in ONE place: missing actor → Unsure; FrameQuality POOR
   → Unsure; actor confidence <0.45 → Unsure; ambiguous track or player →
   Unsure; insufficient evidence → Unsure. Pass may only be emitted by
   deterministic verification after all required evidence and temporal
   conditions are satisfied. Fail may only be emitted when required evidence
   is confidently false.

8. Implement every rule in §4.1:
   - directional rules require separation to EXCEED the tolerance
   - OBJECT_VANISHED_IN_ZONE: last position inside zone + undetected N frames
     + container still present; Unsure (not Fail) if it exited a frame edge
   - NON_DEGENERATE_TRIANGLE: cross-product area, all three angles; pass only
     if area > minArea AND minAngle > threshold AND longest/shortest <
     maxRatio. Camera-space 2D only unless planarSurfaceAvailable (§7.1)
   - ARRANGEMENT_MATCH: subtract centroid, normalize by RMS radius, match by
     label/track, every per-object displacement under tolerance, worst
     offender reported in evidence. (Generator deferred per §24 — build the
     verifier anyway; it's a Q&A talking point.)
   - PoseMath: jointAngle, poseFeatureVector using ONLY joint angles (elbows,
     shoulders, hips, knees, torso lean), poseDistance weighted by the min
     visibility of the three constituent landmarks and EXCLUDING joints below
     0.5 visibility with weights renormalised, motionEnergy as a
     visibility-weighted EMA of landmark speed.
     Also expose a poseClusterId(featureVector) helper — S3.5's poseVariety
     term needs it to count distinct pose clusters.

Every verifier: explicit Pass + Fail + both boundary sides + missing-actor →
Unsure + insufficient-evidence → Unsure. Outcomes must be deterministic for
identical inputs. Plus: ARRANGEMENT_MATCH survives 2× scale + translation;
POSE_MATCH is identical at two camera distances; Evidence domain is
NORMALIZED everywhere while planarSurfaceAvailable is false; and a test
asserting Unsure can never be widened into Pass by any public API.
```

## S5 · Engine + registry + G0/G1 — **Claude Opus 4.1**
```
Implement §13 S5 using the SCORED selection model of §6 — not a boolean.

1. challenge/: ChallengeSpec, VerificationStep, ChallengeType, Tier, AgeBand,
   ActorRef (by label | by trackId | zone | player), Requirement and
   ChallengeGenerator exactly per §6.3 — INCLUDING needsNameable,
   needsPlanarSurface, and maxStepsForTier(tier): Int with a default of 1.
   SelectionMode enum. SafetyFilter per §11. DifficultyKnobs per §8.1 taking
   spread and stability.

2. ChallengeRegistry.select(world, cap, affordances, ctx, mode):
   - pre-filter on Requirement ints AND the §3.5 capability flags —
     needsNameable fails when semanticLabelsAvailable is false, minPlayers
     fails when playerCount is 0, each with an explicit reason string
   - feasibility → score = feasibility × tierFit × noveltyBonus × ageGate
   - RECOMMENDED → argmax with a stable tie-break by generator ID
     (deterministic — §20 invariant 11)
   - OPEN → weighted-random among score > 0
   - PINNED → forced generator, still fully verified
   - compute stepBudget = winner.maxStepsForTier(effectiveTier), clamped to 1
     for TODDLER and EARLY age bands (§20 invariant 17), and pass it into
     generate()
   - returns SelectionResult(spec, rankedCandidates, mode) where EVERY zero
     carries a specific reason. Expose as data, not just a log line — the UI
     renders it.

3. G0LastResortGenerator per §6.4. CRITICAL: Requirement remains EMPTY — no
   zone, no semantic label, no player. maxStepsForTier returns 1 at every
   tier. Its generate() chooses the safest available variant: if a confirmed
   player exists it may return the player variant; otherwise, if a suitable
   tracked object exists it returns the object variant. The generator itself
   must never require a player, zone, semantic label, or other capability in
   order to remain selectable. Object variant "Bring the glowing object close
   to the camera" → OBJECT_PRESENT with min area 6%. Player variant "Wave at
   me!" → LIMB_RAISED + MOTION_ABOVE.
   Then a property-style test throwing many random synthetic WorldStates at
   the registry asserting it NEVER returns null (§20 invariant 13), plus a
   focused test proving G0 both SELECTS and GENERATES successfully whenever
   at least one usable object or player exists.

4. engine/: GameStateMachine with legal transitions declared as data, illegal
   ones logged loudly, plus a unit test walking the table. MissionRunner —
   per-step TemporalGates, advances only when a gate fires, honours
   mustFollowPreviousStep, exposes current step index and per-step progress.
   It must already handle 2-step ordered missions even though G1 ships
   single-step at first; write a unit test with a synthetic 2-step spec
   proving ordering is enforced and out-of-order completion does not advance.
   ScoreEngine per §9 INCLUDING stepBonus (+40 per completed step beyond the
   first, partial credit on incomplete missions), pure and tested.
   GameSession. GameViewModel (plain ViewModel, no Hilt) exposing ONE
   StateFlow<GameUiState>. Verification on Dispatchers.Default, never on the
   analyzer thread.

5. G1 MoveNear per §7 + §7.1: Requirement(minMovable=2), the §7 feasibility
   formula, maxStepsForTier returning 2 at HARD and 1 otherwise. For now
   generate() may emit only the single-step form — S9 adds the chained form —
   but it must accept and respect the stepBudget parameter. Picks two movable
   stable objects separated by >1.8× the target threshold, prefers
   distinct+nameable, and AUTOMATICALLY falls back to highlight-colour
   phrasing ("the glowing blue one") when not nameable or when trackOnlyMode
   is true — automatic, not a separate mode.

6. Minimal GameScreen: preview + overlay + instruction + progress ring +
   score/streak. Implement timeout, retry (max 2), and the Unsure path
   (coaches, consumes no retry, no difficulty change).

Manual test script: 10 steps including two attempts to pass incorrectly, two
where I remove an object mid-scan, one with force-track-only ON, one proving
RECOMMENDED gives an identical pick 10 times on an unchanged scene, and one
confirming G0 generates from a single unlabelled object.
```

## S6 · UI & feel — **Claude Sonnet 4.5**
```
Implement §13 S6. Presentation only — do not touch engine/, verify/, world/
or perception/.

ui/game/: BriefingOverlay (instruction animates in, actor objects pulse in
the live view, 3-2-1 countdown). ProgressRing (circular, driven by
TemporalGate progress, must visibly fill while the condition is held — the
single most persuasive element for judges). HudBar (score, streak flame,
challenge counter, time bar only when a limit exists). SuccessBurst
(particles + "PERFECT!" + score counting up).

MULTI-STEP SUPPORT (§8.1b): when a spec has more than one step, show
"Step 1 of 2" prominently, render a separate progress indicator per step,
mark completed steps with a persistent tick, and make it visually obvious
that step 2 only becomes active after step 1 fires. This is how the
structural difficulty axis is communicated in 5 seconds on stage — make it
legible from a metre away.

EvidencePanel — CRITICAL per §12 and §20 invariant 6: render measured vs
required IN THE EVIDENCE'S OWN MeasurementDomain. While
planarSurfaceAvailable is false the wording must be normalized/relative, e.g.
"Bottle to Book: 0.18 — needed under 0.30 ✓" or "Closer than the target ✓".
Only render centimetres when domain == METRIC. Never fabricate a unit. For
multi-step missions, show one evidence row per step.

CoachingToast for Unsure in a distinctly calm colour that never reads as
failure.

SoundManager via SoundPool: success, step (distinct from success — fires on
intermediate step completion), fail, countdown, start, with a Settings mute
toggle.

Then: HomeScreen. ModeSelectScreen (mode Objects/Body/Mixed with Body
disabled and labelled "needs a person" when pose is off, age band per §10,
player count 1–2, big visual cards usable by a parent in 3 seconds).
CalibrationScreen (coaches framing, live object/people counts, lighting
indicator, READY enabled only when usable). ResultScreen (score, best streak,
per-challenge list with measurements, domains and step counts, Play Again /
Home).

Readable at arm's length, one-handed, portrait. No allocation in DrawScope.
```

## S7 · Zones + G2/G3 + CapabilityCard ⭐ — **GPT-5 (high)**; card **Sonnet 4.5**
```
Implement §13 S7.

1. perception/ZoneDetector: no OpenCV. Downscale to ~160x120, HSV, threshold
   per target colour with saturation/value minimums to reject shadows,
   connected components, keep area between 1.5% and 40%, approximate each to
   a quad from extreme points, emit normalized polygons. Confirm after 5
   consecutive frames, survive 15 absent, EMA the vertices so the render
   doesn't shimmer. Run at most every 3rd frame. Render with ZoneShape.
   Tell me which physical materials work best and which to avoid.

2. G2 DropZone per §7 and §7.1, maxStepsForTier returning 2 at HARD.
   TWO CRITICAL GUARDS:
   (a) the source trackId must NEVER equal the target trackId — a cup is both
       handheld and container, and without this it can ask me to put the cup
       inside itself
   (b) prefer a detected zone over an object-container when both exist
   Also never target an object already inside. Steps: POINT_IN_ZONE on the
   centroid AND OVERLAP_RATIO_ABOVE 0.5.

3. G3 FindColor per §7: Requirement(minDistinctColors=2), maxStepsForTier
   returning 2 at HARD, picks a colour that IS present but NOT on the largest
   object in frame.

4. SceneCapabilityCard per §15 — the most important UI element for judging.
   Shown once between CALIBRATING and the first BRIEFING, under 2 seconds,
   tap to skip, counts animating 0→value over ~600 ms with a stagger. Omit
   zero rows.
   ⚠ The denominator MUST be read from the live registry size, never
   hard-coded (§20 invariant 14). "K of N games possible here" must be
   prominent.
   On a human-only scene the card must show player-derived rows (people,
   movement) rather than object rows, so it still reads sensibly when the
   table is empty.
   Sparse branch at richness < 0.3 with the §15 wording — confident, not
   apologetic.
   Long-press reveals the ranked list with scores to 2dp and every
   zero-reason, in a clean monospace table suitable for a mirrored screen.
   Re-show a compact one-line version whenever richness shifts by >0.2.

5. Dev screen listing every generator with its live feasibility score AND its
   maxStepsForTier at the current tier, refreshing as I move objects. I'll use
   this constantly for tuning.
```

## S8 · Pose + identity + G4/G5 — **GPT-5-Codex** (math: **GPT-5 high**) — *cuttable*
```
Implement §13 S8 and §5. This stage is P1 — before you start, confirm the
object-only path (G0,G1,G2,G3) still runs with pose fully disabled, and keep
it that way throughout (§20 invariant 12).

1. interface PoseDetectorSource + MediaPipePoseDetector
   (pose_landmarker_lite.task from bundled assets, LIVE_STREAM, numPoses
   1..2, GPU with CPU fallback). MoveNetPoseDetector as a stub behind the
   same interface — I want the seam now so I can swap in 10 minutes.

2. PerceptionScheduler per §12: enables the object detector and pose detector
   per frame based on what the ACTIVE spec references. Both only when the
   challenge needs both. Log the active perceiver set whenever it changes.

3. Feed S3.5's player-derived capability terms: poseVariety (using S4's
   poseClusterId), motionRange and frameCoverage must now compute real values
   rather than zeros. Verify in the dev overlay that §6.2 Branch B activates
   on an object-free scene and that richness climbs above 0.70 when I move
   through several distinct poses.

4. PlayerIdentity per §5 exactly: anchor at round start, hold with 1.6×/5-frame
   hysteresis, ambiguity at 0.08 separation resolved by colour band or frozen
   with `ambiguous` set so player rules return Unsure, re-anchor every round.
   PlayerRegistry in engine/ tracking registered players, the ACTIVE player,
   and turn rotation. Support 1 primary and 2 turn-based; do not build 3–4.

5. UI: coloured halo + large "P1"/"P2" above each player. Multiplayer
   onboarding during CALIBRATING with TTS, anchoring once N stable poses are
   present, with a visible confirmation.

6. G4 StatueMatch and G5 RedLightGreenLight per §7.
   G4: 8 target poses as joint-angle vectors (T-pose, both hands up, one hand
   up, star jump, hands on hips, arms crossed, one leg up, reaching left),
   silhouette beside the live view, per-joint green indicators.
   maxStepsForTier returns 2 at HARD — two poses in sequence, the second only
   counted after the first fires.
   G5: feasibility is 0.70 + 0.15·min(players,2)/2 per §7 — this v3.4 value
   is deliberate, so that a single-player human-only scene doesn't collapse to
   Statue Match every time under argmax. Randomized green/red windows,
   per-player MOTION_BELOW through red, kids-safe — no elimination, "Ooh, you
   wiggled!" and lost points only, with a per-player motion meter.
   maxStepsForTier stays 1.

Report measured fps with 0, 1 and 2 people. Unit tests for synthetic pose
sequences: stable pair, crossing, leave-and-return, background walker. Plus a
test confirming that on a human-only scene both G4 and G5 score above zero
and are within 0.15 of each other at single-player.
```

## S9 · Difficulty + G6/G7 + structural axis — **Claude Opus 4.1**
```
Implement §13 S9.

1. DifficultyDirector per §8: continuous rating with the stated adjustments,
   ±5 hysteresis at 33/66, Unsure NEVER moving the rating, and
   effectiveTier = min(skillTier, sceneTier(richness), ageCap) with the §8.1
   scene-scaled knob interpolation. Expose a presentable explanation string
   like "skill=HARD but richness 0.41 → playing MEDIUM" in the dev overlay —
   a direct answer to a likely judge question.

2. ⭐ STRUCTURAL AXIS (§8.1b) — the second difficulty dimension.
   Now that stepBudget flows through the registry, implement the chained form
   for the generators that opt in:
   - G1 at HARD: "Move the bottle next to the book, THEN move the cup away
     from both." Step 2 is DISTANCE_GREATER_THAN with
     mustFollowPreviousStep = true.
   - G2 at HARD: two different objects into two different zones, in order.
   - G3 at HARD: "Show me something red, then something blue."
   - G4 at HARD (if S8 shipped): two poses in sequence, each held.
   Rules: never more than 2 steps in this build; TODDLER and EARLY bands are
   hard-capped to 1 step regardless of tier (§20 invariant 17); the spec's
   step count must never exceed the generator's maxStepsForTier.

3. PLAYER_HOLDS_OBJECT (either wrist within threshold of the object centroid,
   sustained 400 ms, Unsure on low wrist visibility) and PLAYER_NEAR_OBJECT.

4. G6 FetchRace per §7 (skip if S8 was cut): turn-based, names the active
   player, and chooses the requested attribute from what the scene ACTUALLY
   contains — never ask for an attribute no present object satisfies. Ordered
   steps per §7. maxStepsForTier stays 1 — it is already multi-step by nature.
   Live UI showing which object the active player is holding.

5. G7 TriangleBuild per §7 and §7.1: explicitly 2D camera-space unless
   planarSurfaceAvailable. Rejects collinear points, tiny-area triangles and
   points outside the playable surface. maxStepsForTier stays 1 — already
   geometrically hard. LIVE OVERLAY drawing the triangle between the three
   centroids, each edge green when its constraint holds and amber when not,
   with computed area and smallest angle printed. Demo centrepiece — make it
   beautiful.

Tests:
 - a sparse scene caps difficulty despite a high rating
 - rising richness unlocks a higher tier
 - alternating pass/fail never oscillates across a boundary
 - with playerCount==0 a rich object-only table still reaches HARD (§6.2 A)
 - ⚠ with movableCount==0 and an engaged player, the scene reaches MEDIUM or
   HARD (§6.2 B) — this is the v3.4 human-only fix
 - ⚠ at HARD, G1 emits exactly 2 steps and MissionRunner rejects out-of-order
   completion
 - ⚠ in TODDLER and EARLY, step count is 1 across a sweep of tiers
 - stepBonus is awarded per completed step, with partial credit
```

## S10 · Toddler + TTS — **Claude Sonnet 4.5**
```
Implement §13 S10 and §10.

1. ai/Narrator using Android TextToSpeech — fully offline, no external model.
   Prefer en-IN then en-US, log available offline voices at startup,
   speak(text, priority) with FLUSH for new instructions and ADD for
   encouragement, slower rate + higher pitch in toddler mode, persisted mute
   toggle, fire-and-forget (nothing in the game loop ever awaits speech),
   proper shutdown in onCleared. Speak the instruction at BRIEFING, the
   countdown, one of 8 varied congratulations at CELEBRATING, and escalating
   hints. Surface a clear Settings warning with a button to system TTS
   settings if no offline voice data is installed, plus a "Test voice" button.

2. ToddlerPolicy enforcing all TEN §10 hard constraints in the ENGINE, not
   the theme. Three that are easy to get wrong:
   - Fail must render IDENTICALLY to Unsure
   - ⚠ effective holdMs is capped at 2000 ms AFTER the ×1.7 toddler multiplier
     and the §8.1 stability multiplier are applied — otherwise a jittery scene
     compounds to ~2.4 s, which no toddler can hold
   - ⚠ stepBudget is hard-capped at 1 regardless of tier (§20 invariant 17)
3. Adult-supervision notice on entering toddler mode, acknowledged before
   play, persisted so it shows once per install (§21 SAFETY gate).
4. Toddler generators: FindColor (easy variant), ShowMeTheObject ("Where is
   the teddy?"), TouchTheBigOne (two objects, larger box area is correct,
   verified by which is brought closest to the camera). All return 1 from
   maxStepsForTier at every tier. Plus G0, always available.
5. ToddlerTheme: much larger type, very high contrast, rounded shapes, mascot
   reactions, huge animated stars, NO numeric score anywhere.
6. Age-band variants for 4-6 / 6-8 / 8+ per §10 altering vocabulary, timer
   presence, unlocked tiers AND max step count (1 for EARLY, 2 for MID and
   OLDER), with pose-dependent games hidden when pose is off. Mid-session
   switching must cancel cleanly and regenerate under the new policy.

Tests: in toddler mode no spec ever carries a time limit, no Fail ever
reaches the UI, effective holdMs never exceeds 2000 ms across a sweep of
stability values, and step count is always 1 across a sweep of tiers and
richness values in TODDLER and EARLY.
```

## S11 · SLM — **off critical path, 60-min hard cap** — **Opus 4.1** + **GPT-5-Codex**
```
Implement §13 S11. Completely removable without affecting gameplay.

⚠ FIRST, before writing any model code, implement and run the AI-OFF GATE:
prove that with the runtime model disabled, scan → select → play → verify →
score completes normally. If that fails, stop — S11 cannot proceed.

1. interface LanguageModel { generate(prompt, maxTokens); isAvailable;
   close() }. MockLanguageModel returning canned valid JSON — build and test
   everything against this FIRST.
2. GemmaModel via tasks-genai LlmInference. TIER-B ONLY (§1.2): loads from
   /sdcard/realplay/models/. isAvailable=false with a logged reason if the
   file is missing or init throws — never crash, never block startup. Lazy
   loading only (§20 invariant 8). Own dispatcher. Hard 3 s withTimeoutOrNull.
3. PromptBuilder: a compact WorldState summary (labels, colours, 3x3 grid
   cells, player count, zones) + SceneCapability (including which richness
   branch is active) + the ranked feasible candidates + tier + age band + the
   current stepBudget. Two few-shot examples. Ask for a single JSON object.
4. CRITICAL — the model RE-RANKS and REWORDS. It may reorder the already-
   feasible candidates and rewrite the instruction. It may NOT introduce a
   generator that scored zero, invent a rule, add steps beyond the budget, or
   touch verification.
5. SchemaValidator rejecting unless: type is a ChallengeType that was in the
   feasible list; every rule is a RuleId enum member; every actor resolves to
   something in WorldState; every parameter is inside hard-coded safe ranges;
   the instruction is non-empty, <120 chars and free of §11 denylist verbs;
   no rule requires a capability flag that is currently false (§4.1); and the
   step count does not exceed the generator's maxStepsForTier. On failure log
   the reason and return null. Add a JSON repair pass extracting the first
   {...} block and retrying parse once.
6. AiChallengeSource: try model → validate → on null fall back to the
   deterministic registry. Invoked only from GENERATING, off the camera
   thread. Generate the NEXT challenge speculatively during CELEBRATING so
   latency hides behind the success animation. Speculative generation must use
   its own dispatcher and must never block or interfere with CameraX analysis.
   No LLM inference may execute concurrently with object + pose perception
   when both are required by the active challenge.

Settings toggle, default OFF. Confirm the game is functionally identical with
it disabled.
```

## S12 · Hardening + freeze — **Claude Opus 4.1**
```
Implement §13 S12, §16 and §17.

1. DemoController with the three §16 modes, passing SelectionMode into the
   registry. RECOMMENDED = argmax + proven-only + noveltyBonus off, and must
   be verifiably deterministic on an unchanged scene. NO forced ordering
   anywhere (§20 invariant 11).

2. Presenter controls: long-press bottom-left 600 ms reveals a sheet with
   mode switch, PINNED picker, SKIP, REPEAT, a force-tier control (so I can
   demonstrate the 2-step HARD mission on demand), and the live ranked
   candidate list. Invisible to an audience, reachable in under 2 seconds.

3. Sparse self-narration at richness < 0.3 per §15, spoken via TTS.

4. SessionStore — SessionSnapshot's owner (§4). Persist sessionId, gameState,
   score, streak, difficultyRating, challengeHistory, demoMode and
   demoPosition to DataStore or SharedPreferences. Restore on launch.
   Best-effort recovery ONLY — never evidence for PASS/FAIL (§20 inv. 10).

5. Robustness: supervisor scope around the game loop so a single challenge
   failure is caught, logged with a full WorldState dump, and recovers to
   SCANNING. Global uncaught-exception handler returning to Home with the
   session intact.

6. ABSOLUTELY FORBIDDEN in every mode including PINNED: faked detection,
   faked verification, hard-coded PASS. A judge will be invited to
   deliberately fail a challenge and it must genuinely fail.

7. Thermal management per §12: poll every 10 s; MODERATE → halve detection
   cadence, pose off unless required; SEVERE → drop resolution, LLM off,
   reduce overlay; idle in menus.

8. Then act as a senior Android reviewer and audit for: ImageProxy leaks on
   exception paths; any suspend reachable from the analyzer thread;
   allocation in DrawScope or the per-frame path; MediaPipe lifecycle; ANY
   path that could pass from a single frame; any place Unsure could be
   widened into Pass; unchecked indices in verifiers; config change and
   process death; leftover hard-coded pixel coordinates; any hard-coded
   game-count denominator; any spec able to exceed its step budget;
   non-lifecycle-aware StateFlow collection; any path running both detectors
   plus the LLM concurrently. Output CRITICAL/HIGH/MEDIUM with file and line,
   then fix CRITICAL and HIGH only.

9. Finally, walk the §21 Architecture Freeze Gate item by item and report
   PASS/FAIL for each with evidence — including both richness branches and
   both difficulty axes. Then README per §23 + the §3 diagram + the games
   table with maxStepsForTier + model list with sources and licences + the
   §24 deferred list. Confirm no model binaries are committed. Give me the
   signed release APK commands.
```

---

# PART B — Recovery prompts

## Build
**R1 Gradle fails** — `Gradle sync fails: [ERROR]. Versions are pinned in BUILD_MATRIX.md, possibly no network. Root cause + minimum fix. If it's a version conflict give me exact catalog pins and update BUILD_MATRIX. Don't restructure. If it needs a download, give me the offline workaround.`

**R2 Crash at launch** — `Crashes on launch: [TRACE]. Root cause + minimal fix, then a guard so this class of failure shows a readable error screen instead of crashing — I can't afford a crash on stage.`

**R3 adb can't see the phone** — `adb shows no device / unauthorized for an iQOO 15 on OriginOS 6. Step-by-step: developer options, USB debugging, USB install, USB debugging (security settings), the authorization dialog, OEM USB modes. Plus wireless adb as backup.`

## Camera
**R4 Overlay misaligned** ⚠️ *most likely bug* — `Calibration rect is [offset X / offset Y / mirrored / rotated 90 / too large / too small / stretched]. Analysis [W]x[H], rotation [R], [FILL_CENTER/FIT_CENTER], [back/front], view [VW]x[VH]. Rewrite CoordinateMapper from first principles, deriving each step in comments. Then a JVM test asserting centre→centre and all four unit-square corners for all four rotations.`

**R5 Preview black/frozen** — `Preview is [black/frozen/green]: [LOGCAT]. Check missing imageProxy.close(), wrong lifecycle owner, binding before the surface is ready, rejected use-case combo, permission granted but provider not rebound. Corrected CameraController with unbindAll-then-bind and full bind logging.`

**R6 FPS too low** — `Analyzer at [N] fps; §12 says ≥10 acceptable, target 15. Optimise the pipeline, no game-logic changes. Check resolution, backpressure, YUV→RGB efficiency, per-frame Bitmap allocation, shared executor, overlay recomposition. Give me the fewest-copy path from CameraX into MediaPipe.`

## Detection
**R7 Props not detected** ⚠️ — `[OBJECT] is detected [never / below 0.3 / flickering / as the wrong label]. In order, reporting each: (1) which model tier and path loaded; (2) orientation and colour-channel order — a wrong channel order silently destroys accuracy; (3) drop threshold to 0.25 and log ALL raw detections; (4) which of my props are actually in EfficientDet-Lite0's COCO label set. Then, if labels are genuinely unreliable, flip semanticLabelsAvailable=false so §3.5 trackOnlyMode engages — G1 should automatically switch to highlight-colour phrasing and semantic generators should drop out with an explicit reason. Confirm that path works end to end.`

**R8 Load the fine-tuned model** — `I have realplay_props.tflite from Model Maker with classes [LIST]. Confirm the Tier-B path takes precedence over bundled assets, handle the different label map, auto-fall back to bundled on load failure with a visible log. Give me the adb push command and how to verify at runtime which tier is active.`

**R9 Boxes flicker** — `Boxes jitter when nothing moves. Fix in the tracker, not the detector: promotion to 4 frames, coast to 12, EMA on box CORNERS (alpha 0.5), confidence hysteresis 0.45 in / 0.30 out, NMS at IoU 0.5 for same-label duplicates. Show the diff, add hysteresis tests.`

## World / affordances
**R10 IDs swapping** — `Two similar objects swap track IDs when close. Strengthen the appearance-histogram weight, widen the incumbent-preference band, and confirm BOTH tracks get ambiguous=true so verifiers return Unsure rather than a wrong answer (§20 invariant 2). Add a same-label crossing test.`

**R11 Never stable** — `StabilityDetector never reports STABLE so nothing generates. Log per-object movement magnitudes over the window. Make it adaptive: measure the noise floor over the first 2 s of a static scene and set the threshold to 2.5× that. Add a 3-second hard timeout that proceeds with a degraded-stability flag.`

**R12 Everything scores zero** — `Registry returns only G0 despite several objects present. Log every tracked object with all eight affordance flags and why each was set, plus the three §3.5 capability flags. Most likely the §6 area thresholds are wrong for my camera's field of view, or stability never reaches threshold so landmark and the stability term stay zero. Make the area thresholds Settings-configurable and tell me how to calibrate in 60 seconds on the device.`

**R13 Richness won't rise (objects)** — `richness stays low however many objects I add. Print each §6.2 term separately in the dev overlay and confirm which branch is active. Confirm BRANCH A (playerCount==0) is firing — if it isn't, an object-only scene is capped at 0.85 and Tier 3 is unreachable. Then tell me which physical change raises it fastest.`

**R14 Registry picks a bad game** — `Registry chose [GAME] but the scene couldn't support it well: [SYMPTOM]. Dump the ranked list with scores and reasons. Identify whether the bug is (a) Requirement too permissive, (b) feasibility over-scoring, (c) a wrong affordance flag, or (d) a capability flag not enforced in the pre-filter. Fix only that one. Add a regression test with that exact synthetic scene.`

**R15 G0 fails to generate** ⚠️ *breaks the finale* — `G0 was selected but failed to produce a spec. G0 MUST have an empty Requirement — no zone, no label, no player (§6.4, §20 invariant 13). Audit its generate() for any hidden dependency on a zone, a nameable label, or a player. Then extend the property test to assert that G0 both SELECTS AND GENERATES successfully across every synthetic scene containing at least one object or one player.`

**R39 Human-only scene stuck on EASY** ⚠️ *v3.4* — `An object-free scene with one active player never rises above EASY tier. This means §6.2 BRANCH B is not firing. Verify in order: (1) is movableCount actually 0, or are background objects being counted as movable? (2) is the branch condition movableCount==0 && playerCount>=1 evaluated before the base formula? (3) are poseVariety, motionRange and frameCoverage returning real values or zeros — S8 must feed them. (4) print every Branch B term separately. The target is richness > 0.70 for an engaged, well-framed player, which puts sceneTier at MEDIUM or HARD. Then tell me what I should physically do — distance from camera, how many distinct poses — to raise each term.`

## Verification
**R16 Passes when it shouldn't** — `[GAME] passes without the player completing it. Repro: [WHAT I DID]. Audit the whole path: maths, gate config, actor resolution, confidence gating. Specifically: can the gate fire on a partially-filled window; are missing actors silently treated as satisfied; is a STALE coasted track being used as a live detection; can an Unsure be widened to Pass anywhere. Add a test reproducing this false positive, then fix.`

**R17 Never passes** — `[GAME] won't pass when I clearly complete it. Add a verbose debug overlay showing live: every actor's resolved track and confidence, raw measured value and its domain, required threshold, comparator result, gate fill ratio, frame-quality verdict, and for multi-step specs the current step index. Change NO thresholds yet — I need to see what's blocking. Then propose the specific relaxation.`

**R18 Flickers pass/fail** — `Result oscillates. Raise holdMs to [N]; add asymmetric hysteresis (satisfied at T, unsatisfied only at 1.25T); median-filter the last 5 measurements; ignore POOR-quality frames entirely rather than counting them unsatisfied. Add a noisy-input stability test.`

**R19 Pose match unreliable** — `POSE_MATCH is [too strict / too loose / depends on my distance / depends on where I stand]. Confirm poseFeatureVector uses ONLY joint angles, weights by min visibility of the three constituent landmarks, and fully excludes joints below 0.5 visibility with weights renormalised. Add a per-joint angular-error debug overlay. Then give me easy/medium/hard tolerances.`

**R20 Evidence shows wrong units** — `EvidencePanel is printing centimetres but planarSurfaceAvailable is false — this violates §12 and §20 invariant 6 and will get us caught by a judge. Audit every Evidence construction site, confirm each carries the correct MeasurementDomain, and make the UI switch wording on domain rather than assuming. Add a test that fails if any NORMALIZED evidence renders a metric unit.`

## Engine
**R21 Stuck in a state** — `Stuck in [STATE]. Add a debug overlay showing current state, time in state, and the exact condition it's waiting on. Then a watchdog: any state other than ACTIVE/IDLE persisting past 8 s dumps a full diagnostic and force-transitions to a safe recovery state. The demo must never freeze.`

**R22 Crash mid-challenge** — `Crash during gameplay: [TRACE]. Fix the root cause, then wrap each challenge lifecycle in a try/catch logging the full WorldState at failure, abandoning only that challenge and returning cleanly to SCANNING. One bad challenge must never end the session.`

**R23 RECOMMENDED isn't deterministic** — `RECOMMENDED gave different picks on an unchanged scene, violating §20 invariant 11. Confirm it uses argmax with a stable tie-break by generator ID, that noveltyBonus is fully disabled, and that no weighted-random path is reachable in that mode. Add a test running select() 50 times on one frozen WorldState and asserting a single distinct result.`

**R40 Multi-step mission misbehaving** ⚠️ *v3.4* — `A 2-step HARD mission is [advancing out of order / never advancing past step 1 / awarding full score on partial completion / appearing in toddler mode]. Audit MissionRunner: confirm mustFollowPreviousStep is honoured, that each step has its own TemporalGate, that step 2's gate is not fed samples until step 1 fires, that stepBonus is per completed step with partial credit, and that stepBudget is clamped to 1 for TODDLER and EARLY (§20 invariant 17). If this can't be stabilised in 20 minutes, set every maxStepsForTier to 1 and ship single-step only — the numeric axis alone still works.`

## Models
**R24 Model not found** — `A model fails to load. Implement the §1.2 two-tier resolution with every attempted path logged: Tier-B /sdcard first, then bundled assets. A missing Tier-B file must be silent and normal. A missing Tier-A asset is a build error — surface it loudly at startup and fail the build. Add a Settings section listing every expected model, which tier it came from, and its size.`

**R25 GPU delegate broken** — `MediaPipe crashes or returns garbage on the GPU delegate. Auto-fallback: try GPU; on any exception OR if the first 10 inferences yield zero detections, tear down and recreate on CPU, logging loudly. Persist the working choice. Add a manual Settings override.`

**R26 LLM slow or broken** — `LLM is [slow at N s / invalid JSON / won't load / proposing over-budget step counts]. Enforce the 3 s timeout with silent fallback, confirm speculative generation during CELEBRATING hides the latency and runs on its own dispatcher, add the JSON repair pass, confirm the validator rejects step counts above maxStepsForTier, and give me a one-tap Settings kill switch. Then re-run the AI-OFF gate and confirm the game is functionally identical with it fully disabled.`

**R27 TTS silent** — `TextToSpeech says nothing. Diagnose: init status, locale availability and whether offline voice data is installed, stream volume, utterances flushed by rapid successive calls, premature shutdown by a lifecycle callback. Add a "Test voice" button and an on-screen warning with a button opening system TTS settings.`

## Performance
**R28 Overheating** — `Device heats and fps degrades after minutes. Implement the §12 thermal policy in full and show me the PerceptionScheduler enforcing "never both detectors unless the active spec references both an object and a player", and never an LLM call concurrent with both. Log every transition.`

**R29 Memory growth** — `Memory climbs during play. Audit ImageProxy/MPImage lifecycle, Bitmap reuse, unbounded track history, unbounded pose-cluster history (poseVariety must use a bounded set), unbounded session log, detectors recreated without closing, coroutine scopes outliving their screen. Fix and add bounded ring buffers.`

## UI
**R30 Janky** — `UI stutters. Compose only: hoist state so the smallest composables recompose, move overlay data into one immutable per-frame snapshot, eliminate every DrawScope allocation, derivedStateOf for computed values, and check I'm not recomposing the whole tree per frame from a StateFlow. Before/after on the worst offenders.`

**R31 Looks unfinished** — `[N] minutes left and the UI looks rough. No new features. Highest-visual-impact only, in priority order: consistent spacing and type scale, one accent colour used consistently, smooth instruction and result transitions, a satisfying success animation, legible "Step 1 of 2" for multi-step, readable overlay contrast. Small focused diffs I can apply and build in under [N] minutes.`

## Git
**R32 Conflicts** — `Conflicts in [FILES]. I need a working APK, not clean history. Fastest safe resolution. If non-trivial, tell me which side to take wholesale and what I lose. No interactive rebase.`

**R33 Rollback** — `Current build is broken, last good tag [TAG]. Exact commands to return to it while preserving current work on a side branch, then rebuild and reinstall. Speed over elegance.`

## Demo day
**R34 Hostile environment** — `Venue has [dim light / spotlights / clutter / reflective table / people walking behind]. Prioritised list doable in under 10 minutes, covering physical setup (props, surface, phone placement, lighting) and in-app Settings — thresholds, confidence floors, which generators to favour or disable. Settings only, no rebuild.`

**R35 Finale panic — OPEN mode failing on an unknown table** — `About to demo OPEN mode on an unfamiliar surface and it's selecting poorly or stalling. In under 10 minutes: safest threshold loosening, confirmation that G0 generates with one unlabelled object and no zone, confirmation the object-only path works with pose off, confirmation the human-only path works if the surface is bare, and the exact presenter steps to drop to PINNED mid-demo unnoticed. Settings only, no rebuild.`

**R36 One game unreliable, 30 min to pitch** — `[GAME] fails ~[N]% of the time. Choose between (a) wider tolerances + longer hold, (b) restrict to a controlled prop setup, (c) set proven=false so RECOMMENDED stops selecting it, (d) set its maxStepsForTier to 1 if the failures are multi-step ordering. Recommend one, implement it as a minimal change, no refactor. Then give me the updated §17 running order.`

**R37 Pose must be cut, 2 hours left** — `S8 isn't working and I need to cut pose entirely. Give me the minimal change set: disable pose in PerceptionScheduler, remove G4/G5/G6 from the registry, confirm §6.2 BRANCH A keeps richness reaching HARD on an object-only table, hide Body mode in ModeSelect, update the capability-card denominator from the live registry, and verify the §22 core acceptance list still passes including the 2-step HARD mission on G1. Then give me the revised §17 demo script and the revised §18 empty-room answer.`

**R38 Pre-submission** — `30 minutes to deadline. Numbered checklist with the exact command or click path for each: release APK built and on both phones, backed up in 4 places, repo fully pushed, README + architecture committed, Tier-A models in the APK but not in Git, no secrets, one more airplane-mode rehearsal, Office Kit mirroring confirmed, §21 freeze gate results recorded. Be terse.`

---

# Pre-development freeze checklist

Before starting S0, verify these once. Do not begin implementation until all are confirmed:

1. `REALPLAY_ARCHITECTURE_v3.4_FINAL.md` is the only architecture attachment.
2. The architecture version and this playbook version are recorded in the repository README.
3. `docs/BUILD_MATRIX.md` exists and pins every dependency/plugin/tool version.
4. Tier-A model acquisition is reproducible, models are not committed to Git, and the build fails clearly when a required model is absent.
5. The target iQOO 15 is available for S0 runtime validation.
6. Android SDK/JDK/Gradle requirements are compatible with BUILD_MATRIX.
7. The repository is clean or the starting commit is explicitly recorded.
8. Every stage will use a fresh coding-agent chat with the architecture attached.
9. No agent is allowed to resolve architecture conflicts silently.
10. The first release checkpoint is S5, with additional checkpoints at S9 and S12.
11. Both richness branches (§6.2 A and B) are understood — an object-only scene and a human-only scene must each be able to reach their natural tier.
12. Both difficulty axes (§8.1 numeric, §8.1b structural) are understood, and the fallback is to set every `maxStepsForTier` to 1 if multi-step misbehaves.

---

# Model map

| Stage | Primary | Fallback |
|---|---|---|
| S0 Foundation | **GPT-5-Codex** | Sonnet 4.5 |
| S1 Camera | **GPT-5-Codex** | Opus 4.1 |
| S1 CoordinateMapper | **GPT-5 (high)** | Opus 4.1 |
| S2 Detection | **GPT-5-Codex** | Sonnet 4.5 |
| S3 Tracker | **GPT-5 (high)** | Opus 4.1 |
| **S3.5 Affordances + richness branches** | **Opus 4.1** | GPT-5 (high) |
| **S4 Verification** | **GPT-5 (high)** | Opus 4.1 |
| S4 Test suites | **Sonnet 4.5** | GPT-5 mini |
| S5 Engine + registry + stepBudget | **Opus 4.1** | GPT-5-Codex |
| S6 UI (incl. multi-step display) | **Sonnet 4.5** | GPT-5-Codex |
| S7 Zones | **GPT-5 (high)** | GPT-5-Codex |
| S7 CapabilityCard | **Sonnet 4.5** | GPT-5-Codex |
| S8 Pose integration | **GPT-5-Codex** | Opus 4.1 |
| S8 Pose math | **GPT-5 (high)** | Opus 4.1 |
| **S9 Difficulty + structural axis** | **Opus 4.1** | GPT-5 (high) |
| S10 Toddler | **Sonnet 4.5** | GPT-5-Codex |
| S11 SLM | **Opus 4.1** + **GPT-5-Codex** | — |
| S12 Hardening + audit | **Opus 4.1** | GPT-5-Codex |
| R4–R20, R39 (diagnosis) | **GPT-5 (high)** | Opus 4.1 |
| R21–R33, R40 (fixes) | **GPT-5-Codex** | Sonnet 4.5 |
| R34–R38 (panic) | **Sonnet 4.5** | GPT-5 mini |

---

# Sixteen rules to survive the build

1. **Never skip a gate.** A bug carried forward costs 5× to find later.
2. **Commit on every green gate.** Tag `v0.1-demoable` at S5.
3. **Test on the phone for every stage that touches runtime.** S4 is pure JVM and needs no device; everything else does.
4. **Overrun a stage by 50% → take the fallback.** §19 exists for this.
5. **Person 2 builds S3.5 + S4 on the JVM in parallel.** Biggest win available.
6. **Release APK at S5, S9, S12.** Three escape hatches.
7. **The analyzer never awaits anything.** Verify after every prompt.
8. **`Unsure` over `Fail` whenever uncertain.** It's what makes it feel smart.
9. **G0 must never gain a requirement.** It is the finale's safety net.
10. **Never print a unit you haven't earned.** Normalized is honest; fake cm is fatal.
11. **The object-only path must stay complete.** Test it with pose off at every stage.
12. **The human-only path must reach MEDIUM.** Test it by standing in an empty frame.
13. **Read the capability-card denominator from the registry.** Never hard-code it.
14. **Stop adding features at hour 14.** Quality is 30% of the grade.
15. **`VerificationOutcome` is a sealed interface.** Never collapse uncertainty into failure or success.
16. **Architecture conflicts stop the agent.** Report first; never silently invent a resolution.