package com.lavara.system.device

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lavara.R
import com.lavara.actions.Action
import com.lavara.actions.ActionExecutor
import com.lavara.actions.ActionResult
import com.lavara.ui.MainActivity
import java.util.concurrent.atomic.AtomicInteger

/** Hace en el teléfono las acciones que el motor le pasa. */
class AndroidActionExecutor(private val context: Context) : ActionExecutor {

    override suspend fun execute(action: Action): ActionResult = when (action) {
        is Action.ShowNotification -> showNotification(action)
        is Action.OpenApp -> openApp(action)
        // Delay y RunAutomation los resuelve el motor; no deberían llegar acá.
        is Action.Delay, is Action.RunAutomation -> ActionResult.Failure("El motor no pasó esta acción al ejecutor")
    }

    fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    // El permiso se comprueba en canNotify(); lint no lo ve porque está en otra función.
    @SuppressLint("MissingPermission")
    private fun showNotification(action: Action.ShowNotification): ActionResult {
        if (!canNotify()) {
            return ActionResult.Failure("Falta el permiso de notificaciones. Abrí La Vara y permitilo.")
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            return ActionResult.Failure("Las notificaciones de La Vara están apagadas en los ajustes de Android.")
        }
        ensureChannel()
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(action.title)
            .setContentText(action.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(action.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        return try {
            manager.notify(nextId.incrementAndGet(), notification)
            ActionResult.Success
        } catch (e: SecurityException) {
            ActionResult.Failure("Android no dejó mostrar la notificación: ${e.message}")
        }
    }

    /**
     * Intento básico. Desde Android 10 una app en segundo plano no puede abrir otras apps libremente;
     * la solución completa (permiso "Mostrar sobre otras apps" o notificación) llega en S5.
     */
    private fun openApp(action: Action.OpenApp): ActionResult {
        val launch = context.packageManager.getLaunchIntentForPackage(action.packageName)
            ?: return ActionResult.Failure("La app ${action.packageName} no está instalada o no se puede abrir")
        return try {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ActionResult.Success
        } catch (e: RuntimeException) {
            ActionResult.Failure("Android no dejó abrir ${action.packageName}: ${e.message}")
        }
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Automatizaciones", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "Notificaciones que muestran tus automatizaciones." }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "automatizaciones"

        // Los ids de las notificaciones de actualizaciones son 1001 y 1002; estas empiezan en 2000.
        val nextId = AtomicInteger(2000)
    }
}
