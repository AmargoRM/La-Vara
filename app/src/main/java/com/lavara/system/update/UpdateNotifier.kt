package com.lavara.system.update

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
import com.lavara.ui.MainActivity

class UpdateNotifier(private val context: Context) {

    fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun notifyNewVersion(release: ReleaseInfo) {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_INSTALL_UPDATE, true)
        show(
            id = ID_NEW_VERSION,
            title = "Hay una versión nueva de La Vara",
            text = "Versión ${release.versionName}. Tocá para descargarla e instalarla.",
            intent = intent,
        )
    }

    fun notifyTokenExpiring(daysLeft: Long) {
        val text = if (daysLeft < 0) {
            "El token de GitHub venció. La Vara no puede buscar actualizaciones hasta que pegues uno nuevo."
        } else {
            "El token de GitHub vence en $daysLeft días. Creá uno nuevo y pegalo en La Vara."
        }
        show(ID_TOKEN, "Token de GitHub por vencer", text, Intent(context, MainActivity::class.java))
    }

    // El permiso se comprueba en canNotify(); lint no lo ve porque está en otra función.
    @SuppressLint("MissingPermission")
    private fun show(id: Int, title: String, text: String, intent: Intent) {
        if (!canNotify()) return
        ensureChannel()
        val pending = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // El permiso se quitó entre la comprobación y el aviso: no hay nada que mostrar.
        }
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Actualizaciones de La Vara", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "Avisa cuando hay una versión nueva para instalar." }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "actualizaciones"
        const val ID_NEW_VERSION = 1001
        const val ID_TOKEN = 1002
    }
}
