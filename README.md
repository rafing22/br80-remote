# Livall BR80 Remote — Android BLE Controller & Automation Bridge (v1.5)

Applicazione Android open source per connettere, decodificare e mappare i tasti del telecomando Bluetooth Low Energy **Livall BR80** (noto anche come *BlingRemote*), trasformandolo in un controller versatile per musica, assistente vocale Google Gemini, navigazione, chiamate e automazioni avanzate (**Tasker**, **MacroDroid**, ecc.).

---

## 🎯 Novità Versione 1.5

- **🤖 Google Gemini & Voice Assistant in Background:**
  - Avvio immediato dell'assistente vocale o di Google Gemini da qualsiasi app o a schermo spento/bloccato tramite dispatch nativo `KEYCODE_VOICE_ASSIST` e autorizzazione di visualizzazione su altre app (`SYSTEM_ALERT_WINDOW`).
- **⚡ Auto-Ascolto all'Avvio:**
  - Non appena apri l'app, il motore di scansione BLE si attiva automaticamente se un telecomando era già stato associato in precedenza (nessun click manuale necessario).
- **📱 Scansione Bilanciata ad Alta Reattività (BALANCED):**
  - Cattura istantanea (< 30ms) dei pacchetti BLE inviati dal telecomando Livall BR80 quando premi un tasto, evitando perdite di segnale.
- **🛡️ Modalità Standby Listener & Riconnessione Istantanea:**
  - Risolto il problema di disconnessione quando lo smartphone o il telecomando entrano in standby.
- **⚡ Riconnessione a 1-Click (Fix Errore GATT 133):**
  - Eliminata la necessità di premere connetti/disconnetti ripetutamente grazie alla pulizia cache GATT con reflection (`refresh()`).
- **💓 Keep-Alive 35s Attivo di Default:**
  - Ping periodico a 35s per prevenire lo spegnimento per inattività del telecomando durante i percorsi.

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

---

## 📲 Installazione e Aggiornamento

### 1. Download Diretto
Puoi scaricare l'APK da:
- **[GitHub Releases](https://github.com/rafing22/br80-remote/releases)** (File **`Livall-BR80-Remote-v1.5.apk`**)
- **[GitHub Actions](https://github.com/rafing22/br80-remote/actions)**

### 2. Aggiornamenti In-App
Dalla scheda **⚙️ Opzioni**, tocca **"🔄 Verifica Aggiornamenti su GitHub"**: l'app rileva le nuove versioni e installa l'aggiornamento automaticamente.

---

## 🛠️ Compilazione da Sorgente

```bash
# Compilazione APK Debug
./gradlew assembleDebug

# L'output sarà generato in:
# release_apk/Livall-BR80-Remote-v1.5.apk
```

---

## 📄 Licenza

Rilasciato sotto licenza MIT.
