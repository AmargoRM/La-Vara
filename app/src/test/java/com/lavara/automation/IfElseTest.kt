package com.lavara.automation

import com.lavara.actions.Action
import com.lavara.conditions.Comparison
import com.lavara.conditions.Condition
import com.lavara.triggers.Trigger
import com.lavara.triggers.TriggerEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IfElseTest {

    private val clock = FakeClock()
    private val device = FakeDevice(battery = 50)
    private val yes = Action.Flashlight(on = true)
    private val no = Action.Vibrate(300)
    private val after = Action.ShowNotification("Fin", "")

    private fun ifBattery(over: Int, then: List<Action> = listOf(yes), otherwise: List<Action> = listOf(no)) =
        Action.IfElse(listOf(Condition.BatteryLevel(Comparison.GREATER_THAN, over)), then = then, otherwise = otherwise)

    private fun run(actions: List<Action>, onError: OnError = OnError.STOP, executor: RecordingExecutor = RecordingExecutor()) =
        runBlocking {
            val automation = Automation(id = "a", name = "A", trigger = Trigger.Time("08:00"), actions = actions, onError = onError)
            AutomationEngine(ListSource(listOf(automation)), executor, clock, device, sleep = {})
                .handle(TriggerEvent.TimeReached(clock.now())).single() to executor
        }

    @Test
    fun seCumple_haceLaPrimeraRamaYSigue() {
        val (result, executor) = run(listOf(ifBattery(20), after))
        assertTrue(result.success)
        assertEquals(listOf(yes, after), executor.done)
        assertTrue(result.executedActions[0].action, result.executedActions[0].action.endsWith("→ se cumple"))
    }

    @Test
    fun noSeCumple_haceLaOtraRama() {
        val (result, executor) = run(listOf(ifBattery(80), after))
        assertTrue(result.success)
        assertEquals(listOf(no, after), executor.done)
        assertTrue(result.executedActions[0].action.endsWith("→ no se cumple"))
    }

    @Test
    fun sinCondiciones_seCumple() {
        val (_, executor) = run(listOf(Action.IfElse(then = listOf(yes), otherwise = listOf(no))))
        assertEquals(listOf(yes), executor.done)
    }

    @Test
    fun alguna_bastaConUna() {
        val anyOf = Action.IfElse(
            listOf(Condition.BatteryLevel(Comparison.GREATER_THAN, 80), Condition.BatteryLevel(Comparison.LESS_THAN, 60)),
            matchAll = false, then = listOf(yes), otherwise = listOf(no),
        )
        assertEquals(listOf(yes), run(listOf(anyOf)).second.done)
    }

    @Test
    fun fallaAdentro_conDetener_noSigue() {
        val (result, executor) = run(listOf(ifBattery(20, then = listOf(yes, no)), after), executor = RecordingExecutor(failing = setOf(yes)))
        assertFalse(result.success)
        assertEquals(listOf(yes), executor.done)
        assertTrue(result.errorMessage!!.contains("falla de prueba"))
    }

    @Test
    fun fallaAdentro_conSeguir_haceElResto() {
        val (result, executor) = run(
            listOf(ifBattery(20, then = listOf(yes, no)), after),
            onError = OnError.CONTINUE,
            executor = RecordingExecutor(failing = setOf(yes)),
        )
        assertEquals(ExecutionStatus.FAILED, result.status)
        assertEquals(listOf(yes, no, after), executor.done)
    }

    @Test
    fun esperaLargaAdentro_falla() {
        val (result, executor) = run(listOf(ifBattery(20, then = listOf(Action.Delay(60), yes))))
        assertFalse(result.success)
        assertTrue(executor.done.isEmpty())
    }

    @Test
    fun json_nombresEstables_eIdaYVuelta() {
        val automation = Automation(id = "a", name = "A", trigger = Trigger.Manual, actions = listOf(ifBattery(20)))
        val text = AutomationJson.encode(automation)
        assertEquals(automation, AutomationJson.decode(text))
        listOf("\"type\": \"if\"", "\"then\"", "\"otherwise\"", "\"type\": \"vibrate\"").forEach { assertTrue("Falta $it en\n$text", text.contains(it)) }
    }

    @Test
    fun borrador_revisaLasRamas() {
        val draft = AutomationDraft(actions = listOf(Action.IfElse(then = listOf(Action.Delay(60), Action.CopyToClipboard("")))))
        assertEquals(
            listOf(
                "La acción 1 (si se cumple, 1): dentro de un \"si\" la espera máxima es de 10 segundos.",
                "La acción 1 (si se cumple, 2) necesita el texto a copiar.",
            ),
            draft.problems(),
        )
        assertEquals(listOf("La acción 1 (\"si\") no tiene acciones adentro."), AutomationDraft(actions = listOf(Action.IfElse())).problems())
    }
}
