package com.lavara.actions

import com.lavara.automation.AutomationDraft
import com.lavara.triggers.Trigger

/**
 * "Mirame y repetí": lo que se grabó mientras el usuario tocaba botones en otra app, y cómo se convierte en
 * una automatización. Lógica pura: la parte Android ([com.lavara.system.accessibility.TapService]) le pasa
 * el nombre visible de cada cosa tocada.
 *
 * @param labels los nombres de los botones tocados, en orden.
 * @param skipped los toques que no se pudieron grabar (botón sin nombre visible, o un campo de texto).
 */
data class TapRecording(
    val packageName: String,
    val appLabel: String,
    val labels: List<String> = emptyList(),
    val skipped: Int = 0,
    val active: Boolean = true,
    /** Cuándo se grabó el último toque (milisegundos desde que arrancó el teléfono), para ignorar los repetidos. */
    val lastTapAt: Long = 0L,
) {
    /**
     * Agrega un toque. Algunas apps avisan dos veces el mismo toque (el botón y lo que tiene adentro): si el
     * mismo nombre llega antes de [REPEAT_MILLIS], se cuenta una sola vez. Un nombre null es un toque sin nombre.
     */
    fun add(label: String?, at: Long): TapRecording = when {
        !active -> this
        labels.size >= MAX_STEPS -> this
        label == null -> copy(skipped = skipped + 1, lastTapAt = at)
        labels.lastOrNull().equals(label, ignoreCase = true) && at - lastTapAt < REPEAT_MILLIS -> copy(lastTapAt = at)
        else -> copy(labels = labels + label, lastTapAt = at)
    }

    /** Saca el toque número [index] (si el usuario tocó algo de más). */
    fun remove(index: Int): TapRecording =
        if (index in labels.indices) copy(labels = labels.filterIndexed { i, _ -> i != index }) else this

    /**
     * Las acciones que repiten lo grabado: abrir la app y tocar cada botón, esperando hasta
     * [AutomationDraft.MAX_DELAY_SECONDS] segundos a que aparezca. Si el mismo botón se toca dos veces seguidas
     * (ej. "Siguiente" en dos pantallas), entre los dos toques se espera un segundo, para no tocar dos veces
     * la misma pantalla antes de que cambie.
     */
    fun toActions(): List<Action> = buildList {
        add(Action.OpenApp(packageName))
        labels.forEachIndexed { i, label ->
            if (i > 0 && labels[i - 1].equals(label, ignoreCase = true)) add(Action.Delay(1))
            add(Action.TapInApp(packageName = packageName, button = label, waitSeconds = AutomationDraft.MAX_DELAY_SECONDS.toInt()))
        }
    }

    /** Borrador para el editor: se ejecuta "a mano" (botón Ejecutar o widget); el usuario puede cambiarlo. */
    fun toDraft(): AutomationDraft = AutomationDraft(
        name = "Toques en $appLabel",
        trigger = Trigger.Manual,
        actions = toActions(),
    )

    companion object {
        const val REPEAT_MILLIS = 600L

        /** Una grabación más larga que esto seguramente es un error (se olvidó de terminarla). */
        const val MAX_STEPS = 30
    }
}
