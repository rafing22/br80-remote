package com.br80.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED || intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val mappingStorage = MappingStorage.getInstance(context)
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
