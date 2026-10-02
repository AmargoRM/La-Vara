package com.lavara.triggers

/**
 * Android avisa muchas veces por minuto cosas de la batería (temperatura, voltaje) aunque el porcentaje
 * no cambie. Esto decide cuándo vale la pena pasarle un evento al motor. Lógica pura, sin Android.
 */
object BatteryReading {
    /**
     * Evento para el motor, o null si no hay que hacer nada: el porcentaje no cambió, o es la primera
     * lectura y todavía no hay con qué comparar (así no se dispara "batería baja" solo por arrancar).
     */
    fun eventFor(previous: Int?, level: Int): TriggerEvent.BatteryChanged? =
        if (previous == null || previous == level) null else TriggerEvent.BatteryChanged(level, previous)
}
