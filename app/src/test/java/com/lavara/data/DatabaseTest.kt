package com.lavara.data

import androidx.room.Room
import com.lavara.actions.Action
import com.lavara.automation.ActionRecord
import com.lavara.automation.Automation
import com.lavara.automation.ExecutionResult
import com.lavara.automation.ExecutionStatus
import com.lavara.logging.AppLogger
import com.lavara.logging.LogLevel
import com.lavara.triggers.Trigger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DatabaseTest {

    private lateinit var db: LaVaraDatabase
    private val logged = mutableListOf<Pair<LogLevel, String>>()
    private val logger = object : AppLogger {
        override fun log(level: LogLevel, source: String, message: String, automationId: String?) {
            logged += level to message
        }
    }

    private val prueba = Automation(
        id = "prueba-vara",
        name = "Prueba Vara",
        trigger = Trigger.Time("08:00"),
        actions = listOf(Action.ShowNotification("Hola")),
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LaVaraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun automatizacion_seGuardaYSeLee() = runBlocking {
        val repo = AutomationRepository(db.automationDao(), logger)
        repo.save(prueba)

        assertEquals(prueba, repo.find("prueba-vara"))
        assertEquals(listOf(prueba), repo.all())
        assertEquals(listOf(prueba), repo.observeAll().first())
        val entity = db.automationDao().find("prueba-vara")!!
        assertEquals("time", entity.triggerType)
        assertTrue(entity.definition.contains("\"type\": \"show_notification\""))

        repo.delete("prueba-vara")
        assertNull(repo.find("prueba-vara"))
    }

    @Test
    fun automatizacionIlegible_seSaltaYQuedaEnElLog() = runBlocking {
        val repo = AutomationRepository(db.automationDao(), logger)
        repo.save(prueba)
        db.automationDao().upsert(AutomationRepository.toEntity(prueba.copy(id = "rota", name = "Rota")).copy(definition = "{no es json"))

        assertEquals(listOf("prueba-vara"), repo.all().map { it.id })
        assertEquals(LogLevel.ERROR, logged.single().first)
        assertTrue(logged.single().second.contains("Rota"))
    }

    @Test
    fun ejecucion_seGuardaYActualizaContadores() = runBlocking {
        AutomationRepository(db.automationDao(), logger).save(prueba)
        val runs = RunRepository(db)
        val actions = listOf(ActionRecord(0, "Mostrar notificación \"Hola\"", 12, success = false, errorMessage = "sin permiso"))

        runs.record(ExecutionResult("prueba-vara", ExecutionStatus.FAILED, "Falló", 1_000, 15, actions, "Mostrar notificación", "sin permiso"))
        runs.record(ExecutionResult("prueba-vara", ExecutionStatus.SKIPPED_CONDITIONS, "No se cumple", 2_000, 0))

        val saved = runs.latest(10)
        assertEquals(listOf("SKIPPED_CONDITIONS", "FAILED"), saved.map { it.status })
        assertEquals("Prueba Vara", saved[1].automationName)
        assertEquals(actions, RunRepository.decodeActions(saved[1].actions))

        // Solo cuenta como ejecución la que corrió de verdad.
        val automation = db.automationDao().find("prueba-vara")!!
        assertEquals(1L, automation.executionCount)
        assertEquals(1L, automation.failureCount)
        assertEquals(1_000L, automation.lastExecutedAt)
        // Y el motor ve esos contadores.
        assertEquals(1_000L, AutomationRepository(db.automationDao(), logger).find("prueba-vara")!!.lastExecutedAt)
    }

    @Test
    fun logs_seRecortanALosMasNuevos() = runBlocking {
        val dao = db.logDao()
        repeat(10) { dao.insert(LogEntity(timestamp = it.toLong(), level = "INFO", source = "Test", message = "log $it")) }
        dao.trim(3)
        assertEquals(listOf("log 9", "log 8", "log 7"), dao.latest(10).map { it.message })
    }

    @Test
    fun ajustes_seGuardanYReemplazan() = runBlocking {
        val settings = SettingsRepository(db.settingDao())
        assertNull(settings.get("tema"))
        settings.set("tema", "claro")
        settings.set("tema", "oscuro")
        assertEquals("oscuro", settings.get("tema"))
    }
}
