package com.lavara.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lavara.core.AppContainer
import com.lavara.system.accessibility.TapService
import com.lavara.system.connectivity.BluetoothDevices
import com.lavara.system.nfc.NfcTags
import com.lavara.system.notifications.NotificationWatchService

/** Un permiso: si está dado, para qué sirve y cómo darlo. */
private class PermissionRow(val name: String, val purpose: String, val granted: Boolean, val open: () -> Unit)

/**
 * Todos los permisos en un solo lugar, en verde o en rojo. Ninguno es obligatorio: cada uno hace falta
 * solo si una automatización usa esa función. Se vuelve a revisar cada vez que La Vara vuelve al frente.
 */
@Composable
fun PermissionsCard(container: AppContainer) {
    val context = LocalContext.current
    val resume = LocalResumeCount.current
    var refresh by remember { mutableIntStateOf(0) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        container.logger.info("Permisos", "Permiso de notificaciones: ${if (granted) "concedido" else "negado"}")
        refresh++
    }
    val executor = container.actionExecutor
    val controls = executor.systemControls
    val location = container.locationAccess
    val rows = remember(resume, refresh) {
        buildList {
            add(PermissionRow("Notificaciones", "Mostrar tus avisos y el de versión nueva.", executor.canNotify()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            })
            add(PermissionRow("Alarmas exactas", "Que las automatizaciones con hora y las esperas largas lleguen a tiempo.", container.alarmScheduler.canScheduleExact()) {
                container.alarmScheduler.openExactAlarmSettings()
            })
            add(PermissionRow("Sin ahorro de batería", "Que Android no frene a La Vara cuando está cerrada.", container.batteryOptimization.isExcluded()) {
                container.batteryOptimization.requestExclusion()
            })
            add(PermissionRow("Mostrar sobre otras apps", "Abrir apps y enlaces con La Vara cerrada.", executor.canOpenAppsInBackground()) {
                executor.openBackgroundAppsSettings()
            })
            add(PermissionRow("Accesibilidad", "Tocar botones en las apps que elegiste.", TapService.isEnabled(context)) {
                TapService.openSettings(context)
            })
            add(PermissionRow("Acceso a notificaciones", "Disparar cuando llega una notificación de otra app.", NotificationWatchService.isEnabled(context)) {
                NotificationWatchService.openSettings(context)
            })
            add(PermissionRow("SMS", "Enviar SMS automáticos.", executor.smsSender.hasPermission()) { executor.smsSender.openAppSettings() })
            add(PermissionRow("Ubicación todo el tiempo", "Disparar al llegar o irte de un lugar, y leer el nombre del Wi-Fi.", location.hasBackground()) {
                location.openAppSettings()
            })
            add(PermissionRow("Dispositivos cercanos", "Saber qué aparato Bluetooth se conectó.", BluetoothDevices.hasPermission(context)) {
                location.openAppSettings()
            })
            add(PermissionRow("Acceso a No molestar", "Cambiar No molestar y el modo silencio.", controls.hasDndAccess()) { controls.openDndAccessSettings() })
            add(PermissionRow("Modificar ajustes del sistema", "Cambiar el brillo.", controls.canWriteSettings()) { controls.openWriteSettings() })
            if (NfcTags.hasNfc(context)) {
                add(PermissionRow("NFC encendido", "Ejecutar automatizaciones con etiquetas NFC.", NfcTags.isOn(context)) { NfcTags.openSettings(context) })
            }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Permisos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${rows.count { it.granted }} de ${rows.size} dados. Solo hace falta el de cada función que uses; " +
                    "si falta uno que una automatización necesita, el aviso aparece en Inicio.",
                style = MaterialTheme.typography.bodySmall,
            )
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (row.granted) "✓" else "✗",
                        color = if (row.granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(22.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(row.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(row.purpose, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = row.open) { Text(if (row.granted) "Ver" else "Dar") }
                }
            }
        }
    }
}
