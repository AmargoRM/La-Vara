package com.lavara.system.device

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.service.media.MediaBrowserService
import android.view.KeyEvent
import com.lavara.actions.MediaCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Apps de música del teléfono y cómo despertarlas aunque estén cerradas, con APIs públicas:
 * 1. Conectarse a su "servicio de música" (MediaBrowserService), lo mismo que hace Android para el botón
 *    "reanudar" de la música y los autos, y pedirle reproducir.
 * 2. Si no deja, mandarle la tecla de música directo a ella (no a "la última que sonó").
 * No lee qué canción suena ni nada de la app.
 */
class MusicApps(private val context: Context) {

    /** Las apps que aceptan órdenes de música (tienen servicio de música o reciben la tecla de música). */
    fun list(): List<InstalledApp> {
        val pm = context.packageManager
        val services = pm.queryIntentServices(Intent(MediaBrowserService.SERVICE_INTERFACE), 0).map { it.serviceInfo.packageName }
        val receivers = pm.queryBroadcastReceivers(Intent(Intent.ACTION_MEDIA_BUTTON), 0).map { it.activityInfo.packageName }
        val labels = InstalledApps(context)
        return (services + receivers).distinct()
            .filter { it != context.packageName }
            .mapNotNull { pkg -> labels.label(pkg)?.let { InstalledApp(it, pkg) } }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Le da [command] a [packageName]. Devuelve cómo lo logró (para los registros), o null si la app no
     * respondió por ningún camino.
     */
    suspend fun control(packageName: String, command: MediaCommand): String? {
        // Solo "reproducir" se comprueba: con "reproducir o pausar" un segundo intento podría volver a pausar.
        val wantsSound = command == MediaCommand.PLAY
        if (viaService(packageName, command)) {
            if (!wantsSound || soundsWithin(VERIFY_MILLIS)) return "por su servicio de música"
        }
        if (viaButton(packageName, command)) {
            if (!wantsSound || soundsWithin(VERIFY_MILLIS)) return "con la tecla de música enviada a esa app"
        }
        return null
    }

    /** Se conecta al servicio de música de la app, le manda la orden y espera a ver si suena antes de soltarla. */
    private suspend fun viaService(packageName: String, command: MediaCommand): Boolean {
        val service = context.packageManager
            .queryIntentServices(Intent(MediaBrowserService.SERVICE_INTERFACE).setPackage(packageName), 0)
            .firstOrNull()?.serviceInfo ?: return false
        // MediaBrowser tiene que usarse desde el hilo principal.
        return withContext(Dispatchers.Main) {
            var browser: MediaBrowser? = null
            val connected = withTimeoutOrNull(CONNECT_MILLIS) {
                suspendCancellableCoroutine { cont ->
                    // "Lo reciente": la misma pista que usa Android para reanudar la última música de una app.
                    val hints = Bundle().apply { putBoolean(MediaBrowserService.BrowserRoot.EXTRA_RECENT, true) }
                    val callback = object : MediaBrowser.ConnectionCallback() {
                        override fun onConnected() { if (cont.isActive) cont.resume(true) }
                        override fun onConnectionFailed() { if (cont.isActive) cont.resume(false) }
                        override fun onConnectionSuspended() { if (cont.isActive) cont.resume(false) }
                    }
                    val created = MediaBrowser(context, ComponentName(service.packageName, service.name), callback, hints)
                    browser = created
                    created.connect()
                    cont.invokeOnCancellation { created.disconnect() }
                }
            } ?: false
            val current = browser
            if (!connected || current == null) {
                current?.disconnect()
                return@withContext false
            }
            try {
                val controller = MediaController(context, current.sessionToken)
                val controls = controller.transportControls
                when (command) {
                    MediaCommand.PLAY -> { controls.prepare(); controls.play() }
                    MediaCommand.PAUSE -> controls.pause()
                    MediaCommand.PLAY_PAUSE ->
                        if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) controls.pause()
                        else { controls.prepare(); controls.play() }
                    MediaCommand.NEXT -> controls.skipToNext()
                    MediaCommand.PREVIOUS -> controls.skipToPrevious()
                }
                // Mantener la conexión un rato: si se suelta enseguida, algunas apps se cierran antes de sonar.
                delay(HOLD_MILLIS)
                true
            } catch (_: RuntimeException) {
                false
            } finally {
                current.disconnect()
            }
        }
    }

    /** Le manda la tecla de música directo a la app (no a la que sonó por última vez). */
    private fun viaButton(packageName: String, command: MediaCommand): Boolean {
        val receiver = context.packageManager
            .queryBroadcastReceivers(Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(packageName), 0)
            .firstOrNull()?.activityInfo ?: return false
        val code = keyCode(command)
        val now = SystemClock.uptimeMillis()
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
                .setComponent(ComponentName(receiver.packageName, receiver.name))
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(now, now, action, code, 0))
            try {
                context.sendBroadcast(intent)
            } catch (_: RuntimeException) {
                return false
            }
        }
        return true
    }

    /** Si empieza a sonar algo en [millis] milisegundos (mira cada medio segundo, solo ese rato). */
    private suspend fun soundsWithin(millis: Long): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        val deadline = SystemClock.uptimeMillis() + millis
        while (SystemClock.uptimeMillis() < deadline) {
            if (audio.isMusicActive) return true
            delay(500)
        }
        return audio.isMusicActive
    }

    /** Si la app está instalada. */
    fun installed(packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    companion object {
        private const val CONNECT_MILLIS = 5_000L
        private const val HOLD_MILLIS = 1_500L
        private const val VERIFY_MILLIS = 3_000L

        fun keyCode(command: MediaCommand): Int = when (command) {
            MediaCommand.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            MediaCommand.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
            MediaCommand.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
            MediaCommand.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaCommand.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }
    }
}
