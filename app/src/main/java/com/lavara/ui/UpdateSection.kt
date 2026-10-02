package com.lavara.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lavara.BuildConfig
import com.lavara.core.AppContainer
import com.lavara.system.update.ReleaseInfo
import com.lavara.system.update.TokenExpiry
import com.lavara.system.update.UpdateStatus
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateTime = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
private val dateOnly = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/**
 * Sección "Actualizaciones": token de GitHub, revisión manual y descarga/instalación.
 * [installRequest] cambia cada vez que la app se abre desde la notificación de versión nueva.
 */
@Composable
fun UpdateSection(container: AppContainer, installRequest: Int) {
    val manager = container.updateManager
    val installer = container.apkInstaller
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf(manager.status()) }
    var hasToken by remember { mutableStateOf(manager.tokenStore.hasToken()) }
    var editingToken by remember { mutableStateOf(!hasToken) }
    var tokenText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) note = "Sin el permiso de notificaciones, La Vara no puede avisarte de versiones nuevas. Igual podés revisar con el botón."
    }

    fun check() {
        scope.launch {
            busy = true
            note = null
            status = manager.check(notify = false)
            busy = false
        }
    }

    fun downloadAndInstall(release: ReleaseInfo) {
        if (!installer.canInstall()) {
            note = "Android necesita que le permitas a La Vara instalar apps. Activá \"Permitir de esta fuente\", volvé y tocá de nuevo el botón."
            installer.openInstallPermissionSettings()
            return
        }
        scope.launch {
            busy = true
            note = null
            progress = 0f
            val error = manager.download(release) { progress = it }
            progress = null
            busy = false
            if (error != null) {
                note = error
                return@launch
            }
            val apk = manager.apkFile()
            val invalid = installer.validate(apk, BuildConfig.VERSION_CODE)
            if (invalid != null) {
                note = invalid
                return@launch
            }
            note = "Descarga completa. En la ventana de Android tocá \"Actualizar\"."
            installer.install(apk)
        }
    }

    LaunchedEffect(installRequest) {
        if (installRequest > 0 && hasToken) {
            busy = true
            status = manager.check(notify = false)
            busy = false
            status.available?.let { downloadAndInstall(it) }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Actualizaciones", style = MaterialTheme.typography.titleMedium)

            Text(statusText(status), style = MaterialTheme.typography.bodyMedium)
            status.tokenExpiry?.let { Text(expiryText(it), style = MaterialTheme.typography.bodySmall) }

            status.available?.let { release ->
                Button(onClick = { downloadAndInstall(release) }, enabled = !busy) {
                    Text("Descargar e instalar ${release.versionName}")
                }
            }
            progress?.let { fraction ->
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                Text("Descargando… ${(fraction * 100).toInt()} %", style = MaterialTheme.typography.bodySmall)
            }

            if (hasToken) {
                OutlinedButton(onClick = { check() }, enabled = !busy) { Text("Buscar actualización") }
            }

            if (editingToken) {
                OutlinedTextField(
                    value = tokenText,
                    onValueChange = { tokenText = it },
                    label = { Text("Token de GitHub (empieza con github_pat_)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        manager.tokenStore.save(tokenText.trim())
                        tokenText = ""
                        hasToken = true
                        editingToken = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !container.updateNotifier.canNotify()) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        check()
                    },
                    enabled = tokenText.isNotBlank() && !busy,
                ) { Text("Guardar token") }
            } else {
                OutlinedButton(onClick = { editingToken = true }, enabled = !busy) { Text("Cambiar token") }
            }

            note?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

private fun statusText(status: UpdateStatus): String {
    if (status.lastCheckMillis == 0L) return status.lastMessage
    val time = Instant.ofEpochMilli(status.lastCheckMillis).atZone(ZoneId.systemDefault()).format(dateTime)
    return "${status.lastMessage}\nÚltima revisión: $time"
}

private fun expiryText(expiry: LocalDate): String {
    val days = TokenExpiry.daysLeft(expiry, LocalDate.now())
    return when {
        days < 0 -> "El token venció el ${expiry.format(dateOnly)}. Pegá uno nuevo."
        TokenExpiry.shouldWarn(expiry, LocalDate.now()) -> "⚠️ El token vence el ${expiry.format(dateOnly)} (en $days días). Creá uno nuevo."
        else -> "El token vence el ${expiry.format(dateOnly)}."
    }
}
