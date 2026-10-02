package com.lavara.automation

import com.lavara.actions.Action
import com.lavara.triggers.TriggerEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TemplatesTest {

    @Test
    fun pruebaVara_nacedesactivadaYSeLeeIgualDesdeJson() {
        val prueba = Templates.pruebaVara(123)
        assertFalse(prueba.enabled)
        assertEquals(prueba, AutomationJson.decode(AutomationJson.encode(prueba)))
    }

    @Test
    fun pruebaVara_activada_muestraLaNotificacionALas8() = runBlocking {
        val clock = FakeClock()
        val executor = RecordingExecutor()
        val engine = AutomationEngine(ListSource(listOf(Templates.pruebaVara(0).copy(enabled = true))), executor, clock, FakeDevice(64))

        val result = engine.handle(TriggerEvent.TimeReached(clock.now())).single()

        assertEquals(ExecutionStatus.EXECUTED, result.status)
        assertEquals(
            Action.ShowNotification("Prueba Vara", "Funciona: batería 64 % a las 08:00 del 05/10/2026."),
            executor.done.single(),
        )
    }
}
