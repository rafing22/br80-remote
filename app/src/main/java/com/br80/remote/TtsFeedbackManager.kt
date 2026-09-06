package com.br80.remote

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
    private val handler = Handler(Looper.getMainLooper())
    private var utteranceListenerSet = false
    private var pendingUtteranceId: String? = null
    private var pendingOnComplete: (() -> Unit)? = null

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

    /**
     * Come [speak], ma invoca [onComplete] quando la frase ha davvero finito di essere
     * pronunciata (UtteranceProgressListener.onDone), invece di far indovinare al chiamante
     * un ritardo fisso. Se il completamento non arriva mai (errore del motore TTS, engine
     * bloccato, ecc.) [onComplete] scatta comunque dopo [maxWaitMs] come rete di sicurezza,
     * per non restare bloccati in attesa indefinitamente.
     */
    fun speakAndAwaitCompletion(text: String, maxWaitMs: Long, onComplete: () -> Unit) {
        if (text.isBlank() || !isInitialized || tts == null) {
            onComplete()
            return
        }
        ensureUtteranceListener()

        val utteranceId = "br80_priming_${System.currentTimeMillis()}"
        var completed = false
        fun complete() {
            if (completed) return
            completed = true
            pendingUtteranceId = null
            pendingOnComplete = null
            onComplete()
        }
        pendingUtteranceId = utteranceId
        pendingOnComplete = { complete() }
        handler.postDelayed({
            if (pendingUtteranceId == utteranceId) complete()
        }, maxWaitMs)

        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } catch (e: Exception) {
            Log.e(tag, "Errore pronuncia TTS (priming): ${e.message}")
            complete()
        }
    }

    private fun ensureUtteranceListener() {
        if (utteranceListenerSet) return
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                notifyUtteranceFinished(utteranceId)
            }

            @Deprecated("Deprecated in Java", ReplaceWith(""))
            override fun onError(utteranceId: String?) {
                notifyUtteranceFinished(utteranceId)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                notifyUtteranceFinished(utteranceId)
            }
        })
        utteranceListenerSet = true
    }

    private fun notifyUtteranceFinished(utteranceId: String?) {
        if (utteranceId == null || utteranceId != pendingUtteranceId) return
        val callback = pendingOnComplete
        pendingUtteranceId = null
        pendingOnComplete = null
        handler.post { callback?.invoke() }
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
