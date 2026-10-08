package com.lavara.system.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.lavara.LaVaraApp
import com.lavara.triggers.Trigger
import com.lavara.triggers.TriggerEvent
import com.lavara.triggers.TriggerMatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Disparador "Al llegar una notificación". Android le pasa a La Vara cada notificación nueva solo si el
 * usuario le dio "Acceso a notificaciones". El título y el texto se usan para comparar y se descartan:
 * nunca se guardan ni van a los registros. Solo pasa al motor lo que coincide con alguna automatización.
 *
 * Android le avisa de todas las notificaciones del teléfono (y de cada vez que una se actualiza). Para no
 * trabajar de más, las automatizaciones con este disparador se tienen en memoria y se comparan ahí: sin
 * ninguna que coincida, La Vara no toca la base de datos ni el motor.
 */
class NotificationWatchService : NotificationListenerService() {

    /** Automatizaciones activas con disparador de notificación; null hasta la primera lectura. */
    @Volatile private var watching: List<Pair<String, Trigger.Notification>>? = null
    private var watchJob: Job? = null

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || sbn.isOngoing) return
        // Sin automatizaciones de notificación, no se lee ni el título.
        if (watching?.isEmpty() == true) return
        val notification = sbn.notification ?: return
        // El resumen de un grupo repite lo que ya avisaron sus notificaciones.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        val event = TriggerEvent.NotificationPosted(sbn.packageName, title, text, sbn.key, sbn.postTime)
        val container = (applicationContext as LaVaraApp).container
        val known = watching
        if (known != null) {
            if (known.any { (id, trigger) -> TriggerMatcher.matches(trigger, id, event) }) {
                container.appScope.launch { container.automationRunner.handle(event) }
            }
            return
        }
        container.appScope.launch {
            val interested = container.automationRepository.all().any {
                it.enabled && it.trigger is Trigger.Notification && TriggerMatcher.matches(it.trigger, it.id, event)
            }
            if (interested) container.automationRunner.handle(event)
        }
    }

    override fun onListenerConnected() {
        instance = WeakReference(this)
        val container = (applicationContext as LaVaraApp).container
        watchJob?.cancel()
        watchJob = container.appScope.launch {
            container.automationRepository.observeAll().collect { list ->
                watching = list.mapNotNull { a -> (a.trigger as? Trigger.Notification)?.takeIf { a.enabled }?.let { a.id to it } }
            }
        }
        (applicationContext as LaVaraApp).container.logger.info("Notificaciones", "Acceso a notificaciones activo: La Vara escucha las notificaciones de otras apps")
    }

    override fun onListenerDisconnected() {
        instance = null
        watchJob?.cancel()
        watchJob = null
        watching = null
    }

    companion object {
        private var instance: WeakReference<NotificationWatchService>? = null

        /** El servicio conectado, para leer las notificaciones visibles; null sin "Acceso a notificaciones". */
        fun current(): NotificationWatchService? = instance?.get()

        fun isEnabled(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

        /** Abre la pantalla de Android donde se da "Acceso a notificaciones" (directo a La Vara en Android 11+). */
        fun openSettings(context: Context) {
            val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(context, NotificationWatchService::class.java).flattenToString(),
                )
            } else {
                null
            }
            val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            for (intent in listOfNotNull(detail, list)) {
                try {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return
                } catch (_: RuntimeException) {
                    // Algunos teléfonos no tienen la pantalla de detalle: se prueba con la lista.
                }
            }
        }
    }
}
