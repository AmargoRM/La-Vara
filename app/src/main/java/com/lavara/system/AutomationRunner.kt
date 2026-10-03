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
    /**
     * Recibe las automatizaciones que quedaron esperando el desbloqueo: [whole] esperan enteras; [rest] ya
     * hicieron lo que no necesita pantalla y solo esperan lo que abre apps o toca botones.
     */
    private val onWaitingUnlock: suspend (whole: List<String>, rest: List<String>) -> Unit = { _, _ -> },
    /** Recibe cada automatización que falló (id, nombre, motivo), para avisar al usuario. */
    private val onFailed: (id: String, name: String, reason: String) -> Unit = { _, _, _ -> },
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
        record(results)
        val whole = results.filter { it.status == ExecutionStatus.WAITING_UNLOCK }.map { it.automationId }
        val rest = results.filter { it.waitingUnlock }.map { it.automationId }
        if (whole.isNotEmpty() || rest.isNotEmpty()) onWaitingUnlock(whole, rest)
        return results
    }

    /** Hace la parte de [automationId] que esperaba el desbloqueo (lo que abre apps o toca botones). */
    suspend fun runAfterUnlock(automationId: String): ExecutionResult? {
        val result = try {
            engine.runAfterUnlock(automationId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(SOURCE, "El motor falló al seguir $automationId tras el desbloqueo: ${e.message ?: e::class.simpleName}", automationId)
            return null
        }
        record(listOf(result))
        return result
    }

    /** Sigue una automatización que estaba en una espera larga, desde la acción [fromAction]. */
    suspend fun resume(automationId: String, fromAction: Int): ExecutionResult? {
        val name = automations.find(automationId)?.name ?: automationId
        logger.info(SOURCE, "Terminó la espera de $name: sigue desde la acción ${fromAction + 1}", automationId)
        val result = try {
            engine.resume(automationId, fromAction)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(SOURCE, "El motor falló al seguir $name: ${e.message ?: e::class.simpleName}", automationId)
            return null
        }
        record(listOf(result))
        return result
    }

    private suspend fun record(results: List<ExecutionResult>) {
        for (result in results) {
            runs.record(result)
            val name = automations.find(result.automationId)?.name ?: result.automationId
            val level = if (result.status == ExecutionStatus.FAILED) LogLevel.ERROR else LogLevel.INFO
            logger.log(level, SOURCE, "$name ${LogExporter.statusText(result.status.name)}: ${result.reason} (${result.durationMillis} ms)", result.automationId)
            for (action in result.executedActions.filter { !it.success }) {
                logger.error(SOURCE, "$name: falló \"${action.action}\": ${action.errorMessage}", result.automationId)
            }
            if (result.status == ExecutionStatus.FAILED) {
                onFailed(result.automationId, name, result.errorMessage?.let { "${result.failedAction}: $it" } ?: result.reason)
            }
        }
    }

    private fun describe(event: TriggerEvent): String = when (event) {
        is TriggerEvent.TimeReached -> "hora %02d:%02d".format(event.at.hour, event.at.minute)
        is TriggerEvent.BatteryChanged -> "batería ${event.previousLevel ?: "?"} % → ${event.level} %"
        is TriggerEvent.PowerChanged -> if (event.connected) "cargador conectado" else "cargador desconectado"
        is TriggerEvent.LocationChanged -> (if (event.entered) "entrada a la zona de " else "salida de la zona de ") + event.automationId
        is TriggerEvent.BluetoothChanged -> "Bluetooth ${event.name.ifBlank { event.address }} " + if (event.connected) "conectado" else "desconectado"
        is TriggerEvent.WifiChanged -> "Wi-Fi ${event.ssid ?: "(nombre desconocido)"} " + if (event.connected) "conectado" else "desconectado"
        // Solo la app: el contenido de las notificaciones de otras apps nunca va a los registros.
        is TriggerEvent.NotificationPosted -> "notificación de ${event.packageName}"
        is TriggerEvent.NfcTagScanned -> "etiqueta NFC ${event.tagId}"
        is TriggerEvent.ManualRun -> "ejecución manual de ${event.automationId}"
    }

    private companion object {
        const val SOURCE = "Motor"
    }
}
