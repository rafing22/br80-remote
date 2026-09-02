package com.br80.remote

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), BleForegroundService.BleServiceListener {

    private var bleService: BleForegroundService? = null
    private var isBound = false
    private var pendingConnectOnBind = false

    private lateinit var mappingStorage: MappingStorage

    private lateinit var tvStatus: TextView
    private lateinit var tvBattery: TextView
    private lateinit var btnConnect: Button
    private lateinit var cbHaptic: CheckBox
    private lateinit var cbSound: CheckBox
    private lateinit var btnBatteryOpt: Button
    private lateinit var llButtonsList: LinearLayout
    private lateinit var tvLog: TextView
    private lateinit var svLog: ScrollView
    private lateinit var btnLogClear: TextView

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BleForegroundService.LocalBinder
            bleService = binder.getService()
            bleService?.listener = this@MainActivity
            isBound = true

            bleService?.let {
                onStateChanged(it.currentState)
                if (it.batteryLevel >= 0) {
                    onBatteryUpdated(it.batteryLevel)
                }
                if (pendingConnectOnBind && it.currentState == BleGattManager.ConnectionState.DISCONNECTED) {
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
        setContentView(R.layout.activity_main)

        mappingStorage = MappingStorage(this)

        initViews()
        setupListeners()
        populateMappingList()
        updateBatteryOptButtonState()
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvBattery = findViewById(R.id.tvBattery)
        btnConnect = findViewById(R.id.btnConnect)
        cbHaptic = findViewById(R.id.cbHaptic)
        cbSound = findViewById(R.id.cbSound)
        btnBatteryOpt = findViewById(R.id.btnBatteryOpt)
        llButtonsList = findViewById(R.id.llButtonsList)
        tvLog = findViewById(R.id.tvLog)
        svLog = findViewById(R.id.svLog)
        btnLogClear = findViewById(R.id.btnLogClear)

        cbHaptic.isChecked = mappingStorage.isHapticFeedbackEnabled()
        cbSound.isChecked = mappingStorage.isSoundFeedbackEnabled()
    }

    private fun setupListeners() {
        btnConnect.setOnClickListener {
            onConnectButtonClicked()
        }

        cbHaptic.setOnCheckedChangeListener { _, isChecked ->
            mappingStorage.setHapticFeedbackEnabled(isChecked)
            log("Vibrazione feedback: " + (if (isChecked) "Attiva" else "Disattivata"))
        }

        cbSound.setOnCheckedChangeListener { _, isChecked ->
            mappingStorage.setSoundFeedbackEnabled(isChecked)
            log("Beep audio feedback: " + (if (isChecked) "Attivo" else "Disattivato"))
        }

        btnBatteryOpt.setOnClickListener {
            requestIgnoreBatteryOptimization()
        }

        btnLogClear.setOnClickListener {
            tvLog.text = "[LOG PULITO]"
        }
    }

    private fun onConnectButtonClicked() {
        bleService?.let { service ->
            when (service.currentState) {
                BleGattManager.ConnectionState.DISCONNECTED -> {
                    checkPermissionsAndConnect()
                }
                BleGattManager.ConnectionState.CONNECTING,
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed.add(Manifest.permission.BLUETOOTH_SCAN)
            needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            needed.add(Manifest.permission.ANSWER_PHONE_CALLS)
        }

        val missing = needed.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            log("Richiesta permessi: ${missing.joinToString()}")
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
                log("Tutti i permessi concessi.")
                startAndBindBleService()
                bleService?.connectDevice()
            } else {
                log("Alcuni permessi sono stati negati.")
                startAndBindBleService()
                bleService?.connectDevice()
            }
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

    override fun onResume() {
        super.onResume()
        updateBatteryOptButtonState()
    }

    // Callbacks del Service
    override fun onStateChanged(state: BleGattManager.ConnectionState) {
        runOnUiThread {
            when (state) {
                BleGattManager.ConnectionState.DISCONNECTED -> {
                    tvStatus.text = "Stato: Disconnesso"
                    tvStatus.setTextColor(Color.parseColor("#64748B"))
                    btnConnect.text = "Connetti"
                    btnConnect.backgroundTintList = toColorStateList(Color.parseColor("#0284C7"))
                }
                BleGattManager.ConnectionState.CONNECTING -> {
                    tvStatus.text = "Stato: Connessione in corso..."
                    tvStatus.setTextColor(Color.parseColor("#EAB308"))
                    btnConnect.text = "Annulla"
                    btnConnect.backgroundTintList = toColorStateList(Color.parseColor("#CA8A04"))
                }
                BleGattManager.ConnectionState.CONNECTED -> {
                    tvStatus.text = "Stato: Connesso"
                    tvStatus.setTextColor(Color.parseColor("#16A34A"))
                    btnConnect.text = "Disconnetti"
                    btnConnect.backgroundTintList = toColorStateList(Color.parseColor("#DC2626"))
                }
            }
        }
    }

    override fun onGestureExecuted(button: Br80Button, gesture: GestureType) {
        runOnUiThread {
            val time = timeFormat.format(Date())
            log("[$time] AZIONE ESEGUITA -> ${button.name} [${gesture.name}]")
        }
    }

    override fun onBatteryUpdated(level: Int) {
        runOnUiThread {
            tvBattery.text = "Batteria: $level%"
            tvBattery.setTextColor(if (level <= 20) Color.parseColor("#DC2626") else Color.parseColor("#0EA5E9"))
        }
    }

    override fun onLog(message: String) {
        runOnUiThread {
            log(message)
        }
    }

    private fun log(message: String) {
        val time = timeFormat.format(Date())
        tvLog.append("\n[$time] $message")
        svLog.post {
            svLog.fullScroll(View.FOCUS_DOWN)
        }
    }

    // UI Mappatura Tasti & Gesti
    private fun populateMappingList() {
        llButtonsList.removeAllViews()

        for (btn in Br80Button.values()) {
            val buttonCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(8, 8, 8, 12)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, 8)
                layoutParams = params
                setBackgroundColor(Color.parseColor("#F8FAFC"))
            }

            val btnTitle = TextView(this).apply {
                text = "${btn.displayName} (${btn.name})"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#0F172A"))
                setPadding(0, 0, 0, 4)
            }
            buttonCard.addView(btnTitle)

            for (gesture in GestureType.values()) {
                val action = mappingStorage.getAction(btn, gesture)

                val gestureRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(8, 6, 8, 6)
                    isClickable = true
                    isFocusable = true
                    setBackgroundColor(Color.parseColor("#FFFFFF"))
                    setOnClickListener {
                        showActionPicker(btn, gesture)
                    }
                }

                val tvGesture = TextView(this).apply {
                    text = "• ${gesture.displayName}:"
                    textSize = 13f
                    setTextColor(Color.parseColor("#475569"))
                    val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
                    layoutParams = params
                }

                val tvAction = TextView(this).apply {
                    text = action.getReadableDescription()
                    textSize = 13f
                    setTextColor(if (action.type == ActionType.NONE) Color.parseColor("#94A3B8") else Color.parseColor("#0284C7"))
                    setTypeface(null, if (action.type == ActionType.NONE) Typeface.NORMAL else Typeface.BOLD)
                    val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
                    layoutParams = params
                    gravity = Gravity.END
                }

                gestureRow.addView(tvGesture)
                gestureRow.addView(tvAction)
                buttonCard.addView(gestureRow)
            }

            llButtonsList.addView(buttonCard)
        }
    }

    private fun showActionPicker(button: Br80Button, gesture: GestureType) {
        val actions = ActionType.values()
        val actionNames = actions.map { it.displayName }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("${button.displayName} — ${gesture.displayName}")
            .setItems(actionNames) { _, which ->
                val selectedType = actions[which]
                when {
                    selectedType == ActionType.OPEN_APP -> {
                        showAppPicker(button, gesture)
                    }
                    selectedType == ActionType.START_NAVIGATION -> {
                        showDestinationPicker(button, gesture)
                    }
                    else -> {
                        mappingStorage.setAction(button, gesture, ButtonAction(selectedType))
                        populateMappingList()
                        log("Mappatura aggiornata: ${button.name}_${gesture.name} -> ${selectedType.displayName}")
                    }
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showAppPicker(button: Br80Button, gesture: GestureType) {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(intent, 0)
            .sortedBy { it.loadLabel(pm).toString() }

        val appLabels = apps.map { it.loadLabel(pm).toString() }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Seleziona Applicazione da aprire")
            .setItems(appLabels) { _, which ->
                val selectedApp = apps[which]
                val pkgName = selectedApp.activityInfo.packageName
                val appName = selectedApp.loadLabel(pm).toString()

                mappingStorage.setAction(button, gesture, ButtonAction(ActionType.OPEN_APP, pkgName))
                populateMappingList()
                log("Mappatura aggiornata: ${button.name}_${gesture.name} -> Apri $appName ($pkgName)")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showDestinationPicker(button: Br80Button, gesture: GestureType) {
        val input = EditText(this).apply {
            hint = "Es. Casa, Roma Termini, o coordinate GPS"
            setText(mappingStorage.getAction(button, gesture).parameter)
        }

        AlertDialog.Builder(this)
            .setTitle("Destinazione Navigazione")
            .setMessage("Inserisci l'indirizzo o destinazione per Google Maps:")
            .setView(input)
            .setPositiveButton("Salva") { _, _ ->
                val dest = input.text.toString().trim()
                mappingStorage.setAction(button, gesture, ButtonAction(ActionType.START_NAVIGATION, dest))
                populateMappingList()
                log("Mappatura aggiornata: ${button.name}_${gesture.name} -> Naviga verso '$dest'")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun updateBatteryOptButtonState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isIgnoring = pm?.isIgnoringBatteryOptimizations(packageName) == true
            if (isIgnoring) {
                btnBatteryOpt.text = "Doze: Escluso ✓"
                btnBatteryOpt.isEnabled = false
                btnBatteryOpt.backgroundTintList = toColorStateList(Color.parseColor("#16A34A"))
            } else {
                btnBatteryOpt.text = "Disattiva Doze"
                btnBatteryOpt.isEnabled = true
                btnBatteryOpt.backgroundTintList = toColorStateList(Color.parseColor("#0284C7"))
            }
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm?.isIgnoringBatteryOptimizations(packageName) == false) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    log("Impossibile aprire impostazioni Doze: ${e.message}")
                }
            }
        }
    }

    private fun toColorStateList(color: Int) = android.content.res.ColorStateList.valueOf(color)

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 100
    }
}
