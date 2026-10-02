package com.lavara.system.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lavara.LaVaraApp
import com.lavara.triggers.TriggerEvent
import kotlinx.coroutines.launch
import java.time.Instant

/** Sonó la alarma: el motor procesa esa hora y se programa la siguiente. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val container = (context.applicationContext as LaVaraApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.alarmScheduler.markFired()
                val now = container.clock.now()
                val atMillis = intent.getLongExtra(EXTRA_AT, -1L)
                // Se usa la hora programada, no la actual: si Android la entrega tarde, igual corresponde a esa hora.
                val scheduled = if (atMillis > 0) Instant.ofEpochMilli(atMillis).atZone(now.zone) else now
                val lateSeconds = (now.toInstant().toEpochMilli() - scheduled.toInstant().toEpochMilli()) / 1000
                container.logger.info("Alarmas", "Sonó la alarma de las ${AlarmScheduler.format(scheduled)} ($lateSeconds s tarde)")
                container.automationRunner.handle(TriggerEvent.TimeReached(scheduled))
            } finally {
                container.alarmScheduler.reschedule("después de la alarma")
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.lavara.ALARMA"
        const val EXTRA_AT = "hora_programada"
    }
}
