package com.lavara.system.notifications

import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.lavara.LaVaraApp
import com.lavara.actions.Action
import com.lavara.actions.ActionResult
import com.lavara.actions.NotificationPick
import com.lavara.system.device.InstalledApps

/**
 * Usa los botones de las notificaciones de otras apps ("Responder", "Marcar como leído", "Pausa"…), como si
 * el usuario los tocara en la cortina de notificaciones. No abre la app, así que funciona con el teléfono
 * bloqueado. Respeta los botones que la app marcó como "solo desbloqueado". Nunca escribe en los registros
 * el contenido de las notificaciones ni la respuesta.
 */
class NotificationActions(private val context: Context) {

    fun reply(action: Action.ReplyToNotification): ActionResult {
        val (name, active) = active(action.packageName) ?: return noAccess()
        val choice = NotificationPick.reply(active.map { shown(it) }, action.from)
            ?: return ActionResult.Failure(
                "No hay ninguna notificación de $name" + forWhom(action.from) + " con botón de responder. " +
                    "Solo se puede responder a un chat que tenga una notificación sin leer.",
            )
        val button = active[choice.notification].notification.actions.orEmpty()[choice.button]
        lockedProblem(button, name)?.let { return it }
        val inputs = button.remoteInputs ?: return ActionResult.Failure("El botón de responder de $name no tiene dónde escribir.")
        val fill = Intent()
        RemoteInput.addResultsToIntent(inputs, fill, Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, action.text) } })
        return send(button.actionIntent, fill, name, "respondió una notificación")
    }

    fun tap(action: Action.TapNotificationButton): ActionResult {
        val (name, active) = active(action.packageName) ?: return noAccess()
        val choice = NotificationPick.button(active.map { shown(it) }, action.from, action.button)
            ?: return ActionResult.Failure("No hay ninguna notificación de $name" + forWhom(action.from) + " con el botón \"${action.button}\".")
        val button = active[choice.notification].notification.actions.orEmpty()[choice.button]
        lockedProblem(button, name)?.let { return it }
        return send(button.actionIntent, null, name, "tocó \"${button.title}\" en una notificación")
    }

    /** Nombre de la app y sus notificaciones visibles ahora, o null si no hay "Acceso a notificaciones". */
    private fun active(packageName: String): Pair<String, List<StatusBarNotification>>? {
        val service = NotificationWatchService.current() ?: return null
        val list = try {
            service.activeNotifications.orEmpty().filter {
                it.packageName == packageName && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
            }
        } catch (_: SecurityException) {
            return null
        }
        return (InstalledApps(context).label(packageName) ?: packageName) to list
    }

    private fun shown(sbn: StatusBarNotification): NotificationPick.Shown {
        val n = sbn.notification
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val buttons = n.actions.orEmpty().map { NotificationPick.Button(it.title?.toString().orEmpty(), !it.remoteInputs.isNullOrEmpty()) }
        return NotificationPick.Shown(title, sbn.postTime, buttons)
    }

    /** Android 12+: si la app pidió que ese botón solo funcione desbloqueado, La Vara no se lo salta. */
    private fun lockedProblem(button: Notification.Action, name: String): ActionResult? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !button.isAuthenticationRequired) return null
        val locked = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        return if (locked) ActionResult.Failure("$name pide desbloquear el teléfono para ese botón. La Vara no se salta ese bloqueo.") else null
    }

    private fun send(intent: PendingIntent, fill: Intent?, name: String, what: String): ActionResult = try {
        intent.send(context, 0, fill)
        (context.applicationContext as LaVaraApp).container.logger.info("Notificaciones", "En $name: $what.")
        ActionResult.Success
    } catch (_: PendingIntent.CanceledException) {
        ActionResult.Failure("La notificación de $name ya no estaba (se borró o se respondió antes).")
    }

    private fun noAccess() = ActionResult.Failure(
        "Falta el \"Acceso a notificaciones\" de La Vara. Encendelo desde la tarjeta Permisos en Inicio.",
    )

    private fun forWhom(from: String) = if (from.isBlank()) "" else " de \"$from\""
}
