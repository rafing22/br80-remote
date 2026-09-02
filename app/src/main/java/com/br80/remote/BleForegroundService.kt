package com.br80.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class BleForegroundService : Service() {

    private val binder = LocalBinder()
    var listener: BleServiceListener? = null

    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }
    var currentState: ConnectionState = ConnectionState.DISCONNECTED
        private set

    var batteryLevel: Int = -1
        private set

    interface BleServiceListener {
        fun onStateChanged(state: ConnectionState)
        fun onButtonEvent(button: String, gesture: String)
        fun onBatteryUpdated(level: Int)
    }

    inner class LocalBinder : Binder() {
        fun getService(): BleForegroundService = this@BleForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Servizio in esecuzione in background")
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    fun connectDevice(macAddress: String? = null) {
        updateState(ConnectionState.CONNECTING)
        updateNotification("Connessione al BR80 in corso...")
        
        // TODO: Inserire qui il codice BLE di scan e connessione GATT
    }

    fun disconnectDevice() {
        updateState(ConnectionState.DISCONNECTED)
        updateNotification("Disconnesso")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateState(newState: ConnectionState) {
        currentState = newState
        listener?.onStateChanged(newState)
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BR80 Remote")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(contentText))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BR80 Remote Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "br80_ble_service_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
