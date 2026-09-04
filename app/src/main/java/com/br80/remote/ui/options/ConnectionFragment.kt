package com.br80.remote.ui.options

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.br80.remote.Br80AccessibilityService
import com.br80.remote.R

class ConnectionFragment : OptionsDetailFragment(R.layout.fragment_option_connection, "Connessione & Automazione") {

    private lateinit var cbOptBoot: CheckBox
    private lateinit var cbOptKeepAlive: CheckBox
    private lateinit var cbOptConditionalBt: CheckBox
    private lateinit var tvConditionalBtDevice: TextView
    private lateinit var btnOptChooseBtDevice: Button
    private lateinit var btnOptDoze: Button
    private lateinit var btnOptOverlay: Button
    private lateinit var btnOptAccessibility: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cbOptBoot = view.findViewById(R.id.cbOptBoot)
        cbOptKeepAlive = view.findViewById(R.id.cbOptKeepAlive)
        cbOptConditionalBt = view.findViewById(R.id.cbOptConditionalBt)
        tvConditionalBtDevice = view.findViewById(R.id.tvConditionalBtDevice)
        btnOptChooseBtDevice = view.findViewById(R.id.btnOptChooseBtDevice)
        btnOptDoze = view.findViewById(R.id.btnOptDoze)
        btnOptOverlay = view.findViewById(R.id.btnOptOverlay)
        btnOptAccessibility = view.findViewById(R.id.btnOptAccessibility)

        cbOptBoot.isChecked = mappingStorage.isAutoStartOnBootEnabled()
        cbOptKeepAlive.isChecked = mappingStorage.isKeepAliveEnabled()
        cbOptConditionalBt.isChecked = mappingStorage.isConditionalBtEnabled()
        updateConditionalBtDeviceLabel()

        cbOptBoot.setOnCheckedChangeListener { _, isChecked ->
            mappingStorage.setAutoStartOnBootEnabled(isChecked)
            host.appendLog("Avvio automatico al boot: " + if (isChecked) "ATTIVO" else "DISATTIVATO")
        }

        cbOptKeepAlive.setOnCheckedChangeListener { _, isChecked ->
            mappingStorage.setKeepAliveEnabled(isChecked)
            if (isChecked && cbOptConditionalBt.isChecked) {
                cbOptConditionalBt.isChecked = false
                mappingStorage.setConditionalBtEnabled(false)
                host.appendLog("Keep-Alive condizionale disattivato: incompatibile con Keep-Alive Always-On.")
            }
            host.bleService?.gattManager?.startKeepAliveIfEnabled()
            host.appendLog("Keep-Alive impostato a: " + if (isChecked) "ATTIVO (Ping ogni 35s)" else "DISATTIVATO")
        }

        cbOptConditionalBt.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && mappingStorage.getConditionalBtDevices().isEmpty()) {
                Toast.makeText(requireContext(), "Seleziona prima almeno un dispositivo BT dall'elenco qui sotto.", Toast.LENGTH_LONG).show()
                cbOptConditionalBt.isChecked = false
                return@setOnCheckedChangeListener
            }
            mappingStorage.setConditionalBtEnabled(isChecked)
            if (isChecked && cbOptKeepAlive.isChecked) {
                cbOptKeepAlive.isChecked = false
                mappingStorage.setKeepAliveEnabled(false)
                host.appendLog("Keep-Alive Always-On disattivato: incompatibile con Keep-Alive condizionale.")
            }
            host.appendLog("Keep-Alive condizionale a dispositivo BT: " + if (isChecked) "ATTIVO" else "DISATTIVATO")
        }

        btnOptChooseBtDevice.setOnClickListener {
            showBondedDeviceMultiPickerDialog(requireContext(), mappingStorage.getConditionalBtDevices()) { selected ->
                mappingStorage.setConditionalBtDevices(selected)
                updateConditionalBtDeviceLabel()
                host.appendLog("Dispositivi BT condizionali impostati: ${selected.joinToString(", ") { it.second }}")
            }
        }

        btnOptDoze.setOnClickListener { requestIgnoreBatteryOptimization() }
        btnOptOverlay.setOnClickListener { requestOverlayPermission() }
        btnOptAccessibility.setOnClickListener { requestAccessibilityPermission() }
    }

    override fun onResume() {
        super.onResume()
        updateBatteryOptButtonState()
        updateOverlayButtonState()
        updateAccessibilityButtonState()
    }

    private fun updateConditionalBtDeviceLabel() {
        val devices = mappingStorage.getConditionalBtDevices()
        tvConditionalBtDevice.text = if (devices.isEmpty()) {
            "Nessun dispositivo selezionato"
        } else {
            "Dispositivi selezionati: " + devices.joinToString(", ") { it.second }
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val ctx = requireContext()
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm?.isIgnoringBatteryOptimizations(ctx.packageName) == false) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    host.appendLog("Impossibile aprire impostazioni Doze: ${e.message}")
                }
            }
        }
    }

    private fun updateBatteryOptButtonState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val ctx = requireContext()
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isIgnoring = pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true
            if (isIgnoring) {
                btnOptDoze.text = "Doze: Escluso con Successo ✓"
                btnOptDoze.isEnabled = false
                btnOptDoze.backgroundTintList = toColorStateList(ContextCompat.getColor(ctx, R.color.status_success))
            } else {
                btnOptDoze.text = "Disattiva Ottimizzazione Batteria (Doze)"
                btnOptDoze.isEnabled = true
                btnOptDoze.backgroundTintList = toColorStateList(ContextCompat.getColor(ctx, R.color.status_warning))
            }
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(requireContext())) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:${requireContext().packageName}")
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    host.appendLog("Impossibile aprire impostazioni overlay: ${e.message}")
                }
            }
        }
    }

    private fun updateOverlayButtonState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val ctx = requireContext()
            val canDraw = Settings.canDrawOverlays(ctx)
            if (canDraw) {
                btnOptOverlay.text = "Avvio su Altre App: Autorizzato ✓"
                btnOptOverlay.isEnabled = false
                btnOptOverlay.backgroundTintList = toColorStateList(ContextCompat.getColor(ctx, R.color.status_success))
            } else {
                btnOptOverlay.text = "Consenti Avvio su Altre App (Gemini / Mappe)"
                btnOptOverlay.isEnabled = true
                btnOptOverlay.backgroundTintList = toColorStateList(ContextCompat.getColor(ctx, R.color.status_warning))
            }
        }
    }

    private fun updateAccessibilityButtonState() {
        val ctx = requireContext()
        if (Br80AccessibilityService.isRunning()) {
            btnOptAccessibility.text = "Servizio Accessibilità: Attivo ✓"
            btnOptAccessibility.isEnabled = false
            btnOptAccessibility.backgroundTintList = toColorStateList(ContextCompat.getColor(ctx, R.color.status_success))
        } else {
            btnOptAccessibility.text = "Attiva Servizio Accessibilità (Indietro / Home / Blocca Schermo)"
            btnOptAccessibility.isEnabled = true
            btnOptAccessibility.backgroundTintList = toColorStateList(ContextCompat.getColor(ctx, R.color.status_warning))
        }
    }

    private fun requestAccessibilityPermission() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            host.appendLog("Impossibile aprire impostazioni accessibilità: ${e.message}")
        }
    }

    private fun toColorStateList(color: Int) = ColorStateList.valueOf(color)
}
