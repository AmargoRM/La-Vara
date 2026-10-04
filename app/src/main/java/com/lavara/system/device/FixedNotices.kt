package com.lavara.system.device

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Canales de los avisos fijos que Android exige a los servicios en primer plano ("La Vara está activa" y
 * "La Vara espera el desbloqueo"). Una app no puede esconderlos sola: solo el usuario, desde Ajustes de
 * Android. Si los apaga, los servicios siguen funcionando igual; el aviso solo deja de verse (en Android 13+
 * La Vara sigue listada en "Apps activas" de los ajustes rápidos).
 */
object FixedNotices {
    const val WATCH_CHANNEL = "vigilancia"
    const val UNLOCK_CHANNEL = "espera_desbloqueo"

    /** Crea los dos canales para que aparezcan en Ajustes aunque el servicio todavía no haya corrido. */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(WATCH_CHANNEL, "La Vara está activa", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Aviso fijo mientras La Vara escucha la batería, el cargador y el Wi-Fi. Se puede apagar sin apagar la vigilancia."
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(UNLOCK_CHANNEL, "Esperando el desbloqueo", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Aviso silencioso mientras una automatización espera a que desbloquees el teléfono. Se puede apagar sin perder la espera."
                setShowBadge(false)
            },
        )
    }

    /** Pantalla de Android de un canal, donde el usuario lo apaga. */
    fun channelSettings(context: Context, channelId: String): Intent {
        ensureChannels(context)
        return Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
    }

    /** Pantalla de Android con todas las notificaciones de La Vara (interruptor general y cada tipo). */
    fun appSettings(context: Context): Intent {
        ensureChannels(context)
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
}
