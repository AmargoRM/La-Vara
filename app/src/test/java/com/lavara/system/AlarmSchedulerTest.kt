package com.lavara.system

import android.app.AlarmManager
import androidx.room.Room
import com.lavara.automation.Templates
import com.lavara.core.Clock
import com.lavara.data.AutomationRepository
import com.lavara.data.LaVaraDatabase
import com.lavara.data.SettingsRepository
import com.lavara.logging.AppLogger
import com.lavara.logging.LogLevel
import com.lavara.system.alarm.AlarmScheduler
import com.lavara.triggers.Trigger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class AlarmSchedulerTest {

    private val app = RuntimeEnvironment.getApplication()
    private val db = Room.inMemoryDatabaseBuilder(app, LaVaraDatabase::class.java).allowMainThreadQueries().build()
    private val zone = ZoneId.of("America/Costa_Rica")
    private var now = ZonedDateTime.of(2026, 10, 5, 7, 0, 0, 0, zone)
    private val clock = Clock { now }
    private val logged = mutableListOf<Pair<LogLevel, String>>()
    private val logger = object : AppLogger {
        override fun log(level: LogLevel, source: String, message: String, automationId: String?) {
            logged += level to message
        }
    }
    private val repo = AutomationRepository(db.automationDao(), logger)
    private val settings = SettingsRepository(db.settingDao())
    private val scheduler = AlarmScheduler(app, repo, settings, logger, clock)
    private val alarms = shadowOf(app.getSystemService(AlarmManager::class.java))

    @After
    fun tearDown() = db.close()

    @Test
    fun plantillaDesactivada_noProgramaNada() = runBlocking {
        repo.save(Templates.pruebaVara(0))
        scheduler.reschedule("test")
        assertNull(alarms.nextScheduledAlarm)
        assertNull(scheduler.next.value)
    }

    @Test
    fun activada_programaLaProximaHoraYLoRegistra() = runBlocking {
        repo.save(Templates.pruebaVara(0).copy(enabled = true, trigger = Trigger.Time("08:00")))
        scheduler.reschedule("test")

        val expected = ZonedDateTime.of(2026, 10, 5, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, alarms.nextScheduledAlarm.triggerAtTime)
        assertEquals(expected, scheduler.next.value)
        assertTrue(logged.last().second, logged.last().second.contains("05/10 08:00"))
    }

    @Test
    fun alarmaQueNoSono_quedaComoAviso() = runBlocking {
        repo.save(Templates.pruebaVara(0).copy(enabled = true))
        scheduler.reschedule("test")
        // El teléfono estuvo apagado hasta el día siguiente.
        now = now.plusDays(1).plusHours(3)
        scheduler.reschedule("teléfono reiniciado")

        assertTrue(logged.any { it.first == LogLevel.WARN && it.second.contains("no sonó") })
    }
}
