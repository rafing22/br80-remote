package com.br80.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class BleForegroundService : Service(), BleGattManager.BleGattListener {

    private val binder = LocalBinder()
    var listener: BleServiceListener? = null

    lateinit var mappingStorage: MappingStorage
        private set
    lateinit var gattManager: BleGattManager
        private set
    lateinit var gestureDetector: GestureDetector
        private set
    lateinit var actionExecutor: ActionExecutor
        private set

    val currentState: BleGattManager.ConnectionState
        get() = gattManager.currentState

    val batteryLevel: Int
        get() = gattManager.batteryLevel

    interface BleServiceListener {
        fun onStateChanged(state: BleGattManager.ConnectionState)
        fun onGestureExecuted(button: Br80Button, gesture: GestureType)
        fun onBatteryUpdated(level: Int)
        fun onLog(message: String)
    }

    inner class LocalBinder : Binder() {
        fun getService(): BleForegroundService = this@BleForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        mappingStorage = MappingStorage(this)
        actionExecutor = ActionExecutor(this, mappingStorage) { logMsg ->
            listener?.onLog(logMsg)
        }
        gestureDetector = GestureDetector(mappingStorage) { button, gesture ->
            actionExecutor.execute(button, gesture, batteryLevel)
            listener?.onGestureExecuted(button, gesture)
        }
        gattManager = BleGattManager(this, mappingStorage, this)

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Servizio BR80 attivo in background")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Se avviato con richiesta di connect
        if (intent?.getBooleanExtra(EXTRA_CONNECT_NOW, false) == true) {
            connectDevice()
        }

        return START_STICKY
    }

    fun connectDevice() {
        gattManager.connect()
    }

    fun disconnectDevice() {
        gattManager.disconnect()
        gestureDetector.reset()
        updateNotification("Disconnesso")
    }

    // Callbacks da BleGattManager
    override fun onStateChanged(state: BleGattManager.ConnectionState) {
        val statusText = when (state) {
            BleGattManager.ConnectionState.DISCONNECTED -> "Disconnesso"
            BleGattManager.ConnectionState.CONNECTING -> "Connessione in corso..."
            BleGattManager.ConnectionState.CONNECTED -> {
                val battStr = if (batteryLevel >= 0) " (Batt: $batteryLevel%)" else ""
                "Connesso al telecomando BR80$battStr"
            }
        }
        updateNotification(statusText)
        listener?.onStateChanged(state)
    }

    override fun onButtonRawEvent(button: Br80Button, isPress: Boolean) {
        gestureDetector.onButtonRawEvent(button, isPress)
    }

    override fun onBatteryUpdated(level: Int) {
        if (currentState == BleGattManager.ConnectionState.CONNECTED) {
            updateNotification("Connesso al telecomando BR80 (Batt: $level%)")
        }
        listener?.onBatteryUpdated(level)
    }

    override fun onLog(message: String) {
        listener?.onLog(message)
    }

    private fun createNotification(contentText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Livall BR80 Remote")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, createNotification(contentText))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BR80 Remote Foreground Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene la connessione attiva con il telecomando BR80 in background"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gattManager.disconnect()
        gestureDetector.reset()
    }

    companion object {
        const val CHANNEL_ID = "br80_ble_service_channel"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_CONNECT_NOW = "extra_connect_now"
    }
}
