package com.br80.remote.tasker

import android.content.Context
import com.br80.remote.Br80Button
import com.br80.remote.GestureType
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerCondition
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerConditionEvent
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied

/**
 * Filtro scelto dall'utente nell'editor del Profilo Tasker: quale combinazione
 * tasto+gesto del BR80 deve far scattare questo specifico Evento plugin.
 * I valori sono i nomi degli enum (Br80Button.name / GestureType.name), non le
 * label leggibili: stabili anche se in futuro cambiano i testi visualizzati.
 */
@TaskerInputRoot
class Br80EventFilter @JvmOverloads constructor(
    @field:TaskerInputField("button") val button: String? = null,
    @field:TaskerInputField("gesture") val gesture: String? = null
)

/** Evento realmente accaduto: confrontato col filtro sopra, e se combacia i suoi
 * campi diventano le variabili %bt_button/%bt_gesture/%bt_battery nella Task Tasker. */
// La libreria richiede che l'oggetto passato a requestQuery() sia annotato anche
// come TaskerInputRoot (non solo TaskerOutputObject), altrimenti lancia
// "Input types must be annotated by either TaskerInputRoot or TaskerInputObject"
// a runtime: usa lo stesso meccanismo di serializzazione sia per l'input del
// filtro sia per il payload dell'evento in arrivo.
@TaskerInputRoot
@TaskerOutputObject()
class Br80EventUpdate @JvmOverloads constructor(
    @field:TaskerInputField("button") @get:TaskerOutputVariable("bt_button", labelResIdName = "tasker_var_label_button") val button: String? = null,
    @field:TaskerInputField("gesture") @get:TaskerOutputVariable("bt_gesture", labelResIdName = "tasker_var_label_gesture") val gesture: String? = null,
    @field:TaskerInputField("battery") @get:TaskerOutputVariable("bt_battery", labelResIdName = "tasker_var_label_battery") val battery: Int? = null
)

class Br80EventRunner : TaskerPluginRunnerConditionEvent<Br80EventFilter, Br80EventUpdate, Br80EventUpdate>() {
    override fun getSatisfiedCondition(context: Context, input: TaskerInput<Br80EventFilter>, update: Br80EventUpdate?): TaskerPluginResultCondition<Br80EventUpdate> {
        if (update == null) return TaskerPluginResultConditionUnsatisfied()
        val matches = input.regular.button == update.button && input.regular.gesture == update.gesture
        if (!matches) return TaskerPluginResultConditionUnsatisfied()
        return TaskerPluginResultConditionSatisfied(context, update)
    }
}

class Br80EventHelper(config: TaskerPluginConfig<Br80EventFilter>) : TaskerPluginConfigHelper<Br80EventFilter, Br80EventUpdate, Br80EventRunner>(config) {
    override val runnerClass = Br80EventRunner::class.java
    override val inputClass = Br80EventFilter::class.java
    override val outputClass = Br80EventUpdate::class.java

    // Traduce i valori grezzi degli enum (es. "UP", "DOUBLE") nelle label leggibili
    // già definite altrove nell'app, per la descrizione mostrata dentro Tasker
    // (es. "BR80: Freccia Su — Doppio Tap") invece dei nomi tecnici.
    override val inputTranslationsForStringBlurb = HashMap<String, (Any?) -> String?>().apply {
        put("button") { raw -> (raw as? String)?.let { name -> runCatching { Br80Button.valueOf(name).displayName }.getOrNull() } ?: raw?.toString() }
        put("gesture") { raw -> (raw as? String)?.let { name -> runCatching { GestureType.valueOf(name).displayName }.getOrNull() } ?: raw?.toString() }
    }
}

/** Notifica a Tasker che è appena avvenuta una pressione, da chiamare da codice
 * qualsiasi (non serve che l'Activity di configurazione sia visibile o in memoria). */
fun notifyBr80TaskerEvent(context: Context, button: Br80Button, gesture: GestureType, batteryLevel: Int) {
    val battery = batteryLevel.takeIf { it in 0..100 }
    TaskerPluginRunnerCondition.requestQuery(
        context,
        Br80EventConfigActivity::class.java,
        Br80EventUpdate(button.name, gesture.name, battery)
    )
}
