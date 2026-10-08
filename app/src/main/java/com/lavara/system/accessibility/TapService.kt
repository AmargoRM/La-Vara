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
import com.lavara.actions.TapRecording
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.lang.ref.WeakReference

/**
 * Servicio de Accesibilidad de La Vara. Hace tres cosas, siempre en apps de la lista [AllowedApps]:
 * - cuando una automatización lo pide, toca un botón;
 * - cuando el usuario lo pide desde el editor ("Ver los botones"), lista los nombres de los botones que se
 *   ven en esa app, solo en memoria, por 3 minutos como máximo, para elegir uno;
 * - cuando el usuario toca "Grabar toques" en Inicio, anota el nombre visible de cada botón que él toca en esa
 *   app, solo en memoria y por 5 minutos como máximo, para armar una automatización que los repita.
 * No guarda lo que hay en pantalla y nunca escribe en los registros el texto de otras apps.
 */
class TapService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val captureTimeout = Runnable { stopCapture() }
    private val recordTimeout = Runnable { stopRecording() }

    /** Última vez que se recorrió la pantalla mientras el usuario elige un botón. */
    private var lastScan = 0L

    override fun onServiceConnected() {
        instance = WeakReference(this)
        val apps = AllowedApps(this).get()
        applyAllowed(apps)
        log("Accesibilidad encendida. Apps permitidas: ${apps.size}.")
    }

    /** Solo se usa mientras el usuario elige un botón o graba toques; el resto del tiempo La Vara no mira la pantalla. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (recordPackage != null) {
            if (event != null) onRecordEvent(event)
            return
        }
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
        stopRecording()
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
        if (recordPackage == null) setEventTypes(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        NotificationManagerCompat.from(this).cancel(CAPTURE_NOTIFICATION)
    }

    /**
     * Empieza a grabar los toques del usuario en [packageName] (que tiene que estar en la lista permitida).
     * Pide a Android solo el aviso "se tocó algo" mientras dura, y se apaga sola a los 5 minutos.
     */
    fun startRecording(packageName: String, appLabel: String): Boolean {
        if (packageName !in AllowedApps(this).get()) return false
        stopCapture()
        val recording = TapRecording(packageName, appLabel)
        _recording.value = recording
        recordPackage = packageName
        recordUntil = SystemClock.uptimeMillis() + RECORD_MILLIS
        handler.removeCallbacks(recordTimeout)
        handler.postDelayed(recordTimeout, RECORD_MILLIS)
        setEventTypes(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_VIEW_CLICKED)
        showRecordNotification(recording)
        log("Grabación de toques empezada en $appLabel.")
        return true
    }

    /** Termina la grabación (si había una) y vuelve a los avisos mínimos. Lo grabado queda para crear la automatización. */
    fun stopRecording() {
        handler.removeCallbacks(recordTimeout)
        if (recordPackage == null) return
        recordPackage = null
        if (capturePackage == null) setEventTypes(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        NotificationManagerCompat.from(this).cancel(RECORD_NOTIFICATION)
        val recording = _recording.value ?: return
        _recording.value = recording.copy(active = false)
        log("Grabación de toques terminada en ${recording.appLabel}: ${recording.labels.size} toques grabados, ${recording.skipped} sin nombre.")
    }

    /** Un toque del usuario mientras se graba. Solo cuenta si fue en la app que se está grabando. */
    private fun onRecordEvent(event: AccessibilityEvent) {
        val target = recordPackage ?: return
        if (SystemClock.uptimeMillis() > recordUntil) {
            stopRecording()
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED || event.packageName?.toString() != target) return
        if (target !in AllowedApps(this).get()) {
            stopRecording()
            return
        }
        val before = _recording.value ?: return
        val after = before.add(clickedLabel(event, target), SystemClock.uptimeMillis())
        if (after == before) return
        _recording.value = after
        if (after.labels.size != before.labels.size || after.skipped != before.skipped) showRecordNotification(after)
    }

    /**
     * El nombre visible de lo que el usuario tocó, el mismo que "Ver los botones" mostraría y con el que
     * [ButtonMatch] después lo encuentra. null si no tiene nombre útil o si era un campo para escribir
     * (lo que se escribe nunca se graba).
     */
    private fun clickedLabel(event: AccessibilityEvent, target: String): String? {
        if (event.isPassword) return null
        val node = event.source
        if (node != null) {
            if (node.isPassword || node.isEditable) return null
            ButtonList.label(ButtonList.Candidate(node.text, node.contentDescription, innerText(node), 0, 0))?.let { return it }
            // Muchas apps dibujan el texto encima del botón sin ponerlo adentro.
            val root = rootInActiveWindow
            if (root != null && root.packageName?.toString() == target) textOver(root, node)?.let { return it }
        }
        return ButtonList.label(ButtonList.Candidate(event.text.singleOrNull(), event.contentDescription, null, 0, 0))
    }

    /** El primer texto visible (de arriba hacia abajo) que queda encima de [button] y que tocaría ese botón. */
    private fun textOver(root: AccessibilityNodeInfo, button: AccessibilityNodeInfo): String? {
        val bounds = Rect()
        button.getBoundsInScreen(bounds)
        val target = ButtonList.Box(bounds.left, bounds.top, bounds.right, bounds.bottom)
        val parts = visibleParts(root)
        val boxes = parts.clickables.map { it.second }
        return parts.texts
            .sortedWith(compareBy({ it.second.top }, { it.second.left }))
            .firstNotNullOfOrNull { (node, rect) ->
                val index = ButtonList.smallestCovering(boxes, rect.centerX(), rect.centerY())
                if (index != null && boxes[index] == target) {
                    ButtonList.label(ButtonList.Candidate(node.text, node.contentDescription, null, 0, 0))
                } else {
                    null
                }
            }
    }

    // El permiso de notificaciones se revisa con areNotificationsEnabled; sin él, no se muestra y listo.
    @SuppressLint("MissingPermission")
    private fun showRecordNotification(recording: TapRecording) {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(RECORD_CHANNEL, "Grabar toques", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Aparece solo mientras grabás toques en otra app."
            },
        )
        val back = packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) ?: return
        val count = recording.labels.size
        val text = "Tocá los botones como siempre. Cuando termines, tocá acá para volver a La Vara."
        val notification = NotificationCompat.Builder(this, RECORD_CHANNEL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle("Grabando en ${recording.appLabel}: $count ${if (count == 1) "toque" else "toques"}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(PendingIntent.getActivity(this, RECORD_NOTIFICATION, back, PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setTimeoutAfter(RECORD_MILLIS)
            .build()
        try {
            manager.notify(RECORD_NOTIFICATION, notification)
        } catch (_: SecurityException) {
            // Sin permiso de notificaciones: se vuelve a La Vara con el botón de apps recientes.
        }
    }

    private fun setEventTypes(types: Int) {
        val info = serviceInfo ?: return
        info.eventTypes = types
        serviceInfo = info
    }

    /**
     * Todo lo que se puede elegir en la pantalla, con su nombre y su posición:
     * - cada cosa tocable y visible, con su texto, su descripción o el texto que tiene adentro;
     * - cada texto visible que queda encima de algo tocable (aunque no esté "adentro" del botón), porque
     *   muchas apps dibujan el texto aparte y así el usuario ve en la lista lo mismo que en la pantalla.
     */
    private fun candidates(root: AccessibilityNodeInfo): List<ButtonList.Candidate> {
        val parts = visibleParts(root)
        val found = parts.clickables.map { (node, box) ->
            ButtonList.Candidate(node.text, node.contentDescription, innerText(node), box.top, box.left)
        }.toMutableList()
        val boxes = parts.clickables.map { it.second }
        for ((node, bounds) in parts.texts) {
            if (ButtonList.smallestCovering(boxes, bounds.centerX(), bounds.centerY()) != null) {
                found += ButtonList.Candidate(node.text, node.contentDescription, null, bounds.top, bounds.left)
            }
        }
        return found
    }

    /** Lo visible de la pantalla: las cosas tocables con su rectángulo, y los textos que no son tocables. */
    private class Parts(
        val clickables: List<Pair<AccessibilityNodeInfo, ButtonList.Box>>,
        val texts: List<Pair<AccessibilityNodeInfo, Rect>>,
    )

    private fun visibleParts(root: AccessibilityNodeInfo): Parts {
        val clickables = mutableListOf<Pair<AccessibilityNodeInfo, ButtonList.Box>>()
        val texts = mutableListOf<Pair<AccessibilityNodeInfo, Rect>>()
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            if (node.isVisibleToUser) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (node.isClickable) {
                    clickables += node to ButtonList.Box(bounds.left, bounds.top, bounds.right, bounds.bottom)
                } else if (!node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()) {
                    texts += node to bounds
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return Parts(clickables, texts)
    }

    /** El primer texto o descripción de adentro de un botón que no tiene nombre propio (hasta 5 niveles). */
    private fun innerText(node: AccessibilityNodeInfo, depth: Int = 0): CharSequence? {
        if (depth >= 5) return null
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val own = listOf(child.text, child.contentDescription)
                .firstOrNull { !it.isNullOrBlank() && !ButtonList.looksLikeCode(it.toString().trim()) }
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
        handler.removeCallbacks(recordTimeout)
        instance = null
        capturePackage = null
        recordPackage = null
        _recording.value = _recording.value?.copy(active = false)
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
                if (node != null) return if (click(root, node)) null else "Android no dejó tocar \"$button\"."
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

    /**
     * Toca el botón o, si el texto está dentro de algo tocable, ese contenedor. Si el texto está dibujado
     * encima del botón sin estar adentro, toca lo tocable más chico que hay debajo del texto.
     */
    private fun click(root: AccessibilityNodeInfo, node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        if (target != null) return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val under = clickablesUnder(root, bounds.centerX(), bounds.centerY())
        val index = ButtonList.smallestCovering(under.map { it.second }, bounds.centerX(), bounds.centerY()) ?: return false
        return under[index].first.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /** Las cosas tocables y visibles que contienen el punto, con su rectángulo. */
    private fun clickablesUnder(root: AccessibilityNodeInfo, x: Int, y: Int): List<Pair<AccessibilityNodeInfo, ButtonList.Box>> {
        val found = mutableListOf<Pair<AccessibilityNodeInfo, ButtonList.Box>>()
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        val bounds = Rect()
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            if (node.isVisibleToUser && node.isClickable) {
                node.getBoundsInScreen(bounds)
                val box = ButtonList.Box(bounds.left, bounds.top, bounds.right, bounds.bottom)
                if ((x to y) in box) found += node to box
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return found
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
        private const val RECORD_MILLIS = 5 * 60_000L
        private const val RECORD_CHANNEL = "grabar_toques"
        private const val RECORD_NOTIFICATION = 3002

        private var instance: WeakReference<TapService>? = null

        @Volatile private var capturePackage: String? = null
        @Volatile private var captureUntil = 0L
        @Volatile private var recordPackage: String? = null
        @Volatile private var recordUntil = 0L

        private val _recording = MutableStateFlow<TapRecording?>(null)

        /** Lo que se está grabando o se grabó con "Grabar toques"; null si no hay nada. Solo vive en memoria. */
        val recording: StateFlow<TapRecording?> = _recording

        /** Termina la grabación (el usuario volvió a La Vara); lo grabado queda para crear la automatización. */
        fun finishRecording() {
            current()?.stopRecording() ?: run {
                recordPackage = null
                _recording.value = _recording.value?.copy(active = false)
            }
        }

        /** Cambia lo grabado (por ejemplo, el usuario sacó un toque de más). */
        fun updateRecording(recording: TapRecording) {
            _recording.value = recording
        }

        /** Termina y borra lo grabado (el usuario ya creó la automatización o la descartó). */
        fun discardRecording() {
            finishRecording()
            _recording.value = null
        }

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
