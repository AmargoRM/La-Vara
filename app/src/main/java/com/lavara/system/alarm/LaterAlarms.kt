package com.lavara.system.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lavara.automation.LaterScheduler
import com.lavara.core.Clock
import com.lavara.data.AutomationRepository
import com.lavara.data.SettingsRepository
import com.lavara.logging.AppLogger
import com.lavara.system.AutomationRunner
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID

/**
 * Esperas largas ("Esperar 10 min"): en vez de dejar a La Vara despierta, el resto de la automatización
 * sigue con una alarma exacta. Cada espera se guarda en settings para volver a programarla si el teléfono
 * se reinicia; si su hora pasó con el teléfono apagado, sigue apenas se pueda y queda anotado.
 */
class LaterAlarms(
    private val context: Context,
    private val settings: SettingsRepository,
    private val automations: AutomationRepository,
    private val logger: AppLogger,
    private val clock: Clock,
    private val runner: () -> AutomationRunner,
) : LaterScheduler {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()

    private data class Entry(val id: String, val automationId: String, val fromAction: Int, val atMillis: Long)

    override suspend fun schedule(automationId: String, fromAction: Int, atMillis: Long) {
        val entry = Entry(UUID.randomUUID().toString().take(12), automationId, fromAction, atMillis)
        mutex.withLock { write(read() + entry) }
        set(entry)
        val name = automations.find(automationId)?.name ?: automationId
        logger.info(SOURCE, "$name sigue ${AlarmScheduler.format(Instant.ofEpochMilli(atMillis).atZone(clock.now().zone))} (alarma programada)", automationId)
    }

    /** Sonó la alarma de la espera [entryId]: sigue la automatización. */
    suspend fun fire(entryId: String) {
        val entry = mutex.withLock {
            val all = read()
            all.firstOrNull { it.id == entryId }?.also { write(all - it) }
        } ?: return // Ya se siguió (por ejemplo, al reiniciar).
        val lateSeconds = (clock.now().toInstant().toEpochMilli() - entry.atMillis) / 1000
        if (lateSeconds > 60) logger.warn(SOURCE, "La espera terminó con $lateSeconds s de atraso (teléfono apagado o Android la demoró)", entry.automationId)
        runner().resume(entry.automationId, entry.fromAction)
    }

    /** Después de reiniciar o de cambiar la hora: vuelve a programar las esperas y sigue las vencidas. */
    suspend fun sync(reason: String) {
        val now = clock.now().toInstant().toEpochMilli()
        val all = mutex.withLock { read() }
        if (all.isEmpty()) return
        val (due, upcoming) = all.partition { it.atMillis <= now }
        upcoming.forEach { set(it) }
        if (upcoming.isNotEmpty()) logger.info(SOURCE, "${upcoming.size} esperas largas reprogramadas ($reason)")
        due.forEach { fire(it.id) }
    }

    /** Si una automatización se borra o se edita, sus esperas pendientes se olvidan. */
    suspend fun cancel(automationId: String) {
        val removed = mutex.withLock {
            val all = read()
            all.filter { it.automationId == automationId }.also { write(all - it.toSet()) }
        }
        removed.forEach { alarmManager.cancel(pendingIntent(it)) }
        if (removed.isNotEmpty()) logger.info(SOURCE, "Se cancelaron ${removed.size} esperas pendientes", automationId)
    }

    private fun set(entry: Entry) {
        val intent = pendingIntent(entry)
        if (Build.canScheduleExact(alarmManager)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entry.atMillis, intent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entry.atMillis, intent)
        }
    }

    private fun pendingIntent(entry: Entry): PendingIntent {
        val intent = Intent(context, LaterReceiver::class.java)
            .setAction(LaterReceiver.ACTION)
            // La dirección distingue cada espera, para que una alarma no reemplace a otra.
            .setData(Uri.parse("lavara://espera/${entry.id}"))
            .putExtra(LaterReceiver.EXTRA_ID, entry.id)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private suspend fun read(): List<Entry> =
        settings.get(KEY).orEmpty().lines().mapNotNull { line ->
            val p = line.split('|')
            if (p.size != 4) return@mapNotNull null
            Entry(p[0], p[1], p[2].toIntOrNull() ?: return@mapNotNull null, p[3].toLongOrNull() ?: return@mapNotNull null)
        }

    private suspend fun write(entries: List<Entry>) =
        settings.set(KEY, entries.joinToString("\n") { "${it.id}|${it.automationId}|${it.fromAction}|${it.atMillis}" })

    private object Build {
        fun canScheduleExact(manager: AlarmManager): Boolean =
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
    }

    private companion object {
        const val SOURCE = "Esperas"
        const val KEY = "esperas_largas"
    }
}
