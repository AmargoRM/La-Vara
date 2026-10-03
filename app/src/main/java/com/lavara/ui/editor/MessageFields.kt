package com.lavara.ui.editor

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import com.lavara.LaVaraApp
import com.lavara.actions.Phone
import com.lavara.system.device.ContactPhones
import com.lavara.system.device.SmsSender

/**
 * Botón "Elegir de mis contactos" y campo del número. Con [international] (WhatsApp), el número elegido
 * se completa con el código de país de la SIM.
 */
@Composable
internal fun PhoneField(phone: String, contactName: String, international: Boolean, onChange: (phone: String, name: String) -> Unit) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val (number, name) = ContactPhones.read(context, uri) ?: return@rememberLauncherForActivityResult
        onChange(if (international) ContactPhones.withCountryCode(context, number) else number, name)
    }
    OutlinedButton(onClick = { picker.launch(ContactPhones.pickIntent()) }, modifier = Modifier.fillMaxWidth()) {
        Text(if (contactName.isBlank()) "Elegir de mis contactos" else "$contactName · cambiar")
    }
    val missingCode = international && phone.any { it.isDigit() } && !Phone.hasCountryCode(phone)
    OutlinedTextField(
        value = phone,
        onValueChange = { typed ->
            val clean = typed.filter { it.isDigit() || it in "+ -()" }.take(25)
            // Corregir el número del mismo contacto conserva el nombre; uno distinto lo borra.
            val same = clean.filter { it.isDigit() }.takeLast(4) == phone.filter { it.isDigit() }.takeLast(4)
            onChange(clean, if (same) contactName else "")
        },
        label = { Text("Número") },
        singleLine = true,
        isError = missingCode,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        supportingText = {
            Text(
                when {
                    missingCode -> "Falta el código de país adelante: 506 para Costa Rica."
                    international -> "Con código de país (ej.: 506 8888 7777)."
                    else -> "Como lo marcarías en el teléfono."
                },
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Texto del mensaje, con variables. */
@Composable
internal fun MessageField(text: String, onChange: (String) -> Unit) {
    OutlinedTextField(text, onChange, label = { Text("Mensaje") }, modifier = Modifier.fillMaxWidth())
}

/**
 * Pide el permiso de SMS. Si Android no lo da (o no muestra la pregunta, porque en apps instaladas desde
 * un APK puede ser "configuración restringida"), explica cómo darlo desde Ajustes.
 */
@Composable
internal fun SmsPermissionHint() {
    val context = LocalContext.current
    val sender = remember { SmsSender(context) }
    var granted by remember { mutableStateOf(sender.hasPermission()) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
        (context.applicationContext as LaVaraApp).container.logger
            .info("Permisos", "Permiso para enviar SMS: ${if (ok) "concedido" else "negado"}")
    }
    if (granted || sender.hasPermission()) return
    Text(
        if (!denied) "Falta el permiso para enviar SMS. Android te va a preguntar: tocá Permitir."
        else "Android no dio el permiso. Si no apareció la pregunta o dice \"configuración restringida\": " +
            "tocá \"Abrir ajustes de La Vara\", después ⋮ (arriba a la derecha) → \"Permitir configuración restringida\", " +
            "y luego Permisos → SMS → Permitir.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
    if (!denied) {
        OutlinedButton(onClick = { launcher.launch(Manifest.permission.SEND_SMS) }) { Text("Dar el permiso") }
    } else {
        OutlinedButton(onClick = { sender.openAppSettings() }) { Text("Abrir ajustes de La Vara") }
    }
}
