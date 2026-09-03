# Livall BR80 Remote — Android BLE Controller & Automation Bridge (v3.2)

Applicazione Android open source per connettere, decodificare e mappare i tasti del telecomando Bluetooth Low Energy **Livall BR80** (noto anche come *BlingRemote*), trasformandolo in un controller versatile per musica, assistente vocale Google Gemini, navigazione, chiamate e automazioni avanzate (**Tasker**, **MacroDroid**, ecc.).

---

## 🔋 Novità Versione 3.2 — Ottimizzazioni batteria BLE, fix di stabilità, pulizia codice

### Consumo batteria (telefono e telecomando)
- **Filtro di scansione corretto**: dopo il primo abbinamento, la scansione filtra ora esclusivamente sul MAC del telecomando — prima un filtro generico residuo affiancato annullava di fatto il filtro MAC, facendo risalire alla CPU ogni dispositivo BLE nei paraggi invece di lasciar scartare in autonomia dall'hardware.
- **Ascolto standby più leggero**: la scansione passiva di lunga durata (in attesa che tu prema un tasto) ora usa `SCAN_MODE_LOW_POWER` invece di `BALANCED`, riducendo il consumo radio di 3-5 volte a fronte di una latenza di rilevamento comunque nell'ordine dei secondi.
- **Priorità di connessione adattiva nel tempo**: la modalità ad alta velocità (`CONNECTION_PRIORITY_HIGH`) usata per l'handshake iniziale viene ora riportata automaticamente a `BALANCED` non appena il telecomando è pronto, invece di restare attiva per l'intera sessione — beneficio diretto anche per la batteria del telecomando stesso, non solo del telefono.

### Fix di stabilità
- **Audio non più bloccato dopo Gemini**: se l'apertura del canale voce SCO verso l'interfono falliva o andava in timeout, il telefono restava con l'audio bloccato in modalità chiamata. Corretto, insieme a un guard contro sessioni Gemini sovrapposte.
- **Gesti più affidabili**: un click fisico molto rapido non viene più scambiato per un glitch hardware e "perso", evitando falsi scatti di pressione lunga.
- **"Disconnetti" sempre rispettato**: annullare la connessione nella finestra di avvio (150ms) ora impedisce davvero l'apertura del canale GATT, invece di procedere comunque.
- **Esportazione Tasker**: rimosso un tag XML duplicato nel progetto esportato.

### Pulizia
- Rimosso codice morto (import, stringhe e id XML inutilizzati) e risolti tutti i warning di compilazione residui (API deprecate sostituite con le controparti moderne dove disponibile un'alternativa).

---

## 🐛 Novità Versione 3.1 — Fix notifica persistente

- **Corretto "Esci / Chiudi Applicazione"**: la notifica del servizio in background a volte non spariva. Causa: la disconnessione Bluetooth è asincrona e il suo callback tardivo ripubblicava la notifica subito dopo averla rimossa. Ora la rimozione è garantita e i callback tardivi vengono ignorati durante lo spegnimento.

---

## 🎛️ Novità Versione 3.0 — Redesign "Cruscotto" e pulizia interfaccia

### Nuova interfaccia scura ispirata al telecomando reale
- **Tema "Cruscotto" su tutta l'app:** sfondo scuro, card con accento rosso coerente con l'unico colore acceso presente sul telecomando fisico (il tasto centrale).
- **D-pad a sagoma reale:** il controller nella tab principale riproduce la forma vera del BR80 (corpo a pillola, alette per fotocamera/voce, ghiera con frecce e L/R), non più semplici pulsanti quadrati.
- **Gauge analogici Batteria / RSSI:** due indicatori ad arco in stile cruscotto mostrano in tempo reale il livello di batteria e la potenza del segnale Bluetooth del telecomando.
- **Card "Ultima Azione Eseguita":** mostra tasto, gesto e azione dell'ultimo comando ricevuto dal telecomando, senza dover controllare il log.
- **Tutti i dialog e i popup ritemati:** selezione dispositivi Bluetooth, profili, scelta azione e gestione testi TTS ora coerenti con il tema scuro (prima apparivano con lo stile chiaro di sistema).

### Testi TTS riorganizzati
- Il pannello "Gestisci Testi TTS" ora raggruppa le voci per tasto fisico (badge "UP", "CALL", ecc.) invece di un'unica lista piatta, con tag di gesto in stile monospace ("1x", "2x", "3x", "LONG") e un'etichetta "● PERSONALIZZATO" mostrata solo sulle frasi effettivamente modificate.

### Pulizia funzionalità
- **Rimosso l'Auto-Apprendimento del Ritmo Tap** (calibrazione a 3 tentativi): restano i 4 preset rapidi (Sportivo 280ms, Standard 420ms, Guanti 550ms, Lento 700ms) per impostare la finestra del doppio/triplo tap.

---

## 🎯 Novità Versione 2.x

### Stabilità BLE per uso motociclistico
- **Coda FIFO per le operazioni GATT:** scritture/letture/descrittori vengono serializzate invece di essere concorrenti, prevenendo errori GATT 133 in caso di comandi ravvicinati.
- **`connect()` idempotente:** richieste di connessione duplicate (es. per race tra `onCreate`/`onStart` dell'activity) vengono ignorate se una connessione è già in corso o attiva, eliminando le connessioni GATT concorrenti che causavano blocchi.
- **Backoff esponenziale** sulla riconnessione automatica (1s, 2.5s, 5s, 10s) e watchdog di standby per il risveglio del telecomando.
- **Ripristino automatico del Bluetooth di sistema:** se l'utente spegne/riaccende il Bluetooth del telefono durante la guida, l'ascolto riparte da solo.

### Keep-Alive condizionale e chiusura pulita
- **Keep-Alive condizionale a dispositivo BT specifico** (es. interfono/casco): il ping anti-standby e l'ascolto reattivo si attivano/disattivano automaticamente in base alla connessione del dispositivo scelto — mutuamente esclusivo con il Keep-Alive Always-On.
- **"Disconnetti"** ora mette il telecomando in ascolto passivo reale: premendo un tasto fisico, l'app si riconnette da sola senza dover toccare "Connetti".
- **Pulsante "Esci"** per l'arresto completo e pulito di app, servizio e connessione GATT.

### Canale voce garantito per l'interfono (SCO Gateway)
- Prima di attivare **Google Gemini**, l'app apre esplicitamente il canale voce Bluetooth (SCO) verso l'interfono e attende la conferma di sistema prima di procedere, evitando che il comando parta a vuoto o venga perso perché il canale non era ancora pronto.
- Il canale viene rilasciato automaticamente a fine ascolto (monitor di sistema + timeout di sicurezza), senza tenere il microfono attivo inutilmente.
- Il TTS di conferma azione continua a usare il canale audio stereo (A2DP) normale, per non degradarne la qualità né interferire con l'apertura del canale voce.

### Feedback vocale (TTS) e profili
- **Annuncio vocale (TTS)** configurabile per ogni azione eseguita, con soppressione di annunci duplicati ravvicinati e nessun annuncio per "Nessuna Azione".
- **Profili di mappatura multipli:** crea, cambia ed elimina profili di mappatura direttamente dall'app.
- **Esportazione log diagnostico** in `.txt` condivisibile per supporto/debug.

### Aggiornamenti
- Verifica e installazione degli aggiornamenti in-app corretta (permesso `INTERNET` e `REQUEST_INSTALL_PACKAGES` dichiarati correttamente).
- **Keystore di debug condiviso** committato nel repository: build locali e build CI producono APK firmati in modo identico, così gli aggiornamenti in-app funzionano sempre senza conflitti di firma.

### Novità Versione 2.4 — Rilevamento gesti riprogettato e dispositivi multipli
- **Rilevamento tap riprogettato** secondo le best practice standard (debounce + finestra scorrevole + guardia anti-glitch):
  - **Debounce hardware** (~35ms): filtra i pacchetti PRESS/RELEASE spuri che il telecomando a volte invia per una singola pressione fisica, che prima corrompevano il conteggio dei tap.
  - **Guardia press/release fantasma:** un RELEASE senza PRESS corrispondente (o viceversa) viene ignorato, eliminando i falsi "LONG" o "SINGLE" fantasma osservati nei log.
  - **Finestra scorrevole per doppio/triplo tap:** il timer si riavvia ad ogni tap invece di essere fisso dal primo, così anche un triplo tap con cadenza più lenta viene riconosciuto correttamente (prima veniva letto come doppio tap).
  - **LONG press reattivo:** ora scatta nell'istante esatto in cui superi la soglia configurata, mentre tieni ancora premuto, senza dover attendere il rilascio del tasto.
- **Connessione GATT ulteriormente rinforzata:** guardia anti-duplicazione spostata al livello più basso (`connectGattTo`), così nessun percorso (scanner, watchdog, retry) può più avviare due connessioni concorrenti verso lo stesso telecomando, anche dopo lunghe pause di inattività del telefono.
- **Dispositivi Bluetooth multipli:** sia il Keep-Alive condizionale sia il canale audio SCO per TTS/Gemini supportano ora la selezione di più dispositivi contemporaneamente (es. più interfoni/cuffie): la funzione resta attiva finché almeno uno dei dispositivi scelti è connesso.
- **Canale voce SCO più affidabile:** margine di assestamento audio dopo la conferma di apertura del canale e impostazione esplicita di `AudioManager.MODE_IN_COMMUNICATION`, per evitare che l'inizio del beep di attivazione di Gemini venga perso su alcuni dispositivi.
- **TTS prima di Gemini rimosso:** l'annuncio vocale di conferma prima dell'attivazione di Gemini tagliava a metà frase a causa del cambio di canale audio A2DP → SCO; resta solo vibrazione/beep immediati.

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
- **[GitHub Releases](https://github.com/rafing22/br80-remote/releases)** (File **`Livall-BR80-Remote-v3.0.apk`**)
- **[GitHub Actions](https://github.com/rafing22/br80-remote/actions)**

### 2. Aggiornamenti In-App
Dalla scheda **⚙️ Opzioni**, tocca **"🔄 Verifica Aggiornamenti su GitHub"**: l'app rileva le nuove versioni e installa l'aggiornamento automaticamente.

---

## 🛠️ Compilazione da Sorgente

Il repository include il wrapper Gradle e un keystore di debug condiviso, per build riproducibili identiche tra CI e macchine locali.

```bash
# Compilazione APK Debug
./gradlew assembleDebug

# L'output sarà generato in:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 🚀 Processo di Rilascio

Versione e changelog meccanico (`CHANGELOG.md`) sono generati automaticamente da [release-please](https://github.com/googleapis/release-please) a partire dai messaggi di commit, che devono seguire [Conventional Commits](https://www.conventionalcommits.org/):

- `fix: ...` → nuova versione patch
- `feat: ...` → nuova versione minor
- `feat!: ...` o `BREAKING CHANGE:` nel footer → nuova versione major
- `chore:`, `docs:`, `ci:`, `refactor:` → nessun bump di versione, compaiono comunque nel changelog

Ad ogni push su `main` con commit di questo tipo, si apre/aggiorna automaticamente una Release PR con versione e changelog già pronti. Mergiandola parte da sola la pubblicazione: tag, GitHub Release e build dell'APK allegato. Le sezioni "Novità Versione X.Y" qui sopra restano scritte a mano per le release principali, come vetrina più discorsiva accanto al changelog meccanico.

---

## 📄 Licenza

Rilasciato sotto licenza MIT.
