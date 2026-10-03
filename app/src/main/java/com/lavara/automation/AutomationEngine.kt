package com.lavara.automation

import com.lavara.actions.Action
import com.lavara.actions.ActionExecutor
import com.lavara.actions.ActionResult
import com.lavara.actions.needsUnlock
import com.lavara.actions.recipient
import com.lavara.actions.waitText
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
    /**
     * Programa la continuación de una espera larga (más de [INLINE_DELAY_SECONDS]). Si es null, el motor
     * espera ahí mismo, como antes.
     */
    private val later: LaterScheduler? = null,
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
     * Sigue una automatización que quedó en una espera larga, desde la acción [fromAction]. No vuelve a
     * revisar condiciones ni cooldown: eso se revisó cuando arrancó.
     */
    suspend fun resume(automationId: String, fromAction: Int): ExecutionResult {
        val start = nowMillis()
        val automation = source.find(automationId)
            ?: return ExecutionResult(automationId, ExecutionStatus.SKIPPED_DISABLED, "La automatización ya no existe", start, 0)
        if (!automation.enabled) {
            return ExecutionResult(automationId, ExecutionStatus.SKIPPED_DISABLED, "Se desactivó durante la espera: no sigue", start, 0)
        }
        val busy = mutex.withLock {
            (automation.id in running).also { if (!it) running += automation.id }
        }
        if (busy) return ExecutionResult(automationId, ExecutionStatus.SKIPPED_DUPLICATE, "Ya se está ejecutando", start, 0)
        try {
            return executeActions(automation, listOf(automation.id), start, mutableListOf(), from = fromAction)
        } finally {
            mutex.withLock { running -= automation.id }
        }
    }

    /**
     * Hace la parte que quedó esperando el desbloqueo (ver [LockedSplit]): solo lo que abre apps o toca
     * botones, con las esperas que hay entre esas acciones. No vuelve a revisar condiciones ni cooldown.
     */
    suspend fun runAfterUnlock(automationId: String): ExecutionResult {
        val start = nowMillis()
        val automation = source.find(automationId)
            ?: return ExecutionResult(automationId, ExecutionStatus.SKIPPED_DISABLED, "La automatización ya no existe", start, 0)
        if (!automation.enabled) {
            return ExecutionResult(automationId, ExecutionStatus.SKIPPED_DISABLED, "Se desactivó mientras esperaba el desbloqueo", start, 0)
        }
        val part = LockedSplit.of(automation.actions)?.afterUnlock
            ?: return ExecutionResult(automationId, ExecutionStatus.SKIPPED_CONDITIONS, "Se editó mientras esperaba: ya no tiene una parte que espere el desbloqueo", start, 0)
        val busy = mutex.withLock {
            (automation.id in running).also { if (!it) running += automation.id }
        }
        if (busy) return ExecutionResult(automationId, ExecutionStatus.SKIPPED_DUPLICATE, "Ya se está ejecutando", start, 0)
        try {
            val result = executeActions(automation, listOf(automation.id), start, mutableListOf(), only = part)
            return result.copy(reason = "Al desbloquear: " + result.reason.replaceFirstChar { it.lowercase() })
        } finally {
            mutex.withLock { running -= automation.id }
        }
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

        // Abrir apps o tocar botones con el teléfono bloqueado no sirve. Si tiene acciones que no necesitan
        // pantalla (volumen, música…), esas corren ya y el resto al desbloquear; si no, se ejecuta entera al
        // desbloquear, sin contar para el cooldown. El mismo evento no la vuelve a poner en espera.
        var split: LockedSplit? = null
        if (dedupKey != null && automation.actions.any { it.needsUnlock() } && deviceState.isLocked()) {
            split = LockedSplit.of(automation.actions)
            if (split == null) {
                mutex.withLock { remember("${automation.id}|$dedupKey") }
                return skipped(ExecutionStatus.WAITING_UNLOCK, "El teléfono está bloqueado: se ejecuta apenas lo desbloquees")
            }
        }

        mutex.withLock {
            running += automation.id
            lastRunMillis[automation.id] = start
            if (dedupKey != null) remember("${automation.id}|$dedupKey")
        }
        try {
            var result = executeActions(automation, chain, start, results, only = split?.now)
            if (split != null) {
                val stopped = result.status == ExecutionStatus.FAILED && automation.onError == OnError.STOP
                val waiting = split.afterUnlock.count { !(automation.actions[it] is Action.Delay) }
                result = if (stopped) result else result.copy(
                    reason = result.reason + "; " + (if (waiting == 1) "1 espera" else "$waiting esperan") + " el desbloqueo (teléfono bloqueado)",
                    waitingUnlock = true,
                )
            }
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
        from: Int = 0,
        /** Si no es null, solo hace las acciones con estos números (ver [LockedSplit]). */
        only: Set<Int>? = null,
    ): ExecutionResult {
        val variables = Variables.from(clock.now(), deviceState.batteryLevel())
        val records = mutableListOf<ActionRecord>()
        var failedAction: String? = null
        var errorMessage: String? = null
        var continuesAt: Long? = null

        for ((index, action) in automation.actions.withIndex()) {
            if (index < from || (only != null && index !in only)) continue
            // Espera larga: no se queda despierto esperando; el resto sigue con una alarma.
            if (action is Action.Delay && action.seconds > INLINE_DELAY_SECONDS && later != null) {
                if (index == automation.actions.lastIndex) break
                val at = nowMillis() + action.seconds * 1000
                later.schedule(automation.id, index + 1, at)
                records += ActionRecord(index, describe(action), 0, true, null)
                continuesAt = at
                break
            }
            val actionStart = nowMillis()
            var label = describe(action)
            val outcome = if (action is Action.IfElse) {
                // Se evalúa una sola vez: el historial dice qué camino tomó.
                val passed = ifPasses(action)
                label += if (passed) " → se cumple" else " → no se cumple"
                runBranch(if (passed) action.then else action.otherwise, variables, chain, results, automation.onError)
            } else {
                perform(action, variables, chain, results, automation.onError)
            }
            val failure = outcome as? ActionResult.Failure
            records += ActionRecord(index, label, nowMillis() - actionStart, failure == null, failure?.message)

            if (failure != null) {
                if (failedAction == null) {
                    failedAction = label
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
        } + (continuesAt?.let { "; las demás siguen a las ${clockText(it)}" } ?: "")
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
        onError: OnError,
    ): ActionResult = try {
        when (action) {
            is Action.Delay -> {
                sleep(action.seconds * 1000)
                ActionResult.Success
            }
            is Action.IfElse -> runBranch(if (ifPasses(action)) action.then else action.otherwise, variables, chain, results, onError)
            is Action.RunAutomation -> runNested(action.automationId, chain, results)
            else -> executor.execute(variables.applyTo(action))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ActionResult.Failure(e.message ?: e::class.simpleName ?: "Error desconocido")
    }

    private fun ifPasses(action: Action.IfElse): Boolean = when {
        action.conditions.isEmpty() -> true
        action.matchAll -> action.conditions.all { evaluator.evaluate(it) }
        else -> action.conditions.any { evaluator.evaluate(it) }
    }

    /** Hace las acciones de un camino del "si". Devuelve la primera falla, o Success. */
    private suspend fun runBranch(
        actions: List<Action>,
        variables: Variables,
        chain: List<String>,
        results: MutableList<ExecutionResult>,
        onError: OnError,
    ): ActionResult {
        var first: ActionResult.Failure? = null
        for (inner in actions) {
            // Las esperas largas siguen con una alarma solo en la lista principal; adentro de un "si" no.
            val outcome = if (inner is Action.Delay && inner.seconds > INLINE_DELAY_SECONDS) {
                ActionResult.Failure("Dentro de un \"si\" la espera máxima es de $INLINE_DELAY_SECONDS s")
            } else {
                perform(inner, variables, chain, results, onError)
            }
            if (outcome is ActionResult.Failure) {
                if (first == null) first = ActionResult.Failure("${describe(inner)}: ${outcome.message}")
                if (onError == OnError.STOP) break
            }
        }
        return first ?: ActionResult.Success
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

    private fun clockText(millis: Long): String {
        val at = java.time.Instant.ofEpochMilli(millis).atZone(clock.now().zone)
        val day = if (at.toLocalDate() == clock.now().toLocalDate()) "" else " del %02d/%02d".format(at.dayOfMonth, at.monthValue)
        return "%02d:%02d%s".format(at.hour, at.minute, day)
    }

    companion object {
        private const val MAX_SEEN_EVENTS = 500

        /** Esperas de hasta este tiempo se hacen ahí mismo; las más largas siguen con una alarma. */
        const val INLINE_DELAY_SECONDS = 10L

        private fun describe(action: Action): String = when (action) {
            is Action.Vibrate -> "Vibrar ${action.millis} ms"
            is Action.CopyToClipboard -> "Copiar texto al portapapeles"
            is Action.ShareText -> "Compartir texto"
            is Action.MediaControl -> "Música: ${action.command.label}"
            is Action.IfElse -> "Si (${action.conditions.size} condiciones): ${action.then.size} acciones; si no: ${action.otherwise.size}"
            is Action.ShowNotification -> "Mostrar notificación \"${action.title}\""
            is Action.OpenApp -> "Abrir app ${action.packageName}"
            is Action.OpenUrl -> "Abrir enlace ${action.host}"
            is Action.Delay -> "Esperar ${waitText(action.seconds)}"
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
            is Action.TapInApp -> "Tocar \"${action.button}\" en ${action.packageName}"
        }
    }
}
