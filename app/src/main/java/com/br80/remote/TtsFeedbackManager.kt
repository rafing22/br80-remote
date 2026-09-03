package com.br80.remote

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TtsFeedbackManager(
    private val context: Context,
    private val mappingStorage: MappingStorage
) : TextToSpeech.OnInitListener {

    private val tag = "TtsFeedbackManager"
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var lastSpokenText: String? = null
    private var lastSpokenAtMs: Long = 0L
    private val duplicateSuppressWindowMs = 800L

    init {
        initTts()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(tag, "Errore inizializzazione TextToSpeech: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.ITALIAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.getDefault()
            }
            isInitialized = true
            Log.d(tag, "TextToSpeech inizializzato con successo.")
        } else {
            Log.w(tag, "Inizializzazione TextToSpeech fallita con codice $status.")
            isInitialized = false
        }
    }

    fun speak(text: String) {
        if (!mappingStorage.isTtsFeedbackEnabled() || text.isBlank()) return

        val now = System.currentTimeMillis()
        if (text == lastSpokenText && (now - lastSpokenAtMs) < duplicateSuppressWindowMs) {
            Log.d(tag, "Annuncio duplicato ignorato: \"$text\"")
            return
        }
        lastSpokenText = text
        lastSpokenAtMs = now

        if (!isInitialized || tts == null) {
            initTts()
            return
        }

        // Il TTS di conferma azione usa sempre il canale audio predefinito (A2DP stereo):
        // il canale voce SCO (mono) serve solo quando serve catturare il microfono (Gemini),
        // non per la sola riproduzione di un annuncio.
        try {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "br80_action_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.e(tag, "Errore pronuncia TTS: ${e.message}")
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (e: Exception) {
            Log.w(tag, "Errore durante shutdown TTS: ${e.message}")
        }
    }
}
