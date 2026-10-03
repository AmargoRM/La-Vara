package com.lavara.automation

import com.lavara.actions.Action
import com.lavara.actions.DndMode
import com.lavara.actions.RingerMode
import com.lavara.actions.SystemPanel
import com.lavara.actions.VolumeStream
import com.lavara.conditions.Comparison
import com.lavara.conditions.Condition
import com.lavara.triggers.BatteryDirection
import com.lavara.triggers.PowerEvent
import com.lavara.triggers.Trigger
import com.lavara.triggers.Weekday
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationJsonTest {

    private fun roundTrip(automation: Automation) {
        val text = AutomationJson.encode(automation)
        assertEquals(text, automation, AutomationJson.decode(text))
    }

    private fun base(trigger: Trigger) = Automation(id = "x", name = "X", trigger = trigger)

    @Test
    fun idaYVuelta_cadaTrigger() {
        roundTrip(base(Trigger.Time("08:00", listOf(Weekday.MONDAY, Weekday.FRIDAY))))
        roundTrip(base(Trigger.Battery(20, BatteryDirection.BELOW)))
        roundTrip(base(Trigger.Manual))
        roundTrip(base(Trigger.Power(PowerEvent.CONNECTED)))
        roundTrip(base(Trigger.Power(PowerEvent.DISCONNECTED)))
    }

    @Test
    fun cargador_nombresEstablesYValorPorDefecto() {
        val text = AutomationJson.encode(base(Trigger.Power(PowerEvent.DISCONNECTED))).filterNot { it.isWhitespace() }
        assertTrue(text, text.contains("\"type\":\"power\""))
        assertTrue(text, text.contains("\"event\":\"disconnected\""))
        val sinEvento = AutomationJson.decode("""{"id":"a","name":"A","trigger":{"type":"power"}}""")
        assertEquals(Trigger.Power(PowerEvent.CONNECTED), sinEvento.trigger)
    }

    @Test
    fun idaYVuelta_cadaCondicion() {
        roundTrip(
            base(Trigger.Manual).copy(
                conditions = listOf(
                    Condition.BatteryLevel(Comparison.GREATER_THAN, 20),
                    Condition.TimeBetween("22:00", "06:00"),
                    Condition.And(listOf(Condition.Or(listOf(Condition.Not(Condition.BatteryLevel(Comparison.EQUAL, 100)))))),
                ),
            ),
        )
    }

    @Test
    fun idaYVuelta_cadaAccion() {
        roundTrip(
            base(Trigger.Manual).copy(
                actions = listOf(
                    Action.ShowNotification("Hola", "Batería %battery %"),
                    Action.OpenApp("com.whatsapp"),
                    Action.OpenUrl("https://waze.com/ul?ll=9.93,-84.08"),
                    Action.Delay(10),
                    Action.RunAutomation("otra"),
                    Action.Flashlight(on = false),
                    Action.SetVolume(VolumeStream.RING, 30),
                    Action.SetRingerMode(RingerMode.SILENT),
                    Action.DoNotDisturb(DndMode.ALARMS),
                    Action.SetBrightness(percent = 80),
                    Action.SetBrightness(auto = true),
                    Action.OpenSystemPanel(SystemPanel.MOBILE_DATA),
                ),
                onError = OnError.CONTINUE,
                cooldownSeconds = 60,
                lastExecutedAt = 1_790_000_000_000,
            ),
        )
    }

    /** Este es el ejemplo de docs/FORMATO_JSON.md. Si este test falla, el documento quedó desactualizado. */
    @Test
    fun ejemploDelDocumento_pruebaVara() {
        val text = """
            {
              "id": "prueba-vara",
              "name": "Prueba Vara",
              "trigger": { "type": "time", "time": "08:00" },
              "conditions": [
                { "type": "battery_level", "comparison": "greater_than", "value": 20 }
              ],
              "actions": [
                { "type": "show_notification", "title": "Prueba Vara", "text": "Batería %battery % a las %time" }
              ]
            }
        """.trimIndent()
        val a = AutomationJson.decode(text)
        assertEquals(Trigger.Time("08:00"), a.trigger)
        assertEquals(listOf(Condition.BatteryLevel(Comparison.GREATER_THAN, 20)), a.conditions)
        assertEquals(OnError.STOP, a.onError)
        assertTrue(a.enabled)
        assertEquals(0L, a.cooldownSeconds)
    }

    @Test
    fun nombresEstables_enElJson() {
        // Estos textos son el contrato: nunca deben cambiar.
        val text = AutomationJson.encode(
            base(Trigger.Battery(15, BatteryDirection.ABOVE)).copy(
                conditions = listOf(Condition.TimeBetween("01:00", "02:00")),
                actions = listOf(Action.OpenApp("a.b"), Action.OpenUrl("https://x.cr"), Action.Delay(1)),
                onError = OnError.CONTINUE,
            ),
        )
        listOf(
            "\"type\": \"battery\"", "\"direction\": \"above\"", "\"type\": \"time_between\"",
            "\"type\": \"open_app\"", "\"type\": \"open_url\"", "\"url\": \"https://x.cr\"", "\"type\": \"delay\"", "\"onError\": \"continue\"",
        ).forEach { assertTrue("Falta $it en\n$text", text.contains(it)) }
    }

    @Test
    fun enlace_soloHttp_yHostSinRutaNiClaves() {
        assertThrows(IllegalArgumentException::class.java) { Action.OpenUrl("waze.com") }
        assertEquals("waze.com", Action.OpenUrl("https://waze.com/ul?token=secreto").host)
    }

    @Test
    fun camposDesconocidos_seIgnoran() {
        val a = AutomationJson.decode("""{"id":"a","name":"A","trigger":{"type":"manual"},"campoDelFuturo":1}""")
        assertEquals(Trigger.Manual, a.trigger)
    }

    @Test
    fun horaInvalida_esRechazada() {
        assertThrows(IllegalArgumentException::class.java) {
            AutomationJson.decode("""{"id":"a","name":"A","trigger":{"type":"time","time":"25:00"}}""")
        }
    }

    @Test
    fun tipoDesconocido_esRechazado() {
        assertThrows(SerializationException::class.java) {
            AutomationJson.decode("""{"id":"a","name":"A","trigger":{"type":"terremoto"}}""")
        }
    }

    /** Los nombres de docs/FORMATO_JSON.md para las acciones del sistema. Nunca deben cambiar. */
    @Test
    fun accionesDelSistema_nombresFijos() {
        val text = """{"id":"a","name":"A","trigger":{"type":"manual"},"actions":[
            {"type":"flashlight","on":true},
            {"type":"set_volume","stream":"media","percent":40},
            {"type":"set_ringer_mode","mode":"vibrate"},
            {"type":"do_not_disturb","mode":"off"},
            {"type":"set_brightness","percent":20,"auto":false},
            {"type":"open_system_panel","panel":"bluetooth"}
        ]}"""
        assertEquals(
            listOf(
                Action.Flashlight(true),
                Action.SetVolume(VolumeStream.MEDIA, 40),
                Action.SetRingerMode(RingerMode.VIBRATE),
                Action.DoNotDisturb(DndMode.OFF),
                Action.SetBrightness(20, auto = false),
                Action.OpenSystemPanel(SystemPanel.BLUETOOTH),
            ),
            AutomationJson.decode(text).actions,
        )
    }

    @Test
    fun porcentajeFueraDeRango_esRechazado() {
        assertThrows(IllegalArgumentException::class.java) { Action.SetVolume(percent = 101) }
        assertThrows(IllegalArgumentException::class.java) { Action.SetBrightness(percent = 0) }
    }
}
