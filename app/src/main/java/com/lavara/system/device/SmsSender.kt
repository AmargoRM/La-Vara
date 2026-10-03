package com.lavara.system.device

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.lavara.actions.Action
import com.lavara.actions.ActionResult
import com.lavara.actions.Phone
import com.lavara.actions.recipient
import java.util.concurrent.atomic.AtomicInteger

/**
 * Envía SMS con el permiso SEND_SMS, que el usuario concede al agregar la acción. Usa la SIM que el
 * teléfono tenga elegida para SMS. Android avisa después si salió o no: lo registra [SmsSentReceiver].
 */
class SmsSender(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    /** Ajustes de La Vara, donde se destraba la "configuración restringida" y se da el permiso a mano. */
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // El permiso se comprueba en hasPermission(); lint no lo ve porque está en otra función.
    @SuppressLint("MissingPermission")
    fun send(action: Action.SendSms): ActionResult {
        val to = action.recipient
        if (!hasPermission()) {
            return ActionResult.Failure("Falta el permiso para enviar SMS. Abrí esta automatización en La Vara y tocá \"Dar el permiso\".")
        }
        val number = Phone.dialable(action.phone)
        if (number.count { it.isDigit() } < 3) return ActionResult.Failure("El SMS no tiene número. Editá la automatización.")
        if (action.text.isBlank()) return ActionResult.Failure("El SMS a $to no tiene texto. Editá la automatización.")
        val manager = smsManager() ?: return ActionResult.Failure("Este teléfono no puede enviar SMS.")
        return try {
            val parts = manager.divideMessage(action.text)
            val sent = ArrayList(parts.indices.map { i -> sentIntent(to, i + 1, parts.size) })
            manager.sendMultipartTextMessage(number, null, parts, sent, null)
            ActionResult.Success
        } catch (e: SecurityException) {
            ActionResult.Failure("Android no dejó enviar el SMS a $to: falta el permiso.")
        } catch (e: RuntimeException) {
            ActionResult.Failure("No se pudo enviar el SMS a $to: ${e.message ?: e::class.simpleName}")
        }
    }

    @Suppress("DEPRECATION")
    private fun smsManager(): SmsManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java) else SmsManager.getDefault()

    /** Aviso que Android manda al terminar de enviar cada parte; lleva solo a quién, nunca el texto. */
    private fun sentIntent(to: String, part: Int, total: Int): PendingIntent {
        val intent = Intent(context, SmsSentReceiver::class.java)
            .putExtra(SmsSentReceiver.EXTRA_TO, to)
            .putExtra(SmsSentReceiver.EXTRA_PART, part)
            .putExtra(SmsSentReceiver.EXTRA_TOTAL, total)
        return PendingIntent.getBroadcast(context, nextRequest.incrementAndGet(), intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private companion object {
        val nextRequest = AtomicInteger(9000)
    }
}
