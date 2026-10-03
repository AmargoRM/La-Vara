package com.lavara.conditions

import com.lavara.core.Clock
import com.lavara.core.DeviceState
import com.lavara.core.TimeText
import com.lavara.triggers.Weekday

/** Resultado de evaluar condiciones: si se cumplen y, si no, cuál falló, en palabras para el log. */
data class ConditionCheck(val passed: Boolean, val reason: String)

class ConditionEvaluator(
    private val clock: Clock,
    private val deviceState: DeviceState,
) {
    /** Lista de condiciones de una automatización: se tienen que cumplir todas. */
    fun check(conditions: List<Condition>): ConditionCheck {
        for (condition in conditions) {
            if (!evaluate(condition)) return ConditionCheck(false, "No se cumple: ${describe(condition)}")
        }
        return ConditionCheck(true, if (conditions.isEmpty()) "Sin condiciones" else "Se cumplen todas las condiciones")
    }

    fun evaluate(condition: Condition): Boolean = when (condition) {
        is Condition.BatteryLevel -> {
            // Si no se puede leer la batería, la condición no se da por cumplida.
            val level = deviceState.batteryLevel()
            level != null && condition.comparison.test(level, condition.value)
        }
        is Condition.TimeBetween -> {
            val now = clock.now().toLocalTime()
            val start = TimeText.parseOrNull(condition.start)!!
            val end = TimeText.parseOrNull(condition.end)!!
            if (start <= end) {
                now >= start && now < end
            } else {
                now >= start || now < end
            }
        }
        // Si no se puede saber el estado, la condición no se da por cumplida.
        is Condition.WifiConnected -> deviceState.isWifiConnected() == true &&
            (condition.ssid.isBlank() || condition.ssid.trim().equals(deviceState.wifiSsid()?.trim(), ignoreCase = true))
        is Condition.Charging -> deviceState.isCharging()?.let { it == condition.charging } ?: false
        is Condition.DaysOfWeek -> condition.days.isEmpty() || Weekday.of(clock.now().dayOfWeek) in condition.days
        is Condition.And -> condition.conditions.all { evaluate(it) }
        is Condition.Or -> condition.conditions.any { evaluate(it) }
        is Condition.Not -> !evaluate(condition.condition)
    }

    fun describe(condition: Condition): String = when (condition) {
        is Condition.BatteryLevel ->
            "batería ${condition.comparison.symbol} ${condition.value} % (ahora: ${deviceState.batteryLevel()?.let { "$it %" } ?: "desconocida"})"
        is Condition.TimeBetween -> "hora entre ${condition.start} y ${condition.end}"
        is Condition.WifiConnected -> (if (condition.ssid.isBlank()) "conectado a un Wi-Fi" else "conectado al Wi-Fi ${condition.ssid}") +
            " (ahora: ${wifiNow()})"
        is Condition.Charging -> (if (condition.charging) "cargando" else "sin cargador") +
            " (ahora: ${when (deviceState.isCharging()) { true -> "cargando"; false -> "sin cargador"; null -> "desconocido" }})"
        is Condition.DaysOfWeek -> "día: " + condition.days.sortedBy { it.isoNumber }.joinToString(", ") { DAY_NAMES[it.isoNumber - 1] }
        is Condition.And -> condition.conditions.joinToString(" Y ", "(", ")") { describe(it) }
        is Condition.Or -> condition.conditions.joinToString(" O ", "(", ")") { describe(it) }
        is Condition.Not -> "NO ${describe(condition.condition)}"
    }

    private fun wifiNow(): String = when (deviceState.isWifiConnected()) {
        true -> deviceState.wifiSsid() ?: "Wi-Fi sin nombre conocido"
        false -> "sin Wi-Fi"
        null -> "desconocido"
    }

    private companion object {
        val DAY_NAMES = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
    }
}
