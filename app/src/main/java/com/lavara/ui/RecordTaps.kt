package com.lavara.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lavara.LaVaraApp
import com.lavara.automation.AutomationDraft
import com.lavara.system.accessibility.AllowedApps
import com.lavara.system.accessibility.TapService
import com.lavara.system.device.InstalledApp
import com.lavara.ui.editor.AllowAppDialog
import com.lavara.ui.editor.AppPickerDialog

/**
 * "Mirame y repetí": el usuario elige una app (de la lista permitida), La Vara la abre y anota el nombre de cada
 * botón que él toca. Al volver a La Vara, muestra lo grabado y lo convierte en una automatización nueva que
 * se abre en el editor para revisarla y guardarla.
 */
@Composable
fun RecordTapsButton(onCreate: (AutomationDraft) -> Unit) {
    val context = LocalContext.current
    val resumeCount = LocalResumeCount.current
    val recording by TapService.recording.collectAsState()
    // En qué "vuelta al frente" empezó la grabación: la siguiente vez que La Vara vuelve, la grabación termina.
    var startedAt by rememberSaveable { mutableIntStateOf(-1) }
    var picking by remember { mutableStateOf(false) }
    var confirmAllow by remember { mutableStateOf<InstalledApp?>(null) }
    var needsAccess by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(resumeCount) {
        if (startedAt >= 0 && resumeCount > startedAt) {
            startedAt = -1
            TapService.finishRecording()
        }
    }

    fun start(app: InstalledApp) {
        val service = TapService.current()
        val launch = context.packageManager.getLaunchIntentForPackage(app.packageName)
        problem = when {
            service == null -> "La Accesibilidad de La Vara todavía no arrancó. Apagala y encendela en Ajustes."
            launch == null -> "No se pudo abrir ${app.label}."
            !service.startRecording(app.packageName, app.label) -> "${app.label} no está en la lista de apps permitidas."
            else -> {
                startedAt = resumeCount
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                null
            }
        }
    }

    OutlinedButton(
        onClick = {
            problem = null
            if (TapService.isEnabled(context)) picking = true else needsAccess = true
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("⏺  Grabar toques en una app") }
    problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

    if (picking) {
        AppPickerDialog(
            title = "¿En qué app grabar?",
            onDismiss = { picking = false },
            onPick = { app ->
                picking = false
                if (app.packageName in AllowedApps(context).get()) start(app) else confirmAllow = app
            },
        )
    }

    confirmAllow?.let { app ->
        AllowAppDialog(
            appLabel = app.label,
            onAllow = {
                confirmAllow = null
                AllowedApps(context).add(app.packageName)
                (context.applicationContext as LaVaraApp).container.logger
                    .info("Accesibilidad", "${app.label} agregada a las apps donde La Vara puede tocar botones.")
                start(app)
            },
            onDismiss = { confirmAllow = null },
        )
    }

    if (needsAccess) {
        AlertDialog(
            onDismissRequest = { needsAccess = false },
            title = { Text("Falta la Accesibilidad") },
            text = {
                Text(
                    "Para ver qué botones tocás, La Vara necesita el permiso de Accesibilidad. En la pantalla que se abre: " +
                        "Apps instaladas (o Apps descargadas) → \"La Vara: tocar botones\" → encender. Si está gris o dice " +
                        "\"configuración restringida\": Ajustes → Aplicaciones → La Vara → ⋮ → \"Permitir configuración restringida\".",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    needsAccess = false
                    TapService.openSettings(context)
                }) { Text("Abrir Accesibilidad") }
            },
            dismissButton = { TextButton(onClick = { needsAccess = false }) { Text("Cancelar") } },
        )
    }

    val done = recording?.takeIf { !it.active } ?: return
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Toques en ${done.appLabel}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (done.labels.isEmpty()) {
                    Text(
                        "No se grabó ningún botón. Algunas apps no le avisan a Android cuando tocás algo (por ejemplo, mapas o juegos); " +
                            "en esas no se puede grabar. Podés armarla a mano con la acción \"Tocar un botón\" y \"Ver los botones\".",
                    )
                } else {
                    Text("La Vara va a abrir ${done.appLabel} y tocar, en orden:")
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        itemsIndexed(done.labels) { index, label ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${index + 1}. $label", modifier = Modifier.weight(1f))
                                TextButton(onClick = { TapService.updateRecording(done.remove(index)) }) { Text("Quitar") }
                            }
                        }
                    }
                }
                if (done.skipped > 0) {
                    Text(
                        "${done.skipped} ${if (done.skipped == 1) "toque no se pudo grabar" else "toques no se pudieron grabar"}: " +
                            "el botón no tiene nombre visible (por ejemplo, un ícono sin texto) o era un campo para escribir. " +
                            "Lo que escribís nunca se graba.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            if (done.labels.isNotEmpty()) {
                TextButton(onClick = {
                    TapService.discardRecording()
                    onCreate(done.toDraft())
                }) { Text("Crear automatización") }
            }
        },
        dismissButton = { TextButton(onClick = { TapService.discardRecording() }) { Text("Descartar") } },
    )
}
