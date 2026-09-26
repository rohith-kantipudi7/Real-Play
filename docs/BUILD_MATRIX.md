# BUILD_MATRIX — pinned versions

**No floating versions anywhere.** These are the exact versions the project builds with.
Update this file (and `gradle/libs.versions.toml`) together whenever a version changes.

| Component | Version | Where pinned |
|---|---|---|
| Android Studio | Ladybug 2024.2.1+ | IDE (informational) |
| Android Gradle Plugin (AGP) | 8.7.2 | `libs.versions.toml` → `agp` |
| Gradle | 8.10.2 | `gradle/wrapper/gradle-wrapper.properties` |
| Kotlin | 2.0.21 | `libs.versions.toml` → `kotlin` |
| Kotlin Compose plugin | 2.0.21 | `libs.versions.toml` → `kotlin` (plugin `kotlin-compose`) |
| Compose BOM | 2024.10.01 | `libs.versions.toml` → `composeBom` |
| JDK (source/target bytecode) | 17 | `app/build.gradle.kts` (`sourceCompatibility`/`jvmTarget`) |
| JDK (Gradle runtime JVM) | **21** (Temurin `C:\Users\Vikram\.jdks\jdk-21.0.12.1+1`) | `JAVA_HOME` / Android Studio Gradle JDK |

> ⚠ Kotlin 2.0.21 cannot run on JDK 25 (Android Studio's embedded JBR). Use JDK 17–21 as the
> **Gradle runtime JVM**. The bytecode target stays 17 for broad compatibility.
| compileSdk | 35 | `app/build.gradle.kts` |
| targetSdk | 35 | `app/build.gradle.kts` |
| minSdk | 26 | `app/build.gradle.kts` |

## Dependencies

| Library | Version | Priority | Wired in stage |
|---|---|---|---|
| androidx.core:core-ktx | 1.13.1 | P0 | S0 |
| androidx.activity:activity-compose | 1.9.3 | P0 | S0 |
| androidx.lifecycle:lifecycle-runtime-ktx | 2.8.6 | P0 | S0 |
| androidx.lifecycle:lifecycle-viewmodel-compose | 2.8.6 | P0 | S0 |
| androidx.navigation:navigation-compose | 2.8.3 | P0 | S0 |
| androidx.compose:compose-bom | 2024.10.01 | P0 | S0 |
| androidx.compose.material3:material3 | (BOM) | P0 | S0 |
| androidx.camera:camera-core/camera2/lifecycle/view | 1.4.0 | P0 | S1 (declared S0) |
| com.google.mediapipe:tasks-vision | 0.10.18 | P0 | S2 (declared S0) |
| com.google.mediapipe:tasks-genai | 0.10.18 | P1 | S11 (declared S0) |
| com.microsoft.onnxruntime:onnxruntime-android | 1.19.2 | P2 | deferred (declared S0) |
| junit | 4.13.2 | test | S0 |
| androidx.test.ext:junit | 1.2.1 | test | S0 |
| androidx.test.espresso:espresso-core | 3.6.1 | test | S0 |

## Tier-A model assets (§1.2 — not committed to Git)

| Asset | Source URL | Approx size |
|---|---|---|
| efficientdet_lite0.tflite | https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/1/efficientdet_lite0.tflite | ~5 MB |
| pose_landmarker_lite.task | https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task | ~6 MB |
| pose_landmarker_full.task | https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task | ~9 MB |

Acquire with `./gradlew :app:fetchTierAModels`. The build fails via `:app:verifyTierAModels`
if any are absent.
