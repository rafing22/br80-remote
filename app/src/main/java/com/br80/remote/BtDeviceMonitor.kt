package com.br80.remote

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.IntentCompat

class BtDeviceMonitor(
    private val context: Context,
    private val mappingStorage: MappingStorage,
    private val listener: BtDeviceMonitorListener
) {

    private val tag = "BtDeviceMonitor"
    private var isReceiverRegistered = false

    interface BtDeviceMonitorListener {
        fun onTargetDeviceConnectionChanged(isConnected: Boolean, deviceName: String?)
        fun onBluetoothStateChanged(isBtOn: Boolean)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return

            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    val targetMacs = mappingStorage.getConditionalBtDevices().map { it.first }

                    if (device != null && isTargetDevice(device, targetMacs)) {
                        // Ricalcola lo stato aggregato: se hai più dispositivi target configurati,
                        // la disconnessione di UNO solo non deve spegnere il keep-alive se un
                        // altro dispositivo target resta connesso.
                        val stillConnected = isTargetCurrentlyConnected()
                        val name = device.name ?: mappingStorage.getConditionalBtDevices()
                            .firstOrNull { it.first.equals(device.address, ignoreCase = true) }?.second
                            ?: "Dispositivo BT"
                        Log.d(tag, "Evento BT target [$action] su $name [${device.address}]. Stato aggregato connesso=$stillConnected")
                        listener.onTargetDeviceConnectionChanged(stillConnected, name)
                    }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    val isBtOn = (state == BluetoothAdapter.STATE_ON)
                    Log.d(tag, "Stato Bluetooth di sistema cambiato: ON=$isBtOn")
                    listener.onBluetoothStateChanged(isBtOn)
                }
            }
        }
    }

    fun startMonitoring() {
        if (isReceiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            context.registerReceiver(receiver, filter)
            isReceiverRegistered = true
            Log.d(tag, "BtDeviceMonitor avviato con successo.")

            // Verifica immediata dello stato attuale se il tracciamento condizionale è attivo
            checkCurrentTargetConnectionState()
        } catch (e: Exception) {
            Log.e(tag, "Errore registrazione BtDeviceMonitor: ${e.message}")
        }
    }

    fun stopMonitoring() {
        if (!isReceiverRegistered) return
        try {
            context.unregisterReceiver(receiver)
            isReceiverRegistered = false
            Log.d(tag, "BtDeviceMonitor fermato.")
        } catch (e: Exception) {
            Log.w(tag, "Errore durante unregister BtDeviceMonitor: ${e.message}")
        }
    }

    fun isTargetCurrentlyConnected(): Boolean {
        val targetMacs = mappingStorage.getConditionalBtDevices().map { it.first }
        if (targetMacs.isEmpty()) return false

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: return false
        if (!adapter.isEnabled) return false

        try {
            // GATT (es. telecomando BR80 stesso) è l'unico profilo supportato da
            // BluetoothManager.getConnectedDevices(); A2DP/HEADSET (cuffie/interfoni) passano
            // dal checker condiviso basato sui proxy di profilo.
            val gattConnected = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
            if (gattConnected.any { device -> targetMacs.any { it.equals(device.address, ignoreCase = true) } }) {
                return true
            }
            return BtProfileConnectionChecker.isAnyDeviceConnected(targetMacs)
        } catch (e: Exception) {
            Log.w(tag, "Impossibile verificare dispositivi connessi da BluetoothManager: ${e.message}")
        }
        return false
    }

    private fun checkCurrentTargetConnectionState() {
        if (mappingStorage.isConditionalBtEnabled()) {
            val isConnected = isTargetCurrentlyConnected()
            val name = mappingStorage.getConditionalBtDevices().firstOrNull()?.second
            listener.onTargetDeviceConnectionChanged(isConnected, name)
        }
    }

    private fun isTargetDevice(device: BluetoothDevice, targetMacs: List<String>): Boolean {
        if (targetMacs.isEmpty()) return false
        return targetMacs.any { it.equals(device.address, ignoreCase = true) }
    }
}
