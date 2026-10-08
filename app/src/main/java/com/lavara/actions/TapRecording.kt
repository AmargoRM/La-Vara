package com.lavara.actions

import com.lavara.automation.AutomationDraft
import com.lavara.triggers.Trigger
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * "Mirame y repetí": lo que se grabó mientras el usuario tocaba otra app, y cómo se convierte en una
 * automatización. Lógica pura: la parte Android ([com.lavara.system.accessibility.TapService]) le pasa cada toque.
 *
 * @param steps los toques grabados, en orden.
 * @param skipped los toques que no se grabaron (en el teclado, o fuera de la app que se graba).
 * @param startedAt cuándo empezó la grabación (milisegundos desde que arrancó el teléfono).
 */
data class TapRecording(
    val packageName: String,
    val appLabel: String,
    val steps: List<Step> = emptyList(),
    val skipped: Int = 0,
    val active: Boolean = true,
    val startedAt: Long = 0L,
    /** Cuándo terminó el último toque, para saber cuánto esperó el usuario antes del siguiente. */
    val lastTapAt: Long = startedAt,
) {
    /**
     * Un toque grabado. Si el lugar tocado tiene un nombre visible que no se repite en la pantalla ([label]),
     * se repite buscando ese nombre (sirve aunque el botón cambie de lugar). Si no, se repite tocando la
     * misma posición ([x], [y], de 0 a 1), o deslizando hasta [toX], [toY].
     */
    data class Step(
        val label: String? = null,
        val x: Double,
        val y: Double,
        val toX: Double? = null,
        val toY: Double? = null,
        val durationMillis: Long = 50,
        /** Cuánto esperó el usuario desde el toque anterior (o desde que se abrió la app). */
        val pauseMillis: Long = 0,
    )

    /** Agrega un toque que terminó en [at]. Se guarda cuánto esperó el usuario antes de hacerlo. */
    fun add(step: Step, at: Long): TapRecording {
        if (!active || steps.size >= MAX_STEPS) return this
        val began = at - step.durationMillis
        val pause = (began - lastTapAt).coerceIn(MIN_PAUSE_MILLIS, MAX_PAUSE_MILLIS)
        return copy(steps = steps + step.copy(pauseMillis = pause), lastTapAt = at)
    }

    /** Un toque que no se grabó (en el teclado o fuera de la app). */
    fun skip(at: Long): TapRecording = if (active) copy(skipped = skipped + 1, lastTapAt = at) else this

    /** Saca el toque número [index] (si el usuario tocó algo de más). */
    fun remove(index: Int): TapRecording =
        if (index in steps.indices) copy(steps = steps.filterIndexed { i, _ -> i != index }) else this

    /**
     * Las acciones que repiten lo grabado: abrir la app y repetir cada toque. Los que tienen nombre se buscan
     * por nombre (esperando hasta [AutomationDraft.MAX_DELAY_SECONDS] segundos a que aparezcan); si el mismo
     * nombre se toca dos veces seguidas, entre los dos se espera un segundo para que la pantalla cambie. Los
     * demás se tocan en la misma posición, después de esperar lo mismo que esperó el usuario.
     */
    fun toActions(): List<Action> = buildList {
        add(Action.OpenApp(packageName))
        val wait = AutomationDraft.MAX_DELAY_SECONDS.toInt()
        steps.forEachIndexed { i, step ->
            if (step.label != null) {
                if (i > 0 && steps[i - 1].label.equals(step.label, ignoreCase = true)) add(Action.Delay(1))
                add(Action.TapInApp(packageName = packageName, button = step.label, waitSeconds = wait))
            } else {
                add(
                    Action.TouchScreen(
                        packageName = packageName,
                        x = step.x.round(), y = step.y.round(),
                        toX = step.toX?.round(), toY = step.toY?.round(),
                        durationMillis = step.durationMillis.coerceIn(1, MAX_PAUSE_MILLIS),
                        pauseMillis = step.pauseMillis,
                        waitSeconds = wait,
                    ),
                )
            }
        }
    }

    /** Borrador para el editor: se ejecuta "a mano" (botón Ejecutar o widget); el usuario puede cambiarlo. */
    fun toDraft(): AutomationDraft = AutomationDraft(
        name = "Toques en $appLabel",
        trigger = Trigger.Manual,
        actions = toActions(),
    )

    companion object {
        /** Una grabación más larga que esto seguramente es un error (se olvidó de terminarla). */
        const val MAX_STEPS = 50
        const val MIN_PAUSE_MILLIS = 300L
        const val MAX_PAUSE_MILLIS = AutomationDraft.MAX_DELAY_SECONDS * 1000

        /** Si el dedo se movió menos que esto (parte de la pantalla), fue un toque y no un deslizamiento. */
        const val SWIPE_MIN = 0.03

        private fun Double.round(): Double = (this * 1000).roundToInt() / 1000.0
    }
}

/** Cómo se muestra un toque grabado, en palabras: "Tocar \"Casa\"", "Tocar en un punto (45 %, 30 %)", "Deslizar hacia arriba". */
fun TapRecording.Step.text(): String = when {
    label != null -> "Tocar \"$label\""
    toX != null && toY != null -> swipeText(x, y, toX, toY)
    else -> pointText(x, y, durationMillis)
}

/** El texto de [Action.TouchScreen] para el editor y la lista de automatizaciones. */
fun touchText(action: Action.TouchScreen): String {
    val toX = action.toX
    val toY = action.toY
    return if (toX != null && toY != null) swipeText(action.x, action.y, toX, toY) else pointText(action.x, action.y, action.durationMillis)
}

private fun pointText(x: Double, y: Double, durationMillis: Long): String =
    (if (durationMillis >= 500) "Mantener tocado" else "Tocar") +
        " en un punto (${(x * 100).roundToInt()} % desde la izquierda, ${(y * 100).roundToInt()} % desde arriba)"

private fun swipeText(x: Double, y: Double, toX: Double, toY: Double): String {
    val dx = toX - x
    val dy = toY - y
    return "Deslizar hacia " + if (abs(dy) >= abs(dx)) (if (dy < 0) "arriba" else "abajo") else (if (dx < 0) "la izquierda" else "la derecha")
}
