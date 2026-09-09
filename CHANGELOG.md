# Changelog

## [4.5.0](https://github.com/rafing22/br80-remote/compare/v4.4.1...v4.5.0) (2026-09-09)


### Features

* avvia subito la connessione al rilevamento, notifica solo informativa ([626d090](https://github.com/rafing22/br80-remote/commit/626d09061b60f77d93674b49ae360ef87a1b16e0))

## [4.4.1](https://github.com/rafing22/br80-remote/compare/v4.4.0...v4.4.1) (2026-09-09)


### Bug Fixes

* popup "telecomando rilevato" mai mostrato per FLAG_IMMUTABLE sul PendingIntent ([af52071](https://github.com/rafing22/br80-remote/commit/af52071a0074e5bbc53652ea9eac07ed4afec551))

## [4.4.0](https://github.com/rafing22/br80-remote/compare/v4.3.9...v4.4.0) (2026-09-09)


### Features

* popup di notifica quando il BR80 viene rilevato ad app chiusa ([1080254](https://github.com/rafing22/br80-remote/commit/1080254f710a3a6d15678a6f280bcde910e0eacb))

## [4.3.9](https://github.com/rafing22/br80-remote/compare/v4.3.8...v4.3.9) (2026-09-08)


### Bug Fixes

* race condition nella riconnessione BLE dopo standby prolungato ([4a8019e](https://github.com/rafing22/br80-remote/commit/4a8019e625712f8f50add30d4a527db9e8a3520a))

## [4.3.8](https://github.com/rafing22/br80-remote/compare/v4.3.7...v4.3.8) (2026-09-07)


### Bug Fixes

* widget Esci non funzionante e Riconnetti poco chiaro ([c4194cc](https://github.com/rafing22/br80-remote/commit/c4194cc1933fe1b93876dac17af0669bce2f4eac))

## [4.3.7](https://github.com/rafing22/br80-remote/compare/v4.3.6...v4.3.7) (2026-09-07)


### Bug Fixes

* BLE handshake deadlock and silent auto-heal drop, add diagnostic phase ([bd7d0b1](https://github.com/rafing22/br80-remote/commit/bd7d0b1c577e50cbe2fdcc793f8cac886ffc4527))

## [4.3.6](https://github.com/rafing22/br80-remote/compare/v4.3.5...v4.3.6) (2026-09-07)


### Bug Fixes

* don't retry GATT write on timeout, only on explicit failure status ([5e87606](https://github.com/rafing22/br80-remote/commit/5e876062d7a5eb760fe5d93901d5cd79dede8b5a))

## [4.3.5](https://github.com/rafing22/br80-remote/compare/v4.3.4...v4.3.5) (2026-09-07)


### Bug Fixes

* rewrite BLE connection handling with coroutines for stability ([936af3d](https://github.com/rafing22/br80-remote/commit/936af3d14cece42b36735dc144b2d2fe4f02d881))

## [4.3.4](https://github.com/rafing22/br80-remote/compare/v4.3.3...v4.3.4) (2026-09-07)


### Bug Fixes

* gate conditional Keep-Alive activation behind its own toggle ([3049541](https://github.com/rafing22/br80-remote/commit/30495415153b34929b8c7d1e5d23872e28d70057))

## [4.3.3](https://github.com/rafing22/br80-remote/compare/v4.3.2...v4.3.3) (2026-09-07)


### Bug Fixes

* conditional Keep-Alive never activated on headset connect ([7ba8e15](https://github.com/rafing22/br80-remote/commit/7ba8e151e04b5f7e7f959793c24e3566a3352d82))

## [4.3.2](https://github.com/rafing22/br80-remote/compare/v4.3.1...v4.3.2) (2026-09-07)


### Bug Fixes

* crash on first launch when Log/Options fragments hidden in same transaction ([3d8d9d5](https://github.com/rafing22/br80-remote/commit/3d8d9d59456a2018ff63af1cd0082027333587df))

## [4.3.1](https://github.com/rafing22/br80-remote/compare/v4.3.0...v4.3.1) (2026-09-07)


### Bug Fixes

* widget fails to load — plain View not allowed in RemoteViews ([27510fc](https://github.com/rafing22/br80-remote/commit/27510fcd728d5195c1a2b9198a6865464e303e72))

## [4.3.0](https://github.com/rafing22/br80-remote/compare/v4.2.4...v4.3.0) (2026-09-07)


### Features

* add home screen widget for status and quick controls ([35a4dab](https://github.com/rafing22/br80-remote/commit/35a4dab6dda98d554763d65552c4bd72fdfaf164))

## [4.2.4](https://github.com/rafing22/br80-remote/compare/v4.2.3...v4.2.4) (2026-09-06)


### Bug Fixes

* Keep-Alive off by default, auto-enabled by conditional BT device ([0abdd41](https://github.com/rafing22/br80-remote/commit/0abdd41820c4e14233e826ebce65bc25d0202d2c))

## [4.2.3](https://github.com/rafing22/br80-remote/compare/v4.2.2...v4.2.3) (2026-09-06)


### Bug Fixes

* clean up dead code, duplicated log timestamp, missing Tasker strings ([d1d19a2](https://github.com/rafing22/br80-remote/commit/d1d19a2b603ef4c45f3c59ca221db1001ede0296))

## [4.2.2](https://github.com/rafing22/br80-remote/compare/v4.2.1...v4.2.2) (2026-09-06)


### Bug Fixes

* improve interfono SCO channel reliability after BLE reconnects ([393e133](https://github.com/rafing22/br80-remote/commit/393e1331c9df7518b768c920dd4076ff623f6de1))

## [4.2.1](https://github.com/rafing22/br80-remote/compare/v4.2.0...v4.2.1) (2026-09-06)


### Bug Fixes

* lancia Gemini a completamento reale della frase di pre-riscaldamento ([25e1ccd](https://github.com/rafing22/br80-remote/commit/25e1ccd06c60f4649eb4d0f670950b961b81aa6a))

## [4.2.0](https://github.com/rafing22/br80-remote/compare/v4.1.0...v4.2.0) (2026-09-04)


### Features

* pre-riscaldamento canale interfono prima di Gemini ([10e9bd2](https://github.com/rafing22/br80-remote/commit/10e9bd2e932510952266f191db1b439149296e1c))

## [4.1.0](https://github.com/rafing22/br80-remote/compare/v4.0.0...v4.1.0) (2026-09-04)


### Features

* elimina Tasti Virtuali Tasker dalla schermata di gestione ([417e79e](https://github.com/rafing22/br80-remote/commit/417e79ecbbd0cde73c71c39e05e5c6b6b8a09b50))

## [4.0.0](https://github.com/rafing22/br80-remote/compare/v3.7.0...v4.0.0) (2026-09-04)


### ⚠ BREAKING CHANGES

* i Profili Tasker già configurati con il vecchio abbinamento tasto+gesto smettono di scattare (fallimento silenzioso, nessun crash) finché non vengono riconfigurati scegliendo un Tasto Virtuale, sia nell'app sia nella configurazione dell'Evento dentro Tasker.

### Features

* tasti virtuali Tasker e testi TTS per-azione ([aa65d75](https://github.com/rafing22/br80-remote/commit/aa65d75a0110cc26ec52c00d5e16173b842c0891))

## [3.7.0](https://github.com/rafing22/br80-remote/compare/v3.6.0...v3.7.0) (2026-09-04)


### Features

* chiudi Gemini con "Indietro" prima di rilanciarlo ([6052a49](https://github.com/rafing22/br80-remote/commit/6052a4993f7a690aa760668061bed6d6eb8bfb9f))

## [3.6.0](https://github.com/rafing22/br80-remote/compare/v3.5.0...v3.6.0) (2026-09-04)


### Features

* firma di release dedicata e icona coerente col tema cruscotto ([bb77816](https://github.com/rafing22/br80-remote/commit/bb7781615ed1875dbcb1adea4f3e9e692471a84d))
* ritardo configurabile lancio Gemini dopo apertura canale interfono ([aade18f](https://github.com/rafing22/br80-remote/commit/aade18f6ebf6a13be418abef52196e718d2c78c7))


### Bug Fixes

* mappature tasto non applicate alle pressioni reali e doppio bip Gemini ([a4a892b](https://github.com/rafing22/br80-remote/commit/a4a892b64cebaebcecf513584bce29262d2096d2))

## [3.5.0](https://github.com/rafing22/br80-remote/compare/v3.4.0...v3.5.0) (2026-09-04)


### Features

* redesign UI (MainActivity in Fragment, Opzioni a elenco, nuovo picker azioni, Room) ([9ddb1f1](https://github.com/rafing22/br80-remote/commit/9ddb1f1c74740bd45a1e607d574cff9f485aa4a3))


### Bug Fixes

* bug scoperti dalla revisione indipendente di stile/architettura ([1f57ccd](https://github.com/rafing22/br80-remote/commit/1f57ccd4a592a1a049972639b5bd853ebea5daa5))

## [3.4.0](https://github.com/rafing22/br80-remote/compare/v3.3.3...v3.4.0) (2026-09-04)


### Features

* vero plugin Tasker (Evento) al posto dell'export XML ([cf1cc5e](https://github.com/rafing22/br80-remote/commit/cf1cc5e25f3ae396999724b1b6c5bf80cb725839))

## [3.3.3](https://github.com/rafing22/br80-remote/compare/v3.3.2...v3.3.3) (2026-09-04)


### Bug Fixes

* esportazione Tasker non funzionante (intent sbagliato + XML non valido) ([a5ce81b](https://github.com/rafing22/br80-remote/commit/a5ce81bc441a8d3d68b12777a4ee19d4329b7af3))

## [3.3.2](https://github.com/rafing22/br80-remote/compare/v3.3.1...v3.3.2) (2026-09-04)


### Bug Fixes

* richiede i permessi anche quando l'app si auto-connette all'avvio ([1054020](https://github.com/rafing22/br80-remote/commit/105402049dab06c7fee388b0d69fe6970a59af13))

## [3.3.1](https://github.com/rafing22/br80-remote/compare/v3.3.0...v3.3.1) (2026-09-03)


### Bug Fixes

* le azioni chiamata avviano davvero la chiamata, registratore vocale funzionante ([9ef4469](https://github.com/rafing22/br80-remote/commit/9ef44695a46e0a34bc7009fbcadebc893a1ba70c))

## [3.3.0](https://github.com/rafing22/br80-remote/compare/v3.2.2...v3.3.0) (2026-09-03)


### Features

* bugfix BLE confermati dal vivo + 6 nuove azioni mappabili ([3ed4ac5](https://github.com/rafing22/br80-remote/commit/3ed4ac5f57fadc70407d5506edcdc77804d72339))


### Bug Fixes

* cancella il testo TTS personalizzato quando cambia l'azione mappata ([011b930](https://github.com/rafing22/br80-remote/commit/011b930d693ff82b45a7519d6d946e4eb6d77454))
* **ci:** evita crash del naming APK su build di pull_request ([281aa13](https://github.com/rafing22/br80-remote/commit/281aa138e97e285c7d9a5f31bd8fb819cd7e88f9))

## [3.2.2](https://github.com/rafing22/br80-remote/compare/v3.2.1...v3.2.2) (2026-09-03)


### Bug Fixes

* usa lo stesso PAT di release-please per allegare l'APK alla release ([2b0b650](https://github.com/rafing22/br80-remote/commit/2b0b650d99418f1e0db958818aef4d6e43ed1f40))

## [3.2.1](https://github.com/rafing22/br80-remote/compare/v3.2.0...v3.2.1) (2026-09-03)


### Bug Fixes

* rispetta l'esito reale dei permessi Bluetooth prima di connettersi ([3b14ca5](https://github.com/rafing22/br80-remote/commit/3b14ca5dc45785a42dba909579e37c1461da9fb0))
