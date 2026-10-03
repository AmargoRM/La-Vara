package com.lavara.triggers

import com.lavara.automation.Automation
import com.lavara.automation.AutomationJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionTriggerTest {

    @Test
    fun json_idaYVuelta_yNombresEstables() {
        val bt = Automation(id = "b", name = "Carro", trigger = Trigger.Bluetooth("00:11:22:AA:BB:CC", "Carro", ConnectionEvent.DISCONNECTED))
        val wifi = Automation(id = "w", name = "Casa", trigger = Trigger.Wifi("Casa", ConnectionEvent.CONNECTED))
        val btText = AutomationJson.encode(bt)
        val wifiText = AutomationJson.encode(wifi)
        listOf("\"type\": \"bluetooth\"", "\"event\": \"disconnected\"", "\"deviceAddress\": \"00:11:22:AA:BB:CC\"")
            .forEach { assertTrue("Falta $it en\n$btText", btText.contains(it)) }
        listOf("\"type\": \"wifi\"", "\"ssid\": \"Casa\"", "\"event\": \"connected\"")
            .forEach { assertTrue("Falta $it en\n$wifiText", wifiText.contains(it)) }
        assertEquals(bt, AutomationJson.decode(btText))
        assertEquals(wifi, AutomationJson.decode(wifiText))
    }

    @Test
    fun json_valoresPorDefecto() {
        assertEquals(Trigger.Bluetooth(), AutomationJson.decode("""{"id":"a","name":"A","trigger":{"type":"bluetooth"}}""").trigger)
        assertEquals(Trigger.Wifi(), AutomationJson.decode("""{"id":"a","name":"A","trigger":{"type":"wifi"}}""").trigger)
    }

    @Test
    fun bluetooth_aparatoConcretoOCualquiera() {
        val carro = Trigger.Bluetooth("00:11:22:AA:BB:CC", "Carro")
        val on = TriggerEvent.BluetoothChanged("00:11:22:aa:bb:cc", "Carro", true, 1)
        val other = TriggerEvent.BluetoothChanged("99:99:99:99:99:99", "Reloj", true, 1)
        val off = TriggerEvent.BluetoothChanged("00:11:22:AA:BB:CC", "Carro", false, 1)
        assertTrue(TriggerMatcher.matches(carro, "a", on))
        assertFalse(TriggerMatcher.matches(carro, "a", other))
        assertFalse(TriggerMatcher.matches(carro, "a", off))
        assertTrue(TriggerMatcher.matches(Trigger.Bluetooth(), "a", other))
        assertTrue(TriggerMatcher.matches(carro.copy(event = ConnectionEvent.DISCONNECTED), "a", off))
    }

    @Test
    fun wifi_redConcretaOCualquiera() {
        val casa = Trigger.Wifi("Casa")
        assertTrue(TriggerMatcher.matches(casa, "a", TriggerEvent.WifiChanged("casa", true, 1)))
        assertFalse(TriggerMatcher.matches(casa, "a", TriggerEvent.WifiChanged("Oficina", true, 1)))
        assertFalse(TriggerMatcher.matches(casa, "a", TriggerEvent.WifiChanged(null, true, 1)))
        assertTrue(TriggerMatcher.matches(Trigger.Wifi(), "a", TriggerEvent.WifiChanged(null, true, 1)))
        assertFalse(TriggerMatcher.matches(Trigger.Wifi(), "a", TriggerEvent.WifiChanged("Casa", false, 1)))
        assertTrue(TriggerMatcher.matches(Trigger.Wifi(event = ConnectionEvent.DISCONNECTED), "a", TriggerEvent.WifiChanged("Casa", false, 1)))
    }
}
