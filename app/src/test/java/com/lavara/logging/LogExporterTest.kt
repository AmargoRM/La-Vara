package com.lavara.logging

import com.lavara.automation.ActionRecord
import com.lavara.data.AutomationRunEntity
import com.lavara.data.LogEntity
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class LogExporterTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun incluyeEjecucionesAccionesYRegistros() {
        val actions = Json.encodeToString(
            ListSerializer(ActionRecord.serializer()),
            listOf(ActionRecord(0, "Abrir app com.x", 5, success = false, errorMessage = "no instalada")),
        )
        val run = AutomationRunEntity(
            automationId = "a", automationName = "Abrir X", status = "FAILED", reason = "Falló",
            startedAt = 0, durationMillis = 7, actions = actions, failedAction = "Abrir app com.x", errorMessage = "no instalada",
        )
        val log = LogEntity(timestamp = 1_000, level = "INFO", source = "App", message = "App iniciada")

        val text = LogExporter.build("La Vara 0.1.20", listOf(run), listOf(log), zone)

        assertTrue(text, text.startsWith("La Vara 0.1.20"))
        assertTrue(text, text.contains("1970-01-01 00:00:00 Abrir X [a] falló: Falló (7 ms)"))
        assertTrue(text, text.contains("    1. Abrir app com.x: ERROR no instalada (5 ms)"))
        assertTrue(text, text.contains("1970-01-01 00:00:01 INFO App: App iniciada"))
    }

    @Test
    fun textoLargo_seRecorta() {
        val logs = List(100) { LogEntity(timestamp = 0, level = "INFO", source = "S", message = "m".repeat(100)) }
        val text = LogExporter.build("h", emptyList(), logs, zone, maxChars = 1_000)
        assertTrue(text.endsWith("(recortado)"))
        assertTrue(text.length < 1_100)
    }
}
