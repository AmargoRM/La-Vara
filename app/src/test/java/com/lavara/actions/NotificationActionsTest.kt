package com.lavara.actions

import com.lavara.actions.NotificationPick.Button
import com.lavara.actions.NotificationPick.Choice
import com.lavara.actions.NotificationPick.Shown
import com.lavara.automation.Automation
import com.lavara.automation.AutomationDraft
import com.lavara.automation.AutomationJson
import com.lavara.triggers.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationActionsTest {

    private val esteban = Shown("Esteban DA", 100, listOf(Button("Responder", canType = true), Button("Marcar como leído", canType = false)))
    private val grupo = Shown("Familia", 200, listOf(Button("Responder", canType = true)))
    private val sinBotones = Shown("WhatsApp", 300, emptyList())

    @Test
    fun responder_laMasNuevaConCampo_oLaDeEseContacto() {
        val shown = listOf(esteban, grupo, sinBotones)
        assertEquals(Choice(1, 0), NotificationPick.reply(shown, ""))
        assertEquals(Choice(0, 0), NotificationPick.reply(shown, "esteban"))
        assertNull(NotificationPick.reply(shown, "Ana"))
        assertNull(NotificationPick.reply(listOf(sinBotones), ""))
    }

    @Test
    fun boton_porNombre() {
        val shown = listOf(esteban, grupo)
        assertEquals(Choice(0, 1), NotificationPick.button(shown, "", "marcar como leído"))
        assertEquals(Choice(0, 1), NotificationPick.button(shown, "", "leído"))
        assertNull(NotificationPick.button(shown, "Familia", "Marcar como leído"))
    }

    @Test
    fun json_nombresEstables_yNoEsperanDesbloqueo() {
        val actions = listOf(
            Action.ReplyToNotification("com.whatsapp", "Voy manejando", "Esteban"),
            Action.TapNotificationButton("com.whatsapp", "Marcar como leído"),
        )
        val automation = Automation(id = "a", name = "A", trigger = Trigger.Manual, actions = actions)
        val text = AutomationJson.encode(automation)
        assertEquals(automation, AutomationJson.decode(text))
        assertTrue(text.contains("\"reply_notification\"") && text.contains("\"tap_notification_button\""))
        actions.forEach {
            assertFalse(it.needsUnlock())
            assertTrue(it.needsNotificationAccess())
        }
    }

    @Test
    fun editor_pideAppYTexto() {
        fun problems(action: Action) = AutomationDraft(name = "X", actions = listOf(action)).problems()
        assertTrue(problems(Action.ReplyToNotification(text = "Hola")).single().contains("app"))
        assertTrue(problems(Action.ReplyToNotification("com.whatsapp")).single().contains("texto"))
        assertTrue(problems(Action.TapNotificationButton("com.whatsapp")).single().contains("botón"))
        assertEquals(emptyList<String>(), problems(Action.ReplyToNotification("com.whatsapp", "Hola")))
    }
}
