package com.lavara.system.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.lavara.LaVaraApp
import com.lavara.triggers.TriggerEvent
import kotlinx.coroutines.launch

/** Android avisa que el teléfono entró o salió de una zona: se ejecuta la automatización de esa zona. */
class GeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val event = GeofencingEvent.fromIntent(intent) ?: return
        val container = (context.applicationContext as LaVaraApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                if (event.hasError()) {
                    val text = GeofenceStatusCodes.getStatusCodeString(event.errorCode)
                    container.logger.error("Ubicación", "Android avisó un error con las zonas: $text")
                    // Error 1000 = la ubicación se apagó: Android borró las zonas.
                    return@launch
                }
                val entered = event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER
                val now = container.clock.now().toInstant().toEpochMilli()
                for (geofence in event.triggeringGeofences.orEmpty()) {
                    container.automationRunner.handle(TriggerEvent.LocationChanged(geofence.requestId, entered, now))
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.lavara.ZONA"
    }
}
