package com.lavara.logging

import com.lavara.core.Clock
import com.lavara.data.LogDao
import com.lavara.data.LogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

enum class LogLevel { INFO, WARN, ERROR }

/**
 * Registro estructurado hacia la tabla logs. El usuario no ve logcat: todo lo importante pasa por acá.
 * Nunca registrar tokens, contraseñas, headers de autenticación ni el contenido de notificaciones privadas.
 */
interface AppLogger {
    fun log(level: LogLevel, source: String, message: String, automationId: String? = null)

    fun info(source: String, message: String, automationId: String? = null) = log(LogLevel.INFO, source, message, automationId)
    fun warn(source: String, message: String, automationId: String? = null) = log(LogLevel.WARN, source, message, automationId)
    fun error(source: String, message: String, automationId: String? = null) = log(LogLevel.ERROR, source, message, automationId)
}

/**
 * Escribe en Room sin trabar a quien registra. Una sola corrutina escribe, en el orden en que se
 * llamó a [log]; cada [TRIM_EVERY] registros borra los más viejos.
 */
class RoomLogger(
    private val dao: LogDao,
    scope: CoroutineScope,
    private val clock: Clock,
) : AppLogger {
    private val queue = Channel<LogEntity>(Channel.UNLIMITED)

    init {
        scope.launch {
            var writes = 0
            for (entry in queue) {
                dao.insert(entry)
                if (++writes % TRIM_EVERY == 0) dao.trim(MAX_LOGS)
            }
        }
    }

    override fun log(level: LogLevel, source: String, message: String, automationId: String?) {
        queue.trySend(
            LogEntity(
                timestamp = clock.now().toInstant().toEpochMilli(),
                level = level.name,
                source = source,
                message = message.take(MAX_MESSAGE),
                automationId = automationId,
            ),
        )
    }

    /** Para tests: deja de aceptar registros; la escritura termina cuando vacía la cola. */
    fun close() {
        queue.close()
    }

    companion object {
        const val MAX_LOGS = 5_000
        const val TRIM_EVERY = 100
        const val MAX_MESSAGE = 2_000
    }
}
