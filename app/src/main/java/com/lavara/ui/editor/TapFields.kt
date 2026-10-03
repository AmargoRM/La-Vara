package com.lavara.ui.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.lavara.actions.Action
import com.lavara.automation.AutomationDraft
import com.lavara.system.accessibility.AllowedApps
import com.lavara.system.accessibility.TapService
import com.lavara.system.device.InstalledApps

/** Campos de "Tocar un botón en otra app": la app (de la lista permitida), el botón y cuánto esperar. */
@Composable
internal fun TapFields(action: Action.TapInApp, onPickApp: () -> Unit, onChange: (Action.TapInApp) -> Unit) {
    val context = LocalContext.current
    val label = remember(action.packageName) { InstalledApps(context).label(action.packageName) }
    OutlinedButton(onClick = onPickApp, modifier = Modifier.fillMaxWidth()) {
        Text(
            when {
                action.packageName.isBlank() -> "Elegir la app…"
                label == null -> "No está instalada (${action.packageName}). Tocá para elegir otra."
                else -> "En $label · cambiar"
            },
        )
    }
    OutlinedTextField(
        value = action.button,
        onValueChange = { onChange(action.copy(button = it.take(60))) },
        label = { Text("Botón que toca") },
        placeholder = { Text("Ej.: Enviar") },
        singleLine = true,
        supportingText = { Text("El texto del botón o su nombre. En WhatsApp, la flecha de enviar se llama \"Enviar\".") },
        modifier = Modifier.fillMaxWidth(),
    )
    var seconds by remember(action.waitSeconds) { mutableStateOf(action.waitSeconds.toString()) }
    OutlinedTextField(
        value = seconds,
        onValueChange = { new ->
            seconds = new.filter { it.isDigit() }.take(2)
            seconds.toIntOrNull()?.let { onChange(action.copy(waitSeconds = it)) }
        },
        label = { Text("Esperar hasta (segundos)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = { Text("Lo que tarda la app en abrirse. Entre 1 y ${AutomationDraft.MAX_DELAY_SECONDS}.") },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "Funciona con el teléfono desbloqueado y la app a la vista. Ponela después de la acción que la abre.",
        style = MaterialTheme.typography.bodySmall,
    )
    val notAllowed = action.packageName.isNotBlank() && action.packageName !in AllowedApps(context).get()
    if (notAllowed) {
        Text(
            "Esta app no está en tu lista de apps permitidas: la acción va a fallar. Elegila de nuevo para permitirla.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (!TapService.isEnabled(context)) {
        Text(
            "Falta encender el permiso de Accesibilidad. En la pantalla que se abre: Apps instaladas (o Apps descargadas) → " +
                "\"La Vara: tocar botones\" → encender. Si está gris o dice \"configuración restringida\": Ajustes → Aplicaciones → " +
                "La Vara → ⋮ → \"Permitir configuración restringida\", y volvé a intentarlo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedButton(onClick = { TapService.openSettings(context) }) { Text("Abrir Accesibilidad") }
    }
}

/** Confirmación antes de agregar una app a la lista donde La Vara puede tocar botones. */
@Composable
internal fun AllowAppDialog(appLabel: String, onAllow: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Permitir tocar botones en $appLabel?") },
        text = {
            Text(
                "Cuando una automatización lo pida, La Vara va a poder ver la pantalla de $appLabel y tocar el botón que elegiste. " +
                    "No guarda lo que ve. No agregues apps de banco, contraseñas ni correo. Podés quitarla cuando quieras en el menú ☰.",
            )
        },
        confirmButton = { TextButton(onClick = onAllow) { Text("Permitir") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
