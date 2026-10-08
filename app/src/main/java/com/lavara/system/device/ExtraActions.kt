package com.lavara.system.device

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import android.view.KeyEvent
import com.lavara.LaVaraApp
import com.lavara.actions.Action
import com.lavara.actions.ActionResult
import com.lavara.actions.MediaCommand

/** Vibrar, copiar al portapapeles y controlar la música. Ninguna pide permisos peligrosos. */
class ExtraActions(private val context: Context) {

    fun vibrate(action: Action.Vibrate): ActionResult {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (vibrator == null || !vibrator.hasVibrator()) return ActionResult.Failure("Este teléfono no puede vibrar.")
        vibrator.vibrate(VibrationEffect.createOneShot(action.millis, VibrationEffect.DEFAULT_AMPLITUDE))
        return ActionResult.Success
    }

    /** Android 10+ no deja leer el portapapeles en segundo plano, pero sí escribirlo. */
    fun copy(action: Action.CopyToClipboard): ActionResult = try {
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("La Vara", action.text))
        ActionResult.Success
    } catch (e: RuntimeException) {
        ActionResult.Failure("Android no dejó copiar al portapapeles: ${e.message}")
    }

    /**
     * Sin app elegida: manda la tecla de música, como los botones de los audífonos (la recibe la app que esté
     * sonando o la última que sonó). Con app elegida: le habla directo a esa app, aunque esté cerrada.
     */
    suspend fun media(action: Action.MediaControl): ActionResult {
        if (action.packageName.isBlank()) return mediaKey(action.command)
        val music = MusicApps(context)
        val name = InstalledApps(context).label(action.packageName) ?: return ActionResult.Failure("La app de música ${action.packageName} no está instalada.")
        val outcome = music.control(action.packageName, action.command)
        if (outcome.how != null) {
            log("$name respondió ${outcome.how}.")
            return ActionResult.Success
        }
        // Último intento: la tecla de siempre, por si igual era la última app que sonó.
        mediaKey(action.command)
        if (action.command != MediaCommand.PLAY || music.soundsWithin(MusicApps.VERIFY_MILLIS)) {
            log("$name: ${outcome.steps.joinToString("; ")}; sonó con la tecla de música general.")
            return ActionResult.Success
        }
        return ActionResult.Failure(
            "$name no empezó a sonar (${outcome.steps.joinToString("; ")}; la tecla de música general tampoco). " +
                "$name no acepta que otra app la despierte estando cerrada.",
        )
    }

    private fun mediaKey(command: MediaCommand): ActionResult {
        val audio = context.getSystemService(AudioManager::class.java) ?: return ActionResult.Failure("No se encontró el audio del teléfono.")
        val code = MusicApps.keyCode(command)
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
        return ActionResult.Success
    }

    private fun log(message: String) {
        (context.applicationContext as LaVaraApp).container.logger.info("Música", message)
    }
}
