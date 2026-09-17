package com.example.flightinstruments

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class SpeechHelper(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var enabled = false
    private var lastSpeechTime = 0L

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts?.language = Locale.CHINA
        }
    }

    fun speak(text: String, minIntervalMs: Long = 30_000L) {
        if (!enabled || !ready || text.isBlank()) return

        val now = System.currentTimeMillis()
        if (now - lastSpeechTime < minIntervalMs) return

        lastSpeechTime = now
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "flight_instruments")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
