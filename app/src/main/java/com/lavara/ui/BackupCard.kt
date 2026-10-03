package com.lavara.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lavara.automation.Backup
import com.lavara.core.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Respaldo: guarda todas las automatizaciones en un archivo (por ejemplo, en Drive o Descargas) y las
 * vuelve a cargar. Importar nunca borra ni reemplaza las que ya están.
 */
@Composable
fun BackupCard(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf<String?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            note = try {
                val all = container.automationRepository.all()
                val text = Backup.encode(all, container.clock.now().toInstant().toEpochMilli())
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                }
                container.logger.info("Respaldo", "Respaldo guardado: ${all.size} automatizaciones")
                "Listo: ${all.size} automatizaciones guardadas en el archivo."
            } catch (e: Exception) {
                container.logger.error("Respaldo", "No se pudo guardar el respaldo: ${e.message ?: e::class.simpleName}")
                "No se pudo guardar el archivo: ${e.message ?: "error desconocido"}."
            }
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            note = try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                }
                val read = Backup.decode(text)
                val now = container.clock.now().toInstant().toEpochMilli()
                val plan = Backup.plan(container.automationRepository.all(), read.automations, now) {
                    "a-" + UUID.randomUUID().toString().take(8)
                }
                plan.toSave.forEach { container.automationRepository.save(it) }
                container.logger.info(
                    "Respaldo",
                    "Respaldo cargado: ${plan.toSave.size} agregadas (${plan.copies} como copia), ${plan.alreadyThere} ya estaban, ${read.unreadable} sin leer",
                )
                container.refreshTriggers("respaldo cargado")
                buildString {
                    append(if (plan.toSave.isEmpty()) "No había nada nuevo." else "Se agregaron ${plan.toSave.size} automatizaciones.")
                    if (plan.alreadyThere > 0) append(" ${plan.alreadyThere} ya estaban iguales.")
                    if (plan.copies > 0) append(" ${plan.copies} ya existían con cambios: se agregaron como copia \"(importada)\", desactivadas.")
                    if (read.unreadable > 0) append(" ${read.unreadable} no se pudieron leer (quizás son de una versión más nueva de La Vara).")
                    if (plan.toSave.isNotEmpty()) append(" Revisá Inicio por si alguna pide permisos.")
                }
            } catch (e: IllegalArgumentException) {
                e.message
            } catch (e: Exception) {
                container.logger.error("Respaldo", "No se pudo cargar el respaldo: ${e.message ?: e::class.simpleName}")
                "No se pudo leer el archivo: ${e.message ?: "error desconocido"}."
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Respaldo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Guarda todas tus automatizaciones en un archivo. Si cambiás de teléfono o reinstalás La Vara, las cargás de nuevo. " +
                    "El archivo incluye los números y textos de tus mensajes: guardalo en un lugar tuyo.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val date = container.clock.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    exporter.launch("la-vara-respaldo-$date.json")
                }) { Text("Guardar") }
                OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Cargar") }
            }
            note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
