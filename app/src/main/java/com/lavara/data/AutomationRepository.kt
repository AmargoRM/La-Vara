package com.lavara.data

import com.lavara.automation.Automation
import com.lavara.automation.AutomationJson
import com.lavara.automation.AutomationSource
import com.lavara.logging.AppLogger
import com.lavara.triggers.Trigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Guarda y lee automatizaciones. Es la fuente del motor ([AutomationSource]). */
class AutomationRepository(
    private val dao: AutomationDao,
    private val logger: AppLogger,
) : AutomationSource {

    override suspend fun all(): List<Automation> = dao.all().mapNotNull { decode(it) }

    override suspend fun find(id: String): Automation? = dao.find(id)?.let { decode(it) }

    fun observeAll(): Flow<List<Automation>> = dao.observeAll().map { list -> list.mapNotNull { decode(it) } }

    suspend fun save(automation: Automation) = dao.upsert(toEntity(automation))

    suspend fun delete(id: String) = dao.delete(id)

    /** Una automatización que no se puede leer no frena a las demás: se salta y queda en el log. */
    private suspend fun decode(entity: AutomationEntity): Automation? =
        try {
            AutomationJson.decode(entity.definition).copy(
                // Los contadores se actualizan en las columnas; mandan sobre el JSON.
                lastExecutedAt = entity.lastExecutedAt,
                executionCount = entity.executionCount,
                failureCount = entity.failureCount,
            )
        } catch (e: IllegalArgumentException) {
            logger.error("Automatizaciones", "No se pudo leer \"${entity.name}\": ${e.message}", entity.id)
            null
        }

    companion object {
        fun toEntity(automation: Automation) = AutomationEntity(
            id = automation.id,
            name = automation.name,
            enabled = automation.enabled,
            priority = automation.priority,
            triggerType = triggerType(automation.trigger),
            definition = AutomationJson.encode(automation),
            createdAt = automation.createdAt,
            updatedAt = automation.updatedAt,
            lastExecutedAt = automation.lastExecutedAt,
            executionCount = automation.executionCount,
            failureCount = automation.failureCount,
        )

        fun triggerType(trigger: Trigger): String = when (trigger) {
            is Trigger.Time -> "time"
            is Trigger.Battery -> "battery"
            is Trigger.Power -> "power"
            is Trigger.Location -> "location"
            is Trigger.Bluetooth -> "bluetooth"
            is Trigger.Wifi -> "wifi"
            is Trigger.Notification -> "notification"
            is Trigger.Nfc -> "nfc"
            Trigger.Manual -> "manual"
        }
    }
}
