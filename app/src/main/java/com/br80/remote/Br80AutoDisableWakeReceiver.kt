package com.br80.remote

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.IntentCompat

/**
 * Riattiva l'app quando il dispositivo BT scelto per la "Disattivazione automatica" si
 * ricollega, anche ad app/servizio completamente spenti. Necessario perché
 * `BtDeviceMonitor` (che normalmente rileva questo evento) vive dentro `BleForegroundService`:
 * quando quel servizio viene fermato dalla disattivazione stessa, muore con lui anche
 * l'unico componente in grado di accorgersi della riconnessione — senza questo receiver
 * l'app restava disattivata per sempre, anche dopo un riavvio manuale del servizio (bug
 * confermato dal vivo: `isAppDisabled` restava `true` per sempre, con la conseguenza che una
 * successiva disconnessione del dispositivo diventava un no-op in `applyDisabledState`,
 * lasciando servizio/scan attivi indisturbati).
 *
 * `ACTION_ACL_CONNECTED` è una delle eccezioni Bluetooth esenti dalle restrizioni sui
 * broadcast impliciti di Android 8+ (continua a essere consegnata anche a processo killato),
 * già usata con lo stesso presupposto altrove nel progetto. A differenza del caso del BR80
 * (dove ACL_CONNECTED non scatta mai perché il telecomando non forma mai un bond/ACL da solo),
 * qui è il segnale giusto: il dispositivo audio scelto (es. interfono/auto) forma il proprio
 * ACL in autonomia quando torna alla portata, senza bisogno che l'app faccia nulla.
 *
 * Usato solo come "sveglia": la verifica precisa dello stato (profilo A2DP/HFP negoziato,
 * non solo link radio formato) resta a `BtDeviceMonitor`/`BtProfileConnectionChecker` una
 * volta che il servizio è di nuovo vivo — stesso principio già in uso per il Keep-Alive
 * condizionale.
 */
class Br80AutoDisableWakeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != BluetoothDevice.ACTION_ACL_CONNECTED) return

        val mappingStorage = MappingStorage.getInstance(context)
        if (!mappingStorage.isAutoDisableBtEnabled()) return
        if (!mappingStorage.isAppDisabled()) return // niente da riattivare

        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            ?: return
        val targetMacs = mappingStorage.getAutoDisableBtDevices().map { it.first }
        if (targetMacs.none { it.equals(device.address, ignoreCase = true) }) return

        Log.d("Br80AutoDisableWake", "Riattivo l'app: dispositivo ${device.address} riconnesso.")

        BleForegroundService.applyDisabledState(context, false)

        val connectIntent = Intent(context, BleForegroundService::class.java).apply {
            action = BleForegroundService.ACTION_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(connectIntent)
        } else {
            context.startService(connectIntent)
        }
    }
}
