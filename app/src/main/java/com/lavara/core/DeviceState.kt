package com.lavara.core

/** Estado del teléfono que el motor puede consultar sin depender de Android. */
interface DeviceState {
    /** Nivel de batería de 0 a 100, o null si no se pudo leer. */
    fun batteryLevel(): Int?

    /** true si la pantalla está apagada o con el bloqueo puesto: no se puede abrir nada a la vista. */
    fun isLocked(): Boolean = false
}
