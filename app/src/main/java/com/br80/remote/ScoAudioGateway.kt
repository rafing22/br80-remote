package com.br80.remote

import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Garantisce che TTS e comandi vocali (Gemini) vengano recapitati/ascoltati
 * tramite un canale voce Bluetooth (SCO) realmente aperto verso l'interfono,
 * invece di sperare che sia già pronto. Risolve il problema noto degli interfoni:
 * comando che parte prima che il canale sia aperto, o canale chiuso a metà frase.
 */
object ScoAudioGateway {

    private const val TAG = "ScoAudioGateway"
    private val handler = Handler(Looper.getMainLooper())

    // Piccolo margine dopo la conferma SCO_AUDIO_STATE_CONNECTED: su alcuni OEM l'evento
    // arriva un istante prima che il percorso audio sia davvero stabile, tagliando l'inizio
    // del beep/prompt di Gemini.
    private const val AUDIO_SETTLE_DELAY_MS = 350L

    private var scoReceiver: BroadcastReceiver? = null
    private var scoTimeoutRunnable: Runnable? = null
    private var settleRunnable: Runnable? = null
    private var geminiWatchRunnable: Runnable? = null
    private var previousAudioMode: Int? = null

    fun isTargetAudioDeviceConnected(context: Context, mappingStorage: MappingStorage): Boolean {
        val targetMacs = mappingStorage.getAudioBtDevices().map { it.first }
        if (targetMacs.isEmpty()) return false
        return isAnyDeviceConnected(context, targetMacs)
    }

    /** True se una sessione SCO precedente è ancora aperta o in fase di chiusura. */
    fun isSessionActive(): Boolean = scoReceiver != null || previousAudioMode != null

    fun isAnyDeviceConnected(context: Context, macs: List<String>): Boolean {
        if (macs.isEmpty()) return false
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return false
        val adapter = bluetoothManager.adapter ?: return false
        if (!adapter.isEnabled) return false
        return BtProfileConnectionChecker.isAnyDeviceConnected(macs)
    }

    /**
     * Apre il canale SCO verso l'interfono e attende la conferma di sistema (più un
     * breve margine di assestamento). Chiama [onResult] con true se il canale è pronto
     * entro [timeoutMs], false altrimenti (il chiamante decide il fallback).
     */
    fun openScoAndAwait(context: Context, timeoutMs: Long = 2500L, onResult: (Boolean) -> Unit) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null) {
            onResult(false)
            return
        }

        // Evita sessioni SCO sovrapposte (es. due gesti mappati su Gemini premuti a raffica):
        // una seconda richiesta mentre una è già aperta/in attesa clobbererebbe previousAudioMode,
        // impedendo poi il ripristino della modalità audio originale del telefono.
        if (scoReceiver != null || previousAudioMode != null) {
            Log.w(TAG, "Richiesta apertura SCO ignorata: una sessione è già in corso.")
            onResult(false)
            return
        }

        val appContext = context.applicationContext
        var resolved = false

        fun resolve(success: Boolean) {
            if (resolved) return
            resolved = true
            cleanupScoWait(appContext)
            if (!success) {
                // Il chiamante non aprirà mai una sessione da rilasciare in caso di fallimento
                // (ActionExecutor procede solo su successo): ripristiniamo qui lo stato audio
                // che avevamo alterato per il tentativo, altrimenti resta bloccato su
                // MODE_IN_COMMUNICATION indefinitamente.
                try {
                    audioManager.isBluetoothScoOn = false
                    @Suppress("DEPRECATION")
                    audioManager.stopBluetoothSco()
                    audioManager.mode = previousAudioMode ?: AudioManager.MODE_NORMAL
                } catch (e: Exception) {
                    Log.w(TAG, "Errore ripristino audio dopo fallimento SCO: ${e.message}")
                }
                previousAudioMode = null
            }
            onResult(success)
        }

        scoReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val state = intent?.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1) ?: -1
                when (state) {
                    AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                        // Attendi il margine di assestamento prima di considerare il canale
                        // davvero pronto per riprodurre/catturare audio.
                        settleRunnable = Runnable { resolve(true) }
                        handler.postDelayed(settleRunnable!!, AUDIO_SETTLE_DELAY_MS)
                    }
                    AudioManager.SCO_AUDIO_STATE_DISCONNECTED, AudioManager.SCO_AUDIO_STATE_ERROR -> resolve(false)
                }
            }
        }

        try {
            appContext.registerReceiver(scoReceiver, IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED))
        } catch (e: Exception) {
            Log.w(TAG, "Errore registrazione receiver SCO: ${e.message}")
        }

        scoTimeoutRunnable = Runnable {
            Log.w(TAG, "Timeout apertura canale SCO: interfono non pronto entro ${timeoutMs}ms.")
            resolve(false)
        }
        handler.postDelayed(scoTimeoutRunnable!!, timeoutMs)

        try {
            previousAudioMode = audioManager.mode
            // MODE_IN_COMMUNICATION è necessario su diversi OEM perché l'audio venga
            // effettivamente instradato sul canale SCO invece che restare sullo speaker/A2DP.
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
        } catch (e: Exception) {
            Log.e(TAG, "Errore avvio SCO: ${e.message}")
            resolve(false)
        }
    }

    /** Rilascia il canale SCO. Da chiamare sempre a fine messaggio TTS o fine sessione Gemini. */
    fun releaseSco(context: Context) {
        cleanupScoWait(context)
        geminiWatchRunnable?.let { handler.removeCallbacks(it) }
        geminiWatchRunnable = null
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.isBluetoothScoOn = false
            @Suppress("DEPRECATION")
            audioManager?.stopBluetoothSco()
            audioManager?.mode = previousAudioMode ?: AudioManager.MODE_NORMAL
            previousAudioMode = null
        } catch (e: Exception) {
            Log.w(TAG, "Errore rilascio SCO: ${e.message}")
        }
    }

    private fun cleanupScoWait(context: Context) {
        scoTimeoutRunnable?.let { handler.removeCallbacks(it) }
        scoTimeoutRunnable = null
        settleRunnable?.let { handler.removeCallbacks(it) }
        settleRunnable = null
        scoReceiver?.let {
            try {
                context.applicationContext.unregisterReceiver(it)
            } catch (e: Exception) {
                // Già deregistrato, ignora
            }
        }
        scoReceiver = null
    }

    /**
     * Dopo l'attivazione di Gemini, monitora quando il microfono di sistema
     * smette di essere usato (fine ascolto) e rilascia il canale SCO di conseguenza.
     * Applica comunque un timeout massimo di sicurezza per non tenere il canale aperto
     * indefinitamente se il monitor non rileva nulla.
     */
    fun releaseScoWhenGeminiFinishes(context: Context, onLog: ((String) -> Unit)? = null, maxTimeoutMs: Long = 10_000L, pollIntervalMs: Long = 400L) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null) {
            releaseSco(context)
            return
        }

        geminiWatchRunnable?.let { handler.removeCallbacks(it) }
        val appContext = context.applicationContext
        val startTime = System.currentTimeMillis()
        var sawActiveRecording = false
        var loggedMicSource = false

        val poller = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startTime
                val recordings = try {
                    audioManager.activeRecordingConfigurations
                } catch (e: Exception) {
                    emptyList()
                }
                val hasActiveRecording = recordings.isNotEmpty()

                if (hasActiveRecording) {
                    sawActiveRecording = true
                    // Log una tantum, alla prima registrazione rilevata: dice all'utente se
                    // il microfono effettivamente in uso è quello dell'interfono (SCO) o è
                    // ricaduto sul telefono, invece di doverlo solo presumere dal canale audio.
                    if (!loggedMicSource && onLog != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        loggedMicSource = true
                        val deviceType = recordings.firstOrNull()?.audioDevice?.type
                        val label = when (deviceType) {
                            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Interfono/Cuffie Bluetooth (SCO)"
                            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Telefono (microfono integrato)"
                            null -> "Sconosciuto (nessuna registrazione rilevata)"
                            else -> "Altro dispositivo (tipo $deviceType)"
                        }
                        onLog("Microfono in uso per Gemini: $label")
                    }
                }

                val geminiLikelyDone = sawActiveRecording && !hasActiveRecording

                if (geminiLikelyDone || elapsed >= maxTimeoutMs) {
                    releaseSco(appContext)
                } else {
                    geminiWatchRunnable = this
                    handler.postDelayed(this, pollIntervalMs)
                }
            }
        }
        geminiWatchRunnable = poller
        handler.postDelayed(poller, pollIntervalMs)
    }
}
