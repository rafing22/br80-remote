# Changelog

## 1.0.0 (2026-09-03)


### Features

* background Gemini activation with KEYCODE_VOICE_ASSIST, PendingIntent, and SYSTEM_ALERT_WINDOW permission ([1730b01](https://github.com/rafing22/br80-remote/commit/1730b017a2c02d8a073628c39af98d39589e3137))
* **phase2:** implement Foreground Service, auto-reconnect, gestures, Tasker broadcast and mapping UI ([68228ab](https://github.com/rafing22/br80-remote/commit/68228abd7a94db4c16e63a395b541c3449087066))
* **v1.1:** interactive D-Pad controller, categories with search, Keep-Alive, Gemini/Torch/Mute actions, Tasker exporter ([deab072](https://github.com/rafing22/br80-remote/commit/deab07259d61e420b572fb838b03b8cfbe50a827))
* **v1.2:** in-app updater, realistic BR80 icon/D-Pad, collapsible accordion categories, 15s watchdog auto-healing ([ce80ca4](https://github.com/rafing22/br80-remote/commit/ce80ca47bcdcf015dbb76d0e4d2744ce2449a564))
* **v1.3:** auto-learning tap calibration (3 attempts), tap speed presets, boot receiver for always-on background, robust in-app updater ([d373451](https://github.com/rafing22/br80-remote/commit/d37345109a3327db5a2509a1f4d229ebf6d05ec0))
* **v1.4:** standby listener with scan filters, gatt refresh auto-healing, and 35s keep-alive ([a7e359b](https://github.com/rafing22/br80-remote/commit/a7e359bf92ade355ff253fa39e129cbc0b89341c))


### Bug Fixes

* add missing PowerManager import in BleForegroundService ([bf1d982](https://github.com/rafing22/br80-remote/commit/bf1d982f410e1c2207b489bda6c348e2c8398b8f))
* auto-start listening mode on app launch and optimize balanced scan responsiveness ([de268d7](https://github.com/rafing22/br80-remote/commit/de268d72f1d0983e168ca62c97a34ae1089b9a33))
* keystore di debug condiviso per firma coerente tra CI e build locali ([3afd5ce](https://github.com/rafing22/br80-remote/commit/3afd5ce9acaa4855924d4b671c1d210fadc420f5))
* notifica del servizio non veniva rimossa all'uscita dall'app ([edde94c](https://github.com/rafing22/br80-remote/commit/edde94c557711780fd33eee0320d1b17ff699e8e))
* ottimizzazioni batteria BLE, bugfix stabilità, pulizia codice morto ([05e444c](https://github.com/rafing22/br80-remote/commit/05e444c590151773ad7f6a4cff22844ae1d8ff90))
* rename helper to toColorStateList to avoid method conflict with AppCompatActivity ([35d788b](https://github.com/rafing22/br80-remote/commit/35d788b7c7075330f62b05550ccb74070e92545a))
