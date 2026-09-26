# RealPlay

> **RealPlay is an offline-first, phone-native physical game engine: on-device perception builds a live world model, an affordance and capability layer determines what the current scene can actually support, a deterministic registry composes a mission — tightening thresholds and, at the top tier, chaining primitives into ordered multi-step missions — from verifiable rules, optional local AI improves wording and ranking without ever controlling truth, and deterministic geometry plus temporal evidence decide PASS, FAIL or UNSURE.**

**Team Cognex · iQOO Hackathon 2026 · Hyderabad**

- Architecture spec: [REALPLAY_ARCHITECTURE_v3.4_FINAL.md](REALPLAY_ARCHITECTURE_v3.4_FINAL.md) (v3.4 — the only architecture attachment)
- Prompt playbook: [REALPLAY_PROMPTS_v3.4_FINAL.md](REALPLAY_PROMPTS_v3.4_FINAL.md) (v3.4)
- Build/version matrix: [docs/BUILD_MATRIX.md](docs/BUILD_MATRIX.md)
- Model acquisition: [docs/MODELS.md](docs/MODELS.md)

Target device: iQOO 15 · SD 8 Elite Gen 5 · 16 GB · Android 16. Single `:app` module,
Kotlin + Compose, no Hilt/Room/multi-module.

## ⚠ Required JDK — read first

The toolchain (AGP 8.7.2 + Kotlin 2.0.21) must run on **JDK 17–21**. This machine only had
JDK 8, 11 and Android Studio's JBR **25** — and Kotlin 2.0.21 crashes on JDK 25
(`IllegalArgumentException: 25.0.3`). A self-contained **Temurin JDK 21** was installed at:

```
C:\Users\Vikram\.jdks\jdk-21.0.12.1+1
```

- **Command line:** set `JAVA_HOME` to that path before `./gradlew` (see commands below).
- **Android Studio:** Settings → Build, Execution, Deployment → Build Tools → Gradle →
  **Gradle JDK** → add/select that folder. (The default embedded JBR 25 will fail the build.)

## Current stage: S0 — Foundation

Implemented: Compose project, navigation (home/modeselect/calibration/game/result/settings),
`RealPlayTheme`, `RpLog`, device probe (`DeviceCapabilities` + `PerformanceProfile`), the
§3.4 package skeleton, and Tier-A model bundling with a build-time verify-or-fail gate.

## Tier-A models (§1.2)

Model binaries are **never committed to Git**. Before your first build, acquire them into
`app/src/main/assets/models/`:

```powershell
./gradlew :app:fetchTierAModels
```

This downloads from the official MediaPipe URLs, or — if `realplay.models.dir` is set in
`local.properties` — copies from that local folder (best for offline machines). The build
fails clearly via `:app:verifyTierAModels` if any required model is missing. See
[docs/MODELS.md](docs/MODELS.md).

## Build & install

```powershell
# Point Gradle at JDK 21 (this session; or set the Gradle JDK in Android Studio)
$env:JAVA_HOME = "C:\Users\Vikram\.jdks\jdk-21.0.12.1+1"

# 1) Acquire Tier-A models (once)
./gradlew :app:fetchTierAModels

# 2) Debug build (offline once dependencies are cached)
./gradlew assembleDebug --offline

# 3) Install on the connected iQOO (real device, not emulator)
./gradlew installDebug
```

Filter logs by subsystem, e.g.:

```powershell
adb logcat -s RealPlay/APP RealPlay/DEVICE RealPlay/MODEL
```
