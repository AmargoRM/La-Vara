package com.lavara.automation

import com.lavara.actions.Action
import com.lavara.conditions.Condition
import com.lavara.triggers.Trigger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Una automatización: CUANDO [trigger], SI [conditions] (todas), HACER [actions] en orden.
 * Los campos nuevos que se agreguen en el futuro llevan siempre valor por defecto,
 * para que las automatizaciones guardadas sigan siendo legibles.
 */
@Serializable
data class Automation(
    val id: String,
    val name: String,
    val description: String = "",
    val enabled: Boolean = true,
    /** Si varias automatizaciones responden al mismo evento, primero corre la de número mayor. */
    val priority: Int = 0,
    val trigger: Trigger,
    val conditions: List<Condition> = emptyList(),
    val actions: List<Action> = emptyList(),
    val onError: OnError = OnError.STOP,
    /** Segundos mínimos entre dos ejecuciones. 0 = sin espera. */
    val cooldownSeconds: Long = 0,
    /** Fechas en milisegundos desde 1970 (UTC). */
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val lastExecutedAt: Long? = null,
    val executionCount: Long = 0,
    val failureCount: Long = 0,
) {
    init {
        require(id.isNotBlank()) { "id no puede estar vacío" }
        require(cooldownSeconds >= 0) { "cooldownSeconds no puede ser negativo, no $cooldownSeconds" }
    }
}

/** Qué hacer si una acción falla. */
@Serializable
enum class OnError {
    /** Registrar el error y seguir con la acción siguiente. */
    @SerialName("continue") CONTINUE,

    /** Registrar el error y no hacer las acciones que faltan. */
    @SerialName("stop") STOP,
}
