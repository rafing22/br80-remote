package com.br80.remote

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

class GestureDetector(
    private val mappingStorage: MappingStorage,
    private val onGestureDetected: (Br80Button, GestureType) -> Unit
) {

    private val handler = Handler(Looper.getMainLooper())

    private data class ButtonState(
        var pressTimestamp: Long = 0L,
        var tapCount: Int = 0,
        var pendingRunnable: Runnable? = null
    )

    private val buttonStates = mutableMapOf<Br80Button, ButtonState>()

    private fun getState(button: Br80Button): ButtonState {
        return buttonStates.getOrPut(button) { ButtonState() }
    }

    fun onButtonRawEvent(button: Br80Button, isPress: Boolean) {
        val state = getState(button)

        if (isPress) {
            state.pressTimestamp = SystemClock.uptimeMillis()
            // Se c'è un timer multi-tap pendente, lo annulliamo per attendere il nuovo tap
            state.pendingRunnable?.let {
                handler.removeCallbacks(it)
                state.pendingRunnable = null
            }
        } else {
            val duration = SystemClock.uptimeMillis() - state.pressTimestamp

            if (duration >= LONG_PRESS_THRESHOLD_MS) {
                // Pressione lunga scatta immediatamente
                state.tapCount = 0
                state.pendingRunnable?.let {
                    handler.removeCallbacks(it)
                    state.pendingRunnable = null
                }
                onGestureDetected(button, GestureType.LONG)
            } else {
                // Pressione breve (tap)
                val hasMultiTap = mappingStorage.hasMultiTapGestures(button)

                if (!hasMultiTap) {
                    // ZERO-LATENCY: Nessun multi-tap configurato per questo tasto, dispatch istantaneo!
                    state.tapCount = 0
                    onGestureDetected(button, GestureType.SINGLE)
                } else {
                    // C'è un doppio o triplo tap configurato: accumuliamo il tap e apriamo la finestra
                    state.tapCount++
                    val currentTaps = state.tapCount

                    val runnable = Runnable {
                        val gesture = when (currentTaps) {
                            1 -> GestureType.SINGLE
                            2 -> GestureType.DOUBLE
                            else -> GestureType.TRIPLE
                        }
                        state.tapCount = 0
                        state.pendingRunnable = null
                        onGestureDetected(button, gesture)
                    }

                    state.pendingRunnable = runnable
                    handler.postDelayed(runnable, MULTI_TAP_WINDOW_MS)
                }
            }
        }
    }

    fun reset() {
        for ((_, state) in buttonStates) {
            state.pendingRunnable?.let { handler.removeCallbacks(it) }
            state.pendingRunnable = null
            state.tapCount = 0
        }
    }

    companion object {
        const val LONG_PRESS_THRESHOLD_MS = 500L
        const val MULTI_TAP_WINDOW_MS = 350L
    }
}
