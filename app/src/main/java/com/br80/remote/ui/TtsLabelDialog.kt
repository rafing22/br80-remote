package com.br80.remote.ui

import android.content.Context
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.br80.remote.ActionType
import com.br80.remote.Br80Button
import com.br80.remote.GestureType
import com.br80.remote.MappingStorage
import com.br80.remote.R

/**
 * Testo TTS personalizzato per un'azione (condiviso da qualunque tasto/gesto la esegua).
 * Per "Attiva Trigger Tasker" non si apre: il nome viene dal Tasto Virtuale (Opzioni >
 * Gestisci Tasti Tasker), non da un testo TTS separato.
 */
fun showCustomTtsLabelDialog(
    context: Context,
    mappingStorage: MappingStorage,
    button: Br80Button,
    gesture: GestureType,
    onLog: (String) -> Unit,
    onSaved: (() -> Unit)? = null
) {
    val action = mappingStorage.getAction(button, gesture)
    if (action.type == ActionType.TASKER_TRIGGER_EVENT) {
        Toast.makeText(context, "Per i trigger Tasker, rinomina il Tasto Virtuale in Opzioni > Gestisci Tasti Tasker.", Toast.LENGTH_LONG).show()
        return
    }
    showCustomTtsLabelForActionDialog(context, mappingStorage, action.type, onLog, onSaved)
}

fun showCustomTtsLabelForActionDialog(
    context: Context,
    mappingStorage: MappingStorage,
    actionType: ActionType,
    onLog: (String) -> Unit,
    onSaved: (() -> Unit)? = null
) {
    val currentCustom = mappingStorage.getCustomTtsLabelForActionType(actionType)

    val input = EditText(context).apply {
        hint = actionType.displayName
        setText(currentCustom ?: "")
        setSelection(text.length)
        setTextColor(ContextCompat.getColor(context, R.color.cockpit_ink))
        setHintTextColor(ContextCompat.getColor(context, R.color.cockpit_muted))
    }

    AlertDialog.Builder(context, R.style.Theme_Br80_CockpitDialog)
        .setTitle("Personalizza Annuncio Vocale")
        .setMessage("${actionType.displayName}\nTesto pronunciato dal TTS per questa azione, condiviso da qualunque tasto/gesto la esegua. Lascia vuoto per usare il testo automatico (\"${actionType.displayName}\").")
        .setView(input)
        .setPositiveButton("Salva") { _, _ ->
            val label = input.text.toString().trim()
            mappingStorage.setCustomTtsLabelForActionType(actionType, if (label.isEmpty()) null else label)
            onLog("Testo TTS personalizzato per ${actionType.displayName}: " + if (label.isEmpty()) "rimosso (torna automatico)" else "\"$label\"")
            onSaved?.invoke()
        }
        .setNegativeButton("Annulla", null)
        .show()
}
