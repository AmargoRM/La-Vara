package com.lavara.automation

/** Qué pasó con una automatización ante un evento. Es lo que se guardará en el historial (S2). */
data class ExecutionResult(
    val automationId: String,
    val status: ExecutionStatus,
    /** Explicación en español de por qué se ejecutó o no. */
    val reason: String,
    /** Milisegundos desde 1970 (UTC) en que empezó. */
    val timestamp: Long,
    val durationMillis: Long,
    val executedActions: List<ActionRecord> = emptyList(),
    /** Primera acción que falló, en texto, o null. */
    val failedAction: String? = null,
    val errorMessage: String? = null,
) {
    /** true solo si se ejecutó y ninguna acción falló. */
    val success: Boolean get() = status == ExecutionStatus.EXECUTED
}

enum class ExecutionStatus {
    /** Se hicieron todas las acciones sin errores. */
    EXECUTED,

    /** Se ejecutó, pero al menos una acción falló. */
    FAILED,

    /** No se ejecutó porque una condición no se cumplió. */
    SKIPPED_CONDITIONS,

    /** No se ejecutó porque no pasó el tiempo mínimo entre ejecuciones. */
    SKIPPED_COOLDOWN,

    /** No se ejecutó porque el mismo evento ya la había disparado, o ya estaba corriendo. */
    SKIPPED_DUPLICATE,

    /** No se ejecutó porque está desactivada (solo al pedirla por RunAutomation o a mano). */
    SKIPPED_DISABLED,

    /**
     * Abre algo en pantalla y el teléfono estaba bloqueado: queda esperando y se ejecuta entera apenas el
     * usuario desbloquea (lo hace la parte Android, no el motor).
     */
    WAITING_UNLOCK,
}

/** Una acción hecha: cuál, cuánto tardó y si salió bien. Se guarda como JSON en el historial. */
@kotlinx.serialization.Serializable
data class ActionRecord(
    val index: Int,
    val action: String,
    val durationMillis: Long,
    val success: Boolean,
    val errorMessage: String? = null,
)
