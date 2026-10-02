package com.lavara.system.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.lavara.core.Clock
import com.lavara.data.AutomationRepository
import com.lavara.data.SettingsRepository
import com.lavara.logging.AppLogger
import com.lavara.triggers.NextAlarm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Programa UNA alarma: la más próxima entre todas las automatizaciones con hora.
 * Cuando suena, el motor procesa la hora y se programa la siguiente. Sin consultas continuas.
 */
class AlarmScheduler(
    private val context: Context,
    private val automations: AutomationRepository,
    private val settings: SettingsRepository,
    private val logger: AppLogger,
    private val clock: Clock,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()
    private val _next = MutableStateFlow<Long?>(null)

    /** Hora (milisegundos) de la próxima alarma programada, o null si no hay. */
    val next: StateFlow<Long?> = _next

    /** Android 12+ puede negar las alarmas exactas; La Vara declara USE_EXACT_ALARM, que se concede al instalar. */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Vuelve a calcular y programar la próxima alarma. [reason] queda en el registro. */
    suspend fun reschedule(reason: String) = mutex.withLock {
        val now = clock.now()
        warnIfMissed(now)

        val pending = pendingIntent(null)
        val upcoming = NextAlarm.earliest(automations.all(), now)
        if (upcoming == null) {
            alarmManager.cancel(pending)
            settings.set(KEY_NEXT, "")
            _next.value = null
            logger.info(SOURCE, "Sin alarmas: ninguna automatización activa tiene hora ($reason)")
            return@withLock
        }

        val (at, which) = upcoming
        val millis = at.toInstant().toEpochMilli()
        val intent = pendingIntent(millis)
        val names = which.joinToString(", ") { it.name }
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
            logger.info(SOURCE, "Alarma exacta programada para ${format(at)}: $names ($reason)")
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
            logger.warn(SOURCE, "Alarmas exactas no permitidas: programada aproximada para ${format(at)}, puede llegar tarde: $names ($reason)")
        }
        settings.set(KEY_NEXT, millis.toString())
        _next.value = millis
    }

    /** La alarma sonó: se olvida, para no confundirla con una alarma perdida. */
    suspend fun markFired() = settings.set(KEY_NEXT, "")

    // Si había una alarma guardada y ya pasó hace más de un minuto sin sonar, se perdió
    // (por ejemplo, el teléfono estaba apagado). Queda en el registro para el diagnóstico.
    private suspend fun warnIfMissed(now: ZonedDateTime) {
        val pendingMillis = settings.get(KEY_NEXT)?.toLongOrNull() ?: return
        if (pendingMillis < now.toInstant().toEpochMilli() - 60_000) {
            logger.warn(SOURCE, "La alarma de ${format(Instant.ofEpochMilli(pendingMillis).atZone(now.zone))} no sonó (teléfono apagado o app cerrada por Android)")
        }
    }

    private fun pendingIntent(millis: Long?): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION)
        if (millis != null) intent.putExtra(AlarmReceiver.EXTRA_AT, millis)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        private const val SOURCE = "Alarmas"
        private const val KEY_NEXT = "alarma_programada"
        private val formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")

        fun format(at: ZonedDateTime): String = at.format(formatter)
    }
}
