package com.br80.remote

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews

/** Widget home screen: stato connessione/batteria/audio interfono, profilo attivo
 * (tap per ciclare), riconnetti, esci. Aggiornato via push da [BleForegroundService]
 * ad ogni cambio di stato, non dal refresh periodico di sistema (troppo lento). */
class Br80WidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_CYCLE_PROFILE) {
            cycleProfile(context)
            updateAllWidgets(context)
            return
        }
        super.onReceive(context, intent)
    }

    private fun cycleProfile(context: Context) {
        val mappingStorage = MappingStorage.getInstance(context)
        val profiles = mappingStorage.getProfileNames()
        if (profiles.isEmpty()) return
        val currentIndex = profiles.indexOf(mappingStorage.getActiveProfileName()).let { if (it < 0) 0 else it }
        val next = profiles[(currentIndex + 1) % profiles.size]
        mappingStorage.setActiveProfileName(next)
    }

    private fun buildViews(context: Context): RemoteViews {
        val mappingStorage = MappingStorage.getInstance(context)
        val views = RemoteViews(context.packageName, R.layout.widget_br80)

        val connected = BleServiceStateHolder.currentState == BleGattManager.ConnectionState.CONNECTED
        views.setTextViewText(R.id.tvWidgetStatus, if (connected) "Connesso" else "In attesa")
        views.setImageViewResource(
            R.id.viewWidgetStatusDot,
            if (connected) R.drawable.widget_dot_success else R.drawable.widget_dot_muted
        )

        val battery = BleServiceStateHolder.batteryLevel
        views.setTextViewText(R.id.tvWidgetBattery, if (battery >= 0) "Batteria: $battery%" else "Batteria: --")

        val audioConnected = ScoAudioGateway.isTargetAudioDeviceConnected(context, mappingStorage)
        views.setTextViewText(R.id.tvWidgetAudioDevice, if (audioConnected) "Interfono connesso" else "Interfono non connesso")

        views.setTextViewText(R.id.btnWidgetProfile, mappingStorage.getActiveProfileName())

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

        // Etichetta/azione dipendono dallo stato, come il toggle già presente nella notifica:
        // "Connetti" quando serve avviare la connessione, "Disconnetti" quando è già attiva.
        // Prima era sempre "Riconnetti" con un'unica azione (ACTION_CONNECT), poco chiaro
        // quando la connessione era già up.
        views.setTextViewText(R.id.btnWidgetReconnect, if (connected) "Disconnetti" else "Connetti")
        val toggleIntent = Intent(context, BleForegroundService::class.java).apply {
            action = if (connected) BleForegroundService.ACTION_DISCONNECT else BleForegroundService.ACTION_CONNECT
        }
        views.setOnClickPendingIntent(R.id.btnWidgetReconnect, PendingIntent.getService(context, 10, toggleIntent, flags))

        // "Esci" apre un'attività di conferma invece di fermare subito il servizio: un widget
        // non può mostrare un dialog direttamente, e getActivity (a differenza di getService)
        // funziona anche quando il servizio non è già vivo in foreground.
        val exitConfirmIntent = Intent(context, Br80WidgetExitConfirmActivity::class.java)
        views.setOnClickPendingIntent(R.id.btnWidgetExit, PendingIntent.getActivity(context, 11, exitConfirmIntent, flags))

        val cycleIntent = Intent(context, Br80WidgetProvider::class.java).apply { action = ACTION_CYCLE_PROFILE }
        views.setOnClickPendingIntent(R.id.btnWidgetProfile, PendingIntent.getBroadcast(context, 12, cycleIntent, flags))

        return views
    }

    companion object {
        const val ACTION_CYCLE_PROFILE = "com.br80.remote.widget.ACTION_CYCLE_PROFILE"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, Br80WidgetProvider::class.java))
            if (ids.isEmpty()) return
            val provider = Br80WidgetProvider()
            for (id in ids) {
                manager.updateAppWidget(id, provider.buildViews(context))
            }
        }
    }
}

/** Ultimo stato connessione/batteria noto, aggiornato da [BleForegroundService] ad ogni
 * callback: il widget vive in un altro ciclo di vita (nessuna istanza viva del service
 * garantita nel momento in cui viene ridisegnato) e legge questi valori invece di dover
 * bindare il service solo per un aggiornamento grafico. */
object BleServiceStateHolder {
    var currentState: BleGattManager.ConnectionState = BleGattManager.ConnectionState.DISCONNECTED
    var batteryLevel: Int = -1
}
