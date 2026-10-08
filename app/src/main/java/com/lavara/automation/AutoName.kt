package com.lavara.automation

import com.lavara.actions.Action
import com.lavara.triggers.BatteryDirection
import com.lavara.triggers.ConnectionEvent
import com.lavara.triggers.LocationTransition
import com.lavara.triggers.PowerEvent
import com.lavara.triggers.Trigger

/** Nombre automático para una automatización sin nombre: "Al conectar el cargador → Linterna". */
object AutoName {
    fun of(trigger: Trigger, actions: List<Action>): String {
        val what = actions.firstOrNull()?.let { short(it) } ?: "sin acciones"
        val more = if (actions.size > 1) " y ${actions.size - 1} más" else ""
        return "${short(trigger)} → $what$more".take(MAX_LENGTH)
    }

    private fun short(trigger: Trigger): String = when (trigger) {
        is Trigger.Time -> "A las ${trigger.time}"
        is Trigger.Battery -> "Batería ${if (trigger.direction == BatteryDirection.BELOW) "baja a" else "sube a"} ${trigger.threshold} %"
        is Trigger.Power -> if (trigger.event == PowerEvent.CONNECTED) "Al conectar el cargador" else "Al desconectar el cargador"
        is Trigger.Location -> (if (trigger.transition == LocationTransition.ENTER) "Al llegar a " else "Al irme de ") +
            trigger.placeName.ifBlank { "un lugar" }
        is Trigger.Bluetooth -> "Bluetooth " + trigger.deviceName.ifBlank { "cualquiera" } +
            if (trigger.event == ConnectionEvent.CONNECTED) "" else " (al desconectar)"
        is Trigger.Wifi -> "Wi-Fi " + trigger.ssid.ifBlank { "cualquiera" } +
            if (trigger.event == ConnectionEvent.CONNECTED) "" else " (al desconectar)"
        is Trigger.Notification -> "Notificación de " + trigger.appName.ifBlank { "cualquier app" }
        is Trigger.Nfc -> "Etiqueta " + trigger.tagName.ifBlank { "NFC" }
        Trigger.Manual -> "Botón"
    }

    private fun short(action: Action): String = when (action) {
        is Action.ShowNotification -> "Aviso " + action.title
        is Action.OpenApp -> "Abrir app"
        is Action.OpenUrl -> "Abrir ${action.host}"
        is Action.Delay -> "Esperar"
        is Action.RunAutomation -> "Otra automatización"
        is Action.Flashlight -> if (action.on) "Encender linterna" else "Apagar linterna"
        is Action.SetVolume -> "Volumen ${action.percent} %"
        is Action.SetRingerMode -> "Modo ${action.mode.label}"
        is Action.DoNotDisturb -> "No molestar"
        is Action.SetBrightness -> "Brillo"
        is Action.OpenSystemPanel -> action.panel.label.replaceFirstChar { it.uppercase() }
        is Action.WhatsAppMessage -> "WhatsApp" + action.contactName.let { if (it.isBlank()) "" else " a $it" }
        is Action.DialNumber -> "Llamar" + action.contactName.let { if (it.isBlank()) "" else " a $it" }
        is Action.Navigate -> "Navegar" + action.destination.let { if (it.isBlank()) "" else " a $it" }
        is Action.SendSms -> "SMS" + action.contactName.let { if (it.isBlank()) "" else " a $it" }
        is Action.TapInApp -> "Tocar ${action.button}"
        is Action.TouchScreen -> if (action.isSwipe) "Deslizar" else "Tocar la pantalla"
        is Action.Vibrate -> "Vibrar"
        is Action.CopyToClipboard -> "Copiar texto"
        is Action.ShareText -> "Compartir texto"
        is Action.ReplyToNotification -> "Responder" + action.from.let { if (it.isBlank()) "" else " a $it" }
        is Action.TapNotificationButton -> "Tocar ${action.button}"
        is Action.MediaControl -> "Música: ${action.command.label}"
        is Action.IfElse -> "Si…"
    }

    private const val MAX_LENGTH = 60
}
