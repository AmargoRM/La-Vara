package com.lavara.ui

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.lavara.automation.Automation
import com.lavara.core.AppContainer
import com.lavara.core.TimeText
import com.lavara.system.alarm.AlarmScheduler
import com.lavara.triggers.Trigger
import com.lavara.triggers.TriggerEvent
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/**
 * Lista mínima de automatizaciones (el editor completo llega en S4): activar/desactivar,
 * cambiar la hora y probar ahora. Arriba, lo que falta para que las alarmas funcionen.
 */
@Composable
fun AutomationsSection(container: AppContainer, resumeCount: Int) {
    val automations by remember { container.automationRepository.observeAll() }.collectAsState(initial = emptyList())
    val nextAlarm by container.alarmScheduler.next.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var note by remember { mutableStateOf<String?>(null) }

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
            )
        }
        if (automations.any { it.enabled } && !health.allOk) {
            HealthCard(
                health = health,
                onNotifications = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                onExactAlarms = { container.alarmScheduler.openExactAlarmSettings() },
                onBattery = { container.batteryOptimization.requestExclusion() },
            )
        }

        Text("Automatizaciones", style = MaterialTheme.typography.titleMedium)
        Text(
            nextAlarm?.let { "Próxima alarma: ${AlarmScheduler.format(Instant.ofEpochMilli(it).atZone(container.clock.now().zone))}" }
                ?: "No hay alarmas programadas.",
            style = MaterialTheme.typography.bodyMedium,
        )

        automations.forEach { automation ->
            AutomationCard(
                automation = automation,
                onToggle = { enabled ->
                    if (enabled && !container.actionExecutor.canNotify() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    save(automation.copy(enabled = enabled), if (enabled) "activada" else "desactivada")
                },
                onChangeTime = { trigger ->
                    val current = TimeText.parseOrNull(trigger.time)!!
                    TimePickerDialog(context, { _, hour, minute ->
                        val time = "%02d:%02d".format(hour, minute)
                        save(automation.copy(trigger = trigger.copy(time = time)), "hora cambiada a $time")
                    }, current.hour, current.minute, true).show()
                },
                onRunNow = {
                    scope.launch {
                        val result = container.automationRunner
                            .handle(TriggerEvent.ManualRun(automation.id, UUID.randomUUID().toString()))
                            .firstOrNull { it.automationId == automation.id }
                        note = result?.let { "${automation.name}: ${it.reason}" } ?: "${automation.name}: no se ejecutó."
                    }
                },
            )
        }
        note?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
    }
}

private data class Health(val notifications: Boolean, val exactAlarms: Boolean, val battery: Boolean) {
    val allOk get() = notifications && exactAlarms && battery
}

@Composable
private fun HealthCard(health: Health, onNotifications: () -> Unit, onExactAlarms: () -> Unit, onBattery: () -> Unit) {
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
        }
    }
}

@Composable
private fun AutomationCard(
    automation: Automation,
    onToggle: (Boolean) -> Unit,
    onChangeTime: (Trigger.Time) -> Unit,
    onRunNow: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(automation.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(describe(automation.trigger), style = MaterialTheme.typography.bodyMedium)
                }
                Switch(checked = automation.enabled, onCheckedChange = onToggle)
            }
            if (automation.description.isNotBlank()) {
                Text(automation.description, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val trigger = automation.trigger
                if (trigger is Trigger.Time) OutlinedButton(onClick = { onChangeTime(trigger) }) { Text("Cambiar hora") }
                OutlinedButton(onClick = onRunNow) { Text("Probar ahora") }
            }
        }
    }
}

private fun describe(trigger: Trigger): String = when (trigger) {
    is Trigger.Time -> "Cada " + (if (trigger.days.isEmpty()) "día" else trigger.days.joinToString(", ") { dayName(it.isoNumber) }) + " a las ${trigger.time}"
    is Trigger.Battery -> "Cuando la batería ${if (trigger.direction.name == "BELOW") "baja a" else "sube a"} ${trigger.threshold} %"
    Trigger.Manual -> "Solo a mano"
}

private fun dayName(iso: Int) = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")[iso - 1]
