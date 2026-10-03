package com.lavara.system.location

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.lavara.data.AutomationRepository
import com.lavara.logging.AppLogger
import com.lavara.triggers.LocationTransition
import com.lavara.triggers.Trigger

/**
 * Registra en Android una zona (geocerca) por cada automatización activa con disparador de ubicación.
 * Android vigila las zonas por su cuenta, sin que La Vara consulte el GPS a cada rato, y avisa a
 * [GeofenceReceiver] al entrar o salir. Las zonas se borran al reiniciar: se vuelven a registrar.
 */
class GeofenceSync(
    private val context: Context,
    private val automations: AutomationRepository,
    private val access: LocationAccess,
    private val logger: AppLogger,
) {
    private val client by lazy { LocationServices.getGeofencingClient(context) }

    @SuppressLint("MissingPermission") // access.hasBackground() lo comprueba antes.
    suspend fun sync(reason: String) {
        val zones = automations.all().filter { it.enabled && it.trigger is Trigger.Location }
        // Siempre se borran las anteriores: así una zona cambiada o desactivada no queda vigilada.
        client.removeGeofences(pendingIntent()).awaitSuccess()
        if (zones.isEmpty()) return
        if (!access.hasBackground()) {
            logger.warn(SOURCE, "${zones.size} zona(s) sin vigilar: falta el permiso de ubicación \"Permitir todo el tiempo\" ($reason)")
            return
        }
        if (zones.size > MAX_ZONES) logger.warn(SOURCE, "Android vigila hasta $MAX_ZONES zonas; se usan las primeras $MAX_ZONES")
        val geofences = zones.take(MAX_ZONES).map { automation ->
            val trigger = automation.trigger as Trigger.Location
            val dwell = trigger.transition == LocationTransition.ENTER && trigger.dwellMinutes > 0
            Geofence.Builder()
                .setRequestId(automation.id)
                .setCircularRegion(trigger.latitude, trigger.longitude, trigger.radiusMeters.toFloat())
                .setTransitionTypes(
                    when {
                        // "Quedarse X minutos": Android avisa recién cuando pasó ese tiempo adentro.
                        dwell -> Geofence.GEOFENCE_TRANSITION_DWELL
                        trigger.transition == LocationTransition.ENTER -> Geofence.GEOFENCE_TRANSITION_ENTER
                        else -> Geofence.GEOFENCE_TRANSITION_EXIT
                    },
                )
                .apply { if (dwell) setLoiteringDelay(trigger.dwellMinutes * 60_000) }
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .build()
        }
        // Sin aviso inicial: si ya estás adentro al guardar, no se dispara hasta que salgas y vuelvas a entrar.
        val request = GeofencingRequest.Builder().setInitialTrigger(0).addGeofences(geofences).build()
        val ok = client.addGeofences(request, pendingIntent()).awaitSuccess()
        if (ok) {
            logger.info(SOURCE, "Zonas vigiladas: ${zones.take(MAX_ZONES).joinToString { it.name }} ($reason)")
        } else {
            val hint = if (!access.isLocationOn()) "la ubicación del teléfono está apagada" else "Android no las aceptó"
            logger.error(SOURCE, "No se pudieron vigilar las zonas: $hint ($reason)")
        }
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, GeofenceReceiver::class.java).setAction(GeofenceReceiver.ACTION),
        // Google Play services completa el intent con la zona: tiene que ser modificable.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    private companion object {
        const val SOURCE = "Ubicación"
        const val MAX_ZONES = 100
    }
}
