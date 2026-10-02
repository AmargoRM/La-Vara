package com.lavara.logging

import androidx.room.Room
import com.lavara.data.LaVaraDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class RoomLoggerTest {

    @Test
    fun registros_seGuardanEnOrden() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LaVaraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        val clock = { ZonedDateTime.of(2026, 10, 5, 8, 0, 0, 0, ZoneId.of("UTC")) }
        val logger = RoomLogger(db.logDao(), scope, clock)

        logger.info("App", "App iniciada", null)
        logger.warn("Alarmas", "Alarma atrasada", "prueba-vara")
        logger.error("Acciones", "x".repeat(5_000), "prueba-vara")
        scope.coroutineContext.job.children.forEach { it.join() }

        val logs = db.logDao().latest(10).sortedBy { it.id }
        assertEquals(listOf("INFO", "WARN", "ERROR"), logs.map { it.level })
        assertEquals("App iniciada", logs[0].message)
        assertEquals("prueba-vara", logs[1].automationId)
        assertEquals(RoomLogger.MAX_MESSAGE, logs[2].message.length)
        db.close()
    }
}
