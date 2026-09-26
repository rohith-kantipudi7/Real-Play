package com.cognex.realplay.ui.game

import android.media.AudioManager
import android.media.ToneGenerator
import com.cognex.realplay.engine.AppSettings
import com.cognex.realplay.util.RpLog

/**
 * Game sound effects (Architecture §13 S6). Five distinct cues — success, step, fail, countdown,
 * start — each honouring the [AppSettings.muted] toggle.
 *
 * The spec calls for a SoundPool; RealPlay ships no bundled audio assets, so this uses the
 * platform [ToneGenerator] (system tones, no `res/raw` files) to give real, distinct audio
 * feedback with zero asset weight. The API surface (one method per cue + mute) is identical to a
 * SoundPool-backed manager, so swapping in sampled assets later is a drop-in change.
 *
 * [ToneGenerator] is created lazily and released in [release]. Playback is fire-and-forget and
 * never blocks the caller.
 */
class SoundManager {

    private var tone: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME)
    } catch (t: Throwable) {
        RpLog.w(RpLog.Tag.APP, "ToneGenerator unavailable: ${t.message}")
        null
    }

    /** Intermediate step completion — deliberately distinct from [success]. */
    fun step() = play(ToneGenerator.TONE_PROP_BEEP2, 120)

    /** Whole-mission success. */
    fun success() = play(ToneGenerator.TONE_CDMA_CONFIRM, 200)

    /** A confident failure / timeout. Calm — never harsh. */
    fun fail() = play(ToneGenerator.TONE_PROP_NACK, 200)

    /** One 3-2-1 countdown tick. */
    fun countdown() = play(ToneGenerator.TONE_PROP_BEEP, 90)

    /** Challenge start / "GO". */
    fun start() = play(ToneGenerator.TONE_PROP_ACK, 150)

    private fun play(toneType: Int, durationMs: Int) {
        if (AppSettings.muted.value) return
        val t = tone ?: return
        try {
            t.startTone(toneType, durationMs)
        } catch (_: Throwable) {
            // A busy generator just drops the cue — audio is never load-bearing.
        }
    }

    fun release() {
        tone?.release()
        tone = null
    }

    private companion object {
        const val VOLUME = 80 // 0..100
    }
}
