package com.lavara.ui.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lavara.actions.Action
import com.lavara.system.device.InstalledApps
import com.lavara.system.device.MusicApps

/** Con qué app de música habla la acción "Música": cualquiera (como los audífonos) o una elegida. */
@Composable
internal fun MusicAppField(action: Action.MediaControl, onChange: (Action.MediaControl) -> Unit) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    val label = remember(action.packageName) {
        if (action.packageName.isBlank()) null else InstalledApps(context).label(action.packageName) ?: action.packageName
    }
    OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
        Text(if (label == null) "App: cualquiera · cambiar" else "App: $label · cambiar")
    }
    Text(
        if (label == null) {
            "Le llega a la app que esté sonando o a la última que sonó, como los botones de los audífonos. " +
                "Si esa app está cerrada del todo, puede no arrancar: elegí la app para despertarla."
        } else {
            "La Vara le habla directo a $label, aunque esté cerrada y el teléfono bloqueado. " +
                "Si $label no lo acepta, el Historial lo dice."
        },
        style = MaterialTheme.typography.bodySmall,
    )
    if (!picking) return
    val apps = remember { MusicApps(context).list() }
    AlertDialog(
        onDismissRequest = { picking = false },
        title = { Text("¿Qué app de música?") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                item {
                    TextButton(onClick = { onChange(action.copy(packageName = "")); picking = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("Cualquiera (la que esté sonando o la última)", modifier = Modifier.fillMaxWidth())
                    }
                }
                items(apps) { app ->
                    TextButton(onClick = { onChange(action.copy(packageName = app.packageName)); picking = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(app.label, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancelar") } },
    )
}
