package com.br80.remote

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

class BleGattManager(
    private val context: Context,
    private val mappingStorage: MappingStorage,
    private val listener: BleGattListener
) {

    private val tag = "BleGattManager"

    interface BleGattListener {
        fun onStateChanged(state: ConnectionState)
        fun onButtonRawEvent(button: Br80Button, isPress: Boolean)
        fun onBatteryUpdated(level: Int)
        fun onLog(message: String)
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    var currentState: ConnectionState = ConnectionState.DISCONNECTED
        private set

    var batteryLevel: Int = -1
        private set

    private var bluetoothGatt: BluetoothGatt? = null
    private var userRequestedDisconnect = false
    private var isScanning = false
    private var scanCallback: ScanCallback? = null

    private var wakeRetries = 0
    private val maxWakeRetries = 5
    private val wakeRetryDelayMillis = 1000L

    private var reconnectAttempts = 0
    private val reconnectDelays = listOf(1000L, 2500L, 5000L, 10000L)
    private var reconnectRunnable: Runnable? = null
    private var scanTimeoutRunnable: Runnable? = null
    private var connectionWatchdogRunnable: Runnable? = null
    private val connectionWatchdogTimeoutMs = 5000L // 5s timeout rapido per non bloccare lo stack se il device dorme

    private var keepAliveRunnable: Runnable? = null
    private val keepAliveIntervalMs = 35_000L // Ping ogni 35s per prevenire lo standby firmware

    private val handler = Handler(Looper.getMainLooper())

    private val serviceUuid = UUID.fromString("0000a2a0-0000-1000-8000-00805f9b34fb")
    private val wakeUuid = UUID.fromString("0000a2a3-0000-1000-8000-00805f9b34fb")
    private val buttonUuid = UUID.fromString("0000a2a4-0000-1000-8000-00805f9b34fb")
    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val batteryServiceUuid = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    private val batteryLevelUuid = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    private fun log(msg: String) {
        Log.d(tag, msg)
        handler.post {
            listener.onLog(msg)
        }
    }

    private fun updateState(newState: ConnectionState) {
        handler.post {
            currentState = newState
            listener.onStateChanged(newState)
        }
    }

    private fun getBluetoothAdapter(): BluetoothAdapter? {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bluetoothManager?.adapter
    }

    private fun startConnectionWatchdog() {
        stopConnectionWatchdog()
        connectionWatchdogRunnable = Runnable {
            if (currentState == ConnectionState.CONNECTING) {
                log("Watchdog: il telecomando è in Standby. Attivo ascolto automatico a schermo spento...")
                closeGatt(refresh = true)
                updateState(ConnectionState.DISCONNECTED)
                val adapter = getBluetoothAdapter()
                if (adapter != null && adapter.isEnabled && !userRequestedDisconnect) {
                    startLeScan(adapter, isBackgroundStandby = true)
                }
            }
        }
        handler.postDelayed(connectionWatchdogRunnable!!, connectionWatchdogTimeoutMs)
    }

    private fun stopConnectionWatchdog() {
        connectionWatchdogRunnable?.let { handler.removeCallbacks(it) }
        connectionWatchdogRunnable = null
    }

    @SuppressLint("MissingPermission")
    fun connect() {
        userRequestedDisconnect = false
        cancelPendingReconnect()
        updateState(ConnectionState.CONNECTING)

        val adapter = getBluetoothAdapter()
        if (adapter == null || !adapter.isEnabled) {
            log("Bluetooth spento o non disponibile.")
            stopConnectionWatchdog()
            updateState(ConnectionState.DISCONNECTED)
            return
        }

        closeGatt(refresh = false)

        val savedMac = mappingStorage.getLastConnectedMac()
        if (!savedMac.isNullOrEmpty() && BluetoothAdapter.checkBluetoothAddress(savedMac)) {
            log("Tentativo di connessione rapida al MAC salvato: $savedMac...")
            try {
                val device = adapter.getRemoteDevice(savedMac)
                startConnectionWatchdog()
                connectGattTo(device)
                return
            } catch (e: Exception) {
                log("Connessione diretta fallita: ${e.message}, avvio scansione...")
            }
        }

        startConnectionWatchdog()
        startLeScan(adapter, isBackgroundStandby = false)
    }

    @SuppressLint("MissingPermission")
    private fun startLeScan(adapter: BluetoothAdapter, isBackgroundStandby: Boolean = false) {
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            log("BLE Scanner non disponibile.")
            stopConnectionWatchdog()
            updateState(ConnectionState.DISCONNECTED)
            scheduleAutoReconnect()
            return
        }

        stopLeScan()

        log(if (isBackgroundStandby) "Avvio ascolto Standby BLE (a schermo spento)..." else "Avvio scansione BLE per Livall BR80 / BlingRemote...")
        isScanning = true

        val scanMode = if (isBackgroundStandby) {
            ScanSettings.SCAN_MODE_LOW_POWER
        } else {
            ScanSettings.SCAN_MODE_LOW_LATENCY
        }

        val settings = ScanSettings.Builder()
            .setScanMode(scanMode)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .build()

        val filters = mutableListOf<ScanFilter>()
        val savedMac = mappingStorage.getLastConnectedMac()
        if (!savedMac.isNullOrEmpty() && BluetoothAdapter.checkBluetoothAddress(savedMac)) {
            filters.add(ScanFilter.Builder().setDeviceAddress(savedMac).build())
        }
        filters.add(ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceUuid)).build())
        filters.add(ScanFilter.Builder().setDeviceName("BlingRemote").build())
        filters.add(ScanFilter.Builder().setDeviceName("BR80").build())
        filters.add(ScanFilter.Builder().setDeviceName("Livall").build())

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (!isScanning) return

                val device = result.device
                val devName = device.name
                val recordName = result.scanRecord?.deviceName
                val serviceUuids = result.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()
                val savedMacCurrent = mappingStorage.getLastConnectedMac()

                val matchesName = (devName != null && isMatchingName(devName)) ||
                        (recordName != null && isMatchingName(recordName))
                val matchesService = serviceUuids.contains(serviceUuid)
                val matchesMac = (savedMacCurrent != null && device.address.equals(savedMacCurrent, ignoreCase = true))

                if (matchesName || matchesService || matchesMac) {
                    val displayName = recordName ?: devName ?: "BR80"
                    log("Dispositivo rilevato: $displayName [${device.address}], RSSI: ${result.rssi}")
                    stopLeScan()
                    mappingStorage.setLastConnectedMac(device.address)
                    connectGattTo(device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                log("Scansione BLE fallita con codice: $errorCode")
                isScanning = false
                stopConnectionWatchdog()
                updateState(ConnectionState.DISCONNECTED)
                scheduleAutoReconnect()
            }
        }

        scanCallback = callback
        try {
            scanner.startScan(filters, settings, callback)
        } catch (e: Exception) {
            log("Errore avvio scansione: ${e.message}")
            isScanning = false
            scheduleAutoReconnect()
            return
        }

        if (!isBackgroundStandby) {
            // Timeout scansione attiva a 8 secondi -> passa all'ascolto Standby a schermo spento
            scanTimeoutRunnable = Runnable {
                if (isScanning) {
                    log("Nessun segnale immediato: passo all'ascolto Standby in background...")
                    stopLeScan()
                    startLeScan(adapter, isBackgroundStandby = true)
                }
            }
            handler.postDelayed(scanTimeoutRunnable!!, 8000L)
        }
    }

    private fun isMatchingName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("blingremote") || lower.contains("br80") || lower.contains("livall")
    }

    @SuppressLint("MissingPermission")
    private fun stopLeScan() {
        scanTimeoutRunnable?.let { handler.removeCallbacks(it) }
        scanTimeoutRunnable = null

        if (isScanning) {
            val scanner = getBluetoothAdapter()?.bluetoothLeScanner
            scanCallback?.let {
                try {
                    scanner?.stopScan(it)
                } catch (e: Exception) {
                    Log.w(tag, "Stop scan error: ${e.message}")
                }
            }
            scanCallback = null
            isScanning = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectGattTo(device: BluetoothDevice) {
        wakeRetries = 0
        handler.postDelayed({
            try {
                log("Connessione GATT a ${device.address}...")
                bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } catch (e: Exception) {
                log("Eccezione connectGatt: ${e.message}")
                stopConnectionWatchdog()
                updateState(ConnectionState.DISCONNECTED)
                scheduleAutoReconnect()
            }
        }, 150L)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        userRequestedDisconnect = true
        stopConnectionWatchdog()
        stopKeepAlive()
        cancelPendingReconnect()
        stopLeScan()

        log("Disconnessione richiesta dall'utente.")
        closeGatt(refresh = true)
        updateState(ConnectionState.DISCONNECTED)
    }

    private fun refreshGatt(gatt: BluetoothGatt): Boolean {
        return try {
            val refreshMethod = gatt.javaClass.getMethod("refresh")
            (refreshMethod.invoke(gatt) as? Boolean) ?: false
        } catch (e: Exception) {
            Log.w(tag, "Gatt refresh exception: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt(refresh: Boolean = true) {
        stopConnectionWatchdog()
        stopKeepAlive()
        val gatt = bluetoothGatt
        bluetoothGatt = null
        if (gatt != null) {
            try {
                if (refresh) {
                    refreshGatt(gatt)
                }
                gatt.disconnect()
            } catch (e: Exception) {
                Log.w(tag, "Error disconnecting gatt: ${e.message}")
            }
            handler.postDelayed({
                try {
                    gatt.close()
                } catch (e: Exception) {
                    Log.w(tag, "Error closing gatt: ${e.message}")
                }
            }, 100L)
        }
    }

    private fun scheduleAutoReconnect() {
        if (userRequestedDisconnect) return

        stopConnectionWatchdog()
        stopKeepAlive()
        cancelPendingReconnect()
        val delay = reconnectDelays[minOf(reconnectAttempts, reconnectDelays.size - 1)]
        reconnectAttempts++
        log("Auto-Healing: ascolto o riconnessione programmata tra ${delay / 1000}s...")

        reconnectRunnable = Runnable {
            if (!userRequestedDisconnect && currentState == ConnectionState.DISCONNECTED) {
                val adapter = getBluetoothAdapter()
                if (adapter != null && adapter.isEnabled) {
                    startLeScan(adapter, isBackgroundStandby = true)
                }
            }
        }
        handler.postDelayed(reconnectRunnable!!, delay)
    }

    private fun cancelPendingReconnect() {
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        reconnectRunnable = null
    }

    fun startKeepAliveIfEnabled() {
        stopKeepAlive()
        if (mappingStorage.isKeepAliveEnabled() && currentState == ConnectionState.CONNECTED) {
            log("Keep-Alive attivo: ping periodico impostato ogni 35s per prevenire lo standby.")
            keepAliveRunnable = object : Runnable {
                override fun run() {
                    if (currentState == ConnectionState.CONNECTED && bluetoothGatt != null) {
                        log("Keep-Alive: invio ping per mantenere il canale attivo...")
                        readBatteryLevel()
                        handler.postDelayed(this, keepAliveIntervalMs)
                    }
                }
            }
            handler.postDelayed(keepAliveRunnable!!, keepAliveIntervalMs)
        }
    }

    fun stopKeepAlive() {
        keepAliveRunnable?.let { handler.removeCallbacks(it) }
        keepAliveRunnable = null
    }

    private fun gattStatusString(status: Int): String {
        return when (status) {
            BluetoothGatt.GATT_SUCCESS -> "SUCCESS (0)"
            0x85 -> "GATT_ERROR (133)"
            0x13 -> "CONN_TERMINATE_PEER_USER (19)"
            0x08 -> "CONN_TIMEOUT (8)"
            0x16 -> "CONN_TERMINATE_LOCAL_HOST (22)"
            0x3e -> "CONN_FAIL_ESTABLISH (62)"
            else -> "CODE_$status"
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            handler.post {
                log("Stato connessione BLE: status=$status (${gattStatusString(status)}), newState=$newState")

                if (status != BluetoothGatt.GATT_SUCCESS) {
                    log("Errore GATT rilevato ($status). Ripristino automatico stack...")
                    stopConnectionWatchdog()
                    closeGatt(refresh = true)
                    updateState(ConnectionState.DISCONNECTED)
                    scheduleAutoReconnect()
                    return@post
                }

                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    reconnectAttempts = 0
                    stopConnectionWatchdog()
                    stopLeScan()
                    updateState(ConnectionState.CONNECTED)
                    startKeepAliveIfEnabled()
                    log("Connesso al BR80. Scoperta servizi GATT in corso...")
                    handler.postDelayed({
                        try {
                            gatt.discoverServices()
                        } catch (e: Exception) {
                            log("Errore discoverServices: ${e.message}")
                        }
                    }, 300L)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    log("Telecomando disconnesso (standby o fuori portata).")
                    stopConnectionWatchdog()
                    stopKeepAlive()
                    closeGatt(refresh = false)
                    updateState(ConnectionState.DISCONNECTED)
                    scheduleAutoReconnect()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            handler.post {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    log("Scoperta servizi fallita: status $status. Riavvio auto-healing...")
                    closeGatt(refresh = true)
                    updateState(ConnectionState.DISCONNECTED)
                    scheduleAutoReconnect()
                    return@post
                }

                val service = gatt.getService(serviceUuid)
                if (service == null) {
                    log("Servizio a2a0 non trovato sul device.")
                    return@post
                }

                log("Servizio a2a0 trovato. Invio comando Wake (0xFF su a2a3)...")
                val wakeChar = service.getCharacteristic(wakeUuid)
                if (wakeChar != null) {
                    wakeRetries = 0
                    writeWakeCharacteristic(gatt, wakeChar)
                } else {
                    log("Caratteristica a2a3 (Wake) non trovata.")
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            handler.post {
                if (characteristic.uuid == wakeUuid) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        log("Wake inviato con successo. Abilito notifiche su a2a4...")
                        val service = gatt.getService(serviceUuid)
                        val buttonChar = service?.getCharacteristic(buttonUuid)
                        if (buttonChar != null) {
                            enableCharacteristicNotifications(gatt, buttonChar)
                        }
                    } else if (wakeRetries < maxWakeRetries) {
                        wakeRetries++
                        log("Wake non riuscito (status $status). Riprovo ($wakeRetries/$maxWakeRetries)...")
                        handler.postDelayed({
                            writeWakeCharacteristic(gatt, characteristic)
                        }, wakeRetryDelayMillis)
                    } else {
                        log("Wake fallito dopo $maxWakeRetries tentativi. Reset auto-healing...")
                        closeGatt(refresh = true)
                        updateState(ConnectionState.DISCONNECTED)
                        scheduleAutoReconnect()
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            handler.post {
                if (descriptor.uuid == cccdUuid && descriptor.characteristic.uuid == buttonUuid) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        log("Notifiche abilitate su a2a4! Telecomando pronto.")
                        handler.postDelayed({
                            readBatteryLevel(gatt)
                        }, 400L)
                    } else {
                        log("Abilitazione descrittore notifiche fallita: status $status")
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (characteristic.uuid == batteryLevelUuid && status == BluetoothGatt.GATT_SUCCESS) {
                @Suppress("DEPRECATION")
                val bytes = characteristic.value
                val level = bytes?.getOrNull(0)?.toInt()?.and(0xFF) ?: -1
                if (level in 0..100) {
                    batteryLevel = level
                    log("Livello batteria letto: $level%")
                    handler.post {
                        listener.onBatteryUpdated(level)
                    }
                }
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (characteristic.uuid == batteryLevelUuid && status == BluetoothGatt.GATT_SUCCESS) {
                val level = value.getOrNull(0)?.toInt()?.and(0xFF) ?: -1
                if (level in 0..100) {
                    batteryLevel = level
                    log("Livello batteria letto: $level%")
                    handler.post {
                        listener.onBatteryUpdated(level)
                    }
                }
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == buttonUuid) {
                handleButtonPayload(characteristic.value)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == buttonUuid) {
                handleButtonPayload(value)
            }
        }
    }

    private fun handleButtonPayload(value: ByteArray?) {
        if (value == null || value.isEmpty()) return
        val code = value[0].toInt() and 0xFF
        val parsed = Br80Button.fromCode(code)

        if (parsed != null) {
            val (button, isPress) = parsed
            val stateStr = if (isPress) "PRESS" else "RELEASE"
            log("Tasto [0x${code.toString(16)}]: ${button.name} $stateStr")
            handler.post {
                listener.onButtonRawEvent(button, isPress)
            }
        } else {
            log("Payload sconosciuto su a2a4: 0x${code.toString(16)} ($code)")
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeWakeCharacteristic(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
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
    private fun enableCharacteristicNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
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
        } else {
            log("Descrittore CCCD (0x2902) non trovato su characteristic.")
        }
    }

    @SuppressLint("MissingPermission")
    fun readBatteryLevel(gatt: BluetoothGatt? = bluetoothGatt) {
        val g = gatt ?: return
        val batteryService = g.getService(batteryServiceUuid)
        val batteryChar = batteryService?.getCharacteristic(batteryLevelUuid)
        if (batteryChar != null) {
            g.readCharacteristic(batteryChar)
        }
    }
}
