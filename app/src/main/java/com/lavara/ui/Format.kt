package com.lavara.ui

import android.content.Context
import android.text.format.DateFormat
import com.lavara.actions.Action
import com.lavara.automation.Automation
import com.lavara.conditions.Comparison
import com.lavara.conditions.Condition
import com.lavara.core.TimeText
import com.lavara.triggers.BatteryDirection
import com.lavara.triggers.ConnectionEvent
import com.lavara.triggers.LocationTransition
import com.lavara.triggers.PowerEvent
import com.lavara.triggers.Trigger
import com.lavara.triggers.Weekday
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

/** Textos para mostrar automatizaciones y horas al usuario, con el formato de hora del teléfono. */

/** Hora como la muestra el reloj del teléfono: "4:30 p. m." o "16:30". */
fun timeText(context: Context, time: LocalTime): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, time.hour)
        set(Calendar.MINUTE, time.minute)
    }
    return DateFormat.getTimeFormat(context).format(calendar.time)
}

fun timeText(context: Context, text: String): String = TimeText.parseOrNull(text)?.let { timeText(context, it) } ?: text

/** "hoy a las 4:30 p. m.", "mañana a las 3:48 a. m." o "05/10 a las 8:00 a. m.". */
fun whenText(context: Context, at: ZonedDateTime, now: ZonedDateTime): String {
    val day = when (at.toLocalDate()) {
        now.toLocalDate() -> "hoy"
        now.toLocalDate().plusDays(1) -> "mañana"
        else -> at.format(DateTimeFormatter.ofPattern("dd/MM"))
    }
    return "$day a las ${timeText(context, at.toLocalTime())}"
}

/** "3 minutos", "2 horas y 5 minutos". */
fun untilText(now: ZonedDateTime, at: ZonedDateTime): String {
    val minutes = Duration.between(now, at).toMinutes().coerceAtLeast(1)
    val h = minutes / 60
    val m = minutes % 60
    val mText = if (m == 1L) "1 minuto" else "$m minutos"
    val hText = if (h == 1L) "1 hora" else "$h horas"
    return when {
        h == 0L -> mText
        m == 0L -> hText
        else -> "$hText y $mText"
    }
}

fun dayName(day: Weekday) = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")[day.isoNumber - 1]

fun daysText(days: List<Weekday>): String = when {
    days.isEmpty() || days.size == 7 -> "todos los días"
    days.toSet() == Weekday.entries.take(5).toSet() -> "de lunes a viernes"
    days.toSet() == setOf(Weekday.SATURDAY, Weekday.SUNDAY) -> "sábados y domingos"
    else -> "los " + days.sortedBy { it.isoNumber }.joinToString(", ") { dayName(it) }
}

fun describe(context: Context, trigger: Trigger): String = when (trigger) {
    is Trigger.Time -> "${daysText(trigger.days).replaceFirstChar { it.uppercase() }} a las ${timeText(context, trigger.time)}"
    is Trigger.Battery -> "Cuando la batería ${if (trigger.direction == BatteryDirection.BELOW) "baja a" else "sube a"} ${trigger.threshold} %"
    is Trigger.Location -> (if (trigger.transition == LocationTransition.ENTER) "Al llegar a " else "Al irse de ") +
        trigger.placeName.ifBlank { "la zona marcada" } + " (${trigger.radiusMeters} m)"
    is Trigger.Power -> if (trigger.event == PowerEvent.CONNECTED) "Al conectar el cargador" else "Al desconectar el cargador"
    is Trigger.Bluetooth -> (if (trigger.event == ConnectionEvent.CONNECTED) "Al conectar el Bluetooth " else "Al desconectar el Bluetooth ") +
        (trigger.deviceName.ifBlank { trigger.deviceAddress }.ifBlank { "de cualquier aparato" })
    is Trigger.Wifi -> (if (trigger.event == ConnectionEvent.CONNECTED) "Al conectarse al Wi-Fi " else "Al desconectarse del Wi-Fi ") +
        trigger.ssid.ifBlank { "(cualquier red)" }
    Trigger.Manual -> "Solo al tocarla"
}

fun describe(context: Context, condition: Condition): String = when (condition) {
    is Condition.BatteryLevel -> "la batería está ${comparisonText(condition.comparison)} ${condition.value} %"
    is Condition.TimeBetween -> "es entre ${timeText(context, condition.start)} y ${timeText(context, condition.end)}"
    is Condition.And -> condition.conditions.joinToString(" y ", "(", ")") { describe(context, it) }
    is Condition.Or -> condition.conditions.joinToString(" o ", "(", ")") { describe(context, it) }
    is Condition.Not -> "no ${describe(context, condition.condition)}"
}

fun comparisonText(comparison: Comparison) = when (comparison) {
    Comparison.GREATER_THAN -> "sobre"
    Comparison.GREATER_OR_EQUAL -> "en o sobre"
    Comparison.LESS_THAN -> "bajo"
    Comparison.LESS_OR_EQUAL -> "en o bajo"
    Comparison.EQUAL -> "en"
}

fun actionTitle(action: Action) = when (action) {
    is Action.ShowNotification -> "Mostrar notificación"
    is Action.OpenApp -> "Abrir app"
    is Action.OpenUrl -> "Abrir enlace"
    is Action.Delay -> "Esperar"
    is Action.RunAutomation -> "Ejecutar otra automatización"
}

/** Resumen de una línea: "Todos los días a las 8:00 a. m. · si la batería está sobre 20 % · mostrar notificación". */
fun summary(context: Context, automation: Automation): String = buildList {
    add(describe(context, automation.trigger))
    if (automation.conditions.isNotEmpty()) add("si " + automation.conditions.joinToString(" y ") { describe(context, it) })
    if (automation.actions.isEmpty()) add("sin acciones") else add(automation.actions.joinToString(", ") { actionTitle(it).lowercase() })
}.joinToString(" · ")
