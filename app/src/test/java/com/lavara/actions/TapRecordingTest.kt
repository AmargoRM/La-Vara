package com.lavara.actions

import com.lavara.automation.Automation
import com.lavara.automation.AutomationDraft
import com.lavara.automation.AutomationJson
import com.lavara.triggers.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TapRecordingTest {

    private val uber = TapRecording("com.ubercab", "Uber", startedAt = 0)
    private fun named(label: String) = TapRecording.Step(label = label, x = 0.5, y = 0.5)
    private fun point(x: Double, y: Double) = TapRecording.Step(x = x, y = y)

    @Test
    fun grabaEnOrden_conLaEsperaDelUsuario() {
        val r = uber.add(named("Casa"), 2_050).add(point(0.25, 0.75), 4_000)
        assertEquals(listOf("Casa", null), r.steps.map { it.label })
        assertEquals(2_000L, r.steps[0].pauseMillis)
        assertEquals(1_900L, r.steps[1].pauseMillis)
        // Esperas muy cortas o muy largas se acomodan entre 0,3 y 10 segundos.
        assertEquals(TapRecording.MIN_PAUSE_MILLIS, r.add(point(0.1, 0.1), 4_060).steps[2].pauseMillis)
        assertEquals(TapRecording.MAX_PAUSE_MILLIS, r.add(point(0.1, 0.1), 60_000).steps[2].pauseMillis)
    }

    @Test
    fun toquesSinGrabar_seCuentan() {
        val r = uber.skip(1_000).add(named("Casa"), 3_050)
        assertEquals(1, r.skipped)
        assertEquals(2_000L, r.steps.single().pauseMillis)
    }

    @Test
    fun terminada_noGrabaMas_yTieneLimite() {
        assertTrue(uber.copy(active = false).add(named("Casa"), 1_000).steps.isEmpty())
        var r = uber
        repeat(TapRecording.MAX_STEPS + 5) { r = r.add(point(0.5, 0.5), it * 1_000L) }
        assertEquals(TapRecording.MAX_STEPS, r.steps.size)
    }

    @Test
    fun quitarUnToque() {
        val r = uber.add(named("Casa"), 1_000).add(named("Otro"), 2_000).add(named("Confirmar"), 3_000).remove(1)
        assertEquals(listOf("Casa", "Confirmar"), r.steps.map { it.label })
        assertEquals(r, r.remove(9))
    }

    @Test
    fun borrador_porNombreYPorPosicion() {
        val swipe = TapRecording.Step(x = 0.5, y = 0.8, toX = 0.5, toY = 0.2, durationMillis = 300)
        val draft = uber.add(named("Casa"), 1_000).add(point(0.12345, 0.9), 3_000).add(swipe, 5_300).toDraft()
        val wait = AutomationDraft.MAX_DELAY_SECONDS.toInt()
        assertEquals(Trigger.Manual, draft.trigger)
        assertEquals(
            listOf(
                Action.OpenApp("com.ubercab"),
                Action.TapInApp("com.ubercab", "Casa", wait),
                Action.TouchScreen("com.ubercab", x = 0.123, y = 0.9, durationMillis = 50, pauseMillis = 1_950, waitSeconds = wait),
                Action.TouchScreen("com.ubercab", 0.5, 0.8, 0.5, 0.2, durationMillis = 300, pauseMillis = 2_000, waitSeconds = wait),
            ),
            draft.actions,
        )
        assertTrue(draft.problems().toString(), draft.problems().isEmpty())
    }

    @Test
    fun mismoBotonSeguido_esperaUnSegundoEntreLosDos() {
        val actions = uber.add(named("Siguiente"), 1_000).add(named("Siguiente"), 5_000).toActions()
        assertEquals(Action.Delay(1), actions[2])
        assertEquals(4, actions.size)
    }

    @Test
    fun textos() {
        assertEquals("Tocar \"Casa\"", named("Casa").text())
        assertEquals("Tocar en un punto (25 % desde la izquierda, 75 % desde arriba)", point(0.25, 0.75).text())
        assertEquals("Deslizar hacia arriba", TapRecording.Step(x = 0.5, y = 0.8, toX = 0.5, toY = 0.2).text())
        assertEquals("Deslizar hacia la izquierda", touchText(Action.TouchScreen("a", 0.9, 0.5, 0.1, 0.55)))
        assertEquals("Mantener tocado en un punto (50 % desde la izquierda, 50 % desde arriba)", touchText(Action.TouchScreen("a", durationMillis = 800)))
    }

    @Test
    fun json_touchScreen_nombreEstableYValoresPorDefecto() {
        val automation = Automation(id = "x", name = "X", trigger = Trigger.Manual, actions = listOf(Action.TouchScreen("com.supercell", 0.3, 0.4)))
        val text = AutomationJson.encode(automation)
        assertEquals(automation, AutomationJson.decode(text))
        assertTrue(text, text.filterNot { it.isWhitespace() }.contains("\"type\":\"touch_screen\""))
        val minimo = AutomationJson.decode("""{"id":"a","name":"A","trigger":{"type":"manual"},"actions":[{"type":"touch_screen"}]}""")
        assertEquals(listOf(Action.TouchScreen()), minimo.actions)
    }

    @Test
    fun problemas_touchScreen() {
        fun problems(a: Action) = AutomationDraft(trigger = Trigger.Manual, actions = listOf(a)).problems()
        assertTrue(problems(Action.TouchScreen("")).isNotEmpty())
        assertTrue(problems(Action.TouchScreen("a", x = 1.2)).isNotEmpty())
        assertTrue(problems(Action.TouchScreen("a", pauseMillis = 20_000)).isNotEmpty())
        assertTrue(problems(Action.TouchScreen("a")).isEmpty())
        assertTrue(Action.TouchScreen("a").needsUnlock())
    }
}
