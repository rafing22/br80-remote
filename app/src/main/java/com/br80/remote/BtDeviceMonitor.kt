package com.br80.remote

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.IntentCompat

class BtDeviceMonitor(
    private val context: Context,
    private val mappingStorage: MappingStorage,
    private val listener: BtDeviceMonitorListener
) {

    private val tag = "BtDeviceMonitor"
    private var isReceiverRegistered = false

    // Ultimo stato aggregato già segnalato al listener per ciascun elenco (null = mai calcolato).
    // Un dispositivo che espone sia A2DP sia HFP/Headset (es. auricolari con supporto chiamate)
    // genera PIÙ broadcast di sistema separati per la stessa disconnessione/riconnessione fisica
    // (ACL + A2DP + HEADSET): senza questo controllo, ogni broadcast ridondante ridispacciava da
    // capo entrambi i callback, causando un loop di attivazioni/disattivazioni concorrenti che si
    // accavallavano a vicenda (riprodotto dal vivo con Galaxy Buds Live: 3 cicli identici nello
    // stesso secondo). Dispacciare solo sui cambi di stato REALI elimina il loop alla radice.
    private var lastDispatchedConditionalState: Boolean? = null
    private var lastDispatchedAutoDisableState: Boolean? = null

    // Debounce per gli eventi di DISCONNESSIONE: confermato dal vivo che, appena dopo una
    // riconnessione, il sistema può emettere un BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED
    // con esito "disconnesso" prima che il proxy di profilo (usato da isAnyOfMacsConnected)
    // abbia sincronizzato lo stato vero (~80ms di scarto osservato in log). Un dispatch
    // immediato su quel falso negativo disattivava di nuovo l'app un istante dopo averla
    // appena riattivata. Un evento di CONNESSIONE non ha lo stesso problema (dispatchato
    // subito, vedi sotto) — solo la disconnessione va confermata con un ricontrollo ritardato.
    private val handler = Handler(Looper.getMainLooper())
    private var conditionalDisconnectCheck: Runnable? = null
    private var autoDisableDisconnectCheck: Runnable? = null
    private val disconnectDebounceMs = 1500L

    interface BtDeviceMonitorListener {
        fun onTargetDeviceConnectionChanged(isConnected: Boolean, deviceName: String?)
        fun onAutoDisableTargetConnectionChanged(isConnected: Boolean, deviceName: String?)
        fun onBluetoothStateChanged(isBtOn: Boolean)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return

            when (action) {
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    // ACL_CONNECTED (sotto) resta solo per la disconnessione totale del link:
                    // per il momento in cui il dispositivo è DAVVERO pronto (usato per Keep-Alive
                    // condizionale e per la disattivazione automatica) serve il broadcast di stato
                    // del profilo A2DP/HFP, non l'evento ACL grezzo — quest'ultimo scatta quando il
                    // link radio si forma, PRIMA che la negoziazione del profilo audio sia completa:
                    // un controllo sincrono su ACL trovava sempre "non connesso" anche a cuffie già
                    // accoppiate (visto dal vivo).
                    val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        ?: return

                    val conditionalMacs = mappingStorage.getConditionalBtDevices().map { it.first }
                    if (isTargetDevice(device, conditionalMacs)) {
                        // Ricalcola lo stato aggregato: se hai più dispositivi target configurati,
                        // la disconnessione di UNO solo non deve spegnere il keep-alive se un
                        // altro dispositivo target resta connesso.
                        val stillConnected = isTargetCurrentlyConnected()
                        val name = deviceDisplayName(device, mappingStorage.getConditionalBtDevices())
                        if (stillConnected) {
                            conditionalDisconnectCheck?.let { handler.removeCallbacks(it) }
                            conditionalDisconnectCheck = null
                            if (stillConnected != lastDispatchedConditionalState) {
                                lastDispatchedConditionalState = true
                                Log.d(tag, "Evento BT target [$action] su $name [${device.address}]. Stato aggregato connesso=true")
                                listener.onTargetDeviceConnectionChanged(true, name)
                            }
                        } else {
                            Log.d(tag, "Evento BT target [$action] su $name [${device.address}]. Possibile disconnessione, conferma tra ${disconnectDebounceMs}ms...")
                            conditionalDisconnectCheck?.let { handler.removeCallbacks(it) }
                            val check = Runnable {
                                val confirmedStillConnected = isTargetCurrentlyConnected()
                                if (confirmedStillConnected != lastDispatchedConditionalState) {
                                    lastDispatchedConditionalState = confirmedStillConnected
                                    Log.d(tag, "Disconnessione target confermata dopo debounce: connesso=$confirmedStillConnected")
                                    listener.onTargetDeviceConnectionChanged(confirmedStillConnected, name)
                                }
                            }
                            conditionalDisconnectCheck = check
                            handler.postDelayed(check, disconnectDebounceMs)
                        }
                    }

                    val autoDisableMacs = mappingStorage.getAutoDisableBtDevices().map { it.first }
                    if (isTargetDevice(device, autoDisableMacs)) {
                        val stillConnected = isAutoDisableTargetCurrentlyConnected()
                        val name = deviceDisplayName(device, mappingStorage.getAutoDisableBtDevices())
                        if (stillConnected) {
                            autoDisableDisconnectCheck?.let { handler.removeCallbacks(it) }
                            autoDisableDisconnectCheck = null
                            if (stillConnected != lastDispatchedAutoDisableState) {
                                lastDispatchedAutoDisableState = true
                                Log.d(tag, "Evento BT auto-disattivazione [$action] su $name [${device.address}]. Stato aggregato connesso=true")
                                listener.onAutoDisableTargetConnectionChanged(true, name)
                            }
                        } else {
                            Log.d(tag, "Evento BT auto-disattivazione [$action] su $name [${device.address}]. Possibile disconnessione, conferma tra ${disconnectDebounceMs}ms...")
                            autoDisableDisconnectCheck?.let { handler.removeCallbacks(it) }
                            val check = Runnable {
                                val confirmedStillConnected = isAutoDisableTargetCurrentlyConnected()
                                if (confirmedStillConnected != lastDispatchedAutoDisableState) {
                                    lastDispatchedAutoDisableState = confirmedStillConnected
                                    Log.d(tag, "Disconnessione auto-disattivazione confermata dopo debounce: connesso=$confirmedStillConnected")
                                    listener.onAutoDisableTargetConnectionChanged(confirmedStillConnected, name)
                                }
                            }
                            autoDisableDisconnectCheck = check
                            handler.postDelayed(check, disconnectDebounceMs)
                        }
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    // Solo log/diagnostica: non attendibile per decidere lo stato "connesso"
                    // (vedi sopra). Lo stato aggregato reale arriva dai broadcast di profilo.
                    val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        ?: return
                    val allTracked = mappingStorage.getConditionalBtDevices().map { it.first } +
                        mappingStorage.getAutoDisableBtDevices().map { it.first }
                    if (isTargetDevice(device, allTracked)) {
                        Log.d(tag, "Link ACL formato con ${device.address}, in attesa della negoziazione profilo A2DP/HFP...")
                    }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    val isBtOn = (state == BluetoothAdapter.STATE_ON)
                    Log.d(tag, "Stato Bluetooth di sistema cambiato: ON=$isBtOn")
                    listener.onBluetoothStateChanged(isBtOn)
                }
            }
        }
    }

    fun startMonitoring() {
        if (isReceiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            context.registerReceiver(receiver, filter)
            isReceiverRegistered = true
            Log.d(tag, "BtDeviceMonitor avviato con successo.")

            // Verifica immediata dello stato attuale se il tracciamento condizionale è attivo
            checkCurrentTargetConnectionState()
            checkCurrentAutoDisableConnectionState()
        } catch (e: Exception) {
            Log.e(tag, "Errore registrazione BtDeviceMonitor: ${e.message}")
        }
    }

    fun stopMonitoring() {
        conditionalDisconnectCheck?.let { handler.removeCallbacks(it) }
        autoDisableDisconnectCheck?.let { handler.removeCallbacks(it) }
        conditionalDisconnectCheck = null
        autoDisableDisconnectCheck = null
        if (!isReceiverRegistered) return
        try {
            context.unregisterReceiver(receiver)
            isReceiverRegistered = false
            Log.d(tag, "BtDeviceMonitor fermato.")
        } catch (e: Exception) {
            Log.w(tag, "Errore durante unregister BtDeviceMonitor: ${e.message}")
        }
    }

    fun isTargetCurrentlyConnected(): Boolean {
        return isAnyOfMacsConnected(mappingStorage.getConditionalBtDevices().map { it.first })
    }

    fun isAutoDisableTargetCurrentlyConnected(): Boolean {
        return isAnyOfMacsConnected(mappingStorage.getAutoDisableBtDevices().map { it.first })
    }

    private fun isAnyOfMacsConnected(targetMacs: List<String>): Boolean {
        if (targetMacs.isEmpty()) return false

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: return false
        if (!adapter.isEnabled) return false

        try {
            // GATT (es. telecomando BR80 stesso) è l'unico profilo supportato da
            // BluetoothManager.getConnectedDevices(); A2DP/HEADSET (cuffie/interfoni) passano
            // dal checker condiviso basato sui proxy di profilo.
            val gattConnected = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
            if (gattConnected.any { device -> targetMacs.any { it.equals(device.address, ignoreCase = true) } }) {
                return true
            }
            return BtProfileConnectionChecker.isAnyDeviceConnected(targetMacs)
        } catch (e: Exception) {
            Log.w(tag, "Impossibile verificare dispositivi connessi da BluetoothManager: ${e.message}")
        }
        return false
    }

    // Entrambi i controlli di sincronizzazione sotto girano all'avvio del servizio (startMonitoring,
    // chiamato da BleForegroundService.onCreate()) — incluso quando il servizio viene appena
    // risvegliato da Br80AutoDisableWakeReceiver proprio perché il dispositivo si è riconnesso.
    // In quel caso preciso, il profilo A2DP/HFP potrebbe non essere ancora registrato come
    // connesso nel sistema nell'istante in cui questo controllo sincrono gira (stesso ritardo di
    // negoziazione per cui ACL_CONNECTED non basta da solo, vedi commenti sopra) — un falso
    // "non connesso" qui disattiverebbe di nuovo l'app un istante dopo averla riattivata (bug
    // confermato dal vivo: il servizio si fermava da solo ~26ms dopo essere ripartito). Per
    // questo, un esito "non connesso" in QUESTO controllo iniziale non viene mai dispacciato
    // come disconnessione: solo un vero evento di disconnessione in diretta (broadcast successivo,
    // non questo controllo una tantum) può farlo. Nessun problema simmetrico sull'esito "connesso":
    // dispacciarlo subito è sicuro e anzi desiderabile (riattiva Keep-Alive/servizio prima possibile).
    private fun checkCurrentTargetConnectionState() {
        if (!mappingStorage.isConditionalBtEnabled()) return
        val isConnected = isTargetCurrentlyConnected()
        if (!isConnected) {
            lastDispatchedConditionalState = null // stato ignoto, non "disconnesso": vedi commento sopra
            return
        }
        lastDispatchedConditionalState = true
        val name = mappingStorage.getConditionalBtDevices().firstOrNull()?.second
        listener.onTargetDeviceConnectionChanged(true, name)
    }

    private fun checkCurrentAutoDisableConnectionState() {
        if (!mappingStorage.isAutoDisableBtEnabled()) return
        val isConnected = isAutoDisableTargetCurrentlyConnected()
        if (!isConnected) {
            lastDispatchedAutoDisableState = null // stato ignoto, non "disconnesso": vedi commento sopra
            return
        }
        lastDispatchedAutoDisableState = true
        val name = mappingStorage.getAutoDisableBtDevices().firstOrNull()?.second
        listener.onAutoDisableTargetConnectionChanged(true, name)
    }

    private fun deviceDisplayName(device: BluetoothDevice, knownDevices: Set<Pair<String, String>>): String {
        return device.name
            ?: knownDevices.firstOrNull { it.first.equals(device.address, ignoreCase = true) }?.second
            ?: "Dispositivo BT"
    }

    private fun isTargetDevice(device: BluetoothDevice, targetMacs: List<String>): Boolean {
        if (targetMacs.isEmpty()) return false
        return targetMacs.any { it.equals(device.address, ignoreCase = true) }
    }
}
