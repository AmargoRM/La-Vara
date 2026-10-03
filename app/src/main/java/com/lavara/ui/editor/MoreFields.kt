package com.lavara.ui.editor

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lavara.LaVaraApp
import com.lavara.actions.Action
import com.lavara.automation.AutomationDraft
import com.lavara.automation.AutomationEngine
import com.lavara.conditions.Condition
import com.lavara.system.connectivity.WifiWatcher
import com.lavara.system.nfc.NfcTags
import com.lavara.system.notifications.NotificationWatchService
import com.lavara.triggers.Trigger
import com.lavara.triggers.Weekday
import com.lavara.ui.LocalResumeCount

// ---------- Disparadores nuevos ----------

/** "Al llegar una notificación": de qué app, qué texto, y el permiso "Acceso a notificaciones". */
@Composable
internal fun NotificationTriggerFields(trigger: Trigger.Notification, onChange: (Trigger.Notification) -> Unit) {
    val context = LocalContext.current
    val resume = LocalResumeCount.current
    val granted = remember(resume) { NotificationWatchService.isEnabled(context) }
    var picking by remember { mutableStateOf(false) }

    OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
        if (trigger.packageName.isNotBlank()) AppIcon(trigger.packageName)
        Text(
            "App: " + trigger.appName.ifBlank { trigger.packageName }.ifBlank { "cualquiera" },
            modifier = Modifier.padding(start = 10.dp).weight(1f),
        )
    }
    if (trigger.packageName.isNotBlank()) {
        TextButton(onClick = { onChange(trigger.copy(packageName = "", appName = "")) }) { Text("Usar cualquier app") }
    }
    OutlinedTextField(
        value = trigger.textContains,
        onValueChange = { onChange(trigger.copy(textContains = it)) },
        label = { Text("Solo si dice… (opcional)") },
        placeholder = { Text("Ej.: Mamá, o llegué") },
        supportingText = { Text("Se busca en el título y en el texto, sin importar mayúsculas. Vacío = cualquier notificación.") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "La Vara lee la notificación solo para comparar y no la guarda. En Historial solo queda de qué app vino. " +
            "Si una app repite el aviso, usá \"Tiempo mínimo entre ejecuciones\" (paso 3) para que no se dispare varias veces.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (!granted) {
        Text(
            "Falta el permiso \"Acceso a notificaciones\": sin él, Android no le pasa las notificaciones a La Vara. En la pantalla que se abre, " +
                "activá \"La Vara: disparar con notificaciones\". Si dice \"configuración restringida\": Ajustes → Aplicaciones → La Vara → ⋮ → " +
                "\"Permitir configuración restringida\", y volvé a intentarlo.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = { NotificationWatchService.openSettings(context) }) { Text("Dar acceso a notificaciones") }
    }

    if (picking) {
        AppPickerDialog(
            title = "¿De qué app?",
            onDismiss = { picking = false },
            onPick = { app ->
                picking = false
                onChange(trigger.copy(packageName = app.packageName, appName = app.label))
            },
        )
    }
}

/** "Etiqueta NFC": nombre y grabar la etiqueta con su código. */
@Composable
internal fun NfcTriggerFields(trigger: Trigger.Nfc, onChange: (Trigger.Nfc) -> Unit) {
    val context = LocalContext.current
    val resume = LocalResumeCount.current
    val hasNfc = remember { NfcTags.hasNfc(context) }
    val nfcOn = remember(resume) { NfcTags.isOn(context) }
    var writing by remember { mutableStateOf(false) }

    if (!hasNfc) {
        Text("Este teléfono no tiene NFC: este disparador no va a funcionar aquí.", color = MaterialTheme.colorScheme.error)
        return
    }
    OutlinedTextField(
        value = trigger.tagName,
        onValueChange = { onChange(trigger.copy(tagName = it)) },
        label = { Text("Nombre de la etiqueta (ej.: Mesa de noche)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        if (trigger.tagId.isBlank()) "Todavía no grabaste ninguna etiqueta para esta automatización."
        else "Etiqueta grabada (código ${trigger.tagId}). Para usarla, acercá el teléfono desbloqueado a la etiqueta.",
        style = MaterialTheme.typography.bodyMedium,
    )
    if (!nfcOn) {
        Text("El NFC del teléfono está apagado.", color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = { NfcTags.openSettings(context) }) { Text("Encender NFC") }
    }
    OutlinedButton(onClick = { writing = true }, enabled = nfcOn, modifier = Modifier.fillMaxWidth()) {
        Text(if (trigger.tagId.isBlank()) "Grabar una etiqueta" else "Grabar otra etiqueta")
    }
    Text(
        "Android solo lee etiquetas con la pantalla encendida y desbloqueada. Sirven etiquetas NFC comunes (NTAG213, 215 o 216).",
        style = MaterialTheme.typography.bodySmall,
    )

    if (writing) {
        NfcWriteDialog(
            onDismiss = { writing = false },
            onWritten = { code ->
                onChange(trigger.copy(tagId = code))
                (context.applicationContext as LaVaraApp).container.logger
                    .info("NFC", "Etiqueta grabada con el código $code" + trigger.tagName.let { if (it.isBlank()) "" else " ($it)" })
            },
        )
    }
}

@Composable
private fun NfcWriteDialog(onDismiss: () -> Unit, onWritten: (String) -> Unit) {
    val context = LocalContext.current
    val activity = remember { context.findActivity() }
    val code = remember { NfcTags.newCode() }
    var message by remember { mutableStateOf("Acercá la etiqueta a la parte de atrás del teléfono y dejala quieta.") }
    var done by remember { mutableStateOf(false) }

    DisposableEffect(activity) {
        if (activity == null) {
            message = "No se pudo usar el NFC desde esta pantalla."
        } else {
            NfcTags.startWriting(activity, code) { error ->
                activity.runOnUiThread {
                    if (error == null) {
                        done = true
                        message = "Listo: etiqueta grabada. Guardá la automatización para empezar a usarla."
                        onWritten(code)
                        NfcTags.stop(activity)
                    } else {
                        message = error
                    }
                }
            }
        }
        onDispose { activity?.let { NfcTags.stop(it) } }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (done) "Etiqueta grabada" else "Grabar etiqueta NFC") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (done) "Listo" else "Cancelar") } },
    )
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

// ---------- Condiciones nuevas ----------

@Composable
internal fun WifiConditionFields(condition: Condition.WifiConnected, onChange: (Condition.WifiConnected) -> Unit) {
    val context = LocalContext.current
    var note by remember { mutableStateOf<String?>(null) }
    OutlinedTextField(
        value = condition.ssid,
        onValueChange = { onChange(condition.copy(ssid = it)) },
        label = { Text("Nombre de la red (vacío = cualquiera)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(onClick = {
        val current = WifiWatcher.currentSsid(context)
        if (current != null) {
            note = null
            onChange(condition.copy(ssid = current))
        } else {
            note = "No pude leer la red actual. Hace falta estar conectado, con la ubicación encendida y el permiso de ubicación de La Vara."
        }
    }, modifier = Modifier.fillMaxWidth()) { Text("Usar la red actual") }
    note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    if (condition.ssid.isNotBlank()) {
        Text(
            "Para leer el nombre de la red con La Vara cerrada, Android pide la ubicación \"Permitir todo el tiempo\".",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
internal fun ChargingConditionFields(condition: Condition.Charging, onChange: (Condition.Charging) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Choice("Cargando", condition.charging, Modifier.weight(1f)) { onChange(Condition.Charging(true)) }
        Choice("Sin cargador", !condition.charging, Modifier.weight(1f)) { onChange(Condition.Charging(false)) }
    }
}

@Composable
internal fun DaysConditionFields(condition: Condition.DaysOfWeek, onChange: (Condition.DaysOfWeek) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Weekday.entries.forEach { day ->
            val on = day in condition.days
            val label = "LMXJVSD"[day.isoNumber - 1].toString()
            val toggle = {
                val days = if (on) condition.days - day else (condition.days + day).sortedBy { it.isoNumber }
                onChange(Condition.DaysOfWeek(days))
            }
            val mod = Modifier.weight(1f).height(44.dp)
            if (on) Button(onClick = toggle, modifier = mod, shape = CircleShape, contentPadding = PaddingValues(0.dp)) { Text(label) }
            else OutlinedButton(onClick = toggle, modifier = mod, shape = CircleShape, contentPadding = PaddingValues(0.dp)) { Text(label) }
        }
    }
    if (condition.days.isEmpty()) Text("Ningún día marcado = cualquier día.", style = MaterialTheme.typography.bodySmall)
}

// ---------- Esperar ----------

private enum class WaitUnit(val label: String, val seconds: Long) {
    SECONDS("segundos", 1), MINUTES("minutos", 60), HOURS("horas", 3600),
}

/** "Esperar": cantidad y unidad (segundos, minutos u horas), hasta 24 horas. */
@Composable
internal fun WaitFields(action: Action.Delay, onChange: (Action.Delay) -> Unit) {
    // La unidad se elige según lo guardado: 600 s se muestra como 10 minutos.
    var unit by remember {
        mutableStateOf(
            when {
                action.seconds >= 3600 && action.seconds % 3600 == 0L -> WaitUnit.HOURS
                action.seconds >= 60 && action.seconds % 60 == 0L -> WaitUnit.MINUTES
                else -> WaitUnit.SECONDS
            },
        )
    }
    var text by remember(unit) { mutableStateOf((action.seconds / unit.seconds).toString()) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WaitUnit.entries.forEach { option ->
            Choice(option.label, unit == option, Modifier.weight(1f)) {
                unit = option
                val amount = text.toLongOrNull() ?: 0
                onChange(Action.Delay(amount * option.seconds))
            }
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new.filter { it.isDigit() }.take(5)
            text.toLongOrNull()?.let { onChange(Action.Delay(it * unit.seconds)) }
        },
        label = { Text("Cuántos ${unit.label}") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = {
            Text(
                if (action.seconds <= AutomationEngine.INLINE_DELAY_SECONDS) "Máximo 24 horas."
                else "Máximo 24 horas. Las acciones de abajo siguen con una alarma, aunque el teléfono esté bloqueado o La Vara cerrada.",
            )
        },
        isError = action.seconds > AutomationDraft.MAX_WAIT_SECONDS,
        modifier = Modifier.fillMaxWidth(),
    )
}
