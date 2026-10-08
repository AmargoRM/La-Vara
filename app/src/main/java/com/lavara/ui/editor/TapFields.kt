package com.lavara.ui.editor

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.lavara.actions.Action
import com.lavara.actions.touchText
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
        supportingText = { Text("El texto del botón o su nombre. Si no lo sabés, usá \"Ver los botones\". En WhatsApp, la flecha de enviar se llama \"Enviar\".") },
        modifier = Modifier.fillMaxWidth(),
    )
    ButtonPicker(action, label, onChange)
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
        "Funciona con el teléfono desbloqueado y la app a la vista. Ponela después de la acción que la abre. " +
            "Para reproducir o pausar música no hace falta: la acción \"Música\" funciona en cualquier app y con el teléfono bloqueado.",
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

/**
 * "Ver los botones": abre la app elegida y La Vara anota los nombres de los botones que se ven. Al volver,
 * muestra la lista para elegir uno. Solo con la app en la lista permitida y la Accesibilidad encendida.
 */
@Composable
private fun ButtonPicker(action: Action.TapInApp, appLabel: String?, onChange: (Action.TapInApp) -> Unit) {
    val context = LocalContext.current
    if (appLabel == null || action.packageName !in AllowedApps(context).get() || !TapService.isEnabled(context)) return
    // Sobrevive si Android recrea la pantalla mientras el usuario está en la otra app.
    var waiting by rememberSaveable(action.packageName) { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val captured by TapService.captured.collectAsState()
    OutlinedButton(
        onClick = {
            val service = TapService.current()
            val launch = context.packageManager.getLaunchIntentForPackage(action.packageName)
            problem = when {
                service == null -> "La Accesibilidad de La Vara todavía no arrancó. Apagala y encendela en Ajustes."
                launch == null -> "No se pudo abrir $appLabel."
                !service.startCapture(action.packageName, appLabel) -> "$appLabel no está en la lista de apps permitidas."
                else -> {
                    waiting = true
                    context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    null
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Ver los botones de $appLabel") }
    Text(
        "Se abre $appLabel: andá a la pantalla donde está el botón, esperá un segundo y volvé a La Vara " +
            "(con la notificación \"Elegí el botón\" o con apps recientes). Vas a ver la lista para elegir.",
        style = MaterialTheme.typography.bodySmall,
    )
    problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

    if (!waiting) return
    val buttons = captured?.takeIf { it.packageName == action.packageName }?.labels
    fun close() {
        waiting = false
        TapService.clearCapture()
    }
    AlertDialog(
        onDismissRequest = { close() },
        title = { Text("Botones de $appLabel") },
        text = {
            if (buttons.isNullOrEmpty()) {
                Text("Todavía no vi ningún botón. Andá a $appLabel, abrí la pantalla donde está el botón y volvé.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(buttons) { name ->
                        TextButton(
                            onClick = {
                                onChange(action.copy(button = name.take(60)))
                                close()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(name, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { close() }) { Text("Cancelar") } },
    )
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

/** Campos de un toque grabado por posición: dónde toca (no se edita, se graba de nuevo) y cuánto espera antes. */
@Composable
internal fun TouchFields(action: Action.TouchScreen, onChange: (Action.TouchScreen) -> Unit) {
    val context = LocalContext.current
    val label = remember(action.packageName) { InstalledApps(context).label(action.packageName) }
    Text(
        "${touchText(action)} en ${label ?: action.packageName}. Se grabó con \"Grabar toques\"; para cambiar el lugar, grabalo de nuevo.",
        style = MaterialTheme.typography.bodyMedium,
    )
    var seconds by remember(action.packageName, action.x, action.y) { mutableStateOf((action.pauseMillis / 1000.0).toString().removeSuffix(".0")) }
    OutlinedTextField(
        value = seconds,
        onValueChange = { new ->
            seconds = new.filter { it.isDigit() || it == '.' || it == ',' }.take(4)
            seconds.replace(',', '.').toDoubleOrNull()?.let { onChange(action.copy(pauseMillis = (it * 1000).toLong())) }
        },
        label = { Text("Esperar antes de tocar (segundos)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = { Text("Lo que tarda la pantalla en cambiar. Entre 0 y ${AutomationDraft.MAX_DELAY_SECONDS}.") },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "Toca el mismo lugar de la pantalla: si la app cambia de lugar las cosas (otro orden, un anuncio, el teléfono girado), " +
            "puede tocar otra cosa. Funciona con el teléfono desbloqueado y la app a la vista.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (action.packageName.isNotBlank() && action.packageName !in AllowedApps(context).get()) {
        Text(
            "Esta app no está en tu lista de apps permitidas: la acción va a fallar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
