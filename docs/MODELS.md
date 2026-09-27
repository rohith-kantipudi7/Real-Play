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

## Tier-0 (optional, side-loaded — a STRONGER object detector via YOLO + ONNX Runtime)

The bundled detector is EfficientDet-Lite2 (the strongest model MediaPipe's ObjectDetector supports).
For higher accuracy you can drop in a **YOLO** model (v8/v11, 80-class COCO) run on **ONNX Runtime** —
it activates automatically when the file below is present, and the app falls back to EfficientDet
when it is absent. Nothing else changes.

- **Get a model** (needs Python + Ultralytics, one-time):
  ```bash
  pip install ultralytics
  yolo export model=yolo11n.pt format=onnx imgsz=640   # or yolov8n.pt
  ```
  This produces `yolo11n.onnx` (~10 MB). Any Ultralytics YOLOv8/YOLO11 `.onnx` at 640×640 works.

- **Push it to the exact path** `YoloOnnxDetector` looks for:
  ```powershell
  adb shell mkdir -p /sdcard/realplay/models
  adb push yolo11n.onnx /sdcard/realplay/models/yolo.onnx
  ```

- **Relaunch** the app. Logcat should show `Detection pipeline: YOLO (ONNX) detector`. Delete the
  file to go back to EfficientDet.

Notes: YOLO's raw output is export-specific — `YoloOnnxDetector` decodes the standard Ultralytics
`[1,84,8400]` layout with confidence gating (0.30) + NMS (IoU 0.45). If a particular export detects
nothing, the tensor orientation / thresholds in that file are the knobs to tune. Runs off-thread on
ONNX Runtime (NNAPI when available, else CPU).

## Tier-B (optional, side-loaded — NOT part of this step)

Tier-B models live in `/sdcard/realplay/models/` and are optional by definition. The app runs
identically with that directory empty. See §1.2 of the architecture.

### On-device composer fallback — Gemma 3n E4B (effective 4B)

This is the **offline fallback** for the v3.7 challenge composer (used only when the cloud model is
unreachable). It is optional — with it absent, the app still composes games via the cloud, then the
deterministic composer.

- **Model:** Gemma 3n E4B, 4-bit, MediaPipe `.task` format (`gemma-3n-E4B-it-int4.task`, ~4.4 GB).
- **Download (gated — sign in to Hugging Face and accept Google's Gemma licence first):**
  - Model page: https://huggingface.co/google/gemma-3n-E4B-it-litert-preview
  - Direct file: https://huggingface.co/google/gemma-3n-E4B-it-litert-preview/resolve/main/gemma-3n-E4B-it-int4.task?download=true
  - Easiest path on a phone: install the **Google AI Edge Gallery** app
    (https://play.google.com/store/apps/details?id=com.google.ai.edge.gallery) and download
    Gemma 3n E4B in-app.

Push it to the device (the exact filename matters — `GemmaModel` looks for it):

```powershell
adb shell mkdir -p /sdcard/realplay/models
adb push gemma-3n-E4B-it-int4.task /sdcard/realplay/models/gemma-3n-E4B-it-int4.task
```

`GemmaModel` resolves, in order: `gemma-3n-E4B-it-int4.task` → `gemma3-1b-it-int4.task` →
`qwen2.5-1.5b-instruct.task`. Requires `com.google.mediapipe:tasks-genai` ≥ 0.10.24 (already set).
A high-end device (Pixel 8 / Galaxy S23 or newer, ≥ 8 GB RAM) is recommended for the 4B model.


---

# Full download reference — every model, where to get it

> Quick status: **Tier-A ✅ all present in the APK.** **Tier-B ⬇ Gemma downloading / ✅ Qwen local.**

## 1. Tier-A (REQUIRED — in the APK, ~20 MB)

Public URLs, no login. Fetched by `:app:fetchTierAModels` into `app/src/main/assets/models/`.

| Asset | Size | Delegate | Source URL |
|---|---|---|---|
| `efficientdet_lite0.tflite` | ~4.5 MB | **CPU/XNNPACK** (int8) | `https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/1/efficientdet_lite0.tflite` |
| `pose_landmarker_lite.task` | ~6 MB | CPU/GPU | `https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task` |
| `pose_landmarker_full.task` | ~9 MB | CPU/GPU | `https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task` |

> ⚠ `efficientdet_lite0` is **int8** → object detection runs on **CPU** (GPU delegate fails at
> runtime for int8 models). Do not switch it to GPU.

**Offline TTS (S10)** — installed via the OS, not a file:
Phone → Settings → System → Languages → Text-to-speech → install **en-IN + en-US**, verify in airplane mode.

## 2. Tier-B (OPTIONAL — the on-device LLM, side-loaded to the phone)

Powers the AI composer (§6.5, §S11). App is fully playable **without** it. Must be a MediaPipe
**`.task`** bundle (NOT GGUF). Push to `/sdcard/realplay/models/`; first match wins:
1. `gemma3-1b-it-int4.task` (primary)  2. `qwen2.5-1.5b-instruct.task` (backup)

| Model | License | Size | Login? | Where |
|---|---|---|---|---|
| **Gemma 3 1B IT** | Gemma license | ~0.5–1.0 GB | **Yes** | HF: https://huggingface.co/litert-community/Gemma3-1B-IT · Kaggle: https://www.kaggle.com/models/google/gemma-3/tfLite |
| **Qwen2.5-1.5B-Instruct** | Apache-2.0 | ~1.6 GB | No | `https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task` |

- **Gemma:** sign in → accept the license → **Files** tab → download a `.task` → **rename to** `gemma3-1b-it-int4.task`.
- **Qwen:** already downloaded to `%USERPROFILE%\realplay-models\qwen2.5-1.5b-instruct.task`.

**Install onto the phone (USB debugging authorized):**
```powershell
# Helper (pushes the Qwen backup):
powershell -ExecutionPolicy Bypass -File scripts\install-local-llm.ps1

# Or manual, either model:
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb shell mkdir -p /sdcard/realplay/models
& $adb push "<path-to>.task" /sdcard/realplay/models/gemma3-1b-it-int4.task
```
Then in-app: **Settings → AI composer → "Use Gemma to compose games" → ON**.

## 3. Optional / P2 (designed, NOT required for the demo)

| Model | Purpose | Stage | Notes |
|---|---|---|---|
| `realplay_props.tflite` | Fine-tuned detector on your props | §1.5 | Colab ~40 min, high ROI, same interface |
| MoveNet MultiPose | Pose backup if MediaPipe Pose too slow | S8 | P2 |
| SmolVLM-256M / Qwen2-VL-2B | VLM scene enrichment | S13 | P2 |
| Kokoro-82M ONNX (+ onnxruntime-android) | Higher-quality offline TTS | S13 | P2 |
| Qualcomm AI Hub (NPU compile) | NPU acceleration | S13 | P2 — never claimed unless measured |

## Master checklist

```
[x] A1 efficientdet_lite0.tflite      (in APK)
[x] A2 pose_landmarker_lite.task      (in APK)
[x] A3 pose_landmarker_full.task      (in APK)
[ ] Offline TTS en-IN + en-US installed + verified in airplane mode
[~] B1 gemma3-1b-it-int4.task         (downloading — HF/Kaggle login) → rename → adb push
[x] B2 qwen2.5-1.5b-instruct.task     (downloaded locally)            → adb push (backup)
[ ] Tier-B pushed to /sdcard/realplay/models/ + AI toggle ON
[ ] C1 realplay_props.tflite          (optional fine-tune)
```
