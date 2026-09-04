package com.br80.remote

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
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
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), BleForegroundService.BleServiceListener {

    private var bleService: BleForegroundService? = null
    private var isBound = false
    private var pendingConnectOnBind = false

    private lateinit var mappingStorage: MappingStorage

    // Header Views
    private lateinit var viewStatusDot: View
    private lateinit var tvHeaderStatus: TextView
    private lateinit var tvHeaderBattery: TextView
    private lateinit var btnQuickConnect: Button

    // Gauge cruscotto (batteria / RSSI) e card ultima azione
    private lateinit var gaugeBattery: ArcGaugeView
    private lateinit var tvGaugeBatteryValue: TextView
    private lateinit var gaugeRssi: ArcGaugeView
    private lateinit var tvGaugeRssiValue: TextView
    private lateinit var tvLastActionTitle: TextView
    private lateinit var tvLastActionSub: TextView

    // Tab Containers
    private lateinit var tabController: ScrollView
    private lateinit var tabOptions: ScrollView
    private lateinit var tabLog: LinearLayout

    // Bottom Navigation
    private lateinit var navBtnController: LinearLayout
    private lateinit var navBtnOptions: LinearLayout
    private lateinit var navBtnLog: LinearLayout
    private lateinit var tvNavTextController: TextView
    private lateinit var tvNavTextOptions: TextView
    private lateinit var tvNavTextLog: TextView

    // Controller Pad Buttons
    private lateinit var btnPadUp: Button
    private lateinit var btnPadDown: Button
    private lateinit var btnPadLeft: Button
    private lateinit var btnPadRight: Button
    private lateinit var btnPadHome: Button
    private lateinit var btnPadCamera: Button
    private lateinit var btnPadCall: Button

    // Selected Button Card
    private var currentSelectedButton: Br80Button = Br80Button.UP
    private lateinit var tvSelectedButtonTitle: TextView
    private lateinit var rowGestureSingle: LinearLayout
    private lateinit var tvActionSingle: TextView
    private lateinit var rowGestureDouble: LinearLayout
    private lateinit var tvActionDouble: TextView
    private lateinit var rowGestureTriple: LinearLayout
    private lateinit var tvActionTriple: TextView
    private lateinit var rowGestureLong: LinearLayout
    private lateinit var tvActionLong: TextView

    // Options Tab Views
    private lateinit var tvCurrentTapSpeed: TextView
    private lateinit var btnPresetFast: Button
    private lateinit var btnPresetStd: Button
    private lateinit var btnPresetGloves: Button
    private lateinit var btnPresetSlow: Button
    private lateinit var cbOptBoot: CheckBox
    private lateinit var cbOptKeepAlive: CheckBox
    private lateinit var cbOptConditionalBt: CheckBox
    private lateinit var tvConditionalBtDevice: TextView
    private lateinit var btnOptChooseBtDevice: Button
    private lateinit var cbOptAudioBtRouting: CheckBox
    private lateinit var tvAudioBtDevice: TextView
    private lateinit var btnOptChooseAudioBtDevice: Button
    private lateinit var btnManageTtsLabels: Button
    private lateinit var btnOptDoze: Button
    private lateinit var btnOptOverlay: Button
    private lateinit var btnOptAccessibility: Button
    private lateinit var tvPreferredVolumeLevel: TextView
    private lateinit var btnVolPreset25: Button
    private lateinit var btnVolPreset50: Button
    private lateinit var btnVolPreset75: Button
    private lateinit var btnVolPreset100: Button
    private lateinit var cardDeveloperMode: LinearLayout
    private lateinit var cbOptDevAdbHook: CheckBox
    private var versionTapCount = 0
    private var versionTapLastMs = 0L
    private lateinit var cbOptHaptic: CheckBox
    private lateinit var cbOptSound: CheckBox
    private lateinit var cbOptTts: CheckBox
    private lateinit var btnCheckUpdate: Button
    private lateinit var btnOptTaskerExport: Button
    private lateinit var btnExitApp: Button
    private lateinit var tvActiveProfile: TextView
    private lateinit var btnChooseProfile: Button
    private lateinit var btnNewProfile: Button
    private lateinit var btnDeleteProfile: Button

    // Log Tab Views
    private lateinit var tvLogFull: TextView
    private lateinit var svLogFull: ScrollView
    private lateinit var btnLogCopy: TextView
    private lateinit var btnLogExport: TextView
    private lateinit var btnLogClearTab: TextView

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
                val hasSavedMac = !mappingStorage.getLastConnectedMac().isNullOrEmpty()
                if ((pendingConnectOnBind || hasSavedMac) && it.currentState == BleGattManager.ConnectionState.DISCONNECTED) {
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
        setupBottomNav()
        selectButton(Br80Button.UP)
        updateBatteryOptButtonState()
        updateTapSpeedText()

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

    private fun initViews() {
        // Header
        viewStatusDot = findViewById(R.id.viewStatusDot)
        tvHeaderStatus = findViewById(R.id.tvHeaderStatus)
        tvHeaderBattery = findViewById(R.id.tvHeaderBattery)
        btnQuickConnect = findViewById(R.id.btnQuickConnect)

        gaugeBattery = findViewById(R.id.gaugeBattery)
        tvGaugeBatteryValue = findViewById(R.id.tvGaugeBatteryValue)
        gaugeRssi = findViewById(R.id.gaugeRssi)
        tvGaugeRssiValue = findViewById(R.id.tvGaugeRssiValue)
        tvLastActionTitle = findViewById(R.id.tvLastActionTitle)
        tvLastActionSub = findViewById(R.id.tvLastActionSub)

        // Tabs
        tabController = findViewById(R.id.tabController)
        tabOptions = findViewById(R.id.tabOptions)
        tabLog = findViewById(R.id.tabLog)

        // Bottom Nav
        navBtnController = findViewById(R.id.navBtnController)
        navBtnOptions = findViewById(R.id.navBtnOptions)
        navBtnLog = findViewById(R.id.navBtnLog)
        tvNavTextController = findViewById(R.id.tvNavTextController)
        tvNavTextOptions = findViewById(R.id.tvNavTextOptions)
        tvNavTextLog = findViewById(R.id.tvNavTextLog)

        // Pad Buttons
        btnPadUp = findViewById(R.id.btnPadUp)
        btnPadDown = findViewById(R.id.btnPadDown)
        btnPadLeft = findViewById(R.id.btnPadLeft)
        btnPadRight = findViewById(R.id.btnPadRight)
        btnPadHome = findViewById(R.id.btnPadHome)
        btnPadCamera = findViewById(R.id.btnPadCamera)
        btnPadCall = findViewById(R.id.btnPadCall)

        // Selected Button Card
        tvSelectedButtonTitle = findViewById(R.id.tvSelectedButtonTitle)
        rowGestureSingle = findViewById(R.id.rowGestureSingle)
        tvActionSingle = findViewById(R.id.tvActionSingle)
        rowGestureDouble = findViewById(R.id.rowGestureDouble)
        tvActionDouble = findViewById(R.id.tvActionDouble)
        rowGestureTriple = findViewById(R.id.rowGestureTriple)
        tvActionTriple = findViewById(R.id.tvActionTriple)
        rowGestureLong = findViewById(R.id.rowGestureLong)
        tvActionLong = findViewById(R.id.tvActionLong)

        // Options
        tvCurrentTapSpeed = findViewById(R.id.tvCurrentTapSpeed)
        btnPresetFast = findViewById(R.id.btnPresetFast)
        btnPresetStd = findViewById(R.id.btnPresetStd)
        btnPresetGloves = findViewById(R.id.btnPresetGloves)
        btnPresetSlow = findViewById(R.id.btnPresetSlow)
        cbOptBoot = findViewById(R.id.cbOptBoot)
        cbOptKeepAlive = findViewById(R.id.cbOptKeepAlive)
        cbOptConditionalBt = findViewById(R.id.cbOptConditionalBt)
        tvConditionalBtDevice = findViewById(R.id.tvConditionalBtDevice)
        btnOptChooseBtDevice = findViewById(R.id.btnOptChooseBtDevice)
        cbOptAudioBtRouting = findViewById(R.id.cbOptAudioBtRouting)
        tvAudioBtDevice = findViewById(R.id.tvAudioBtDevice)
        btnOptChooseAudioBtDevice = findViewById(R.id.btnOptChooseAudioBtDevice)
        btnManageTtsLabels = findViewById(R.id.btnManageTtsLabels)
        btnOptDoze = findViewById(R.id.btnOptDoze)
        btnOptOverlay = findViewById(R.id.btnOptOverlay)
        btnOptAccessibility = findViewById(R.id.btnOptAccessibility)
        tvPreferredVolumeLevel = findViewById(R.id.tvPreferredVolumeLevel)
        btnVolPreset25 = findViewById(R.id.btnVolPreset25)
        btnVolPreset50 = findViewById(R.id.btnVolPreset50)
        btnVolPreset75 = findViewById(R.id.btnVolPreset75)
        btnVolPreset100 = findViewById(R.id.btnVolPreset100)
        cardDeveloperMode = findViewById(R.id.cardDeveloperMode)
        cbOptDevAdbHook = findViewById(R.id.cbOptDevAdbHook)
        cbOptHaptic = findViewById(R.id.cbOptHaptic)
        cbOptSound = findViewById(R.id.cbOptSound)
        cbOptTts = findViewById(R.id.cbOptTts)
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)
        btnOptTaskerExport = findViewById(R.id.btnOptTaskerExport)
        btnExitApp = findViewById(R.id.btnExitApp)
        tvActiveProfile = findViewById(R.id.tvActiveProfile)
        btnChooseProfile = findViewById(R.id.btnChooseProfile)
        btnNewProfile = findViewById(R.id.btnNewProfile)
        btnDeleteProfile = findViewById(R.id.btnDeleteProfile)
        updateActiveProfileLabel()
        val tvAppVersionInfo = findViewById<TextView>(R.id.tvAppVersionInfo)
        tvAppVersionInfo.text =
            "Livall BR80 Remote v${BuildConfig.VERSION_NAME} • Open Source\nSupporta telecomandi Livall BR80 / BlingRemote"
        tvAppVersionInfo.setOnClickListener { onVersionInfoTapped() }

        tvPreferredVolumeLevel.text = "Livello attuale: ${mappingStorage.getPreferredVolumeLevelPercent()}%"
        if (BuildConfig.DEBUG && mappingStorage.isDeveloperModeEnabled()) {
            cardDeveloperMode.visibility = android.view.View.VISIBLE
        }
        cbOptDevAdbHook.isChecked = mappingStorage.isDeveloperModeEnabled()

        cbOptBoot.isChecked = mappingStorage.isAutoStartOnBootEnabled()
        cbOptKeepAlive.isChecked = mappingStorage.isKeepAliveEnabled()
        cbOptConditionalBt.isChecked = mappingStorage.isConditionalBtEnabled()
        updateConditionalBtDeviceLabel()
        cbOptAudioBtRouting.isChecked = mappingStorage.isAudioBtRoutingEnabled()
        updateAudioBtDeviceLabel()
        cbOptHaptic.isChecked = mappingStorage.isHapticFeedbackEnabled()
        cbOptSound.isChecked = mappingStorage.isSoundFeedbackEnabled()
        cbOptTts.isChecked = mappingStorage.isTtsFeedbackEnabled()

        // Log Tab
        tvLogFull = findViewById(R.id.tvLogFull)
        svLogFull = findViewById(R.id.svLogFull)
        btnLogCopy = findViewById(R.id.btnLogCopy)
        btnLogExport = findViewById(R.id.btnLogExport)
        btnLogClearTab = findViewById(R.id.btnLogClearTab)
    }

    private fun setupListeners() {
        btnQuickConnect.setOnClickListener {
            onConnectButtonClicked()
        }

        // Pad Buttons Click Listeners
        btnPadUp.setOnClickListener { selectButton(Br80Button.UP) }
        btnPadDown.setOnClickListener { selectButton(Br80Button.DOWN) }
        btnPadLeft.setOnClickListener { selectButton(Br80Button.LEFT) }
        btnPadRight.setOnClickListener { selectButton(Br80Button.RIGHT) }
        btnPadHome.setOnClickListener { selectButton(Br80Button.HOME) }
        btnPadCamera.setOnClickListener { selectButton(Br80Button.CAMERA) }
        btnPadCall.setOnClickListener { selectButton(Br80Button.CALL) }

        // Gestures Click Listeners
        rowGestureSingle.setOnClickListener { showActionPicker(currentSelectedButton, GestureType.SINGLE) }
        rowGestureDouble.setOnClickListener { showActionPicker(currentSelectedButton, GestureType.DOUBLE) }
        rowGestureTriple.setOnClickListener { showActionPicker(currentSelectedButton, GestureType.TRIPLE) }
        rowGestureLong.setOnClickListener { showActionPicker(currentSelectedButton, GestureType.LONG) }

        // Tieni premuto su un gesto per personalizzare il testo pronunciato dal TTS
        rowGestureSingle.setOnLongClickListener { showCustomTtsLabelDialog(currentSelectedButton, GestureType.SINGLE); true }
        rowGestureDouble.setOnLongClickListener { showCustomTtsLabelDialog(currentSelectedButton, GestureType.DOUBLE); true }
        rowGestureTriple.setOnLongClickListener { showCustomTtsLabelDialog(currentSelectedButton, GestureType.TRIPLE); true }
        rowGestureLong.setOnLongClickListener { showCustomTtsLabelDialog(currentSelectedButton, GestureType.LONG); true }

        // Presets rapidi ritmo tap
        btnPresetFast.setOnClickListener { setTapSpeedPreset(280L, "Sportivo (280ms)") }
        btnPresetStd.setOnClickListener { setTapSpeedPreset(420L, "Standard (420ms)") }
        btnPresetGloves.setOnClickListener { setTapSpeedPreset(550L, "Guanti (550ms)") }
        btnPresetSlow.setOnClickListener { setTapSpeedPreset(700L, "Lento (700ms)") }

        // Options Listeners
        cbOptBoot.setOnCheckedChangeListener { _, isChecked ->
            mappingStorage.setAutoStartOnBootEnabled(isChecked)
            log("Avvio automatico al boot: " + if (isChecked) "ATTIVO" else "DISATTIVATO")
        }

        cbOptKeepAlive.setOnCheckedChangeListener { _, isChecked ->
            mappingStorage.setKeepAliveEnabled(isChecked)
            if (isChecked && cbOptConditionalBt.isChecked) {
                cbOptConditionalBt.isChecked = false
                mappingStorage.setConditionalBtEnabled(false)
                log("Keep-Alive condizionale disattivato: incompatibile con Keep-Alive Always-On.")
            }
            bleService?.gattManager?.startKeepAliveIfEnabled()
            log("Keep-Alive impostato a: " + if (isChecked) "ATTIVO (Ping ogni 35s)" else "DISATTIVATO")
        }

        cbOptConditionalBt.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && mappingStorage.getConditionalBtDevices().isEmpty()) {
                Toast.makeText(this, "Seleziona prima almeno un dispositivo BT dall'elenco qui sotto.", Toast.LENGTH_LONG).show()
                cbOptConditionalBt.isChecked = false
                return@setOnCheckedChangeListener
            }
            mappingStorage.setConditionalBtEnabled(isChecked)
            if (isChecked && cbOptKeepAlive.isChecked) {
                cbOptKeepAlive.isChecked = false
                mappingStorage.setKeepAliveEnabled(false)
                log("Keep-Alive Always-On disattivato: incompatibile con Keep-Alive condizionale.")
            }
            log("Keep-Alive condizionale a dispositivo BT: " + if (isChecked) "ATTIVO" else "DISATTIVATO")
        }

        btnOptChooseBtDevice.setOnClickListener {
            showBondedDeviceMultiPickerDialog(mappingStorage.getConditionalBtDevices()) { selected ->
                mappingStorage.setConditionalBtDevices(selected)
                updateConditionalBtDeviceLabel()
                log("Dispositivi BT condizionali impostati: ${selected.joinToString(", ") { it.second }}")
            }
        }

        cbOptAudioBtRouting.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && mappingStorage.getAudioBtDevices().isEmpty()) {
                Toast.makeText(this, "Seleziona prima almeno un dispositivo audio dall'elenco qui sotto.", Toast.LENGTH_LONG).show()
                cbOptAudioBtRouting.isChecked = false
                return@setOnCheckedChangeListener
            }
            mappingStorage.setAudioBtRoutingEnabled(isChecked)
            log("Canale voce garantito verso interfono: " + if (isChecked) "ATTIVO" else "DISATTIVATO")
        }

        btnOptChooseAudioBtDevice.setOnClickListener {
            showBondedDeviceMultiPickerDialog(mappingStorage.getAudioBtDevices()) { selected ->
                mappingStorage.setAudioBtDevices(selected)
                updateAudioBtDeviceLabel()
                log("Dispositivi audio per TTS/Comandi impostati: ${selected.joinToString(", ") { it.second }}")
            }
        }

        btnManageTtsLabels.setOnClickListener {
            showManageTtsLabelsDialog()
        }

        cbOptHaptic.setOnCheckedChangeListener { _, isChecked ->
            mappingStorage.setHapticFeedbackEnabled(isChecked)
            log("Vibrazione feedback: " + if (isChecked) "Attiva" else "Disattivata")
        }

        cbOptSound.setOnCheckedChangeListener { _, isChecked ->
            mappingStorage.setSoundFeedbackEnabled(isChecked)
            log("Beep audio feedback: " + if (isChecked) "Attivo" else "Disattivato")
        }

        cbOptTts.setOnCheckedChangeListener { _, isChecked ->
            mappingStorage.setTtsFeedbackEnabled(isChecked)
            log("Annuncio vocale (TTS): " + if (isChecked) "Attivo" else "Disattivato")
            if (isChecked) {
                bleService?.ttsFeedbackManager?.speak("Annuncio vocale attivato")
            }
        }

        btnOptDoze.setOnClickListener {
            requestIgnoreBatteryOptimization()
        }

        btnOptOverlay.setOnClickListener {
            requestOverlayPermission()
        }

        btnOptAccessibility.setOnClickListener {
            requestAccessibilityPermission()
        }

        btnVolPreset25.setOnClickListener { setPreferredVolumePreset(25) }
        btnVolPreset50.setOnClickListener { setPreferredVolumePreset(50) }
        btnVolPreset75.setOnClickListener { setPreferredVolumePreset(75) }
        btnVolPreset100.setOnClickListener { setPreferredVolumePreset(100) }

        cbOptDevAdbHook.setOnCheckedChangeListener { _, isChecked ->
            mappingStorage.setDeveloperModeEnabled(isChecked)
            log("Opzione sviluppatore - Gancio Comandi ADB: " + if (isChecked) "Attivo" else "Disattivato")
        }

        btnCheckUpdate.setOnClickListener {
            AppUpdateManager.checkForUpdates(this, isManualCheck = true)
        }

        btnChooseProfile.setOnClickListener { showChooseProfileDialog() }
        btnNewProfile.setOnClickListener { showNewProfileDialog() }
        btnDeleteProfile.setOnClickListener { showDeleteProfileDialog() }

        btnOptTaskerExport.setOnClickListener {
            TaskerExporter.exportAndShare(this)
            log("Progetto Tasker XML esportato.")
        }

        btnExitApp.setOnClickListener {
            AlertDialog.Builder(this, R.style.Theme_Br80_CockpitDialog)
                .setTitle("Esci dall'applicazione")
                .setMessage("L'app verrà chiusa completamente e il servizio in background verrà interrotto. Il telecomando smetterà di funzionare finché non riapri l'app. Continuare?")
                .setPositiveButton("Esci") { _, _ ->
                    exitApplication()
                }
                .setNegativeButton("Annulla", null)
                .show()
        }

        // Log Tab Listeners
        btnLogCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("BR80 Log", tvLogFull.text)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(this, "Log copiato negli appunti!", Toast.LENGTH_SHORT).show()
        }

        btnLogClearTab.setOnClickListener {
            tvLogFull.text = "[LOG PULITO]"
        }

        btnLogExport.setOnClickListener {
            exportLogToFile()
        }
    }

    private fun exportLogToFile() {
        try {
            val logsDir = File(cacheDir, "logs").apply { mkdirs() }
            val fileName = "BR80_Log_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.txt"
            val logFile = File(logsDir, fileName)
            logFile.writeText(tvLogFull.text.toString())

            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                logFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Esporta Log Diagnostico"))
        } catch (e: Exception) {
            Toast.makeText(this, "Errore esportazione log: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setTapSpeedPreset(ms: Long, name: String) {
        mappingStorage.setMultiTapWindowMs(ms)
        updateTapSpeedText()
        Toast.makeText(this, "Profilo impostato: $name", Toast.LENGTH_SHORT).show()
        log("Velocità multi-tap impostata a $ms ms ($name)")
    }

    private fun updateTapSpeedText() {
        val current = mappingStorage.getMultiTapWindowMs()
        val desc = when {
            current <= 300L -> "Sportivo"
            current <= 450L -> "Standard"
            current <= 600L -> "Guanti"
            else -> "Personalizzato"
        }
        tvCurrentTapSpeed.text = "Finestra Doppio Tap: $current ms ($desc)"
    }

    private fun setupBottomNav() {
        navBtnController.setOnClickListener { switchTab(0) }
        navBtnOptions.setOnClickListener { switchTab(1) }
        navBtnLog.setOnClickListener { switchTab(2) }
    }

    private fun switchTab(tabIndex: Int) {
        tabController.visibility = if (tabIndex == 0) View.VISIBLE else View.GONE
        tabOptions.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE
        tabLog.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE

        tvNavTextController.setTextColor(toColorStateList(if (tabIndex == 0) Color.parseColor("#E0140F") else Color.parseColor("#A3927B")))
        tvNavTextOptions.setTextColor(toColorStateList(if (tabIndex == 1) Color.parseColor("#E0140F") else Color.parseColor("#A3927B")))
        tvNavTextLog.setTextColor(toColorStateList(if (tabIndex == 2) Color.parseColor("#E0140F") else Color.parseColor("#A3927B")))
    }

    // Selezione del tasto del D-Pad
    private fun selectButton(button: Br80Button) {
        currentSelectedButton = button

        // Evidenzia il tasto selezionato sulla sagoma reale del telecomando: il corpo/hub resta
        // fisso (drawable), cambia solo il colore/opacità del testo dell'elemento selezionato.
        val chevronDefault = Color.parseColor("#75797F")
        val chevronActive = Color.parseColor("#F2E6D4")

        btnPadUp.setTextColor(if (button == Br80Button.UP) chevronActive else chevronDefault)
        btnPadDown.setTextColor(if (button == Br80Button.DOWN) chevronActive else chevronDefault)
        btnPadLeft.setTextColor(if (button == Br80Button.LEFT) chevronActive else chevronDefault)
        btnPadRight.setTextColor(if (button == Br80Button.RIGHT) chevronActive else chevronDefault)
        btnPadCamera.alpha = if (button == Br80Button.CAMERA) 1f else 0.55f
        btnPadCall.alpha = if (button == Br80Button.CALL) 1f else 0.55f
        btnPadHome.alpha = if (button == Br80Button.HOME) 1f else 0.9f

        // Aggiorna scheda gesti
        tvSelectedButtonTitle.text = "${button.displayName} (${button.name})"

        val actSingle = mappingStorage.getAction(button, GestureType.SINGLE)
        tvActionSingle.text = actSingle.getReadableDescription()
        tvActionSingle.setTextColor(if (actSingle.type == ActionType.NONE) Color.parseColor("#A3927B") else Color.parseColor("#E0140F"))

        val actDouble = mappingStorage.getAction(button, GestureType.DOUBLE)
        tvActionDouble.text = actDouble.getReadableDescription()
        tvActionDouble.setTextColor(if (actDouble.type == ActionType.NONE) Color.parseColor("#A3927B") else Color.parseColor("#E0140F"))

        val actTriple = mappingStorage.getAction(button, GestureType.TRIPLE)
        tvActionTriple.text = actTriple.getReadableDescription()
        tvActionTriple.setTextColor(if (actTriple.type == ActionType.NONE) Color.parseColor("#A3927B") else Color.parseColor("#E0140F"))

        val actLong = mappingStorage.getAction(button, GestureType.LONG)
        tvActionLong.text = actLong.getReadableDescription()
        tvActionLong.setTextColor(if (actLong.type == ActionType.NONE) Color.parseColor("#A3927B") else Color.parseColor("#E0140F"))
    }

    // Dialog Selezione Azioni con Categorie Collassabili (chiuse all'avvio) e Ricerca Testuale
    private fun showActionPicker(button: Br80Button, gesture: GestureType) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_action_picker, null)
        val etSearch = dialogView.findViewById<EditText>(R.id.etActionSearch)
        val llContainer = dialogView.findViewById<LinearLayout>(R.id.llActionsContainer)

        val dialog = AlertDialog.Builder(this, R.style.Theme_Br80_CockpitDialog)
            .setTitle("${button.displayName} — ${gesture.displayName}")
            .setView(dialogView)
            .setNegativeButton("Annulla", null)
            .create()

        val expandedCategories = mutableMapOf<ActionCategory, Boolean>()

        fun populateList(query: String) {
            llContainer.removeAllViews()
            val allActions = ActionType.values()
            val isSearching = query.isNotBlank()

            val filtered = if (!isSearching) {
                allActions.toList()
            } else {
                allActions.filter {
                    it.displayName.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true) ||
                    it.category.displayName.contains(query, ignoreCase = true)
                }
            }

            val grouped = filtered.groupBy { it.category }

            for ((category, actions) in grouped) {
                val isExpanded = if (isSearching) true else (expandedCategories[category] == true)

                val headerLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(12, 12, 12, 12)
                    setBackgroundColor(Color.parseColor("#382F24"))
                    isClickable = true
                    isFocusable = true
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.setMargins(0, 4, 0, 2)
                    layoutParams = params
                }

                val arrowIcon = TextView(this).apply {
                    text = if (isExpanded) "▼" else "▶"
                    textSize = 12f
                    setTextColor(Color.parseColor("#A3927B"))
                    setPadding(0, 0, 8, 0)
                }

                val catTitle = TextView(this).apply {
                    text = "${category.icon} ${category.displayName} (${actions.size})"
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#F2E6D4"))
                    val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    layoutParams = p
                }

                headerLayout.addView(arrowIcon)
                headerLayout.addView(catTitle)

                val actionsContainer = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = if (isExpanded) View.VISIBLE else View.GONE
                    setPadding(8, 0, 0, 4)
                }

                for (action in actions) {
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(12, 8, 12, 8)
                        setBackgroundColor(Color.parseColor("#241F19"))
                        isClickable = true
                        isFocusable = true
                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        params.setMargins(0, 1, 0, 2)
                        layoutParams = params

                        setOnClickListener {
                            dialog.dismiss()
                            handleActionSelection(button, gesture, action)
                        }
                    }

                    val tvTitle = TextView(this).apply {
                        text = action.displayName
                        textSize = 13.5f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(if (action == ActionType.NONE) Color.parseColor("#A3927B") else Color.parseColor("#E0140F"))
                    }

                    val tvDesc = TextView(this).apply {
                        text = action.description
                        textSize = 12f
                        setTextColor(Color.parseColor("#A3927B"))
                    }

                    row.addView(tvTitle)
                    row.addView(tvDesc)
                    actionsContainer.addView(row)
                }

                headerLayout.setOnClickListener {
                    val currentlyExpanded = actionsContainer.visibility == View.VISIBLE
                    val nextState = !currentlyExpanded
                    expandedCategories[category] = nextState
                    actionsContainer.visibility = if (nextState) View.VISIBLE else View.GONE
                    arrowIcon.text = if (nextState) "▼" else "▶"
                }

                llContainer.addView(headerLayout)
                llContainer.addView(actionsContainer)
            }
        }

        populateList("")

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                populateList(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        dialog.show()
    }

    private fun handleActionSelection(button: Br80Button, gesture: GestureType, action: ActionType) {
        when (action) {
            ActionType.OPEN_APP -> showAppPicker(button, gesture)
            ActionType.START_NAVIGATION -> showDestinationPicker(button, gesture)
            ActionType.PHONE_SPEED_DIAL -> showSpeedDialPicker(button, gesture)
            else -> {
                mappingStorage.setAction(button, gesture, ButtonAction(action))
                selectButton(button)
                log("Mappatura: ${button.name}_${gesture.name} -> ${action.displayName}")
            }
        }
    }

    private fun showAppPicker(button: Br80Button, gesture: GestureType) {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(intent, 0)
            .sortedBy { it.loadLabel(pm).toString() }

        val appLabels = apps.map { it.loadLabel(pm).toString() }.toTypedArray()

        AlertDialog.Builder(this, R.style.Theme_Br80_CockpitDialog)
            .setTitle("Seleziona Applicazione da aprire")
            .setItems(appLabels) { _, which ->
                val selectedApp = apps[which]
                val pkgName = selectedApp.activityInfo.packageName
                val appName = selectedApp.loadLabel(pm).toString()

                mappingStorage.setAction(button, gesture, ButtonAction(ActionType.OPEN_APP, pkgName))
                selectButton(button)
                log("Mappatura: ${button.name}_${gesture.name} -> Apri $appName ($pkgName)")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    // Dialog "Gestisci Testi TTS" raggruppato per tasto fisico (badge UP/CALL/CAMERA...),
    // tag di gesto in monospace (1x/2x/3x/LONG) e chip "personalizzato" solo quando serve.
    private fun showManageTtsLabelsDialog() {
        val entries = mutableListOf<Pair<Br80Button, GestureType>>()
        for (button in Br80Button.values()) {
            for (gesture in GestureType.values()) {
                val action = mappingStorage.getAction(button, gesture)
                if (action.type != ActionType.NONE) {
                    entries.add(button to gesture)
                }
            }
        }

        if (entries.isEmpty()) {
            Toast.makeText(this, "Nessuna azione mappata su cui personalizzare il TTS.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_manage_tts_labels, null)
        val container = dialogView.findViewById<LinearLayout>(R.id.llTtsLabelsContainer)

        fun populateTtsList() {
            container.removeAllViews()
            val grouped = entries.groupBy({ it.first }, { it.second })

            for ((button, gestures) in grouped) {
                val groupHead = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(8, 20, 8, 8)
                }

                val badge = TextView(this).apply {
                    text = button.name
                    textSize = 11f
                    setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                    setTextColor(Color.parseColor("#241F19"))
                    background = androidx.core.content.ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_tts_button_badge)
                    setPadding(14, 4, 14, 4)
                }

                val name = TextView(this).apply {
                    text = "  ${button.displayName}"
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#F2E6D4"))
                }

                groupHead.addView(badge)
                groupHead.addView(name)
                container.addView(groupHead)

                for (gesture in gestures) {
                    val action = mappingStorage.getAction(button, gesture)
                    val custom = mappingStorage.getCustomTtsLabel(button, gesture)
                    val ttsText = custom ?: action.getReadableDescription()

                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(12, 10, 10, 10)
                        background = androidx.core.content.ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_tts_row)
                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        params.setMargins(0, 0, 0, 6)
                        layoutParams = params
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            showCustomTtsLabelDialog(button, gesture) { populateTtsList() }
                        }
                    }

                    val gestureTag = TextView(this).apply {
                        text = gesture.tag
                        textSize = 10.5f
                        setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                        setTextColor(Color.parseColor("#FF5147"))
                        background = androidx.core.content.ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_tts_gesture_tag)
                        setPadding(10, 4, 10, 4)
                        minWidth = 60
                        gravity = Gravity.CENTER
                    }

                    val mainCol = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        p.setMargins(16, 0, 8, 0)
                        layoutParams = p
                    }

                    val phrase = TextView(this).apply {
                        text = "“$ttsText”"
                        textSize = 13f
                        setTextColor(Color.parseColor("#F2E6D4"))
                    }
                    mainCol.addView(phrase)

                    if (custom != null) {
                        val customChip = TextView(this).apply {
                            text = "● PERSONALIZZATO"
                            textSize = 9f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.parseColor("#FBBF24"))
                            background = androidx.core.content.ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_tts_custom_chip)
                            setPadding(8, 2, 8, 2)
                            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                            p.topMargin = 4
                            layoutParams = p
                        }
                        mainCol.addView(customChip)
                    }

                    val pencil = TextView(this).apply {
                        text = "✏️"
                        textSize = 13f
                    }

                    row.addView(gestureTag)
                    row.addView(mainCol)
                    row.addView(pencil)
                    container.addView(row)
                }
            }
        }

        populateTtsList()

        AlertDialog.Builder(this, R.style.Theme_Br80_CockpitDialog)
            .setTitle("Gestisci Testi TTS")
            .setView(dialogView)
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun showCustomTtsLabelDialog(button: Br80Button, gesture: GestureType, onSaved: (() -> Unit)? = null) {
        val action = mappingStorage.getAction(button, gesture)
        val currentCustom = mappingStorage.getCustomTtsLabel(button, gesture)

        val input = EditText(this).apply {
            hint = action.getReadableDescription()
            setText(currentCustom ?: "")
            setSelection(text.length)
            setTextColor(Color.parseColor("#F2E6D4"))
            setHintTextColor(Color.parseColor("#A3927B"))
        }

        AlertDialog.Builder(this, R.style.Theme_Br80_CockpitDialog)
            .setTitle("Personalizza Annuncio Vocale")
            .setMessage("${button.displayName} — ${gesture.displayName}\nTesto pronunciato dal TTS per questa azione. Lascia vuoto per usare il testo automatico (\"${action.getReadableDescription()}\").")
            .setView(input)
            .setPositiveButton("Salva") { _, _ ->
                val label = input.text.toString().trim()
                mappingStorage.setCustomTtsLabel(button, gesture, if (label.isEmpty()) null else label)
                log("Testo TTS personalizzato per ${button.name}_${gesture.name}: " + if (label.isEmpty()) "rimosso (torna automatico)" else "\"$label\"")
                onSaved?.invoke()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showDestinationPicker(button: Br80Button, gesture: GestureType) {
        val input = EditText(this).apply {
            hint = "Es. Casa, Lavoro, o coordinate GPS"
            setText(mappingStorage.getAction(button, gesture).parameter)
        }

        AlertDialog.Builder(this, R.style.Theme_Br80_CockpitDialog)
            .setTitle("Destinazione Navigazione")
            .setMessage("Inserisci l'indirizzo o punto per Google Maps:")
            .setView(input)
            .setPositiveButton("Salva") { _, _ ->
                val dest = input.text.toString().trim()
                mappingStorage.setAction(button, gesture, ButtonAction(ActionType.START_NAVIGATION, dest))
                selectButton(button)
                log("Mappatura: ${button.name}_${gesture.name} -> Naviga verso '$dest'")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showSpeedDialPicker(button: Br80Button, gesture: GestureType) {
        val input = EditText(this).apply {
            hint = "Es. +393331234567"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setText(mappingStorage.getAction(button, gesture).parameter)
        }

        AlertDialog.Builder(this, R.style.Theme_Br80_CockpitDialog)
            .setTitle("Numero Chiamata Rapida")
            .setMessage("Inserisci il numero telefonico da chiamare direttamente:")
            .setView(input)
            .setPositiveButton("Salva") { _, _ ->
                val num = input.text.toString().trim()
                mappingStorage.setAction(button, gesture, ButtonAction(ActionType.PHONE_SPEED_DIAL, num))
                selectButton(button)
                log("Mappatura: ${button.name}_${gesture.name} -> Chiama '$num'")
            }
            .setNegativeButton("Annulla", null)
            .show()
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

        needed.add(Manifest.permission.READ_CALL_LOG)
        needed.add(Manifest.permission.CALL_PHONE)

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
                startAndBindBleService()
                bleService?.connectDevice()
            } else {
                Toast.makeText(this, "Permessi negati: impossibile connettersi al telecomando senza autorizzare Bluetooth.", Toast.LENGTH_LONG).show()
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
        if (!isBound) {
            val intent = Intent(this, BleForegroundService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun updateActiveProfileLabel() {
        tvActiveProfile.text = "Profilo Attivo: ${mappingStorage.getActiveProfileName()}"
    }

    private fun showChooseProfileDialog() {
        val profiles = mappingStorage.getProfileNames()
        val current = mappingStorage.getActiveProfileName()
        val checkedIndex = profiles.indexOfFirst { it.equals(current, ignoreCase = true) }.coerceAtLeast(0)

        AlertDialog.Builder(this, R.style.Theme_Br80_CockpitDialog)
            .setTitle("Scegli Profilo di Mappatura")
            .setSingleChoiceItems(profiles.toTypedArray(), checkedIndex) { dialog, which ->
                val chosen = profiles[which]
                mappingStorage.setActiveProfileName(chosen)
                updateActiveProfileLabel()
                selectButton(currentSelectedButton)
                log("Profilo di mappatura attivo: $chosen")
                dialog.dismiss()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showNewProfileDialog() {
        val input = EditText(this).apply {
            hint = "Es. Musica, Navigatore, Sportivo"
        }

        AlertDialog.Builder(this, R.style.Theme_Br80_CockpitDialog)
            .setTitle("Nuovo Profilo di Mappatura")
            .setMessage("Le azioni del nuovo profilo partiranno vuote (Nessuna azione) e potrai personalizzarle liberamente.")
            .setView(input)
            .setPositiveButton("Crea") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Il nome del profilo non può essere vuoto.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (mappingStorage.addProfile(name)) {
                    mappingStorage.setActiveProfileName(name)
                    updateActiveProfileLabel()
                    selectButton(currentSelectedButton)
                    log("Nuovo profilo creato e attivato: $name")
                } else {
                    Toast.makeText(this, "Esiste già un profilo con questo nome.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showDeleteProfileDialog() {
        val profiles = mappingStorage.getProfileNames().filter { !it.equals("Standard", ignoreCase = true) }
        if (profiles.isEmpty()) {
            Toast.makeText(this, "Non ci sono profili personalizzati da eliminare.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this, R.style.Theme_Br80_CockpitDialog)
            .setTitle("Elimina Profilo")
            .setItems(profiles.toTypedArray()) { _, which ->
                val toDelete = profiles[which]
                AlertDialog.Builder(this, R.style.Theme_Br80_CockpitDialog)
                    .setTitle("Conferma Eliminazione")
                    .setMessage("Eliminare definitivamente il profilo \"$toDelete\" e tutte le sue mappature?")
                    .setPositiveButton("Elimina") { _, _ ->
                        mappingStorage.deleteProfile(toDelete)
                        updateActiveProfileLabel()
                        selectButton(currentSelectedButton)
                        log("Profilo eliminato: $toDelete")
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun formatDeviceSetLabel(devices: Set<Pair<String, String>>): String {
        return if (devices.isEmpty()) {
            "Nessun dispositivo selezionato"
        } else {
            "Dispositivi selezionati: " + devices.joinToString(", ") { it.second }
        }
    }

    private fun updateConditionalBtDeviceLabel() {
        tvConditionalBtDevice.text = formatDeviceSetLabel(mappingStorage.getConditionalBtDevices())
    }

    private fun updateAudioBtDeviceLabel() {
        tvAudioBtDevice.text = formatDeviceSetLabel(mappingStorage.getAudioBtDevices())
    }

    /**
     * Mostra un elenco di dispositivi accoppiati a selezione multipla, pre-selezionando
     * quelli già scelti in [currentSelection] (per mac). Conferma con [onSelectionConfirmed].
     */
    @SuppressLint("MissingPermission")
    private fun showBondedDeviceMultiPickerDialog(
        currentSelection: Set<Pair<String, String>>,
        onSelectionConfirmed: (Set<Pair<String, String>>) -> Unit
    ) {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (!hasPermission) {
            Toast.makeText(this, "Permesso Bluetooth mancante: concedilo e riprova.", Toast.LENGTH_LONG).show()
            return
        }

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        val adapter = bluetoothManager?.adapter
        val bondedDevices = adapter?.bondedDevices?.toList() ?: emptyList()

        if (bondedDevices.isEmpty()) {
            Toast.makeText(this, "Nessun dispositivo Bluetooth accoppiato trovato. Accoppia prima l'interfono/casco nelle impostazioni di sistema.", Toast.LENGTH_LONG).show()
            return
        }

        val currentMacs = currentSelection.map { it.first }
        val labels = bondedDevices.map { "${it.name ?: "Sconosciuto"} [${it.address}]" }.toTypedArray()
        val checkedItems = bondedDevices.map { device -> currentMacs.any { it.equals(device.address, ignoreCase = true) } }.toBooleanArray()

        AlertDialog.Builder(this, R.style.Theme_Br80_CockpitDialog)
            .setTitle("Scegli Dispositivi BT (selezione multipla)")
            .setMultiChoiceItems(labels, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Conferma") { _, _ ->
                val selected = bondedDevices.filterIndexed { index, _ -> checkedItems[index] }
                    .map { it.address to (it.name ?: "Sconosciuto") }
                    .toSet()
                onSelectionConfirmed(selected)
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun exitApplication() {
        if (isBound) {
            bleService?.listener = null
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
            bleService?.listener = null
            unbindService(serviceConnection)
            isBound = false
        }
    }

    override fun onResume() {
        super.onResume()
        updateBatteryOptButtonState()
        updateOverlayButtonState()
        updateAccessibilityButtonState()
        updateTapSpeedText()
    }

    // Callbacks del Service
    override fun onStateChanged(state: BleGattManager.ConnectionState) {
        runOnUiThread {
            when (state) {
                BleGattManager.ConnectionState.DISCONNECTED -> {
                    val hasSaved = !mappingStorage.getLastConnectedMac().isNullOrEmpty()
                    if (hasSaved) {
                        viewStatusDot.backgroundTintList = toColorStateList(Color.parseColor("#EAB308"))
                        tvHeaderStatus.text = "In ascolto (Premi un tasto)"
                        tvHeaderStatus.setTextColor(Color.parseColor("#CA8A04"))
                        tvHeaderBattery.text = ""
                        btnQuickConnect.text = "Riconnetti"
                        btnQuickConnect.backgroundTintList = toColorStateList(Color.parseColor("#E0140F"))
                    } else {
                        viewStatusDot.backgroundTintList = toColorStateList(Color.parseColor("#DC2626"))
                        tvHeaderStatus.text = "Disconnesso"
                        tvHeaderStatus.setTextColor(Color.parseColor("#A3927B"))
                        tvHeaderBattery.text = ""
                        btnQuickConnect.text = "Connetti"
                        btnQuickConnect.backgroundTintList = toColorStateList(Color.parseColor("#E0140F"))
                    }
                }
                BleGattManager.ConnectionState.CONNECTING -> {
                    viewStatusDot.backgroundTintList = toColorStateList(Color.parseColor("#EAB308"))
                    tvHeaderStatus.text = "Connessione in corso..."
                    tvHeaderStatus.setTextColor(Color.parseColor("#CA8A04"))
                    btnQuickConnect.text = "Annulla"
                    btnQuickConnect.backgroundTintList = toColorStateList(Color.parseColor("#CA8A04"))
                }
                BleGattManager.ConnectionState.CONNECTED -> {
                    viewStatusDot.backgroundTintList = toColorStateList(Color.parseColor("#16A34A"))
                    tvHeaderStatus.text = "Connesso"
                    tvHeaderStatus.setTextColor(Color.parseColor("#16A34A"))
                    btnQuickConnect.text = "Disconnetti"
                    btnQuickConnect.backgroundTintList = toColorStateList(Color.parseColor("#DC2626"))
                }
            }
        }
    }

    override fun onButtonRawEvent(button: Br80Button, isPress: Boolean) {
        runOnUiThread {
            if (isPress) {
                selectButton(button)
            }
        }
    }

    override fun onGestureExecuted(button: Br80Button, gesture: GestureType) {
        runOnUiThread {
            val time = timeFormat.format(Date())
            log("[$time] AZIONE ESEGUITA -> ${button.name} [${gesture.name}]")

            val action = mappingStorage.getAction(button, gesture)
            tvLastActionTitle.text = "${button.name} — ${gesture.displayName}"
            tvLastActionSub.text = "→ ${action.getReadableDescription().uppercase(Locale.getDefault())}"
        }
    }

    override fun onBatteryUpdated(level: Int) {
        runOnUiThread {
            tvHeaderBattery.text = "• $level% 🔋"
            tvHeaderBattery.setTextColor(if (level <= 20) Color.parseColor("#DC2626") else Color.parseColor("#E0140F"))
            tvGaugeBatteryValue.text = if (level >= 0) "$level%" else "--"
            gaugeBattery.setValue(if (level >= 0) level / 100f else 0f)
        }
    }

    override fun onRssiUpdated(rssi: Int) {
        runOnUiThread {
            tvGaugeRssiValue.text = "$rssi"
            // RSSI tipico BLE tra -100 dBm (segnale minimo) e -30 dBm (segnale massimo)
            val normalized = ((rssi + 100) / 70f).coerceIn(0f, 1f)
            gaugeRssi.setValue(normalized)
        }
    }

    override fun onLog(message: String) {
        runOnUiThread {
            log(message)
        }
    }

    private fun log(message: String) {
        val time = timeFormat.format(Date())
        tvLogFull.append("\n[$time] $message")
        svLogFull.post {
            svLogFull.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun updateBatteryOptButtonState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isIgnoring = pm?.isIgnoringBatteryOptimizations(packageName) == true
            if (isIgnoring) {
                btnOptDoze.text = "Doze: Escluso con Successo ✓"
                btnOptDoze.isEnabled = false
                btnOptDoze.backgroundTintList = toColorStateList(Color.parseColor("#16A34A"))
            } else {
                btnOptDoze.text = "Disattiva Ottimizzazione Batteria (Doze)"
                btnOptDoze.isEnabled = true
                btnOptDoze.backgroundTintList = toColorStateList(Color.parseColor("#475569"))
            }
        }
    }

    private fun updateOverlayButtonState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val canDraw = Settings.canDrawOverlays(this)
            if (canDraw) {
                btnOptOverlay.text = "Avvio su Altre App: Autorizzato ✓"
                btnOptOverlay.isEnabled = false
                btnOptOverlay.backgroundTintList = toColorStateList(Color.parseColor("#16A34A"))
            } else {
                btnOptOverlay.text = "Consenti Avvio su Altre App (Gemini / Mappe)"
                btnOptOverlay.isEnabled = true
                btnOptOverlay.backgroundTintList = toColorStateList(Color.parseColor("#475569"))
            }
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    log("Impossibile aprire impostazioni overlay: ${e.message}")
                }
            }
        }
    }

    private fun updateAccessibilityButtonState() {
        val enabled = Br80AccessibilityService.isRunning()
        if (enabled) {
            btnOptAccessibility.text = "Servizio Accessibilità: Attivo ✓"
            btnOptAccessibility.isEnabled = false
            btnOptAccessibility.backgroundTintList = toColorStateList(Color.parseColor("#16A34A"))
        } else {
            btnOptAccessibility.text = "Attiva Servizio Accessibilità (Indietro / Home / Blocca Schermo)"
            btnOptAccessibility.isEnabled = true
            btnOptAccessibility.backgroundTintList = toColorStateList(Color.parseColor("#475569"))
        }
    }

    private fun requestAccessibilityPermission() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            log("Impossibile aprire impostazioni accessibilità: ${e.message}")
        }
    }

    private fun setPreferredVolumePreset(percent: Int) {
        mappingStorage.setPreferredVolumeLevelPercent(percent)
        tvPreferredVolumeLevel.text = "Livello attuale: $percent%"
        log("Volume preciso preferito impostato a $percent%")
    }

    // Sblocco nascosto dell'opzione sviluppatore: 7 tocchi consecutivi entro 3s sul
    // testo versione, come il "tap sul numero build" di Android.
    private fun onVersionInfoTapped() {
        val now = System.currentTimeMillis()
        if (now - versionTapLastMs > 3000L) {
            versionTapCount = 0
        }
        versionTapLastMs = now
        versionTapCount++
        if (versionTapCount >= 7) {
            versionTapCount = 0
            if (BuildConfig.DEBUG) {
                mappingStorage.setDeveloperModeEnabled(true)
                cbOptDevAdbHook.isChecked = true
                cardDeveloperMode.visibility = android.view.View.VISIBLE
                Toast.makeText(this, "Modalità sviluppatore attivata", Toast.LENGTH_SHORT).show()
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
