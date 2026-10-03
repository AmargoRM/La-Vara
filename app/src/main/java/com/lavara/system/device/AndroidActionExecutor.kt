package com.lavara.system.device

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lavara.R
import com.lavara.actions.Action
import com.lavara.actions.ActionExecutor
import com.lavara.actions.ActionResult
import com.lavara.actions.NavigationApp
import com.lavara.actions.Phone
import com.lavara.actions.link
import com.lavara.actions.recipient
import com.lavara.system.accessibility.AllowedApps
import com.lavara.system.accessibility.TapService
import com.lavara.ui.MainActivity
import java.util.concurrent.atomic.AtomicInteger

/** Hace en el teléfono las acciones que el motor le pasa. */
class AndroidActionExecutor(private val context: Context) : ActionExecutor {

    val systemControls = SystemControls(context)
    val smsSender = SmsSender(context)
    val notificationPrefs = NotificationPrefs(context)

    override suspend fun execute(action: Action): ActionResult = when (action) {
        is Action.ShowNotification -> showNotification(action)
        is Action.OpenApp -> openApp(action)
        is Action.OpenUrl -> openUrl(action)
        is Action.Flashlight -> systemControls.flashlight(action)
        is Action.SetVolume -> systemControls.setVolume(action)
        is Action.SetRingerMode -> systemControls.setRingerMode(action)
        is Action.DoNotDisturb -> systemControls.doNotDisturb(action)
        is Action.SetBrightness -> systemControls.setBrightness(action)
        is Action.OpenSystemPanel -> start(
            systemControls.panelIntent(action.panel),
            "el interruptor de ${action.panel.label}",
            requestCode = action.panel.ordinal + 7000,
        )
        is Action.WhatsAppMessage -> whatsApp(action)
        is Action.DialNumber -> start(
            Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", Phone.dialable(action.phone), null)),
            "el marcador con ${action.recipient}",
            requestCode = action.phone.hashCode(),
        )
        is Action.Navigate -> navigate(action)
        is Action.SendSms -> smsSender.send(action)
        is Action.TapInApp -> tapInApp(action)
        // Delay y RunAutomation los resuelve el motor; no deberían llegar acá.
        is Action.Delay, is Action.RunAutomation -> ActionResult.Failure("El motor no pasó esta acción al ejecutor")
    }

    fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun showNotification(action: Action.ShowNotification): ActionResult {
        // Con "Solo avisar si algo sale mal" la acción se da por hecha sin mostrar nada.
        if (notificationPrefs.onlyErrors) return ActionResult.Success
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val channel = if (notificationPrefs.floating) Channel.FLOATING else Channel.QUIET
        return notify(action.title, action.text, open, channel)?.let { ActionResult.Failure(it) } ?: ActionResult.Success
    }

    /** Muestra una notificación. Devuelve null si salió bien, o el motivo si no. */
    // El permiso se comprueba en canNotify(); lint no lo ve porque está en otra función.
    @SuppressLint("MissingPermission")
    private fun notify(title: String, text: String, onTap: PendingIntent, channel: Channel, id: Int = nextId.incrementAndGet()): String? {
        if (!canNotify()) return "Falta el permiso de notificaciones. Abrí La Vara y permitilo."
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return "Las notificaciones de La Vara están apagadas en los ajustes de Android."
        ensureChannel(channel)
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(onTap)
            .setAutoCancel(true)
            .build()
        return try {
            manager.notify(id, notification)
            null
        } catch (e: SecurityException) {
            "Android no dejó mostrar la notificación: ${e.message}"
        }
    }

    /**
     * Aviso discreto (no flota ni suena) de que [name] falló. Uno por automatización: si vuelve a fallar,
     * reemplaza al anterior en vez de acumularse. No se muestra si La Vara está a la vista.
     */
    fun notifyFailure(automationId: String, name: String, reason: String) {
        if (isInForeground()) return
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        notify("Falló: $name", reason, open, Channel.ERRORS, id = FAILURE_IDS + (automationId.hashCode() and 0xFFFF))
    }

    /**
     * Si Android deja a La Vara abrir apps aunque esté en segundo plano: con "Mostrar sobre otras apps", o con
     * el permiso de Accesibilidad encendido (Android exime a las apps con un servicio de Accesibilidad activo).
     */
    fun canOpenAppsInBackground(): Boolean = Settings.canDrawOverlays(context) || TapService.isEnabled(context)

    fun openBackgroundAppsSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** La Vara está a la vista (por ejemplo, al tocar "Probar ahora"). */
    private fun isInForeground(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    /**
     * Desde Android 10, una app en segundo plano no puede abrir otras apps salvo que tenga el permiso
     * "Mostrar sobre otras apps". Sin ese permiso, Android bloquea la apertura sin avisar; por eso se decide
     * antes y, si no se puede, se muestra una notificación que abre la app al tocarla.
     */
    private fun openApp(action: Action.OpenApp): ActionResult {
        val launch = context.packageManager.getLaunchIntentForPackage(action.packageName)
            ?: return ActionResult.Failure(
                "La app ${action.packageName} no está instalada o no se puede abrir. Editá la automatización y elegila de la lista.",
            )
        val name = InstalledApps(context).label(action.packageName) ?: action.packageName
        return start(launch, name, requestCode = action.packageName.hashCode())
    }

    private fun openUrl(action: Action.OpenUrl): ActionResult {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(action.url))
        if (context.packageManager.queryIntentActivities(view, 0).isEmpty()) {
            return ActionResult.Failure("No hay ninguna app para abrir enlaces de ${action.host}.")
        }
        return start(view, "el enlace de ${action.host}", requestCode = action.url.hashCode())
    }

    /** Abre el chat de WhatsApp con el texto escrito. Prefiere WhatsApp normal; si no está, WhatsApp Business. */
    private fun whatsApp(action: Action.WhatsAppMessage): ActionResult {
        val app = WHATSAPP_PACKAGES.firstOrNull { context.packageManager.getLaunchIntentForPackage(it) != null }
            ?: return ActionResult.Failure("WhatsApp no está instalado.")
        if (!Phone.hasCountryCode(action.phone)) {
            return ActionResult.Failure("El número de ${action.recipient} no tiene código de país (506 para Costa Rica). Editá la automatización.")
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(action.link())).setPackage(app)
        return start(intent, "el chat de WhatsApp con ${action.recipient}", requestCode = action.phone.hashCode())
    }

    /** Toca un botón en otra app con Accesibilidad, solo si el usuario la puso en la lista de permitidas. */
    private suspend fun tapInApp(action: Action.TapInApp): ActionResult {
        val name = InstalledApps(context).label(action.packageName) ?: action.packageName
        if (action.packageName !in AllowedApps(context).get()) {
            return ActionResult.Failure("$name no está en la lista de apps donde La Vara puede tocar botones. Agregala desde el editor.")
        }
        val service = TapService.current() ?: return ActionResult.Failure(
            "El permiso de Accesibilidad de La Vara está apagado. Encendelo en Ajustes → Accesibilidad → La Vara: tocar botones.",
        )
        val problem = service.tap(action.packageName, action.button, action.waitSeconds.coerceIn(1, 10) * 1000L)
        return if (problem == null) ActionResult.Success else ActionResult.Failure("No se pudo tocar en $name: $problem")
    }

    /** Waze se abre con un enlace web (sin Waze, abre el navegador); Google Maps, con su propio enlace. */
    private fun navigate(action: Action.Navigate): ActionResult {
        if (action.destination.isBlank()) return ActionResult.Failure("Falta el destino. Editá la automatización.")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(action.link()))
        if (action.app == NavigationApp.GOOGLE_MAPS) {
            if (context.packageManager.getLaunchIntentForPackage(GOOGLE_MAPS) == null) {
                return ActionResult.Failure("Google Maps no está instalado.")
            }
            intent.setPackage(GOOGLE_MAPS)
        }
        return start(intent, "${action.app.label} con el destino", requestCode = action.destination.hashCode())
    }

    /**
     * Abre [intent] si Android lo permite; si no, deja una notificación que lo abre al tocarla.
     * Con el teléfono bloqueado o la pantalla apagada, enciende la pantalla y abre apenas el usuario desbloquea.
     */
    private fun start(intent: Intent, name: String, requestCode: Int): ActionResult {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val foreground = isInForeground()
        if (foreground || canOpenAppsInBackground()) {
            val locked = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
                !context.getSystemService(PowerManager::class.java).isInteractive
            return try {
                context.startActivity(if (locked && !foreground) UnlockAndOpenActivity.intent(context, intent, name) else intent)
                ActionResult.Success
            } catch (e: RuntimeException) {
                ActionResult.Failure("Android no dejó abrir $name: ${e.message}")
            }
        }
        val shown = notifyToOpen(intent, name, requestCode)
        return ActionResult.Failure(
            "Android no deja abrir $name con La Vara en segundo plano. " +
                when {
                    !notificationPrefs.tapToOpen -> ""
                    shown == null -> "Se mostró una notificación para abrirla. "
                    else -> "Tampoco se pudo avisar: $shown "
                } +
                "Para que se abra sola, permití \"Mostrar sobre otras apps\" o la Accesibilidad de La Vara.",
        )
    }

    /**
     * Notificación "Tocá para abrir", discreta (no flota ni suena). Solo si el usuario la activó en el menú ☰.
     * Devuelve null si se mostró, o el motivo si no.
     */
    fun notifyToOpen(intent: Intent, name: String, requestCode: Int): String? {
        if (!notificationPrefs.tapToOpen) return "la notificación \"Tocá para abrir\" está apagada"
        val tap = PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE)
        return notify("Abrir $name", "Tocá para abrir $name.", tap, Channel.TAP_TO_OPEN)
    }

    /** Canales de Android: la importancia de un canal no se puede subir después de crearlo, por eso son tres. */
    private enum class Channel(val id: String, val title: String, val importance: Int, val description: String) {
        FLOATING("automatizaciones", "Automatizaciones (flotantes)", NotificationManager.IMPORTANCE_HIGH,
            "Notificaciones de tus automatizaciones que aparecen arriba de la pantalla."),
        QUIET("automatizaciones_discretas", "Automatizaciones (discretas)", NotificationManager.IMPORTANCE_LOW,
            "Notificaciones de tus automatizaciones que no flotan ni suenan."),
        TAP_TO_OPEN("tocar_para_abrir", "Tocá para abrir", NotificationManager.IMPORTANCE_LOW,
            "Aviso discreto cuando Android no dejó abrir algo solo."),
        ERRORS("errores", "Errores", NotificationManager.IMPORTANCE_LOW,
            "Aviso discreto cuando una automatización falla."),
    }

    private fun ensureChannel(channel: Channel) {
        val created = NotificationChannel(channel.id, channel.title, channel.importance).apply { description = channel.description }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(created)
    }

    private companion object {
        const val GOOGLE_MAPS = "com.google.android.apps.maps"
        val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

        // Los ids de las notificaciones de actualizaciones son 1001 y 1002; estas empiezan en 2000.
        val nextId = AtomicInteger(2000)

        // Avisos de error: un id fijo por automatización, lejos de los demás.
        const val FAILURE_IDS = 100_000
    }
}
