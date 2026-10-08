package com.lavara.ui.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.lavara.actions.Action
import com.lavara.system.device.InstalledApps
import com.lavara.system.notifications.NotificationWatchService

/** Campos de "Responder una notificación": la app, a quién (opcional) y el texto. */
@Composable
internal fun ReplyNotificationFields(action: Action.ReplyToNotification, onChange: (Action.ReplyToNotification) -> Unit) {
    NotificationAppButton(action.packageName) { onChange(action.copy(packageName = it)) }
    FromField(action.from) { onChange(action.copy(from = it)) }
    OutlinedTextField(
        value = action.text,
        onValueChange = { onChange(action.copy(text = it.take(500))) },
        label = { Text("Respuesta") },
        placeholder = { Text("Ej.: Voy manejando, te escribo luego") },
        supportingText = { Text("Podés usar %battery, %time y %date.") },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "Usa el botón \"Responder\" de la notificación, sin abrir la app: funciona con el teléfono bloqueado. " +
            "Solo sirve si hay una notificación sin leer de ese chat (por ejemplo, con el disparador \"Al llegar una notificación\").",
        style = MaterialTheme.typography.bodySmall,
    )
    AccessWarning()
}

/** Campos de "Tocar un botón de una notificación": la app, a quién (opcional) y el botón. */
@Composable
internal fun TapNotificationFields(action: Action.TapNotificationButton, onChange: (Action.TapNotificationButton) -> Unit) {
    NotificationAppButton(action.packageName) { onChange(action.copy(packageName = it)) }
    FromField(action.from) { onChange(action.copy(from = it)) }
    OutlinedTextField(
        value = action.button,
        onValueChange = { onChange(action.copy(button = it.take(60))) },
        label = { Text("Botón de la notificación") },
        placeholder = { Text("Ej.: Marcar como leído") },
        singleLine = true,
        supportingText = { Text("El texto tal como aparece debajo de la notificación.") },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "Toca el botón sin abrir la app: funciona con el teléfono bloqueado, si la notificación está a la vista en la cortina.",
        style = MaterialTheme.typography.bodySmall,
    )
    AccessWarning()
}

@Composable
private fun NotificationAppButton(packageName: String, onPick: (String) -> Unit) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    val label = remember(packageName) { if (packageName.isBlank()) null else InstalledApps(context).label(packageName) ?: packageName }
    OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
        Text(if (label == null) "Elegir de qué app es la notificación…" else "Notificación de $label · cambiar")
    }
    if (picking) {
        AppPickerDialog(
            title = "¿De qué app es la notificación?",
            onDismiss = { picking = false },
            onPick = {
                picking = false
                onPick(it.packageName)
            },
        )
    }
}

@Composable
private fun FromField(from: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = from,
        onValueChange = { onChange(it.take(60)) },
        label = { Text("De quién (opcional)") },
        placeholder = { Text("Ej.: Esteban") },
        singleLine = true,
        supportingText = { Text("Parte del título de la notificación. Vacío = la más nueva de esa app.") },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AccessWarning() {
    val context = LocalContext.current
    if (NotificationWatchService.isEnabled(context)) return
    Text(
        "Falta el \"Acceso a notificaciones\" de La Vara.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
    OutlinedButton(onClick = { NotificationWatchService.openSettings(context) }) { Text("Dar acceso") }
}
