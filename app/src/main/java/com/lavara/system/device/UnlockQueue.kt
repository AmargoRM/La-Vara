package com.lavara.system.device

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.lavara.core.Clock
import com.lavara.data.AutomationRepository
import com.lavara.data.SettingsRepository
import com.lavara.logging.AppLogger
import com.lavara.system.AutomationRunner
import com.lavara.triggers.TriggerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Automatizaciones que abren algo en pantalla y se dispararon con el teléfono bloqueado. Android no deja a
 * ninguna app saltarse el PIN, así que esperan: apenas el usuario desbloquea, se ejecutan enteras, sin
 * notificación de "Tocá para abrir". Mientras esperan, [UnlockWaitService] mantiene viva a La Vara.
 * Se guardan en settings para no perderlas si Android cierra la app; las de más de [MAX_WAIT_MILLIS] se descartan.
 */
class UnlockQueue(
    private val context: Context,
    private val settings: SettingsRepository,
    private val automations: AutomationRepository,
    private val logger: AppLogger,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val runner: () -> AutomationRunner,
    private val canPrompt: () -> Boolean,
) {
    private val mutex = Mutex()
    private var fallbackReceiver: BroadcastReceiver? = null

    /** Pone en espera [ids], enciende la pantalla para pedir el desbloqueo y espera a que el usuario desbloquee. */
    suspend fun wait(ids: List<String>) {
        if (ids.isEmpty()) return
        val now = clock.now().toInstant().toEpochMilli()
        mutex.withLock {
            val pending = read().filterKeys { it !in ids } + ids.associateWith { now }
            write(pending)
        }
        val names = ids.map { automations.find(it)?.name ?: it }
        logger.info(SOURCE, "Teléfono bloqueado: ${names.joinToString()} espera a que lo desbloquees.")
        if (!UnlockWaitService.start(context)) {
            logger.warn(SOURCE, "Android no dejó encender la espera con La Vara en segundo plano. Se intenta igual mientras La Vara siga abierta; quitá el ahorro de batería para que no pase.")
            listenWhileAlive()
        }
        // Enciende la pantalla y muestra el pedido de desbloqueo (necesita "Mostrar sobre otras apps" o Accesibilidad).
        if (canPrompt()) {
            try {
                context.startActivity(UnlockAndOpenActivity.prompt(context))
            } catch (e: RuntimeException) {
                logger.warn(SOURCE, "No se pudo encender la pantalla para pedir el desbloqueo: ${e.message}")
            }
        }
    }

    suspend fun hasPending(): Boolean = mutex.withLock { read().isNotEmpty() }

    /** El usuario desbloqueó: ejecuta lo que esperaba. Si el teléfono sigue bloqueado, no hace nada. */
    suspend fun runPending(reason: String) {
        if (context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return
        val now = clock.now().toInstant().toEpochMilli()
        val due = mutex.withLock {
            val all = read()
            write(emptyMap())
            all
        }
        for ((id, since) in due) {
            val name = automations.find(id)?.name ?: id
            if (now - since > MAX_WAIT_MILLIS) {
                logger.warn(SOURCE, "$name no se ejecutó: esperó más de ${MAX_WAIT_MILLIS / 3_600_000} horas al desbloqueo.", id)
                continue
            }
            logger.info(SOURCE, "Desbloqueado ($reason): se ejecuta $name, que esperaba desde hace ${(now - since) / 1000} s.", id)
            runner().handle(TriggerEvent.ManualRun(id, "desbloqueo-" + UUID.randomUUID()))
        }
    }

    /** Sin el servicio, escucha el desbloqueo solo mientras Android no cierre La Vara. */
    private fun listenWhileAlive() {
        if (fallbackReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                scope.launch {
                    runPending("aviso del sistema")
                    if (!hasPending()) stopListening()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(context.applicationContext, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        fallbackReceiver = receiver
    }

    private fun stopListening() {
        fallbackReceiver?.let { runCatching { context.applicationContext.unregisterReceiver(it) } }
        fallbackReceiver = null
    }

    private suspend fun read(): Map<String, Long> =
        settings.get(KEY).orEmpty().lines().mapNotNull { line ->
            val parts = line.split('|')
            val since = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            parts[0] to since
        }.toMap()

    private suspend fun write(pending: Map<String, Long>) =
        settings.set(KEY, pending.entries.joinToString("\n") { "${it.key}|${it.value}" })

    private companion object {
        const val SOURCE = "Desbloqueo"
        const val KEY = "pendientes_desbloqueo"
        const val MAX_WAIT_MILLIS = 2 * 3_600_000L
    }
}
