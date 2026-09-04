package com.br80.remote.ui.options

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.br80.remote.R
import com.br80.remote.TaskerVirtualSlot

/** Schermata "Gestisci Tasti Tasker": elenco dei Tasti Virtuali (identificatori di
 * automazione per il plugin Tasker, indipendenti dal tasto/gesto fisico), ciascuno
 * rinominabile — il nome è anche il testo pronunciato via TTS quando il trigger scatta. */
class TaskerSlotsFragment : OptionsDetailFragment(R.layout.fragment_option_tasker_slots, "Gestisci Tasti Tasker") {

    private lateinit var container: LinearLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        container = view.findViewById(R.id.llTaskerSlotsContainer)
        val btnAdd = view.findViewById<Button>(R.id.btnAddTaskerSlot)

        btnAdd.setOnClickListener {
            val newSlot = mappingStorage.addTaskerVirtualSlot()
            host.appendLog("Nuovo Tasto Virtuale creato: ${newSlot.name}")
            populateSlotsList()
        }

        populateSlotsList()
    }

    private fun populateSlotsList() {
        val ctx = requireContext()
        val slots = mappingStorage.getTaskerVirtualSlots()

        container.removeAllViews()

        for (slot in slots) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(12, 14, 10, 14)
                background = ContextCompat.getDrawable(ctx, R.drawable.bg_tts_row)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, 6)
                layoutParams = params
                isClickable = true
                isFocusable = true
                setOnClickListener { showRenameDialog(slot) }
            }

            val name = TextView(ctx).apply {
                text = slot.name
                textSize = 14f
                setTextColor(ContextCompat.getColor(ctx, R.color.cockpit_ink))
                val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                layoutParams = p
            }

            val pencil = TextView(ctx).apply {
                text = "✏️"
                textSize = 15f
            }

            row.addView(name)
            row.addView(pencil)
            container.addView(row)
        }
    }

    private fun showRenameDialog(slot: TaskerVirtualSlot) {
        val ctx = requireContext()
        val input = EditText(ctx).apply {
            setText(slot.name)
            setSelection(text.length)
            setTextColor(ContextCompat.getColor(ctx, R.color.cockpit_ink))
        }

        AlertDialog.Builder(ctx, R.style.Theme_Br80_CockpitDialog)
            .setTitle("Rinomina Tasto Virtuale")
            .setMessage("Il nome scelto sarà pronunciato via TTS quando il trigger scatta, e va scelto anche nella configurazione dell'Evento dentro Tasker.")
            .setView(input)
            .setPositiveButton("Salva") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    mappingStorage.renameTaskerVirtualSlot(slot.id, newName)
                    host.appendLog("Tasto Virtuale rinominato: \"${slot.name}\" -> \"$newName\"")
                    populateSlotsList()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }
}
