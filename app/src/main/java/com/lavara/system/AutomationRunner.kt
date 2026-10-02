package com.lavara.system

import com.lavara.automation.AutomationEngine
import com.lavara.automation.ExecutionResult
import com.lavara.automation.ExecutionStatus
import com.lavara.data.AutomationRepository
import com.lavara.data.RunRepository
import com.lavara.logging.AppLogger
import com.lavara.logging.LogExporter
import com.lavara.logging.LogLevel
import com.lavara.triggers.TriggerEvent
import kotlin.coroutines.cancellation.CancellationException

/** Pasa un evento al motor y deja todo en el historial y en los registros. */
class AutomationRunner(
    private val engine: AutomationEngine,
    private val automations: AutomationRepository,
    private val runs: RunRepository,
    private val logger: AppLogger,
) {
    suspend fun handle(event: TriggerEvent): List<ExecutionResult> {
        logger.info(SOURCE, "Evento: ${describe(event)}")
        val results = try {
            engine.handle(event)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(SOURCE, "El motor falló con el evento ${describe(event)}: ${e.message ?: e::class.simpleName}")
            return emptyList()
        }
        if (results.isEmpty()) logger.info(SOURCE, "Ninguna automatización activa responde a este evento")

        for (result in results) {
            runs.record(result)
            val name = automations.find(result.automationId)?.name ?: result.automationId
            val level = if (result.status == ExecutionStatus.FAILED) LogLevel.ERROR else LogLevel.INFO
            logger.log(level, SOURCE, "$name ${LogExporter.statusText(result.status.name)}: ${result.reason} (${result.durationMillis} ms)", result.automationId)
            for (action in result.executedActions.filter { !it.success }) {
                logger.error(SOURCE, "$name: falló \"${action.action}\": ${action.errorMessage}", result.automationId)
            }
        }
        return results
    }

    private fun describe(event: TriggerEvent): String = when (event) {
        is TriggerEvent.TimeReached -> "hora %02d:%02d".format(event.at.hour, event.at.minute)
        is TriggerEvent.BatteryChanged -> "batería ${event.previousLevel ?: "?"} % → ${event.level} %"
        is TriggerEvent.PowerChanged -> if (event.connected) "cargador conectado" else "cargador desconectado"
        is TriggerEvent.ManualRun -> "ejecución manual de ${event.automationId}"
    }

    private companion object {
        const val SOURCE = "Motor"
    }
}
