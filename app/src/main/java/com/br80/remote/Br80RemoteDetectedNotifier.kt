package com.br80.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object Br80RemoteDetectedNotifier {

    const val HEADSUP_CHANNEL_ID = "br80_remote_detected_channel"
    private const val HEADSUP_NOTIFICATION_ID = 1002 // distinto da 1001 (notifica ongoing del service)

    fun showHeadsUpNotification(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                HEADSUP_CHANNEL_ID,
                "Telecomando BR80 rilevato",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avvisa quando il telecomando viene rilevato ad app chiusa"
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.createNotificationChannel(channel)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

        // Tap → avvia direttamente ACTION_CONNECT sul service, come il tasto "Connetti" già
        // esistente nella notifica ongoing, invece di aprire MainActivity: riconnessione più
        // rapida, nessuna UI necessaria solo per riconnettersi.
        val connectIntent = Intent(context, BleForegroundService::class.java).apply {
            action = BleForegroundService.ACTION_CONNECT
        }
        val pendingConnect = PendingIntent.getService(context, 20, connectIntent, flags)

        val notification = NotificationCompat.Builder(context, HEADSUP_CHANNEL_ID)
            .setContentTitle("Livall BR80 Remote")
            .setContentText("Telecomando rilevato. Tocca per connettere.")
            .setSmallIcon(R.drawable.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingConnect)
            .setAutoCancel(true)
            .setTimeoutAfter(15_000L)
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
            ?.notify(HEADSUP_NOTIFICATION_ID, notification)
    }
}
