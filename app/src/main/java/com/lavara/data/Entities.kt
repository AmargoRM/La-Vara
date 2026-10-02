package com.lavara.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Una automatización guardada. Las columnas sueltas son un resumen para listar y ordenar;
 * la verdad completa está en [definition], el mismo JSON de docs/FORMATO_JSON.md.
 */
@Entity(tableName = "automations")
data class AutomationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val enabled: Boolean,
    val priority: Int,
    @ColumnInfo(name = "trigger_type") val triggerType: String,
    val definition: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "last_executed_at") val lastExecutedAt: Long?,
    @ColumnInfo(name = "execution_count") val executionCount: Long,
    @ColumnInfo(name = "failure_count") val failureCount: Long,
)

/** Una vez que una automatización se evaluó: se ejecutó, falló o no corrió (con el motivo). */
@Entity(
    tableName = "automation_runs",
    indices = [Index("automation_id"), Index("started_at")],
)
data class AutomationRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "automation_id") val automationId: String,
    /** Nombre en el momento de ejecutar: sigue legible aunque después se borre la automatización. */
    @ColumnInfo(name = "automation_name") val automationName: String,
    val status: String,
    val reason: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "duration_millis") val durationMillis: Long,
    /** Lista de ActionRecord en JSON. */
    val actions: String,
    @ColumnInfo(name = "failed_action") val failedAction: String?,
    @ColumnInfo(name = "error_message") val errorMessage: String?,
)

/** Un registro (log). Es la única herramienta de diagnóstico del usuario: no hay logcat. */
@Entity(
    tableName = "logs",
    indices = [Index("timestamp"), Index("automation_id")],
)
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val level: String,
    val source: String,
    val message: String,
    @ColumnInfo(name = "automation_id") val automationId: String? = null,
)

/** Ajustes sueltos de la app, como pares nombre → valor. */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
