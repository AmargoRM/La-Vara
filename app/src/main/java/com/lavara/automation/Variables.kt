package com.lavara.automation

import com.lavara.actions.Action
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Variables de solo lectura que se reemplazan en los textos de las acciones. */
data class Variables(val values: Map<String, String>) {

    fun expand(text: String): String =
        values.entries.fold(text) { acc, (name, value) -> acc.replace(name, value) }

    /** Devuelve la acción con sus textos ya reemplazados. Las acciones sin texto quedan igual. */
    fun applyTo(action: Action): Action = when (action) {
        is Action.ShowNotification -> action.copy(title = expand(action.title), text = expand(action.text))
        is Action.WhatsAppMessage -> action.copy(text = expand(action.text))
        is Action.SendSms -> action.copy(text = expand(action.text))
        is Action.CopyToClipboard -> action.copy(text = expand(action.text))
        is Action.ShareText -> action.copy(text = expand(action.text))
        else -> action
    }

    companion object {
        const val BATTERY = "%battery"
        const val TIME = "%time"
        const val DATE = "%date"

        private val time = DateTimeFormatter.ofPattern("HH:mm")
        private val date = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        fun from(now: ZonedDateTime, batteryLevel: Int?): Variables = Variables(
            mapOf(
                BATTERY to (batteryLevel?.toString() ?: "?"),
                TIME to now.format(time),
                DATE to now.format(date),
            ),
        )
    }
}
