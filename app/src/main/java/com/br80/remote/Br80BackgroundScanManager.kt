package com.br80.remote

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/** Registra uno scan BLE "offloaded" filtrato sul MAC del BR80, gestito dallo stack Bluetooth di
 * sistema indipendentemente dal ciclo di vita del processo dell'app: a differenza di uno scan
 * avviato con ScanCallback (quello usato da BleGattManager mentre l'app è viva), questo continua
 * a girare e a consegnare risultati anche a processo completamente killato — è il meccanismo che
 * rende possibile il popup "telecomando rilevato" ad app chiusa (vedi Br80RemoteScanReceiver). */
object Br80BackgroundScanManager {

    private const val REQUEST_CODE = 30
    private const val TAG = "Br80BackgroundScan"

    @SuppressLint("MissingPermission")
    fun ensureRegistered(context: Context) {
        val mac = MappingStorage.getInstance(context).getLastConnectedMac() ?: return
        if (!BluetoothAdapter.checkBluetoothAddress(mac)) return

        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null || !adapter.isEnabled) return
        val scanner = adapter.bluetoothLeScanner ?: return

        val filters = listOf(ScanFilter.Builder().setDeviceAddress(mac).build())
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        try {
            scanner.startScan(filters, settings, scanResultPendingIntent(context))
        } catch (e: Exception) {
            Log.w(TAG, "Registrazione scan in background fallita: ${e.message}")
        }
    }

    private fun scanResultPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, Br80RemoteScanReceiver::class.java)
        // FLAG_MUTABLE, non FLAG_IMMUTABLE come altrove nel progetto: lo stack Bluetooth deve
        // poter "riempire" questo PendingIntent con l'Intent contenente i veri risultati dello
        // scan (EXTRA_LIST_SCAN_RESULT) al momento dell'invio — con FLAG_IMMUTABLE quel
        // riempimento viene ignorato e l'Intent arriva sempre senza extra (bug riprodotto dal
        // vivo: onReceive riceveva il broadcast ma intent.extras era sempre null).
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
