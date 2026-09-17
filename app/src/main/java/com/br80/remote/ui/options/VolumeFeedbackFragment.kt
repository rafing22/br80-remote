package com.br80.remote.ui.options

import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import com.br80.remote.R

class VolumeFeedbackFragment : OptionsDetailFragment(R.layout.fragment_option_volume_feedback, "Volume & Feedback") {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
    }
}
