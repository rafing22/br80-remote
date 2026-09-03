package com.br80.remote

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.telecom.TelecomManager
import android.util.Log
import android.view.KeyEvent

class ActionExecutor(
    private val context: Context,
    private val mappingStorage: MappingStorage,
    var ttsFeedbackManager: TtsFeedbackManager? = null,
    private val onLog: (String) -> Unit
) {

    private val tag = "ActionExecutor"

    private var isTorchOn = false
    private var wakeLock: PowerManager.WakeLock? = null

    fun execute(button: Br80Button, gesture: GestureType, batteryLevel: Int) {
        val action = mappingStorage.getAction(button, gesture)
        val eventId = "${button.name}_${gesture.name}"

        onLog("[GESTO] $eventId -> ${action.getReadableDescription()}")

        // 1. Emette sempre il broadcast per Tasker e altre app di automazione
        sendTaskerBroadcast(button, gesture, eventId, batteryLevel)

        // 2. Emette feedback aptico/sonoro/TTS (solo se l'azione non è "Nessuna Azione").
        // Per Gemini niente TTS: l'apertura del canale SCO subito dopo taglierebbe la frase
        // a metà mentre il canale passa da A2DP a SCO; resta solo vibrazione/beep immediati.
        if (action.type != ActionType.NONE && action.type != ActionType.TASKER_ONLY) {
            val ttsText = mappingStorage.getCustomTtsLabel(button, gesture) ?: action.getReadableDescription()
            triggerFeedback(ttsText, allowTts = action.type != ActionType.VOICE_ASSISTANT_GEMINI)
        }

        // 3. Esegue l'azione nativa corrispondente
        try {
            when (action.type) {
                ActionType.NONE, ActionType.TASKER_ONLY -> {
                    // Solo broadcast emesso
                }
                ActionType.VOLUME_UP -> {
                    adjustVolume(AudioManager.ADJUST_RAISE)
                }
                ActionType.VOLUME_DOWN -> {
                    adjustVolume(AudioManager.ADJUST_LOWER)
                }
                ActionType.MEDIA_PLAY_PAUSE -> {
                    sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                }
                ActionType.MEDIA_NEXT -> {
                    sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT)
                }
                ActionType.MEDIA_PREV -> {
                    sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                }
                ActionType.MUTE_TOGGLE -> {
                    toggleMute()
                }
                ActionType.VOICE_ASSISTANT_GEMINI -> {
                    launchVoiceAssistantGemini()
                }
                ActionType.VOICE_RECORD_MEMO -> {
                    launchVoiceRecorder()
                }
                ActionType.START_NAVIGATION -> {
                    startNavigation(action.parameter)
                }
                ActionType.OPEN_MAPS -> {
                    openMaps()
                }
                ActionType.PHONE_ACCEPT -> {
                    acceptPhoneCall()
                }
                ActionType.PHONE_REJECT -> {
                    rejectPhoneCall()
                }
                ActionType.PHONE_SPEED_DIAL -> {
                    speedDial(action.parameter)
                }
                ActionType.FLASHLIGHT_TOGGLE -> {
                    toggleFlashlight()
                }
                ActionType.CAMERA_SHUTTER -> {
                    triggerCameraShutter()
                }
                ActionType.KEEP_SCREEN_ON_TOGGLE -> {
                    toggleKeepScreenOn()
                }
                ActionType.OPEN_APP -> {
                    openApp(action.parameter)
                }
            }
        } catch (e: Exception) {
            val err = "Errore esecuzione azione ${action.type}: ${e.message}"
            Log.e(tag, err, e)
            onLog("ERRORE: $err")
        }
    }

    private fun sendTaskerBroadcast(button: Br80Button, gesture: GestureType, eventId: String, batteryLevel: Int) {
        val intent = Intent(ACTION_BUTTON_EVENT).apply {
            putExtra("button", button.name)
            putExtra("gesture", gesture.name)
            putExtra("event_id", eventId)
            putExtra("battery", batteryLevel)
            putExtra("timestamp", System.currentTimeMillis())
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        context.sendBroadcast(intent)
    }

    private fun adjustVolume(direction: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    private fun sendMediaKeyEvent(keyCode: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null) {
            val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            audioManager.dispatchMediaKeyEvent(eventDown)
            audioManager.dispatchMediaKeyEvent(eventUp)
        }
    }

    private fun toggleMute() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null) {
            val currentMode = audioManager.ringerMode
            if (currentMode == AudioManager.RINGER_MODE_NORMAL) {
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                onLog("Audio: Modalità Vibrazione attivata")
            } else {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                onLog("Audio: Modalità Normale (Suoneria) attivata")
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeUpScreenBriefly() {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wakeLock = powerManager?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "br80:screen_wake"
            )
            wakeLock?.acquire(3000L)
        } catch (e: Exception) {
            // Ignora
        }
    }

    private fun launchVoiceAssistantGemini() {
        wakeUpScreenBriefly()

        val shouldUseScoGateway = mappingStorage.isAudioBtRoutingEnabled() &&
            ScoAudioGateway.isTargetAudioDeviceConnected(context, mappingStorage)

        if (shouldUseScoGateway) {
            ScoAudioGateway.openScoAndAwait(context) { connected ->
                if (connected) {
                    onLog("Canale voce interfono aperto. Attivo Gemini...")
                } else {
                    onLog("Canale voce interfono non disponibile: attivo Gemini sul percorso audio predefinito.")
                }
                fireGeminiIntents()
                if (connected) {
                    ScoAudioGateway.releaseScoWhenGeminiFinishes(context)
                }
            }
        } else {
            fireGeminiIntents()
        }
    }

    private fun fireGeminiIntents() {
        // 1. Invio evento KEYCODE_VOICE_ASSIST hardware (il metodo nativo Android che attiva Gemini/Google Assistant in overlay anche da background)
        sendMediaKeyEvent(KeyEvent.KEYCODE_VOICE_ASSIST)

        // 2. Invio Intent ACTION_VOICE_COMMAND tramite PendingIntent per aggirare le restrizioni di background su Android 10-14+
        try {
            val voiceIntent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            val pendingIntent = PendingIntent.getActivity(context, 99, voiceIntent, flags)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                ).toBundle()
                pendingIntent.send(context, 0, null, null, null, null, options)
            } else {
                pendingIntent.send()
            }
            onLog("Gemini / Assistente Vocale attivato in background.")
            return
        } catch (e: Exception) {
            Log.w(tag, "PendingIntent ACTION_VOICE_COMMAND: ${e.message}")
        }

        // 3. Fallback con RecognizerIntent o app diretta
        try {
            val intent = Intent(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            onLog("Voice search avviato.")
        } catch (e: Exception) {
            onLog("Assistente attivato tramite tasto multimediale.")
        }
    }

    private fun launchVoiceRecorder() {
        val pm = context.packageManager
        val intent = Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(pm) != null) {
            context.startActivity(intent)
            onLog("Registratore vocale aperto.")
        } else {
            onLog("Nessuna app registratore trovata.")
        }
    }

    private fun openMaps() {
        val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(mapIntent)
            onLog("Google Maps aperto.")
        } catch (e: Exception) {
            val genericIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(genericIntent)
        }
    }

    private fun startNavigation(destination: String) {
        val target = if (destination.isNotBlank()) destination else "casa"
        val gmmIntentUri = Uri.parse("google.navigation:q=" + Uri.encode(target))
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(mapIntent)
            onLog("Navigazione avviata verso '$target'.")
        } catch (e: Exception) {
            val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(target))).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(geoIntent)
        }
    }

    @SuppressLint("MissingPermission")
    private fun speedDial(number: String) {
        if (number.isBlank()) {
            onLog("Nessun numero impostato per Chiamata Rapida.")
            return
        }
        val cleanNumber = number.trim()
        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$cleanNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(callIntent)
            onLog("Chiamata avviata verso $cleanNumber.")
        } else {
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            onLog("Compositore aperto per $cleanNumber (permesso CALL_PHONE non concesso).")
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION") // Nessuna alternativa pubblica senza diventare l'app Dialer di default (InCallService)
    private fun acceptPhoneCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            if (context.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                telecomManager?.acceptRingingCall()
                onLog("Chiamata accettata.")
            } else {
                onLog("Permesso ANSWER_PHONE_CALLS non concesso.")
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION") // Nessuna alternativa pubblica senza diventare l'app Dialer di default (InCallService)
    private fun rejectPhoneCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            if (context.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                telecomManager?.endCall()
                onLog("Chiamata rifiutata/terminata.")
            } else {
                onLog("Permesso ANSWER_PHONE_CALLS non concesso.")
            }
        }
    }

    private fun toggleFlashlight() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                val cameraId = cameraManager?.cameraIdList?.firstOrNull { id ->
                    val chars = cameraManager.getCameraCharacteristics(id)
                    chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
                if (cameraManager != null && cameraId != null) {
                    isTorchOn = !isTorchOn
                    cameraManager.setTorchMode(cameraId, isTorchOn)
                    onLog("Torcia: " + if (isTorchOn) "ACCESA 🔦" else "SPENTA")
                } else {
                    onLog("Flash torcia non disponibile su questo dispositivo.")
                }
            } catch (e: Exception) {
                onLog("Errore controllo torcia: ${e.message}")
            }
        }
    }

    private fun triggerCameraShutter() {
        // La pressione di KEYCODE_CAMERA o KEYCODE_VOLUME_DOWN scatta la foto in tutte le app fotocamera
        sendMediaKeyEvent(KeyEvent.KEYCODE_CAMERA)
        sendMediaKeyEvent(KeyEvent.KEYCODE_VOLUME_DOWN)
        onLog("Scatto Fotocamera inviato.")
    }

    @Suppress("DEPRECATION")
    private fun toggleKeepScreenOn() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (wakeLock != null && wakeLock?.isHeld == true) {
            wakeLock?.release()
            wakeLock = null
            onLog("Schermo sempre acceso: DISATTIVATO")
        } else if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
                "br80:keep_screen_on"
            ).apply {
                acquire(30 * 60 * 1000L) // Timeout sicurezza 30 minuti
            }
            onLog("Schermo sempre acceso: ATTIVATO (per 30 min o fino a nuovo tocco)")
        }
    }

    private fun openApp(packageName: String) {
        if (packageName.isBlank()) return
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            onLog("Applicazione $packageName avviata.")
        } else {
            onLog("Applicazione $packageName non trovata.")
        }
    }

    private fun triggerFeedback(actionDescription: String, allowTts: Boolean = true) {
        if (mappingStorage.isHapticFeedbackEnabled()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(45)
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Vibration failed: ${e.message}")
            }
        }

        if (mappingStorage.isSoundFeedbackEnabled()) {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 70)
            } catch (e: Exception) {
                Log.w(tag, "Tone failed: ${e.message}")
            }
        }

        if (allowTts && mappingStorage.isTtsFeedbackEnabled()) {
            ttsFeedbackManager?.speak(actionDescription)
        }
    }

    companion object {
        const val ACTION_BUTTON_EVENT = "com.br80.remote.BUTTON_EVENT"
    }
}
