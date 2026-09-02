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
import android.os.SystemClock
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
    private lateinit var btnAutoCalibrate: Button
    private lateinit var btnPresetFast: Button
    private lateinit var btnPresetStd: Button
    private lateinit var btnPresetGloves: Button
    private lateinit var btnPresetSlow: Button
    private lateinit var cbOptBoot: CheckBox
    private lateinit var cbOptKeepAlive: CheckBox
    private lateinit var btnOptDoze: Button
    private lateinit var btnOptOverlay: Button
    private lateinit var cbOptHaptic: CheckBox
    private lateinit var cbOptSound: CheckBox
    private lateinit var btnCheckUpdate: Button
    private lateinit var btnOptTaskerExport: Button

    // Calibration Dialog State Callback
    private var activeCalibrationCallback: (() -> Unit)? = null

    // Log Tab Views
    private lateinit var tvLogFull: TextView
    private lateinit var svLogFull: ScrollView
    private lateinit var btnLogCopy: TextView
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

        // Auto-avvio servizio e ascolto se c'è un telecomando già associato
        if (!mappingStorage.getLastConnectedMac().isNullOrEmpty()) {
            pendingConnectOnBind = true
            startAndBindBleService()
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
        btnAutoCalibrate = findViewById(R.id.btnAutoCalibrate)
        btnPresetFast = findViewById(R.id.btnPresetFast)
        btnPresetStd = findViewById(R.id.btnPresetStd)
        btnPresetGloves = findViewById(R.id.btnPresetGloves)
        btnPresetSlow = findViewById(R.id.btnPresetSlow)
        cbOptBoot = findViewById(R.id.cbOptBoot)
        cbOptKeepAlive = findViewById(R.id.cbOptKeepAlive)
        btnOptDoze = findViewById(R.id.btnOptDoze)
        btnOptOverlay = findViewById(R.id.btnOptOverlay)
        cbOptHaptic = findViewById(R.id.cbOptHaptic)
        cbOptSound = findViewById(R.id.cbOptSound)
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)
        btnOptTaskerExport = findViewById(R.id.btnOptTaskerExport)

        cbOptBoot.isChecked = mappingStorage.isAutoStartOnBootEnabled()
        cbOptKeepAlive.isChecked = mappingStorage.isKeepAliveEnabled()
        cbOptHaptic.isChecked = mappingStorage.isHapticFeedbackEnabled()
        cbOptSound.isChecked = mappingStorage.isSoundFeedbackEnabled()

        // Log Tab
        tvLogFull = findViewById(R.id.tvLogFull)
        svLogFull = findViewById(R.id.svLogFull)
        btnLogCopy = findViewById(R.id.btnLogCopy)
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

        // Calibration & Presets
        btnAutoCalibrate.setOnClickListener {
            showAutoCalibrationDialog()
        }

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
            bleService?.gattManager?.startKeepAliveIfEnabled()
            log("Keep-Alive impostato a: " + if (isChecked) "ATTIVO (Ping ogni 35s)" else "DISATTIVATO")
        }

        cbOptHaptic.setOnCheckedChangeListener { _, isChecked ->
            mappingStorage.setHapticFeedbackEnabled(isChecked)
            log("Vibrazione feedback: " + if (isChecked) "Attiva" else "Disattivata")
        }

        cbOptSound.setOnCheckedChangeListener { _, isChecked ->
            mappingStorage.setSoundFeedbackEnabled(isChecked)
            log("Beep audio feedback: " + if (isChecked) "Attivo" else "Disattivato")
        }

        btnOptDoze.setOnClickListener {
            requestIgnoreBatteryOptimization()
        }

        btnOptOverlay.setOnClickListener {
            requestOverlayPermission()
        }

        btnCheckUpdate.setOnClickListener {
            AppUpdateManager.checkForUpdates(this, isManualCheck = true)
        }

        btnOptTaskerExport.setOnClickListener {
            TaskerExporter.exportAndShare(this)
            log("Progetto Tasker XML esportato.")
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

    // Dialog Auto-Apprendimento Ritmo Tap a 3 tentativi
    private fun showAutoCalibrationDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_tap_calibration, null)
        val tvHeader = dialogView.findViewById<TextView>(R.id.tvCalibAttemptHeader)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tvCalibAttemptStatus)
        val tvHistory = dialogView.findViewById<TextView>(R.id.tvCalibHistory)
        val btnTap = dialogView.findViewById<Button>(R.id.btnCalibTapArea)

        var currentAttempt = 1
        val measuredIntervals = mutableListOf<Long>()
        var firstTapTime = 0L

        val dialog = AlertDialog.Builder(this)
            .setTitle("🎯 Calibrazione Ritmo Personale")
            .setView(dialogView)
            .setNegativeButton("Annulla") { _, _ ->
                activeCalibrationCallback = null
            }
            .setCancelable(false)
            .create()

        fun handleTapEvent() {
            val now = SystemClock.uptimeMillis()
            if (firstTapTime == 0L) {
                firstTapTime = now
                tvStatus.text = "1° tocco registrato! Fai subito il 2° tocco..."
                tvStatus.setTextColor(Color.parseColor("#0284C7"))
            } else {
                val interval = now - firstTapTime
                firstTapTime = 0L

                if (interval in 100L..1500L) {
                    measuredIntervals.add(interval)
                    val historyText = measuredIntervals.mapIndexed { idx, ms -> "Tentativo ${idx + 1}: ${ms}ms ✓" }.joinToString("\n")
                    tvHistory.text = historyText

                    if (currentAttempt < 3) {
                        currentAttempt++
                        tvHeader.text = "Tentativo $currentAttempt di 3"
                        tvStatus.text = "Ottimo! Ora fai il Tentativo $currentAttempt..."
                        tvStatus.setTextColor(Color.parseColor("#16A34A"))
                    } else {
                        // Calibrazione completata!
                        activeCalibrationCallback = null
                        val avg = measuredIntervals.average().toLong()
                        val optimal = (avg + 70L).coerceIn(250L, 850L)
                        mappingStorage.setMultiTapWindowMs(optimal)
                        updateTapSpeedText()
                        dialog.dismiss()

                        AlertDialog.Builder(this)
                            .setTitle("✅ Calibrazione Riuscita!")
                            .setMessage("Media misurata: $avg ms\nFinestra ideale impostata: $optimal ms (con margine confortevole).\n\nOra i doppi e tripli tap saranno perfettamente calibrati sulla tua velocità!")
                            .setPositiveButton("Perfetto", null)
                            .show()

                        log("Auto-apprendimento completato: media $avg ms -> impostato $optimal ms")
                    }
                } else {
                    tvStatus.text = "Troppo lento (>1.5s). Riprova il 1° tocco..."
                    tvStatus.setTextColor(Color.parseColor("#DC2626"))
                }
            }
        }

        btnTap.setOnClickListener {
            handleTapEvent()
        }

        activeCalibrationCallback = {
            runOnUiThread {
                handleTapEvent()
            }
        }

        dialog.show()
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

        tvNavTextController.setTextColor(toColorStateList(if (tabIndex == 0) Color.parseColor("#0284C7") else Color.parseColor("#64748B")))
        tvNavTextOptions.setTextColor(toColorStateList(if (tabIndex == 1) Color.parseColor("#0284C7") else Color.parseColor("#64748B")))
        tvNavTextLog.setTextColor(toColorStateList(if (tabIndex == 2) Color.parseColor("#0284C7") else Color.parseColor("#64748B")))
    }

    // Selezione del tasto del D-Pad
    private fun selectButton(button: Br80Button) {
        currentSelectedButton = button

        // Reset colori di sfondo D-Pad
        val defaultPadColor = Color.parseColor("#1E293B")
        val defaultCenterColor = Color.parseColor("#EF4444")
        val defaultSpecialColor = Color.parseColor("#334155")
        val activePadColor = Color.parseColor("#0EA5E9")
        val activeCenterColor = Color.parseColor("#B91C1C")

        btnPadUp.backgroundTintList = toColorStateList(if (button == Br80Button.UP) activePadColor else defaultPadColor)
        btnPadDown.backgroundTintList = toColorStateList(if (button == Br80Button.DOWN) activePadColor else defaultPadColor)
        btnPadLeft.backgroundTintList = toColorStateList(if (button == Br80Button.LEFT) activePadColor else defaultPadColor)
        btnPadRight.backgroundTintList = toColorStateList(if (button == Br80Button.RIGHT) activePadColor else defaultPadColor)
        btnPadHome.backgroundTintList = toColorStateList(if (button == Br80Button.HOME) activeCenterColor else defaultCenterColor)
        btnPadCamera.backgroundTintList = toColorStateList(if (button == Br80Button.CAMERA) activePadColor else defaultSpecialColor)
        btnPadCall.backgroundTintList = toColorStateList(if (button == Br80Button.CALL) activePadColor else defaultSpecialColor)

        // Aggiorna scheda gesti
        tvSelectedButtonTitle.text = "${button.displayName} (${button.name})"

        val actSingle = mappingStorage.getAction(button, GestureType.SINGLE)
        tvActionSingle.text = actSingle.getReadableDescription()
        tvActionSingle.setTextColor(if (actSingle.type == ActionType.NONE) Color.parseColor("#94A3B8") else Color.parseColor("#0284C7"))

        val actDouble = mappingStorage.getAction(button, GestureType.DOUBLE)
        tvActionDouble.text = actDouble.getReadableDescription()
        tvActionDouble.setTextColor(if (actDouble.type == ActionType.NONE) Color.parseColor("#94A3B8") else Color.parseColor("#0284C7"))

        val actTriple = mappingStorage.getAction(button, GestureType.TRIPLE)
        tvActionTriple.text = actTriple.getReadableDescription()
        tvActionTriple.setTextColor(if (actTriple.type == ActionType.NONE) Color.parseColor("#94A3B8") else Color.parseColor("#0284C7"))

        val actLong = mappingStorage.getAction(button, GestureType.LONG)
        tvActionLong.text = actLong.getReadableDescription()
        tvActionLong.setTextColor(if (actLong.type == ActionType.NONE) Color.parseColor("#94A3B8") else Color.parseColor("#0284C7"))
    }

    // Dialog Selezione Azioni con Categorie Collassabili (chiuse all'avvio) e Ricerca Testuale
    private fun showActionPicker(button: Br80Button, gesture: GestureType) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_action_picker, null)
        val etSearch = dialogView.findViewById<EditText>(R.id.etActionSearch)
        val llContainer = dialogView.findViewById<LinearLayout>(R.id.llActionsContainer)

        val dialog = AlertDialog.Builder(this)
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
                    setBackgroundColor(Color.parseColor("#E2E8F0"))
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
                    setTextColor(Color.parseColor("#475569"))
                    setPadding(0, 0, 8, 0)
                }

                val catTitle = TextView(this).apply {
                    text = "${category.icon} ${category.displayName} (${actions.size})"
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#0F172A"))
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
                        setBackgroundColor(Color.parseColor("#FFFFFF"))
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
                        setTextColor(if (action == ActionType.NONE) Color.parseColor("#64748B") else Color.parseColor("#0284C7"))
                    }

                    val tvDesc = TextView(this).apply {
                        text = action.description
                        textSize = 12f
                        setTextColor(Color.parseColor("#64748B"))
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

        AlertDialog.Builder(this)
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

    private fun showDestinationPicker(button: Br80Button, gesture: GestureType) {
        val input = EditText(this).apply {
            hint = "Es. Casa, Lavoro, o coordinate GPS"
            setText(mappingStorage.getAction(button, gesture).parameter)
        }

        AlertDialog.Builder(this)
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

        AlertDialog.Builder(this)
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
            startAndBindBleService()
            bleService?.connectDevice()
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
        updateOverlayButtonState()
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
                        btnQuickConnect.backgroundTintList = toColorStateList(Color.parseColor("#0284C7"))
                    } else {
                        viewStatusDot.backgroundTintList = toColorStateList(Color.parseColor("#DC2626"))
                        tvHeaderStatus.text = "Disconnesso"
                        tvHeaderStatus.setTextColor(Color.parseColor("#64748B"))
                        tvHeaderBattery.text = ""
                        btnQuickConnect.text = "Connetti"
                        btnQuickConnect.backgroundTintList = toColorStateList(Color.parseColor("#0284C7"))
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
                activeCalibrationCallback?.invoke()
                selectButton(button)
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
            tvHeaderBattery.text = "• $level% 🔋"
            tvHeaderBattery.setTextColor(if (level <= 20) Color.parseColor("#DC2626") else Color.parseColor("#0284C7"))
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
