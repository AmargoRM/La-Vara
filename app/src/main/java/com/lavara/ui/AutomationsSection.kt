package com.lavara.ui

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lavara.actions.Action
import com.lavara.automation.Automation
import com.lavara.automation.AutomationDraft
import com.lavara.automation.ExecutionStatus
import com.lavara.core.AppContainer
import com.lavara.triggers.NextAlarm
import com.lavara.triggers.Trigger
import com.lavara.triggers.TriggerEvent
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/**
 * Inicio: resumen del motor, lo que falta para que las alarmas funcionen y la lista de automatizaciones
 * (activar, probar, editar, duplicar, eliminar). El editor se abre con [onEdit] (id null = nueva).
 */
@Composable
fun AutomationsSection(
    container: AppContainer,
    resumeCount: Int,
    onEdit: (String?) -> Unit,
    onShowHistory: () -> Unit,
) {
    val automations by remember { container.automationRepository.observeAll() }.collectAsState(initial = emptyList())
    val runs by remember { container.runRepository.observeLatest(100) }.collectAsState(initial = emptyList())
    val nextAlarm by container.alarmScheduler.next.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var note by remember { mutableStateOf<String?>(null) }
    // Hora elegida que todavía falta confirmar: automatización y hora nueva "HH:mm".
    var pendingTime by remember { mutableStateOf<Pair<Automation, Trigger.Time>?>(null) }
    var pendingDelete by remember { mutableStateOf<Automation?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        container.logger.info("Permisos", "Permiso de notificaciones: ${if (granted) "concedido" else "negado"}")
    }

    fun save(updated: Automation, what: String) {
        scope.launch {
            container.automationRepository.save(updated.copy(updatedAt = container.clock.now().toInstant().toEpochMilli()))
            container.logger.info("Automatizaciones", "${updated.name}: $what", updated.id)
            container.alarmScheduler.reschedule("${updated.name}: $what")
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Se vuelve a leer cada vez que la app vuelve al frente (por si el usuario cambió algo en Ajustes).
        val health = remember(resumeCount, automations) {
            Health(
                notifications = container.actionExecutor.canNotify(),
                exactAlarms = container.alarmScheduler.canScheduleExact(),
                battery = container.batteryOptimization.isExcluded(),
                // Solo hace falta si alguna automatización activa abre apps.
                openApps = automations.none { a -> a.enabled && a.actions.any { it is Action.OpenApp || it is Action.OpenUrl } } ||
                    container.actionExecutor.canOpenAppsInBackground(),
            )
        }

        val now = container.clock.now()
        val nowMillis = now.toInstant().toEpochMilli()
        val lastRun = runs.firstOrNull { it.status == ExecutionStatus.EXECUTED.name || it.status == ExecutionStatus.FAILED.name }
        val recentErrors = runs.count { it.status == ExecutionStatus.FAILED.name && nowMillis - it.startedAt < 24 * 3_600_000L }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (health.allOk) "El motor está listo" else "El motor funciona, pero faltan permisos (abajo)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text("${automations.size} automatizaciones · ${automations.count { it.enabled }} activas", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Última ejecución: " + (lastRun?.let {
                        val mark = if (it.status == ExecutionStatus.EXECUTED.name) "✓" else "✗"
                        "${whenText(context, Instant.ofEpochMilli(it.startedAt).atZone(now.zone), now)} · ${it.automationName} $mark"
                    } ?: "todavía ninguna"),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    nextAlarm?.let { "Próxima alarma: ${whenText(context, Instant.ofEpochMilli(it).atZone(now.zone), now)}" }
                        ?: "No hay alarmas programadas.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (recentErrors > 0) {
            Card(
                Modifier.fillMaxWidth().clickable(onClick = onShowHistory),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (recentErrors == 1) "1 error en las últimas 24 horas" else "$recentErrors errores en las últimas 24 horas",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text("Ver", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (automations.any { it.enabled } && !health.allOk) {
            HealthCard(
                health = health,
                onNotifications = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                onExactAlarms = { container.alarmScheduler.openExactAlarmSettings() },
                onBattery = { container.batteryOptimization.requestExclusion() },
                onOpenApps = { container.actionExecutor.openBackgroundAppsSettings() },
            )
        }

        Text("Mis automatizaciones", style = MaterialTheme.typography.titleMedium)
        if (automations.isEmpty()) Text("Todavía no hay ninguna. Tocá \"Nueva\" para crear la primera.", style = MaterialTheme.typography.bodyMedium)

        automations.forEach { automation ->
            AutomationCard(
                automation = automation,
                onOpen = { onEdit(automation.id) },
                onToggle = { enabled ->
                    if (enabled && !container.actionExecutor.canNotify() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    save(automation.copy(enabled = enabled), if (enabled) "activada" else "desactivada")
                },
                onChangeTime = { trigger ->
                    // Arranca en la hora actual + 2 minutos (no en la guardada): así a. m./p. m. ya viene bien
                    // y para una prueba alcanza con tocar Aceptar.
                    val start = container.clock.now().plusMinutes(2)
                    TimePickerDialog(context, { _, hour, minute ->
                        pendingTime = automation to trigger.copy(time = "%02d:%02d".format(hour, minute))
                    // Mismo formato que el reloj del teléfono: con a. m./p. m. si el teléfono usa 12 horas.
                    }, start.hour, start.minute, DateFormat.is24HourFormat(context)).show()
                },
                onRunNow = {
                    scope.launch {
                        val result = container.automationRunner
                            .handle(TriggerEvent.ManualRun(automation.id, UUID.randomUUID().toString()))
                            .firstOrNull { it.automationId == automation.id }
                        note = result?.let { "${automation.name}: ${it.reason}" } ?: "${automation.name}: no se ejecutó."
                    }
                },
                onDuplicate = {
                    scope.launch {
                        val copy = AutomationDraft.duplicate(automation, "a-" + UUID.randomUUID().toString().take(8), container.clock.now().toInstant().toEpochMilli())
                        container.automationRepository.save(copy)
                        container.logger.info("Automatizaciones", "${automation.name}: duplicada como \"${copy.name}\" (desactivada)", copy.id)
                        note = "Se creó \"${copy.name}\", desactivada."
                    }
                },
                onDelete = { pendingDelete = automation },
            )
        }
        note?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
    }

    pendingTime?.let { (automation, trigger) ->
        val now = container.clock.now()
        val at = NextAlarm.after(trigger, now)
        AlertDialog(
            onDismissRequest = { pendingTime = null },
            title = { Text("¿Guardar esta hora?") },
            text = {
                Text(
                    "${automation.name} va a sonar ${whenText(context, at, now)}, dentro de ${untilText(now, at)}.",
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingTime = null
                    save(automation.copy(trigger = trigger), "hora cambiada a ${trigger.time}")
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { pendingTime = null }) { Text("Cancelar") } },
        )
    }

    pendingDelete?.let { automation ->
        val users = AutomationDraft.usersOf(automation.id, automations)
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("¿Eliminar \"${automation.name}\"?") },
            text = {
                Text(
                    "Se borra para siempre; no se puede deshacer. Su historial de ejecuciones se conserva." +
                        if (users.isEmpty()) "" else "\n\nOjo: la usa ${users.joinToString(", ")}. Esa acción va a fallar.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        container.automationRepository.delete(automation.id)
                        container.logger.info("Automatizaciones", "${automation.name}: eliminada", automation.id)
                        container.alarmScheduler.reschedule("${automation.name}: eliminada")
                        note = "Se eliminó \"${automation.name}\"."
                    }
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } },
        )
    }
}

private data class Health(val notifications: Boolean, val exactAlarms: Boolean, val battery: Boolean, val openApps: Boolean) {
    val allOk get() = notifications && exactAlarms && battery && openApps
}

@Composable
private fun HealthCard(
    health: Health,
    onNotifications: () -> Unit,
    onExactAlarms: () -> Unit,
    onBattery: () -> Unit,
    onOpenApps: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Para que las alarmas lleguen a tiempo", style = MaterialTheme.typography.titleMedium)
            if (!health.notifications) {
                Text("La Vara no tiene permiso para mostrar notificaciones.", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onNotifications) { Text("Permitir notificaciones") }
            }
            if (!health.exactAlarms) {
                Text("Android no deja a La Vara poner alarmas exactas: pueden llegar tarde.", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onExactAlarms) { Text("Permitir alarmas exactas") }
            }
            if (!health.battery) {
                Text(
                    "El ahorro de batería puede frenar a La Vara cuando no la usás. Android te va a preguntar si la dejás funcionar sin restricciones: tocá Permitir.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onBattery) { Text("Quitar el ahorro de batería") }
            }
            if (!health.openApps) {
                Text(
                    "Una automatización abre apps. Android solo deja hacerlo con La Vara cerrada si le das el permiso \"Mostrar sobre otras apps\". Sin él, vas a recibir una notificación para abrirla a mano.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onOpenApps) { Text("Permitir abrir apps") }
            }
        }
    }
}

@Composable
private fun AutomationCard(
    automation: Automation,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onChangeTime: (Trigger.Time) -> Unit,
    onRunNow: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(automation.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(summary(context, automation), style = MaterialTheme.typography.bodyMedium)
                }
                Switch(checked = automation.enabled, onCheckedChange = onToggle)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val trigger = automation.trigger
                if (trigger is Trigger.Time) OutlinedButton(onClick = { onChangeTime(trigger) }) { Text("Cambiar hora") }
                OutlinedButton(onClick = onRunNow) { Text("Probar ahora") }
                Box {
                    TextButton(onClick = { menu = true }) { Text("Más") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Editar") }, onClick = { menu = false; onOpen() })
                        DropdownMenuItem(text = { Text("Duplicar") }, onClick = { menu = false; onDuplicate() })
                        DropdownMenuItem(text = { Text("Eliminar") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
    }
}
