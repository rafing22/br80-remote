package com.br80.remote

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import java.util.UUID
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * [test/coroutine-ble] Riscrittura sperimentale: la sequenza di connessione BLE (scan → connect
 * → discover services → wake → abilita notifiche) è espressa come funzioni `suspend` lineari
 * invece che come catena di callback + Handler.postDelayed. Stesso comportamento/tempi/log del
 * ramo main, meccanismo di concorrenza diverso — vedi CLAUDE.md e il piano di questo branch per
 * il contesto. API pubblica identica a main: nessun altro file dell'app è stato toccato.
 */
class BleGattManager(
    private val context: Context,
    private val mappingStorage: MappingStorage,
    private val listener: BleGattListener
) {

    private val tag = "BleGattManager"

    interface BleGattListener {
        fun onStateChanged(state: ConnectionState)
        fun onButtonRawEvent(button: Br80Button, isPress: Boolean)
        fun onBatteryUpdated(level: Int)
        fun onRssiUpdated(rssi: Int)
        fun onLog(message: String)
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    var currentState: ConnectionState = ConnectionState.DISCONNECTED
        private set

    var batteryLevel: Int = -1
        private set

    // Scope proprio dell'istanza: ogni sequenza (connect, reconnect, keep-alive) vive qui,
    // cancellabile individualmente (Job dedicato) o tutta insieme via shutdown().
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var bluetoothGatt: BluetoothGatt? = null
    private var isConnectingGatt = false
    // Pubblico (non più private) così la UI può leggere l'ultimo valore noto al bind, invece di
    // restare vuota finché non arriva un nuovo aggiornamento "push" da uno scan — con la
    // connessione automatica in background la UI si apre spesso a connessione già avvenuta,
    // quando nessuno scan è più in corso per generarne uno nuovo.
    var lastKnownRssi: Int? = null
        private set
    private val weakSignalRssiThreshold = -75 // Sotto questa soglia il segnale è considerato marginale
    private var userRequestedDisconnect = false
    private var isScanning = false
    private var scanCallback: ScanCallback? = null

    private val maxWakeRetries = 5
    private val wakeRetryDelayMillis = 1000L

    private var reconnectAttempts = 0
    private val reconnectDelays = listOf(1000L, 2500L, 5000L, 10000L)

    // Segnale per chi deve decidere se "aspettare un po' di più" prima di usare il radio
    // Bluetooth per altro (es. apertura canale SCO) subito dopo una riconnessione. Conta
    // solo i VERI errori radio (GATT_ERROR, timeout operazione) verificatisi prima della
    // riconnessione riuscita — NON ogni ciclo di riconnessione (il telecomando va in
    // standby e si riconnette da solo ad ogni pressione: quello è normale, non "rocky",
    // e non deve far scattare il ritardo proattivo).
    private var radioErrorCount = 0
    var lastReconnectErrorCount: Int = 0
        private set
    private var lastSuccessfulConnectAtMs: Long = 0L

    private val connectionWatchdogTimeoutMs = 5000L // 5s timeout rapido per non bloccare lo stack se il device dorme
    private val gattOperationTimeoutMs = 8000L
    private val keepAliveIntervalMs = 35_000L // Ping ogni 35s per prevenire lo standby firmware

    private var connectJob: Job? = null
    private var reconnectJob: Job? = null
    private var keepAliveJob: Job? = null
    private var scanTimeoutJob: Job? = null

    // Serializza le operazioni GATT (una Read/Write/Descriptor alla volta): lo stack Android
    // BLE non gestisce operazioni concorrenti. Sostituisce la coda ArrayDeque + flag booleano
    // di main con un Mutex, che ogni funzione di operazione acquisisce prima di agire.
    private val gattOperationMutex = Mutex()

    // Continuation "in sospeso" per il singolo evento GATT atteso in questo momento (al più una
    // alla volta, garantito dal Mutex sopra per le operazioni in coda, e dal fatto che una sola
    // sequenza di connect() è mai attiva). Il callback GATT le risolve quando arriva l'evento
    // giusto; se non c'è nessuno in attesa (es. disconnessione inattesa durante l'uso normale),
    // il callback gestisce la cosa direttamente invece di risolvere una continuation.
    private var pendingConnectContinuation: CancellableContinuation<ConnectOutcome>? = null
    private var pendingServicesContinuation: CancellableContinuation<Int>? = null
    private var pendingWriteContinuation: CancellableContinuation<Int>? = null
    private var pendingDescriptorContinuation: CancellableContinuation<Int>? = null
    private var pendingReadContinuation: CancellableContinuation<Pair<Int, ByteArray>>? = null

    private sealed class ConnectOutcome {
        data object Connected : ConnectOutcome()
        data class Failed(val status: Int) : ConnectOutcome()
    }

    private val serviceUuid = UUID.fromString("0000a2a0-0000-1000-8000-00805f9b34fb")
    private val wakeUuid = UUID.fromString("0000a2a3-0000-1000-8000-00805f9b34fb")
    private val buttonUuid = UUID.fromString("0000a2a4-0000-1000-8000-00805f9b34fb")
    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val batteryServiceUuid = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    private val batteryLevelUuid = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    private fun log(msg: String) {
        Log.d(tag, msg)
        listener.onLog(msg)
    }

    private fun updateState(newState: ConnectionState) {
        currentState = newState
        listener.onStateChanged(newState)
    }

    private fun getBluetoothAdapter(): BluetoothAdapter? {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bluetoothManager?.adapter
    }

    /** Da chiamare quando il service viene distrutto: ferma tutte le coroutine dell'istanza. */
    fun shutdown() {
        scope.cancel()
    }

    // ---------------------------------------------------------------------------------------
    // Connessione
    // ---------------------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    fun connect() {
        if (currentState == ConnectionState.CONNECTING || currentState == ConnectionState.CONNECTED) {
            log("Connessione già in corso o attiva: richiesta duplicata ignorata.")
            return
        }

        userRequestedDisconnect = false
        reconnectJob?.cancel()
        reconnectJob = null
        connectJob?.cancel()
        connectJob = scope.launch { runConnectSequence() }
    }

    private suspend fun runConnectSequence() {
        updateState(ConnectionState.CONNECTING)

        val adapter = getBluetoothAdapter()
        if (adapter == null || !adapter.isEnabled) {
            log("Bluetooth spento o non disponibile.")
            updateState(ConnectionState.DISCONNECTED)
            return
        }

        closeGattInternal(refresh = false)

        // Il watchdog (5s) copre SOLO "trova e aggancia il telecomando" — come in main, dove
        // era un timer separato dal timeout delle singole operazioni GATT successive (8s
        // ciascuna). Avvolgere anche discoverServices/wake/notifiche nello stesso timeout di 5s
        // era il bug di questa riscrittura: l'intero handshake doveva finire in 5s totali,
        // interrompendo sempre il comando Wake a metà (riprodotto dal vivo).
        val gatt = try {
            withTimeout(connectionWatchdogTimeoutMs) { connectToRemote(adapter) }
        } catch (e: TimeoutCancellationException) {
            log("Watchdog: il telecomando è in Standby. Attivo ascolto automatico a schermo spento...")
            closeGattInternal(refresh = true)
            updateState(ConnectionState.DISCONNECTED)
            stopLeScan()
            val adapter2 = getBluetoothAdapter()
            if (adapter2 != null && adapter2.isEnabled && !userRequestedDisconnect) {
                startLeScanBackground(adapter2)
            }
            return
        } catch (e: ConnectHandshakeException) {
            updateState(ConnectionState.DISCONNECTED)
            if (!userRequestedDisconnect) scheduleAutoReconnect()
            return
        } catch (e: CancellationException) {
            throw e // una nuova connect()/disconnect() ha cancellato questa sequenza: non è un errore
        } catch (e: Exception) {
            log("Errore connessione GATT: ${e.message}")
            closeGattInternal(refresh = true)
            updateState(ConnectionState.DISCONNECTED)
            if (!userRequestedDisconnect) scheduleAutoReconnect()
            return
        }

        // Da qui il link GATT è stabilito: ogni passo (discoverServices, wake, notifiche) ha
        // il proprio timeout indipendente (gattOperationTimeoutMs), non più quello del watchdog.
        try {
            performHandshake(gatt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Errore handshake: ${e.message}")
            closeGattInternal(refresh = true)
            updateState(ConnectionState.DISCONNECTED)
            if (!userRequestedDisconnect) scheduleAutoReconnect(wasConnected = true)
        }
    }

    private class ConnectHandshakeException(val outcome: ConnectOutcome.Failed) : Exception()

    /** Variante di runConnectSequence per quando il dispositivo è già noto (es. lo scan in
     * background per l'auto-reconnect lo trova senza passare da connect()): stessa struttura
     * watchdog+handshake, senza la fase di risoluzione del device. */
    @SuppressLint("MissingPermission")
    private suspend fun runHandshakeFor(device: BluetoothDevice) {
        updateState(ConnectionState.CONNECTING)
        closeGattInternal(refresh = false)

        val gatt = try {
            withTimeout(connectionWatchdogTimeoutMs) { connectAndAwaitGatt(device) }
        } catch (e: TimeoutCancellationException) {
            log("Watchdog: il telecomando è in Standby. Attivo ascolto automatico a schermo spento...")
            closeGattInternal(refresh = true)
            updateState(ConnectionState.DISCONNECTED)
            stopLeScan()
            val adapter2 = getBluetoothAdapter()
            if (adapter2 != null && adapter2.isEnabled && !userRequestedDisconnect) {
                startLeScanBackground(adapter2)
            }
            return
        } catch (e: ConnectHandshakeException) {
            updateState(ConnectionState.DISCONNECTED)
            if (!userRequestedDisconnect) scheduleAutoReconnect()
            return
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Errore connessione GATT: ${e.message}")
            closeGattInternal(refresh = true)
            updateState(ConnectionState.DISCONNECTED)
            if (!userRequestedDisconnect) scheduleAutoReconnect()
            return
        }

        try {
            performHandshake(gatt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Errore handshake: ${e.message}")
            closeGattInternal(refresh = true)
            updateState(ConnectionState.DISCONNECTED)
            if (!userRequestedDisconnect) scheduleAutoReconnect(wasConnected = true)
        }
    }

    /** Sospende finché lo scan (avviato qui) non trova un dispositivo compatibile. */
    @SuppressLint("MissingPermission")
    private suspend fun awaitDeviceFromScan(adapter: BluetoothAdapter): BluetoothDevice {
        return suspendCancellableCoroutine { cont ->
            startLeScanForeground(adapter, onDeviceFound = { device ->
                if (cont.isActive) cont.resume(device)
            })
            cont.invokeOnCancellation { stopLeScan() }
        }
    }

    /** Risolve il dispositivo (MAC noto o scansione) e apre la connessione GATT fino a
     * STATE_CONNECTED incluso — nessuna scoperta servizi/scrittura qui, solo il link radio. */
    @SuppressLint("MissingPermission")
    private suspend fun connectToRemote(adapter: BluetoothAdapter): BluetoothGatt {
        val savedMac = mappingStorage.getLastConnectedMac()
        val knownDevice = if (!savedMac.isNullOrEmpty() && BluetoothAdapter.checkBluetoothAddress(savedMac)) {
            log("In attesa del telecomando $savedMac (premi un tasto sul telecomando)...")
            adapter.getRemoteDevice(savedMac)
        } else {
            null
        }

        // Stessa strategia "doppio binario" di main: tenta la connessione diretta al MAC noto E
        // scansiona in parallelo, solo per aggiornare RSSI/MAC prima possibile — la connessione
        // vera e propria resta sempre quella diretta (`direct`). onDeviceFound qui è
        // deliberatamente no-op: senza, il ramo di default dello scan (pensato per l'ascolto
        // passivo standalone in disconnect()/scheduleAutoReconnect) cancella connectJob e ne
        // lancia uno nuovo se lo scan trova il device prima che connectAndAwaitGatt() faccia il
        // proprio stopLeScan() interno — una race reale (più probabile dopo un lungo standby,
        // quando connectGatt() è più lento a rispondere) che interrompe a metà un connectGatt()
        // già in volo verso lo stesso MAC: due connectGatt() sovrapposti sullo stesso device
        // possono bloccarsi silenziosamente nello stack Bluetooth di sistema, senza più
        // richiamare alcun callback — nemmeno il watchdog riesce più a intervenire perché il
        // job che lo conteneva è già stato cancellato. Riprodotto dal vivo: connessione riuscita
        // silenziosamente bloccata dopo un risveglio da standby prolungato.
        return if (knownDevice != null) {
            coroutineScope {
                val direct = async { connectAndAwaitGatt(knownDevice) }
                startLeScanForeground(adapter, onDeviceFound = { /* no-op: vedi commento sopra */ })
                direct.await()
            }
        } else {
            val found = awaitDeviceFromScan(adapter)
            connectAndAwaitGatt(found)
        }
    }

    /** Guardia + apertura vera e propria del link GATT, sospesa fino a STATE_CONNECTED. */
    @SuppressLint("MissingPermission")
    private suspend fun connectAndAwaitGatt(device: BluetoothDevice): BluetoothGatt {
        if (isConnectingGatt || bluetoothGatt != null) {
            log("Connessione GATT già in corso: richiesta duplicata verso ${device.address} ignorata.")
            throw ConnectHandshakeException(ConnectOutcome.Failed(-1))
        }
        isConnectingGatt = true
        stopLeScan()

        delay(150L) // stesso margine di main prima di aprire connectGatt

        log("Connessione GATT ad alta velocità a ${device.address}...")
        val connectOutcome = try {
            suspendCancellableCoroutine<ConnectOutcome> { cont ->
                pendingConnectContinuation = cont
                cont.invokeOnCancellation { pendingConnectContinuation = null }
                try {
                    bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                } catch (e: Exception) {
                    log("Eccezione connectGatt: ${e.message}")
                    pendingConnectContinuation = null
                    isConnectingGatt = false
                    cont.resume(ConnectOutcome.Failed(-1))
                }
            }
        } finally {
            isConnectingGatt = false
        }

        if (connectOutcome is ConnectOutcome.Failed) {
            throw ConnectHandshakeException(connectOutcome)
        }
        mappingStorage.setLastConnectedMac(device.address)
        return bluetoothGatt ?: throw ConnectHandshakeException(ConnectOutcome.Failed(-1))
    }

    /** Scoperta servizi → wake → abilitazione notifiche, ciascuno con il proprio timeout. */
    @SuppressLint("MissingPermission")
    /** Fase interna dell'handshake per la sola diagnostica (Log/screen di debug): NON
     * espone un quarto valore nel `ConnectionState` pubblico (che resterebbe DISCONNECTED/
     * CONNECTING/CONNECTED com'è oggi, invariato per tutti i chiamanti esistenti — UI,
     * widget, notifica), così nessun `when` esaustivo altrove nell'app va toccato. */
    enum class HandshakePhase {
        DISCOVERING_SERVICES,
        WAKING,
        ENABLING_NOTIFICATIONS
    }

    var handshakePhase: HandshakePhase? = null
        private set

    /** Un solo punto per "fallimento durante l'handshake": pulisce, aggiorna lo stato e fa
     * scattare davvero l'auto-healing — a differenza di prima, dove la maggior parte dei rami
     * di fallimento qui sotto si limitava a loggare "Ripristino auto-healing..." senza in
     * realtà richiamare scheduleAutoReconnect() (bug trovato in revisione: l'app restava
     * silenziosamente disconnessa, senza scansione né riconnessione programmata). */
    private fun failHandshake(message: String) {
        log(message)
        handshakePhase = null
        closeGattInternal(refresh = true)
        updateState(ConnectionState.DISCONNECTED)
        // wasConnected=true: qui il GATT era già stabilito (siamo dentro l'handshake), il
        // telecomando era raggiungibile — non far salire il gradino del backoff, era solo
        // lento a rispondere allo step in corso (scoperta servizi/wake/notifiche).
        if (!userRequestedDisconnect) scheduleAutoReconnect(wasConnected = true)
    }

    private suspend fun performHandshake(gatt: BluetoothGatt) {
        handshakePhase = HandshakePhase.DISCOVERING_SERVICES
        log("Connesso al BR80. Scoperta servizi GATT in corso...")
        delay(300L)
        val discoverStatus = try {
            withTimeout(gattOperationTimeoutMs) { awaitServicesDiscovered(gatt) }
        } catch (e: TimeoutCancellationException) {
            radioErrorCount++
            failHandshake("Timeout scoperta servizi. Ripristino auto-healing...")
            return
        }
        if (discoverStatus != BluetoothGatt.GATT_SUCCESS) {
            failHandshake("Scoperta servizi fallita: status $discoverStatus. Riavvio auto-healing...")
            return
        }

        val service = gatt.getService(serviceUuid)
        if (service == null) {
            failHandshake("Servizio a2a0 non trovato sul device. Ripristino auto-healing...")
            return
        }

        handshakePhase = HandshakePhase.WAKING
        log("Servizio a2a0 trovato. Invio comando Wake (0xFF su a2a3)...")
        val wakeChar = service.getCharacteristic(wakeUuid)
        if (wakeChar == null) {
            failHandshake("Caratteristica a2a3 (Wake) non trovata. Ripristino auto-healing...")
            return
        }

        val wakeOk = writeCharacteristicWithRetry(gatt, wakeChar, byteArrayOf(0xFF.toByte()), maxWakeRetries, wakeRetryDelayMillis)
        if (!wakeOk) {
            failHandshake("Wake fallito dopo $maxWakeRetries tentativi. Reset auto-healing...")
            return
        }
        log("Wake inviato con successo. Abilito notifiche su a2a4...")

        handshakePhase = HandshakePhase.ENABLING_NOTIFICATIONS
        val buttonChar = service.getCharacteristic(buttonUuid)
        if (buttonChar == null) {
            failHandshake("Caratteristica a2a4 (Notifiche) non trovata. Ripristino auto-healing...")
            return
        }
        val notifyStatus = try {
            withTimeout(gattOperationTimeoutMs) { enableCharacteristicNotifications(gatt, buttonChar) }
        } catch (e: TimeoutCancellationException) {
            radioErrorCount++
            failHandshake("Timeout abilitazione notifiche. Ripristino auto-healing...")
            return
        }
        if (notifyStatus == null) {
            failHandshake("Descrittore CCCD (0x2902) non trovato su characteristic. Ripristino auto-healing...")
            return
        }
        if (notifyStatus != BluetoothGatt.GATT_SUCCESS) {
            failHandshake("Abilitazione descrittore notifiche fallita: status $notifyStatus. Ripristino auto-healing...")
            return
        }
        log("Notifiche abilitate su a2a4! Telecomando pronto.")
        handshakePhase = null

        // Connessione riuscita: azzera i contatori di errore, marca lo stato, avvia il keep-alive.
        lastReconnectErrorCount = radioErrorCount
        lastSuccessfulConnectAtMs = System.currentTimeMillis()
        radioErrorCount = 0
        reconnectAttempts = 0
        updateState(ConnectionState.CONNECTED)
        startKeepAliveIfEnabled()

        // Letto attivamente qui perché lastKnownRssi viene aggiornato solo dal ramo scan della
        // strategia "doppio binario" di connectToRemote() — se vince il tentativo diretto al MAC
        // noto (il caso più comune per una riconnessione), quello scan può non trovare mai nulla,
        // lasciando l'indicatore RSSI della UI vuoto per l'intera sessione anche a connessione
        // riuscita.
        try {
            gatt.readRemoteRssi()
        } catch (e: Exception) {
            Log.w(tag, "readRemoteRssi non disponibile: ${e.message}")
        }

        delay(400L)
        readBatteryLevel(gatt)
    }

    private suspend fun awaitServicesDiscovered(gatt: BluetoothGatt): Int {
        return suspendCancellableCoroutine { cont ->
            pendingServicesContinuation = cont
            cont.invokeOnCancellation { pendingServicesContinuation = null }
            try {
                gatt.discoverServices()
            } catch (e: Exception) {
                log("Errore discoverServices: ${e.message}")
                pendingServicesContinuation = null
                cont.resume(-1)
            }
        }
    }

    /** Scrive una characteristic e ritenta fino a [maxAttempts] volte con [delayMs] tra un
     * tentativo e l'altro se lo stack Bluetooth rifiuta la scrittura — generico, riusabile per
     * qualunque comando futuro oltre al wake (non più cablato su una sola characteristic come
     * in main). */
    @SuppressLint("MissingPermission")
    private suspend fun writeCharacteristicWithRetry(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        maxAttempts: Int,
        delayMs: Long
    ): Boolean {
        // Due filosofie DIVERSE, come in main, non un unico meccanismo: uno status di
        // fallimento ESPLICITO e pronto (il callback ha risposto, dicendo "no") è ritentato
        // sulla stessa connessione. Un TIMEOUT (nessuna risposta entro gattOperationTimeoutMs)
        // NON viene ritentato con una nuova scrittura: significa che l'operazione precedente
        // potrebbe essere ancora pendente lato stack Android, e riemettere subito un'altra
        // scrittura sopra rischia di confondere lo stack (riprodotto dal vivo: retry-su-timeout
        // ripetuti hanno preceduto un GATT_ERROR/disconnessione vera). Un timeout esce subito
        // e lascia che il chiamante faccia un reset pieno (chiudi+riconnetti), esattamente come
        // il timeout di coda in main.
        var attempt = 0
        while (attempt < maxAttempts) {
            val status = try {
                withTimeout(gattOperationTimeoutMs) {
                    gattOperationMutex.withLock {
                        suspendCancellableCoroutine<Int> { cont ->
                            pendingWriteContinuation = cont
                            cont.invokeOnCancellation { pendingWriteContinuation = null }
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    gatt.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                                } else {
                                    @Suppress("DEPRECATION")
                                    characteristic.value = value
                                    @Suppress("DEPRECATION")
                                    gatt.writeCharacteristic(characteristic)
                                }
                            } catch (e: Exception) {
                                pendingWriteContinuation = null
                                cont.resume(-1)
                            }
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                log("Timeout scrittura (nessuna risposta dopo ${gattOperationTimeoutMs / 1000}s). Ripristino auto-healing...")
                radioErrorCount++
                return false
            }
            if (status == BluetoothGatt.GATT_SUCCESS) return true
            attempt++
            if (attempt < maxAttempts) {
                log("Wake non riuscito (status $status). Riprovo ($attempt/$maxAttempts)...")
                delay(delayMs)
            }
        }
        return false
    }

    @SuppressLint("MissingPermission")
    private suspend fun enableCharacteristicNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic): Int? {
        gatt.setCharacteristicNotification(characteristic, true)
        val cccd = characteristic.getDescriptor(cccdUuid) ?: return null
        return gattOperationMutex.withLock {
            suspendCancellableCoroutine { cont ->
                pendingDescriptorContinuation = cont
                cont.invokeOnCancellation { pendingDescriptorContinuation = null }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(cccd)
                    }
                } catch (e: Exception) {
                    pendingDescriptorContinuation = null
                    cont.resume(-1)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun readBatteryLevel(gatt: BluetoothGatt? = bluetoothGatt) {
        val g = gatt ?: return
        val batteryService = g.getService(batteryServiceUuid)
        val batteryChar = batteryService?.getCharacteristic(batteryLevelUuid) ?: return
        scope.launch {
            // Come per le scritture: senza un timeout qui, una risposta che non arriva mai
            // (scenario BLE normale, non esotico) sospenderebbe questa coroutine per sempre
            // TENENDO BLOCCATO il Mutex condiviso — bloccando di conseguenza ogni futura
            // operazione GATT in coda, incluso il ping periodico del Keep-Alive che chiama
            // proprio questa funzione ogni 35s (bug reale trovato in revisione, non ipotetico).
            val (status, value) = try {
                withTimeout(gattOperationTimeoutMs) {
                    gattOperationMutex.withLock {
                        suspendCancellableCoroutine<Pair<Int, ByteArray>> { cont ->
                            pendingReadContinuation = cont
                            cont.invokeOnCancellation { pendingReadContinuation = null }
                            try {
                                g.readCharacteristic(batteryChar)
                            } catch (e: Exception) {
                                pendingReadContinuation = null
                                cont.resume(-1 to ByteArray(0))
                            }
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                log("Timeout lettura batteria (nessuna risposta dopo ${gattOperationTimeoutMs / 1000}s).")
                radioErrorCount++
                -1 to ByteArray(0)
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val level = value.getOrNull(0)?.toInt()?.and(0xFF) ?: -1
                if (level in 0..100) {
                    batteryLevel = level
                    log("Livello batteria letto: $level%")
                    listener.onBatteryUpdated(level)
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Scansione
    // ---------------------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun startLeScanForeground(adapter: BluetoothAdapter, onDeviceFound: ((BluetoothDevice) -> Unit)? = null) {
        startLeScanInternal(adapter, isBackgroundStandby = false, onDeviceFound = onDeviceFound)
    }

    @SuppressLint("MissingPermission")
    private fun startLeScanBackground(adapter: BluetoothAdapter, onDeviceFound: ((BluetoothDevice) -> Unit)? = null) {
        startLeScanInternal(adapter, isBackgroundStandby = true, onDeviceFound = onDeviceFound)
    }

    @SuppressLint("MissingPermission")
    private fun startLeScanInternal(adapter: BluetoothAdapter, isBackgroundStandby: Boolean, onDeviceFound: ((BluetoothDevice) -> Unit)?) {
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            log("BLE Scanner non disponibile.")
            updateState(ConnectionState.DISCONNECTED)
            scheduleAutoReconnect()
            return
        }

        stopLeScan()

        log(if (isBackgroundStandby) "Ascolto Standby attivo (premi un tasto sul telecomando)..." else "Scansione rapida attiva per Livall BR80...")
        isScanning = true

        // SCAN_MODE_LOW_POWER (duty cycle ~512ms ogni 4.9s) per l'ascolto standby di lunga
        // durata: qui il telecomando resta per la maggior parte del tempo tra una pressione e
        // l'altra, e qualche secondo di latenza in più nel rilevarlo è accettabile a fronte di
        // un consumo radio 3-5 volte inferiore rispetto a BALANCED/LOW_LATENCY.
        val scanMode = if (isBackgroundStandby) {
            ScanSettings.SCAN_MODE_LOW_POWER
        } else {
            ScanSettings.SCAN_MODE_LOW_LATENCY
        }

        val settings = ScanSettings.Builder()
            .setScanMode(scanMode)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .build()

        val filters = mutableListOf<ScanFilter>()
        val savedMac = mappingStorage.getLastConnectedMac()
        if (!savedMac.isNullOrEmpty() && BluetoothAdapter.checkBluetoothAddress(savedMac)) {
            // Il telecomando è già stato associato: filtra SOLO sul suo MAC, così l'hardware
            // Bluetooth può scartare in autonomia (offload) ogni altro dispositivo nei paraggi
            // senza svegliare la CPU. Un filtro generico aggiuntivo qui annullerebbe il filtro
            // MAC (i filtri sono in OR), facendo risalire all'app ogni dispositivo BLE intorno.
            filters.add(ScanFilter.Builder().setDeviceAddress(savedMac).build())
        } else {
            // Nessun MAC salvato (primo abbinamento): serve un filtro generico per riconoscere
            // il telecomando dal nome/servizio nel callback, non avendo ancora nulla di più specifico.
            filters.add(ScanFilter.Builder().build())
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (!isScanning) return

                val device = result.device
                val devName = device.name
                val recordName = result.scanRecord?.deviceName
                val serviceUuids = result.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()
                val savedMacCurrent = mappingStorage.getLastConnectedMac()

                val matchesName = isMatchingName(devName) || isMatchingName(recordName)
                val matchesService = serviceUuids.contains(serviceUuid)
                val matchesMac = (savedMacCurrent != null && device.address.equals(savedMacCurrent, ignoreCase = true))

                if (matchesName || matchesService || matchesMac) {
                    val displayName = recordName ?: devName ?: "BR80"
                    lastKnownRssi = result.rssi
                    listener.onRssiUpdated(result.rssi)
                    log("Telecomando rilevato: $displayName [${device.address}], RSSI: ${result.rssi}")
                    stopLeScan()
                    mappingStorage.setLastConnectedMac(device.address)
                    if (onDeviceFound != null) {
                        onDeviceFound(device)
                    } else {
                        connectJob?.cancel()
                        connectJob = scope.launch { runHandshakeFor(device) }
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                log("Scansione BLE fallita con codice: $errorCode")
                isScanning = false
                updateState(ConnectionState.DISCONNECTED)
                scheduleAutoReconnect()
            }
        }

        scanCallback = callback
        try {
            scanner.startScan(filters, settings, callback)
        } catch (e: Exception) {
            log("Errore avvio scansione: ${e.message}")
            isScanning = false
            scheduleAutoReconnect()
            return
        }

        if (!isBackgroundStandby) {
            // Dopo 8 secondi di scansione attiva passa all'ascolto continuo BALANCED
            scanTimeoutJob?.cancel()
            scanTimeoutJob = scope.launch {
                delay(8000L)
                if (isScanning) {
                    stopLeScan()
                    startLeScanInternal(adapter, isBackgroundStandby = true, onDeviceFound = onDeviceFound)
                }
            }
        }
    }

    private fun isMatchingName(name: String?): Boolean {
        if (name == null) return false
        val lower = name.lowercase()
        return lower.contains("blingremote") || lower.contains("br80") || lower.contains("livall") || lower.contains("remote") || lower.contains("bling")
    }

    @SuppressLint("MissingPermission")
    private fun stopLeScan() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null

        if (isScanning) {
            val scanner = getBluetoothAdapter()?.bluetoothLeScanner
            scanCallback?.let {
                try {
                    scanner?.stopScan(it)
                } catch (e: Exception) {
                    Log.w(tag, "Stop scan error: ${e.message}")
                }
            }
            scanCallback = null
            isScanning = false
        }
    }

    // ---------------------------------------------------------------------------------------
    // Disconnessione / chiusura
    // ---------------------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    fun disconnect(enterPassiveListening: Boolean = true) {
        connectJob?.cancel()
        stopKeepAlive()
        reconnectJob?.cancel()
        reconnectJob = null
        stopLeScan()
        closeGattInternal(refresh = true)
        updateState(ConnectionState.DISCONNECTED)

        if (enterPassiveListening) {
            userRequestedDisconnect = false
            reconnectAttempts = 0
            log("Disconnesso. Ascolto passivo attivo: premi un tasto sul telecomando per riconnetterti.")
            val adapter = getBluetoothAdapter()
            if (adapter != null && adapter.isEnabled) {
                startLeScanBackground(adapter)
            }
        } else {
            userRequestedDisconnect = true
            log("Disconnessione completa richiesta dall'utente.")
        }
    }

    private fun refreshGatt(gatt: BluetoothGatt): Boolean {
        return try {
            val refreshMethod = gatt.javaClass.getMethod("refresh")
            (refreshMethod.invoke(gatt) as? Boolean) ?: false
        } catch (e: Exception) {
            Log.w(tag, "Gatt refresh exception: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeGattInternal(refresh: Boolean = true) {
        stopKeepAlive()
        // Eventuali continuation pendenti restano sospese finché il loro withTimeout/cancel non
        // le raccoglie: non serve risolverle qui a mano, la cancellazione del Job che le contiene
        // (connectJob.cancel() nei chiamanti) se ne occupa via invokeOnCancellation.
        isConnectingGatt = false
        val gatt = bluetoothGatt
        bluetoothGatt = null
        if (gatt != null) {
            try {
                if (refresh) {
                    refreshGatt(gatt)
                }
                gatt.disconnect()
            } catch (e: Exception) {
                Log.w(tag, "Error disconnecting gatt: ${e.message}")
            }
            scope.launch {
                delay(100L)
                try {
                    gatt.close()
                } catch (e: Exception) {
                    Log.w(tag, "Error closing gatt: ${e.message}")
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Auto-reconnect / Keep-Alive
    // ---------------------------------------------------------------------------------------

    /** @param wasConnected true se il link GATT era già stabilito quando è scattato questo
     * fallimento (handshake fallito, o disconnessione durante l'uso normale) — in quel caso il
     * telecomando era comunque raggiungibile, quindi non fa salire il gradino del backoff
     * (restiamo al primo, più breve): il gradino crescente serve a non tempestare di scan un
     * telecomando davvero irraggiungibile, non un caso in cui si connette ma è solo lento a
     * rispondere. Di default false (mai connesso in questo tentativo: scan fallito, CONN_TIMEOUT
     * prima di STATE_CONNECTED), che fa salire il gradino come prima. */
    private fun scheduleAutoReconnect(wasConnected: Boolean = false) {
        if (userRequestedDisconnect) return

        stopKeepAlive()
        reconnectJob?.cancel()
        val delayMs = if (wasConnected) {
            reconnectDelays[0]
        } else {
            val d = reconnectDelays[minOf(reconnectAttempts, reconnectDelays.size - 1)]
            reconnectAttempts++
            d
        }
        log("Auto-Healing: ascolto o riconnessione programmata tra ${delayMs / 1000}s...")

        reconnectJob = scope.launch {
            delay(delayMs)
            if (!userRequestedDisconnect && currentState == ConnectionState.DISCONNECTED) {
                val adapter = getBluetoothAdapter()
                if (adapter != null && adapter.isEnabled) {
                    startLeScanBackground(adapter)
                }
            }
        }
    }

    fun startKeepAliveIfEnabled() {
        stopKeepAlive()
        if (mappingStorage.isKeepAliveEnabled() && currentState == ConnectionState.CONNECTED) {
            log("Keep-Alive attivo: ping periodico impostato ogni 35s per prevenire lo standby.")
            keepAliveJob = scope.launch {
                while (isActive) {
                    delay(keepAliveIntervalMs)
                    if (currentState == ConnectionState.CONNECTED && bluetoothGatt != null) {
                        log("Keep-Alive: invio ping per mantenere il canale attivo...")
                        readBatteryLevel()
                    } else {
                        break
                    }
                }
            }
        }
    }

    fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }

    /** Quanti errori radio (GATT_ERROR, timeout operazione) hanno preceduto l'ultima
     * riconnessione riuscita, se avvenuta negli ultimi [withinMs] ms — 0 se la riconnessione
     * è stata pulita o troppo lontana nel tempo. Segnale per chi deve decidere se dare al
     * radio Bluetooth un momento in più prima di un'altra operazione (es. apertura SCO). */
    fun recentReconnectErrorCount(withinMs: Long = 5000L): Int {
        if (System.currentTimeMillis() - lastSuccessfulConnectAtMs >= withinMs) return 0
        return lastReconnectErrorCount
    }

    private fun gattStatusString(status: Int): String {
        return when (status) {
            BluetoothGatt.GATT_SUCCESS -> "SUCCESS (0)"
            0x85 -> "GATT_ERROR (133)"
            0x13 -> "CONN_TERMINATE_PEER_USER (19)"
            0x08 -> "CONN_TIMEOUT (8)"
            0x16 -> "CONN_TERMINATE_LOCAL_HOST (22)"
            0x3e -> "CONN_FAIL_ESTABLISH (62)"
            else -> "CODE_$status"
        }
    }

    // ---------------------------------------------------------------------------------------
    // Callback GATT: il suo unico compito è aggiornare lo stato/log e risolvere la
    // continuation in sospeso, se ce n'è una. Nessuna logica di business qui dentro.
    // ---------------------------------------------------------------------------------------

    // Le callback di BluetoothGattCallback arrivano su un thread di sistema (binder) e possono
    // già essere in coda/in esecuzione quando una nuova connectGatt() sostituisce bluetoothGatt
    // (es. dopo una riconnessione rapida): la cancellazione della coroutine che le racchiude
    // non basta a fermarle in tempo, perché quella cancellazione è cooperativa e non interrompe
    // una callback binder già partita. Un controllo per riferimento su gatt stesso, prima di
    // toccare qualunque stato condiviso (continuation, currentState, batteryLevel...), è
    // l'unica garanzia reale contro una callback "vecchia" che corrompe lo stato di una
    // connessione più recente. Pattern preso in prestito (senza copiare codice) dall'app
    // ufficiale LIVALL, che fa lo stesso confronto per riferimento nella sua BleManager.java.
    private fun isStaleGatt(gatt: BluetoothGatt): Boolean = gatt !== bluetoothGatt

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (isStaleGatt(gatt)) return
            scope.launch {
                log("Stato connessione BLE: status=$status (${gattStatusString(status)}), newState=$newState")

                if (status != BluetoothGatt.GATT_SUCCESS) {
                    log("Errore GATT rilevato ($status). Ripristino automatico stack...")
                    radioErrorCount++
                    val cont = pendingConnectContinuation
                    pendingConnectContinuation = null
                    if (cont != null && cont.isActive) {
                        closeGattInternal(refresh = true)
                        cont.resume(ConnectOutcome.Failed(status))
                    } else {
                        closeGattInternal(refresh = true)
                        updateState(ConnectionState.DISCONNECTED)
                        // cont null: non era una connessione iniziale in corso, il link era già
                        // stabilito prima di questo errore — telecomando raggiungibile.
                        if (!userRequestedDisconnect) scheduleAutoReconnect(wasConnected = true)
                    }
                    return@launch
                }

                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // Priorità di connessione adattiva impostata una sola volta, per l'intera
                    // sessione: HIGH su segnale buono (7.5-15ms di latenza), BALANCED su segnale
                    // debole per non stressare un link marginale. Non viene più riportata a
                    // BALANCED dopo l'handshake (era causa confermata di CONN_TIMEOUT/GATT_ERROR
                    // a pochi secondi dal downgrade, osservato 2 volte dal vivo).
                    try {
                        val rssi = lastKnownRssi
                        if (rssi != null && rssi < weakSignalRssiThreshold) {
                            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED)
                            log("Segnale debole (RSSI $rssi): priorità connessione BALANCED per maggiore stabilità.")
                        } else {
                            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                            log("Impostata priorità connessione BLE ad Alta Velocità (CONNECTION_PRIORITY_HIGH).")
                        }
                    } catch (e: Exception) {
                        Log.w(tag, "Impossibile impostare priorità elevata: ${e.message}")
                    }

                    val cont = pendingConnectContinuation
                    pendingConnectContinuation = null
                    cont?.takeIf { it.isActive }?.resume(ConnectOutcome.Connected)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    log("Telecomando disconnesso (standby o fuori portata).")
                    val cont = pendingConnectContinuation
                    pendingConnectContinuation = null
                    if (cont != null && cont.isActive) {
                        closeGattInternal(refresh = false)
                        cont.resume(ConnectOutcome.Failed(status))
                    } else {
                        // Disconnessione durante l'uso normale (non parte di un handshake in
                        // corso): qui main faceva scattare l'auto-healing direttamente. Link
                        // già stabilito prima di questa disconnessione: telecomando raggiungibile.
                        closeGattInternal(refresh = false)
                        updateState(ConnectionState.DISCONNECTED)
                        if (!userRequestedDisconnect) scheduleAutoReconnect(wasConnected = true)
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (isStaleGatt(gatt)) return
            scope.launch {
                val cont = pendingServicesContinuation
                pendingServicesContinuation = null
                cont?.takeIf { it.isActive }?.resume(status)
            }
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (isStaleGatt(gatt)) return
            if (status != BluetoothGatt.GATT_SUCCESS) return
            scope.launch {
                lastKnownRssi = rssi
                listener.onRssiUpdated(rssi)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (isStaleGatt(gatt)) return
            scope.launch {
                val cont = pendingWriteContinuation
                pendingWriteContinuation = null
                cont?.takeIf { it.isActive }?.resume(status)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (isStaleGatt(gatt)) return
            scope.launch {
                val cont = pendingDescriptorContinuation
                pendingDescriptorContinuation = null
                cont?.takeIf { it.isActive }?.resume(status)
            }
        }

        // Stessa doppia-chiamata di sistema su Android 13+ già osservata per
        // onCharacteristicChanged: solo un overload deve risolvere la continuation.
        @SuppressLint("MissingPermission")
        @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            if (isStaleGatt(gatt)) return
            @Suppress("DEPRECATION")
            val bytes = characteristic.value ?: ByteArray(0)
            scope.launch {
                val cont = pendingReadContinuation
                pendingReadContinuation = null
                cont?.takeIf { it.isActive }?.resume(status to bytes)
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (isStaleGatt(gatt)) return
            scope.launch {
                val cont = pendingReadContinuation
                pendingReadContinuation = null
                cont?.takeIf { it.isActive }?.resume(status to value)
            }
        }

        // Su Android 13+ (API 33+) il sistema chiama ENTRAMBI gli overload per lo stesso
        // evento, raddoppiando ogni pressione fisica: solo l'overload byte[] elabora il
        // payload da API 33 in su, il legacy solo sotto (dove il nuovo non viene mai chiamato).
        @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            if (isStaleGatt(gatt)) return
            if (characteristic.uuid == buttonUuid) {
                @Suppress("DEPRECATION")
                handleButtonPayload(characteristic.value)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (isStaleGatt(gatt)) return
            if (characteristic.uuid == buttonUuid) {
                handleButtonPayload(value)
            }
        }
    }

    private fun handleButtonPayload(value: ByteArray?) {
        if (value == null || value.isEmpty()) return
        val code = value[0].toInt() and 0xFF
        val parsed = Br80Button.fromCode(code)

        if (parsed != null) {
            val (button, isPress) = parsed
            val stateStr = if (isPress) "PRESS" else "RELEASE"
            log("Tasto [0x${code.toString(16)}]: ${button.name} $stateStr")
            listener.onButtonRawEvent(button, isPress)
        } else {
            log("Payload sconosciuto su a2a4: 0x${code.toString(16)} ($code)")
        }
    }
}
