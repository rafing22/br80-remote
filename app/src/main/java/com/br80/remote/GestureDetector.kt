package com.br80.remote

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

class GestureDetector(
    private val mappingStorage: MappingStorage,
    private val onGestureDetected: (Br80Button, GestureType) -> Unit
) {

    private val handler = Handler(Looper.getMainLooper())

    // Filtra eventi raw duplicati/spuri ravvicinati (glitch hardware del telecomando):
    // il BR80 a volte invia un PRESS o RELEASE fantasma a pochi millisecondi da quello reale.
    private val debounceMs = 35L
    private val maxTapCount = 3

    private data class ButtonState(
        var isPressed: Boolean = false,
        var lastEventTimestamp: Long = 0L,
        var tapCount: Int = 0,
        var windowRunnable: Runnable? = null,
        var longPressRunnable: Runnable? = null,
        var longPressFired: Boolean = false
    )

    private val buttonStates = mutableMapOf<Br80Button, ButtonState>()

    private fun getState(button: Br80Button): ButtonState {
        return buttonStates.getOrPut(button) { ButtonState() }
    }

    fun onButtonRawEvent(button: Br80Button, isPress: Boolean) {
        val state = getState(button)
        val now = SystemClock.uptimeMillis()

        if (now - state.lastEventTimestamp < debounceMs) {
            return
        }
        state.lastEventTimestamp = now

        if (isPress) {
            if (state.isPressed) return // press duplicato senza release intermedio: glitch, ignora
            state.isPressed = true
            state.longPressFired = false

            // Il LONG scatta durante la pressione, appena superata la soglia, senza aspettare
            // il rilascio: più reattivo per azioni tipo torcia/assistente vocale.
            val longRunnable = Runnable {
                state.longPressFired = true
                cancelPendingWindow(state)
                state.tapCount = 0
                onGestureDetected(button, GestureType.LONG)
            }
            state.longPressRunnable = longRunnable
            handler.postDelayed(longRunnable, mappingStorage.getLongPressThresholdMs())
            return
        }

        if (!state.isPressed) return // release senza press corrispondente: glitch, ignora
        state.isPressed = false

        state.longPressRunnable?.let { handler.removeCallbacks(it) }
        state.longPressRunnable = null

        if (state.longPressFired) {
            // Il LONG è già scattato mentre il tasto era premuto: il rilascio non deve
            // generare un ulteriore gesto.
            state.longPressFired = false
            return
        }

        // Pressione breve: rilasciato prima che la soglia LONG scattasse.
        if (!mappingStorage.hasMultiTapGestures(button)) {
            // Nessun doppio/triplo tap configurato per questo tasto: dispatch immediato.
            state.tapCount = 0
            onGestureDetected(button, GestureType.SINGLE)
            return
        }

        state.tapCount++

        if (state.tapCount >= maxTapCount) {
            // Raggiunto il massimo gesto supportato: non serve aspettare la fine della finestra.
            cancelPendingWindow(state)
            state.tapCount = 0
            onGestureDetected(button, GestureType.TRIPLE)
            return
        }

        // Finestra scorrevole: ogni tap la riavvia da capo, così è l'intervallo fra un tap
        // e il successivo a dover stare sotto la soglia, non l'intera sequenza dal primo tap
        // (altrimenti un triplo tap un po' più lento del previsto verrebbe letto come doppio).
        cancelPendingWindow(state)
        val runnable = Runnable {
            val gesture = when (state.tapCount) {
                1 -> GestureType.SINGLE
                2 -> GestureType.DOUBLE
                else -> GestureType.TRIPLE
            }
            state.tapCount = 0
            state.windowRunnable = null
            onGestureDetected(button, gesture)
        }
        state.windowRunnable = runnable
        handler.postDelayed(runnable, mappingStorage.getMultiTapWindowMs())
    }

    private fun cancelPendingWindow(state: ButtonState) {
        state.windowRunnable?.let { handler.removeCallbacks(it) }
        state.windowRunnable = null
    }

    fun reset() {
        for ((_, state) in buttonStates) {
            cancelPendingWindow(state)
            state.longPressRunnable?.let { handler.removeCallbacks(it) }
            state.longPressRunnable = null
            state.tapCount = 0
            state.isPressed = false
            state.longPressFired = false
        }
    }
}
