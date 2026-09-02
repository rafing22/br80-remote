package com.br80.remote

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.UUID

class BleForegroundService : Service() {

    private val tag = "BR80Service"

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
        fun onLog(message: String)
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

    private fun log(msg: String) {
        Log.d(tag, msg)
        listener?.onLog(msg)
    }

    // ---- Profilo BR80 (validato sul device reale, build #5) ----

    private val serviceUuid = UUID.fromString("0000a2a0-0000-1000-8000-00805f9b34fb")
    private val wakeUuid = UUID.fromString("0000a2a3-0000-1000-8000-00805f9b34fb")
    private val buttonUuid = UUID.fromString("0000a2a4-0000-1000-8000-00805f9b34fb")
    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val maxWakeRetries = 5
    private val wakeRetryDelayMillis = 1000L
    private val connectDelayMillis = 500L
    private var wakeRetries = 0
    private val handler = Handler(Looper.getMainLooper())

    private val buttonNames = mapOf(
        6 to "UP press", 38 to "UP release",
        5 to "DOWN press", 37 to "DOWN release",
        7 to "LEFT press", 39 to "LEFT release",
        8 to "RIGHT press", 40 to "RIGHT release",
        9 to "HOME press", 41 to "HOME release",
        2 to "CAMERA press", 34 to "CAMERA release",
        29 to "CALL press", 45 to "CALL release"
    )

    private var bluetoothGatt: BluetoothGatt? = null
    private var userRequestedDisconnect = false

    @SuppressLint("MissingPermission")
    fun connectDevice(macAddress: String? = null) {
        userRequestedDisconnect = false
        updateState(ConnectionState.CONNECTING)
        updateNotification("Connessione al BR80 in corso...")
        closeExistingGatt()

        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            log("Bluetooth non disponibile o spento.")
            updateState(ConnectionState.DISCONNECTED)
            return
        }
        val scanner = adapter.bluetoothLeScanner
        log("Scansione in corso, cerco BlingRemote (a2a0)...")

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name
                if (name == "BlingRemote") {
                    log("Trovato: $name (${result.device.address}), RSSI ${result.rssi}")
                    scanner.stopScan(this)
                    connectGattTo(result.device)
                }
            }
            override fun onScanFailed(errorCode: Int) {
                log("Scan fallito, codice $errorCode")
            }
        }
        scanner.startScan(callback)
    }

    @SuppressLint("MissingPermission")
    fun disconnectDevice() {
        userRequestedDisconnect = true
        bluetoothGatt?.disconnect()
        closeExistingGatt()
        updateState(ConnectionState.DISCONNECTED)
        updateNotification("Disconnesso")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @SuppressLint("MissingPermission")
    private fun closeExistingGatt() {
        bluetoothGatt?.let {
            log("Chiudo la connessione GATT precedente prima di riprovare...")
            it.close()
        }
        bluetoothGatt = null
    }

    @SuppressLint("MissingPermission")
    private fun connectGattTo(device: BluetoothDevice) {
        wakeRetries = 0
        handler.postDelayed({
            log("Connessione a ${device.address}...")
            bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }, connectDelayMillis)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            log("Connection state change: status=$status newState=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                updateState(ConnectionState.CONNECTED)
                updateNotification("Connesso")
                log("Connesso. Scopro i servizi...")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("Disconnesso.")
                gatt.close()
                if (bluetoothGatt === gatt) {
                    bluetoothGatt = null
                }
                updateState(ConnectionState.DISCONNECTED)
                updateNotification("Disconnesso")
                // Riconnessione automatica: implementata in una fase successiva (§2 del piano)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(serviceUuid)
            if (service == null) {
                log("Servizio a2a0 non trovato.")
                return
            }
            log("Servizio a2a0 trovato. Sveglio il device (write 0xFF su a2a3)...")
            val wakeChar = service.getCharacteristic(wakeUuid)
            if (wakeChar != null) {
                wakeRetries = 0
                writeWake(gatt, wakeChar)
            } else {
                log("Characteristic a2a3 non trovata.")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (characteristic.uuid == wakeUuid) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    log("Wake inviato correttamente (status $status). Abilito le notifiche su a2a4...")
                    val service = gatt.getService(serviceUuid)
                    val buttonChar = service?.getCharacteristic(buttonUuid)
                    if (buttonChar != null) {
                        enableNotify(gatt, buttonChar)
                    }
                } else if (wakeRetries < maxWakeRetries) {
                    wakeRetries++
                    log("Wake fallito (status $status). Riprovo tra 1s (tentativo $wakeRetries/$maxWakeRetries)...")
                    handler.postDelayed({
                        writeWake(gatt, characteristic)
                    }, wakeRetryDelayMillis)
                } else {
                    log("Wake fallito dopo $maxWakeRetries tentativi (status $status). Riprova a premere Connetti.")
                }
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == buttonUuid) {
                val value = characteristic.value
                if (value != null && value.isNotEmpty()) {
                    val code = value[0].toInt() and 0xFF
                    val label = buttonNames[code] ?: "sconosciuto"
                    log("a2a4 = $code (0x${code.toString(16)}) -> $label")
                    val parts = label.split(" ")
                    if (parts.size == 2) {
                        listener?.onButtonEvent(parts[0], parts[1].uppercase())
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeWake(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        val value = byteArrayOf(0xFF.toByte())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = value
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotify(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val cccd = characteristic.getDescriptor(cccdUuid)
        if (cccd != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(cccd)
            }
            log("Notifiche abilitate su a2a4. Premi un tasto sul BR80.")
        }
    }

    // ---- Stato / notifica ----

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

    override fun onDestroy() {
        super.onDestroy()
        closeExistingGatt()
    }

    companion object {
        private const val CHANNEL_ID = "br80_ble_service_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
