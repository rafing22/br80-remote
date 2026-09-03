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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class BleForegroundService : Service(), BleGattManager.BleGattListener, BtDeviceMonitor.BtDeviceMonitorListener {

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
    lateinit var ttsFeedbackManager: TtsFeedbackManager
        private set
    lateinit var btDeviceMonitor: BtDeviceMonitor
        private set

    val currentState: BleGattManager.ConnectionState
        get() = gattManager.currentState

    val batteryLevel: Int
        get() = gattManager.batteryLevel

    interface BleServiceListener {
        fun onStateChanged(state: BleGattManager.ConnectionState)
        fun onButtonRawEvent(button: Br80Button, isPress: Boolean)
        fun onGestureExecuted(button: Br80Button, gesture: GestureType)
        fun onBatteryUpdated(level: Int)
        fun onLog(message: String)
    }

    inner class LocalBinder : Binder() {
        fun getService(): BleForegroundService = this@BleForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private var cpuWakeLock: PowerManager.WakeLock? = null

    private fun acquireWakeLock(timeoutMs: Long = 3000L) {
        try {
            if (cpuWakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                cpuWakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "br80:service_wakelock")
                cpuWakeLock?.setReferenceCounted(false)
            }
            cpuWakeLock?.acquire(timeoutMs)
        } catch (e: Exception) {
            // Ignora eccezioni di acquisizione
        }
    }

    override fun onCreate() {
        super.onCreate()
        mappingStorage = MappingStorage(this)
        ttsFeedbackManager = TtsFeedbackManager(this, mappingStorage)
        actionExecutor = ActionExecutor(this, mappingStorage, ttsFeedbackManager) { logMsg ->
            listener?.onLog(logMsg)
        }
        gestureDetector = GestureDetector(mappingStorage) { button, gesture ->
            acquireWakeLock(3000L)
            actionExecutor.execute(button, gesture, batteryLevel)
            listener?.onGestureExecuted(button, gesture)
        }
        gattManager = BleGattManager(this, mappingStorage, this)
        btDeviceMonitor = BtDeviceMonitor(this, mappingStorage, this)
        btDeviceMonitor.startMonitoring()

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                connectDevice()
            }
            ACTION_DISCONNECT -> {
                disconnectDevice()
            }
            ACTION_STOP_SERVICE -> {
                stopServiceCompletely()
                return START_NOT_STICKY
            }
            else -> {
                val notification = createNotification(getNotificationContentText())
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

                if (intent?.getBooleanExtra(EXTRA_CONNECT_NOW, false) == true) {
                    if (!mappingStorage.isConditionalBtEnabled() || btDeviceMonitor.isTargetCurrentlyConnected()) {
                        connectDevice()
                    } else {
                        onLog("Keep-Alive condizionale attivo: in attesa della connessione del dispositivo BT target.")
                    }
                }
            }
        }

        return START_STICKY
    }

    fun connectDevice() {
        gattManager.connect()
    }

    fun disconnectDevice() {
        gattManager.disconnect()
        gestureDetector.reset()
        updateNotification()
    }

    fun stopServiceCompletely() {
        gattManager.disconnect(enterPassiveListening = false)
        gestureDetector.reset()
        btDeviceMonitor.stopMonitoring()
        ttsFeedbackManager.shutdown()
        try {
            if (cpuWakeLock?.isHeld == true) {
                cpuWakeLock?.release()
            }
        } catch (e: Exception) {
            // Ignora
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // Callbacks da BtDeviceMonitor
    override fun onTargetDeviceConnectionChanged(isConnected: Boolean, deviceName: String?) {
        val name = deviceName ?: "Interfono/Casco"
        if (isConnected) {
            onLog("Dispositivo BT Target ($name) connesso! Attivo Keep-Alive e ascolto reattivo...")
            connectDevice()
        } else {
            onLog("Dispositivo BT Target ($name) disconnesso. Arresto Keep-Alive e ascolto reattivo.")
            if (mappingStorage.isConditionalBtEnabled()) {
                disconnectDevice()
            }
        }
    }

    override fun onBluetoothStateChanged(isBtOn: Boolean) {
        if (isBtOn && !mappingStorage.getLastConnectedMac().isNullOrEmpty()) {
            if (!mappingStorage.isConditionalBtEnabled() || btDeviceMonitor.isTargetCurrentlyConnected()) {
                onLog("Bluetooth riattivato sul telefono. Riavvio ascolto automatico del telecomando...")
                connectDevice()
            } else {
                onLog("Bluetooth riattivato: in attesa della connessione del dispositivo BT target per il Keep-Alive condizionale.")
            }
        }
    }

    // Callbacks da BleGattManager
    override fun onStateChanged(state: BleGattManager.ConnectionState) {
        updateNotification()
        listener?.onStateChanged(state)
    }

    override fun onButtonRawEvent(button: Br80Button, isPress: Boolean) {
        acquireWakeLock(3000L)
        listener?.onButtonRawEvent(button, isPress)
        gestureDetector.onButtonRawEvent(button, isPress)
    }

    override fun onBatteryUpdated(level: Int) {
        updateNotification()
        listener?.onBatteryUpdated(level)
    }

    override fun onLog(message: String) {
        listener?.onLog(message)
    }

    private fun getNotificationContentText(): String {
        return when (currentState) {
            BleGattManager.ConnectionState.DISCONNECTED -> "Stato: Disconnesso (In ascolto)"
            BleGattManager.ConnectionState.CONNECTING -> "Stato: Connessione in corso..."
            BleGattManager.ConnectionState.CONNECTED -> {
                val battStr = if (batteryLevel >= 0) " • Batteria: $batteryLevel%" else ""
                "Stato: Connesso$battStr"
            }
        }
    }

    private fun createNotification(contentText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

        val pendingOpen = PendingIntent.getActivity(this, 0, openIntent, flags)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Livall BR80 Remote")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        // Pulsanti d'azione notifica
        if (currentState == BleGattManager.ConnectionState.DISCONNECTED) {
            val connectIntent = Intent(this, BleForegroundService::class.java).apply {
                action = ACTION_CONNECT
            }
            val pendingConnect = PendingIntent.getService(this, 1, connectIntent, flags)
            builder.addAction(android.R.drawable.ic_media_play, "Connetti", pendingConnect)
        } else {
            val disconnectIntent = Intent(this, BleForegroundService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            val pendingDisconnect = PendingIntent.getService(this, 2, disconnectIntent, flags)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnetti", pendingDisconnect)
        }

        // Pulsante Esci definitivo
        val stopIntent = Intent(this, BleForegroundService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pendingStop = PendingIntent.getService(this, 3, stopIntent, flags)
        builder.addAction(android.R.drawable.ic_lock_power_off, "Esci", pendingStop)

        return builder.build()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, createNotification(getNotificationContentText()))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Livall BR80 Remote Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene attiva la connessione con il telecomando BR80 in background"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gattManager.disconnect(enterPassiveListening = false)
        gestureDetector.reset()
        btDeviceMonitor.stopMonitoring()
        ttsFeedbackManager.shutdown()
        try {
            if (cpuWakeLock?.isHeld == true) {
                cpuWakeLock?.release()
            }
        } catch (e: Exception) {
            // Ignora eccezioni
        }
    }

    companion object {
        const val CHANNEL_ID = "br80_ble_service_channel"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_CONNECT_NOW = "extra_connect_now"
        const val ACTION_CONNECT = "com.br80.remote.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.br80.remote.ACTION_DISCONNECT"
        const val ACTION_STOP_SERVICE = "com.br80.remote.ACTION_STOP_SERVICE"
    }
}
