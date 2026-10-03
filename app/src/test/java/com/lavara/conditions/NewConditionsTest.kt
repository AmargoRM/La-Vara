package com.lavara.conditions

import com.lavara.automation.Automation
import com.lavara.automation.AutomationJson
import com.lavara.automation.FakeClock
import com.lavara.automation.FakeDevice
import com.lavara.triggers.Trigger
import com.lavara.triggers.Weekday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewConditionsTest {

    // FakeClock arranca el lunes 5 de octubre de 2026.
    private val clock = FakeClock()
    private val device = FakeDevice()
    private val evaluator = ConditionEvaluator(clock, device)

    @Test
    fun wifi_cualquierRed_yPorNombre() {
        assertFalse(evaluator.evaluate(Condition.WifiConnected()))
        device.wifi = true
        device.ssid = "Casa"
        assertTrue(evaluator.evaluate(Condition.WifiConnected()))
        assertTrue(evaluator.evaluate(Condition.WifiConnected(" casa ")))
        assertFalse(evaluator.evaluate(Condition.WifiConnected("Oficina")))
        // Conectado pero sin saber el nombre: solo vale "cualquier red".
        device.ssid = null
        assertTrue(evaluator.evaluate(Condition.WifiConnected()))
        assertFalse(evaluator.evaluate(Condition.WifiConnected("Casa")))
        device.wifi = null
        assertFalse(evaluator.evaluate(Condition.WifiConnected()))
    }

    @Test
    fun cargando() {
        device.charging = true
        assertTrue(evaluator.evaluate(Condition.Charging()))
        assertFalse(evaluator.evaluate(Condition.Charging(charging = false)))
        device.charging = false
        assertTrue(evaluator.evaluate(Condition.Charging(charging = false)))
        device.charging = null
        assertFalse(evaluator.evaluate(Condition.Charging()))
        assertFalse(evaluator.evaluate(Condition.Charging(charging = false)))
    }

    @Test
    fun dias() {
        assertTrue(evaluator.evaluate(Condition.DaysOfWeek(listOf(Weekday.MONDAY, Weekday.FRIDAY))))
        assertFalse(evaluator.evaluate(Condition.DaysOfWeek(listOf(Weekday.SATURDAY, Weekday.SUNDAY))))
        assertTrue(evaluator.evaluate(Condition.DaysOfWeek()))
        clock.advanceSeconds(5 * 86_400)
        assertTrue(evaluator.evaluate(Condition.DaysOfWeek(listOf(Weekday.SATURDAY))))
    }

    @Test
    fun explicaPorQueNoSeCumple() {
        device.wifi = true
        device.ssid = "Casa"
        val check = evaluator.check(listOf(Condition.WifiConnected("Oficina")))
        assertFalse(check.passed)
        assertTrue(check.reason, check.reason.contains("Oficina") && check.reason.contains("ahora: Casa"))
    }

    @Test
    fun json_nombresEstables_yValoresPorDefecto() {
        val a = Automation(
            id = "a", name = "A", trigger = Trigger.Manual,
            conditions = listOf(Condition.WifiConnected("Casa"), Condition.Charging(false), Condition.DaysOfWeek(listOf(Weekday.SUNDAY))),
        )
        val text = AutomationJson.encode(a)
        listOf("\"type\": \"wifi_connected\"", "\"type\": \"charging\"", "\"charging\": false", "\"type\": \"days_of_week\"", "\"sunday\"")
            .forEach { assertTrue("Falta $it en\n$text", text.contains(it)) }
        assertEquals(a, AutomationJson.decode(text))
        val defaults = AutomationJson.decode(
            """{"id":"a","name":"A","trigger":{"type":"manual"},"conditions":[{"type":"wifi_connected"},{"type":"charging"},{"type":"days_of_week"}]}""",
        )
        assertEquals(listOf(Condition.WifiConnected(), Condition.Charging(), Condition.DaysOfWeek()), defaults.conditions)
    }
}
