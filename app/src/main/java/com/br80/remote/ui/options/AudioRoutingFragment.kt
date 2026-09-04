package com.br80.remote.ui.options

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.br80.remote.R

class AudioRoutingFragment : OptionsDetailFragment(R.layout.fragment_option_audio_routing, "Audio Interfono") {

    private lateinit var tvAudioBtDevice: TextView
    private lateinit var tvGeminiLaunchDelay: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val cbOptAudioBtRouting = view.findViewById<CheckBox>(R.id.cbOptAudioBtRouting)
        tvAudioBtDevice = view.findViewById(R.id.tvAudioBtDevice)
        val btnOptChooseAudioBtDevice = view.findViewById<Button>(R.id.btnOptChooseAudioBtDevice)
        val btnApplyGeminiLaunchDelay = view.findViewById<Button>(R.id.btnApplyGeminiLaunchDelay)
        tvGeminiLaunchDelay = view.findViewById(R.id.tvGeminiLaunchDelay)

        cbOptAudioBtRouting.isChecked = mappingStorage.isAudioBtRoutingEnabled()
        updateAudioBtDeviceLabel()

        cbOptAudioBtRouting.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && mappingStorage.getAudioBtDevices().isEmpty()) {
                Toast.makeText(requireContext(), "Seleziona prima almeno un dispositivo audio dall'elenco qui sotto.", Toast.LENGTH_LONG).show()
                cbOptAudioBtRouting.isChecked = false
                return@setOnCheckedChangeListener
            }
            mappingStorage.setAudioBtRoutingEnabled(isChecked)
            host.appendLog("Canale voce garantito verso interfono: " + if (isChecked) "ATTIVO" else "DISATTIVATO")
        }

        btnOptChooseAudioBtDevice.setOnClickListener {
            showBondedDeviceMultiPickerDialog(requireContext(), mappingStorage.getAudioBtDevices()) { selected ->
                mappingStorage.setAudioBtDevices(selected)
                updateAudioBtDeviceLabel()
                host.appendLog("Dispositivi audio per TTS/Comandi impostati: ${selected.joinToString(", ") { it.second }}")
            }
        }

        updateGeminiLaunchDelayLabel()

        btnApplyGeminiLaunchDelay.setOnClickListener {
            showGeminiLaunchDelayDialog()
        }
    }

    private fun showGeminiLaunchDelayDialog() {
        val context = requireContext()
        val input = EditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "0-3000"
            setText(mappingStorage.getGeminiLaunchDelayMs().toString())
            setSelection(text.length)
            setTextColor(ContextCompat.getColor(context, R.color.cockpit_ink))
            setHintTextColor(ContextCompat.getColor(context, R.color.cockpit_muted))
        }

        AlertDialog.Builder(context, R.style.Theme_Br80_CockpitDialog)
            .setTitle("Ritardo Lancio Gemini")
            .setMessage("Attesa (in millisecondi) tra la conferma di apertura del canale voce e l'attivazione di Gemini. 0 = nessuna attesa.")
            .setView(input)
            .setPositiveButton("Applica") { _, _ ->
                val requested = input.text.toString().toLongOrNull()
                if (requested == null) {
                    Toast.makeText(context, "Inserisci un numero valido di millisecondi.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                mappingStorage.setGeminiLaunchDelayMs(requested)
                updateGeminiLaunchDelayLabel()
                host.appendLog("Ritardo lancio Gemini impostato a ${mappingStorage.getGeminiLaunchDelayMs()} ms")
                Toast.makeText(context, "Ritardo applicato: ${mappingStorage.getGeminiLaunchDelayMs()} ms", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun updateGeminiLaunchDelayLabel() {
        tvGeminiLaunchDelay.text = "Ritardo attivo: ${mappingStorage.getGeminiLaunchDelayMs()} ms"
    }

    private fun updateAudioBtDeviceLabel() {
        val devices = mappingStorage.getAudioBtDevices()
        tvAudioBtDevice.text = if (devices.isEmpty()) {
            "Nessun dispositivo selezionato"
        } else {
            "Dispositivi selezionati: " + devices.joinToString(", ") { it.second }
        }
    }
}
