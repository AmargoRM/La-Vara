package com.lavara.logging

import com.lavara.automation.ExecutionStatus
import com.lavara.data.AutomationRunEntity
import com.lavara.data.LogEntity
import com.lavara.data.RunRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Arma el texto de "Exportar logs", pensado para pegárselo a Claude. Lógica pura. */
object LogExporter {
    private val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun build(
        header: String,
        runs: List<AutomationRunEntity>,
        logs: List<LogEntity>,
        zone: ZoneId,
        maxChars: Int = 200_000,
    ): String {
        val text = buildString {
            appendLine(header)
            appendLine()
            appendLine("== EJECUCIONES (más nuevas primero) ==")
            if (runs.isEmpty()) appendLine("(ninguna)")
            for (run in runs) {
                appendLine("${time(run.startedAt, zone)} ${run.automationName} [${run.automationId}] ${statusText(run.status)}: ${run.reason} (${run.durationMillis} ms)")
                for (action in RunRepository.decodeActions(run.actions)) {
                    val result = if (action.success) "ok" else "ERROR ${action.errorMessage}"
                    appendLine("    ${action.index + 1}. ${action.action}: $result (${action.durationMillis} ms)")
                }
            }
            appendLine()
            appendLine("== REGISTROS (más nuevos primero) ==")
            if (logs.isEmpty()) appendLine("(ninguno)")
            for (log in logs) {
                val id = log.automationId?.let { " [$it]" } ?: ""
                appendLine("${time(log.timestamp, zone)} ${log.level} ${log.source}$id: ${log.message}")
            }
        }
        return if (text.length <= maxChars) text else text.take(maxChars) + "\n… (recortado)"
    }

    fun time(millis: Long, zone: ZoneId): String = Instant.ofEpochMilli(millis).atZone(zone).format(format)

    fun statusText(status: String): String = when (runCatching { ExecutionStatus.valueOf(status) }.getOrNull()) {
        ExecutionStatus.EXECUTED -> "se ejecutó"
        ExecutionStatus.FAILED -> "falló"
        ExecutionStatus.SKIPPED_CONDITIONS -> "no se ejecutó"
        ExecutionStatus.SKIPPED_COOLDOWN -> "no se ejecutó (cooldown)"
        ExecutionStatus.SKIPPED_DUPLICATE -> "no se ejecutó (repetido)"
        ExecutionStatus.SKIPPED_DISABLED -> "no se ejecutó (desactivada)"
        ExecutionStatus.WAITING_UNLOCK -> "espera el desbloqueo"
        null -> status
    }
}
