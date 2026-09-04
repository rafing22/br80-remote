package com.br80.remote.ui.options

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import com.br80.remote.R

class VolumeFeedbackFragment : OptionsDetailFragment(R.layout.fragment_option_volume_feedback, "Volume & Feedback") {

    private lateinit var tvPreferredVolumeLevel: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvPreferredVolumeLevel = view.findViewById(R.id.tvPreferredVolumeLevel)

        view.findViewById<Button>(R.id.btnVolPreset25).setOnClickListener { setPreferredVolumePreset(25) }
        view.findViewById<Button>(R.id.btnVolPreset50).setOnClickListener { setPreferredVolumePreset(50) }
        view.findViewById<Button>(R.id.btnVolPreset75).setOnClickListener { setPreferredVolumePreset(75) }
        view.findViewById<Button>(R.id.btnVolPreset100).setOnClickListener { setPreferredVolumePreset(100) }

        val cbOptHaptic = view.findViewById<CheckBox>(R.id.cbOptHaptic)
        val cbOptSound = view.findViewById<CheckBox>(R.id.cbOptSound)
        val cbOptTts = view.findViewById<CheckBox>(R.id.cbOptTts)

        cbOptHaptic.isChecked = mappingStorage.isHapticFeedbackEnabled()
        cbOptSound.isChecked = mappingStorage.isSoundFeedbackEnabled()
        cbOptTts.isChecked = mappingStorage.isTtsFeedbackEnabled()

        cbOptHaptic.setOnCheckedChangeListener { _, isChecked ->
            mappingStorage.setHapticFeedbackEnabled(isChecked)
            host.appendLog("Vibrazione feedback: " + if (isChecked) "Attiva" else "Disattivata")
        }
        cbOptSound.setOnCheckedChangeListener { _, isChecked ->
            mappingStorage.setSoundFeedbackEnabled(isChecked)
            host.appendLog("Beep audio feedback: " + if (isChecked) "Attivo" else "Disattivato")
        }
        cbOptTts.setOnCheckedChangeListener { _, isChecked ->
            mappingStorage.setTtsFeedbackEnabled(isChecked)
            host.appendLog("Annuncio vocale (TTS): " + if (isChecked) "Attivo" else "Disattivato")
            if (isChecked) {
                host.bleService?.ttsFeedbackManager?.speak("Annuncio vocale attivato")
            }
        }

        tvPreferredVolumeLevel.text = "Livello attuale: ${mappingStorage.getPreferredVolumeLevelPercent()}%"
    }

    private fun setPreferredVolumePreset(percent: Int) {
        mappingStorage.setPreferredVolumeLevelPercent(percent)
        tvPreferredVolumeLevel.text = "Livello attuale: $percent%"
        host.appendLog("Volume preciso preferito impostato a $percent%")
    }
}
