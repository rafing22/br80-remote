package com.br80.remote

import android.content.Context
import android.content.SharedPreferences

enum class Br80Button(val displayName: String, val pressCode: Int, val releaseCode: Int) {
    UP("Freccia Su", 6, 38),
    DOWN("Freccia Giù", 5, 37),
    LEFT("Freccia Sinistra", 7, 39),
    RIGHT("Freccia Destra", 8, 40),
    HOME("Home / Conferma", 9, 41),
    CAMERA("Foto / Fotocamera", 2, 34),
    CALL("Chiamata / Intercom", 29, 45);

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

enum class GestureType(val displayName: String) {
    SINGLE("Singolo Tap"),
    DOUBLE("Doppio Tap"),
    TRIPLE("Triplo Tap"),
    LONG("Pressione Lunga (>500ms)")
}

enum class ActionType(val id: String, val displayName: String, val requiresParameter: Boolean = false) {
    NONE("none", "Nessuna azione"),
    TASKER_ONLY("tasker_only", "Solo Broadcast Tasker"),
    VOLUME_UP("volume_up", "Volume + (Musica)"),
    VOLUME_DOWN("volume_down", "Volume - (Musica)"),
    MEDIA_PLAY_PAUSE("media_play_pause", "Play / Pausa Musica"),
    MEDIA_NEXT("media_next", "Traccia Successiva"),
    MEDIA_PREV("media_prev", "Traccia Precedente"),
    OPEN_APP("open_app", "Apri Applicazione...", true),
    START_NAVIGATION("start_navigation", "Avvia Navigazione...", true),
    PHONE_ACCEPT("phone_accept", "Rispondi a Chiamata"),
    PHONE_REJECT("phone_reject", "Rifiuta / Chiudi Chiamata");

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
            Br80Button.HOME -> if (gesture == GestureType.SINGLE) ButtonAction(ActionType.MEDIA_PLAY_PAUSE) else ButtonAction(ActionType.NONE)
            Br80Button.RIGHT -> if (gesture == GestureType.SINGLE) ButtonAction(ActionType.MEDIA_NEXT) else ButtonAction(ActionType.NONE)
            Br80Button.LEFT -> if (gesture == GestureType.SINGLE) ButtonAction(ActionType.MEDIA_PREV) else ButtonAction(ActionType.NONE)
            Br80Button.CAMERA -> if (gesture == GestureType.SINGLE) ButtonAction(ActionType.TASKER_ONLY) else ButtonAction(ActionType.NONE)
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
    }
}
