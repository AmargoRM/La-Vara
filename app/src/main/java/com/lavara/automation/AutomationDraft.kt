package com.lavara.automation

import com.lavara.actions.Action
import com.lavara.actions.Phone
import com.lavara.conditions.Condition
import com.lavara.triggers.Trigger

/**
 * Lo que el editor va armando antes de guardar. Lógica pura: se prueba sin Android.
 *
 * Las condiciones se muestran como una lista con "todas" o "alguna". "Alguna" se guarda como una sola
 * condición `or` con la lista adentro, así el JSON sigue siendo el de docs/FORMATO_JSON.md.
 */
data class AutomationDraft(
    val name: String = "",
    val trigger: Trigger = Trigger.Time("08:00"),
    val matchAll: Boolean = true,
    val conditions: List<Condition> = emptyList(),
    val actions: List<Action> = emptyList(),
    val onError: OnError = OnError.STOP,
    val cooldownSeconds: Long = 0,
    /** La automatización original si se está editando; null si es nueva. */
    val original: Automation? = null,
) {
    /** Lo que impide guardar, en palabras para el usuario. Lista vacía = se puede guardar. */
    fun problems(): List<String> = buildList {
        if (name.isBlank()) add("Falta el nombre de la automatización (arriba).")
        if (actions.isEmpty()) add("Falta al menos una acción en el paso 3.")
        actions.forEachIndexed { i, action ->
            when (action) {
                is Action.ShowNotification -> if (action.title.isBlank()) add("La acción ${i + 1} necesita un título.")
                is Action.RunAutomation -> when {
                    action.automationId.isBlank() -> add("La acción ${i + 1} necesita elegir qué automatización ejecutar.")
                    original != null && action.automationId == original.id -> add("La acción ${i + 1} se ejecuta a sí misma.")
                }
                is Action.Delay -> if (action.seconds > MAX_DELAY_SECONDS) {
                    add("La acción ${i + 1} espera más de $MAX_DELAY_SECONDS segundos; por ahora es el máximo.")
                }
                is Action.OpenUrl -> if (action.host == "enlace" || !action.host.contains('.')) {
                    add("Falta el enlace de la acción ${i + 1}: pegalo en el campo \"Enlace\".")
                }
                is Action.WhatsAppMessage -> when {
                    action.phone.none { it.isDigit() } -> add("La acción ${i + 1} necesita un número o un contacto.")
                    !Phone.hasCountryCode(action.phone) ->
                        add("El número de la acción ${i + 1} necesita el código de país adelante (506 para Costa Rica).")
                }
                is Action.SendSms -> when {
                    action.phone.count { it.isDigit() } < 3 -> add("La acción ${i + 1} necesita un número o un contacto.")
                    action.text.isBlank() -> add("La acción ${i + 1} necesita el texto del SMS.")
                }
                is Action.DialNumber -> if (action.phone.count { it.isDigit() } < 3) {
                    add("La acción ${i + 1} necesita un número o un contacto.")
                }
                is Action.Navigate -> if (action.destination.isBlank()) add("La acción ${i + 1} necesita el destino.")
                is Action.OpenApp, is Action.Flashlight, is Action.SetVolume, is Action.SetRingerMode,
                is Action.DoNotDisturb, is Action.SetBrightness, is Action.OpenSystemPanel -> Unit
            }
        }
    }

    /** La automatización lista para guardar. [newId] se usa solo si es nueva; [now] en milisegundos. */
    fun toAutomation(newId: String, now: Long): Automation {
        val savedConditions = if (matchAll || conditions.isEmpty()) conditions else listOf(Condition.Or(conditions))
        val base = original ?: Automation(id = newId, name = name, trigger = trigger, createdAt = now)
        return base.copy(
            name = name.trim(),
            trigger = trigger,
            conditions = savedConditions,
            actions = actions,
            onError = onError,
            cooldownSeconds = cooldownSeconds,
            updatedAt = now,
        )
    }

    fun moveAction(from: Int, to: Int): AutomationDraft {
        if (from !in actions.indices || to !in actions.indices) return this
        val list = actions.toMutableList()
        list.add(to, list.removeAt(from))
        return copy(actions = list)
    }

    companion object {
        /**
         * Espera máxima que ofrece el editor. Cuando suena una alarma, Android da unos 10 segundos para
         * terminar (ver docs/LIMITES_ANDROID.md); las esperas largas con alarma llegan en S5.
         */
        const val MAX_DELAY_SECONDS = 10L

        /** Borrador nuevo: a las 08:00 todos los días, con una notificación de ejemplo. */
        fun new() = AutomationDraft(actions = listOf(Action.ShowNotification(title = "La Vara", text = "Son las %time")))

        fun from(automation: Automation): AutomationDraft {
            // Una sola condición "or" se muestra como lista con "alguna".
            val single = automation.conditions.singleOrNull()
            val anyOf = single is Condition.Or && single.conditions.isNotEmpty()
            return AutomationDraft(
                name = automation.name,
                trigger = automation.trigger,
                matchAll = !anyOf,
                conditions = if (anyOf) (single as Condition.Or).conditions else automation.conditions,
                actions = automation.actions,
                onError = automation.onError,
                cooldownSeconds = automation.cooldownSeconds,
                original = automation,
            )
        }

        /** Copia para "Duplicar": id nuevo, desactivada y con los contadores en cero. */
        fun duplicate(automation: Automation, newId: String, now: Long) = automation.copy(
            id = newId,
            name = "${automation.name} (copia)",
            enabled = false,
            createdAt = now,
            updatedAt = now,
            lastExecutedAt = null,
            executionCount = 0,
            failureCount = 0,
        )

        /** Nombres de las automatizaciones que ejecutan a [id] con `run_automation`. */
        fun usersOf(id: String, all: List<Automation>): List<String> =
            all.filter { other -> other.id != id && other.actions.any { it is Action.RunAutomation && it.automationId == id } }
                .map { it.name }
    }
}
