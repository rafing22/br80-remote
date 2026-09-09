package com.br80.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED || intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val mappingStorage = MappingStorage.getInstance(context)

            // Registra lo scan in background anche se l'auto-avvio al boot è disattivato: senza
            // questo, dopo un riavvio il popup "telecomando rilevato" ad app chiusa resterebbe
            // inattivo finché l'utente non apre l'app almeno una volta (che è esattamente il
            // caso che questo popup vuole evitare).
            Br80BackgroundScanManager.ensureRegistered(context)

            if (mappingStorage.isAutoStartOnBootEnabled()) {
                val serviceIntent = Intent(context, BleForegroundService::class.java).apply {
                    putExtra(BleForegroundService.EXTRA_CONNECT_NOW, true)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
