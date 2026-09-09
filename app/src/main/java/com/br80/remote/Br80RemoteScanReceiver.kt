package com.br80.remote

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.IntentCompat

class Br80RemoteScanReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        intent ?: return

        // Processo già vivo, il service sta già gestendo la propria riconnessione (il nostro
        // stesso connectGatt() genera anche l'ACL per lo stesso MAC che questo scan rileva):
        // niente da fare, evita di spammare la notifica/riavviare la connessione mentre l'app
        // ci sta già pensando.
        if (BleServiceStateHolder.isServiceRunning) return

        val results = IntentCompat.getParcelableArrayListExtra(
            intent, BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, ScanResult::class.java
        ) ?: return
        val lastMac = MappingStorage.getInstance(context).getLastConnectedMac() ?: return
        val matches = results.any { it.device.address.equals(lastMac, ignoreCase = true) }
        if (!matches) return

        // Avvia subito la connessione (invece di aspettare un tap sulla notifica): la notifica
        // resta solo come conferma/apertura app, non più come trigger della connessione.
        val connectIntent = Intent(context, BleForegroundService::class.java).apply {
            action = BleForegroundService.ACTION_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(connectIntent)
        } else {
            context.startService(connectIntent)
        }

        Br80RemoteDetectedNotifier.showHeadsUpNotification(context)
    }
}
