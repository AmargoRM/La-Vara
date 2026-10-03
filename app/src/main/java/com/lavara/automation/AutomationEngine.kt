package com.lavara.automation

import com.lavara.actions.Action
import com.lavara.actions.ActionExecutor
import com.lavara.actions.ActionResult
import com.lavara.actions.recipient
import com.lavara.conditions.ConditionEvaluator
import com.lavara.core.Clock
import com.lavara.core.DeviceState
import com.lavara.triggers.TriggerEvent
import com.lavara.triggers.TriggerMatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/** De dónde saca el motor las automatizaciones. En S2 lo implementa Room. */
interface AutomationSource {
    suspend fun all(): List<Automation>
    suspend fun find(id: String): Automation?
}

/**
 * El motor: recibe un evento, elige las automatizaciones que le corresponden, revisa sus
 * condiciones y hace sus acciones en orden. Es Kotlin puro (no importa nada de Android) para
 * poder probarlo con tests rápidos; lo que toca el teléfono pasa por [ActionExecutor] y [DeviceState].
 *
 * Protecciones:
 * - Cooldown: no repite una automatización antes de [Automation.cooldownSeconds].
 * - Duplicados: el mismo evento no dispara dos veces la misma automatización, y una
 *   automatización que ya está corriendo no arranca otra vez en paralelo.
 * - Ciclos: RunAutomation que vuelve a una automatización de la misma cadena (A → B → A) se detiene.
 */
class AutomationEngine(
    private val source: AutomationSource,
    private val executor: ActionExecutor,
    private val clock: Clock,
    private val deviceState: DeviceState,
    /** Espera sin trabar el hilo. Los tests la reemplazan para no esperar de verdad. */
    private val sleep: suspend (millis: Long) -> Unit = { delay(it) },
) {
    private val evaluator = ConditionEvaluator(clock, deviceState)

    private val mutex = Mutex()
    private val running = mutableSetOf<String>()
    private val lastRunMillis = mutableMapOf<String, Long>()
    private val seenEvents = LinkedHashSet<String>()

    /**
     * Procesa un evento. Devuelve un resultado por cada automatización que correspondía al evento
     * (ejecutada o no, con el motivo), más las que se ejecutaron mediante RunAutomation.
     */
    suspend fun handle(event: TriggerEvent): List<ExecutionResult> {
        val candidates = source.all()
            .filter { TriggerMatcher.matches(it.trigger, it.id, event) }
            // Las desactivadas se ignoran, salvo que el usuario las pida a mano: ahí se explica por qué no corren.
            .filter { it.enabled || event is TriggerEvent.ManualRun }
            .sortedByDescending { it.priority }

        val results = mutableListOf<ExecutionResult>()
        for (automation in candidates) {
            run(automation, event.dedupKey, chain = listOf(automation.id), results)
        }
        return results
    }

    /**
     * Ejecuta una automatización y agrega su resultado a [results].
     * [dedupKey] es null cuando la pide otra automatización (RunAutomation): ahí no se aplican
     * cooldown ni deduplicación, solo las condiciones.
     */
    private suspend fun run(
        automation: Automation,
        dedupKey: String?,
        chain: List<String>,
        results: MutableList<ExecutionResult>,
    ): ExecutionResult {
        val start = nowMillis()
        fun skipped(status: ExecutionStatus, reason: String) =
            ExecutionResult(automation.id, status, reason, start, 0).also { results += it }

        if (!automation.enabled) return skipped(ExecutionStatus.SKIPPED_DISABLED, "Está desactivada")

        val guard = mutex.withLock { checkGuards(automation, dedupKey, start) }
        if (guard != null) return skipped(guard.first, guard.second)

        val check = evaluator.check(automation.conditions)
        if (!check.passed) return skipped(ExecutionStatus.SKIPPED_CONDITIONS, check.reason)

        mutex.withLock {
            running += automation.id
            lastRunMillis[automation.id] = start
            if (dedupKey != null) remember("${automation.id}|$dedupKey")
        }
        try {
            val result = executeActions(automation, chain, start, results)
            results += result
            return result
        } finally {
            mutex.withLock { running -= automation.id }
        }
    }

    /** Devuelve (estado, motivo) si algo impide ejecutar, o null si se puede. */
    private fun checkGuards(automation: Automation, dedupKey: String?, now: Long): Pair<ExecutionStatus, String>? {
        if (automation.id in running) {
            return ExecutionStatus.SKIPPED_DUPLICATE to "Ya se está ejecutando"
        }
        if (dedupKey == null) return null
        if ("${automation.id}|$dedupKey" in seenEvents) {
            return ExecutionStatus.SKIPPED_DUPLICATE to "Este mismo evento ya la ejecutó"
        }
        val last = listOfNotNull(automation.lastExecutedAt, lastRunMillis[automation.id]).maxOrNull()
        if (last != null && automation.cooldownSeconds > 0) {
            val waitMillis = last + automation.cooldownSeconds * 1000 - now
            if (waitMillis > 0) {
                return ExecutionStatus.SKIPPED_COOLDOWN to
                    "Cooldown: faltan ${(waitMillis + 999) / 1000} s de ${automation.cooldownSeconds} s"
            }
        }
        return null
    }

    private suspend fun executeActions(
        automation: Automation,
        chain: List<String>,
        start: Long,
        results: MutableList<ExecutionResult>,
    ): ExecutionResult {
        val variables = Variables.from(clock.now(), deviceState.batteryLevel())
        val records = mutableListOf<ActionRecord>()
        var failedAction: String? = null
        var errorMessage: String? = null

        for ((index, action) in automation.actions.withIndex()) {
            val actionStart = nowMillis()
            val outcome = perform(action, variables, chain, results)
            val failure = outcome as? ActionResult.Failure
            records += ActionRecord(index, describe(action), nowMillis() - actionStart, failure == null, failure?.message)

            if (failure != null) {
                if (failedAction == null) {
                    failedAction = describe(action)
                    errorMessage = failure.message
                }
                if (automation.onError == OnError.STOP) break
            }
        }

        val status = if (failedAction == null) ExecutionStatus.EXECUTED else ExecutionStatus.FAILED
        val reason = when {
            failedAction == null -> "Se ejecutaron ${records.size} acciones"
            automation.onError == OnError.STOP -> "Falló \"$failedAction\" y se detuvo (onError = stop)"
            else -> "Falló \"$failedAction\" y siguió con las demás (onError = continue)"
        }
        return ExecutionResult(
            automationId = automation.id,
            status = status,
            reason = reason,
            timestamp = start,
            durationMillis = nowMillis() - start,
            executedActions = records,
            failedAction = failedAction,
            errorMessage = errorMessage,
        )
    }

    private suspend fun perform(
        action: Action,
        variables: Variables,
        chain: List<String>,
        results: MutableList<ExecutionResult>,
    ): ActionResult = try {
        when (action) {
            is Action.Delay -> {
                sleep(action.seconds * 1000)
                ActionResult.Success
            }
            is Action.RunAutomation -> runNested(action.automationId, chain, results)
            else -> executor.execute(variables.applyTo(action))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ActionResult.Failure(e.message ?: e::class.simpleName ?: "Error desconocido")
    }

    private suspend fun runNested(
        targetId: String,
        chain: List<String>,
        results: MutableList<ExecutionResult>,
    ): ActionResult {
        if (targetId in chain) {
            return ActionResult.Failure("Ciclo detectado: ${(chain + targetId).joinToString(" → ")}. Se detuvo para no repetirse sin fin.")
        }
        val target = source.find(targetId) ?: return ActionResult.Failure("No existe la automatización \"$targetId\"")
        val result = run(target, dedupKey = null, chain = chain + targetId, results)
        return when (result.status) {
            ExecutionStatus.FAILED -> ActionResult.Failure("\"${target.name}\" falló: ${result.errorMessage}")
            ExecutionStatus.SKIPPED_DISABLED, ExecutionStatus.SKIPPED_DUPLICATE ->
                ActionResult.Failure("\"${target.name}\" no se ejecutó: ${result.reason}")
            else -> ActionResult.Success
        }
    }

    private fun remember(key: String) {
        seenEvents += key
        while (seenEvents.size > MAX_SEEN_EVENTS) seenEvents.remove(seenEvents.first())
    }

    private fun nowMillis(): Long = clock.now().toInstant().toEpochMilli()

    private companion object {
        const val MAX_SEEN_EVENTS = 500

        fun describe(action: Action): String = when (action) {
            is Action.ShowNotification -> "Mostrar notificación \"${action.title}\""
            is Action.OpenApp -> "Abrir app ${action.packageName}"
            is Action.OpenUrl -> "Abrir enlace ${action.host}"
            is Action.Delay -> "Esperar ${action.seconds} s"
            is Action.RunAutomation -> "Ejecutar automatización ${action.automationId}"
            is Action.Flashlight -> if (action.on) "Encender linterna" else "Apagar linterna"
            is Action.SetVolume -> "Volumen de ${action.stream.label} al ${action.percent} %"
            is Action.SetRingerMode -> "Modo de sonido: ${action.mode.label}"
            is Action.DoNotDisturb -> "No molestar: ${action.mode.label}"
            is Action.SetBrightness -> if (action.auto) "Brillo automático" else "Brillo al ${action.percent} %"
            is Action.OpenSystemPanel -> "Abrir interruptor de ${action.panel.label}"
            is Action.WhatsAppMessage -> "Abrir WhatsApp con ${action.recipient}"
            is Action.DialNumber -> "Marcar a ${action.recipient}"
            is Action.Navigate -> "Navegar con ${action.app.label}"
            is Action.SendSms -> "Enviar SMS a ${action.recipient}"
        }
    }
}
