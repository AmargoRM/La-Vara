package com.lavara.automation

import com.lavara.actions.Action
import com.lavara.triggers.PowerEvent
import com.lavara.triggers.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoNameTest {

    @Test
    fun nombre_diceCuandoYQue() {
        assertEquals(
            "Al conectar el cargador → Encender linterna y 1 más",
            AutoName.of(Trigger.Power(PowerEvent.CONNECTED), listOf(Action.Flashlight(true), Action.Vibrate())),
        )
        assertEquals("A las 07:30 → Vibrar", AutoName.of(Trigger.Time("07:30"), listOf(Action.Vibrate())))
    }

    @Test
    fun nombreLargo_seCorta() {
        val name = AutoName.of(Trigger.Manual, listOf(Action.ShowNotification("x".repeat(200), "")))
        assertTrue(name.length <= 60)
    }

    @Test
    fun sinNombre_alGuardarUsaElAutomatico_yConNombreLoRespeta() {
        val draft = AutomationDraft(name = "   ", trigger = Trigger.Time("07:30"), actions = listOf(Action.Vibrate()))
        assertEquals("A las 07:30 → Vibrar", draft.toAutomation("id", 0).name)
        assertEquals("Despertar", draft.copy(name = " Despertar ").toAutomation("id", 0).name)
    }
}
