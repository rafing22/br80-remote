package com.br80.remote.data

import androidx.room.Entity

/**
 * Una singola mappatura tasto+gesto per un profilo. Chiave primaria composita reale
 * (invece della vecchia chiave stringa concatenata in SharedPreferences): rename e
 * delete di un profilo diventano una singola query SQL, non una ricostruzione manuale
 * di ogni possibile chiave.
 */
@Entity(
    tableName = "button_mappings",
    primaryKeys = ["profileName", "button", "gesture"]
)
data class ButtonMappingEntity(
    val profileName: String,
    val button: String,
    val gesture: String,
    val actionTypeId: String,
    val parameter: String = "",
    val customTtsLabel: String? = null
)
