package com.lavara.system.device

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.lavara.LaVaraApp
import com.lavara.R
import com.lavara.system.connectivity.WifiWatcher
import com.lavara.ui.MainActivity
import kotlinx.coroutines.launch

/**
 * Servicio con la notificación "La Vara está activa". Escucha los avisos de batería y de cargador,
 * que Android solo entrega a apps abiertas o con un servicio así. No consulta nada por su cuenta:
 * solo espera los avisos del sistema. Lo enciende y apaga [DeviceWatcher].
 */
class DeviceWatchService : Service() {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = handle(intent)
    }
    private var registered = false
    private var wifi: WifiWatcher? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as LaVaraApp).container
        try {
            startInForeground()
        } catch (e: RuntimeException) {
            container.logger.error("Batería", "Android no dejó mostrar \"La Vara está activa\": ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            // Al registrarse, Android entrega enseguida el último estado de la batería.
            ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
            wifi = WifiWatcher(this) { ssid, connected ->
                val at = container.clock.now().toInstant().toEpochMilli()
                container.appScope.launch { container.deviceWatcher.onWifi(ssid, connected, at) }
            }.also { it.start() }
        }
        // Si Android cierra el servicio por falta de memoria, lo vuelve a abrir cuando puede.
        return START_STICKY
    }

    override fun onDestroy() {
        if (registered) unregisterReceiver(receiver)
        wifi?.stop()
        wifi = null
        registered = false
        isRunning = false
        super.onDestroy()
    }

    private fun handle(intent: Intent) {
        val container = (application as LaVaraApp).container
        when (intent.action) {
            Intent.ACTION_BATTERY_CHANGED -> {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level < 0 || scale <= 0) return
                val percent = (level * 100 / scale).coerceIn(0, 100)
                container.appScope.launch { container.deviceWatcher.onBatteryLevel(percent) }
            }
            Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED -> {
                val connected = intent.action == Intent.ACTION_POWER_CONNECTED
                val at = container.clock.now().toInstant().toEpochMilli()
                container.appScope.launch { container.deviceWatcher.onPower(connected, at) }
            }
        }
    }

    private fun startInForeground() {
        FixedNotices.ensureChannels(this)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle("La Vara está activa")
            .setContentText("Atenta a la batería, el cargador y el Wi-Fi.")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = FixedNotices.WATCH_CHANNEL

        // Actualizaciones usan 1001 y 1002; automatizaciones, de 2000 en adelante.
        private const val NOTIFICATION_ID = 1500

        @Volatile
        var isRunning = false
            private set
    }
}
