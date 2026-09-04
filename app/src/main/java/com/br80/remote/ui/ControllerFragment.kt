package com.br80.remote.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.br80.remote.ActionType
import com.br80.remote.ArcGaugeView
import com.br80.remote.Br80Button
import com.br80.remote.ButtonAction
import com.br80.remote.GestureType
import com.br80.remote.MainActivity
import com.br80.remote.R

/** Tab "Controller": D-pad grafico, gauge batteria/RSSI, scheda ultima azione,
 * scheda gesti per il tasto selezionato con i relativi dialog di mappatura. */
class ControllerFragment : Fragment(R.layout.fragment_controller) {

    private lateinit var gaugeBattery: ArcGaugeView
    private lateinit var tvGaugeBatteryValue: TextView
    private lateinit var gaugeRssi: ArcGaugeView
    private lateinit var tvGaugeRssiValue: TextView
    private lateinit var tvLastActionTitle: TextView
    private lateinit var tvLastActionSub: TextView

    private lateinit var btnPadUp: android.widget.Button
    private lateinit var btnPadDown: android.widget.Button
    private lateinit var btnPadLeft: android.widget.Button
    private lateinit var btnPadRight: android.widget.Button
    private lateinit var btnPadHome: android.widget.Button
    private lateinit var btnPadCamera: android.widget.Button
    private lateinit var btnPadCall: android.widget.Button

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

    private val host get() = requireActivity() as MainActivity
    private val mappingStorage get() = host.mappingStorage

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        gaugeBattery = view.findViewById(R.id.gaugeBattery)
        tvGaugeBatteryValue = view.findViewById(R.id.tvGaugeBatteryValue)
        gaugeRssi = view.findViewById(R.id.gaugeRssi)
        tvGaugeRssiValue = view.findViewById(R.id.tvGaugeRssiValue)
        tvLastActionTitle = view.findViewById(R.id.tvLastActionTitle)
        tvLastActionSub = view.findViewById(R.id.tvLastActionSub)

        btnPadUp = view.findViewById(R.id.btnPadUp)
        btnPadDown = view.findViewById(R.id.btnPadDown)
        btnPadLeft = view.findViewById(R.id.btnPadLeft)
        btnPadRight = view.findViewById(R.id.btnPadRight)
        btnPadHome = view.findViewById(R.id.btnPadHome)
        btnPadCamera = view.findViewById(R.id.btnPadCamera)
        btnPadCall = view.findViewById(R.id.btnPadCall)

        tvSelectedButtonTitle = view.findViewById(R.id.tvSelectedButtonTitle)
        rowGestureSingle = view.findViewById(R.id.rowGestureSingle)
        tvActionSingle = view.findViewById(R.id.tvActionSingle)
        rowGestureDouble = view.findViewById(R.id.rowGestureDouble)
        tvActionDouble = view.findViewById(R.id.tvActionDouble)
        rowGestureTriple = view.findViewById(R.id.rowGestureTriple)
        tvActionTriple = view.findViewById(R.id.tvActionTriple)
        rowGestureLong = view.findViewById(R.id.rowGestureLong)
        tvActionLong = view.findViewById(R.id.tvActionLong)

        btnPadUp.setOnClickListener { selectButton(Br80Button.UP) }
        btnPadDown.setOnClickListener { selectButton(Br80Button.DOWN) }
        btnPadLeft.setOnClickListener { selectButton(Br80Button.LEFT) }
        btnPadRight.setOnClickListener { selectButton(Br80Button.RIGHT) }
        btnPadHome.setOnClickListener { selectButton(Br80Button.HOME) }
        btnPadCamera.setOnClickListener { selectButton(Br80Button.CAMERA) }
        btnPadCall.setOnClickListener { selectButton(Br80Button.CALL) }

        rowGestureSingle.setOnClickListener { showActionPicker(currentSelectedButton, GestureType.SINGLE) }
        rowGestureDouble.setOnClickListener { showActionPicker(currentSelectedButton, GestureType.DOUBLE) }
        rowGestureTriple.setOnClickListener { showActionPicker(currentSelectedButton, GestureType.TRIPLE) }
        rowGestureLong.setOnClickListener { showActionPicker(currentSelectedButton, GestureType.LONG) }

        rowGestureSingle.setOnLongClickListener { showLabelDialog(GestureType.SINGLE); true }
        rowGestureDouble.setOnLongClickListener { showLabelDialog(GestureType.DOUBLE); true }
        rowGestureTriple.setOnLongClickListener { showLabelDialog(GestureType.TRIPLE); true }
        rowGestureLong.setOnLongClickListener { showLabelDialog(GestureType.LONG); true }

        selectButton(Br80Button.UP)
    }

    private fun showLabelDialog(gesture: GestureType) {
        showCustomTtsLabelDialog(requireContext(), mappingStorage, currentSelectedButton, gesture, host::appendLog)
    }

    fun selectButton(button: Br80Button) {
        currentSelectedButton = button
        val ctx = requireContext()

        val chevronDefault = ContextCompat.getColor(ctx, R.color.cockpit_chevron)
        val chevronActive = ContextCompat.getColor(ctx, R.color.cockpit_ink)

        btnPadUp.setTextColor(if (button == Br80Button.UP) chevronActive else chevronDefault)
        btnPadDown.setTextColor(if (button == Br80Button.DOWN) chevronActive else chevronDefault)
        btnPadLeft.setTextColor(if (button == Br80Button.LEFT) chevronActive else chevronDefault)
        btnPadRight.setTextColor(if (button == Br80Button.RIGHT) chevronActive else chevronDefault)
        btnPadCamera.alpha = if (button == Br80Button.CAMERA) 1f else 0.55f
        btnPadCall.alpha = if (button == Br80Button.CALL) 1f else 0.55f
        btnPadHome.alpha = if (button == Br80Button.HOME) 1f else 0.9f

        tvSelectedButtonTitle.text = "${button.displayName} (${button.name})"

        val actSingle = mappingStorage.getAction(button, GestureType.SINGLE)
        tvActionSingle.text = mappingStorage.describeAction(actSingle)
        tvActionSingle.setTextColor(if (actSingle.type == ActionType.NONE) ContextCompat.getColor(ctx, R.color.cockpit_muted) else ContextCompat.getColor(ctx, R.color.cockpit_accent))

        val actDouble = mappingStorage.getAction(button, GestureType.DOUBLE)
        tvActionDouble.text = mappingStorage.describeAction(actDouble)
        tvActionDouble.setTextColor(if (actDouble.type == ActionType.NONE) ContextCompat.getColor(ctx, R.color.cockpit_muted) else ContextCompat.getColor(ctx, R.color.cockpit_accent))

        val actTriple = mappingStorage.getAction(button, GestureType.TRIPLE)
        tvActionTriple.text = mappingStorage.describeAction(actTriple)
        tvActionTriple.setTextColor(if (actTriple.type == ActionType.NONE) ContextCompat.getColor(ctx, R.color.cockpit_muted) else ContextCompat.getColor(ctx, R.color.cockpit_accent))

        val actLong = mappingStorage.getAction(button, GestureType.LONG)
        tvActionLong.text = mappingStorage.describeAction(actLong)
        tvActionLong.setTextColor(if (actLong.type == ActionType.NONE) ContextCompat.getColor(ctx, R.color.cockpit_muted) else ContextCompat.getColor(ctx, R.color.cockpit_accent))
    }

    /** Riseleziona il tasto corrente (es. dopo un cambio profilo in Opzioni) per aggiornare la scheda gesti. */
    fun refreshCurrentSelection() {
        selectButton(currentSelectedButton)
    }

    fun updateBatteryGauge(level: Int) {
        tvGaugeBatteryValue.text = if (level >= 0) "$level%" else "--"
        gaugeBattery.setValue(if (level >= 0) level / 100f else 0f)
    }

    fun updateRssiGauge(rssi: Int) {
        tvGaugeRssiValue.text = "$rssi"
        val normalized = ((rssi + 100) / 70f).coerceIn(0f, 1f)
        gaugeRssi.setValue(normalized)
    }

    fun showLastAction(button: Br80Button, gesture: GestureType) {
        val action = mappingStorage.getAction(button, gesture)
        tvLastActionTitle.text = "${button.name} — ${gesture.displayName}"
        tvLastActionSub.text = "→ ${mappingStorage.describeAction(action).uppercase(java.util.Locale.getDefault())}"
    }

    // Selezione azione a due passi (categoria → azione), vedi ActionPickerDialog.
    private fun showActionPicker(button: Br80Button, gesture: GestureType) {
        ActionPickerDialog.show(requireContext(), button, gesture) { action ->
            handleActionSelection(button, gesture, action)
        }
    }

    private fun handleActionSelection(button: Br80Button, gesture: GestureType, action: ActionType) {
        when (action) {
            ActionType.OPEN_APP -> showAppPicker(button, gesture)
            ActionType.START_NAVIGATION -> showDestinationPicker(button, gesture)
            ActionType.PHONE_SPEED_DIAL -> showSpeedDialPicker(button, gesture)
            ActionType.TASKER_TRIGGER_EVENT -> showTaskerSlotPicker(button, gesture)
            else -> {
                mappingStorage.setAction(button, gesture, ButtonAction(action))
                selectButton(button)
                host.appendLog("Mappatura: ${button.name}_${gesture.name} -> ${action.displayName}")
            }
        }
    }

    private fun showAppPicker(button: Br80Button, gesture: GestureType) {
        val ctx = requireContext()
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(intent, 0)
            .sortedBy { it.loadLabel(pm).toString() }

        val appLabels = apps.map { it.loadLabel(pm).toString() }.toTypedArray()

        AlertDialog.Builder(ctx, R.style.Theme_Br80_CockpitDialog)
            .setTitle("Seleziona Applicazione da aprire")
            .setItems(appLabels) { _, which ->
                val selectedApp = apps[which]
                val pkgName = selectedApp.activityInfo.packageName
                val appName = selectedApp.loadLabel(pm).toString()

                mappingStorage.setAction(button, gesture, ButtonAction(ActionType.OPEN_APP, pkgName))
                selectButton(button)
                host.appendLog("Mappatura: ${button.name}_${gesture.name} -> Apri $appName ($pkgName)")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showDestinationPicker(button: Br80Button, gesture: GestureType) {
        val ctx = requireContext()
        val input = EditText(ctx).apply {
            hint = "Es. Casa, Lavoro, o coordinate GPS"
            setText(mappingStorage.getAction(button, gesture).parameter)
        }

        AlertDialog.Builder(ctx, R.style.Theme_Br80_CockpitDialog)
            .setTitle("Destinazione Navigazione")
            .setMessage("Inserisci l'indirizzo o punto per Google Maps:")
            .setView(input)
            .setPositiveButton("Salva") { _, _ ->
                val dest = input.text.toString().trim()
                mappingStorage.setAction(button, gesture, ButtonAction(ActionType.START_NAVIGATION, dest))
                selectButton(button)
                host.appendLog("Mappatura: ${button.name}_${gesture.name} -> Naviga verso '$dest'")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showSpeedDialPicker(button: Br80Button, gesture: GestureType) {
        val ctx = requireContext()
        val input = EditText(ctx).apply {
            hint = "Es. +393331234567"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setText(mappingStorage.getAction(button, gesture).parameter)
        }

        AlertDialog.Builder(ctx, R.style.Theme_Br80_CockpitDialog)
            .setTitle("Numero Chiamata Rapida")
            .setMessage("Inserisci il numero telefonico da chiamare direttamente:")
            .setView(input)
            .setPositiveButton("Salva") { _, _ ->
                val num = input.text.toString().trim()
                mappingStorage.setAction(button, gesture, ButtonAction(ActionType.PHONE_SPEED_DIAL, num))
                selectButton(button)
                host.appendLog("Mappatura: ${button.name}_${gesture.name} -> Chiama '$num'")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showTaskerSlotPicker(button: Br80Button, gesture: GestureType) {
        val ctx = requireContext()
        val slots = mappingStorage.getTaskerVirtualSlots()
        val addNewLabel = "+ Nuovo Tasto Virtuale"
        val items = (slots.map { it.name } + addNewLabel).toTypedArray()

        // AlertDialog non supporta setMessage() insieme a setItems(): la lista verrebbe
        // scartata in favore del messaggio. La spiegazione va quindi nel titolo.
        AlertDialog.Builder(ctx, R.style.Theme_Br80_CockpitDialog)
            .setTitle("Scegli Tasto Virtuale Tasker\n(va scelto lo stesso anche in Tasker)")
            .setItems(items) { _, which ->
                if (which == slots.size) {
                    val newSlot = mappingStorage.addTaskerVirtualSlot()
                    mappingStorage.setAction(button, gesture, ButtonAction(ActionType.TASKER_TRIGGER_EVENT, newSlot.id.toString()))
                    selectButton(button)
                    host.appendLog("Mappatura: ${button.name}_${gesture.name} -> ${newSlot.name} (nuovo Tasto Virtuale)")
                } else {
                    val slot = slots[which]
                    mappingStorage.setAction(button, gesture, ButtonAction(ActionType.TASKER_TRIGGER_EVENT, slot.id.toString()))
                    selectButton(button)
                    host.appendLog("Mappatura: ${button.name}_${gesture.name} -> ${slot.name}")
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }
}
