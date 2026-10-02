package com.lavara.data

import androidx.room.withTransaction
import com.lavara.automation.ActionRecord
import com.lavara.automation.ExecutionResult
import com.lavara.automation.ExecutionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Historial de ejecuciones. Guardar un resultado también actualiza los contadores de la automatización. */
class RunRepository(private val db: LaVaraDatabase) {
    private val runs = db.runDao()
    private val automations = db.automationDao()

    suspend fun record(result: ExecutionResult) {
        db.withTransaction {
            val automation = automations.find(result.automationId)
            runs.insert(
                AutomationRunEntity(
                    automationId = result.automationId,
                    automationName = automation?.name ?: result.automationId,
                    status = result.status.name,
                    reason = result.reason,
                    startedAt = result.timestamp,
                    durationMillis = result.durationMillis,
                    actions = json.encodeToString(actionsSerializer, result.executedActions),
                    failedAction = result.failedAction,
                    errorMessage = result.errorMessage,
                ),
            )
            val ran = result.status == ExecutionStatus.EXECUTED || result.status == ExecutionStatus.FAILED
            if (automation != null && ran) {
                automations.upsert(
                    automation.copy(
                        lastExecutedAt = result.timestamp,
                        executionCount = automation.executionCount + 1,
                        failureCount = automation.failureCount + if (result.status == ExecutionStatus.FAILED) 1 else 0,
                    ),
                )
            }
            runs.trim(MAX_RUNS)
        }
    }

    fun observeLatest(limit: Int = 300): Flow<List<AutomationRunEntity>> = runs.observeLatest(limit)

    suspend fun latest(limit: Int): List<AutomationRunEntity> = runs.latest(limit)

    companion object {
        const val MAX_RUNS = 2_000

        private val json = Json { ignoreUnknownKeys = true }
        private val actionsSerializer = ListSerializer(ActionRecord.serializer())

        fun decodeActions(text: String): List<ActionRecord> =
            runCatching { json.decodeFromString(actionsSerializer, text) }.getOrDefault(emptyList())
    }
}
