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

    /** Manda la tecla de música, como los botones de los audífonos: la recibe la app que esté sonando. */
    fun media(action: Action.MediaControl): ActionResult {
        val audio = context.getSystemService(AudioManager::class.java) ?: return ActionResult.Failure("No se encontró el audio del teléfono.")
        val code = when (action.command) {
            MediaCommand.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            MediaCommand.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
            MediaCommand.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
            MediaCommand.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaCommand.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
        return ActionResult.Success
    }
}
