package com.lavara.automation

/**
 * Programa que una automatización siga más tarde, desde la acción [fromAction], a la hora [atMillis]
 * (milisegundos desde 1970). La parte Android lo hace con una alarma; el motor solo conoce esta interfaz.
 */
fun interface LaterScheduler {
    suspend fun schedule(automationId: String, fromAction: Int, atMillis: Long)
}
