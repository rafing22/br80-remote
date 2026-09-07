package com.br80.remote

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/** Attività trasparente aperta dal tasto "Esci" del widget: un semplice servizio non può
 * mostrare una conferma, e con 3 tasti identici in fila su un widget di home screen un tap
 * sbagliato su "Esci" (azione distruttiva, chiude la connessione) è facile — specialmente
 * raggiungendo "Riconnetti" accanto. Questa finestra di conferma copre anche il caso in cui
 * il servizio non sia già vivo: avviare un'Activity da un widget è sempre consentito, a
 * differenza di un PendingIntent verso un Service quando l'app è in background. */
class Br80WidgetExitConfirmActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AlertDialog.Builder(this, R.style.Theme_Br80_CockpitDialog)
            .setTitle("Uscire da BR80 Remote?")
            .setMessage("La connessione al telecomando verrà chiusa e il servizio in background fermato.")
            .setPositiveButton("Esci") { _, _ ->
                val stopIntent = Intent(this, BleForegroundService::class.java).apply {
                    action = BleForegroundService.ACTION_STOP_SERVICE
                }
                ContextCompat.startForegroundService(this, stopIntent)
                finish()
            }
            .setNegativeButton("Annulla") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}
