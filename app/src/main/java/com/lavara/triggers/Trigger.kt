package com.lavara.triggers

import com.lavara.core.TimeText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Cuándo se dispara una automatización. Cada tipo tiene un @SerialName fijo:
 * es el valor de "type" en el JSON y nunca se cambia (ver docs/FORMATO_JSON.md).
 */
@Serializable
sealed interface Trigger {

    /** A una hora fija, los días indicados. Lista de días vacía = todos los días. */
    @Serializable
    @SerialName("time")
    data class Time(
        val time: String,
        val days: List<Weekday> = emptyList(),
    ) : Trigger {
        init {
            TimeText.requireValid(time, "time")
        }
    }

    /** Cuando la batería cruza un umbral, hacia abajo o hacia arriba. */
    @Serializable
    @SerialName("battery")
    data class Battery(
        val threshold: Int,
        val direction: BatteryDirection,
    ) : Trigger {
        init {
            require(threshold in 0..100) { "threshold debe estar entre 0 y 100, no $threshold" }
        }
    }

    /** Cuando se conecta o se desconecta el cargador (cable o base inalámbrica). */
    @Serializable
    @SerialName("power")
    data class Power(
        val event: PowerEvent = PowerEvent.CONNECTED,
    ) : Trigger

    /** Solo se ejecuta a mano (botón, atajo u otra automatización). */
    @Serializable
    @SerialName("manual")
    data object Manual : Trigger
}

@Serializable
enum class BatteryDirection {
    /** La batería baja hasta el umbral o menos. */
    @SerialName("below") BELOW,

    /** La batería sube hasta el umbral o más. */
    @SerialName("above") ABOVE,
}

@Serializable
enum class PowerEvent {
    /** Se enchufa el cargador. */
    @SerialName("connected") CONNECTED,

    /** Se desenchufa el cargador. */
    @SerialName("disconnected") DISCONNECTED,
}

@Serializable
enum class Weekday(val isoNumber: Int) {
    @SerialName("monday") MONDAY(1),
    @SerialName("tuesday") TUESDAY(2),
    @SerialName("wednesday") WEDNESDAY(3),
    @SerialName("thursday") THURSDAY(4),
    @SerialName("friday") FRIDAY(5),
    @SerialName("saturday") SATURDAY(6),
    @SerialName("sunday") SUNDAY(7),
    ;

    companion object {
        fun of(day: java.time.DayOfWeek): Weekday = entries.first { it.isoNumber == day.value }
    }
}
