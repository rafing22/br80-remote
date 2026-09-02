package com.br80.remote

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import java.util.UUID

class MainActivity : Activity() {

  private val tag = "BR80"

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

  private lateinit var logView: TextView
  private lateinit var scrollView: ScrollView
  private var bluetoothGatt: BluetoothGatt? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val root = LinearLayout(this)
    root.orientation = LinearLayout.VERTICAL

    val scanButton = Button(this)
    scanButton.text = "Scan e connetti al BR80"
    root.addView(scanButton)

    logView = TextView(this)
    logView.text = "Pronto.\n"
    scrollView = ScrollView(this)
    scrollView.addView(logView)
    root.addView(scrollView)

    setContentView(root)

    scanButton.setOnClickListener { requestPermissionsAndScan() }
  }

  private fun log(msg: String) {
    runOnUiThread {
      logView.append("$msg\n")
      scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }
    Log.d(tag, msg)
  }

  private fun requestPermissionsAndScan() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val needed = listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        .filter { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
      if (needed.isNotEmpty()) {
        ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1)
        return
      }
    }
    startScan()
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
      startScan()
    } else {
      log("Permessi Bluetooth negati.")
    }
  }

  @SuppressLint("MissingPermission")
  private fun startScan() {
    closeExistingGatt()

    val bluetoothManager = getSystemService(BluetoothManager::class.java)
    val adapter = bluetoothManager.adapter
    if (adapter == null || !adapter.isEnabled) {
      log("Bluetooth non disponibile o spento.")
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
          connect(result.device)
        }
      }
      override fun onScanFailed(errorCode: Int) {
        log("Scan fallito, codice $errorCode")
      }
    }
    scanner.startScan(callback)
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
  private fun connect(device: BluetoothDevice) {
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
        log("Connesso. Scopro i servizi...")
        gatt.discoverServices()
      } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
        log("Disconnesso.")
        gatt.close()
        if (bluetoothGatt === gatt) {
          bluetoothGatt = null
        }
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
          log("Wake fallito dopo $maxWakeRetries tentativi (status $status). Riprova a premere Scan.")
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

  override fun onDestroy() {
    super.onDestroy()
    closeExistingGatt()
  }
}
