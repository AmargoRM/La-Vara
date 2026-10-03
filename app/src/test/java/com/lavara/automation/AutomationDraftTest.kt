package com.lavara.automation

import com.lavara.actions.Action
import com.lavara.conditions.Comparison
import com.lavara.conditions.Condition
import com.lavara.triggers.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationDraftTest {

    private val battery = Condition.BatteryLevel(Comparison.GREATER_THAN, 20)
    private val night = Condition.TimeBetween("22:00", "06:00")
    private val notify = Action.ShowNotification("Hola")

    @Test
    fun nueva_seGuardaConIdNuevoYFechas() {
        val saved = AutomationDraft.new().copy(name = "  Mañana  ").toAutomation("nueva-1", 1_000)
        assertEquals("nueva-1", saved.id)
        assertEquals("Mañana", saved.name)
        assertEquals(1_000, saved.createdAt)
        assertEquals(1_000, saved.updatedAt)
        assertTrue(saved.enabled)
        assertEquals(Trigger.Time("08:00"), saved.trigger)
    }

    @Test
    fun editar_conservaIdContadoresYEstado() {
        val original = Automation(
            id = "a", name = "A", enabled = false, trigger = Trigger.Manual, actions = listOf(notify),
            createdAt = 5, executionCount = 7, failureCount = 2, lastExecutedAt = 9, priority = 3,
        )
        val saved = AutomationDraft.from(original).copy(name = "B").toAutomation("ignorado", 100)
        assertEquals("a", saved.id)
        assertEquals("B", saved.name)
        assertFalse(saved.enabled)
        assertEquals(5, saved.createdAt)
        assertEquals(100, saved.updatedAt)
        assertEquals(7, saved.executionCount)
        assertEquals(2, saved.failureCount)
        assertEquals(9L, saved.lastExecutedAt)
        assertEquals(3, saved.priority)
    }

    @Test
    fun alguna_seGuardaComoOrYSeLeeIgual() {
        val draft = AutomationDraft.new().copy(name = "X", matchAll = false, conditions = listOf(battery, night))
        val saved = draft.toAutomation("x", 0)
        assertEquals(listOf(Condition.Or(listOf(battery, night))), saved.conditions)

        val back = AutomationDraft.from(saved)
        assertFalse(back.matchAll)
        assertEquals(listOf(battery, night), back.conditions)
    }

    @Test
    fun todas_seGuardaComoLista() {
        val saved = AutomationDraft.new().copy(name = "X", conditions = listOf(battery, night)).toAutomation("x", 0)
        assertEquals(listOf(battery, night), saved.conditions)
        assertTrue(AutomationDraft.from(saved).matchAll)
    }

    @Test
    fun alguna_sinCondiciones_quedaVacia() {
        val saved = AutomationDraft.new().copy(name = "X", matchAll = false).toAutomation("x", 0)
        assertEquals(emptyList<Condition>(), saved.conditions)
    }

    @Test
    fun problemas() {
        assertEquals(listOf("Falta el nombre de la automatización (arriba).", "Falta al menos una acción en el paso 3."), AutomationDraft().problems())
        val blankTitle = AutomationDraft(name = "X", actions = listOf(Action.ShowNotification(" ")))
        assertEquals(listOf("La acción 1 necesita un título."), blankTitle.problems())
        val noTarget = AutomationDraft(name = "X", actions = listOf(Action.RunAutomation("")))
        assertEquals(1, noTarget.problems().size)
        val self = AutomationDraft.from(Automation(id = "a", name = "A", trigger = Trigger.Manual, actions = listOf(Action.RunAutomation("a"))))
        assertEquals(listOf("La acción 1 se ejecuta a sí misma."), self.problems())
        val longDelay = AutomationDraft(name = "X", actions = listOf(Action.Delay(24 * 3600 + 1)))
        assertEquals(1, longDelay.problems().size)
        assertEquals(emptyList<String>(), AutomationDraft(name = "X", actions = listOf(Action.Delay(10))).problems())
        assertEquals(1, AutomationDraft(name = "X", actions = listOf(Action.OpenUrl("https://"))).problems().size)
        assertEquals(emptyList<String>(), AutomationDraft(name = "X", actions = listOf(Action.OpenUrl("https://waze.com/ul?q=x"))).problems())
        assertEquals(emptyList<String>(), AutomationDraft.new().copy(name = "X").problems())
    }

    @Test
    fun moverAcciones() {
        val a = Action.ShowNotification("a")
        val b = Action.Delay(5)
        val c = Action.OpenApp("com.x")
        val draft = AutomationDraft(actions = listOf(a, b, c))
        assertEquals(listOf(b, a, c), draft.moveAction(0, 1).actions)
        assertEquals(listOf(a, c, b), draft.moveAction(2, 1).actions)
        assertEquals(draft, draft.moveAction(2, 3))
    }

    @Test
    fun duplicar() {
        val original = Automation(id = "a", name = "A", trigger = Trigger.Manual, executionCount = 4, lastExecutedAt = 3, createdAt = 1)
        val copy = AutomationDraft.duplicate(original, "b", 50)
        assertEquals("b", copy.id)
        assertEquals("A (copia)", copy.name)
        assertFalse(copy.enabled)
        assertEquals(0, copy.executionCount)
        assertNull(copy.lastExecutedAt)
        assertEquals(50, copy.createdAt)
    }

    @Test
    fun quienLaUsa() {
        val target = Automation(id = "t", name = "T", trigger = Trigger.Manual)
        val user = Automation(id = "u", name = "U", trigger = Trigger.Manual, actions = listOf(Action.RunAutomation("t")))
        val other = Automation(id = "o", name = "O", trigger = Trigger.Manual, actions = listOf(notify))
        assertEquals(listOf("U"), AutomationDraft.usersOf("t", listOf(target, user, other)))
    }
}
