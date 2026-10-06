package com.lavara.actions

/**
 * Elige la notificación y el botón para [Action.ReplyToNotification] y [Action.TapNotificationButton].
 * Lógica pura: la parte Android le pasa las notificaciones activas de la app elegida.
 */
object NotificationPick {

    /** Un botón de una notificación: su texto y si tiene campo para escribir (el de "Responder"). */
    data class Button(val title: String, val canType: Boolean)

    data class Shown(val title: String, val postTime: Long, val buttons: List<Button>)

    /** (notificación, botón) elegidos, por su posición en las listas. */
    data class Choice(val notification: Int, val button: Int)

    /** La notificación más nueva cuyo título contiene [from] (vacío = cualquiera) y que tiene botón de responder. */
    fun reply(shown: List<Shown>, from: String): Choice? =
        newestFirst(shown, from).firstNotNullOfOrNull { i ->
            shown[i].buttons.indexOfFirst { it.canType }.takeIf { it >= 0 }?.let { Choice(i, it) }
        }

    /** La notificación más nueva cuyo título contiene [from] y que tiene un botón que se llama como [button]. */
    fun button(shown: List<Shown>, from: String, button: String): Choice? =
        newestFirst(shown, from).firstNotNullOfOrNull { i ->
            val scores = shown[i].buttons.map { ButtonMatch.score(button, it.title, null, null) }
            val best = scores.withIndex().maxByOrNull { it.value }
            if (best != null && best.value > 0) Choice(i, best.index) else null
        }

    private fun newestFirst(shown: List<Shown>, from: String): List<Int> {
        val wanted = from.trim().lowercase()
        return shown.indices
            .filter { wanted.isEmpty() || shown[it].title.lowercase().contains(wanted) }
            .sortedByDescending { shown[it].postTime }
    }
}
