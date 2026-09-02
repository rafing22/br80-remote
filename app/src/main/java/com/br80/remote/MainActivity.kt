package com.br80.remote

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity(), BleForegroundService.BleServiceListener {

    private var bleService: BleForegroundService? = null
    private var isBound = false
    private var pendingConnectOnBind = false

    private lateinit var tvStatus: TextView
    private lateinit var tvBattery: TextView
    private lateinit var tvLog: TextView
    private lateinit var btnConnect: Button

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BleForegroundService.LocalBinder
            bleService = binder.getService()
            bleService?.listener = this@MainActivity
            isBound = true

            bleService?.let {
                onStateChanged(it.currentState)
                if (pendingConnectOnBind && it.currentState == BleForegroundService.ConnectionState.DISCONNECTED) {
                    pendingConnectOnBind = false
                    it.connectDevice()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bleService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Recupero dinamico dei layout e degli ID per evitare errori con la classe R
        val layoutResId = resources.getIdentifier("activity_main", "layout", packageName)
        setContentView(layoutResId)

        tvStatus = findViewById(resources.getIdentifier("tvStatus", "id", packageName))
        tvBattery = findViewById(resources.getIdentifier("tvBattery", "id", packageName))
        tvLog = findViewById(resources.getIdentifier("tvLog", "id", packageName))
        btnConnect = findViewById(resources.getIdentifier("btnConnect", "id", packageName))

        btnConnect.setOnClickListener {
            onConnectButtonClicked()
        }
    }

    private fun onConnectButtonClicked() {
        try {
            bleService?.let { service ->
                when (service.currentState) {
                    BleForegroundService.ConnectionState.DISCONNECTED -> {
                        requestPermissionsAndConnect()
                    }
                    BleForegroundService.ConnectionState.CONNECTING,
                    BleForegroundService.ConnectionState.CONNECTED -> {
                        service.disconnectDevice()
                    }
                }
            } ?: run {
                pendingConnectOnBind = true
                startAndBindBleService()
            }
        } catch (e: Exception) {
            tvLog.append("\nERRORE: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun requestPermissionsAndConnect() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed.add(Manifest.permission.BLUETOOTH_SCAN)
            needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            tvLog.append("\nRichiedo permessi: ${missing.joinToString()}")
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1)
            return
        }
        bleService?.connectDevice()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            bleService?.connectDevice()
        } else {
            tvLog.append("\nPermessi Bluetooth negati, impossibile connettersi.")
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, BleForegroundService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            bleService?.listener = null
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun startAndBindBleService() {
        val intent = Intent(this, BleForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStateChanged(state: BleForegroundService.ConnectionState) {
        runOnUiThread {
            when (state) {
                BleForegroundService.ConnectionState.DISCONNECTED -> {
                    tvStatus.text = "Stato: Disconnesso"
                    btnConnect.text = "Connetti"
                }
                BleForegroundService.ConnectionState.CONNECTING -> {
                    tvStatus.text = "Stato: Connessione in corso..."
                    btnConnect.text = "Annulla"
                }
                BleForegroundService.ConnectionState.CONNECTED -> {
                    tvStatus.text = "Stato: Connesso"
                    btnConnect.text = "Disconnetti"
                }
            }
        }
    }

    override fun onButtonEvent(button: String, gesture: String) {
        runOnUiThread {
            tvLog.append("\n[$button] $gesture")
        }
    }

    override fun onBatteryUpdated(level: Int) {
        runOnUiThread {
            tvBattery.text = "Batteria: $level%"
        }
    }

    override fun onLog(message: String) {
        runOnUiThread {
            tvLog.append("\n$message")
        }
    }
}
package com.br80.remote

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), BleForegroundService.BleServiceListener {

    private var bleService: BleForegroundService? = null
    private var isBound = false
    private var pendingConnectOnBind = false

    private lateinit var tvStatus: TextView
    private lateinit var tvBattery: TextView
    private lateinit var tvLog: TextView
    private lateinit var btnConnect: Button

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BleForegroundService.LocalBinder
            bleService = binder.getService()
            bleService?.listener = this@MainActivity
            isBound = true

            bleService?.let {
                onStateChanged(it.currentState)
                if (pendingConnectOnBind && it.currentState == BleForegroundService.ConnectionState.DISCONNECTED) {
                    pendingConnectOnBind = false
                    it.connectDevice()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bleService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Recupero dinamico dei layout e degli ID per evitare errori con la classe R
        val layoutResId = resources.getIdentifier("activity_main", "layout", packageName)
        setContentView(layoutResId)

        tvStatus = findViewById(resources.getIdentifier("tvStatus", "id", packageName))
        tvBattery = findViewById(resources.getIdentifier("tvBattery", "id", packageName))
        tvLog = findViewById(resources.getIdentifier("tvLog", "id", packageName))
        btnConnect = findViewById(resources.getIdentifier("btnConnect", "id", packageName))

        btnConnect.setOnClickListener {
            bleService?.let { service ->
                when (service.currentState) {
                    BleForegroundService.ConnectionState.DISCONNECTED -> {
                        service.connectDevice()
                    }
                    BleForegroundService.ConnectionState.CONNECTING,
                    BleForegroundService.ConnectionState.CONNECTED -> {
                        service.disconnectDevice()
                    }
                }
            } ?: run {
                pendingConnectOnBind = true
                startAndBindBleService()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, BleForegroundService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            bleService?.listener = null
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun startAndBindBleService() {
        val intent = Intent(this, BleForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStateChanged(state: BleForegroundService.ConnectionState) {
        runOnUiThread {
            when (state) {
                BleForegroundService.ConnectionState.DISCONNECTED -> {
                    tvStatus.text = "Stato: Disconnesso"
                    btnConnect.text = "Connetti"
                }
                BleForegroundService.ConnectionState.CONNECTING -> {
                    tvStatus.text = "Stato: Connessione in corso..."
                    btnConnect.text = "Annulla"
                }
                BleForegroundService.ConnectionState.CONNECTED -> {
                    tvStatus.text = "Stato: Connesso"
                    btnConnect.text = "Disconnetti"
                }
            }
        }
    }

    override fun onButtonEvent(button: String, gesture: String) {
        runOnUiThread {
            tvLog.append("\n[$button] $gesture")
        }
    }

    override fun onBatteryUpdated(level: Int) {
        runOnUiThread {
            tvBattery.text = "Batteria: $level%"
        }
    }

    override fun onLog(message: String) {
        runOnUiThread {
            tvLog.append("\n$message")
        }
    }
}
