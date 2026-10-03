package com.lavara.triggers

import com.lavara.automation.Automation
import com.lavara.automation.AutomationJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationNfcTriggerTest {

    private fun posted(pkg: String = "com.whatsapp", title: String = "Mamá", text: String = "Ya llegué") =
        TriggerEvent.NotificationPosted(pkg, title, text, key = "k", postTime = 1L)

    @Test
    fun notificacion_porApp_yPorTexto() {
        val whatsapp = Trigger.Notification(packageName = "com.whatsapp")
        assertTrue(TriggerMatcher.matches(whatsapp, "a", posted()))
        assertFalse(TriggerMatcher.matches(whatsapp, "a", posted(pkg = "com.telegram")))

        val llegue = Trigger.Notification(packageName = "com.whatsapp", textContains = "LLEGUÉ")
        assertTrue(TriggerMatcher.matches(llegue, "a", posted()))
        assertTrue(TriggerMatcher.matches(Trigger.Notification(textContains = "mamá"), "a", posted()))
        assertFalse(TriggerMatcher.matches(llegue, "a", posted(text = "Saliendo")))

        assertTrue(TriggerMatcher.matches(Trigger.Notification(), "a", posted(pkg = "cualquier.app")))
        assertFalse(TriggerMatcher.matches(whatsapp, "a", TriggerEvent.ManualRun("b", "r")))
    }

    @Test
    fun notificacion_noMuestraElContenidoAlConvertirseEnTexto() {
        val event = posted(text = "clave 1234")
        assertFalse(event.toString().contains("1234"))
        assertFalse(event.dedupKey.contains("1234"))
    }

    @Test
    fun nfc_porCodigo() {
        val tag = Trigger.Nfc(tagId = "ab12cd", tagName = "Mesa")
        assertTrue(TriggerMatcher.matches(tag, "a", TriggerEvent.NfcTagScanned("AB12CD", "r1")))
        assertFalse(TriggerMatcher.matches(tag, "a", TriggerEvent.NfcTagScanned("otro", "r1")))
        // Sin grabar todavía: no responde a ninguna etiqueta.
        assertFalse(TriggerMatcher.matches(Trigger.Nfc(), "a", TriggerEvent.NfcTagScanned("", "r1")))
    }

    @Test
    fun json_nombresEstables_yValoresPorDefecto() {
        val n = Automation(id = "n", name = "N", trigger = Trigger.Notification("com.whatsapp", "WhatsApp", "llegué"))
        val t = Automation(id = "t", name = "T", trigger = Trigger.Nfc("ab12cd", "Mesa"))
        val nText = AutomationJson.encode(n)
        val tText = AutomationJson.encode(t)
        listOf("\"type\": \"notification\"", "\"packageName\": \"com.whatsapp\"", "\"textContains\": \"llegué\"")
            .forEach { assertTrue("Falta $it en\n$nText", nText.contains(it)) }
        listOf("\"type\": \"nfc\"", "\"tagId\": \"ab12cd\"", "\"tagName\": \"Mesa\"")
            .forEach { assertTrue("Falta $it en\n$tText", tText.contains(it)) }
        assertEquals(n, AutomationJson.decode(nText))
        assertEquals(t, AutomationJson.decode(tText))
        assertEquals(Trigger.Notification(), AutomationJson.decode("""{"id":"a","name":"A","trigger":{"type":"notification"}}""").trigger)
        assertEquals(Trigger.Nfc(), AutomationJson.decode("""{"id":"a","name":"A","trigger":{"type":"nfc"}}""").trigger)
    }

    @Test
    fun soloPideAccesoSiHayUnaActiva() {
        val on = Automation(id = "n", name = "N", trigger = Trigger.Notification())
        assertTrue(TriggerMatcher.needsNotificationAccess(listOf(on)))
        assertFalse(TriggerMatcher.needsNotificationAccess(listOf(on.copy(enabled = false))))
    }
}
