package com.lavara.ui.editor

import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.lavara.system.device.InstalledApps
import com.lavara.system.device.InstalledApp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.produceState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lavara.actions.Action
import com.lavara.actions.DndMode
import com.lavara.actions.NavigationApp
import com.lavara.actions.RingerMode
import com.lavara.actions.SystemPanel
import com.lavara.actions.VolumeStream
import com.lavara.actions.needsDndAccess
import com.lavara.system.device.SystemControls
import com.lavara.system.accessibility.AllowedApps
import com.lavara.LaVaraApp
import com.lavara.automation.Automation
import com.lavara.automation.AutomationDraft
import com.lavara.automation.OnError
import com.lavara.conditions.Comparison
import com.lavara.conditions.Condition
import com.lavara.core.AppContainer
import com.lavara.core.TimeText
import com.lavara.triggers.BatteryDirection
import com.lavara.triggers.ConnectionEvent
import com.lavara.system.connectivity.BluetoothDevices
import com.lavara.system.connectivity.WifiWatcher
import com.lavara.system.location.LocationAccess
import com.lavara.triggers.LocationTransition
import com.lavara.triggers.NextAlarm
import com.lavara.triggers.PowerEvent
import com.lavara.triggers.Trigger
import com.lavara.triggers.Weekday
import com.lavara.system.notifications.NotificationWatchService
import com.lavara.ui.actionTitle
import com.lavara.ui.daysText
import com.lavara.ui.describe
import com.lavara.ui.timeText
import com.lavara.ui.untilText
import com.lavara.ui.whenText
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.UUID

private val STEPS = listOf("¿CUÁNDO?", "¿SI?", "¿HACER QUÉ?")

/**
 * Editor en tres pasos: ¿CUÁNDO? → ¿SI? → ¿HACER QUÉ?. [automationId] null = automatización nueva.
 * Guarda con el mismo JSON de siempre y vuelve a programar las alarmas.
 */
@Composable
fun EditorScreen(container: AppContainer, automationId: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by remember { container.automationRepository.observeAll() }.collectAsState(initial = emptyList())

    var draft by remember { mutableStateOf<AutomationDraft?>(null) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var askDiscard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var initial by remember { mutableStateOf<AutomationDraft?>(null) }

    LaunchedEffect(automationId) {
        val loaded = automationId?.let { container.automationRepository.find(it) }?.let { AutomationDraft.from(it) }
            ?: AutomationDraft.new()
        draft = loaded
        initial = loaded
    }

    fun close() {
        if (draft != null && draft != initial) askDiscard = true else onClose()
    }
    BackHandler { if (step > 0) step-- else close() }

    val current = draft
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (current == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Surface
        }
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { close() }) { Text("✕", fontSize = 22.sp) }
                Text(
                    if (current.original == null) "Nueva automatización" else current.name.ifBlank { "Sin nombre" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            // El nombre se ve en el paso 1 y, si sigue vacío, también en el 3, para poder guardar sin volver.
            if (step == 0 || (step == 2 && current.name.isBlank())) {
                OutlinedTextField(
                    value = current.name,
                    onValueChange = { draft = current.copy(name = it) },
                    label = { Text("Nombre de la automatización") },
                    isError = step == 2,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            StepIndicator(step, Modifier.padding(16.dp))

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (step) {
                    0 -> WhenStep(context, container, current) { draft = it }
                    1 -> IfStep(context, current) { draft = it }
                    else -> DoStep(current, others = all.filter { it.id != current.original?.id }) { draft = it }
                }
            }

            val problems = current.problems()
            if (step == 2 && problems.isNotEmpty()) {
                Text(
                    problems.joinToString("\n"),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { if (step == 0) close() else step-- }, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text(if (step == 0) "Cancelar" else "Atrás")
                }
                if (step < 2) {
                    Button(onClick = { step++ }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Siguiente") }
                } else {
                    Button(
                        enabled = problems.isEmpty() && !saving,
                        onClick = {
                            saving = true
                            scope.launch {
                                val saved = save(container, current)
                                Toast.makeText(context, savedMessage(context, container, saved), Toast.LENGTH_LONG).show()
                                onClose()
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) { Text("Guardar") }
                }
            }
        }
    }

    if (askDiscard) {
        AlertDialog(
            onDismissRequest = { askDiscard = false },
            title = { Text("¿Salir sin guardar?") },
            text = { Text("Los cambios que hiciste se pierden.") },
            confirmButton = { TextButton(onClick = { askDiscard = false; onClose() }) { Text("Salir sin guardar") } },
            dismissButton = { TextButton(onClick = { askDiscard = false }) { Text("Seguir editando") } },
        )
    }
}

private suspend fun save(container: AppContainer, draft: AutomationDraft): Automation {
    val now = container.clock.now().toInstant().toEpochMilli()
    val automation = draft.toAutomation(newId = "a-" + UUID.randomUUID().toString().take(8), now = now)
    // Si cambiaron las acciones, una espera larga pendiente seguiría desde la acción equivocada.
    if (draft.original != null && draft.original.actions != automation.actions) container.laterAlarms.cancel(automation.id)
    container.automationRepository.save(automation)
    val what = if (draft.original == null) "creada con el editor" else "editada"
    container.logger.info("Automatizaciones", "${automation.name}: $what", automation.id)
    container.refreshTriggers("${automation.name}: $what")
    return automation
}

private fun savedMessage(context: Context, container: AppContainer, automation: Automation): String {
    val trigger = automation.trigger
    if (!automation.enabled) return "Guardada. Está desactivada: activala en Inicio."
    if (trigger is Trigger.Battery || trigger is Trigger.Power) return "Guardada. La Vara queda atenta a la batería y al cargador."
    if (trigger is Trigger.Location) {
        return if (container.locationAccess.hasBackground()) "Guardada. La Vara avisa cuando ${describe(context, trigger).lowercase()}."
        else "Guardada, pero falta el permiso de ubicación \"Permitir todo el tiempo\"."
    }
    if (trigger is Trigger.Bluetooth) {
        return if (BluetoothDevices.hasPermission(context)) "Guardada. La Vara avisa ${describe(context, trigger).lowercase()}."
        else "Guardada, pero falta el permiso \"Dispositivos cercanos\"."
    }
    if (trigger is Trigger.Wifi) return "Guardada. La Vara queda atenta al Wi-Fi."
    if (trigger is Trigger.Notification) {
        return if (NotificationWatchService.isEnabled(context)) "Guardada. La Vara queda atenta a las notificaciones."
        else "Guardada, pero falta el permiso \"Acceso a notificaciones\"."
    }
    if (trigger is Trigger.Nfc) return "Guardada. Acercá el teléfono desbloqueado a la etiqueta para ejecutarla."
    if (trigger !is Trigger.Time) return "Guardada."
    val now = container.clock.now()
    val at = NextAlarm.after(trigger, now)
    return "Guardada. Próxima vez: ${whenText(context, at, now)}, dentro de ${untilText(now, at)}."
}

@Composable
private fun StepIndicator(step: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        STEPS.forEachIndexed { i, label ->
            val color = if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(Modifier.fillMaxWidth().height(4.dp), color = color, shape = MaterialTheme.shapes.small) {}
                Text(
                    "${i + 1} $label" + if (i < step) " ✓" else "",
                    fontSize = 12.sp,
                    fontWeight = if (i == step) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Botón de opción: relleno si está elegido, con borde si no. */
@Composable
internal fun Choice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier.height(48.dp)) { Text(label, textAlign = TextAlign.Center) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.height(48.dp)) { Text(label, textAlign = TextAlign.Center) }
    }
}

private fun pickTime(context: Context, start: LocalTime, onPicked: (String) -> Unit) {
    TimePickerDialog(context, { _, hour, minute ->
        onPicked("%02d:%02d".format(hour, minute))
    }, start.hour, start.minute, DateFormat.is24HourFormat(context)).show()
}

private fun parse(text: String) = TimeText.parseOrNull(text) ?: LocalTime.of(8, 0)

// ---------- Paso 1: ¿CUÁNDO? ----------

@Composable
private fun WhenStep(context: Context, container: AppContainer, draft: AutomationDraft, onChange: (AutomationDraft) -> Unit) {
    val trigger = draft.trigger
    val access = container.locationAccess
    var showMap by remember { mutableStateOf(false) }
    // Sin la ubicación precisa el mapa igual abre, pero no puede centrarse donde estás.
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        container.logger.info("Permisos", "Permiso de ubicación: ${if (result.values.any { it }) "concedido" else "negado"}")
        showMap = true
    }
    var btGranted by remember { mutableStateOf(BluetoothDevices.hasPermission(context)) }
    val bluetoothPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        container.logger.info("Permisos", "Permiso de dispositivos cercanos (Bluetooth): ${if (granted) "concedido" else "negado"}")
        btGranted = granted
    }
    var wifiNote by remember { mutableStateOf<String?>(null) }
    val wifiLocationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        container.logger.info("Permisos", "Permiso de ubicación (para el nombre del Wi-Fi): ${if (result.values.any { it }) "concedido" else "negado"}")
        wifiNote = if (result.values.any { it }) "Listo. Tocá de nuevo \"Usar la red actual\"." else "Sin el permiso de ubicación, Android no dice el nombre de la red."
    }
    Text("¿Cuándo se dispara?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Choice("A una hora", trigger is Trigger.Time, Modifier.weight(1f)) {
            if (trigger !is Trigger.Time) onChange(draft.copy(trigger = Trigger.Time(nextMinutes(container))))
        }
        Choice("Al tocarla", trigger == Trigger.Manual, Modifier.weight(1f)) { onChange(draft.copy(trigger = Trigger.Manual)) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Choice("Batería", trigger is Trigger.Battery, Modifier.weight(1f)) {
            if (trigger !is Trigger.Battery) onChange(draft.copy(trigger = Trigger.Battery(20, BatteryDirection.BELOW)))
        }
        Choice("Cargador", trigger is Trigger.Power, Modifier.weight(1f)) {
            if (trigger !is Trigger.Power) onChange(draft.copy(trigger = Trigger.Power(PowerEvent.CONNECTED)))
        }
        Choice("Lugar", trigger is Trigger.Location, Modifier.weight(1f)) {
            if (access.hasPrecise()) showMap = true else locationPermission.launch(LocationAccess.PRECISE)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Choice("Bluetooth", trigger is Trigger.Bluetooth, Modifier.weight(1f)) {
            if (trigger !is Trigger.Bluetooth) onChange(draft.copy(trigger = Trigger.Bluetooth()))
            if (!btGranted) BluetoothDevices.PERMISSION?.let { bluetoothPermission.launch(it) }
        }
        Choice("Wi-Fi", trigger is Trigger.Wifi, Modifier.weight(1f)) {
            if (trigger !is Trigger.Wifi) onChange(draft.copy(trigger = Trigger.Wifi()))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Choice("Notificación", trigger is Trigger.Notification, Modifier.weight(1f)) {
            if (trigger !is Trigger.Notification) onChange(draft.copy(trigger = Trigger.Notification()))
        }
        Choice("Etiqueta NFC", trigger is Trigger.Nfc, Modifier.weight(1f)) {
            if (trigger !is Trigger.Nfc) onChange(draft.copy(trigger = Trigger.Nfc()))
        }
    }

    when (trigger) {
        is Trigger.Time -> Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Hora", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = {
                    pickTime(context, parse(trigger.time)) { onChange(draft.copy(trigger = trigger.copy(time = it))) }
                }) { Text(timeText(context, trigger.time), fontSize = 34.sp, fontWeight = FontWeight.Light) }
                Text("Días (ninguno marcado = todos los días)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Weekday.entries.forEach { day ->
                        val on = day in trigger.days
                        val label = "LMXJVSD"[day.isoNumber - 1].toString()
                        val toggle = {
                            val days = if (on) trigger.days - day else (trigger.days + day).sortedBy { it.isoNumber }
                            onChange(draft.copy(trigger = trigger.copy(days = days)))
                        }
                        val mod = Modifier.weight(1f).height(44.dp)
                        if (on) Button(onClick = toggle, modifier = mod, shape = CircleShape, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text(label) }
                        else OutlinedButton(onClick = toggle, modifier = mod, shape = CircleShape, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text(label) }
                    }
                }
            }
        }
        Trigger.Manual -> Text(
            "No se dispara sola: corre cuando tocás \"Probar ahora\" en Inicio o cuando otra automatización la ejecuta.",
            style = MaterialTheme.typography.bodyMedium,
        )
        is Trigger.Battery -> Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("Baja a", trigger.direction == BatteryDirection.BELOW, Modifier.weight(1f)) {
                        onChange(draft.copy(trigger = trigger.copy(direction = BatteryDirection.BELOW)))
                    }
                    Choice("Sube a", trigger.direction == BatteryDirection.ABOVE, Modifier.weight(1f)) {
                        onChange(draft.copy(trigger = trigger.copy(direction = BatteryDirection.ABOVE)))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = trigger.threshold.toFloat(),
                        onValueChange = { onChange(draft.copy(trigger = trigger.copy(threshold = (it / 5).toInt() * 5))) },
                        valueRange = 0f..100f,
                        steps = 19,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${trigger.threshold} %", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
                }
            }
        }
        is Trigger.Location -> Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = trigger.placeName,
                    onValueChange = { onChange(draft.copy(trigger = trigger.copy(placeName = it))) },
                    label = { Text("Nombre del lugar (ej.: Casa)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("Al llegar", trigger.transition == LocationTransition.ENTER, Modifier.weight(1f)) {
                        onChange(draft.copy(trigger = trigger.copy(transition = LocationTransition.ENTER)))
                    }
                    Choice("Al irme", trigger.transition == LocationTransition.EXIT, Modifier.weight(1f)) {
                        onChange(draft.copy(trigger = trigger.copy(transition = LocationTransition.EXIT)))
                    }
                }
                Text(
                    "Zona: círculo de ${trigger.radiusMeters} m alrededor de %.5f, %.5f.".format(trigger.latitude, trigger.longitude),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { showMap = true }, modifier = Modifier.fillMaxWidth()) { Text("Cambiar la zona en el mapa") }
            }
        }
        is Trigger.Bluetooth -> Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("Al conectar", trigger.event == ConnectionEvent.CONNECTED, Modifier.weight(1f)) {
                        onChange(draft.copy(trigger = trigger.copy(event = ConnectionEvent.CONNECTED)))
                    }
                    Choice("Al desconectar", trigger.event == ConnectionEvent.DISCONNECTED, Modifier.weight(1f)) {
                        onChange(draft.copy(trigger = trigger.copy(event = ConnectionEvent.DISCONNECTED)))
                    }
                }
                if (!btGranted) {
                    Text(
                        "Falta el permiso \"Dispositivos cercanos\": sin él, Android no le avisa a La Vara qué aparato se conectó.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = { BluetoothDevices.PERMISSION?.let { bluetoothPermission.launch(it) } }) { Text("Permitir") }
                    OutlinedButton(onClick = { access.openAppSettings() }) { Text("Abrir Ajustes de La Vara") }
                } else {
                    val paired = remember(btGranted) { BluetoothDevices.paired(context) }
                    var open by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Aparato: " + trigger.deviceName.ifBlank { trigger.deviceAddress }.ifBlank { "cualquiera" })
                        }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            DropdownMenuItem(text = { Text("Cualquier aparato") }, onClick = {
                                open = false
                                onChange(draft.copy(trigger = trigger.copy(deviceAddress = "", deviceName = "")))
                            })
                            paired.forEach { device ->
                                DropdownMenuItem(text = { Text(device.name) }, onClick = {
                                    open = false
                                    onChange(draft.copy(trigger = trigger.copy(deviceAddress = device.address, deviceName = device.name)))
                                })
                            }
                        }
                    }
                    if (paired.isEmpty()) {
                        Text(
                            "No veo aparatos vinculados. Encendé el Bluetooth y vinculá el aparato en los ajustes del teléfono.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        is Trigger.Wifi -> Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("Al conectarme", trigger.event == ConnectionEvent.CONNECTED, Modifier.weight(1f)) {
                        onChange(draft.copy(trigger = trigger.copy(event = ConnectionEvent.CONNECTED)))
                    }
                    Choice("Al desconectarme", trigger.event == ConnectionEvent.DISCONNECTED, Modifier.weight(1f)) {
                        onChange(draft.copy(trigger = trigger.copy(event = ConnectionEvent.DISCONNECTED)))
                    }
                }
                OutlinedTextField(
                    value = trigger.ssid,
                    onValueChange = { onChange(draft.copy(trigger = trigger.copy(ssid = it))) },
                    label = { Text("Nombre de la red (vacío = cualquiera)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = {
                    if (!access.hasPrecise()) {
                        wifiLocationPermission.launch(LocationAccess.PRECISE)
                    } else {
                        val current = WifiWatcher.currentSsid(context)
                        if (current != null) {
                            wifiNote = null
                            onChange(draft.copy(trigger = trigger.copy(ssid = current)))
                        } else {
                            wifiNote = "No pude leer la red actual. ¿Estás conectado a un Wi-Fi y con la ubicación del teléfono encendida?"
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Usar la red actual") }
                wifiNote?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (trigger.ssid.isNotBlank() && !access.hasBackground()) {
                    Text(
                        "Para reconocer la red por nombre con La Vara cerrada, Android pide la ubicación \"Permitir todo el tiempo\" " +
                            "(Ajustes → Permisos → Ubicación).",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = { access.openAppSettings() }) { Text("Abrir Ajustes de La Vara") }
                }
            }
        }
        is Trigger.Notification -> Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NotificationTriggerFields(trigger) { onChange(draft.copy(trigger = it)) }
            }
        }
        is Trigger.Nfc -> Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NfcTriggerFields(trigger) { onChange(draft.copy(trigger = it)) }
            }
        }
        is Trigger.Power -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Al conectar", trigger.event == PowerEvent.CONNECTED, Modifier.weight(1f)) {
                onChange(draft.copy(trigger = trigger.copy(event = PowerEvent.CONNECTED)))
            }
            Choice("Al desconectar", trigger.event == PowerEvent.DISCONNECTED, Modifier.weight(1f)) {
                onChange(draft.copy(trigger = trigger.copy(event = PowerEvent.DISCONNECTED)))
            }
        }
    }

    if (trigger is Trigger.Battery || trigger is Trigger.Power || trigger is Trigger.Wifi) {
        val detail = when (trigger) {
            is Trigger.Battery -> "${describe(context, trigger)}: se dispara una vez al cruzar ese número, no en cada cambio."
            is Trigger.Power -> "${describe(context, trigger)}, con cable o base inalámbrica."
            else -> "${describe(context, trigger)}."
        }
        Text(
            "$detail\nMientras esté activa vas a ver la notificación fija \"La Vara está activa\": Android exige ese aviso " +
                "para que una app cerrada pueda escuchar la batería, el cargador y el Wi-Fi.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    if (trigger is Trigger.Location) {
        Text(
            "Android avisa al entrar o salir de la zona; puede tardar unos minutos en notarlo. Si ya estás adentro al guardar, " +
                "no se dispara hasta que salgas y vuelvas.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!access.hasBackground()) {
            Text(
                "Falta un permiso: para que funcione con La Vara cerrada, en Ajustes → Permisos → Ubicación elegí \"Permitir todo el tiempo\".",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = { access.openAppSettings() }) { Text("Abrir Ajustes de La Vara") }
        }
    }

    if (showMap) {
        MapPicker(
            initial = trigger as? Trigger.Location,
            access = access,
            onDismiss = { showMap = false },
            onPick = { lat, lon, radius ->
                showMap = false
                val zone = (trigger as? Trigger.Location)?.copy(latitude = lat, longitude = lon, radiusMeters = radius)
                    ?: Trigger.Location(lat, lon, radius)
                onChange(draft.copy(trigger = zone))
            },
        )
    }

    if (trigger is Trigger.Time) {
        // Lo más importante: cuándo va a sonar de verdad, para que no quede a la madrugada sin querer.
        val now = container.clock.now()
        val at = NextAlarm.after(trigger, now)
        Text(
            "Se dispara ${daysText(trigger.days)} a las ${timeText(context, trigger.time)}, aunque el teléfono esté bloqueado.\n" +
                "Próxima vez: ${whenText(context, at, now)} (dentro de ${untilText(now, at)}).",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Hora actual + 2 minutos, como texto "HH:mm": sirve para probar sin pensar en a. m./p. m. */
private fun nextMinutes(container: AppContainer): String {
    val t = container.clock.now().plusMinutes(2)
    return "%02d:%02d".format(t.hour, t.minute)
}

// ---------- Paso 2: ¿SI? ----------

@Composable
private fun IfStep(context: Context, draft: AutomationDraft, onChange: (AutomationDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("¿Qué tiene que cumplirse?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        Text("Opcional. Sin condiciones, se ejecuta siempre.", style = MaterialTheme.typography.bodyMedium)
    }
    if (draft.conditions.size > 1) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Se ejecuta si se cumplen", style = MaterialTheme.typography.bodyMedium)
            Choice("todas", draft.matchAll) { onChange(draft.copy(matchAll = true)) }
            Choice("alguna", !draft.matchAll) { onChange(draft.copy(matchAll = false)) }
        }
    }

    draft.conditions.forEachIndexed { index, condition ->
        fun replace(new: Condition) = onChange(draft.copy(conditions = draft.conditions.toMutableList().also { it[index] = new }))
        fun remove() = onChange(draft.copy(conditions = draft.conditions.filterIndexed { i, _ -> i != index }))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (condition) {
                            is Condition.BatteryLevel -> "Nivel de batería"
                            is Condition.TimeBetween -> "Entre dos horas"
                            is Condition.WifiConnected -> "Conectado a un Wi-Fi"
                            is Condition.Charging -> "Cargador"
                            is Condition.DaysOfWeek -> "Solo ciertos días"
                            else -> "Condición avanzada"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { remove() }) { Text("Quitar") }
                }
                when (condition) {
                    is Condition.BatteryLevel -> {
                        val above = condition.comparison == Comparison.GREATER_THAN || condition.comparison == Comparison.GREATER_OR_EQUAL
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Choice("más de", above, Modifier.weight(1f)) { replace(condition.copy(comparison = Comparison.GREATER_THAN)) }
                            Choice("menos de", !above && condition.comparison != Comparison.EQUAL, Modifier.weight(1f)) {
                                replace(condition.copy(comparison = Comparison.LESS_THAN))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = condition.value.toFloat(),
                                onValueChange = { replace(condition.copy(value = (it / 5).toInt() * 5)) },
                                valueRange = 0f..100f,
                                steps = 19,
                                modifier = Modifier.weight(1f),
                            )
                            Text("${condition.value} %", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
                        }
                    }
                    is Condition.TimeBetween -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("de")
                        OutlinedButton(onClick = { pickTime(context, parse(condition.start)) { replace(condition.copy(start = it)) } }) {
                            Text(timeText(context, condition.start))
                        }
                        Text("a")
                        OutlinedButton(onClick = { pickTime(context, parse(condition.end)) { replace(condition.copy(end = it)) } }) {
                            Text(timeText(context, condition.end))
                        }
                    }
                    is Condition.WifiConnected -> WifiConditionFields(condition) { replace(it) }
                    is Condition.Charging -> ChargingConditionFields(condition) { replace(it) }
                    is Condition.DaysOfWeek -> DaysConditionFields(condition) { replace(it) }
                    else -> Text("Si ${describe(context, condition)}. Se conserva tal cual; el editor todavía no la cambia.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    AddMenu(
        label = "+ Agregar condición",
        options = listOf(
            "Nivel de batería" to { onChange(draft.copy(conditions = draft.conditions + Condition.BatteryLevel(Comparison.GREATER_THAN, 20))) },
            "Entre dos horas" to { onChange(draft.copy(conditions = draft.conditions + Condition.TimeBetween("07:00", "22:00"))) },
            "Conectado a un Wi-Fi" to { onChange(draft.copy(conditions = draft.conditions + Condition.WifiConnected())) },
            "Cargando o sin cargador" to { onChange(draft.copy(conditions = draft.conditions + Condition.Charging())) },
            "Solo ciertos días" to {
                onChange(draft.copy(conditions = draft.conditions + Condition.DaysOfWeek(Weekday.entries.take(5))))
            },
        ),
    )
}

@Composable
private fun AddMenu(label: String, options: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(label) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, action) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; action() })
            }
        }
    }
}

// ---------- Paso 3: ¿HACER QUÉ? ----------

@Composable
private fun DoStep(draft: AutomationDraft, others: List<Automation>, onChange: (AutomationDraft) -> Unit) {
    // Para qué acción se está eligiendo app: su posición, o NEW_ACTION para agregar una nueva.
    var pickFor by remember { mutableStateOf<Int?>(null) }
    // Para qué acción "Tocar un botón" se está eligiendo app, y la app que falta confirmar para la lista.
    var tapPickFor by remember { mutableStateOf<Int?>(null) }
    var confirmAllow by remember { mutableStateOf<Pair<Int, InstalledApp>?>(null) }
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("¿Qué hace?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        Text("Las acciones van en orden, de arriba hacia abajo.", style = MaterialTheme.typography.bodyMedium)
    }

    draft.actions.forEachIndexed { index, action ->
        fun replace(new: Action) = onChange(draft.copy(actions = draft.actions.toMutableList().also { it[index] = new }))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(28.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${index + 1}", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                    }
                    Text(actionTitle(action), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 10.dp))
                    if (index > 0) TextButton(onClick = { onChange(draft.moveAction(index, index - 1)) }) { Text("↑", fontSize = 18.sp) }
                    if (index < draft.actions.lastIndex) TextButton(onClick = { onChange(draft.moveAction(index, index + 1)) }) { Text("↓", fontSize = 18.sp) }
                    TextButton(onClick = { onChange(draft.copy(actions = draft.actions.filterIndexed { i, _ -> i != index })) }) { Text("Quitar") }
                }
                Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (action) {
                        is Action.ShowNotification -> {
                            OutlinedTextField(action.title, { replace(action.copy(title = it)) }, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(action.text, { replace(action.copy(text = it)) }, label = { Text("Texto") }, modifier = Modifier.fillMaxWidth())
                            Text("Podés usar %battery (batería), %time (hora) y %date (fecha).", style = MaterialTheme.typography.bodySmall)
                        }
                        is Action.Delay -> WaitFields(action) { replace(it) }
                        is Action.OpenApp -> {
                            val label = remember(action.packageName) { InstalledApps(context).label(action.packageName) }
                            OutlinedButton(onClick = { pickFor = index }, modifier = Modifier.fillMaxWidth()) {
                                AppIcon(action.packageName)
                                Text(label ?: "No está instalada (${action.packageName}). Tocá para elegir otra.", modifier = Modifier.padding(start = 10.dp).weight(1f))
                            }
                        }
                        is Action.OpenUrl -> {
                            // El campo arranca vacío; lo que se escriba o pegue se limpia antes de guardarlo.
                            val saved = if (action.url == "https://") "" else action.url
                            var typed by remember(index) { mutableStateOf(saved) }
                            // Si la acción cambió por otro lado (por ejemplo, al moverla), se muestra la guardada.
                            val text = if ((Action.OpenUrl.normalize(typed) ?: "https://") == action.url) typed else saved
                            val normalized = Action.OpenUrl.normalize(text)
                            OutlinedTextField(
                                value = text,
                                onValueChange = { new ->
                                    typed = new
                                    replace(Action.OpenUrl(Action.OpenUrl.normalize(new) ?: "https://"))
                                },
                                label = { Text("Enlace") },
                                placeholder = { Text("Pegá el enlace (ej.: waze.com/ul?q=casa)") },
                                isError = text.isNotBlank() && normalized == null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                supportingText = {
                                    Text(
                                        when {
                                            normalized != null -> "Se va a abrir: ${Action.OpenUrl(normalized).host}"
                                            text.isBlank() -> "Se abre en el navegador o en la app que corresponda."
                                            else -> "No encuentro un sitio en ese texto. Pegá el enlace tal como lo copiaste."
                                        },
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        is Action.Flashlight -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Choice("Encender", action.on, Modifier.weight(1f)) { replace(Action.Flashlight(true)) }
                            Choice("Apagar", !action.on, Modifier.weight(1f)) { replace(Action.Flashlight(false)) }
                        }
                        is Action.SetVolume -> {
                            OptionPicker(VolumeStream.entries, action.stream, { "Volumen de " + it.label }) { replace(action.copy(stream = it)) }
                            PercentSlider(action.percent, 0) { replace(action.copy(percent = it)) }
                        }
                        is Action.SetRingerMode -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                RingerMode.entries.forEach { mode ->
                                    Choice(mode.label.replaceFirstChar { it.uppercase() }, action.mode == mode, Modifier.weight(1f)) {
                                        replace(Action.SetRingerMode(mode))
                                    }
                                }
                            }
                            PermissionHint(action)
                        }
                        is Action.DoNotDisturb -> {
                            OptionPicker(DndMode.entries, action.mode, { if (it == DndMode.OFF) "Apagar No molestar" else "Encender: " + it.label }) {
                                replace(Action.DoNotDisturb(it))
                            }
                            PermissionHint(action)
                        }
                        is Action.SetBrightness -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Choice("Fijo", !action.auto, Modifier.weight(1f)) { replace(action.copy(auto = false)) }
                                Choice("Automático", action.auto, Modifier.weight(1f)) { replace(action.copy(auto = true)) }
                            }
                            if (!action.auto) PercentSlider(action.percent, 5) { replace(action.copy(percent = it)) }
                            PermissionHint(action)
                        }
                        is Action.OpenSystemPanel -> {
                            OptionPicker(SystemPanel.entries, action.panel, { it.label.replaceFirstChar { c -> c.uppercase() } }) {
                                replace(Action.OpenSystemPanel(it))
                            }
                            Text(
                                "Android no deja a ninguna app encender ni apagar ${action.panel.label} sola. La Vara abre el interruptor y vos lo tocás.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        is Action.WhatsAppMessage -> {
                            PhoneField(action.phone, action.contactName, international = true) { phone, name ->
                                replace(action.copy(phone = phone, contactName = name))
                            }
                            MessageField(action.text) { replace(action.copy(text = it)) }
                            Text(
                                "WhatsApp se abre en ese chat con el mensaje escrito y vos tocás Enviar. Podés usar %battery, %time y %date.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        is Action.SendSms -> {
                            PhoneField(action.phone, action.contactName, international = false) { phone, name ->
                                replace(action.copy(phone = phone, contactName = name))
                            }
                            MessageField(action.text) { replace(action.copy(text = it)) }
                            Text(
                                "El SMS sale solo, sin tocar nada, y cuesta como un SMS normal de tu plan. Podés usar %battery, %time y %date.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            SmsPermissionHint()
                        }
                        is Action.DialNumber -> {
                            PhoneField(action.phone, action.contactName, international = false) { phone, name ->
                                replace(action.copy(phone = phone, contactName = name))
                            }
                            Text("Se abre el teléfono con el número marcado y vos tocás Llamar.", style = MaterialTheme.typography.bodySmall)
                        }
                        is Action.Navigate -> {
                            OptionPicker(NavigationApp.entries, action.app, { "Con " + it.label }) { replace(action.copy(app = it)) }
                            OutlinedTextField(
                                value = action.destination,
                                onValueChange = { replace(action.copy(destination = it)) },
                                label = { Text("Destino") },
                                placeholder = { Text("Dirección, lugar o coordenadas") },
                                supportingText = { Text("Ej.: Mall San Pedro, o 9.9325,-84.0796 copiado del mapa.") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        is Action.TapInApp -> TapFields(action, onPickApp = { tapPickFor = index }) { replace(it) }
                        is Action.RunAutomation -> {
                            var open by remember { mutableStateOf(false) }
                            val target = others.firstOrNull { it.id == action.automationId }
                            Box {
                                OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text(target?.name ?: if (action.automationId.isBlank()) "Elegir cuál…" else "No existe: ${action.automationId}")
                                }
                                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                                    if (others.isEmpty()) DropdownMenuItem(text = { Text("No hay otras automatizaciones") }, onClick = { open = false })
                                    others.forEach { other ->
                                        DropdownMenuItem(text = { Text(other.name) }, onClick = { open = false; replace(Action.RunAutomation(other.id)) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    AddMenu(
        label = "+ Agregar acción",
        options = listOf(
            "Mostrar notificación" to { onChange(draft.copy(actions = draft.actions + Action.ShowNotification("La Vara", ""))) },
            "Esperar (segundos, minutos u horas)" to { onChange(draft.copy(actions = draft.actions + Action.Delay(5))) },
            "Abrir app" to { pickFor = NEW_ACTION },
            "Abrir enlace" to { onChange(draft.copy(actions = draft.actions + Action.OpenUrl("https://"))) },
            "WhatsApp a un contacto" to { onChange(draft.copy(actions = draft.actions + Action.WhatsAppMessage())) },
            "Enviar SMS (sale solo)" to { onChange(draft.copy(actions = draft.actions + Action.SendSms())) },
            "Llamar a un número" to { onChange(draft.copy(actions = draft.actions + Action.DialNumber())) },
            "Navegar a un lugar (Waze, Maps)" to { onChange(draft.copy(actions = draft.actions + Action.Navigate())) },
            "Tocar un botón en otra app" to {
                onChange(draft.copy(actions = draft.actions + Action.TapInApp()))
                tapPickFor = draft.actions.size
            },
            "Ejecutar otra automatización" to { onChange(draft.copy(actions = draft.actions + Action.RunAutomation(""))) },
            "Linterna" to { onChange(draft.copy(actions = draft.actions + Action.Flashlight())) },
            "Volumen" to { onChange(draft.copy(actions = draft.actions + Action.SetVolume())) },
            "Modo de sonido (vibrar, silencio)" to { onChange(draft.copy(actions = draft.actions + Action.SetRingerMode())) },
            "No molestar" to { onChange(draft.copy(actions = draft.actions + Action.DoNotDisturb())) },
            "Brillo de pantalla" to { onChange(draft.copy(actions = draft.actions + Action.SetBrightness())) },
            "Wi-Fi, datos, Bluetooth, ubicación…" to { onChange(draft.copy(actions = draft.actions + Action.OpenSystemPanel())) },
        ),
    )

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Si una acción falla", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("Seguir", draft.onError == OnError.CONTINUE, Modifier.weight(1f)) { onChange(draft.copy(onError = OnError.CONTINUE)) }
                Choice("Detener todo", draft.onError == OnError.STOP, Modifier.weight(1f)) { onChange(draft.copy(onError = OnError.STOP)) }
            }
        }
    }

    CooldownRow(draft.cooldownSeconds) { onChange(draft.copy(cooldownSeconds = it)) }

    fun setTapApp(target: Int, app: InstalledApp) {
        val current = draft.actions.getOrNull(target) as? Action.TapInApp ?: return
        onChange(draft.copy(actions = draft.actions.toMutableList().also { it[target] = current.copy(packageName = app.packageName) }))
    }

    tapPickFor?.let { target ->
        AppPickerDialog(
            title = "¿En qué app tocar?",
            onDismiss = { tapPickFor = null },
            onPick = { app ->
                tapPickFor = null
                if (app.packageName in AllowedApps(context).get()) setTapApp(target, app) else confirmAllow = target to app
            },
        )
    }

    confirmAllow?.let { (target, app) ->
        AllowAppDialog(
            appLabel = app.label,
            onAllow = {
                confirmAllow = null
                AllowedApps(context).add(app.packageName)
                (context.applicationContext as LaVaraApp).container.logger
                    .info("Accesibilidad", "${app.label} agregada a las apps donde La Vara puede tocar botones.")
                setTapApp(target, app)
            },
            onDismiss = { confirmAllow = null },
        )
    }

    pickFor?.let { target ->
        AppPickerDialog(
            onDismiss = { pickFor = null },
            onPick = { app ->
                pickFor = null
                val action = Action.OpenApp(app.packageName)
                onChange(
                    if (target == NEW_ACTION) draft.copy(actions = draft.actions + action)
                    else draft.copy(actions = draft.actions.toMutableList().also { it[target] = action }),
                )
            },
        )
    }
}

private const val NEW_ACTION = -1

/** Botón que muestra la opción elegida y despliega las demás. */
@Composable
private fun <T> OptionPicker(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(label(selected) + "  ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(label(option)) }, onClick = { open = false; onPick(option) }) }
        }
    }
}

/** Porcentaje de [min] a 100, de 5 en 5. */
@Composable
private fun PercentSlider(percent: Int, min: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = percent.toFloat(),
            onValueChange = { onChange(((it / 5).toInt() * 5).coerceIn(min, 100)) },
            valueRange = min.toFloat()..100f,
            steps = (100 - min) / 5 - 1,
            modifier = Modifier.weight(1f),
        )
        Text("$percent %", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
    }
}

/** Si a la acción le falta un permiso especial, lo dice y lleva al ajuste de Android donde se da. */
@Composable
private fun PermissionHint(action: Action) {
    val context = LocalContext.current
    val controls = remember { SystemControls(context) }
    val dnd = action.needsDndAccess() && !controls.hasDndAccess()
    val brightness = action is Action.SetBrightness && !controls.canWriteSettings()
    if (!dnd && !brightness) return
    Text(
        if (dnd) "Falta el permiso \"Acceso a No molestar\". En la lista, buscá La Vara y activalo."
        else "Falta el permiso \"Modificar ajustes del sistema\". Activalo en la pantalla que se abre.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
    OutlinedButton(onClick = { if (dnd) controls.openDndAccessSettings() else controls.openWriteSettings() }) {
        Text("Dar el permiso")
    }
}

@Composable
internal fun AppIcon(packageName: String, size: Int = 32) {
    val context = LocalContext.current
    val icon = remember(packageName) {
        InstalledApps(context).icon(packageName)?.toBitmap(size * 3, size * 3)?.asImageBitmap()
    }
    if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(size.dp))
    else Box(Modifier.size(size.dp))
}

/** Lista de apps instaladas con buscador: el usuario elige por nombre, sin saber el nombre de paquete. */
@Composable
internal fun AppPickerDialog(title: String = "¿Qué app abrir?", onDismiss: () -> Unit, onPick: (InstalledApp) -> Unit) {
    val context = LocalContext.current
    val apps by produceState<List<InstalledApp>?>(null) {
        value = withContext(Dispatchers.IO) { InstalledApps(context).launchable() }
    }
    var search by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(search, { search = it }, singleLine = true, label = { Text("Buscar") }, modifier = Modifier.fillMaxWidth())
                val list = apps
                if (list == null) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    val shown = list.filter { it.label.contains(search.trim(), ignoreCase = true) }
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                        items(shown, key = { it.packageName }) { app ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onPick(app) }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(app.packageName, size = 36)
                                Text(app.label, modifier = Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                    if (shown.isEmpty()) Text("No hay apps con ese nombre.")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private val COOLDOWNS = listOf(0L to "sin espera", 60L to "1 minuto", 300L to "5 minutos", 900L to "15 minutos", 3_600L to "1 hora", 86_400L to "1 día")

@Composable
private fun CooldownRow(seconds: Long, onChange: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = COOLDOWNS.firstOrNull { it.first == seconds }?.second ?: "$seconds segundos"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("No repetir antes de", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { open = true }, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) { Text(label) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                COOLDOWNS.forEach { (value, text) ->
                    DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onChange(value) })
                }
            }
        }
    }
}
