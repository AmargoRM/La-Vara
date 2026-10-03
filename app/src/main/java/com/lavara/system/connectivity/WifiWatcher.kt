package com.lavara.system.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock

/**
 * Escucha las conexiones y desconexiones de Wi-Fi mientras corre el servicio "La Vara está activa".
 * Android no avisa del Wi-Fi a apps cerradas desde Android 7; por eso va dentro del servicio.
 * El nombre de la red (SSID) solo llega con permiso de ubicación "todo el tiempo"; sin él llega null.
 */
class WifiWatcher(
    private val context: Context,
    private val onChange: (ssid: String?, connected: Boolean) -> Unit,
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val networks = mutableMapOf<Network, String?>()
    private var registeredAt = 0L

    private val callback: ConnectivityManager.NetworkCallback =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = changed(network, caps)
                override fun onLost(network: Network) = lost(network)
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = changed(network, caps)
                override fun onLost(network: Network) = lost(network)
            }
        }

    fun start() {
        registeredAt = SystemClock.elapsedRealtime()
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        connectivity.registerNetworkCallback(request, callback)
    }

    fun stop() {
        runCatching { connectivity.unregisterNetworkCallback(callback) }
        synchronized(networks) { networks.clear() }
    }

    private fun changed(network: Network, caps: NetworkCapabilities) {
        val ssid = cleanSsid((caps.transportInfo as? WifiInfo)?.ssid) ?: currentSsid(context)
        val isNew = synchronized(networks) {
            val known = networks.containsKey(network)
            if (!known || networks[network] == null) networks[network] = ssid
            !known
        }
        // Al encender el servicio, Android informa la red a la que ya se estaba conectado: eso no es
        // una conexión nueva y no debe disparar nada.
        if (isNew && SystemClock.elapsedRealtime() - registeredAt > SETTLE_MILLIS) onChange(ssid, true)
    }

    private fun lost(network: Network) {
        val (known, ssid) = synchronized(networks) { networks.containsKey(network) to networks.remove(network) }
        if (known) onChange(ssid, false)
    }

    companion object {
        private const val SETTLE_MILLIS = 3_000L

        /** "\"Casa\"" → "Casa"; null si Android no da el nombre. */
        fun cleanSsid(raw: String?): String? {
            val text = raw?.trim()?.removeSurrounding("\"") ?: return null
            return if (text.isBlank() || text == WifiManager.UNKNOWN_SSID) null else text
        }

        /** Red Wi-Fi actual, para el botón "Usar la red actual". Necesita ubicación precisa y la ubicación encendida. */
        @Suppress("DEPRECATION")
        fun currentSsid(context: Context): String? =
            runCatching { cleanSsid(context.applicationContext.getSystemService(WifiManager::class.java).connectionInfo?.ssid) }.getOrNull()
    }
}
