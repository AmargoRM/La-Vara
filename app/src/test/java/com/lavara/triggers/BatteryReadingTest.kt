package com.lavara.triggers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryReadingTest {

    @Test
    fun primeraLectura_noDisparaNada() {
        assertNull(BatteryReading.eventFor(previous = null, level = 10))
    }

    @Test
    fun mismoPorcentaje_noDisparaNada() {
        assertNull(BatteryReading.eventFor(previous = 42, level = 42))
    }

    @Test
    fun cambioDePorcentaje_pasaAlMotorConElAnterior() {
        assertEquals(TriggerEvent.BatteryChanged(level = 20, previousLevel = 21), BatteryReading.eventFor(previous = 21, level = 20))
    }
}
