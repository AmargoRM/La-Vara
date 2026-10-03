package com.lavara.actions

import com.lavara.automation.Automation
import com.lavara.automation.AutomationDraft
import com.lavara.automation.AutomationJson
import com.lavara.triggers.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TapInAppTest {

    @Test
    fun boton_igualGanaAContiene() {
        assertEquals(3, ButtonMatch.score("Enviar", null, " enviar ", null))
        assertEquals(3, ButtonMatch.score("enviar", "ENVIAR", null, null))
        assertEquals(2, ButtonMatch.score("send", null, null, "com.whatsapp:id/send"))
        assertEquals(1, ButtonMatch.score("Enviar", "Enviar mensaje", null, null))
        assertEquals(0, ButtonMatch.score("Enviar", "Adjuntar", "Cámara", "com.whatsapp:id/camera"))
        assertEquals(0, ButtonMatch.score("  ", "Enviar", null, null))
    }

    @Test
    fun json_nombreEstableYValoresPorDefecto() {
        val automation = Automation(id = "x", name = "X", trigger = Trigger.Manual, actions = listOf(Action.TapInApp("com.whatsapp", "Enviar", 8)))
        val text = AutomationJson.encode(automation)
        assertEquals(automation, AutomationJson.decode(text))
        assertTrue(text, text.filterNot { it.isWhitespace() }.contains("\"type\":\"tap_in_app\""))
        val minimo = AutomationJson.decode("""{"id":"a","name":"A","trigger":{"type":"manual"},"actions":[{"type":"tap_in_app"}]}""")
        assertEquals(listOf(Action.TapInApp("", "", 5)), minimo.actions)
    }

    @Test
    fun editor_pideAppBotonYEsperaValida() {
        fun problems(action: Action) = AutomationDraft(name = "X", actions = listOf(action)).problems()
        assertTrue(problems(Action.TapInApp(button = "Enviar")).single().contains("app"))
        assertTrue(problems(Action.TapInApp("com.whatsapp")).single().contains("botón"))
        assertTrue(problems(Action.TapInApp("com.whatsapp", "Enviar", 30)).single().contains("segundos"))
        assertEquals(emptyList<String>(), problems(Action.TapInApp("com.whatsapp", "Enviar", 5)))
    }
}
