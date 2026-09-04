package com.br80.remote.tasker

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.widget.ArrayAdapter
import com.br80.remote.MappingStorage
import com.br80.remote.R
import com.br80.remote.databinding.ActivityConfigBr80EventBinding
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput

/**
 * Schermata mostrata da Tasker quando l'utente crea/modifica l'Evento plugin
 * "Livall BR80 Remote" in un Profilo: sceglie un Tasto Virtuale da uno Spinner, poi
 * conferma con il tasto Indietro di sistema (convenzione standard dei plugin
 * Tasker, gestita da TaskerPluginConfigHelper.onBackPressed()).
 */
class Br80EventConfigActivity : Activity(), TaskerPluginConfig<Br80EventFilter> {

    private lateinit var binding: ActivityConfigBr80EventBinding
    private val taskerHelper by lazy { Br80EventHelper(this) }
    private lateinit var slots: List<com.br80.remote.TaskerVirtualSlot>

    override val context get() = applicationContext

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfigBr80EventBinding.inflate(layoutInflater)
        setContentView(binding.root)

        slots = MappingStorage.getInstance(applicationContext).getTaskerVirtualSlots()

        // Layout custom con testo chiaro: quello di sistema (simple_spinner_dropdown_item)
        // usa testo nero, illeggibile sullo sfondo scuro dei menu Tasker.
        binding.spinnerVirtualSlot.adapter = ArrayAdapter(
            this,
            R.layout.spinner_item_br80,
            slots.map { it.name }
        ).apply { setDropDownViewResource(R.layout.spinner_item_br80) }

        taskerHelper.onCreate()
    }

    override fun assignFromInput(input: TaskerInput<Br80EventFilter>) {
        input.regular.virtualSlotId?.let { savedId ->
            val index = slots.indexOfFirst { it.id == savedId }
            if (index >= 0) binding.spinnerVirtualSlot.setSelection(index)
        }
    }

    override val inputForTasker: TaskerInput<Br80EventFilter>
        get() {
            val slot = slots.getOrNull(binding.spinnerVirtualSlot.selectedItemPosition)
            return TaskerInput(Br80EventFilter(slot?.id))
        }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0) {
            taskerHelper.onBackPressed()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        // Intenzionalmente vuoto: la conferma/validazione passa da onKeyDown() sopra,
        // stessa convenzione usata nel progetto ufficiale di esempio dei plugin Tasker.
    }
}
