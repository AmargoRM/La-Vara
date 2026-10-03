package com.lavara.system.device

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import com.lavara.system.connectivity.WifiWatcher
import android.os.PowerManager
import android.os.BatteryManager
import com.lavara.core.DeviceState

/** Estado real del teléfono para el motor. */
class AndroidDeviceState(private val context: Context) : DeviceState {
    private val battery = context.getSystemService(BatteryManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)

    override fun isLocked(): Boolean = keyguard.isKeyguardLocked || !power.isInteractive

    // El último aviso de batería queda guardado en Android: se lee sin registrar nada.
    override fun isCharging(): Boolean? = runCatching {
        ContextCompat.registerReceiver(context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            ?.takeIf { it >= 0 }
            ?.let { it != 0 }
    }.getOrNull()

    @Suppress("DEPRECATION") // allNetworks: la alternativa exige registrar un callback.
    override fun isWifiConnected(): Boolean? = runCatching {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        connectivity.allNetworks.any { connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
    }.getOrNull()

    // Android solo dice el nombre con el permiso de ubicación; sin él, null.
    override fun wifiSsid(): String? = if (isWifiConnected() == true) WifiWatcher.currentSsid(context) else null

    override fun batteryLevel(): Int? =
        battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
}
