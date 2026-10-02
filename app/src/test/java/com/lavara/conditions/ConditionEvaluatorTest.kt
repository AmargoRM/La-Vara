package com.lavara.conditions

import com.lavara.automation.FakeClock
import com.lavara.automation.FakeDevice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ConditionEvaluatorTest {

    private val clock = FakeClock()
    private val device = FakeDevice(battery = 50)
    private val evaluator = ConditionEvaluator(clock, device)

    private fun at(hour: Int, minute: Int) {
        clock.current = ZonedDateTime.of(2026, 10, 5, hour, minute, 0, 0, ZoneId.of("America/Costa_Rica"))
    }

    @Test
    fun bateria_comparaciones() {
        assertTrue(evaluator.evaluate(Condition.BatteryLevel(Comparison.GREATER_THAN, 20)))
        assertFalse(evaluator.evaluate(Condition.BatteryLevel(Comparison.GREATER_THAN, 50)))
        assertTrue(evaluator.evaluate(Condition.BatteryLevel(Comparison.GREATER_OR_EQUAL, 50)))
        assertTrue(evaluator.evaluate(Condition.BatteryLevel(Comparison.LESS_THAN, 51)))
        assertTrue(evaluator.evaluate(Condition.BatteryLevel(Comparison.LESS_OR_EQUAL, 50)))
        assertTrue(evaluator.evaluate(Condition.BatteryLevel(Comparison.EQUAL, 50)))
    }

    @Test
    fun bateriaDesconocida_noSeCumple() {
        device.battery = null
        assertFalse(evaluator.evaluate(Condition.BatteryLevel(Comparison.LESS_THAN, 100)))
    }

    @Test
    fun horaEntre_mismoDia() {
        val rango = Condition.TimeBetween("08:00", "12:00")
        at(8, 0); assertTrue(evaluator.evaluate(rango))
        at(11, 59); assertTrue(evaluator.evaluate(rango))
        at(12, 0); assertFalse(evaluator.evaluate(rango))
        at(7, 59); assertFalse(evaluator.evaluate(rango))
    }

    @Test
    fun horaEntre_cruzaMedianoche() {
        val noche = Condition.TimeBetween("22:00", "06:00")
        at(23, 30); assertTrue(evaluator.evaluate(noche))
        at(2, 0); assertTrue(evaluator.evaluate(noche))
        at(6, 0); assertFalse(evaluator.evaluate(noche))
        at(12, 0); assertFalse(evaluator.evaluate(noche))
    }

    @Test
    fun condicionesAnidadas() {
        at(23, 0)
        val bateriaBaja = Condition.BatteryLevel(Comparison.LESS_THAN, 20)
        val noche = Condition.TimeBetween("22:00", "06:00")
        // (batería < 20 O noche) Y NO (batería = 100)
        val compuesta = Condition.And(
            listOf(
                Condition.Or(listOf(bateriaBaja, noche)),
                Condition.Not(Condition.BatteryLevel(Comparison.EQUAL, 100)),
            ),
        )
        assertTrue(evaluator.evaluate(compuesta))
        at(12, 0)
        assertFalse(evaluator.evaluate(compuesta))
        device.battery = 10
        assertTrue(evaluator.evaluate(compuesta))
    }

    @Test
    fun listasVacias() {
        assertTrue(evaluator.evaluate(Condition.And(emptyList())))
        assertFalse(evaluator.evaluate(Condition.Or(emptyList())))
        assertTrue(evaluator.check(emptyList()).passed)
    }
}
