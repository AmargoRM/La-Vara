package com.lavara.triggers

import com.lavara.automation.Automation
import com.lavara.automation.AutomationJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationTriggerTest {

    private val home = Trigger.Location(9.9281, -84.0907, 200, LocationTransition.ENTER, "Casa")

    @Test
    fun json_idaYVuelta_yNombresEstables() {
        val automation = Automation(id = "casa", name = "Llegar a casa", trigger = home)
        val text = AutomationJson.encode(automation)
        assertEquals(automation, AutomationJson.decode(text))
        listOf("\"type\": \"location\"", "\"transition\": \"enter\"", "\"radiusMeters\": 200", "\"placeName\": \"Casa\"")
            .forEach { assertTrue("Falta $it en\n$text", text.contains(it)) }
    }

    @Test
    fun json_conValoresPorDefecto() {
        val a = AutomationJson.decode("""{"id":"a","name":"A","trigger":{"type":"location","latitude":10.0,"longitude":-84.0}}""")
        assertEquals(Trigger.Location(10.0, -84.0, 200, LocationTransition.ENTER, ""), a.trigger)
    }

    @Test
    fun valoresFueraDeRango_seRechazan() {
        assertThrows(IllegalArgumentException::class.java) { Trigger.Location(95.0, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { Trigger.Location(0.0, 0.0, radiusMeters = 50) }
    }

    @Test
    fun coincide_soloConSuAutomatizacionYSuSentido() {
        assertTrue(TriggerMatcher.matches(home, "casa", TriggerEvent.LocationChanged("casa", entered = true, atMillis = 1)))
        assertFalse(TriggerMatcher.matches(home, "casa", TriggerEvent.LocationChanged("casa", entered = false, atMillis = 1)))
        assertFalse(TriggerMatcher.matches(home, "otra", TriggerEvent.LocationChanged("casa", entered = true, atMillis = 1)))
        val leaving = home.copy(transition = LocationTransition.EXIT)
        assertTrue(TriggerMatcher.matches(leaving, "casa", TriggerEvent.LocationChanged("casa", entered = false, atMillis = 1)))
    }
}
