package com.lavara.automation

import com.lavara.actions.Action
import com.lavara.conditions.Comparison
import com.lavara.conditions.Condition
import com.lavara.triggers.BatteryDirection
import com.lavara.triggers.PowerEvent
import com.lavara.triggers.Trigger
import com.lavara.triggers.TriggerEvent
import com.lavara.triggers.TriggerMatcher
import com.lavara.triggers.Weekday
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationEngineTest {

    private val clock = FakeClock()
    private val device = FakeDevice(battery = 50)
    private val notify = Action.ShowNotification("Hola", "Batería %battery % a las %time del %date")
    private val openApp = Action.OpenApp("com.whatsapp")

    private fun automation(
        id: String,
        trigger: Trigger = Trigger.Time("08:00"),
        conditions: List<Condition> = emptyList(),
        actions: List<Action> = listOf(notify),
        onError: OnError = OnError.STOP,
        cooldownSeconds: Long = 0,
        enabled: Boolean = true,
        priority: Int = 0,
    ) = Automation(
        id = id, name = id, trigger = trigger, conditions = conditions, actions = actions,
        onError = onError, cooldownSeconds = cooldownSeconds, enabled = enabled, priority = priority,
    )

    private fun engine(source: AutomationSource, executor: RecordingExecutor, slept: MutableList<Long> = mutableListOf()) =
        AutomationEngine(source, executor, clock, device, sleep = { slept += it })

    private fun at8() = TriggerEvent.TimeReached(clock.now())

    @Test
    fun pruebaVara_seEjecutaConBateriaSuficiente() = runBlocking {
        val prueba = automation(
            "prueba-vara",
            conditions = listOf(Condition.BatteryLevel(Comparison.GREATER_THAN, 20)),
        )
        val executor = RecordingExecutor()
        val results = engine(ListSource(listOf(prueba)), executor).handle(at8())

        assertEquals(1, results.size)
        assertTrue(results[0].success)
        assertEquals(1, results[0].executedActions.size)
        // Las variables se reemplazan antes de llegar al ejecutor.
        assertEquals(Action.ShowNotification("Hola", "Batería 50 % a las 08:00 del 05/10/2026"), executor.done.single())
    }

    @Test
    fun condicionFalsa_noEjecutaYExplicaPorQue() = runBlocking {
        device.battery = 10
        val prueba = automation("a", conditions = listOf(Condition.BatteryLevel(Comparison.GREATER_THAN, 20)))
        val executor = RecordingExecutor()
        val result = engine(ListSource(listOf(prueba)), executor).handle(at8()).single()

        assertEquals(ExecutionStatus.SKIPPED_CONDITIONS, result.status)
        assertTrue(result.reason, result.reason.contains("batería > 20 %"))
        assertTrue(executor.done.isEmpty())
    }

    @Test
    fun soloResponden_lasActivasYCompatibles_porPrioridad() = runBlocking {
        val source = ListSource(
            listOf(
                automation("baja", priority = 1),
                automation("alta", priority = 5),
                automation("apagada", enabled = false),
                automation("otra-hora", trigger = Trigger.Time("09:00")),
                automation("bateria", trigger = Trigger.Battery(20, BatteryDirection.BELOW)),
            ),
        )
        val results = engine(source, RecordingExecutor()).handle(at8())
        assertEquals(listOf("alta", "baja"), results.map { it.automationId })
    }

    @Test
    fun diasDeLaSemana_filtranElTrigger() = runBlocking {
        // 05/10/2026 es lunes.
        val source = ListSource(
            listOf(
                automation("lunes", trigger = Trigger.Time("08:00", listOf(Weekday.MONDAY))),
                automation("sabado", trigger = Trigger.Time("08:00", listOf(Weekday.SATURDAY))),
            ),
        )
        assertEquals(listOf("lunes"), engine(source, RecordingExecutor()).handle(at8()).map { it.automationId })
    }

    @Test
    fun cooldown_impideRepetirAntesDeTiempo() = runBlocking {
        val a = automation("a", trigger = Trigger.Manual, cooldownSeconds = 60)
        val engine = engine(ListSource(listOf(a)), RecordingExecutor())

        assertTrue(engine.handle(TriggerEvent.ManualRun("a", "1")).single().success)
        clock.advanceSeconds(30)
        val second = engine.handle(TriggerEvent.ManualRun("a", "2")).single()
        assertEquals(ExecutionStatus.SKIPPED_COOLDOWN, second.status)
        assertTrue(second.reason, second.reason.contains("faltan 30 s"))
        clock.advanceSeconds(30)
        assertTrue(engine.handle(TriggerEvent.ManualRun("a", "3")).single().success)
    }

    @Test
    fun cooldown_respetaLaUltimaEjecucionGuardada() = runBlocking {
        val a = automation("a", cooldownSeconds = 600).copy(
            lastExecutedAt = clock.now().minusSeconds(60).toInstant().toEpochMilli(),
        )
        val result = engine(ListSource(listOf(a)), RecordingExecutor()).handle(at8()).single()
        assertEquals(ExecutionStatus.SKIPPED_COOLDOWN, result.status)
    }

    @Test
    fun mismoEventoDosVeces_seEjecutaUnaSola() = runBlocking {
        val executor = RecordingExecutor()
        val engine = engine(ListSource(listOf(automation("a"))), executor)
        val event = at8()

        assertTrue(engine.handle(event).single().success)
        assertEquals(ExecutionStatus.SKIPPED_DUPLICATE, engine.handle(event).single().status)
        assertEquals(1, executor.done.size)
    }

    @Test
    fun fallaConStop_noHaceLasAccionesSiguientes() = runBlocking {
        val executor = RecordingExecutor(failing = setOf(openApp))
        val a = automation("a", actions = listOf(openApp, notify), onError = OnError.STOP)
        val result = engine(ListSource(listOf(a)), executor).handle(at8()).single()

        assertEquals(ExecutionStatus.FAILED, result.status)
        assertFalse(result.success)
        assertEquals("Abrir app com.whatsapp", result.failedAction)
        assertEquals("falla de prueba", result.errorMessage)
        assertEquals(1, result.executedActions.size)
        assertEquals(listOf<Action>(openApp), executor.done)
    }

    @Test
    fun fallaConContinue_haceLasAccionesSiguientes() = runBlocking {
        val executor = RecordingExecutor(failing = setOf(openApp))
        val a = automation("a", actions = listOf(openApp, notify), onError = OnError.CONTINUE)
        val result = engine(ListSource(listOf(a)), executor).handle(at8()).single()

        assertEquals(ExecutionStatus.FAILED, result.status)
        assertEquals(listOf(false, true), result.executedActions.map { it.success })
        assertEquals(2, executor.done.size)
    }

    @Test
    fun excepcionDelEjecutor_seRegistraComoFalla() = runBlocking {
        val throwing = AutomationEngine(
            ListSource(listOf(automation("a"))),
            { throw IllegalStateException("se rompió") },
            clock, device,
        )
        val result = throwing.handle(at8()).single()
        assertEquals(ExecutionStatus.FAILED, result.status)
        assertEquals("se rompió", result.errorMessage)
    }

    @Test
    fun delay_esperaSinLlamarAlEjecutor() = runBlocking {
        val slept = mutableListOf<Long>()
        val executor = RecordingExecutor()
        val a = automation("a", actions = listOf(Action.Delay(5), notify))
        val result = engine(ListSource(listOf(a)), executor, slept).handle(at8()).single()

        assertTrue(result.success)
        assertEquals(listOf(5_000L), slept)
        assertEquals(1, executor.done.size)
    }

    @Test
    fun runAutomation_ejecutaLaOtra() = runBlocking {
        val executor = RecordingExecutor()
        val a = automation("a", actions = listOf(Action.RunAutomation("b")))
        val b = automation("b", trigger = Trigger.Manual, actions = listOf(openApp))
        val results = engine(ListSource(listOf(a, b)), executor).handle(at8())

        assertEquals(listOf("b", "a"), results.map { it.automationId })
        assertTrue(results.all { it.success })
        assertEquals(listOf<Action>(openApp), executor.done)
    }

    @Test
    fun runAutomation_cicloSeDetieneYSeRegistra() = runBlocking {
        val a = automation("a", actions = listOf(Action.RunAutomation("b")))
        val b = automation("b", trigger = Trigger.Manual, actions = listOf(Action.RunAutomation("a")))
        val results = engine(ListSource(listOf(a, b)), RecordingExecutor()).handle(at8())

        val resultB = results.first { it.automationId == "b" }
        assertEquals(ExecutionStatus.FAILED, resultB.status)
        assertTrue(resultB.errorMessage!!, resultB.errorMessage!!.contains("Ciclo detectado: a → b → a"))
        assertEquals(ExecutionStatus.FAILED, results.first { it.automationId == "a" }.status)
    }

    @Test
    fun runAutomation_inexistenteFalla() = runBlocking {
        val a = automation("a", actions = listOf(Action.RunAutomation("no-existe")))
        val result = engine(ListSource(listOf(a)), RecordingExecutor()).handle(at8()).single()
        assertEquals(ExecutionStatus.FAILED, result.status)
    }

    @Test
    fun manual_ejecutaCualquieraPeroExplicaSiEstaDesactivada() = runBlocking {
        val source = ListSource(listOf(automation("a"), automation("apagada", enabled = false)))
        val engine = engine(source, RecordingExecutor())

        assertTrue(engine.handle(TriggerEvent.ManualRun("a", "1")).single().success)
        assertEquals(ExecutionStatus.SKIPPED_DISABLED, engine.handle(TriggerEvent.ManualRun("apagada", "2")).single().status)
    }

    @Test
    fun bateria_disparaSoloAlCruzarElUmbral() = runBlocking {
        val executor = RecordingExecutor()
        val engine = engine(ListSource(listOf(automation("baja", trigger = Trigger.Battery(20, BatteryDirection.BELOW)))), executor)

        assertEquals(1, engine.handle(TriggerEvent.BatteryChanged(level = 20, previousLevel = 21)).size)
        assertEquals(0, engine.handle(TriggerEvent.BatteryChanged(level = 19, previousLevel = 20)).size)
        assertEquals(0, engine.handle(TriggerEvent.BatteryChanged(level = 25, previousLevel = 19)).size)
    }

    @Test
    fun cargador_conectarYDesconectarDisparanCadaUnoLoSuyo() = runBlocking {
        val executor = RecordingExecutor()
        val engine = engine(
            ListSource(
                listOf(
                    automation("conectar", trigger = Trigger.Power(PowerEvent.CONNECTED)),
                    automation("desconectar", trigger = Trigger.Power(PowerEvent.DISCONNECTED)),
                    automation("bateria", trigger = Trigger.Battery(20, BatteryDirection.BELOW)),
                ),
            ),
            executor,
        )

        assertEquals(listOf("conectar"), engine.handle(TriggerEvent.PowerChanged(connected = true, atMillis = 1)).map { it.automationId })
        assertEquals(listOf("desconectar"), engine.handle(TriggerEvent.PowerChanged(connected = false, atMillis = 2)).map { it.automationId })
        // Cada conexión es un evento distinto: la segunda también se ejecuta.
        assertTrue(engine.handle(TriggerEvent.PowerChanged(connected = true, atMillis = 3)).single().success)
        // El mismo aviso repetido no ejecuta dos veces.
        assertEquals(
            ExecutionStatus.SKIPPED_DUPLICATE,
            engine.handle(TriggerEvent.PowerChanged(connected = true, atMillis = 3)).single().status,
        )
    }

    @Test
    fun vigilancia_soloHaceFaltaConBateriaOCargadorActivos() {
        assertFalse(TriggerMatcher.needsDeviceWatch(listOf(automation("hora"))))
        assertFalse(TriggerMatcher.needsDeviceWatch(listOf(automation("apagada", enabled = false, trigger = Trigger.Power()))))
        assertTrue(TriggerMatcher.needsDeviceWatch(listOf(automation("hora"), automation("carga", trigger = Trigger.Power()))))
        assertTrue(TriggerMatcher.needsDeviceWatch(listOf(automation("baja", trigger = Trigger.Battery(15, BatteryDirection.BELOW)))))
    }

    @Test
    fun bloqueado_abrirAppEsperaElDesbloqueoYNotificarNo() = runBlocking {
        device.locked = true
        val notify = Action.ShowNotification("Hola")
        val abre = automation("abre", actions = listOf(notify, openApp))
        val avisa = automation("avisa", actions = listOf(notify))
        val executor = RecordingExecutor()
        val engine = engine(ListSource(listOf(abre, avisa)), executor)
        val results = engine.handle(at8())

        assertEquals(ExecutionStatus.WAITING_UNLOCK, results.first { it.automationId == "abre" }.status)
        assertEquals(ExecutionStatus.EXECUTED, results.first { it.automationId == "avisa" }.status)
        assertEquals(listOf<Action>(notify), executor.done)
        // El mismo evento no la vuelve a poner en espera.
        assertEquals(ExecutionStatus.SKIPPED_DUPLICATE, engine.handle(at8()).first { it.automationId == "abre" }.status)

        // Al desbloquear, la parte Android la pide a mano y corre entera.
        device.locked = false
        val replay = engine.handle(TriggerEvent.ManualRun("abre", "desbloqueo-1"))
        assertEquals(ExecutionStatus.EXECUTED, replay.single().status)
        assertEquals(listOf<Action>(notify, notify, openApp), executor.done)
    }
}
