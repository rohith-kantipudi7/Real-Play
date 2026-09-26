# RealPlay — Demo Game Plan & Engine Redesign (Hackathon)

Single source of truth for the hackathon demo redesign. Captures the UX direction, the game
library, the demo arc, and — most importantly — **what is already built vs. what we still need to
build**. We finalize here first, then implement in priority order.

**Status legend:** ✅ implemented & working · 🟡 partial (exists but needs work) · ⬜ not built yet

**Priority order:** (1) in‑game smoothness → (2) Point & Play flow → (3) Audience control →
(4) instruction↔verifier lock → (5) new tough games → (6) demo dry‑run & freeze.

---

## 1. UX direction — "Point & Play" (zero‑config)  🟡

Goal: PLAY → live camera → games. No mode/age/player form. The camera *is* the setup.

| Task | Status | Notes |
|---|---|---|
| Remove the setup screen from the main flow (PLAY → camera directly) | ⬜ | Today: `ModeSelectScreen` (Objects/Body/Mixed + Age×4 + Players×2) sits in between |
| Auto‑sense the scene (objects present / person present) and pick the game family | 🟡 | Engine already supports MIXED + auto pose‑enable; needs wiring so no mode is asked |
| Move mode/age/players to an **Advanced** screen with sensible defaults | ⬜ | Defaults: MIXED, "Pro/Kids" audience, 1 player |
| Add a small **Advanced** link on Home | ⬜ | Keeps main flow clean |
| Capability reveal after scan ("I can see: cup, bottle, book") | ✅ | `SceneCapabilityCard` already redesigned to list detected objects |
| "Creating your game…" compose phase | ✅ | Done (compose‑first flow, part 6) |

---

## 2. Audience control — replaces Age + Difficulty  ⬜

Three tiers. Each tier sets the **starting** difficulty; the game keeps **ramping up** within a
session regardless of tier.

| Tier | Feel | Starts at | Maps to (under the hood) | Opens replay with |
|---|---|---|---|---|
| **Kids** | gentle, big cues, spoken, no timers | Easy | AgeBand EARLY + easy, ramps up | **Grab** |
| **Player** | balanced, playful | Medium | AgeBand MIDDLE + medium, ramps up | **Group** |
| **Pro** | fast, precise, timed (**demo default**) | Hard | AgeBand OLDER + hard, ramps up | **Triangle** |

> Names confirmed: **Kids / Player / Pro**. Difficulty always increases each level; the tier only
> decides where the ramp starts. Every tier opens on a different game.

| Task | Status | Notes |
|---|---|---|
| Difficulty director + age bands + knobs | ✅ | Already drives per‑tier tolerances, timers, step budget |
| Single **Audience** selector UI (3 tiers) | ⬜ | Replaces the 4 age chips + separate difficulty |
| Map Audience → AgeBand + Tier | ⬜ | Thin mapping over existing enums |
| Auto in Point & Play; overridable in Advanced; demo pinned to Pro | ⬜ | |
| **DECIDE:** final tier names | ⬜ | Product owner to suggest |

---

## 3. In‑game smoothness & polish — **TOP PRIORITY**  🟡

The #1 reported pain: "not tracking properly, messed up during the game, looks bad." Fix this first.

### 3a. Smooth level transitions (product owner spec — consistent ~2s beats, never rushed)

Between games the flow must be calm and deliberate, each beat with a consistent animation:

1. **Level Complete** — a smooth celebration panel (≈2s), not an instant jump. Shows points gained.
2. **"Let's go next"** — a short hand‑off beat.
3. **Next challenge intro** — slides in and **reads the game out loud** (TTS narrates the instruction).
4. **First‑time object selection** — the initial scan / "selected objects" reveal animates smoothly too.

| Task | Status |
|---|---|
| Paced advance: hold on PASS ≈2s before composing the next game | ✅ |
| "Level Complete" celebration overlay (smooth fade/scale, points gained) | ✅ |
| "Let's go next" hand‑off beat | ✅ (Level Complete → "Creating your game…" → briefing) |
| Read the instruction aloud (TTS) at each challenge start — all tiers, not just Kids | ✅ |
| Smooth first‑time object‑selection / capability reveal | 🟡 (card animates; make the scan smoother) |

### 3b. Tracking & HUD

| Task | Status | Notes |
|---|---|---|
| Position‑smoothing / interpolation on overlay boxes (glide, not jump) | ⬜ | Render between 10fps detections so boxes don't teleport |
| Tune coast/flicker window so a box never blinks out mid‑game | 🟡 | Tracker has coast; needs tuning for the demo kit |
| Lock the challenge once it starts (no mid‑game swap) | 🟡 | Compose‑first removed the swap; verify no residual re‑selection |
| Simplify in‑game HUD (progress dots ● ● ○ ○ ○ + one instruction + one ring) | ⬜ | Remove clutter; premium, calm |
| Smooth‑animate the goal cues (triangle edges, line‑up line, pose skeleton) | ⬜ | Ease‑in to green, no snapping |
| Live FPS profile on the demo device; fall back to lighter detector if <10 fps | 🟡 | lite2 GPU primary; lite0 fallback exists |
| Stable, clean object labels (no jitter/relabel churn) | 🟡 | Tie labels to stable track IDs |

---

## 4. Game library (verifiable mechanics only)

Every game maps 1:1 to something the camera can actually measure — the instruction wording must
match the verifier exactly (no free‑form prose that drifts). New games reuse existing geometry/pose
math where possible.

| Game | Player does | Mechanic (verifier) | Needs | Difficulty | Status | Generator |
|---|---|---|---|---|---|---|
| **Grab / Spotlight** | Show the **named** object | named object present + prominent/centered | 1 object | Easy (1) | 🟡 new‑small | adapt `G3` / new `GShowNamed` |
| **Group by colour** | Put the same‑**coloured** things together | one colour clustered + separated | ≥2 colours | Easy–Med (2) | ⬜ new‑med | new `GGroupColour` |
| **Group by kind** | Put the same‑**kind** things together (cups, bottles…) | one label/shape group clustered + separated | ≥2 kinds | Easy–Med (2) | ⬜ new‑med | new `GGroupKind` |
| **Statue (Pose)** | Copy & hold a body pose | pose landmark match, sustained | person | Med (2) | ✅ | `G4` STATUE_MATCH |
| **Triangle** | Arrange 3 objects into a triangle | triangle geometry, edges satisfied | 3 objects | Med (3) | ✅ | `G7` TRIANGLE_BUILD |
| **Together** | Bring 2 objects close | distance between two objects | 2 objects | Med (2) | ✅ | `G1` MOVE_CLOSE *(library only)* |
| **Line‑up** | Put **all** objects in one straight row | collinearity (low y‑variance, x‑spread) | 3+ objects | Hard (3) | ⬜ new‑small | new `GLineUp` |
| **Sort by size (↑/↓)** | Order objects **increasing OR decreasing** by size, L→R | x‑order == area‑order in the asked direction | 3+ objects | Hard (4) | ⬜ new‑med | new `GSortSize` |
| **Shuffle & Restore** | Memorize 3 spots → shuffle **all 3** → put them **back as before** | after all 3 moved, current positions match the recorded original | 3 objects | Hard (4) | ⬜ new‑med | new `GRestore` |
| **Freeze (red light)** | Move on green, freeze on red | motion gating over time | person/objects | Hard (3) | ✅ | `G5` RED_LIGHT_GREEN_LIGHT *(library only)* |
| **Fetch** | Grab the named object, carry to a spot | grab + carry to region | objects + spot | Hard (3) | ✅ | `G6` FETCH_RACE *(library only)* |
| **Hold & Pose (combo)** | Hold an object high **and** strike a pose | object‑in‑region **+** pose, simultaneously | person + object | Expert (5) | ⬜ new‑med | new `GComboPose` |

Notes:
- **Group by colour** and **Group by kind** are TWO separate games — product owner will test both on
  the real kit and drop whichever doesn't detect well.
- **Shuffle & Restore** is a 3‑phase state machine: RECORD original → wait until all 3 moved → RESTORE
  → verify match within tolerance.
- `G3` FIND_COLOR (single object) exists ✅ but is **replaced** by the two Group games for the demo.
- `G0` LAST_RESORT ("wave anything") exists ✅ but is **hidden** (childish; never‑fail safety only).
- `G2` DROP_ZONE exists ✅ but is **dropped** (the "zone" idea wasn't clear/compelling).

---

## 5. Demo arc — locked (5 games, increasing difficulty)

1. **Grab** — "Show me the cup" (proves the vision instantly) — Easy (1)
2. **Triangle** — 3 objects into a triangle — Med (3)
3. **Line‑up** — all objects in one straight row — Hard (3)
4. **Sort by size (↑/↓)** *or* **Shuffle & Restore** — Hard (4)
5. **Hold & Pose combo** *(finale, confirmed)* — Expert (5)

## 5.5 Per‑tier arcs (replay variety — each opens on a different game)

| Tier | Arc (opener first) |
|---|---|
| Tier 1 (gentle) | **Grab** → Pose → Group‑by‑kind |
| Tier 2 (default) | **Group‑by‑colour/kind** → Triangle → Line‑up → Pose |
| Tier 3 (demo) | **Triangle** → Line‑up → Sort‑by‑size → Shuffle & Restore → Hold & Pose combo |

> No fruit / colour warm‑up (detection confidence on fruit is unproven — product owner call).

---

## 6. Object kit (confirmed with product owner)

- Have: **cup, phone, water bottle, book, bowl, computer mouse, bag (backpack), people.**
- Add from pantry for colour: **banana / apple / orange** (COCO‑detected + colourful).
- **Bowl** = the container (no tape needed). **Retire the laptop** from games (flaky/dark/reflective).
- **Tripod locked** (stable camera = biggest smoothness win). Good, even lighting.

---

## 7. Open decisions (need product owner)

- [x] **Audience tier names** — Kids / Player / Pro (confirmed).
- [x] **Finale game** — Hold & Pose combo (confirmed).
- [x] **Fruit / colour warm‑up** — OUT (detection confidence unproven).
- [x] **5 games**, increasing difficulty (confirmed).
- [x] **Group by colour** AND **group by kind** — both built as separate games; test & drop the weaker.
- [x] **Sort by size** — randomize increasing/decreasing direction (confirmed).
- [x] **Shuffle & Restore** — added as a new memory game (confirmed).
- [x] **Smooth ~2s level transitions + TTS readout** — confirmed (see §3a).

**Design is FROZEN. Build order:** §3a smooth transitions → §3b tracking → Point & Play → Audience → new games.

---

## 8. Already shipped this session (context)

- ✅ v3.7 hybrid cloud composer (Azure GPT‑5.5 → Gemma 3n E4B → deterministic), object‑aware prompts.
- ✅ Compose‑first flow with "Creating your game…" phase.
- ✅ Capability card redesigned to list detected objects.
- ✅ Pro UI pass (Home/Play/Party/Settings/VR) + new logo.
- ✅ On‑device fallback upgraded to Gemma 3n E4B.
