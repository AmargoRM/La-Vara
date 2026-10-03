package com.lavara.triggers

import com.lavara.automation.Automation
import com.lavara.core.TimeText
import java.time.ZonedDateTime

/**
 * Algo que pasó en el teléfono y puede disparar automatizaciones.
 * [dedupKey] identifica el hecho: si llega dos veces el mismo evento, la segunda se ignora.
 */
sealed interface TriggerEvent {
    val dedupKey: String

    /** Sonó la alarma de la hora [at]. */
    data class TimeReached(val at: ZonedDateTime) : TriggerEvent {
        override val dedupKey: String = "time:${at.toLocalDate()}T${at.hour}:${at.minute}"
    }

    /** Cambió el nivel de batería. [previousLevel] es null si no se conoce. */
    data class BatteryChanged(val level: Int, val previousLevel: Int?) : TriggerEvent {
        override val dedupKey: String = "battery:$previousLevel->$level"
    }

    /**
     * Se conectó ([connected] = true) o se desconectó el cargador. [atMillis] es la hora del aviso de
     * Android: distingue una conexión de la siguiente.
     */
    data class PowerChanged(val connected: Boolean, val atMillis: Long) : TriggerEvent {
        override val dedupKey: String = "power:${if (connected) "connected" else "disconnected"}:$atMillis"
    }

    /**
     * El teléfono entró ([entered] = true) o salió de la zona de la automatización [automationId].
     * Cada zona es de una sola automatización, por eso el evento ya dice a cuál le corresponde.
     */
    data class LocationChanged(val automationId: String, val entered: Boolean, val atMillis: Long) : TriggerEvent {
        override val dedupKey: String = "location:$automationId:${if (entered) "enter" else "exit"}:$atMillis"
    }

    /** Se conectó ([connected] = true) o se desconectó el aparato Bluetooth [address] ([name] para mostrar). */
    data class BluetoothChanged(val address: String, val name: String, val connected: Boolean, val atMillis: Long) : TriggerEvent {
        override val dedupKey: String = "bluetooth:$address:${if (connected) "connected" else "disconnected"}:$atMillis"
    }

    /** Se conectó ([connected] = true) o se desconectó la red Wi-Fi [ssid] (null = Android no dijo el nombre). */
    data class WifiChanged(val ssid: String?, val connected: Boolean, val atMillis: Long) : TriggerEvent {
        override val dedupKey: String = "wifi:${ssid.orEmpty()}:${if (connected) "connected" else "disconnected"}:$atMillis"
    }

    /** El usuario pidió ejecutar [automationId] a mano. [requestId] distingue cada pedido. */
    data class ManualRun(val automationId: String, val requestId: String) : TriggerEvent {
        override val dedupKey: String = "manual:$requestId"
    }
}

/** Decide si un trigger corresponde a un evento. Lógica pura, sin Android. */
object TriggerMatcher {
    fun matches(trigger: Trigger, automationId: String, event: TriggerEvent): Boolean = when (trigger) {
        is Trigger.Time -> event is TriggerEvent.TimeReached &&
            event.at.hour == trigger.timeOfDay().hour &&
            event.at.minute == trigger.timeOfDay().minute &&
            (trigger.days.isEmpty() || Weekday.of(event.at.dayOfWeek) in trigger.days)

        is Trigger.Battery -> event is TriggerEvent.BatteryChanged && crosses(trigger, event)

        is Trigger.Power -> event is TriggerEvent.PowerChanged &&
            event.connected == (trigger.event == PowerEvent.CONNECTED)

        is Trigger.Location -> event is TriggerEvent.LocationChanged && event.automationId == automationId &&
            event.entered == (trigger.transition == LocationTransition.ENTER)

        is Trigger.Bluetooth -> event is TriggerEvent.BluetoothChanged &&
            event.connected == (trigger.event == ConnectionEvent.CONNECTED) &&
            (trigger.deviceAddress.isBlank() || trigger.deviceAddress.equals(event.address, ignoreCase = true))

        is Trigger.Wifi -> event is TriggerEvent.WifiChanged &&
            event.connected == (trigger.event == ConnectionEvent.CONNECTED) &&
            (trigger.ssid.isBlank() || trigger.ssid.trim().equals(event.ssid?.trim(), ignoreCase = true))

        Trigger.Manual -> false
    } || (event is TriggerEvent.ManualRun && event.automationId == automationId)

    // Solo dispara al cruzar el umbral, no en cada cambio mientras sigue del mismo lado.
    private fun crosses(trigger: Trigger.Battery, event: TriggerEvent.BatteryChanged): Boolean {
        val prev = event.previousLevel
        return when (trigger.direction) {
            BatteryDirection.BELOW -> event.level <= trigger.threshold && (prev == null || prev > trigger.threshold)
            BatteryDirection.ABOVE -> event.level >= trigger.threshold && (prev == null || prev < trigger.threshold)
        }
    }

    /** true si alguna automatización activa necesita que La Vara escuche la batería, el cargador o el Wi-Fi. */
    fun needsDeviceWatch(automations: List<Automation>): Boolean =
        automations.any { it.enabled && (it.trigger is Trigger.Battery || it.trigger is Trigger.Power || it.trigger is Trigger.Wifi) }

    private fun Trigger.Time.timeOfDay() = TimeText.parseOrNull(time)!!
}
