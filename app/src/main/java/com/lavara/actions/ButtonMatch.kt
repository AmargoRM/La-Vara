package com.lavara.actions

/** Cómo se reconoce el botón de [Action.TapInApp] en la pantalla de otra app. Lógica pura. */
object ButtonMatch {

    /**
     * 3 = el texto o el nombre del botón es igual a [wanted]; 2 = el nombre interno termina en [wanted]
     * (ej.: "com.whatsapp:id/send" con "send"); 1 = lo contiene; 0 = no es. Sin distinguir mayúsculas.
     */
    fun score(wanted: String, text: CharSequence?, description: CharSequence?, viewId: String?): Int {
        val target = wanted.trim().lowercase()
        if (target.isEmpty()) return 0
        val visible = listOfNotNull(text, description).map { it.toString().trim().lowercase() }
        return when {
            visible.any { it == target } -> 3
            viewId != null && viewId.substringAfterLast('/').lowercase() == target -> 2
            visible.any { it.contains(target) } -> 1
            else -> 0
        }
    }
}

/**
 * Arma la lista de botones que se ven en la pantalla de otra app, para elegir uno en el editor en vez de
 * adivinar su nombre. Solo muestra lo que el usuario ve escrito (o lo que Android lee en voz alta), nunca
 * los nombres internos de la app. Lógica pura: la parte Android le pasa lo que lee de la pantalla.
 */
object ButtonList {

    /**
     * Una cosa de la pantalla con nombre: su texto, su descripción y el texto de adentro (para botones que
     * no tienen texto propio), con su posición.
     */
    data class Candidate(
        val text: CharSequence?,
        val description: CharSequence?,
        val innerText: CharSequence?,
        val top: Int,
        val left: Int,
    )

    /** Un rectángulo de la pantalla, en píxeles. */
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        operator fun contains(point: Pair<Int, Int>): Boolean =
            point.first in left until right && point.second in top until bottom
        val area: Long get() = (right - left).toLong() * (bottom - top).toLong()
    }

    /** Nombres más largos que esto no son botones (suelen ser mensajes o textos de la app) y no se muestran. */
    const val MAX_LABEL = 40
    const val MAX_BUTTONS = 60

    private val SPACES = Regex("\\s+")
    private val CODE_CHARS = Regex("[A-Za-z0-9_.:/-]+")
    private val CAMEL = Regex("[a-z][A-Z]")

    /**
     * true si parece un nombre interno de programador y no algo que el usuario ve: sin espacios y con guion
     * bajo, punto, dos puntos, barra o mayúscula pegada en el medio ("btn_start_trip", "startTrip", "a.b:id/x").
     */
    fun looksLikeCode(name: String): Boolean =
        name.matches(CODE_CHARS) && (name.any { it in "_.:/" } || CAMEL.containsMatchIn(name))

    /**
     * El nombre que se muestra y con el que [ButtonMatch] después lo encuentra: el texto propio, la descripción
     * o el texto de adentro, el primero que parezca escrito para personas. null si no tiene nombre útil.
     */
    fun label(c: Candidate): String? =
        listOf(c.text, c.description, c.innerText)
            .map { it?.toString()?.replace(SPACES, " ")?.trim().orEmpty() }
            .firstOrNull { it.isNotEmpty() && it.length <= MAX_LABEL && !looksLikeCode(it) }

    /**
     * El rectángulo tocable más chico que contiene el punto, o null. Sirve para textos que se ven encima de
     * un botón pero no están adentro de él (algunas apps dibujan el texto aparte y el botón por debajo).
     */
    fun smallestCovering(boxes: List<Box>, x: Int, y: Int): Int? =
        boxes.indices.filter { (x to y) in boxes[it] }.minByOrNull { boxes[it].area }

    /** Los nombres, de arriba hacia abajo y de izquierda a derecha, sin repetir. */
    fun from(candidates: List<Candidate>): List<String> =
        candidates.sortedWith(compareBy({ it.top }, { it.left }))
            .mapNotNull { label(it) }
            .distinctBy { it.lowercase() }
            .take(MAX_BUTTONS)
}
