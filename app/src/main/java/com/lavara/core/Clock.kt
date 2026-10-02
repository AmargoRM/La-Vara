package com.lavara.core

import java.time.ZonedDateTime

/** Reloj inyectable: el motor pregunta la hora a esta interfaz para poder probarlo con horas fijas. */
fun interface Clock {
    fun now(): ZonedDateTime
}

/** Reloj real del teléfono, con su zona horaria. */
object DeviceClock : Clock {
    override fun now(): ZonedDateTime = ZonedDateTime.now()
}
