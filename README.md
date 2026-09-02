# Livall BR80 Remote — Android BLE Controller & Automation Bridge (v1.2)

Applicazione Android open source per connettere, decodificare e mappare i tasti del telecomando Bluetooth Low Energy **Livall BR80** (noto anche come *BlingRemote*), trasformandolo in un controller versatile per musica, assistente vocale Google Gemini, navigazione, chiamate e automazioni avanzate (**Tasker**, **MacroDroid**, ecc.).

---

## 🎯 Novità Versione 1.2

- **🔄 Aggiornamenti In-App Diretti da GitHub:** L'app controlla automaticamente le nuove versioni pubblicate su GitHub Releases e permette di scaricare e installare l'aggiornamento con un tocco.
- **🎨 Design & Icona Fedele al Controller Reale:**
  - Icona dell'applicazione e layout D-Pad interattivo ridisegnati ispirandosi alla sagoma reale del Livall BR80 (Pulsante **FOTO** in alto, disco centrale con frecce **▲**, **L**, **R**, **▼** e pulsante **STOP ROSSO** centrale, pulsante **🗣️ VOCE** in basso).
  - Toccando il telecomando a schermo o premendo il tasto sul BR80 fisico, il tasto si illumina all'istante aprendo la configurazione.
- **📂 Categorie Azioni Collassabili ad Accordion:**
  - Nel menu di configurazione dei gesti, le categorie appaiono **chiuse all'avvio** per una navigazione ordinata e si aprono al tocco.
  - Se digiti nella barra di ricerca testuale rapida, le categorie con risultati si aprono **automaticamente**.
- **🛡️ Connessione Auto-Healing & Watchdog Timeout (15s):**
  - Se la connessione GATT o la scansione BLE si bloccano a metà (es. errori GATT 133), il watchdog interviene automaticamente entro 15 secondi, resetta la connessione e ritenta senza che l'utente debba mai ripremere "Connetti".

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
| ⬅️ **Sinistra (L)** | `LEFT` | `0x07` | 7 | `0x27` | 39 |
| ➡️ **Destra (R)** | `RIGHT` | `0x08` | 8 | `0x28` | 40 |
| ⏹ **Stop / Conferma (Rosso)** | `HOME` | `0x09` | 9 | `0x29` | 41 |
| 📷 **Foto / Fotocamera** | `CAMERA` | `0x02` | 2 | `0x22` | 34 |
| 🗣️ **Voce / Intercom / Call** | `CALL` | `0x1D` | 29 | `0x2D` | 45 |

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

Dalla scheda **⚙️ Opzioni** dell'app puoi toccare **"Esporta Progetto Tasker (.xml)"** per importare istantaneamente tutti i profili e task d'esempio già configurati.

---

## 📲 Installazione e Aggiornamento

### 1. Download Diretto
Puoi scaricare l'ultima versione compilata dell'APK da:
- **[GitHub Releases](https://github.com/rafing22/br80-remote/releases)**
- **[GitHub Actions](https://github.com/rafing22/br80-remote/actions)** (Artefatto **`Livall-BR80-Remote-v1.2`**)

### 2. Aggiornamenti Futuri
Dalla scheda **⚙️ Opzioni**, tocca semplicemente **"🔄 Verifica Aggiornamenti su GitHub"**: l'app scaricherà e installerà autonomamente ogni nuova versione.

---

## 🛠️ Compilazione da Sorgente

```bash
# Compilazione APK Debug
./gradlew assembleDebug

# L'output sarà generato in:
# release_apk/Livall-BR80-Remote-v1.2.apk
```

---

## 📄 Licenza

Rilasciato sotto licenza MIT.
