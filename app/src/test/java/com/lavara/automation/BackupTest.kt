package com.lavara.automation

import com.lavara.actions.Action
import com.lavara.triggers.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupTest {

    private val a = Automation(id = "a", name = "Alarma", trigger = Trigger.Time("07:00"), actions = listOf(Action.Flashlight(true)))
    private val b = Automation(id = "b", name = "Carro", trigger = Trigger.Manual, enabled = false)

    @Test
    fun idaYVuelta() {
        val text = Backup.encode(listOf(a, b), now = 123)
        assertTrue(text.contains("\"format\": \"la-vara-respaldo\""))
        val read = Backup.decode(text)
        assertEquals(listOf(a, b), read.automations)
        assertEquals(0, read.unreadable)
    }

    @Test
    fun aceptaUnaSolaOUnaLista() {
        assertEquals(listOf(a), Backup.decode(AutomationJson.encode(a)).automations)
        assertEquals(listOf(a, b), Backup.decode("[" + AutomationJson.encode(a) + "," + AutomationJson.encode(b) + "]").automations)
    }

    @Test
    fun unaDaniada_noImpideLasDemas() {
        val text = """{"automations":[${AutomationJson.encode(a)},{"id":"x","name":"X","trigger":{"type":"tipo_inventado"}}]}"""
        val read = Backup.decode(text)
        assertEquals(listOf(a), read.automations)
        assertEquals(1, read.unreadable)
    }

    @Test
    fun archivoQueNoEsRespaldo_explica() {
        listOf("hola", "{\"otra\":1}").forEach {
            try {
                Backup.decode(it)
                fail("Debía fallar con $it")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("respaldo"))
            }
        }
    }

    @Test
    fun importar_nuncaPisaNada() {
        val changed = a.copy(name = "Alarma nueva")
        var n = 0
        val plan = Backup.plan(existing = listOf(a), imported = listOf(a, changed, b), now = 9) { "nuevo-${++n}" }
        assertEquals(1, plan.alreadyThere)
        assertEquals(1, plan.copies)
        assertEquals(listOf("nuevo-1", "b"), plan.toSave.map { it.id })
        assertEquals("Alarma nueva (importada)", plan.toSave[0].name)
        assertEquals(false, plan.toSave[0].enabled)
    }

    @Test
    fun importar_contadoresNoCuentanComoCambio() {
        val used = a.copy(executionCount = 40, lastExecutedAt = 5)
        val plan = Backup.plan(existing = listOf(used), imported = listOf(a), now = 9) { "x" }
        assertEquals(1, plan.alreadyThere)
        assertTrue(plan.toSave.isEmpty())
    }
}
