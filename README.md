# Livall BR80 Remote — Android BLE Controller & Automation Bridge (v1.1)

Applicazione Android open source per connettere, decodificare e mappare i tasti del telecomando Bluetooth Low Energy **Livall BR80** (noto anche come *BlingRemote*), trasformandolo in un controller versatile per musica, assistente vocale Google Gemini, navigazione, chiamate e automazioni avanzate (**Tasker**, **MacroDroid**, ecc.).

---

## 🎯 Novità Versione 1.1

- **🎮 Controller Grafico D-Pad Interattivo:** Rappresentazione grafica del BR80 con disco a 4 frecce + Home e tasti Foto/Chiamata. Cliccando a schermo o premendo il tasto fisico sul telecomando reale in mano, il tasto si illumina e si apre la scheda di configurazione dei suoi 4 gesti.
- **🔍 Catalogo Azioni Esteso con Ricerca Testuale & Categorie:**
  - 🎵 *Media & Volume:* Volume +, Volume -, Play/Pausa, Traccia Succ, Traccia Prec, Muto Toggle.
  - 🧠 *Assistente & AI:* Avvia **Google Gemini / Assistente Vocale**, Registratore Vocale.
  - 🧭 *Navigazione & Mappe:* Naviga verso Destinazione, Apri Google Maps.
  - 📱 *Telefono & Chiamate:* Rispondi, Rifiuta, Chiamata Rapida (Speed Dial).
  - 🛠️ *Utilità & Sistema:* Torcia ON/OFF, Schermo Sempre Acceso, Scatto Fotocamera, Apri App specifica.
  - ⚡ *Automazione:* Broadcast Tasker, Nessuna Azione.
  - **Barra di ricerca rapida** per filtrare all'istante le azioni digitando il nome.
- **🔔 Notifica Persistente con Azioni Rapide:** Visualizza stato e livello batteria in tempo reale con pulsante integrato *"Connetti"* o *"Disconnetti"*.
- **⏱️ Keep-Alive / Always-On (Anti-Standby):** Opzione per inviare un ping periodico che impedisce al telecomando di andare in deep sleep, garantendo risposta al primo tocco.
- **📑 Bottom Navigation Bar a 3 Sezioni:** `[🎮 Controller]` | `[⚙️ Opzioni]` | `[📜 Log Eventi]`.
- **🪄 Esportatore Progetto Tasker Preconfigurato:** Genera e condivide con un tocco il file XML con tutti i 28 trigger già pronti per Tasker.

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

Dalla scheda **⚙️ Opzioni** dell'app puoi toccare **"Esporta Progetto Tasker (.xml)"** per importare istantaneamente tutti i profili e task d'esempio già configurati.

---

## 📲 Installazione e Uso

### 1. Download dell'APK
Puoi scaricare l'ultima versione compilata dell'APK da **GitHub Actions**:
1. Vai nella scheda **[Actions](https://github.com/rafing22/br80-remote/actions)** del repository.
2. Clicca sull'ultima esecuzione del workflow **Build APK**.
3. Nella sezione **Artifacts** in fondo alla pagina, scarica **`Livall-BR80-Remote-v1.1`**.

### 2. Primo Avvio
1. Apri l'app e tocca **"Connetti"**.
2. Concedi i permessi Bluetooth, Notifiche e Accessibilità.
3. Nella scheda **⚙️ Opzioni**, tocca **"Disattiva Doze"** per garantire la reattività a schermo spento.
4. Accendi il telecomando BR80: l'app si connetterà automaticamente, mostrerà la batteria e sarà pronta.

---

## 🛠️ Compilazione da Sorgente

```bash
# Compilazione APK Debug
./gradlew assembleDebug

# L'output sarà generato in:
# release_apk/Livall-BR80-Remote-v1.1.apk
```

---

## 📄 Licenza

Rilasciato sotto licenza MIT.
