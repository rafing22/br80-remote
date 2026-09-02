# Livall BR80 Remote — Android BLE Controller & Automation Bridge (v1.0)

Applicazione Android open source per connettere, decodificare e mappare i tasti del telecomando Bluetooth Low Energy **Livall BR80** (noto anche come *BlingRemote*), trasformandolo in un controller versatile per musica, navigazione, chiamate e automazioni avanzate (**Tasker**, **MacroDroid**, ecc.).

---

## 🎯 Caratteristiche Principali

- **Background & Foreground Service Persistente:** Mantiene la connessione BLE attiva anche a schermo spento con notifica di stato e zero consumo anomalo di batteria.
- **Riconnessione Automatica Intelligente:** Memorizzazione del MAC address e riconnessione diretta con backoff esponenziale quando il telecomando si risveglia dallo standby.
- **Indicatore Batteria in Tempo Reale:** Lettura del livello di carica tramite servizio standard Bluetooth SIG (`0x180F` / `0x2A19`).
- **Macchina a Stati Gesti (Zero-Latency):**
  - Singolo Tap (`SINGLE`)
  - Doppio Tap (`DOUBLE`)
  - Triplo Tap (`TRIPLE`)
  - Pressione Lunga (`LONG`, >500ms)
  - *Zero-Latency Mode:* se non sono configurati doppi o tripli tap su un tasto, il singolo tap scatta **istantaneamente** al rilascio senza attendere la finestra di 350ms.
- **Feedback Aptico & Sonoro:** Vibrazione e/o beep di conferma alla pressione per l'uso con guanti da moto/bici.
- **Catalogo Azioni Native:** Controllo volume, musica (Play/Pausa/Next/Prev), lancio rapido di qualsiasi app installata, navigazione Google Maps verso una meta e risposta/rifiuto chiamate.
- **Integrazione Tasker / Automazioni:** Ogni gesto invia un broadcast Android `com.br80.remote.BUTTON_EVENT` con payload arricchito.

---

## 📡 Protocollo Tecnico Livall BR80 (GATT Reverse Engineering)

### Servizi e Caratteristiche BLE

| Servizio / Caratteristica | UUID Completo | Ruolo / Operazione |
|---|---|---|
| **Servizio Primario Telecomando** | `0000a2a0-0000-1000-8000-00805f9b34fb` | Servizio proprietario Livall |
| **Wake-up Characteristic** | `0000a2a3-0000-1000-8000-00805f9b34fb` | Write `0xFF` per attivare lo streaming dei tasti |
| **Button Notify Characteristic** | `0000a2a4-0000-1000-8000-00805f9b34fb` | Notifiche asincrone con payload a 1 byte (CCCD `0x2902`) |
| **Battery Service (SIG)** | `0000180f-0000-1000-8000-00805f9b34fb` | Servizio standard SIG per la batteria |
| **Battery Level Characteristic** | `00002a19-0000-1000-8000-00805f9b34fb` | Lettura livello percentuale (0–100%) |

---

### Tabella dei Payload Raw dei 7 Tasti Fisici

Ogni pressione e rilascio genera un codice esadecimale a 1 byte sulla caratteristica `a2a4`:

| Tasto Fisico | Etichetta | Codice Press (Hex) | Codice Press (Dec) | Codice Release (Hex) | Codice Release (Dec) |
|---|---|:---:|:---:|:---:|:---:|
| ⬆️ **Freccia Su** | `UP` | `0x06` | 6 | `0x26` | 38 |
| ⬇️ **Freccia Giù** | `DOWN` | `0x05` | 5 | `0x25` | 37 |
| ⬅️ **Freccia Sinistra** | `LEFT` | `0x07` | 7 | `0x27` | 39 |
| ➡️ **Freccia Destra** | `RIGHT` | `0x08` | 8 | `0x28` | 40 |
| 🔘 **Home / Conferma** | `HOME` | `0x09` | 9 | `0x29` | 41 |
| 📷 **Foto / Fotocamera** | `CAMERA` | `0x02` | 2 | `0x22` | 34 |
| 📞 **Chiamata / PTT** | `CALL` | `0x1D` | 29 | `0x2D` | 45 |

---

## ⚡ Integrazione con Tasker / MacroDroid

Ogni volta che viene riconosciuto un gesto, l'app trasmette un `Intent` di broadcast globale:

- **Action:** `com.br80.remote.BUTTON_EVENT`
- **Extras disponibili:**
  - `button` *(String)*: `UP`, `DOWN`, `LEFT`, `RIGHT`, `HOME`, `CAMERA`, `CALL`
  - `gesture` *(String)*: `SINGLE`, `DOUBLE`, `TRIPLE`, `LONG`
  - `event_id` *(String)*: `UP_SINGLE`, `HOME_LONG`, `CAMERA_DOUBLE`, ecc.
  - `battery` *(Int)*: livello percentuale della batteria (0–100)
  - `timestamp` *(Long)*: orario dell'evento in millisecondi

### Configurazione su Tasker:
1. Crea un nuovo **Profilo** $\rightarrow$ **Evento** $\rightarrow$ **Sistema** $\rightarrow$ **Intent Ricevuto** (Intent Received).
2. Nel campo **Azione** inserisci: `com.br80.remote.BUTTON_EVENT`.
3. Nei filtri o nelle variabili del task puoi usare `%event_id` (es. `CAMERA_SINGLE` per scattare una foto, aprire il garage o attivare la torcia).

---

## 📲 Installazione e Uso

### 1. Download dell'APK
Puoi scaricare l'ultima versione compilata dell'APK da **GitHub Actions**:
1. Vai nella scheda **[Actions](https://github.com/rafing22/br80-remote/actions)** del repository.
2. Clicca sull'ultima esecuzione del workflow **Build APK**.
3. Nella sezione **Artifacts** in fondo alla pagina, scarica **`br80-remote-debug-apk`**.

### 2. Primo Avvio
1. Apri l'app e tocca **"Connetti"**.
2. Concedi i permessi Bluetooth, Notifiche e Chiamate.
3. Tocca il pulsante **"Disattiva Doze"** per escludere l'app dalle ottimizzazioni energetiche OEM e garantire la reattività a schermo spento.
4. Accendi il telecomando BR80: l'app si connetterà automaticamente, mostrerà la batteria e sarà pronta.

### 3. Personalizzazione Mappatura
Dall'interfaccia principale puoi toccare ciascuna combinazione di tasto e gesto per assegnare l'azione desiderata o scegliere quale app installata aprire.

---

## 🛠️ Compilazione da Sorgente

Il progetto è sviluppato in **Kotlin** con target **SDK 34 (Android 14)** e supporto retrocompatibile a partire da **Android 8.0 (API 26)**.

```bash
# Compilazione APK Debug
./gradlew assembleDebug

# L'output sarà generato in:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 Licenza

Rilasciato sotto licenza MIT.
