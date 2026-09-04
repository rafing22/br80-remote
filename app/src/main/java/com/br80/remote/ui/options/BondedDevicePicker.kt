package com.br80.remote.ui.options

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.br80.remote.R

/** Selezione multipla tra i dispositivi Bluetooth accoppiati: condivisa tra
 * ConnectionFragment (dispositivo Keep-Alive condizionale) e AudioRoutingFragment
 * (dispositivo audio per TTS/comandi vocali) — stessa dialog, target diverso. */
@SuppressLint("MissingPermission")
fun showBondedDeviceMultiPickerDialog(
    context: Context,
    currentSelection: Set<Pair<String, String>>,
    onSelectionConfirmed: (Set<Pair<String, String>>) -> Unit
) {
    val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    if (!hasPermission) {
        Toast.makeText(context, "Permesso Bluetooth mancante: concedilo e riprova.", Toast.LENGTH_LONG).show()
        return
    }

    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = bluetoothManager?.adapter
    val bondedDevices = adapter?.bondedDevices?.toList() ?: emptyList()

    if (bondedDevices.isEmpty()) {
        Toast.makeText(context, "Nessun dispositivo Bluetooth accoppiato trovato. Accoppia prima l'interfono/casco nelle impostazioni di sistema.", Toast.LENGTH_LONG).show()
        return
    }

    val currentMacs = currentSelection.map { it.first }
    val labels = bondedDevices.map { "${it.name ?: "Sconosciuto"} [${it.address}]" }.toTypedArray()
    val checkedItems = bondedDevices.map { device -> currentMacs.any { it.equals(device.address, ignoreCase = true) } }.toBooleanArray()

    AlertDialog.Builder(context, R.style.Theme_Br80_CockpitDialog)
        .setTitle("Scegli Dispositivi BT (selezione multipla)")
        .setMultiChoiceItems(labels, checkedItems) { _, which, isChecked ->
            checkedItems[which] = isChecked
        }
        .setPositiveButton("Conferma") { _, _ ->
            val selected = bondedDevices.filterIndexed { index, _ -> checkedItems[index] }
                .map { it.address to (it.name ?: "Sconosciuto") }
                .toSet()
            onSelectionConfirmed(selected)
        }
        .setNegativeButton("Annulla", null)
        .show()
}
