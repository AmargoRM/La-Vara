package com.lavara.automation

import com.lavara.actions.Action
import com.lavara.actions.Phone
import com.lavara.actions.flatten
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
        val nfc = trigger
        if (nfc is Trigger.Nfc && nfc.tagId.isBlank()) add("Falta grabar la etiqueta NFC (paso 1, \"Grabar una etiqueta\").")
        if (actions.isEmpty()) add("Falta al menos una acción en el paso 3.")
        actions.forEachIndexed { i, action -> addAll(problemsOf(action, "La acción ${i + 1}", insideIf = false)) }
    }

    /** Problemas de una acción; [label] es "La acción 3" o "La acción 3 (si se cumple, 2)". */
    private fun problemsOf(action: Action, label: String, insideIf: Boolean): List<String> = buildList {
        when (action) {
            is Action.ShowNotification -> if (action.title.isBlank()) add("$label necesita un título.")
            is Action.RunAutomation -> when {
                action.automationId.isBlank() -> add("$label necesita elegir qué automatización ejecutar.")
                original != null && action.automationId == original.id -> add("$label se ejecuta a sí misma.")
            }
            is Action.Delay -> when {
                insideIf && action.seconds > MAX_DELAY_SECONDS ->
                    add("$label: dentro de un \"si\" la espera máxima es de $MAX_DELAY_SECONDS segundos.")
                action.seconds > MAX_WAIT_SECONDS -> add("$label espera más de 24 horas; ese es el máximo.")
            }
            is Action.OpenUrl -> if (action.host == "enlace" || !action.host.contains('.')) {
                add("Falta el enlace de ${label.lowercase()}: pegalo en el campo \"Enlace\".")
            }
            is Action.WhatsAppMessage -> when {
                action.phone.none { it.isDigit() } -> add("$label necesita un número o un contacto.")
                !Phone.hasCountryCode(action.phone) ->
                    add("El número de ${label.lowercase()} necesita el código de país adelante (506 para Costa Rica).")
            }
            is Action.SendSms -> when {
                action.phone.count { it.isDigit() } < 3 -> add("$label necesita un número o un contacto.")
                action.text.isBlank() -> add("$label necesita el texto del SMS.")
            }
            is Action.DialNumber -> if (action.phone.count { it.isDigit() } < 3) add("$label necesita un número o un contacto.")
            is Action.Navigate -> if (action.destination.isBlank()) add("$label necesita el destino.")
            is Action.TapInApp -> when {
                action.packageName.isBlank() -> add("$label necesita elegir en qué app tocar.")
                action.button.isBlank() -> add("$label necesita el texto del botón (ej.: Enviar).")
                action.waitSeconds !in 1..MAX_DELAY_SECONDS.toInt() ->
                    add("$label espera entre 1 y $MAX_DELAY_SECONDS segundos a que abra la app.")
            }
            is Action.TouchScreen -> when {
                action.packageName.isBlank() -> add("$label necesita elegir en qué app tocar.")
                listOfNotNull(action.x, action.y, action.toX, action.toY).any { it !in 0.0..1.0 } ->
                    add("$label tiene un punto fuera de la pantalla. Grabalo de nuevo.")
                action.pauseMillis !in 0..MAX_DELAY_SECONDS * 1000 ->
                    add("$label espera entre 0 y $MAX_DELAY_SECONDS segundos antes de tocar.")
                action.durationMillis !in 1..MAX_DELAY_SECONDS * 1000 -> add("$label dura demasiado. Grabalo de nuevo.")
                action.waitSeconds !in 1..MAX_DELAY_SECONDS.toInt() ->
                    add("$label espera entre 1 y $MAX_DELAY_SECONDS segundos a que abra la app.")
            }
            is Action.CopyToClipboard -> if (action.text.isBlank()) add("$label necesita el texto a copiar.")
            is Action.ShareText -> if (action.text.isBlank()) add("$label necesita el texto a compartir.")
            is Action.ReplyToNotification -> when {
                action.packageName.isBlank() -> add("$label necesita elegir de qué app es la notificación.")
                action.text.isBlank() -> add("$label necesita el texto de la respuesta.")
            }
            is Action.TapNotificationButton -> when {
                action.packageName.isBlank() -> add("$label necesita elegir de qué app es la notificación.")
                action.button.isBlank() -> add("$label necesita el nombre del botón (ej.: Marcar como leído).")
            }
            is Action.IfElse -> {
                if (action.then.isEmpty() && action.otherwise.isEmpty()) add("$label (\"si\") no tiene acciones adentro.")
                action.then.forEachIndexed { j, inner -> addAll(problemsOf(inner, "$label (si se cumple, ${j + 1})", insideIf = true)) }
                action.otherwise.forEachIndexed { j, inner -> addAll(problemsOf(inner, "$label (si no, ${j + 1})", insideIf = true)) }
            }
            is Action.OpenApp, is Action.Flashlight, is Action.SetVolume, is Action.SetRingerMode,
            is Action.DoNotDisturb, is Action.SetBrightness, is Action.OpenSystemPanel, is Action.Vibrate, is Action.MediaControl -> Unit
        }
    }

    /** Nombre que se usa si el usuario no escribe ninguno: dice cuándo se dispara y qué hace. */
    fun autoName(): String = AutoName.of(trigger, actions)

    /** La automatización lista para guardar. [newId] se usa solo si es nueva; [now] en milisegundos. */
    fun toAutomation(newId: String, now: Long): Automation {
        val savedConditions = if (matchAll || conditions.isEmpty()) conditions else listOf(Condition.Or(conditions))
        val base = original ?: Automation(id = newId, name = name, trigger = trigger, createdAt = now)
        return base.copy(
            name = name.trim().ifBlank { autoName() },
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
        /** Lo máximo que "Tocar un botón" espera a que abra la app. */
        const val MAX_DELAY_SECONDS = 10L

        /**
         * Espera máxima de la acción "Esperar". Las de más de 10 segundos siguen con una alarma exacta
         * (ver docs/LIMITES_ANDROID.md), así que no dejan a La Vara despierta.
         */
        const val MAX_WAIT_SECONDS = 24 * 3600L

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
            all.filter { other -> other.id != id && other.actions.flatMap { it.flatten() }.any { it is Action.RunAutomation && it.automationId == id } }
                .map { it.name }
    }
}
