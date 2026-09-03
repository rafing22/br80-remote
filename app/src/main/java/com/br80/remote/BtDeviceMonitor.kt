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
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val targetMac = mappingStorage.getConditionalBtMac()
                    val targetName = mappingStorage.getConditionalBtName()

                    if (device != null && isTargetDevice(device, targetMac)) {
                        val name = device.name ?: targetName ?: "Dispositivo BT"
                        Log.d(tag, "Dispositivo BT target connesso: $name [${device.address}]")
                        listener.onTargetDeviceConnectionChanged(true, name)
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val targetMac = mappingStorage.getConditionalBtMac()
                    val targetName = mappingStorage.getConditionalBtName()

                    if (device != null && isTargetDevice(device, targetMac)) {
                        val name = device.name ?: targetName ?: "Dispositivo BT"
                        Log.d(tag, "Dispositivo BT target disconnesso: $name [${device.address}]")
                        listener.onTargetDeviceConnectionChanged(false, name)
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
        val targetMac = mappingStorage.getConditionalBtMac() ?: return false
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: return false

        if (!adapter.isEnabled) return false

        try {
            // Controlla sia il profilo A2DP che HEADSET per cuffie / interfoni
            val a2dpConnected = bluetoothManager.getConnectedDevices(BluetoothProfile.A2DP).any { it.address.equals(targetMac, ignoreCase = true) }
            val headsetConnected = bluetoothManager.getConnectedDevices(BluetoothProfile.HEADSET).any { it.address.equals(targetMac, ignoreCase = true) }
            val gattConnected = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT).any { it.address.equals(targetMac, ignoreCase = true) }
            return a2dpConnected || headsetConnected || gattConnected
        } catch (e: Exception) {
            Log.w(tag, "Impossibile verificare dispositivi connessi da BluetoothManager: ${e.message}")
        }
        return false
    }

    private fun checkCurrentTargetConnectionState() {
        if (mappingStorage.isConditionalBtEnabled()) {
            val isConnected = isTargetCurrentlyConnected()
            val name = mappingStorage.getConditionalBtName()
            listener.onTargetDeviceConnectionChanged(isConnected, name)
        }
    }

    private fun isTargetDevice(device: BluetoothDevice, targetMac: String?): Boolean {
        if (targetMac.isNullOrEmpty()) return false
        return device.address.equals(targetMac, ignoreCase = true)
    }
}
