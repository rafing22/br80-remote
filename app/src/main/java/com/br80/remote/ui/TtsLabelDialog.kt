package com.br80.remote.ui

import android.content.Context
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.br80.remote.Br80Button
import com.br80.remote.GestureType
import com.br80.remote.MappingStorage
import com.br80.remote.R

/**
 * Dialog di personalizzazione del testo TTS per una combinazione tasto/gesto.
 * Funzione condivisa tra ControllerFragment (tieni premuto su un gesto) e
 * OptionsFragment (dialog "Gestisci Testi TTS", che itera tutte le mappature) —
 * nessuna delle due dipende dall'altra.
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
    val currentCustom = mappingStorage.getCustomTtsLabel(button, gesture)

    val input = EditText(context).apply {
        hint = action.getReadableDescription()
        setText(currentCustom ?: "")
        setSelection(text.length)
        setTextColor(ContextCompat.getColor(context, R.color.cockpit_ink))
        setHintTextColor(ContextCompat.getColor(context, R.color.cockpit_muted))
    }

    AlertDialog.Builder(context, R.style.Theme_Br80_CockpitDialog)
        .setTitle("Personalizza Annuncio Vocale")
        .setMessage("${button.displayName} — ${gesture.displayName}\nTesto pronunciato dal TTS per questa azione. Lascia vuoto per usare il testo automatico (\"${action.getReadableDescription()}\").")
        .setView(input)
        .setPositiveButton("Salva") { _, _ ->
            val label = input.text.toString().trim()
            mappingStorage.setCustomTtsLabel(button, gesture, if (label.isEmpty()) null else label)
            onLog("Testo TTS personalizzato per ${button.name}_${gesture.name}: " + if (label.isEmpty()) "rimosso (torna automatico)" else "\"$label\"")
            onSaved?.invoke()
        }
        .setNegativeButton("Annulla", null)
        .show()
}
