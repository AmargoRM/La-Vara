package com.lavara.triggers

import com.lavara.automation.Automation
import com.lavara.core.TimeText
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Calcula cuándo tiene que sonar la próxima alarma. Lógica pura, sin Android. */
object NextAlarm {

    /** Próxima vez, estrictamente después de [now], en que se cumple [trigger]. */
    fun after(trigger: Trigger.Time, now: ZonedDateTime): ZonedDateTime {
        val time = TimeText.parseOrNull(trigger.time)!!
        val start = now.truncatedTo(ChronoUnit.MINUTES)
        // Como mucho 8 días: alcanza para encontrar cualquier día de la semana.
        for (dayOffset in 0..8) {
            val day = start.toLocalDate().plusDays(dayOffset.toLong())
            // Si esa hora no existe ese día (cambio de horario), Java la corre a la hora válida siguiente.
            val candidate = day.atTime(time).atZone(now.zone)
            val dayOk = trigger.days.isEmpty() || Weekday.of(candidate.dayOfWeek) in trigger.days
            if (dayOk && candidate.isAfter(now)) return candidate
        }
        error("No se encontró la próxima hora para ${trigger.time} ${trigger.days}")
    }

    /** La alarma más próxima entre todas las automatizaciones activas con trigger de hora, o null. */
    fun earliest(automations: List<Automation>, now: ZonedDateTime): Pair<ZonedDateTime, List<Automation>>? {
        val upcoming = automations
            .filter { it.enabled && it.trigger is Trigger.Time }
            .map { it to after(it.trigger as Trigger.Time, now) }
        val first = upcoming.minOfOrNull { it.second } ?: return null
        return first to upcoming.filter { it.second == first }.map { it.first }
    }
}
