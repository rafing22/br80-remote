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

    private lateinit var tvStatus: TextView
    private lateinit var btnConnect: Button

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BleForegroundService.LocalBinder
            bleService = binder.getService()
            bleService?.listener = this@MainActivity
            isBound = true

            bleService?.let { onStateChanged(it.currentState) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bleService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate()
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnConnect = findViewById(R.id.btnConnect)

        btnConnect.setOnClickListener {
            bleService?.let { service ->
                when (service.currentState) {
                    BleForegroundService.ConnectionState.DISCONNECTED -> {
                        startAndBindBleService()
                        service.connectDevice()
                    }
                    BleForegroundService.ConnectionState.CONNECTING,
                    BleForegroundService.ConnectionState.CONNECTED -> {
                        service.disconnectDevice()
                    }
                }
            } ?: run {
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
        // Log degli eventi
    }

    override fun onBatteryUpdated(level: Int) {
        // Aggiornamento batteria
    }
}
