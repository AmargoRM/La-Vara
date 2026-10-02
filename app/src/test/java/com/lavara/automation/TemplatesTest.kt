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

    @Test
    fun ejemplosDeBateriaYCargador_nacenDesactivadosYResponden() = runBlocking {
        val baja = Templates.bateriaBaja(1)
        val cargador = Templates.cargadorConectado(1)
        for (a in listOf(baja, cargador)) {
            assertFalse(a.enabled)
            assertEquals(a, AutomationJson.decode(AutomationJson.encode(a)))
        }
        val executor = RecordingExecutor()
        val engine = AutomationEngine(
            ListSource(listOf(baja.copy(enabled = true), cargador.copy(enabled = true))), executor, FakeClock(), FakeDevice(20),
        )

        assertEquals(listOf(baja.id), engine.handle(TriggerEvent.BatteryChanged(level = 20, previousLevel = 21)).map { it.automationId })
        assertEquals(listOf(cargador.id), engine.handle(TriggerEvent.PowerChanged(connected = true, atMillis = 5)).map { it.automationId })
        assertEquals(Action.ShowNotification("Batería baja", "Queda 20 % (a las 08:00)."), executor.done.first())
    }

    @Test
    fun gruposDeEjemplos_tienenClavesDistintas() {
        val keys = Templates.groups().map { it.first }
        assertEquals(keys.distinct(), keys)
        // La clave del primer grupo es la que ya existía: quien borró "Prueba Vara" no la vuelve a ver.
        assertEquals("plantillas_creadas", keys.first())
    }
}
