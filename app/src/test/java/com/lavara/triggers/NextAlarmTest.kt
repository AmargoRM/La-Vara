package com.lavara.triggers

import com.lavara.automation.Automation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class NextAlarmTest {
    private val zone = ZoneId.of("America/Costa_Rica")

    // 05/10/2026 es lunes.
    private fun at(day: Int, hour: Int, minute: Int, second: Int = 0) = ZonedDateTime.of(2026, 10, day, hour, minute, second, 0, zone)

    @Test
    fun hoyMasTarde() {
        assertEquals(at(5, 8, 0), NextAlarm.after(Trigger.Time("08:00"), at(5, 7, 59, 30)))
    }

    @Test
    fun siYaPaso_esMañana() {
        assertEquals(at(6, 8, 0), NextAlarm.after(Trigger.Time("08:00"), at(5, 8, 0)))
        assertEquals(at(6, 8, 0), NextAlarm.after(Trigger.Time("08:00"), at(5, 8, 0, 20)))
    }

    @Test
    fun respetaLosDias() {
        val viernes = Trigger.Time("08:00", listOf(Weekday.FRIDAY))
        assertEquals(at(9, 8, 0), NextAlarm.after(viernes, at(5, 9, 0)))
        // Un viernes después de la hora: el viernes siguiente.
        assertEquals(at(16, 8, 0), NextAlarm.after(viernes, at(9, 8, 1)))
    }

    @Test
    fun laMasProxima_entreVarias() {
        fun a(id: String, time: String, enabled: Boolean = true) =
            Automation(id = id, name = id, trigger = Trigger.Time(time), enabled = enabled)
        val list = listOf(a("tarde", "18:00"), a("temprano", "09:00"), a("tambien", "09:00"), a("apagada", "08:30", enabled = false))

        val (time, which) = NextAlarm.earliest(list, at(5, 8, 0))!!
        assertEquals(at(5, 9, 0), time)
        assertEquals(listOf("temprano", "tambien"), which.map { it.id })
        assertNull(NextAlarm.earliest(listOf(a("apagada", "08:30", enabled = false)), at(5, 8, 0)))
    }
}
