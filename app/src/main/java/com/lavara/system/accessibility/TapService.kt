package com.lavara.system.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lavara.LaVaraApp
import com.lavara.R
import com.lavara.actions.ButtonList
import com.lavara.actions.ButtonMatch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.lang.ref.WeakReference

/**
 * Servicio de Accesibilidad de La Vara. Hace dos cosas, siempre en apps de la lista [AllowedApps]:
 * - cuando una automatización lo pide, toca un botón;
 * - cuando el usuario lo pide desde el editor ("Ver los botones"), lista los nombres de los botones que se
 *   ven en esa app, solo en memoria, por 3 minutos como máximo, para elegir uno.
 * No guarda lo que hay en pantalla y nunca escribe en los registros el texto de otras apps.
 */
class TapService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val captureTimeout = Runnable { stopCapture() }

    /** Última vez que se recorrió la pantalla mientras el usuario elige un botón. */
    private var lastScan = 0L

    override fun onServiceConnected() {
        instance = WeakReference(this)
        val apps = AllowedApps(this).get()
        applyAllowed(apps)
        log("Accesibilidad encendida. Apps permitidas: ${apps.size}.")
    }

    /** Solo se usa mientras el usuario elige un botón; el resto del tiempo La Vara no mira la pantalla. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val target = capturePackage ?: return
        if (SystemClock.uptimeMillis() > captureUntil) {
            stopCapture()
            return
        }
        if (event?.packageName?.toString() != target) return
        // Cada letra que se escribe cambia la pantalla: recorrerla en cada cambio traba la app (y el teclado).
        // Se recorre como mucho una vez por segundo, y una última vez cuando la pantalla se queda quieta.
        handler.removeCallbacks(scan)
        val wait = SCAN_EVERY_MILLIS - (SystemClock.uptimeMillis() - lastScan)
        if (wait <= 0) scan.run() else handler.postDelayed(scan, wait)
    }

    private val scan = Runnable {
        lastScan = SystemClock.uptimeMillis()
        val target = capturePackage ?: return@Runnable
        val root = rootInActiveWindow ?: return@Runnable
        if (root.packageName?.toString() != target) return@Runnable
        val labels = ButtonList.from(candidates(root))
        if (labels.isNotEmpty()) _captured.value = ScreenButtons(target, labels)
    }

    /**
     * Empieza a mirar los botones de [packageName] (que tiene que estar en la lista permitida). Pide a Android
     * los avisos de cambios en pantalla solo mientras dura, y muestra una notificación para volver a La Vara.
     */
    fun startCapture(packageName: String, appLabel: String): Boolean {
        if (packageName !in AllowedApps(this).get()) return false
        _captured.value = null
        capturePackage = packageName
        captureUntil = SystemClock.uptimeMillis() + CAPTURE_MILLIS
        lastScan = 0L
        // Se apaga sola a los 3 minutos aunque no llegue ningún aviso más (antes esperaba al siguiente aviso).
        handler.removeCallbacks(captureTimeout)
        handler.postDelayed(captureTimeout, CAPTURE_MILLIS)
        setEventTypes(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_VIEW_SCROLLED)
        showCaptureNotification(appLabel)
        return true
    }

    /** Deja de mirar la pantalla y vuelve a los avisos mínimos. */
    fun stopCapture() {
        handler.removeCallbacks(captureTimeout)
        handler.removeCallbacks(scan)
        capturePackage = null
        setEventTypes(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        NotificationManagerCompat.from(this).cancel(CAPTURE_NOTIFICATION)
    }

    private fun setEventTypes(types: Int) {
        val info = serviceInfo ?: return
        info.eventTypes = types
        serviceInfo = info
    }

    /** Todo lo tocable y visible de la pantalla, con su nombre y su posición. */
    private fun candidates(root: AccessibilityNodeInfo): List<ButtonList.Candidate> {
        val found = mutableListOf<ButtonList.Candidate>()
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        val bounds = Rect()
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            if (node.isVisibleToUser && node.isClickable) {
                node.getBoundsInScreen(bounds)
                found += ButtonList.Candidate(node.text, node.contentDescription, innerText(node), node.viewIdResourceName, bounds.top, bounds.left)
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return found
    }

    /** El primer texto o descripción de adentro de un botón que no tiene nombre propio (hasta 3 niveles). */
    private fun innerText(node: AccessibilityNodeInfo, depth: Int = 0): CharSequence? {
        if (depth >= 3) return null
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val own = child.text?.takeIf { it.isNotBlank() } ?: child.contentDescription?.takeIf { it.isNotBlank() }
            if (own != null) return own
            innerText(child, depth + 1)?.let { return it }
        }
        return null
    }

    // El permiso de notificaciones se revisa con areNotificationsEnabled; sin él, no se muestra y listo.
    @SuppressLint("MissingPermission")
    private fun showCaptureNotification(appLabel: String) {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CAPTURE_CHANNEL, "Elegir un botón", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Aparece solo mientras elegís un botón de otra app en el editor."
            },
        )
        val back = packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) ?: return
        val notification = NotificationCompat.Builder(this, CAPTURE_CHANNEL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle("Elegí el botón en $appLabel")
            .setContentText("Andá a la pantalla donde está el botón y tocá acá para volver a La Vara.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Andá a la pantalla donde está el botón y tocá acá para volver a La Vara."))
            .setContentIntent(PendingIntent.getActivity(this, CAPTURE_NOTIFICATION, back, PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .setTimeoutAfter(CAPTURE_MILLIS)
            .build()
        try {
            manager.notify(CAPTURE_NOTIFICATION, notification)
        } catch (_: SecurityException) {
            // Sin permiso de notificaciones: se vuelve a La Vara con el botón de apps recientes.
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(captureTimeout)
        handler.removeCallbacks(scan)
        instance = null
        capturePackage = null
        log("Accesibilidad apagada.")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /**
     * Le dice a Android de qué apps puede mandarle avisos. Una lista vacía para Android significa "todas",
     * por eso sin apps elegidas se pone solo La Vara.
     */
    fun applyAllowed(apps: Set<String>) {
        val info = serviceInfo ?: return
        info.packageNames = apps.ifEmpty { setOf(this@TapService.packageName) }.toTypedArray()
        serviceInfo = info
    }

    /**
     * Espera hasta [waitMillis] a que [packageName] esté a la vista y toca el botón [button].
     * Devuelve null si lo tocó, o el motivo si no. Antes de mirar los botones comprueba que la ventana
     * sea de esa app y que la app siga en la lista.
     */
    suspend fun tap(packageName: String, button: String, waitMillis: Long): String? {
        val deadline = SystemClock.uptimeMillis() + waitMillis
        var appSeen = false
        while (true) {
            if (packageName !in AllowedApps(this).get()) return "esa app no está en la lista de apps permitidas."
            val root = rootInActiveWindow
            if (root != null && root.packageName?.toString() == packageName) {
                appSeen = true
                val node = find(root, button)
                if (node != null) return if (click(node)) null else "Android no dejó tocar \"$button\"."
            }
            if (SystemClock.uptimeMillis() >= deadline) break
            delay(POLL_MILLIS)
        }
        if (appSeen) return "no encontré el botón \"$button\" en la pantalla."
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
            !getSystemService(PowerManager::class.java).isInteractive
        return if (locked) "la app no apareció en pantalla: el teléfono estaba bloqueado o con la pantalla apagada."
        else "la app no estaba a la vista. Poné antes la acción \"Abrir app\" de esa misma app."
    }

    /** El botón visible que mejor coincide, recorriendo la pantalla de arriba hacia abajo. */
    private fun find(root: AccessibilityNodeInfo, button: String): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestScore = 0
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            if (node.isVisibleToUser) {
                val score = ButtonMatch.score(button, node.text, node.contentDescription, node.viewIdResourceName)
                if (score > bestScore) {
                    best = node
                    bestScore = score
                    if (score == 3) break
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return best
    }

    /** Toca el botón o, si el texto está dentro de algo tocable, ese contenedor. */
    private fun click(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        return target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }

    private fun log(message: String) {
        (applicationContext as LaVaraApp).container.logger.info("Accesibilidad", message)
    }

    companion object {
        private const val POLL_MILLIS = 300L
        private const val MAX_NODES = 3000
        private const val CAPTURE_MILLIS = 3 * 60_000L
        private const val SCAN_EVERY_MILLIS = 1_000L
        private const val CAPTURE_CHANNEL = "elegir_boton"
        private const val CAPTURE_NOTIFICATION = 3001

        private var instance: WeakReference<TapService>? = null

        @Volatile private var capturePackage: String? = null
        @Volatile private var captureUntil = 0L

        private val _captured = MutableStateFlow<ScreenButtons?>(null)

        /** Los botones que se vieron por última vez mientras el usuario elige uno; null si todavía nada. */
        val captured: StateFlow<ScreenButtons?> = _captured

        /** Borra la lista (el usuario ya eligió o canceló) y deja de mirar la pantalla. */
        fun clearCapture() {
            _captured.value = null
            current()?.stopCapture() ?: run { capturePackage = null }
        }

        fun current(): TapService? = instance?.get()

        /** Si el usuario encendió el permiso de Accesibilidad de La Vara en Ajustes. */
        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, TapService::class.java)
            return context.getSystemService(AccessibilityManager::class.java)
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo?.serviceInfo?.let { s -> ComponentName(s.packageName, s.name) } == me }
        }

        fun openSettings(context: Context) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** Nombres de los botones que se ven en [packageName], para elegir uno. Solo vive en memoria. */
data class ScreenButtons(val packageName: String, val labels: List<String>)
