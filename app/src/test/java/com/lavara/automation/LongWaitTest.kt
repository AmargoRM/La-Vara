package com.lavara.automation

import com.lavara.actions.Action
import com.lavara.actions.waitText
import com.lavara.triggers.Trigger
import com.lavara.triggers.TriggerEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LongWaitTest {

    private val clock = FakeClock()
    private val on = Action.Flashlight(on = true)
    private val off = Action.Flashlight(on = false)
    private val scheduled = mutableListOf<Triple<String, Int, Long>>()
    private val slept = mutableListOf<Long>()

    private fun engine(vararg automations: Automation, executor: RecordingExecutor) = AutomationEngine(
        ListSource(automations.toList()), executor, clock, FakeDevice(),
        later = { id, from, at -> scheduled += Triple(id, from, at) },
        sleep = { slept += it },
    )

    private fun linterna(wait: Long, enabled: Boolean = true) =
        Automation(id = "l", name = "Linterna", trigger = Trigger.Manual, enabled = enabled, actions = listOf(on, Action.Delay(wait), off))

    @Test
    fun esperaLarga_programaElRestoYNoEspera() = runBlocking {
        val executor = RecordingExecutor()
        val result = engine(linterna(600), executor = executor).handle(TriggerEvent.ManualRun("l", "r")).single()

        assertEquals(ExecutionStatus.EXECUTED, result.status)
        assertEquals(listOf<Action>(on), executor.done)
        assertTrue(slept.isEmpty())
        val start = clock.now().toInstant().toEpochMilli()
        assertEquals(listOf(Triple("l", 2, start + 600_000)), scheduled)
        assertTrue(result.reason, result.reason.contains("siguen a las 08:10"))
        assertEquals("Esperar 10 min", result.executedActions[1].action)
    }

    @Test
    fun esperaCorta_esperaAhiMismo() = runBlocking {
        val executor = RecordingExecutor()
        engine(linterna(10), executor = executor).handle(TriggerEvent.ManualRun("l", "r"))
        assertEquals(listOf<Action>(on, off), executor.done)
        assertEquals(listOf(10_000L), slept)
        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun alSeguir_haceSoloLoQueFalta() = runBlocking {
        val executor = RecordingExecutor()
        val result = engine(linterna(600), executor = executor).resume("l", 2)
        assertEquals(ExecutionStatus.EXECUTED, result.status)
        assertEquals(listOf<Action>(off), executor.done)
        assertEquals(2, result.executedActions.single().index)
    }

    @Test
    fun alSeguir_siSeDesactivoOSeBorro_noHaceNada() = runBlocking {
        val executor = RecordingExecutor()
        assertEquals(ExecutionStatus.SKIPPED_DISABLED, engine(linterna(600, enabled = false), executor = executor).resume("l", 2).status)
        assertEquals(ExecutionStatus.SKIPPED_DISABLED, engine(executor = executor).resume("l", 2).status)
        assertTrue(executor.done.isEmpty())
    }

    @Test
    fun dosEsperasLargas_seEncadenan() = runBlocking {
        val executor = RecordingExecutor()
        val a = Automation(id = "a", name = "A", trigger = Trigger.Manual, actions = listOf(on, Action.Delay(3600), off, Action.Delay(7200), on))
        val engine = engine(a, executor = executor)
        engine.handle(TriggerEvent.ManualRun("a", "r"))
        assertEquals(2, scheduled.last().second)
        engine.resume("a", 2)
        assertEquals(4, scheduled.last().second)
        engine.resume("a", 4)
        assertEquals(listOf<Action>(on, off, on), executor.done)
    }

    @Test
    fun esperaLargaAlFinal_noProgramaNada() = runBlocking {
        val executor = RecordingExecutor()
        val a = Automation(id = "a", name = "A", trigger = Trigger.Manual, actions = listOf(on, Action.Delay(3600)))
        engine(a, executor = executor).handle(TriggerEvent.ManualRun("a", "r"))
        assertTrue(scheduled.isEmpty())
        assertTrue(slept.isEmpty())
    }

    @Test
    fun textoDeEspera() {
        assertEquals("30 s", waitText(30))
        assertEquals("10 min", waitText(600))
        assertEquals("1 h 30 min", waitText(5400))
        assertEquals("2 h", waitText(7200))
        assertEquals("0 s", waitText(0))
    }

    @Test
    fun editor_aceptaHasta24Horas() {
        fun problems(seconds: Long) = AutomationDraft(name = "x", actions = listOf(Action.Delay(seconds))).problems()
        assertTrue(problems(86_400).isEmpty())
        assertEquals(1, problems(86_401).size)
    }
}

class NfcDraftTest {
    @Test
    fun nfcSinGrabar_noSeGuarda() {
        val draft = AutomationDraft(name = "x", trigger = Trigger.Nfc(), actions = listOf(Action.Flashlight()))
        assertEquals(1, draft.problems().size)
        assertTrue(draft.copy(trigger = Trigger.Nfc("abc")).problems().isEmpty())
    }
}
