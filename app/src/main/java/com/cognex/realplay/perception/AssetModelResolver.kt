package com.cognex.realplay.perception

import android.content.Context
import android.content.res.AssetFileDescriptor
import com.cognex.realplay.util.RpLog
import java.io.File
import java.io.FileOutputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Resolves Tier-A models bundled in `assets/models/` (§1.2).
 *
 * Bundled binaries are never committed to Git; the Gradle `fetchTierAModels` task places
 * them before assembly, and `verifyTierAModels` fails the build if any are missing. At
 * runtime this class confirms each required asset is actually present in the APK and logs
 * its byte size — satisfying the S0 gate "Tier-A assets present IN THE APK, size logged".
 */
class AssetModelResolver(private val context: Context) {

    /** Required Tier-A model assets (relative to `assets/`). */
    val requiredAssets: List<String> = listOf(
        "models/efficientdet_lite0.tflite",
        "models/pose_landmarker_lite.task",
        "models/pose_landmarker_full.task"
    )

    data class AssetInfo(val path: String, val sizeBytes: Long, val present: Boolean)

    /** Lists each required asset with its size, logging the result. Never throws. */
    fun inspectRequired(): List<AssetInfo> = requiredAssets.map { path ->
        val info = try {
            context.assets.openFd(path).use { fd ->
                AssetInfo(path, fd.length, present = true)
            }
        } catch (e: Exception) {
            // openFd fails for compressed assets; fall back to a stream length probe.
            try {
                context.assets.open(path).use { input ->
                    AssetInfo(path, input.available().toLong(), present = true)
                }
            } catch (_: Exception) {
                AssetInfo(path, 0L, present = false)
            }
        }
        if (info.present) {
            RpLog.i(RpLog.Tag.MODEL, "Tier-A asset present: ${info.path} (${info.sizeBytes} bytes)")
        } else {
            RpLog.e(RpLog.Tag.MODEL, "Tier-A asset MISSING from APK: ${info.path}")
        }
        info
    }

    /**
     * Copies a bundled asset to app-internal storage so native libraries (MediaPipe/TFLite)
     * can open it by absolute path. Idempotent — skips when the destination already matches.
     */
    fun copyToFiles(assetPath: String): File {
        val outFile = File(context.filesDir, assetPath.substringAfterLast('/'))
        val expected = try {
            context.assets.openFd(assetPath).use { it.length }
        } catch (_: Exception) {
            -1L
        }
        if (outFile.exists() && expected > 0 && outFile.length() == expected) return outFile

        context.assets.open(assetPath).use { input ->
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
        }
        RpLog.i(RpLog.Tag.MODEL, "Copied $assetPath -> ${outFile.absolutePath} (${outFile.length()} bytes)")
        return outFile
    }

    /** Memory-maps an uncompressed bundled asset (zero-copy load path). */
    fun memoryMap(assetPath: String): MappedByteBuffer {
        val afd: AssetFileDescriptor = context.assets.openFd(assetPath)
        return afd.createInputStream().channel.use { channel ->
            channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
        }
    }
}
