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

    /**
     * Al entrar o al salir de una zona: un círculo de [radiusMeters] metros alrededor de [latitude], [longitude].
     * [placeName] es solo para mostrar ("Casa"). Necesita el permiso de ubicación "Permitir todo el tiempo".
     */
    @Serializable
    @SerialName("location")
    data class Location(
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Int = 200,
        val transition: LocationTransition = LocationTransition.ENTER,
        val placeName: String = "",
        /**
         * Solo al llegar: minutos que hay que quedarse en la zona antes de que se dispare. 0 = apenas llega.
         * Si se va antes, no se dispara.
         */
        val dwellMinutes: Int = 0,
    ) : Trigger {
        init {
            require(dwellMinutes in 0..MAX_DWELL_MINUTES) { "dwellMinutes debe estar entre 0 y $MAX_DWELL_MINUTES, no $dwellMinutes" }
            require(latitude in -90.0..90.0) { "latitude debe estar entre -90 y 90, no $latitude" }
            require(longitude in -180.0..180.0) { "longitude debe estar entre -180 y 180, no $longitude" }
            require(radiusMeters in MIN_RADIUS..MAX_RADIUS) { "radiusMeters debe estar entre $MIN_RADIUS y $MAX_RADIUS, no $radiusMeters" }
        }

        companion object {
            /** Android recomienda al menos 100 m: con menos, la ubicación no es lo bastante precisa. */
            const val MIN_RADIUS = 100
            const val MAX_RADIUS = 50_000
            const val MAX_DWELL_MINUTES = 240
        }
    }

    /**
     * Cuando el teléfono se conecta o se desconecta de un aparato Bluetooth (carro, audífonos, reloj).
     * [deviceAddress] vacío = cualquier aparato. [deviceName] es solo para mostrar.
     */
    @Serializable
    @SerialName("bluetooth")
    data class Bluetooth(
        val deviceAddress: String = "",
        val deviceName: String = "",
        val event: ConnectionEvent = ConnectionEvent.CONNECTED,
    ) : Trigger

    /** Cuando el teléfono se conecta o se desconecta de una red Wi-Fi. [ssid] vacío = cualquier red. */
    @Serializable
    @SerialName("wifi")
    data class Wifi(
        val ssid: String = "",
        val event: ConnectionEvent = ConnectionEvent.CONNECTED,
    ) : Trigger

    /**
     * Cuando otra app muestra una notificación. [packageName] vacío = cualquier app ([appName] es solo para
     * mostrar). [textContains] vacío = cualquier notificación; si no, el título o el texto tienen que
     * contenerlo, sin importar mayúsculas. Necesita el permiso "Acceso a notificaciones".
     */
    @Serializable
    @SerialName("notification")
    data class Notification(
        val packageName: String = "",
        val appName: String = "",
        val textContains: String = "",
    ) : Trigger

    /**
     * Al acercar el teléfono a la etiqueta NFC [tagId]. La Vara graba ese código en la etiqueta; [tagName]
     * es solo para mostrar ("Mesa de noche").
     */
    @Serializable
    @SerialName("nfc")
    data class Nfc(
        val tagId: String = "",
        val tagName: String = "",
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
enum class LocationTransition {
    /** Al llegar a la zona. */
    @SerialName("enter") ENTER,

    /** Al irse de la zona. */
    @SerialName("exit") EXIT,
}

@Serializable
enum class ConnectionEvent {
    /** Al conectarse. */
    @SerialName("connected") CONNECTED,

    /** Al desconectarse. */
    @SerialName("disconnected") DISCONNECTED,
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
