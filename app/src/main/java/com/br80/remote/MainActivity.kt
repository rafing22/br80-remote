package com.br80.remote

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.br80.remote.ui.ControllerFragment
import com.br80.remote.ui.LogFragment
import com.br80.remote.ui.OptionsFragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Host delle 3 schermate (Controller/Opzioni/Log, come Fragment separati) e unico
 * proprietario del binding al BleForegroundService. Le schermate non si parlano mai
 * direttamente tra loro: passano sempre da qui (mappingStorage, bleService, appendLog,
 * refreshControllerSelection, exitApplication).
 */
class MainActivity : AppCompatActivity(), BleForegroundService.BleServiceListener {

    private var bleServiceInternal: BleForegroundService? = null
    val bleService: BleForegroundService? get() = bleServiceInternal
    private var isBound = false
    private var pendingConnectOnBind = false

    lateinit var mappingStorage: MappingStorage
        private set

    // Header Views (fuori dai Fragment: sempre visibili sopra le 3 tab)
    private lateinit var viewStatusDot: View
    private lateinit var tvHeaderStatus: TextView
    private lateinit var tvHeaderBattery: TextView
    private lateinit var btnQuickConnect: android.widget.Button

    // Bottom Navigation
    private lateinit var navBtnController: android.widget.LinearLayout
    private lateinit var navBtnOptions: android.widget.LinearLayout
    private lateinit var navBtnLog: android.widget.LinearLayout
    private lateinit var tvNavTextController: TextView
    private lateinit var tvNavTextOptions: TextView
    private lateinit var tvNavTextLog: TextView

    private val controllerFragment = ControllerFragment()
    private val optionsFragment = OptionsFragment()
    private val logFragment = LogFragment()
    private var activeFragment: Fragment = controllerFragment

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BleForegroundService.LocalBinder
            bleServiceInternal = binder.getService()
            bleServiceInternal?.listener = this@MainActivity
            isBound = true

            bleServiceInternal?.let {
                onStateChanged(it.currentState)
                if (it.batteryLevel >= 0) {
                    onBatteryUpdated(it.batteryLevel)
                }
                val hasSavedMac = !mappingStorage.getLastConnectedMac().isNullOrEmpty()
                if ((pendingConnectOnBind || hasSavedMac) && it.currentState == BleGattManager.ConnectionState.DISCONNECTED) {
                    pendingConnectOnBind = false
                    it.connectDevice()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bleServiceInternal = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mappingStorage = MappingStorage(this)

        initHeaderViews()
        setupBottomNav()
        setupFragments()
        setupDoubleBackToExit()

        // Auto-avvio servizio e ascolto se c'è un telecomando già associato. Passa da
        // checkPermissionsAndConnect() (non direttamente startAndBindBleService()) perché
        // altrimenti un permesso aggiunto in un aggiornamento successivo (es. CALL_PHONE,
        // READ_CALL_LOG) non veniva mai richiesto per chi aveva già un telecomando salvato:
        // l'utente non passa mai dal pulsante Connetti manuale dove la richiesta avviene.
        if (!mappingStorage.getLastConnectedMac().isNullOrEmpty()) {
            pendingConnectOnBind = true
            checkPermissionsAndConnect()
        }

        // Controllo aggiornamenti all'avvio
        AppUpdateManager.checkForUpdates(this, isManualCheck = false)
    }

    private var backPressedOnce = false

    // Sostituisce il vecchio pulsante "Esci / Chiudi Applicazione": doppio tasto Indietro
    // entro 2s ferma completamente il servizio in background (stesso comportamento del
    // pulsante rimosso), un solo tocco mostra solo l'avviso. Il tasto "Esci" nella
    // notifica del servizio resta comunque disponibile per lo stesso scopo.
    private fun setupDoubleBackToExit() {
        onBackPressedDispatcher.addCallback(this) {
            // Se siamo dentro una sotto-schermata di Opzioni, Indietro la chiude e basta:
            // non deve mai attivare il doppio-back-per-uscire.
            if (activeFragment === optionsFragment && optionsFragment.handleBackPressed()) {
                return@addCallback
            }
            if (backPressedOnce) {
                exitApplication()
                return@addCallback
            }
            backPressedOnce = true
            Toast.makeText(this@MainActivity, "Premi di nuovo Indietro per uscire e fermare il servizio", Toast.LENGTH_SHORT).show()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ backPressedOnce = false }, 2000L)
        }
    }

    private fun initHeaderViews() {
        viewStatusDot = findViewById(R.id.viewStatusDot)
        tvHeaderStatus = findViewById(R.id.tvHeaderStatus)
        tvHeaderBattery = findViewById(R.id.tvHeaderBattery)
        btnQuickConnect = findViewById(R.id.btnQuickConnect)
        btnQuickConnect.setOnClickListener { onConnectButtonClicked() }

        navBtnController = findViewById(R.id.navBtnController)
        navBtnOptions = findViewById(R.id.navBtnOptions)
        navBtnLog = findViewById(R.id.navBtnLog)
        tvNavTextController = findViewById(R.id.tvNavTextController)
        tvNavTextOptions = findViewById(R.id.tvNavTextOptions)
        tvNavTextLog = findViewById(R.id.tvNavTextLog)
    }

    private fun setupBottomNav() {
        navBtnController.setOnClickListener { switchTab(0) }
        navBtnOptions.setOnClickListener { switchTab(1) }
        navBtnLog.setOnClickListener { switchTab(2) }
    }

    // Le 3 istanze restano sempre vive (add + hide/show, mai replace): un replace()
    // distruggerebbe la view dei fragment nascosti ad ogni cambio tab, perdendo lo stato
    // di scroll/input e soprattutto la cronologia del Log (tvLogFull verrebbe ricreato vuoto).
    private fun setupFragments() {
        supportFragmentManager.beginTransaction()
            .add(R.id.fragmentContainer, logFragment, "log")
            .hide(logFragment)
            .add(R.id.fragmentContainer, optionsFragment, "options")
            .hide(optionsFragment)
            .add(R.id.fragmentContainer, controllerFragment, "controller")
            .commitNow()
        activeFragment = controllerFragment
    }

    private fun switchTab(tabIndex: Int) {
        val target = when (tabIndex) {
            0 -> controllerFragment
            1 -> optionsFragment
            else -> logFragment
        }
        if (target !== activeFragment) {
            supportFragmentManager.beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_NONE)
                .hide(activeFragment)
                .show(target)
                .commit()
            activeFragment = target
        }

        tvNavTextController.setTextColor(toColorStateList(if (tabIndex == 0) ContextCompat.getColor(this, R.color.cockpit_accent) else ContextCompat.getColor(this, R.color.cockpit_muted)))
        tvNavTextOptions.setTextColor(toColorStateList(if (tabIndex == 1) ContextCompat.getColor(this, R.color.cockpit_accent) else ContextCompat.getColor(this, R.color.cockpit_muted)))
        tvNavTextLog.setTextColor(toColorStateList(if (tabIndex == 2) ContextCompat.getColor(this, R.color.cockpit_accent) else ContextCompat.getColor(this, R.color.cockpit_muted)))

    }

    /** Appende una riga al log (tab Log), da qualunque fragment o callback del servizio. */
    fun appendLog(message: String) {
        val time = timeFormat.format(Date())
        logFragment.appendLog("[$time] $message")
    }

    /** Da richiamare dopo un cambio/creazione/eliminazione profilo in Opzioni, per
     * aggiornare la scheda gesti del Controller sul profilo appena attivato. */
    fun refreshControllerSelection() {
        controllerFragment.refreshCurrentSelection()
    }

    private fun onConnectButtonClicked() {
        bleService?.let { service ->
            when (service.currentState) {
                BleGattManager.ConnectionState.DISCONNECTED -> {
                    checkPermissionsAndConnect()
                }
                BleGattManager.ConnectionState.CONNECTING -> {
                    // No-op: un secondo tap durante l'handshake interromperebbe una connessione
                    // che si stava per completare (confermato dal vivo, log al millisecondo).
                    // C'è già un watchdog di 5s che sblocca da solo se il device non risponde.
                }
                BleGattManager.ConnectionState.CONNECTED -> {
                    service.disconnectDevice()
                }
            }
        } ?: run {
            pendingConnectOnBind = true
            startAndBindBleService()
        }
    }

    private fun checkPermissionsAndConnect() {
        val needed = mutableListOf<String>()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            needed.add(Manifest.permission.BLUETOOTH_SCAN)
            needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            needed.add(Manifest.permission.ANSWER_PHONE_CALLS)
        }

        needed.add(Manifest.permission.READ_CALL_LOG)
        needed.add(Manifest.permission.CALL_PHONE)

        val missing = needed.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            appendLog("Richiesta permessi: ${missing.joinToString()}")
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSIONS_REQUEST_CODE)
            return
        }

        startAndBindBleService()
        bleService?.connectDevice()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                startAndBindBleService()
                bleService?.connectDevice()
            } else {
                Toast.makeText(this, "Permessi negati: impossibile connettersi al telecomando senza autorizzare Bluetooth.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startAndBindBleService() {
        val intent = Intent(this, BleForegroundService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStart() {
        super.onStart()
        if (!isBound) {
            val intent = Intent(this, BleForegroundService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun exitApplication() {
        if (isBound) {
            bleServiceInternal?.listener = null
            unbindService(serviceConnection)
            isBound = false
        }
        val stopIntent = Intent(this, BleForegroundService::class.java).apply {
            action = BleForegroundService.ACTION_STOP_SERVICE
        }
        startService(stopIntent)
        finishAndRemoveTask()
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            bleServiceInternal?.listener = null
            unbindService(serviceConnection)
            isBound = false
        }
    }


    // Callbacks del Service
    override fun onStateChanged(state: BleGattManager.ConnectionState) {
        runOnUiThread {
            when (state) {
                BleGattManager.ConnectionState.DISCONNECTED -> {
                    val hasSaved = !mappingStorage.getLastConnectedMac().isNullOrEmpty()
                    if (hasSaved) {
                        viewStatusDot.backgroundTintList = toColorStateList(ContextCompat.getColor(this, R.color.status_warning))
                        tvHeaderStatus.text = "In ascolto (Premi un tasto)"
                        tvHeaderStatus.setTextColor(ContextCompat.getColor(this, R.color.status_warning_dark))
                        tvHeaderBattery.text = ""
                        btnQuickConnect.text = "Riconnetti"
                        btnQuickConnect.backgroundTintList = toColorStateList(ContextCompat.getColor(this, R.color.cockpit_accent))
                    } else {
                        viewStatusDot.backgroundTintList = toColorStateList(ContextCompat.getColor(this, R.color.status_error))
                        tvHeaderStatus.text = "Disconnesso"
                        tvHeaderStatus.setTextColor(ContextCompat.getColor(this, R.color.cockpit_muted))
                        tvHeaderBattery.text = ""
                        btnQuickConnect.text = "Connetti"
                        btnQuickConnect.backgroundTintList = toColorStateList(ContextCompat.getColor(this, R.color.cockpit_accent))
                    }
                }
                BleGattManager.ConnectionState.CONNECTING -> {
                    viewStatusDot.backgroundTintList = toColorStateList(ContextCompat.getColor(this, R.color.status_warning))
                    tvHeaderStatus.text = "Connessione in corso..."
                    tvHeaderStatus.setTextColor(ContextCompat.getColor(this, R.color.status_warning_dark))
                    btnQuickConnect.text = "Annulla"
                    btnQuickConnect.backgroundTintList = toColorStateList(ContextCompat.getColor(this, R.color.status_warning_dark))
                }
                BleGattManager.ConnectionState.CONNECTED -> {
                    viewStatusDot.backgroundTintList = toColorStateList(ContextCompat.getColor(this, R.color.status_success))
                    tvHeaderStatus.text = "Connesso"
                    tvHeaderStatus.setTextColor(ContextCompat.getColor(this, R.color.status_success))
                    btnQuickConnect.text = "Disconnetti"
                    btnQuickConnect.backgroundTintList = toColorStateList(ContextCompat.getColor(this, R.color.status_error))
                }
            }
        }
    }

    override fun onButtonRawEvent(button: Br80Button, isPress: Boolean) {
        runOnUiThread {
            if (isPress) {
                controllerFragment.selectButton(button)
            }
        }
    }

    override fun onGestureExecuted(button: Br80Button, gesture: GestureType) {
        runOnUiThread {
            val time = timeFormat.format(Date())
            appendLog("[$time] AZIONE ESEGUITA -> ${button.name} [${gesture.name}]")
            controllerFragment.showLastAction(button, gesture)
        }
    }

    override fun onBatteryUpdated(level: Int) {
        runOnUiThread {
            tvHeaderBattery.text = "• $level% 🔋"
            tvHeaderBattery.setTextColor(if (level <= 20) ContextCompat.getColor(this, R.color.status_error) else ContextCompat.getColor(this, R.color.cockpit_accent))
            controllerFragment.updateBatteryGauge(level)
        }
    }

    override fun onRssiUpdated(rssi: Int) {
        runOnUiThread {
            controllerFragment.updateRssiGauge(rssi)
        }
    }

    override fun onLog(message: String) {
        runOnUiThread {
            appendLog(message)
        }
    }

    private fun toColorStateList(color: Int) = android.content.res.ColorStateList.valueOf(color)

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 100
    }
}
