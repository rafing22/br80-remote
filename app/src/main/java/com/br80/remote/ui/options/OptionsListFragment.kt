package com.br80.remote.ui.options

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.br80.remote.Br80AccessibilityService
import com.br80.remote.MainActivity
import com.br80.remote.R
import com.br80.remote.ui.OptionsFragment

/** Elenco principale di Opzioni: 6 righe navigabili raggruppate per frequenza d'uso,
 * ciascuna con un sottotitolo che riflette lo stato corrente — sostituisce le 9 card
 * impilate in un unico scroll di prima. */
class OptionsListFragment : Fragment(R.layout.fragment_options_list) {

    private val host get() = requireActivity() as MainActivity
    private val mappingStorage get() = host.mappingStorage
    private val optionsHost get() = parentFragment as OptionsFragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.rowProfile).setOnClickListener { optionsHost.navigateToDetail(ProfileFragment()) }
        view.findViewById<View>(R.id.rowTapRhythm).setOnClickListener { optionsHost.navigateToDetail(TapRhythmFragment()) }
        view.findViewById<View>(R.id.rowVolumeFeedback).setOnClickListener { optionsHost.navigateToDetail(VolumeFeedbackFragment()) }
        view.findViewById<View>(R.id.rowConnection).setOnClickListener { optionsHost.navigateToDetail(ConnectionFragment()) }
        view.findViewById<View>(R.id.rowAudioRouting).setOnClickListener { optionsHost.navigateToDetail(AudioRoutingFragment()) }
        view.findViewById<View>(R.id.rowTtsLabels).setOnClickListener { optionsHost.navigateToDetail(TtsLabelsFragment()) }
        view.findViewById<View>(R.id.rowInfo).setOnClickListener { optionsHost.navigateToDetail(InfoFragment()) }
    }

    override fun onResume() {
        super.onResume()
        refreshRows()
    }

    private fun refreshRows() {
        val ctx = requireContext()
        val view = view ?: return

        view.findViewById<TextView>(R.id.tvRowProfileStatus).text = mappingStorage.getActiveProfileName()

        val tapMs = mappingStorage.getMultiTapWindowMs()
        val tapDesc = when {
            tapMs <= 300L -> "Sportivo"
            tapMs <= 450L -> "Standard"
            tapMs <= 600L -> "Guanti"
            else -> "Personalizzato"
        }
        view.findViewById<TextView>(R.id.tvRowTapRhythmStatus).text = "$tapMs ms · $tapDesc"

        // Connessione & Automazione: primo permesso mancante tra Accessibilità/Overlay/Doze,
        // "Tutto configurato" se sono tutti a posto.
        val accessibilityOk = Br80AccessibilityService.isRunning()
        val overlayOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(ctx)
        val dozeOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            (ctx.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager)?.isIgnoringBatteryOptimizations(ctx.packageName) == true
        val dotConnection = view.findViewById<View>(R.id.dotConnection)
        val tvConnectionStatus = view.findViewById<TextView>(R.id.tvRowConnectionStatus)
        when {
            !accessibilityOk -> {
                dotConnection.setBackgroundColor(ContextCompat.getColor(ctx, R.color.status_warning))
                tvConnectionStatus.text = "Accessibilità da attivare"
            }
            !overlayOk -> {
                dotConnection.setBackgroundColor(ContextCompat.getColor(ctx, R.color.status_warning))
                tvConnectionStatus.text = "Permesso overlay da attivare"
            }
            !dozeOk -> {
                dotConnection.setBackgroundColor(ContextCompat.getColor(ctx, R.color.status_warning))
                tvConnectionStatus.text = "Ottimizzazione batteria da escludere"
            }
            else -> {
                dotConnection.setBackgroundColor(ContextCompat.getColor(ctx, R.color.status_success))
                tvConnectionStatus.text = "Tutto configurato ✓"
            }
        }

        val dotAudio = view.findViewById<View>(R.id.dotAudioRouting)
        val tvAudioStatus = view.findViewById<TextView>(R.id.tvRowAudioRoutingStatus)
        val audioDevices = mappingStorage.getAudioBtDevices()
        if (mappingStorage.isAudioBtRoutingEnabled() && audioDevices.isNotEmpty()) {
            dotAudio.setBackgroundColor(ContextCompat.getColor(ctx, R.color.status_success))
            tvAudioStatus.text = "Attivo · ${audioDevices.size} dispositiv${if (audioDevices.size == 1) "o" else "i"}"
        } else {
            dotAudio.setBackgroundColor(ContextCompat.getColor(ctx, R.color.cockpit_muted_dim))
            tvAudioStatus.text = "Non configurato"
        }

        view.findViewById<TextView>(R.id.tvRowInfoStatus).text = "v${com.br80.remote.BuildConfig.VERSION_NAME}"
    }
}
