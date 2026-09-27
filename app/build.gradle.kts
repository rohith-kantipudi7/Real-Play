import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.cognex.realplay"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cognex.realplay"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-s0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Model assets must not be compressed so they can be memory-mapped at runtime.

        // v3.7 cloud composer config (Architecture §6.5 extension) — read from LOCAL, uncommitted
        // local.properties, never hard-coded. Empty defaults keep the build green on a machine with
        // no cloud config: CloudModel.isAvailable is simply false and the deterministic/on-device
        // chain is used (the AI-OFF gate).
        val localProps = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        fun azureProp(key: String): String = localProps.getProperty(key, "")
        buildConfigField("String", "AZURE_ENDPOINT", "\"${azureProp("realplay.azure.endpoint")}\"")
        buildConfigField("String", "AZURE_API_KEY", "\"${azureProp("realplay.azure.apiKey")}\"")
        buildConfigField("String", "AZURE_DEPLOYMENT", "\"${azureProp("realplay.azure.deployment")}\"")
        buildConfigField("String", "AZURE_API_VERSION", "\"${azureProp("realplay.azure.apiVersion").ifBlank { "2024-08-01-preview" }}\"")
        buildConfigField("String", "AZURE_CHAT_COMPLETIONS_URL", "\"${azureProp("realplay.azure.chatCompletionsUrl")}\"")
    }

    androidResources {
        noCompress += listOf("tflite", "task", "onnx")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // world/, challenge/, verify/ are pure JVM — unit tests run on the local JVM.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Core / lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose (BOM-managed)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // CameraX (S1)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // MediaPipe object detection (S2)
    implementation(libs.mediapipe.tasks.vision)

    // ONNX Runtime — optional stronger detector (YOLO), opt-in via a side-loaded model. Separate
    // runtime from MediaPipe's bundled TFLite, so no native conflict.
    implementation(libs.onnxruntime.android)

    // On-device LLM composer — local, offline (S11 / §6.5). Loads a side-loaded Tier-B .task bundle.
    implementation(libs.mediapipe.tasks.genai)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}

// ─────────────────────────────────────────────────────────────────────────────
// TIER-A MODEL BUNDLING (Architecture §1.2)
//
// Tier-A model binaries are NEVER committed to Git (see .gitignore). This is the
// single reproducible acquisition step. It places the required binaries into
// src/main/assets/models/ BEFORE the assets are merged into the APK.
//
//  * `fetchTierAModels`  — copies from a local dir (local.properties key
//                          `realplay.models.dir`) if set, else downloads from the
//                          official MediaPipe URLs. Idempotent: skips files that
//                          already exist with a non-zero size.
//  * `verifyTierAModels` — fails the build CLEARLY if any required asset is
//                          missing. Wired ahead of every build via preBuild.
// ─────────────────────────────────────────────────────────────────────────────
val tierAModels: Map<String, String> = mapOf(
    // Primary object detector: efficientdet_lite2 FLOAT32 — markedly more accurate than the
    // int8 lite0, and (being float) it can run on the GPU delegate. See MediaPipeObjectDetector.
    "efficientdet_lite2.tflite" to
        "https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite2/float32/latest/efficientdet_lite2.tflite",
    // Fallback object detector: efficientdet_lite0 int8 (CPU-only, tiny). Kept as a safety net.
    "efficientdet_lite0.tflite" to
        "https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/1/efficientdet_lite0.tflite",
    "pose_landmarker_lite.task" to
        "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task",
    "pose_landmarker_full.task" to
        "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task"
)

val modelsAssetDir = layout.projectDirectory.dir("src/main/assets/models")

fun localModelsDir(): File? {
    val props = Properties()
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { props.load(it) }
    val path: String = props.getProperty("realplay.models.dir")?.trim().orEmpty()
    return if (path.isEmpty()) null else File(path)
}

tasks.register("fetchTierAModels") {
    group = "realplay"
    description = "Acquire Tier-A model binaries into src/main/assets/models/ (copy from local dir or download)."
    doLast {
        val outDir = modelsAssetDir.asFile
        outDir.mkdirs()
        val fromDir = localModelsDir()
        tierAModels.forEach { (name, url) ->
            val target = File(outDir, name)
            if (target.exists() && target.length() > 0L) {
                logger.lifecycle("[realplay] Tier-A present: $name (${target.length()} bytes)")
                return@forEach
            }
            val local = fromDir?.let { File(it, name) }
            if (local != null && local.exists() && local.length() > 0L) {
                local.copyTo(target, overwrite = true)
                logger.lifecycle("[realplay] Copied $name from ${local.absolutePath} (${target.length()} bytes)")
            } else {
                logger.lifecycle("[realplay] Downloading $name from $url")
                try {
                    URI(url).toURL().openStream().use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    logger.lifecycle("[realplay] Downloaded $name (${target.length()} bytes)")
                } catch (e: Exception) {
                    target.delete()
                    throw GradleException(
                        "Failed to acquire Tier-A model '$name'. " +
                            "Either set `realplay.models.dir` in local.properties to a folder " +
                            "containing it, or restore network access. Cause: ${e.message}"
                    )
                }
            }
        }
    }
}

tasks.register("verifyTierAModels") {
    group = "realplay"
    description = "Fail the build clearly if any required Tier-A model asset is missing (§1.2)."
    doLast {
        val outDir = modelsAssetDir.asFile
        val missing = tierAModels.keys.filter { name ->
            val f = File(outDir, name)
            !f.exists() || f.length() == 0L
        }
        if (missing.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Required Tier-A model asset(s) missing from src/main/assets/models/:")
                    missing.forEach { appendLine("  - $it") }
                    appendLine()
                    appendLine("These binaries are intentionally NOT committed to Git (§1.2).")
                    appendLine("Run the reproducible acquisition step, then rebuild:")
                    appendLine("    ./gradlew :app:fetchTierAModels")
                }
            )
        }
        tierAModels.keys.forEach { name ->
            val f = File(outDir, name)
            logger.lifecycle("[realplay] Tier-A OK: $name (${f.length()} bytes)")
        }
    }
}

// Guarantee the gate runs before any build/assemble.
tasks.named("preBuild") {
    dependsOn("verifyTierAModels")
}
