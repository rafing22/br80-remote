package com.br80.remote.ui.options

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import com.br80.remote.R

class AudioRoutingFragment : OptionsDetailFragment(R.layout.fragment_option_audio_routing, "Audio Interfono") {

    private lateinit var tvAudioBtDevice: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val cbOptAudioBtRouting = view.findViewById<CheckBox>(R.id.cbOptAudioBtRouting)
        tvAudioBtDevice = view.findViewById(R.id.tvAudioBtDevice)
        val btnOptChooseAudioBtDevice = view.findViewById<Button>(R.id.btnOptChooseAudioBtDevice)

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
