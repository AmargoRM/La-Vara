package com.lavara.system.device

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.lavara.LaVaraApp
import com.lavara.R
import com.lavara.ui.MainActivity
import kotlinx.coroutines.launch

/**
 * Servicio corto que espera a que el usuario desbloquee el teléfono para ejecutar las automatizaciones de
 * [UnlockQueue]. Android solo avisa del desbloqueo (USER_PRESENT) a apps que están corriendo. Se apaga solo
 * apenas no queda nada esperando.
 */
class UnlockWaitService : Service() {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val container = (application as LaVaraApp).container
            container.appScope.launch {
                container.unlockQueue.runPending(if (intent.action == Intent.ACTION_USER_PRESENT) "desbloqueo" else "pantalla encendida sin bloqueo")
                if (!container.unlockQueue.hasPending()) stopSelf()
            }
        }
    }
    private var registered = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startInForeground()
        } catch (e: RuntimeException) {
            (application as LaVaraApp).container.logger.error("Desbloqueo", "Android no dejó mostrar el aviso de espera: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            // Son avisos del sistema: hay que registrarlos como exportados para recibirlos.
            ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            registered = true
        }
        val container = (application as LaVaraApp).container
        container.appScope.launch {
            // Si ya está desbloqueado (por ejemplo, el usuario desbloqueó antes de que arrancara), ejecuta ya.
            container.unlockQueue.runPending("al empezar la espera")
            if (!container.unlockQueue.hasPending()) stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (registered) unregisterReceiver(receiver)
        registered = false
        super.onDestroy()
    }

    private fun startInForeground() {
        FixedNotices.ensureChannels(this)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle("La Vara espera el desbloqueo")
            .setContentText("Al desbloquear se ejecuta lo pendiente.")
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
        private const val CHANNEL_ID = FixedNotices.UNLOCK_CHANNEL
        private const val NOTIFICATION_ID = 1600

        /** Enciende la espera. Devuelve false si Android no lo permite (La Vara en segundo plano, Android 12+). */
        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, UnlockWaitService::class.java))
            true
        } catch (e: IllegalStateException) {
            false
        }
    }
}
