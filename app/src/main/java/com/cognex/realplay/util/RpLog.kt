package com.cognex.realplay.util

import android.util.Log

/**
 * Central logging façade. Every log line is tagged with a subsystem so logcat can be
 * filtered per layer (e.g. `adb logcat -s RealPlay/CAMERA`).
 *
 * (Android note: [Log] is Android's built-in logcat writer — the platform equivalent of
 * console.log, but with severity levels and a tag.)
 */
object RpLog {

    /** One tag per architecture §3.4 subsystem, plus APP for app-lifecycle events. */
    enum class Tag {
        APP, CAMERA, PERCEPTION, WORLD, CHALLENGE, VERIFY, ENGINE, AI, DEVICE, UI, MODEL
    }

    private const val PREFIX = "RealPlay"

    private fun tagOf(tag: Tag) = "$PREFIX/${tag.name}"

    fun d(tag: Tag, message: String) = Log.d(tagOf(tag), message)
    fun i(tag: Tag, message: String) = Log.i(tagOf(tag), message)
    fun w(tag: Tag, message: String) = Log.w(tagOf(tag), message)
    fun e(tag: Tag, message: String, throwable: Throwable? = null) =
        Log.e(tagOf(tag), message, throwable)
}
