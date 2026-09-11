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

        // Difesa in profondità: lo scan potrebbe essere rimasto armato da prima che l'app
        // venisse disattivata esplicitamente (applyDisabledState lo ferma di norma subito, ma
        // un rilevamento già in coda potrebbe comunque arrivare qui).
        if (MappingStorage.getInstance(context).isAppDisabled()) return

        // Processo già vivo, il service sta già gestendo la propria riconnessione (il nostro
        // stesso connectGatt() genera anche l'ACL per lo stesso MAC che questo scan rileva):
        // niente da fare, evita di spammare la notifica/riavviare la connessione mentre l'app
        // ci sta già pensando.
        if (BleServiceStateHolder.isServiceRunning) return

        // Subito dopo un'uscita esplicita (Esci) il BR80 riparte quasi sempre con l'advertising
        // per via della disconnessione stessa, non di una pressione reale: ignora per una breve
        // finestra per non riconnettersi da solo un istante dopo che l'utente ha chiuso l'app.
        if (System.currentTimeMillis() < BleServiceStateHolder.suppressAutoConnectUntil) return

        val results = IntentCompat.getParcelableArrayListExtra(
            intent, BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, ScanResult::class.java
        ) ?: return
        val lastMac = MappingStorage.getInstance(context).getLastConnectedMac() ?: return
        val matches = results.any { it.device.address.equals(lastMac, ignoreCase = true) }
        if (!matches) return

        // Impostato SUBITO, prima ancora di avviare il service: l'avvio di un service a freddo
        // richiede un istante (onCreate() gira in modo asincrono), e in quella finestra possono
        // arrivare altri rilevamenti dallo stesso scan che troverebbero ancora
        // isServiceRunning=false, riavviando la connessione e la notifica più volte di seguito
        // (osservato dal vivo: la notifica "ricompariva" ripetutamente).
        BleServiceStateHolder.suppressAutoConnectUntil = System.currentTimeMillis() + 10_000L

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
