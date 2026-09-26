# RealPlay — Engineering Handoff Context

> Purpose: give a fresh machine / agent everything needed to continue building RealPlay from the
> current checkpoint. Read this **first**, then [docs/BUILD_PLAN_TODO.md](BUILD_PLAN_TODO.md) for the
> stage-by-stage checklist. Authoritative design lives in the two spec files at repo root.

**Current position (2026-09-27):** S0–S9 complete + DQ + v3.6-A + v3.6-B. All 286 unit tests green,
`assembleDebug` succeeds, app installs and launches clean on device. **NEXT stage: S10 (Toddler + TTS).**
Last stage committed: **S9** (structural axis + difficulty director + G6/G7).

---

## 1. What this project is

Android game engine (Kotlin + Jetpack Compose, single `:app` module, package `com.cognex.realplay`).
The phone camera watches a room; an on-device perception stack (object detection + colour + zones +
pose) builds a `WorldState`; a **skill library** of pure-JVM challenge generators proposes verifiable
mini-games; a closed set of 24 verifier rules judges success. An optional on-device LLM (Gemma/Qwen)
may *reword* games but never judges. Everything runs offline.

**Authoritative specs (repo root — always defer to these over this file):**
- `REALPLAY_ARCHITECTURE_v3.4_FINAL.md` — architecture, invariants, §-numbered sections (incl. v3.6 §25–§27).
- `REALPLAY_PROMPTS_v3.4_FINAL.md` — per-stage build prompts and acceptance gates (S0…S13).

**Richest running log of what was actually built** (per-stage implementation notes, gotchas, exact
constants): the Copilot repo memory at `/memories/repo/realplay.md`. Mirror new stage notes there.

---

## 2. Toolchain & environment (CRITICAL — read before building)

| Thing | Value |
|---|---|
| Build JDK | **Temurin 21** at `C:\Users\Vikram\.jdks\jdk-21.0.12.1+1` (must set `JAVA_HOME`) |
| Why not default JDK | Machine has JDK 8/11/25. **Kotlin 2.0.21 CANNOT run on JDK 25** (`IllegalArgumentException: 25.0.3`). Android Studio's JBR is 25 → set Gradle JDK to the path above. |
| Versions | AGP 8.7.2, Gradle 8.10.2, Kotlin 2.0.21, Compose BOM 2024.10.01, compileSdk/targetSdk 35, minSdk 26 |
| adb | `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe` |
| Device | `10BFC41SRT001UZ` = vivo I2501, 1440×3168, density 600 |

**Build / install (PowerShell — chain with `;`, never `&&`):**
```powershell
$env:JAVA_HOME="C:\Users\Vikram\.jdks\jdk-21.0.12.1+1"; .\gradlew.bat :app:testDebugUnitTest
$env:JAVA_HOME="C:\Users\Vikram\.jdks\jdk-21.0.12.1+1"; .\gradlew.bat :app:assembleDebug
$env:JAVA_HOME="C:\Users\Vikram\.jdks\jdk-21.0.12.1+1"; .\gradlew.bat :app:installDebug
```

**Gotchas that will bite you:**
- `git push` emits progress on **stderr** → PowerShell reports exit code 1 even on success. Verify
  with `git status -sb`: no `[ahead]` = pushed OK.
- `installDebug` can fail `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (debug keystore mismatch on this unit).
  Fix: `adb uninstall com.cognex.realplay` then `installDebug`, then re-grant CAMERA.
- OriginOS/vivo suppresses `Log.d` — use `RpLog.i` for anything you need in logcat.
- **Compose UI is NOT uiautomator-drivable on this vivo** (semantics not exposed). On-device
  verification is limited to: launch + detector GPU logs + no FATAL. Reaching in-game screens needs
  the phone physically aimed at props/a person (Calibration "Ready" gates on live detections). So
  gameplay logic is validated by **unit tests**, not device automation.
- Unit tests run on plain JVM with `unitTests.isReturnDefaultValues = true` → `RpLog` is a no-op in
  tests. Compose `Color` is a pure value class, so even `MobileTarget` is JVM-testable.

**Models (never in git — `*.tflite/*.task/*.onnx` and `models-local/` are gitignored):**
- Tier-A (bundled, auto-fetched by `:app:fetchTierAModels`, verified by `:app:verifyTierAModels`
  on preBuild): `efficientdet_lite2.tflite` (float32, GPU — primary object model),
  `efficientdet_lite0.tflite` (int8, CPU fallback), `pose_landmarker_lite.task`, `_full.task`.
- Local mirror for offline fetch: `models-local/` + `local.properties: realplay.models.dir=...`.
- Tier-B (optional, side-loaded to `/sdcard/realplay/models/`): `gemma3-1b-it-int4.task` (primary,
  license-gated) or `qwen2.5-1.5b-instruct.task` (backup, already pushed). App is fully playable
  and AI-off without them.

---

## 3. Architecture invariants (do not violate)

The **truth line** (perception → world → challenge → verify) is frozen. Presentation/engagement
layers wrap it but never change verdicts.

1. **Pure-JVM layers stay Android-free & unit-tested:** `challenge/`, `verify/`, `engine/` (logic),
   `coach/`, `present/` (except the `ui/present/MobileTarget` Android adapter). Zero Android imports.
2. **Closed rule set:** exactly 24 `RuleId`s (§4.1). Generators compose these; no new verifier/rule
   without a spec change. Every `RuleId` must have a verifier or `VerifierRegistry` throws at construction.
3. **Actors are positional:** verifiers read `spec.actorAt(0/1/2)`; step params are float-only.
4. **Structural axis (S9):** `VerificationStep.actorIndices: List<Int>? = null` + `ChallengeSpec.scopedTo(step)`
   projects the actor list per step. `null` = use whole list (backward-compatible). Verifiers unchanged.
5. **Totality via G0:** `ChallengeRegistry` always has ≥1 `Requirement.NONE` generator. On an empty
   live frame it binds G0 (never crashes on `.first()` of empty world).
6. **Step budget cap:** TODDLER/EARLY hard-capped to 1 step; others ≤2 (chained HARD forms).
7. **Object-only path survives pose-off:** disabling pose must leave a complete game (G0–G3, G7).
8. **AI never judges:** the LLM only rewords; steps/params/actors are always registry-produced;
   `SchemaValidator` + `SafetyFilter` gate every proposal; any failure → deterministic unchanged.
9. **v3.6 presentation invariants 21–25:** `PresentationTarget` renders only `RenderModel`;
   `RenderModel`/`coach` are pure JVM; visual-first coaching is presentation-only; PARTY reuses the
   same composer/validator/SafetyFilter (no new PASS path); every v3.6 capability is deletable.

**After every stage:** unit tests green → `assembleDebug` → `installDebug` → device launch check →
update `/memories/repo/realplay.md` → commit → push. Never advance on a red gate.

---

## 4. Completed work (through S9)

| Stage | Delivered |
|---|---|
| S0–S3.5 | Repo/nav/theme, CameraX pipeline, EfficientDet + ColorTagger, Tracker + StabilityDetector, Affordances + Richness + SceneCapability. All pure-JVM layers unit-tested. |
| S4 | 24 verifier rules, `TemporalGate`, `PoseMath`, `VerifyGeometry`, `Resolution` policy. 50 tests. |
| S5 | Engine (state machine, `MissionRunner`, `ScoreEngine`, `GameSession`) + G0/G1 + playable loop. |
| S6 | UI & feel: HUD, progress ring, step tracker, evidence panel, coaching toast, briefing, success burst, sound. |
| AI | On-device Gemma/Qwen composer (`ai/`), `SchemaValidator`, `PromptBuilder`; cloud fully removed; offline. |
| DQ | Object model upgraded to `efficientdet_lite2` float32 on GPU (much better accuracy). |
| S7 | Zones (`perception/zone/`) + G2/G3 + `SceneCapabilityCard`. |
| **S8** | Pose (`perception/pose/`, GPU), `PlayerTracker` (band-aware identity), `PlayerRegistry`, `PlayerDynamicsTracker`, `PoseLibrary` (8 poses), G4/G5, player halos. |
| **S9** | `DifficultyDirector` (skill/scene/age binding + explanation), structural axis (`actorIndices`/`scopedTo`), HARD 2-step chaining for G1/G2/G3, G6 (fetch race), G7 (triangle build) + **live triangle overlay**. Registry now 8 generators. |
| v3.6-A | Presentation seam: `present/RenderModel`, `SceneGraph`, `PresentationTarget`, `ui/present/MobileTarget`. |
| v3.6-B | Visual-first coaching: `coach/VisualCue`, `CuePlanner`, `CueResolver`, `ui/overlay/CueCanvas`. |
| S11 | On-device SLM composer **code is done** — activates only when a Tier-B model is present on device. |

The 8 shipped generators: **G0** last-resort, **G1** move-near, **G2** drop-zone, **G3** find-colour,
**G4** statue-match (pose), **G5** red-light-green-light (pose), **G6** fetch-race (turn-based),
**G7** triangle-build (demo centrepiece).

---

## 5. Pending work (in suggested order)

### S10 · Toddler mode + TTS + visual-first  ← NEXT
- `ai/Narrator.kt` (or `engine/Narrator`): Android `TextToSpeech`, offline en-IN/en-US, fire-and-forget.
- Toddler engine enforcement (§10): strip timers, `Fail` renders as `Unsure`, max 2 actors,
  `holdMs × 1.7` capped 2000 ms, tolerances × 2, hints at 8/16/24 s, STRICT `SafetyFilter`
  (UNKNOWN + sub-1.5 % excluded), break at 5 min, supervision notice once per install, `stepBudget = 1`.
- Wire the `coach/` VisualCue track as the **primary** carrier for TODDLER/EARLY (words decorative).
- Age-band variants (4–6 / 6–8 / 8+) + mid-session switch.
- Tests: TTS in airplane mode; no timer/clock/red-X in toddler; wrong action → encouragement; hint
  escalation; effective `holdMs ≤ 2000`; supervision notice once. Gate §13 S10 on device.

### v3.6-C · Party / Event mode  (`party/`, presentation/engagement only)
- `party/Roster.kt` (2–8 players / teams), `RoundPacer`, `PartyOrchestrator` (fairness / rotation),
  `TurnController`, `Leaderboard` (team aggregation) — all over EXISTING skills, no new verifier/RuleId.
- `ui/party/` screens for the 4 formats. Reuses the same composer/validator/SafetyFilter (invariant 24).

### UX · Full UI/UX overhaul  (~30 % of grade)
- Design system: `RpButton`, `RpCard`, etc. (consistent tokens over the current palette in `Color.kt`).
- Per-screen re-skin + polish + accessibility: Home / ModeSelect / Calibration / Game / Result /
  Settings / Party / Toddler.

### S12 · Hardening + freeze
- OPEN mode on ≥6 unfamiliar surfaces (registry never null); RECOMMENDED 10/10 on the prop table;
  sparse self-narration < richness 0.3; 10-min thermal run; airplane-mode full run; kill+relaunch
  resume via new `engine/SessionStore.kt`; APK on both phones/laptops; §21 freeze gate; release APK.

### Deferred / opportunistic
- **S11 activation:** already coded — only needs a Tier-B model on device + the in-app AI toggle.
- **DQ fine-tune:** `realplay_props.tflite` fine-tune (§1.5, Tier-B path already wired) + a live
  device fps/accuracy gate for `efficientdet_lite2`. If fps < 10, fall back to `efficientdet_lite0` float32.
- **S13** (P2 bonuses: Kokoro TTS, VLM enrichment, Office Kit, NPU) and **v3.6-D** (VrTarget) — frozen
  until S12 is fully green.

---

## 6. Where to look in the code

- Challenge generators: `app/src/main/java/com/cognex/realplay/challenge/generators/`
- Verifiers + rules: `.../verify/` and `.../verify/verifiers/`
- Engine (state machine, runner, score, session, difficulty): `.../engine/`
- Perception (object/zone/pose): `.../perception/`, `.../perception/zone/`, `.../perception/pose/`
- Presentation seam + coaching: `.../present/`, `.../coach/`, `.../ui/present/`, `.../ui/overlay/`
- On-device AI composer: `.../ai/`
- Unit tests: `app/src/test/java/com/cognex/realplay/**` (run `:app:testDebugUnitTest`)
