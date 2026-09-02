package com.br80.remote

import android.content.Context
import android.content.SharedPreferences

enum class Br80Button(val displayName: String, val shortName: String, val pressCode: Int, val releaseCode: Int) {
    UP("Freccia Su", "UP", 6, 38),
    DOWN("Freccia Giù", "DOWN", 5, 37),
    LEFT("Sinistra (L)", "LEFT", 7, 39),
    RIGHT("Destra (R)", "RIGHT", 8, 40),
    HOME("Stop / Conferma (Rosso)", "HOME", 9, 41),
    CAMERA("Fotocamera (Alto)", "CAMERA", 2, 34),
    CALL("Voce / Intercom (Basso)", "CALL", 29, 45);

    companion object {
        private val codeMap = mutableMapOf<Int, Pair<Br80Button, Boolean>>()

        init {
            for (btn in values()) {
                codeMap[btn.pressCode] = Pair(btn, true)
                codeMap[btn.releaseCode] = Pair(btn, false)
            }
        }

        fun fromCode(code: Int): Pair<Br80Button, Boolean>? {
            return codeMap[code]
        }
    }
}

enum class GestureType(val displayName: String, val tag: String) {
    SINGLE("Singolo Tap", "1x"),
    DOUBLE("Doppio Tap", "2x"),
    TRIPLE("Triplo Tap", "3x"),
    LONG("Pressione Lunga", "LONG")
}

enum class ActionCategory(val displayName: String, val icon: String) {
    MEDIA("Media & Volume", "🎵"),
    VOICE("Assistente & AI", "🧠"),
    NAVIGATION("Navigazione & Mappe", "🧭"),
    PHONE("Telefono & Chiamate", "📱"),
    UTILITY("Utilità & Sistema", "🛠️"),
    AUTOMATION("Automazione & Macro", "⚡")
}

enum class ActionType(
    val id: String,
    val displayName: String,
    val description: String,
    val category: ActionCategory,
    val requiresParameter: Boolean = false,
    val parameterHint: String = ""
) {
    // Media
    VOLUME_UP("volume_up", "Volume +", "Aumenta il volume multimediale", ActionCategory.MEDIA),
    VOLUME_DOWN("volume_down", "Volume -", "Abbassa il volume multimediale", ActionCategory.MEDIA),
    MEDIA_PLAY_PAUSE("media_play_pause", "Play / Pausa", "Avvia o metti in pausa la riproduzione musicale", ActionCategory.MEDIA),
    MEDIA_NEXT("media_next", "Traccia Successiva", "Passa al brano musicale successivo", ActionCategory.MEDIA),
    MEDIA_PREV("media_prev", "Traccia Precedente", "Torna al brano precedente", ActionCategory.MEDIA),
    MUTE_TOGGLE("mute_toggle", "Muto / Suoneria", "Attiva o disattiva la modalità silenziosa", ActionCategory.MEDIA),

    // Voice
    VOICE_ASSISTANT_GEMINI("voice_gemini", "Google Gemini / Assistente", "Avvia l'ascolto vocale immediato di Gemini / Google", ActionCategory.VOICE),
    VOICE_RECORD_MEMO("voice_memo", "Registratore Vocale", "Apre il registratore vocale per un memo audio", ActionCategory.VOICE),

    // Navigation
    START_NAVIGATION("start_navigation", "Naviga verso Destinazione", "Avvia Google Maps verso l'indirizzo impostato", ActionCategory.NAVIGATION, true, "Es. Casa, Lavoro, o coordinate GPS"),
    OPEN_MAPS("open_maps", "Apri Google Maps", "Apre l'applicazione Google Maps", ActionCategory.NAVIGATION),

    // Phone
    PHONE_ACCEPT("phone_accept", "Rispondi a Chiamata", "Risponde alla telefonata in arrivo", ActionCategory.PHONE),
    PHONE_REJECT("phone_reject", "Rifiuta / Chiudi Chiamata", "Rifiuta la telefonata in arrivo o chiude la chiamata attiva", ActionCategory.PHONE),
    PHONE_SPEED_DIAL("phone_speed_dial", "Chiamata Rapida", "Chiama direttamente un numero di telefono predefinito", ActionCategory.PHONE, true, "Inserisci numero telefonico es. +393331234567"),

    // Utility
    FLASHLIGHT_TOGGLE("flashlight_toggle", "Torcia ON / OFF", "Accende o spegne il flash LED del telefono", ActionCategory.UTILITY),
    CAMERA_SHUTTER("camera_shutter", "Scatto Fotocamera", "Invia il comando hardware per scattare una foto", ActionCategory.UTILITY),
    KEEP_SCREEN_ON_TOGGLE("screen_on_toggle", "Schermo Sempre Acceso", "Mantiene lo schermo attivo per il navigatore", ActionCategory.UTILITY),
    OPEN_APP("open_app", "Apri Applicazione", "Avvia qualsiasi app installata a tua scelta", ActionCategory.UTILITY, true, "Seleziona app"),

    // Automation
    TASKER_ONLY("tasker_only", "Broadcast Tasker / MacroDroid", "Invia broadcast globale 'com.br80.remote.BUTTON_EVENT'", ActionCategory.AUTOMATION),
    NONE("none", "Nessuna Azione", "Disattiva qualsiasi azione per questo gesto", ActionCategory.AUTOMATION);

    companion object {
        fun fromId(id: String): ActionType {
            return values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: NONE
        }
    }
}

data class ButtonAction(val type: ActionType, val parameter: String = "") {
    fun serialize(): String {
        return if (parameter.isNotEmpty()) {
            "${type.id}:$parameter"
        } else {
            type.id
        }
    }

    fun getReadableDescription(): String {
        return when (type) {
            ActionType.OPEN_APP -> if (parameter.isNotEmpty()) "Apri: $parameter" else type.displayName
            ActionType.START_NAVIGATION -> if (parameter.isNotEmpty()) "Naviga: $parameter" else type.displayName
            ActionType.PHONE_SPEED_DIAL -> if (parameter.isNotEmpty()) "Chiama: $parameter" else type.displayName
            else -> type.displayName
        }
    }

    companion object {
        fun deserialize(serialized: String?): ButtonAction {
            if (serialized.isNullOrEmpty()) return ButtonAction(ActionType.NONE)
            val parts = serialized.split(":", limit = 2)
            val type = ActionType.fromId(parts[0])
            val param = if (parts.size > 1) parts[1] else ""
            return ButtonAction(type, param)
        }
    }
}

class MappingStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAction(button: Br80Button, gesture: GestureType): ButtonAction {
        val key = getMappingKey(button, gesture)
        val saved = prefs.getString(key, null)
        if (saved != null) {
            return ButtonAction.deserialize(saved)
        }
        return getDefaultAction(button, gesture)
    }

    fun setAction(button: Br80Button, gesture: GestureType, action: ButtonAction) {
        val key = getMappingKey(button, gesture)
        prefs.edit().putString(key, action.serialize()).apply()
    }

    fun hasMultiTapGestures(button: Br80Button): Boolean {
        val doubleAction = getAction(button, GestureType.DOUBLE)
        val tripleAction = getAction(button, GestureType.TRIPLE)
        return doubleAction.type != ActionType.NONE || tripleAction.type != ActionType.NONE
    }

    // Tempo di attesa finestra Multi-Tap in millisecondi (default 420ms)
    fun getMultiTapWindowMs(): Long {
        return prefs.getLong(KEY_MULTI_TAP_WINDOW, 420L)
    }

    fun setMultiTapWindowMs(ms: Long) {
        val clamped = ms.coerceIn(200L, 900L)
        prefs.edit().putLong(KEY_MULTI_TAP_WINDOW, clamped).apply()
    }

    // Soglia minima pressione lunga in millisecondi (default 550ms)
    fun getLongPressThresholdMs(): Long {
        return prefs.getLong(KEY_LONG_PRESS_THRESHOLD, 550L)
    }

    fun setLongPressThresholdMs(ms: Long) {
        val clamped = ms.coerceIn(300L, 1500L)
        prefs.edit().putLong(KEY_LONG_PRESS_THRESHOLD, clamped).apply()
    }

    fun isAutoStartOnBootEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_BOOT, true)
    }

    fun setAutoStartOnBootEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_BOOT, enabled).apply()
    }

    fun isHapticFeedbackEnabled(): Boolean {
        return prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true)
    }

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, enabled).apply()
    }

    fun isSoundFeedbackEnabled(): Boolean {
        return prefs.getBoolean(KEY_SOUND_FEEDBACK, false)
    }

    fun setSoundFeedbackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND_FEEDBACK, enabled).apply()
    }

    fun isKeepAliveEnabled(): Boolean {
        return prefs.getBoolean(KEY_KEEP_ALIVE, false)
    }

    fun setKeepAliveEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_ALIVE, enabled).apply()
    }

    fun getLastConnectedMac(): String? {
        return prefs.getString(KEY_LAST_MAC, null)
    }

    fun setLastConnectedMac(mac: String?) {
        prefs.edit().putString(KEY_LAST_MAC, mac).apply()
    }

    private fun getMappingKey(button: Br80Button, gesture: GestureType): String {
        return "map_${button.name}_${gesture.name}"
    }

    private fun getDefaultAction(button: Br80Button, gesture: GestureType): ButtonAction {
        return when (button) {
            Br80Button.UP -> if (gesture == GestureType.SINGLE) ButtonAction(ActionType.VOLUME_UP) else ButtonAction(ActionType.NONE)
            Br80Button.DOWN -> if (gesture == GestureType.SINGLE) ButtonAction(ActionType.VOLUME_DOWN) else ButtonAction(ActionType.NONE)
            Br80Button.HOME -> when (gesture) {
                GestureType.SINGLE -> ButtonAction(ActionType.MEDIA_PLAY_PAUSE)
                GestureType.LONG -> ButtonAction(ActionType.VOICE_ASSISTANT_GEMINI)
                else -> ButtonAction(ActionType.NONE)
            }
            Br80Button.RIGHT -> if (gesture == GestureType.SINGLE) ButtonAction(ActionType.MEDIA_NEXT) else ButtonAction(ActionType.NONE)
            Br80Button.LEFT -> if (gesture == GestureType.SINGLE) ButtonAction(ActionType.MEDIA_PREV) else ButtonAction(ActionType.NONE)
            Br80Button.CAMERA -> when (gesture) {
                GestureType.SINGLE -> ButtonAction(ActionType.FLASHLIGHT_TOGGLE)
                GestureType.LONG -> ButtonAction(ActionType.CAMERA_SHUTTER)
                else -> ButtonAction(ActionType.NONE)
            }
            Br80Button.CALL -> when (gesture) {
                GestureType.SINGLE -> ButtonAction(ActionType.PHONE_ACCEPT)
                GestureType.LONG -> ButtonAction(ActionType.PHONE_REJECT)
                else -> ButtonAction(ActionType.NONE)
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "br80_remote_mappings"
        private const val KEY_LAST_MAC = "pref_last_connected_mac"
        private const val KEY_HAPTIC_FEEDBACK = "pref_haptic_feedback"
        private const val KEY_SOUND_FEEDBACK = "pref_sound_feedback"
        private const val KEY_KEEP_ALIVE = "pref_keep_alive"
        private const val KEY_MULTI_TAP_WINDOW = "pref_multi_tap_window_ms"
        private const val KEY_LONG_PRESS_THRESHOLD = "pref_long_press_threshold_ms"
        private const val KEY_AUTO_BOOT = "pref_auto_boot"
    }
}
