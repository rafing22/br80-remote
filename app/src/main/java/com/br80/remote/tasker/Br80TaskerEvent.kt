package com.br80.remote.tasker

import android.content.Context
import com.br80.remote.Br80Button
import com.br80.remote.GestureType
import com.br80.remote.MappingStorage
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
 * Filtro scelto dall'utente nell'editor del Profilo Tasker: quale Tasto Virtuale BR80
 * (identificatore di automazione scelto anche lato app, indipendente dal tasto/gesto
 * fisico) deve far scattare questo specifico Evento plugin.
 */
@TaskerInputRoot
class Br80EventFilter @JvmOverloads constructor(
    @field:TaskerInputField("virtual_slot_id") val virtualSlotId: Int? = null
)

/** Evento realmente accaduto: confrontato col filtro sopra, e se combacia i suoi
 * campi diventano le variabili %bt_slot_id/%bt_slot_name/%bt_button/%bt_gesture/%bt_battery
 * nella Task Tasker. button/gesture/battery restano solo informativi: da questa versione
 * non determinano più se il Profilo scatta (solo virtualSlotId lo fa). */
// La libreria richiede che l'oggetto passato a requestQuery() sia annotato anche
// come TaskerInputRoot (non solo TaskerOutputObject), altrimenti lancia
// "Input types must be annotated by either TaskerInputRoot or TaskerInputObject"
// a runtime: usa lo stesso meccanismo di serializzazione sia per l'input del
// filtro sia per il payload dell'evento in arrivo.
@TaskerInputRoot
@TaskerOutputObject()
class Br80EventUpdate @JvmOverloads constructor(
    @field:TaskerInputField("virtual_slot_id") @get:TaskerOutputVariable("bt_slot_id", labelResIdName = "tasker_var_label_slot_id") val virtualSlotId: Int? = null,
    @field:TaskerInputField("slot_name") @get:TaskerOutputVariable("bt_slot_name", labelResIdName = "tasker_var_label_slot_name") val slotName: String? = null,
    @field:TaskerInputField("button") @get:TaskerOutputVariable("bt_button", labelResIdName = "tasker_var_label_button") val button: String? = null,
    @field:TaskerInputField("gesture") @get:TaskerOutputVariable("bt_gesture", labelResIdName = "tasker_var_label_gesture") val gesture: String? = null,
    @field:TaskerInputField("battery") @get:TaskerOutputVariable("bt_battery", labelResIdName = "tasker_var_label_battery") val battery: Int? = null
)

class Br80EventRunner : TaskerPluginRunnerConditionEvent<Br80EventFilter, Br80EventUpdate, Br80EventUpdate>() {
    override fun getSatisfiedCondition(context: Context, input: TaskerInput<Br80EventFilter>, update: Br80EventUpdate?): TaskerPluginResultCondition<Br80EventUpdate> {
        if (update == null) return TaskerPluginResultConditionUnsatisfied()
        val matches = input.regular.virtualSlotId != null && input.regular.virtualSlotId == update.virtualSlotId
        if (!matches) return TaskerPluginResultConditionUnsatisfied()
        return TaskerPluginResultConditionSatisfied(context, update)
    }
}

class Br80EventHelper(config: TaskerPluginConfig<Br80EventFilter>) : TaskerPluginConfigHelper<Br80EventFilter, Br80EventUpdate, Br80EventRunner>(config) {
    override val runnerClass = Br80EventRunner::class.java
    override val inputClass = Br80EventFilter::class.java
    override val outputClass = Br80EventUpdate::class.java

    // Descrizione mostrata dentro Tasker (es. "BR80: Tasker 3") risolta dal nome
    // attuale del Tasto Virtuale, tramite lo stesso storage usato dall'app.
    override val inputTranslationsForStringBlurb = HashMap<String, (Any?) -> String?>().apply {
        put("virtual_slot_id") { raw ->
            val id = (raw as? Int) ?: (raw as? String)?.toIntOrNull()
            id?.let { MappingStorage.getInstance(config.context).getTaskerVirtualSlotName(it) } ?: raw?.toString()
        }
    }
}

/** Notifica a Tasker che è appena avvenuta una pressione, da chiamare da codice
 * qualsiasi (non serve che l'Activity di configurazione sia visibile o in memoria). */
fun notifyBr80TaskerEvent(context: Context, mappingStorage: MappingStorage, virtualSlotId: Int, button: Br80Button, gesture: GestureType, batteryLevel: Int) {
    val battery = batteryLevel.takeIf { it in 0..100 }
    val slotName = mappingStorage.getTaskerVirtualSlotName(virtualSlotId)
    TaskerPluginRunnerCondition.requestQuery(
        context,
        Br80EventConfigActivity::class.java,
        Br80EventUpdate(virtualSlotId, slotName, button.name, gesture.name, battery)
    )
}
