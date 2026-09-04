package com.br80.remote.ui.options

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.br80.remote.R

class ProfileFragment : OptionsDetailFragment(R.layout.fragment_option_profile, "Profilo di Mappatura") {

    private lateinit var tvActiveProfile: TextView
    private lateinit var btnChooseProfile: Button
    private lateinit var btnNewProfile: Button
    private lateinit var btnRenameProfile: Button
    private lateinit var btnDeleteProfile: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvActiveProfile = view.findViewById(R.id.tvActiveProfile)
        btnChooseProfile = view.findViewById(R.id.btnChooseProfile)
        btnNewProfile = view.findViewById(R.id.btnNewProfile)
        btnRenameProfile = view.findViewById(R.id.btnRenameProfile)
        btnDeleteProfile = view.findViewById(R.id.btnDeleteProfile)

        btnChooseProfile.setOnClickListener { showChooseProfileDialog() }
        btnNewProfile.setOnClickListener { showNewProfileDialog() }
        btnRenameProfile.setOnClickListener { showRenameProfileDialog() }
        btnDeleteProfile.setOnClickListener { showDeleteProfileDialog() }

        updateActiveProfileLabel()
    }

    private fun updateActiveProfileLabel() {
        tvActiveProfile.text = "Profilo Attivo: ${mappingStorage.getActiveProfileName()}"
    }

    private fun showChooseProfileDialog() {
        val profiles = mappingStorage.getProfileNames()
        val current = mappingStorage.getActiveProfileName()
        val checkedIndex = profiles.indexOfFirst { it.equals(current, ignoreCase = true) }.coerceAtLeast(0)

        AlertDialog.Builder(requireContext(), R.style.Theme_Br80_CockpitDialog)
            .setTitle("Scegli Profilo di Mappatura")
            .setSingleChoiceItems(profiles.toTypedArray(), checkedIndex) { dialog, which ->
                val chosen = profiles[which]
                mappingStorage.setActiveProfileName(chosen)
                updateActiveProfileLabel()
                host.refreshControllerSelection()
                host.appendLog("Profilo di mappatura attivo: $chosen")
                dialog.dismiss()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showNewProfileDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Es. Musica, Navigatore, Sportivo"
        }

        AlertDialog.Builder(requireContext(), R.style.Theme_Br80_CockpitDialog)
            .setTitle("Nuovo Profilo di Mappatura")
            .setMessage("Le azioni del nuovo profilo partiranno vuote (Nessuna azione) e potrai personalizzarle liberamente.")
            .setView(input)
            .setPositiveButton("Crea") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), "Il nome del profilo non può essere vuoto.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (mappingStorage.addProfile(name)) {
                    mappingStorage.setActiveProfileName(name)
                    updateActiveProfileLabel()
                    host.refreshControllerSelection()
                    host.appendLog("Nuovo profilo creato e attivato: $name")
                } else {
                    Toast.makeText(requireContext(), "Esiste già un profilo con questo nome.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showRenameProfileDialog() {
        val profiles = mappingStorage.getProfileNames().filter { !it.equals("Standard", ignoreCase = true) }
        if (profiles.isEmpty()) {
            Toast.makeText(requireContext(), "Non ci sono profili personalizzati da rinominare.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(requireContext(), R.style.Theme_Br80_CockpitDialog)
            .setTitle("Rinomina Profilo")
            .setItems(profiles.toTypedArray()) { _, which ->
                val oldName = profiles[which]
                val input = EditText(requireContext()).apply { setText(oldName) }
                AlertDialog.Builder(requireContext(), R.style.Theme_Br80_CockpitDialog)
                    .setTitle("Nuovo nome per \"$oldName\"")
                    .setView(input)
                    .setPositiveButton("Rinomina") { _, _ ->
                        val newName = input.text.toString().trim()
                        if (mappingStorage.renameProfile(oldName, newName)) {
                            updateActiveProfileLabel()
                            host.refreshControllerSelection()
                            host.appendLog("Profilo rinominato: $oldName -> $newName")
                        } else {
                            Toast.makeText(requireContext(), "Nome non valido o già esistente.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun showDeleteProfileDialog() {
        val profiles = mappingStorage.getProfileNames().filter { !it.equals("Standard", ignoreCase = true) }
        if (profiles.isEmpty()) {
            Toast.makeText(requireContext(), "Non ci sono profili personalizzati da eliminare.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(requireContext(), R.style.Theme_Br80_CockpitDialog)
            .setTitle("Elimina Profilo")
            .setItems(profiles.toTypedArray()) { _, which ->
                val toDelete = profiles[which]
                AlertDialog.Builder(requireContext(), R.style.Theme_Br80_CockpitDialog)
                    .setTitle("Conferma Eliminazione")
                    .setMessage("Eliminare definitivamente il profilo \"$toDelete\" e tutte le sue mappature?")
                    .setPositiveButton("Elimina") { _, _ ->
                        mappingStorage.deleteProfile(toDelete)
                        updateActiveProfileLabel()
                        host.refreshControllerSelection()
                        host.appendLog("Profilo eliminato: $toDelete")
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }
}
