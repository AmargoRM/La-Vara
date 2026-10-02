package com.lavara.ui.history

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lavara.BuildConfig
import com.lavara.automation.ExecutionStatus
import com.lavara.core.AppContainer
import com.lavara.data.AutomationRunEntity
import com.lavara.data.LogEntity
import com.lavara.data.RunRepository
import com.lavara.logging.LogExporter
import com.lavara.logging.LogLevel
import kotlinx.coroutines.launch
import java.time.ZoneId

/** Pantalla Historial: ejecuciones y registros, con filtros y el botón Exportar logs. */
@Composable
fun HistoryScreen(container: AppContainer) {
    val runs by remember { container.runRepository.observeLatest(300) }.collectAsState(initial = emptyList())
    val logs by remember { container.database.logDao().observeLatest(500) }.collectAsState(initial = emptyList())

    var showLogs by rememberSaveable { mutableStateOf(false) }
    var onlyErrors by rememberSaveable { mutableStateOf(false) }
    var automationFilter by rememberSaveable { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()

    // Automatizaciones que aparecen en el historial, para el filtro: id → nombre.
    val known = remember(runs, logs) {
        val names = LinkedHashMap<String, String>()
        runs.forEach { names.putIfAbsent(it.automationId, it.automationName) }
        logs.mapNotNull { it.automationId }.forEach { names.putIfAbsent(it, it) }
        names
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Historial", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = {
                scope.launch {
                    val text = LogExporter.build(
                        header = "La Vara ${BuildConfig.VERSION_NAME} (compilación ${BuildConfig.VERSION_CODE}) · zona $zone",
                        runs = container.runRepository.latest(500),
                        logs = container.database.logDao().latest(2_000),
                        zone = zone,
                    )
                    container.logger.info("Historial", "Se exportaron los logs")
                    share(context, text)
                }
            }) { Text("Exportar logs") }
        }

        PrimaryTabRow(selectedTabIndex = if (showLogs) 1 else 0) {
            Tab(selected = !showLogs, onClick = { showLogs = false }, text = { Text("Ejecuciones") })
            Tab(selected = showLogs, onClick = { showLogs = true }, text = { Text("Registros") })
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AutomationFilter(known, automationFilter) { automationFilter = it }
            FilterChip(selected = !onlyErrors, onClick = { onlyErrors = false }, label = { Text("Todo") })
            FilterChip(selected = onlyErrors, onClick = { onlyErrors = true }, label = { Text("Solo errores") })
        }

        if (showLogs) {
            val shown = logs.filter { log ->
                (automationFilter == null || log.automationId == automationFilter) &&
                    (!onlyErrors || log.level == LogLevel.ERROR.name || log.level == LogLevel.WARN.name)
            }
            ListOrEmpty(shown.isEmpty(), "Todavía no hay registros con este filtro.") {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(shown, key = { it.id }) { LogRow(it, zone) }
                }
            }
        } else {
            val shown = runs.filter { run ->
                (automationFilter == null || run.automationId == automationFilter) &&
                    (!onlyErrors || run.status == ExecutionStatus.FAILED.name)
            }
            ListOrEmpty(shown.isEmpty(), "Todavía no hay ejecuciones. Aparecen acá cuando una automatización se evalúa.") {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown, key = { it.id }) { RunRow(it, zone) }
                }
            }
        }
    }
}

@Composable
private fun AutomationFilter(known: Map<String, String>, selected: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text(selected?.let { known[it] ?: it } ?: "Todas las automatizaciones", maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Todas las automatizaciones") }, onClick = { onSelect(null); open = false })
            known.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); open = false })
            }
        }
    }
}

@Composable
private fun ListOrEmpty(empty: Boolean, emptyText: String, content: @Composable () -> Unit) {
    if (empty) {
        Text(
            emptyText,
            modifier = Modifier.padding(24.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        content()
    }
}

@Composable
private fun RunRow(run: AutomationRunEntity, zone: ZoneId) {
    var expanded by remember { mutableStateOf(false) }
    val failed = run.status == ExecutionStatus.FAILED.name
    val ok = run.status == ExecutionStatus.EXECUTED.name
    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    (if (ok) "✓ " else if (failed) "✕ " else "– ") + run.automationName,
                    fontWeight = FontWeight.Bold,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                Text(LogExporter.time(run.startedAt, zone).substring(5, 16), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "${LogExporter.statusText(run.status).replaceFirstChar { it.uppercase() }}: ${run.reason}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expanded) {
                val actions = RunRepository.decodeActions(run.actions)
                if (actions.isEmpty()) Text("Sin acciones ejecutadas.", style = MaterialTheme.typography.bodySmall)
                actions.forEach { action ->
                    Text(
                        "${action.index + 1}. ${action.action}: " +
                            (if (action.success) "bien" else "error: ${action.errorMessage}") + " (${action.durationMillis} ms)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text("Duró ${run.durationMillis} ms", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LogRow(log: LogEntity, zone: ZoneId) {
    val color = when (log.level) {
        LogLevel.ERROR.name -> MaterialTheme.colorScheme.error
        LogLevel.WARN.name -> WarnOrange
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column {
        Text(
            "${LogExporter.time(log.timestamp, zone).substring(5)} · ${log.level} · ${log.source}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(log.message, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

private val WarnOrange = Color(0xFF9A5B00)

/** Abre el menú Compartir de Android con el texto (WhatsApp, correo, etc.). */
private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "Logs de La Vara")
        .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Exportar logs"))
}
