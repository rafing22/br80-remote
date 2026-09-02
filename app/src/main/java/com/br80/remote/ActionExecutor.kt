package com.br80.remote

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telecom.TelecomManager
import android.util.Log
import android.view.KeyEvent

class ActionExecutor(
    private val context: Context,
    private val mappingStorage: MappingStorage,
    private val onLog: (String) -> Unit
) {

    private val tag = "ActionExecutor"

    fun execute(button: Br80Button, gesture: GestureType, batteryLevel: Int) {
        val action = mappingStorage.getAction(button, gesture)
        val eventId = "${button.name}_${gesture.name}"

        onLog("[GESTO] $eventId -> ${action.getReadableDescription()}")

        // 1. Emette sempre il broadcast per Tasker e altre app di automazione
        sendTaskerBroadcast(button, gesture, eventId, batteryLevel)

        // 2. Emette feedback aptico/sonoro
        triggerFeedback()

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
                ActionType.OPEN_APP -> {
                    openApp(action.parameter)
                }
                ActionType.START_NAVIGATION -> {
                    startNavigation(action.parameter)
                }
                ActionType.PHONE_ACCEPT -> {
                    acceptPhoneCall()
                }
                ActionType.PHONE_REJECT -> {
                    rejectPhoneCall()
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

    private fun openApp(packageName: String) {
        if (packageName.isBlank()) return
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            onLog("Applicazione $packageName non trovata.")
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
        } catch (e: Exception) {
            val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(target))).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(geoIntent)
        }
    }

    @SuppressLint("MissingPermission")
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

    private fun triggerFeedback() {
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
    }

    companion object {
        const val ACTION_BUTTON_EVENT = "com.br80.remote.BUTTON_EVENT"
    }
}
