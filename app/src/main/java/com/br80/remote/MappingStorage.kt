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
    SYSTEM_BACK("system_back", "Tasto Indietro", "Simula il tasto di sistema Indietro (richiede Servizio Accessibilità attivo)", ActionCategory.UTILITY),
    SYSTEM_HOME("system_home", "Tasto Home", "Simula il tasto di sistema Home (richiede Servizio Accessibilità attivo)", ActionCategory.UTILITY),
    LOCK_SCREEN("lock_screen", "Blocca Schermo", "Blocca immediatamente lo schermo del telefono (richiede Servizio Accessibilità attivo)", ActionCategory.UTILITY),
    VOLUME_SET_LEVEL("volume_set_level", "Imposta Volume Preciso", "Imposta il volume multimediale al livello preciso configurato in Opzioni", ActionCategory.MEDIA),
    REDIAL_LAST_RECEIVED("redial_last_received", "Richiama Ultima Chiamata Ricevuta", "Richiama l'ultimo numero da cui hai ricevuto una chiamata", ActionCategory.PHONE),
    REDIAL_LAST_DIALED("redial_last_dialed", "Richiama Ultimo Numero Effettuato", "Richiama l'ultimo numero che hai chiamato", ActionCategory.PHONE),

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
        val serialized = action.serialize()
        val previousSerialized = prefs.getString(key, null)
        val editor = prefs.edit().putString(key, serialized)
        // Un testo TTS personalizzato descrive l'azione precedente: se l'azione cambia
        // davvero, il testo vecchio resterebbe a descrivere qualcosa di sbagliato. Lo
        // rimuoviamo, così torna alla descrizione automatica della nuova azione finché
        // l'utente non ne imposta uno nuovo. Se l'azione è la stessa di prima (rise-
        // lezione accidentale), non tocchiamo il testo personalizzato.
        if (previousSerialized != serialized) {
            editor.remove("tts_label_$key")
        }
        editor.apply()
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

    fun isTtsFeedbackEnabled(): Boolean {
        return prefs.getBoolean(KEY_TTS_FEEDBACK, false)
    }

    fun setTtsFeedbackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TTS_FEEDBACK, enabled).apply()
    }

    fun isKeepAliveEnabled(): Boolean {
        return prefs.getBoolean(KEY_KEEP_ALIVE, true)
    }

    fun setKeepAliveEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_ALIVE, enabled).apply()
    }

    private fun decodeDeviceSet(raw: Set<String>?): Set<Pair<String, String>> {
        return raw?.mapNotNull {
            val parts = it.split("|", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank()) parts[0] to parts[1] else null
        }?.toSet() ?: emptySet()
    }

    private fun encodeDeviceSet(devices: Set<Pair<String, String>>): Set<String> {
        return devices.map { "${it.first}|${it.second}" }.toSet()
    }

    // Dispositivi BT Condizionali (es. Interfono / Casco / Auto) - può essere più di uno
    fun isConditionalBtEnabled(): Boolean {
        return prefs.getBoolean(KEY_CONDITIONAL_BT_ENABLED, false)
    }

    fun setConditionalBtEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CONDITIONAL_BT_ENABLED, enabled).apply()
    }

    fun getConditionalBtDevices(): Set<Pair<String, String>> {
        return decodeDeviceSet(prefs.getStringSet(KEY_CONDITIONAL_BT_DEVICES, emptySet()))
    }

    fun setConditionalBtDevices(devices: Set<Pair<String, String>>) {
        prefs.edit().putStringSet(KEY_CONDITIONAL_BT_DEVICES, encodeDeviceSet(devices)).apply()
    }

    // Dispositivi Audio BT per TTS / Comandi Vocali (es. Interfono/Casco), indipendenti dal Keep-Alive condizionale
    fun isAudioBtRoutingEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUDIO_BT_ENABLED, false)
    }

    fun setAudioBtRoutingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUDIO_BT_ENABLED, enabled).apply()
    }

    fun getAudioBtDevices(): Set<Pair<String, String>> {
        return decodeDeviceSet(prefs.getStringSet(KEY_AUDIO_BT_DEVICES, emptySet()))
    }

    fun setAudioBtDevices(devices: Set<Pair<String, String>>) {
        prefs.edit().putStringSet(KEY_AUDIO_BT_DEVICES, encodeDeviceSet(devices)).apply()
    }

    // Gestione Profili di Mappatura
    fun getActiveProfileName(): String {
        return prefs.getString(KEY_ACTIVE_PROFILE_NAME, "Standard") ?: "Standard"
    }

    fun setActiveProfileName(profileName: String) {
        prefs.edit().putString(KEY_ACTIVE_PROFILE_NAME, profileName).apply()
    }

    fun getProfileNames(): List<String> {
        val custom = prefs.getStringSet(KEY_PROFILE_NAMES, emptySet())?.sorted() ?: emptyList()
        return listOf("Standard") + custom
    }

    fun addProfile(profileName: String): Boolean {
        val trimmed = profileName.trim()
        if (trimmed.isEmpty() || trimmed.equals("Standard", ignoreCase = true)) return false
        val current = prefs.getStringSet(KEY_PROFILE_NAMES, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (current.any { it.equals(trimmed, ignoreCase = true) }) return false
        current.add(trimmed)
        prefs.edit().putStringSet(KEY_PROFILE_NAMES, current).apply()
        return true
    }

    fun deleteProfile(profileName: String) {
        if (profileName.equals("Standard", ignoreCase = true)) return
        val current = prefs.getStringSet(KEY_PROFILE_NAMES, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.removeAll { it.equals(profileName, ignoreCase = true) }
        prefs.edit().putStringSet(KEY_PROFILE_NAMES, current).apply()

        // Rimuove anche le mappature e i testi TTS personalizzati salvati per il profilo eliminato
        val editor = prefs.edit()
        for (button in Br80Button.values()) {
            for (gesture in GestureType.values()) {
                val mappingKey = "map_profile_${profileName}_${button.name}_${gesture.name}"
                editor.remove(mappingKey)
                editor.remove("tts_label_$mappingKey")
            }
        }
        editor.apply()

        if (getActiveProfileName().equals(profileName, ignoreCase = true)) {
            setActiveProfileName("Standard")
        }
    }

    // Livello di volume preciso applicato dall'azione VOLUME_SET_LEVEL (default 70%)
    fun getPreferredVolumeLevelPercent(): Int {
        return prefs.getInt(KEY_PREFERRED_VOLUME_PERCENT, 70)
    }

    fun setPreferredVolumeLevelPercent(percent: Int) {
        prefs.edit().putInt(KEY_PREFERRED_VOLUME_PERCENT, percent.coerceIn(0, 100)).apply()
    }

    // Opzione sviluppatore nascosta (sblocco a 7 tocchi in Opzioni): abilita il gancio
    // di debug ADB per simulare pressioni tasto, oltre a esistere solo in build di debug.
    fun isDeveloperModeEnabled(): Boolean {
        return prefs.getBoolean(KEY_DEVELOPER_MODE, false)
    }

    fun setDeveloperModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEVELOPER_MODE, enabled).apply()
    }

    fun getLastConnectedMac(): String? {
        return prefs.getString(KEY_LAST_MAC, null)
    }

    fun setLastConnectedMac(mac: String?) {
        prefs.edit().putString(KEY_LAST_MAC, mac).apply()
    }

    private fun getMappingKey(button: Br80Button, gesture: GestureType): String {
        val profile = getActiveProfileName()
        return if (profile.equals("Standard", ignoreCase = true)) {
            "map_${button.name}_${gesture.name}"
        } else {
            "map_profile_${profile}_${button.name}_${gesture.name}"
        }
    }

    // Testo TTS personalizzato per singola combinazione tasto+gesto (per profilo attivo).
    // Se non impostato, si usa la descrizione automatica dell'azione (ButtonAction.getReadableDescription()).
    fun getCustomTtsLabel(button: Br80Button, gesture: GestureType): String? {
        return prefs.getString("tts_label_${getMappingKey(button, gesture)}", null)
    }

    fun setCustomTtsLabel(button: Br80Button, gesture: GestureType, label: String?) {
        val key = "tts_label_${getMappingKey(button, gesture)}"
        if (label.isNullOrBlank()) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, label.trim()).apply()
        }
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
        private const val KEY_TTS_FEEDBACK = "pref_tts_feedback"
        private const val KEY_KEEP_ALIVE = "pref_keep_alive"
        private const val KEY_MULTI_TAP_WINDOW = "pref_multi_tap_window_ms"
        private const val KEY_LONG_PRESS_THRESHOLD = "pref_long_press_threshold_ms"
        private const val KEY_AUTO_BOOT = "pref_auto_boot"
        private const val KEY_CONDITIONAL_BT_ENABLED = "pref_conditional_bt_enabled"
        private const val KEY_CONDITIONAL_BT_DEVICES = "pref_conditional_bt_devices"
        private const val KEY_AUDIO_BT_ENABLED = "pref_audio_bt_enabled"
        private const val KEY_AUDIO_BT_DEVICES = "pref_audio_bt_devices"
        private const val KEY_ACTIVE_PROFILE_NAME = "pref_active_profile_name"
        private const val KEY_PROFILE_NAMES = "pref_profile_names"
        private const val KEY_PREFERRED_VOLUME_PERCENT = "pref_preferred_volume_percent"
        private const val KEY_DEVELOPER_MODE = "pref_developer_mode_enabled"
    }
}
