package com.lavara.system.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Permisos de ubicación y la ubicación actual (para centrar el mapa). */
class LocationAccess(private val context: Context) {

    fun hasPrecise(): Boolean = granted(Manifest.permission.ACCESS_FINE_LOCATION)

    /** "Permitir todo el tiempo": sin esto, Android no avisa de las zonas con La Vara cerrada. */
    fun hasBackground(): Boolean = hasPrecise() && granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    fun isLocationOn(): Boolean = context.getSystemService(LocationManager::class.java)?.isLocationEnabled == true

    /** Ajustes de La Vara, donde se elige "Permitir todo el tiempo" en Ubicación. */
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openLocationSettings() {
        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Ubicación actual (latitud, longitud), o null si no hay permiso o no se pudo obtener. */
    @SuppressLint("MissingPermission") // hasPrecise() lo comprueba antes.
    suspend fun current(): Pair<Double, Double>? {
        if (!hasPrecise()) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val token = CancellationTokenSource()
        val fresh = runCatching { client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token).await() }.getOrNull()
        val location = fresh ?: runCatching { client.lastLocation.await() }.getOrNull()
        return location?.let { it.latitude to it.longitude }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        /** En Android 10 se puede pedir en el diálogo; desde 11, Android lleva a Ajustes. */
        val BACKGROUND = Manifest.permission.ACCESS_BACKGROUND_LOCATION
        val PRECISE = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val backgroundNeedsSettings = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }
}

/** Espera una tarea de Google Play services sin trabar la app. Devuelve null si falló. */
suspend fun <T> Task<T>.await(): T? = if (awaitSuccess()) result else null

/** Espera una tarea de Google Play services; true si salió bien. */
suspend fun Task<*>.awaitSuccess(): Boolean = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task -> continuation.resume(task.isSuccessful) }
}
