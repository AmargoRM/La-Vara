package com.lavara.system.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import com.lavara.LaVaraApp
import com.lavara.actions.ButtonMatch
import kotlinx.coroutines.delay
import java.lang.ref.WeakReference

/**
 * Servicio de Accesibilidad de La Vara. Solo hace una cosa: cuando una automatización lo pide, toca un
 * botón dentro de una app de la lista [AllowedApps]. No escucha nada por su cuenta, no guarda lo que hay
 * en pantalla y nunca escribe en los registros el texto de otras apps.
 */
class TapService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = WeakReference(this)
        val apps = AllowedApps(this).get()
        applyAllowed(apps)
        log("Accesibilidad encendida. Apps permitidas: ${apps.size}.")
    }

    /** No se usa: La Vara no reacciona a lo que pasa en pantalla, solo toca cuando una acción lo pide. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
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
        return if (appSeen) "no encontré el botón \"$button\" en la pantalla."
        else "la app no apareció en pantalla (¿teléfono bloqueado o pantalla apagada?)."
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

        private var instance: WeakReference<TapService>? = null

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
