package com.br80.remote

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Nessun evento di accessibilità viene realmente intercettato: il servizio esiste solo
 * per esporre performGlobalAction (Indietro/Home/Blocca Schermo), l'unico modo per
 * un'app normale di simulare questi tasti di sistema senza root.
 */
class Br80AccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    companion object {
        var instance: Br80AccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }
}
