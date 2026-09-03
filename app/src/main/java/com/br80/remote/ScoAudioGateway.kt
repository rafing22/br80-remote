package com.br80.remote

import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
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

    private var scoReceiver: BroadcastReceiver? = null
    private var scoTimeoutRunnable: Runnable? = null
    private var geminiWatchRunnable: Runnable? = null

    fun isTargetAudioDeviceConnected(context: Context, mappingStorage: MappingStorage): Boolean {
        val targetMac = mappingStorage.getAudioBtMac() ?: return false
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return false
        val adapter = bluetoothManager.adapter ?: return false
        if (!adapter.isEnabled) return false

        return try {
            val a2dp = bluetoothManager.getConnectedDevices(BluetoothProfile.A2DP).any { it.address.equals(targetMac, ignoreCase = true) }
            val headset = bluetoothManager.getConnectedDevices(BluetoothProfile.HEADSET).any { it.address.equals(targetMac, ignoreCase = true) }
            a2dp || headset
        } catch (e: Exception) {
            Log.w(TAG, "Impossibile verificare dispositivo audio connesso: ${e.message}")
            false
        }
    }

    /**
     * Apre il canale SCO verso l'interfono e attende la conferma di sistema.
     * Chiama [onResult] con true se il canale è confermato aperto entro [timeoutMs],
     * false altrimenti (il chiamante decide il fallback).
     */
    fun openScoAndAwait(context: Context, timeoutMs: Long = 2500L, onResult: (Boolean) -> Unit) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null) {
            onResult(false)
            return
        }

        cleanupScoWait(context)

        val appContext = context.applicationContext
        var resolved = false

        fun resolve(success: Boolean) {
            if (resolved) return
            resolved = true
            cleanupScoWait(appContext)
            onResult(success)
        }

        scoReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val state = intent?.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1) ?: -1
                when (state) {
                    AudioManager.SCO_AUDIO_STATE_CONNECTED -> resolve(true)
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
        } catch (e: Exception) {
            Log.w(TAG, "Errore rilascio SCO: ${e.message}")
        }
    }

    private fun cleanupScoWait(context: Context) {
        scoTimeoutRunnable?.let { handler.removeCallbacks(it) }
        scoTimeoutRunnable = null
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
    fun releaseScoWhenGeminiFinishes(context: Context, maxTimeoutMs: Long = 10_000L, pollIntervalMs: Long = 400L) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null) {
            releaseSco(context)
            return
        }

        geminiWatchRunnable?.let { handler.removeCallbacks(it) }
        val appContext = context.applicationContext
        val startTime = System.currentTimeMillis()
        var sawActiveRecording = false

        val poller = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startTime
                val hasActiveRecording = try {
                    audioManager.activeRecordingConfigurations.isNotEmpty()
                } catch (e: Exception) {
                    false
                }

                if (hasActiveRecording) {
                    sawActiveRecording = true
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
