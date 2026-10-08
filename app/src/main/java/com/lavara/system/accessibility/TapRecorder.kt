package com.lavara.system.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lavara.R
import com.lavara.actions.TapRecording
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs
import kotlin.math.hypot

/**
 * "Grabar toques": pone una capa invisible encima de toda la pantalla (una ventana de Accesibilidad, no necesita
 * "Mostrar sobre otras apps"). Cada vez que el usuario toca o desliza el dedo:
 * 1. si el dedo cayó en la app que se graba, anota el nombre visible de lo tocado o, si no tiene, la posición;
 *    si cayó en el teclado o en otra cosa, no anota nada (lo que se escribe nunca se graba);
 * 2. deja de tapar la pantalla un instante y le pasa el mismo toque a la app, así la app responde como siempre.
 * Un botón rojo arriba termina la grabación y vuelve a La Vara. Todo queda en memoria; en los registros solo
 * quedan cuántos toques se grabaron.
 */
@SuppressLint("ClickableViewAccessibility") // La capa no es un botón: solo mira el dedo y le pasa el toque a la app.
internal class TapRecorder(
    private val service: TapService,
    private val state: MutableStateFlow<TapRecording?>,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val timeout = Runnable { stop() }
    private val restore = Runnable { setTouchable(true) }

    private var target: String? = null
    private var layer: View? = null
    private var layerParams: WindowManager.LayoutParams? = null
    private var finishButton: TextView? = null
    private var finishParams: WindowManager.LayoutParams? = null

    /** El dedo mientras toca: los puntos por donde pasó, en píxeles de la pantalla. */
    private val points = ArrayList<Pair<Float, Float>>()
    private var downTime = 0L
    private var multiTouch = false

    fun start(packageName: String, appLabel: String): String? {
        stop()
        try {
            addLayer()
            addFinishButton()
        } catch (e: RuntimeException) {
            removeViews()
            return "Android no dejó poner la capa para grabar (${e.javaClass.simpleName})."
        }
        target = packageName
        val recording = TapRecording(packageName, appLabel, startedAt = SystemClock.uptimeMillis())
        state.value = recording
        service.setRetrieveWindows(true)
        handler.postDelayed(timeout, RECORD_MILLIS)
        update(recording)
        service.log("Grabación de toques empezada en $appLabel.")
        return null
    }

    fun stop() {
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(restore)
        removeViews()
        points.clear()
        if (target == null) return
        target = null
        service.setRetrieveWindows(false)
        NotificationManagerCompat.from(service).cancel(RECORD_NOTIFICATION)
        val recording = state.value ?: return
        state.value = recording.copy(active = false)
        service.log(
            "Grabación de toques terminada en ${recording.appLabel}: ${recording.steps.size} toques grabados, " +
                "${recording.skipped} sin grabar.",
        )
    }

    private fun overlayParams(width: Int, height: Int, flags: Int) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    private fun addLayer() {
        val view = View(service)
        view.setOnTouchListener { _, event -> onTouch(event) }
        val params = overlayParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        windowManager.addView(view, params)
        layer = view
        layerParams = params
    }

    /** El botón rojo "Terminar": se toca para terminar y se arrastra hacia arriba o abajo si tapa algo. */
    private fun addFinishButton() {
        val dp = { value: Float -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, service.resources.displayMetrics) }
        val button = TextView(service).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(16f).toInt(), dp(10f).toInt(), dp(16f).toInt(), dp(10f).toInt())
            background = GradientDrawable().apply {
                cornerRadius = dp(24f)
                setColor(Color.argb(230, 176, 0, 32))
            }
        }
        val params = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, 0).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(56f).toInt()
        }
        var startY = 0f
        var startParamY = 0
        var moved = false
        button.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    startParamY = params.y
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> if (abs(event.rawY - startY) > dp(8f)) {
                    moved = true
                    params.y = (startParamY + (event.rawY - startY)).toInt().coerceAtLeast(0)
                    runCatching { windowManager.updateViewLayout(button, params) }
                }
                MotionEvent.ACTION_UP -> if (!moved) finishAndReturn()
            }
            true
        }
        windowManager.addView(button, params)
        finishButton = button
        finishParams = params
    }

    private fun removeViews() {
        layer?.let { runCatching { windowManager.removeView(it) } }
        finishButton?.let { runCatching { windowManager.removeView(it) } }
        layer = null
        layerParams = null
        finishButton = null
        finishParams = null
    }

    /** Termina y vuelve a La Vara, donde aparece lo grabado. */
    private fun finishAndReturn() {
        stop()
        service.packageManager.getLaunchIntentForPackage(service.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            ?.let { runCatching { service.startActivity(it) } }
    }

    private fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                points.clear()
                multiTouch = false
                downTime = event.eventTime
                points += event.rawX to event.rawY
            }
            MotionEvent.ACTION_POINTER_DOWN -> multiTouch = true
            MotionEvent.ACTION_MOVE -> if (points.size < MAX_POINTS) points += event.rawX to event.rawY
            MotionEvent.ACTION_UP -> {
                points += event.rawX to event.rawY
                finishGesture((event.eventTime - downTime).coerceAtLeast(1))
            }
            MotionEvent.ACTION_CANCEL -> points.clear()
        }
        return true
    }

    /** El dedo se levantó: anotar el toque (si corresponde) y pasárselo a la app. */
    private fun finishGesture(durationMillis: Long) {
        val app = target ?: return
        val path = points.toList()
        points.clear()
        if (path.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        val (width, height) = service.screenSize()
        val (startX, startY) = path.first()
        val (endX, endY) = path.last()
        val swipe = hypot((endX - startX) / width.toDouble(), (endY - startY) / height.toDouble()) >= TapRecording.SWIPE_MIN
        val current = state.value
        if (current != null && current.active) {
            // Dos dedos (pellizcar) no se pueden repetir bien: se pasa el primero y no se graba.
            val root = if (multiTouch || app !in AllowedApps(service).get()) null else appUnder(app, startX.toInt(), startY.toInt())
            val next = if (root == null) {
                current.skip(now)
            } else {
                val step = TapRecording.Step(
                    label = if (swipe) null else service.labelAt(root, startX.toInt(), startY.toInt()),
                    x = startX / width.toDouble(),
                    y = startY / height.toDouble(),
                    toX = if (swipe) endX / width.toDouble() else null,
                    toY = if (swipe) endY / height.toDouble() else null,
                    durationMillis = durationMillis,
                )
                current.add(step, now)
            }
            state.value = next
            update(next)
        }
        passThrough(path, durationMillis)
    }

    /**
     * La pantalla de [app] que está debajo del punto, o null si debajo hay otra cosa (el teclado, la barra de
     * notificaciones, otra app). Las ventanas se revisan de arriba hacia abajo, salteando esta capa.
     */
    private fun appUnder(app: String, x: Int, y: Int): AccessibilityNodeInfo? {
        val bounds = Rect()
        for (window in service.windows.sortedByDescending { it.layer }) {
            if (window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
            window.getBoundsInScreen(bounds)
            if (!bounds.contains(x, y)) continue
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) return null
            return window.root?.takeIf { it.packageName?.toString() == app }
        }
        return null
    }

    /** Deja de tapar la pantalla, le pasa a la app el mismo gesto del dedo y vuelve a tapar al terminar. */
    private fun passThrough(path: List<Pair<Float, Float>>, durationMillis: Long) {
        setTouchable(false)
        val step = (path.size / MAX_GESTURE_POINTS) + 1
        val gesturePath = Path().apply {
            moveTo(path[0].first.coerceAtLeast(0f), path[0].second.coerceAtLeast(0f))
            for (i in step until path.size step step) lineTo(path[i].first.coerceAtLeast(0f), path[i].second.coerceAtLeast(0f))
            if ((path.size - 1) % step != 0) lineTo(path.last().first.coerceAtLeast(0f), path.last().second.coerceAtLeast(0f))
        }
        val duration = durationMillis.coerceIn(1, GestureDescription.getMaxGestureDuration())
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(gesturePath, 0, duration))
            .build()
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = setTouchable(true)
            override fun onCancelled(gestureDescription: GestureDescription?) = setTouchable(true)
        }
        // Un instante para que Android deje de mandarle el dedo a la capa antes de pasar el toque.
        handler.postDelayed({ if (!service.dispatchGesture(gesture, callback, handler)) setTouchable(true) }, PASS_DELAY_MILLIS)
        // Por si Android no avisa que terminó: la capa vuelve igual.
        handler.removeCallbacks(restore)
        handler.postDelayed(restore, PASS_DELAY_MILLIS + duration + 1_000)
    }

    private fun setTouchable(on: Boolean) {
        val view = layer ?: return
        val params = layerParams ?: return
        val flag = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val flags = if (on) params.flags and flag.inv() else params.flags or flag
        if (flags == params.flags) return
        params.flags = flags
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    /** Muestra cuántos toques van, en el botón rojo y en la notificación. */
    private fun update(recording: TapRecording) {
        val count = recording.steps.size
        val toques = "$count ${if (count == 1) "toque" else "toques"}"
        finishButton?.text = "⏺ Grabando ($toques) · Terminar"
        showNotification(recording.appLabel, toques)
    }

    // El permiso de notificaciones se revisa con areNotificationsEnabled; sin él, no se muestra y listo.
    @SuppressLint("MissingPermission")
    private fun showNotification(appLabel: String, toques: String) {
        val manager = NotificationManagerCompat.from(service)
        if (!manager.areNotificationsEnabled()) return
        service.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(RECORD_CHANNEL, "Grabar toques", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Aparece solo mientras grabás toques en otra app."
            },
        )
        val back = service.packageManager.getLaunchIntentForPackage(service.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) ?: return
        val text = "Usá $appLabel como siempre. Cuando termines, tocá el botón rojo \"Terminar\" o esta notificación."
        val notification = NotificationCompat.Builder(service, RECORD_CHANNEL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle("Grabando en $appLabel: $toques")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(PendingIntent.getActivity(service, RECORD_NOTIFICATION, back, PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setTimeoutAfter(RECORD_MILLIS)
            .build()
        try {
            manager.notify(RECORD_NOTIFICATION, notification)
        } catch (_: SecurityException) {
            // Sin permiso de notificaciones: queda el botón rojo.
        }
    }

    private companion object {
        const val RECORD_MILLIS = 5 * 60_000L
        const val RECORD_CHANNEL = "grabar_toques"
        const val RECORD_NOTIFICATION = 3002
        const val PASS_DELAY_MILLIS = 40L
        const val MAX_POINTS = 2_000
        const val MAX_GESTURE_POINTS = 60
    }
}
