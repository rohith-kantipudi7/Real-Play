# Tier-A model acquisition (§1.2)

Tier-A model binaries are **bundled into the APK** (`app/src/main/assets/models/`) but are
**never committed to Git** (`.gitignore` excludes `*.tflite`, `*.task`, `*.onnx`). This is
the single reproducible acquisition step. The build fails clearly if any are missing, so the
finished APK always contains every required Tier-A asset.

## Required assets

| Asset | Purpose | Stage |
|---|---|---|
| `efficientdet_lite0.tflite` | Object detection (COCO) | S2 |
| `pose_landmarker_lite.task` | Pose landmarks (default) | S8 |
| `pose_landmarker_full.task` | Pose landmarks (fallback) | S8 |

> Offline TTS voices (en-IN + en-US) are installed on the device via
> Settings → System → Languages → TTS. They are not files and not part of this step.

## Acquire the models

### Option A — download from official MediaPipe URLs (needs network once)

```powershell
./gradlew :app:fetchTierAModels
```

### Option B — copy from a local folder (offline machines)

Put the three files in a folder, then add this line to `local.properties`:

```properties
realplay.models.dir=D:\\realplay-models
```

Then run the same task — it copies from that folder instead of downloading:

```powershell
./gradlew :app:fetchTierAModels
```

## Verify

`:app:verifyTierAModels` runs automatically before every build (wired into `preBuild`). To
check on demand:

```powershell
./gradlew :app:verifyTierAModels
```

At runtime, `AssetModelResolver.inspectRequired()` logs each asset's byte size at startup:

```powershell
adb logcat -s RealPlay/MODEL
```

## Tier-B (optional, side-loaded — NOT part of this step)

Tier-B models live in `/sdcard/realplay/models/` and are optional by definition. The app runs
identically with that directory empty. See §1.2 of the architecture.
