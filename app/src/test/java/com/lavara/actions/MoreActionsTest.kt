package com.lavara.actions

import com.lavara.automation.Automation
import com.lavara.automation.AutomationJson
import com.lavara.triggers.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MoreActionsTest {

    @Test
    fun json_nombresEstables_eIdaYVuelta() {
        val actions = listOf(
            Action.Vibrate(800),
            Action.CopyToClipboard("Hola %time"),
            Action.ShareText("Llegué"),
            Action.MediaControl(MediaCommand.NEXT),
        )
        val automation = Automation(id = "a", name = "A", trigger = Trigger.Manual, actions = actions)
        val text = AutomationJson.encode(automation)
        assertEquals(automation, AutomationJson.decode(text))
        listOf("\"vibrate\"", "\"copy_to_clipboard\"", "\"share_text\"", "\"media_control\"", "\"next\"")
            .forEach { assertTrue("Falta $it en\n$text", text.contains(it)) }
    }

    @Test
    fun valoresPorDefecto() {
        val a = AutomationJson.decode(
            """{"id":"a","name":"A","trigger":{"type":"manual"},"actions":[{"type":"vibrate"},{"type":"media_control"}]}""",
        )
        assertEquals(listOf(Action.Vibrate(500), Action.MediaControl(MediaCommand.PLAY_PAUSE)), a.actions)
    }

    @Test
    fun musica_conAppElegida_idaYVuelta() {
        val automation = Automation(
            id = "a", name = "A", trigger = Trigger.Manual,
            actions = listOf(Action.MediaControl(MediaCommand.PLAY, "com.spotify.music")),
        )
        val text = AutomationJson.encode(automation)
        assertEquals(automation, AutomationJson.decode(text))
        assertTrue(text, text.contains("\"packageName\""))
        // Las guardadas antes no tienen app: siguen funcionando como los botones de los audífonos.
        val vieja = AutomationJson.decode(
            """{"id":"a","name":"A","trigger":{"type":"manual"},"actions":[{"type":"media_control","command":"play"}]}""",
        )
        assertEquals(listOf(Action.MediaControl(MediaCommand.PLAY, "")), vieja.actions)
    }

    @Test
    fun vibracionFueraDeRango_seRechaza() {
        assertThrows(IllegalArgumentException::class.java) { Action.Vibrate(0) }
        assertThrows(IllegalArgumentException::class.java) { Action.Vibrate(20_000) }
    }

    @Test
    fun compartir_abrePantalla_yElSiHeredaLoDeAdentro() {
        assertTrue(Action.ShareText("x").opensScreen())
        assertFalse(Action.CopyToClipboard("x").opensScreen())
        val inside = Action.IfElse(otherwise = listOf(Action.DoNotDisturb(DndMode.OFF), Action.OpenApp("x")))
        assertTrue(inside.opensScreen())
        assertTrue(inside.needsUnlock())
        assertTrue(inside.needsDndAccess())
        assertEquals(3, inside.flatten().size)
    }
}
