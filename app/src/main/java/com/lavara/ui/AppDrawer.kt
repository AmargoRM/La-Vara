package com.lavara.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.lavara.system.accessibility.AllowedApps
import com.lavara.system.accessibility.TapService
import com.lavara.system.device.InstalledApps
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lavara.BuildConfig
import com.lavara.core.AppContainer
import com.lavara.automation.ExecutionStatus
import com.lavara.system.update.UpdateStatus
import java.time.Instant

/**
 * Menú lateral: lo que no se usa todos los días (versión, estado del motor, actualizaciones y cómo funciona).
 * Inicio queda solo con las automatizaciones y los avisos que piden hacer algo.
 */
@Composable
fun AppDrawer(versionLabel: String, container: AppContainer, installRequest: Int) {
    ModalDrawerSheet {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("LA VARA", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(versionLabel, style = MaterialTheme.typography.bodyMedium)
            EngineSummaryCard(container)
            UpdateSection(container = container, installRequest = installRequest)
            AllowedAppsCard(container)
            HowItWorksCard()
        }
    }
}

/** Resumen del motor: cuántas automatizaciones hay, la última ejecución y la próxima alarma. */
@Composable
private fun EngineSummaryCard(container: AppContainer) {
    val context = LocalContext.current
    val automations by remember { container.automationRepository.observeAll() }.collectAsState(initial = emptyList())
    val runs by remember { container.runRepository.observeLatest(100) }.collectAsState(initial = emptyList())
    val nextAlarm by container.alarmScheduler.next.collectAsState()
    val now = container.clock.now()
    val lastRun = runs.firstOrNull { it.status == ExecutionStatus.EXECUTED.name || it.status == ExecutionStatus.FAILED.name }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Estado del motor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
            Text("Si falta algún permiso, el aviso aparece en Inicio.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Apps donde La Vara puede tocar botones con Accesibilidad. Se agregan desde el editor; aquí se ven y se
 * quitan. Sin apps, el permiso no sirve para nada aunque esté encendido.
 */
@Composable
private fun AllowedAppsCard(container: AppContainer) {
    val context = LocalContext.current
    val allowed = remember { AllowedApps(context) }
    var apps by remember { mutableStateOf(allowed.get()) }
    val enabled = TapService.isEnabled(context)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tocar botones en otras apps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Permiso de Accesibilidad: " + if (enabled) "encendido." else "apagado.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (apps.isEmpty()) {
                Text("Ninguna app permitida. Se agregan al crear la acción \"Tocar un botón en otra app\".", style = MaterialTheme.typography.bodySmall)
            }
            val installed = remember { InstalledApps(context) }
            apps.sorted().forEach { pkg ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(installed.label(pkg) ?: pkg, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        allowed.remove(pkg)
                        apps = allowed.get()
                        container.logger.info("Accesibilidad", "${installed.label(pkg) ?: pkg} quitada de las apps donde La Vara puede tocar botones.")
                    }) { Text("Quitar") }
                }
            }
            if (enabled) {
                Text("Para apagarlo del todo: Accesibilidad → La Vara: tocar botones → apagar.", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = { TapService.openSettings(context) }) { Text("Abrir Accesibilidad") }
        }
    }
}

@Composable
private fun HowItWorksCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cómo funciona", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Cada automatización tiene tres partes: cuándo se dispara (hora, batería, cargador, lugar, Bluetooth, Wi-Fi " +
                    "o a mano), condiciones opcionales y lo que hace (avisar, abrir una app o un enlace, y más).",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Funciona con La Vara cerrada. Algunos disparadores necesitan la notificación fija \"La Vara está activa\": " +
                    "Android la exige para escuchar la batería, el cargador y el Wi-Fi.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Todo lo que pasa queda en Historial: cada evento, si se ejecutó o no y por qué, y cada error.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** true si hay una versión más nueva que la instalada esperando. */
fun UpdateStatus.hasNewVersion(): Boolean = (available?.versionCode ?: 0) > BuildConfig.VERSION_CODE

/** Aviso en Inicio solo cuando hay versión nueva: lleva al menú, donde está el botón de instalar. */
@Composable
fun NewVersionBanner(status: UpdateStatus, onOpen: () -> Unit) {
    val release = status.available ?: return
    if (!status.hasNewVersion()) return
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Hay una versión nueva: ${release.versionName}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("Tocá para instalarla.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
