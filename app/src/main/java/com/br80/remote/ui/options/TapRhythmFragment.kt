package com.br80.remote.ui.options

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.br80.remote.R

class TapRhythmFragment : OptionsDetailFragment(R.layout.fragment_option_tap_rhythm, "Ritmo Tap") {

    private lateinit var tvCurrentTapSpeed: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvCurrentTapSpeed = view.findViewById(R.id.tvCurrentTapSpeed)

        view.findViewById<Button>(R.id.btnPresetFast).setOnClickListener { setTapSpeedPreset(280L, "Sportivo (280ms)") }
        view.findViewById<Button>(R.id.btnPresetStd).setOnClickListener { setTapSpeedPreset(420L, "Standard (420ms)") }
        view.findViewById<Button>(R.id.btnPresetGloves).setOnClickListener { setTapSpeedPreset(550L, "Guanti (550ms)") }
        view.findViewById<Button>(R.id.btnPresetSlow).setOnClickListener { setTapSpeedPreset(700L, "Lento (700ms)") }

        updateTapSpeedText()
    }

    private fun setTapSpeedPreset(ms: Long, name: String) {
        mappingStorage.setMultiTapWindowMs(ms)
        updateTapSpeedText()
        Toast.makeText(requireContext(), "Profilo impostato: $name", Toast.LENGTH_SHORT).show()
        host.appendLog("Velocità multi-tap impostata a $ms ms ($name)")
    }

    private fun updateTapSpeedText() {
        val current = mappingStorage.getMultiTapWindowMs()
        val desc = when {
            current <= 300L -> "Sportivo"
            current <= 450L -> "Standard"
            current <= 600L -> "Guanti"
            else -> "Personalizzato"
        }
        tvCurrentTapSpeed.text = "Finestra Doppio Tap: $current ms ($desc)"
    }
}
