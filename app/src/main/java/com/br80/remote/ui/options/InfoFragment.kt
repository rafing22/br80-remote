package com.br80.remote.ui.options

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.br80.remote.AppUpdateManager
import com.br80.remote.BuildConfig
import com.br80.remote.R

class InfoFragment : OptionsDetailFragment(R.layout.fragment_option_info, "Info & Aggiornamenti") {

    private var versionTapCount = 0
    private var versionTapLastMs = 0L
    private lateinit var cardDeveloperMode: LinearLayout
    private lateinit var cbOptDevAdbHook: CheckBox

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cardDeveloperMode = view.findViewById(R.id.cardDeveloperMode)
        cbOptDevAdbHook = view.findViewById(R.id.cbOptDevAdbHook)

        view.findViewById<Button>(R.id.btnCheckUpdate).setOnClickListener {
            AppUpdateManager.checkForUpdates(requireActivity(), isManualCheck = true)
        }

        val tvAppVersionInfo = view.findViewById<TextView>(R.id.tvAppVersionInfo)
        tvAppVersionInfo.text =
            "Livall BR80 Remote v${BuildConfig.VERSION_NAME} • Open Source\nSupporta telecomandi Livall BR80 / BlingRemote"
        tvAppVersionInfo.setOnClickListener { onVersionInfoTapped() }

        if (BuildConfig.DEBUG && mappingStorage.isDeveloperModeEnabled()) {
            cardDeveloperMode.visibility = View.VISIBLE
        }
        cbOptDevAdbHook.isChecked = mappingStorage.isDeveloperModeEnabled()
        cbOptDevAdbHook.setOnCheckedChangeListener { _, isChecked ->
            mappingStorage.setDeveloperModeEnabled(isChecked)
            host.appendLog("Opzione sviluppatore - Gancio Comandi ADB: " + if (isChecked) "Attivo" else "Disattivato")
        }
    }

    // Sblocco nascosto dell'opzione sviluppatore: 7 tocchi consecutivi entro 3s sul
    // testo versione, come il "tap sul numero build" di Android.
    private fun onVersionInfoTapped() {
        val now = System.currentTimeMillis()
        if (now - versionTapLastMs > 3000L) {
            versionTapCount = 0
        }
        versionTapLastMs = now
        versionTapCount++
        if (versionTapCount >= 7) {
            versionTapCount = 0
            if (BuildConfig.DEBUG) {
                mappingStorage.setDeveloperModeEnabled(true)
                cbOptDevAdbHook.isChecked = true
                cardDeveloperMode.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "Modalità sviluppatore attivata", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
