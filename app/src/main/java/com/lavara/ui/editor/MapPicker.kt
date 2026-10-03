package com.lavara.ui.editor

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lavara.system.location.LocationAccess
import com.lavara.triggers.Trigger
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.File

/** Centro de Costa Rica, por si todavía no se conoce la ubicación. */
private val FALLBACK = GeoPoint(9.93, -84.08)

/**
 * Mapa de pantalla completa para marcar una zona: tocar el mapa mueve el centro y la barra cambia el radio.
 * Arranca en la zona guardada o, si es nueva, en la ubicación actual.
 */
@Composable
fun MapPicker(
    initial: Trigger.Location?,
    access: LocationAccess,
    onDismiss: () -> Unit,
    onPick: (latitude: Double, longitude: Double, radiusMeters: Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var center by remember { mutableStateOf(initial?.let { GeoPoint(it.latitude, it.longitude) }) }
    var radius by remember { mutableStateOf(initial?.radiusMeters ?: 200) }
    var status by remember { mutableStateOf<String?>(null) }
    val map = remember { createMap(context) }
    val circle = remember { Polygon(map) }
    val marker = remember { Marker(map) }

    fun draw(moveCamera: Boolean) {
        val point = center ?: return
        circle.points = Polygon.pointsAsCircle(point, radius.toDouble())
        marker.position = point
        if (!map.overlays.contains(circle)) map.overlays.add(circle)
        if (!map.overlays.contains(marker)) map.overlays.add(marker)
        if (moveCamera) map.controller.animateTo(point)
        map.invalidate()
    }

    fun goToCurrent() {
        status = "Buscando tu ubicación…"
        scope.launch {
            val here = access.current()
            if (here == null) {
                status = if (!access.hasPrecise()) "Sin permiso de ubicación: tocá el mapa para marcar la zona." else "No se pudo obtener tu ubicación. ¿Está encendida la ubicación del teléfono?"
            } else {
                status = null
                center = GeoPoint(here.first, here.second)
                map.controller.setZoom(16.0)
                draw(moveCamera = true)
            }
        }
    }

    LaunchedEffect(Unit) {
        map.controller.setZoom(if (center == null) 8.0 else 16.0)
        map.controller.setCenter(center ?: FALLBACK)
        map.overlays.add(0, MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                center = p
                status = null
                draw(moveCamera = false)
                return true
            }

            override fun longPressHelper(p: GeoPoint) = false
        }))
        if (center == null) goToCurrent() else draw(moveCamera = true)
    }
    DisposableEffect(Unit) { onDispose { map.onDetach() } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Text(
                    "Tocá el mapa donde está el lugar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp),
                )
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
                }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Radio", modifier = Modifier.width(56.dp))
                        Slider(
                            value = radius.toFloat(),
                            onValueChange = { radius = ((it / 50).toInt() * 50).coerceAtLeast(Trigger.Location.MIN_RADIUS); draw(moveCamera = false) },
                            valueRange = Trigger.Location.MIN_RADIUS.toFloat()..2_000f,
                            modifier = Modifier.weight(1f),
                        )
                        Text("$radius m", fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp), textAlign = TextAlign.End)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { goToCurrent() }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Mi ubicación") }
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp)) { Text("Cancelar") }
                    }
                    Button(
                        enabled = center != null,
                        onClick = { center?.let { onPick(it.latitude, it.longitude, radius) } },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("Usar esta zona") }
                }
            }
        }
    }
}

private fun createMap(context: Context): MapView {
    // OpenStreetMap pide identificar la app; el caché de mapas va a la carpeta propia de La Vara.
    Configuration.getInstance().apply {
        userAgentValue = context.packageName
        osmdroidBasePath = File(context.cacheDir, "osmdroid")
        osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
    }
    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        minZoomLevel = 4.0
    }
}
