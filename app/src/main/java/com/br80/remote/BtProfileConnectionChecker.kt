package com.br80.remote

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log

/**
 * BluetoothManager.getConnectedDevices() supporta SOLO GATT/GATT_SERVER (documentazione
 * Android ufficiale): usarlo per A2DP/HEADSET lancia sempre eccezione, con conseguente
 * falso "nessun dispositivo connesso". Questo oggetto condiviso usa invece i proxy di
 * profilo (BluetoothAdapter.getProfileProxy), l'unico modo corretto per interrogare lo
 * stato A2DP/HEADSET.
 */
object BtProfileConnectionChecker {

    private const val TAG = "BtProfileConnectionChecker"

    private var a2dpProxy: BluetoothA2dp? = null
    private var headsetProxy: BluetoothHeadset? = null

    fun initialize(context: Context) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: return

        adapter.getProfileProxy(context.applicationContext, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                a2dpProxy = proxy as? BluetoothA2dp
            }

            override fun onServiceDisconnected(profile: Int) {
                a2dpProxy = null
            }
        }, BluetoothProfile.A2DP)

        adapter.getProfileProxy(context.applicationContext, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                headsetProxy = proxy as? BluetoothHeadset
            }

            override fun onServiceDisconnected(profile: Int) {
                headsetProxy = null
            }
        }, BluetoothProfile.HEADSET)
    }

    fun isAnyDeviceConnected(macs: List<String>): Boolean {
        if (macs.isEmpty()) return false
        return try {
            val a2dpConnected = a2dpProxy?.connectedDevices ?: emptyList()
            val headsetConnected = headsetProxy?.connectedDevices ?: emptyList()
            (a2dpConnected + headsetConnected).any { device -> macs.any { it.equals(device.address, ignoreCase = true) } }
        } catch (e: Exception) {
            Log.w(TAG, "Impossibile verificare dispositivi audio/BT connessi: ${e.message}")
            false
        }
    }

    fun shutdown(context: Context) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: return
        try {
            a2dpProxy?.let { adapter.closeProfileProxy(BluetoothProfile.A2DP, it) }
            headsetProxy?.let { adapter.closeProfileProxy(BluetoothProfile.HEADSET, it) }
        } catch (e: Exception) {
            Log.w(TAG, "Errore rilascio proxy profilo BT: ${e.message}")
        }
        a2dpProxy = null
        headsetProxy = null
    }
}
