package com.lavara.logging

import com.lavara.core.Clock
import com.lavara.data.LogDao
import com.lavara.data.LogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

/** Escribe en Room sin trabar a quien registra; cada [TRIM_EVERY] registros borra los más viejos. */
class RoomLogger(
    private val dao: LogDao,
    private val scope: CoroutineScope,
    private val clock: Clock,
) : AppLogger {
    private val mutex = Mutex()
    private var writes = 0

    override fun log(level: LogLevel, source: String, message: String, automationId: String?) {
        val entry = LogEntity(
            timestamp = clock.now().toInstant().toEpochMilli(),
            level = level.name,
            source = source,
            message = message.take(MAX_MESSAGE),
            automationId = automationId,
        )
        scope.launch {
            // El orden de escritura respeta el orden de llamada.
            mutex.withLock {
                dao.insert(entry)
                if (++writes % TRIM_EVERY == 0) dao.trim(MAX_LOGS)
            }
        }
    }

    companion object {
        const val MAX_LOGS = 5_000
        const val TRIM_EVERY = 100
        const val MAX_MESSAGE = 2_000
    }
}
