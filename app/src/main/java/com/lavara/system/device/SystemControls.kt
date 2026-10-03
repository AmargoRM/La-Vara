package com.lavara.system.device

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import com.lavara.actions.Action
import com.lavara.actions.ActionResult
import com.lavara.actions.DndMode
import com.lavara.actions.RingerMode
import com.lavara.actions.SystemPanel
import com.lavara.actions.VolumeStream
import kotlin.math.roundToInt

/**
 * Linterna, volumen, modo de sonido, No molestar y brillo: lo que Android sí deja cambiar a una app.
 * Wi-Fi, datos, Bluetooth, ubicación, NFC y modo avión no: para esos solo se abre el interruptor ([panelIntent]).
 */
class SystemControls(private val context: Context) {

    private val audio get() = context.getSystemService(AudioManager::class.java)
    private val notifications get() = context.getSystemService(NotificationManager::class.java)

    /** Permiso "Acceso a No molestar": hace falta para No molestar y para el modo silencio. */
    fun hasDndAccess(): Boolean = notifications.isNotificationPolicyAccessGranted

    fun openDndAccessSettings() {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Permiso "Modificar ajustes del sistema": hace falta para el brillo. */
    fun canWriteSettings(): Boolean = Settings.System.canWrite(context)

    fun openWriteSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun flashlight(action: Action.Flashlight): ActionResult {
        val camera = context.getSystemService(CameraManager::class.java)
        return try {
            val id = camera.cameraIdList.firstOrNull {
                camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return ActionResult.Failure("Este teléfono no tiene linterna que La Vara pueda usar.")
            camera.setTorchMode(id, action.on)
            ActionResult.Success
        } catch (e: Exception) {
            // Por ejemplo, si la cámara está abierta en otra app.
            ActionResult.Failure("Android no dejó ${if (action.on) "encender" else "apagar"} la linterna: ${e.message}")
        }
    }

    fun setVolume(action: Action.SetVolume): ActionResult {
        val stream = when (action.stream) {
            VolumeStream.MEDIA -> AudioManager.STREAM_MUSIC
            VolumeStream.RING -> AudioManager.STREAM_RING
            VolumeStream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
            VolumeStream.ALARM -> AudioManager.STREAM_ALARM
        }
        val min = audio.getStreamMinVolume(stream)
        val max = audio.getStreamMaxVolume(stream)
        val level = (action.percent * max / 100.0).roundToInt().coerceIn(min, max)
        return try {
            audio.setStreamVolume(stream, level, 0)
            ActionResult.Success
        } catch (e: SecurityException) {
            // Bajar el timbre a 0 cambia el modo de sonido; con No molestar encendido Android lo exige.
            ActionResult.Failure("Android no dejó cambiar el volumen de ${action.stream.label}. ${DND_HINT}")
        }
    }

    fun setRingerMode(action: Action.SetRingerMode): ActionResult {
        if (action.mode == RingerMode.SILENT && !hasDndAccess()) {
            return ActionResult.Failure("Para poner el teléfono en silencio falta el permiso. $DND_HINT")
        }
        return try {
            audio.ringerMode = when (action.mode) {
                RingerMode.NORMAL -> AudioManager.RINGER_MODE_NORMAL
                RingerMode.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
                RingerMode.SILENT -> AudioManager.RINGER_MODE_SILENT
            }
            ActionResult.Success
        } catch (e: SecurityException) {
            ActionResult.Failure("Android no dejó cambiar el modo de sonido. $DND_HINT")
        }
    }

    fun doNotDisturb(action: Action.DoNotDisturb): ActionResult {
        if (!hasDndAccess()) return ActionResult.Failure("Falta el permiso para cambiar No molestar. $DND_HINT")
        return try {
            notifications.setInterruptionFilter(
                when (action.mode) {
                    DndMode.OFF -> NotificationManager.INTERRUPTION_FILTER_ALL
                    DndMode.PRIORITY -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    DndMode.ALARMS -> NotificationManager.INTERRUPTION_FILTER_ALARMS
                    DndMode.SILENCE -> NotificationManager.INTERRUPTION_FILTER_NONE
                },
            )
            ActionResult.Success
        } catch (e: SecurityException) {
            ActionResult.Failure("Android no dejó cambiar No molestar: ${e.message}")
        }
    }

    fun setBrightness(action: Action.SetBrightness): ActionResult {
        if (!canWriteSettings()) {
            return ActionResult.Failure(
                "Falta el permiso \"Modificar ajustes del sistema\" para cambiar el brillo. Abrí La Vara: en Inicio está el botón para darlo.",
            )
        }
        val resolver = context.contentResolver
        return try {
            if (action.auto) {
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
            } else {
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                // El ajuste público va de 0 a 255; 0 puede dejar la pantalla negra, por eso el mínimo es 1.
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, (action.percent * 255 / 100).coerceIn(1, 255))
            }
            ActionResult.Success
        } catch (e: SecurityException) {
            ActionResult.Failure("Android no dejó cambiar el brillo: ${e.message}")
        }
    }

    /** La pantalla o ventanita de Android con el interruptor de [panel]. */
    fun panelIntent(panel: SystemPanel): Intent = Intent(
        when (panel) {
            SystemPanel.WIFI -> Settings.Panel.ACTION_WIFI
            SystemPanel.MOBILE_DATA -> Settings.Panel.ACTION_INTERNET_CONNECTIVITY
            SystemPanel.NFC -> Settings.Panel.ACTION_NFC
            SystemPanel.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
            SystemPanel.LOCATION -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            SystemPanel.AIRPLANE_MODE -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
        },
    )

    private companion object {
        const val DND_HINT = "Abrí La Vara y, en Inicio, tocá \"Permitir No molestar\"."
    }
}
