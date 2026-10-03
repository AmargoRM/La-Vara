package com.lavara.actions

import com.lavara.automation.Automation
import com.lavara.automation.AutomationDraft
import com.lavara.automation.AutomationJson
import com.lavara.automation.Variables
import com.lavara.triggers.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageActionsTest {

    @Test
    fun numero_limpioParaMarcarYParaWhatsApp() {
        assertEquals("+50688887777", Phone.dialable("+506 8888-7777"))
        assertEquals("88887777", Phone.dialable(" 8888 7777 "))
        assertEquals("50688887777", Phone.international("+506 8888-7777"))
        assertEquals("50688887777", Phone.international("00506 8888 7777"))
        assertTrue(Phone.hasCountryCode("506 8888 7777"))
        assertFalse(Phone.hasCountryCode("8888-7777"))
    }

    @Test
    fun aQuien_nombreOUltimosDigitos() {
        assertEquals("Mamá", Phone.label("88887777", " Mamá "))
        assertEquals("…7777", Phone.label("+506 8888-7777", ""))
        assertEquals("(sin número)", Phone.label("", ""))
    }

    @Test
    fun whatsApp_enlaceConTextoCodificado() {
        val action = Action.WhatsAppMessage(phone = "+506 8888-7777", text = "Voy en camino & llego 5:30")
        assertEquals(
            "https://api.whatsapp.com/send?phone=50688887777&text=Voy%20en%20camino%20%26%20llego%205%3A30",
            action.link(),
        )
    }

    @Test
    fun navegar_direccionYCoordenadas() {
        assertEquals("https://waze.com/ul?q=Mall%20San%20Pedro&navigate=yes", Action.Navigate("Mall San Pedro").link())
        assertEquals(
            "https://waze.com/ul?ll=9.9325%2C-84.0796&navigate=yes",
            Action.Navigate(" 9.9325, -84.0796 ").link(),
        )
        assertEquals("google.navigation:q=9.9325%2C-84.0796", Action.Navigate("9.9325,-84.0796", NavigationApp.GOOGLE_MAPS).link())
    }

    @Test
    fun variables_seReemplazanEnWhatsAppYSms() {
        val vars = Variables(mapOf(Variables.BATTERY to "15"))
        assertEquals("Batería 15", (vars.applyTo(Action.SendSms("8888", "Batería %battery")) as Action.SendSms).text)
        assertEquals("Batería 15", (vars.applyTo(Action.WhatsAppMessage("506", "Batería %battery")) as Action.WhatsAppMessage).text)
    }

    @Test
    fun json_nombresEstablesYValoresPorDefecto() {
        val all = listOf(
            Action.WhatsAppMessage("50688887777", "Hola", "Mamá"),
            Action.DialNumber("88887777", "Mamá"),
            Action.Navigate("Casa", NavigationApp.GOOGLE_MAPS),
            Action.SendSms("88887777", "Hola", "Mamá"),
        )
        val automation = Automation(id = "x", name = "X", trigger = Trigger.Manual, actions = all)
        val text = AutomationJson.encode(automation)
        assertEquals(automation, AutomationJson.decode(text))
        val compact = text.filterNot { it.isWhitespace() }
        listOf("whatsapp_message", "dial_number", "navigate", "send_sms", "google_maps").forEach {
            assertTrue(compact, compact.contains("\"$it\""))
        }
        val minimo = AutomationJson.decode(
            """{"id":"a","name":"A","trigger":{"type":"manual"},"actions":[{"type":"navigate","destination":"Casa"},{"type":"send_sms"}]}""",
        )
        assertEquals(listOf(Action.Navigate("Casa", NavigationApp.WAZE), Action.SendSms()), minimo.actions)
    }

    @Test
    fun editor_pideNumeroCodigoYTexto() {
        fun problems(action: Action) = AutomationDraft(name = "X", actions = listOf(action)).problems()
        assertTrue(problems(Action.WhatsAppMessage()).single().contains("número"))
        assertTrue(problems(Action.WhatsAppMessage("8888 7777")).single().contains("código de país"))
        assertEquals(emptyList<String>(), problems(Action.WhatsAppMessage("506 8888 7777")))
        assertTrue(problems(Action.SendSms("88887777", " ")).single().contains("texto"))
        assertEquals(emptyList<String>(), problems(Action.SendSms("88887777", "Hola")))
        assertTrue(problems(Action.DialNumber()).single().contains("número"))
        assertTrue(problems(Action.Navigate()).single().contains("destino"))
    }

    @Test
    fun abrenPantalla_lasQueNecesitanMostrarSobreOtrasApps() {
        assertTrue(Action.WhatsAppMessage().opensScreen())
        assertTrue(Action.Navigate().opensScreen())
        assertTrue(Action.DialNumber().opensScreen())
        assertFalse(Action.SendSms().opensScreen())
        assertFalse(Action.Flashlight().opensScreen())
    }
}
