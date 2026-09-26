package com.cognex.realplay.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import com.cognex.realplay.util.RpLog
import java.util.Locale

/**
 * Offline, fire-and-forget speech for the TODDLER path (Architecture §10, §13 S10). Wraps Android's
 * on-device [TextToSpeech] — no network, no key, no runtime model. Prefers en-IN (the target demo
 * locale), then en-US, then the device default; any failure degrades to a silent no-op rather than
 * ever blocking or crashing the game.
 */
class Narrator(context: Context) {

    @Volatile private var ready = false

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS && selectVoice()
    }

    private fun selectVoice(): Boolean {
        for (locale in listOf(Locale("en", "IN"), Locale.US, Locale.getDefault())) {
            if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
                tts.language = locale
                return true
            }
        }
        return false
    }

    /** Speaks [text]. [interrupt] flushes anything queued so a new instruction always wins. */
    fun speak(text: String, interrupt: Boolean = true) {
        if (!ready || text.isBlank()) return
        val mode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        runCatching { tts.speak(text, mode, null, text.hashCode().toString()) }
            .onFailure { RpLog.w(RpLog.Tag.AI, "Narrator speak failed: ${it.message}") }
    }

    fun shutdown() {
        runCatching { tts.stop(); tts.shutdown() }
    }
}
