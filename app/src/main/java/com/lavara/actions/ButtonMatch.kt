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
 * adivinar su nombre. Lógica pura: la parte Android le pasa lo que lee de cada cosa tocable.
 */
object ButtonList {

    /** Una cosa tocable de la pantalla: su texto, su descripción, el texto de adentro y su nombre interno. */
    data class Candidate(
        val text: CharSequence?,
        val description: CharSequence?,
        val innerText: CharSequence?,
        val viewId: String?,
        val top: Int,
        val left: Int,
    )

    /** Nombres más largos que esto no son botones (suelen ser mensajes o textos de la app) y no se muestran. */
    const val MAX_LABEL = 40
    const val MAX_BUTTONS = 60

    /**
     * El nombre con que [ButtonMatch] después lo encuentra: el texto o la descripción propia, o si no tiene, el
     * texto de adentro, o el nombre interno ("send" de "com.whatsapp:id/send"). null si no tiene nombre útil.
     */
    fun label(c: Candidate): String? {
        val visible = listOf(c.text, c.description, c.innerText)
            .map { it?.toString()?.replace(Regex("\\s+"), " ")?.trim().orEmpty() }
            .firstOrNull { it.isNotEmpty() }
        val name = visible ?: c.viewId?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        return name?.takeIf { it.length <= MAX_LABEL }
    }

    /** Los nombres, de arriba hacia abajo y de izquierda a derecha, sin repetir. */
    fun from(candidates: List<Candidate>): List<String> =
        candidates.sortedWith(compareBy({ it.top }, { it.left }))
            .mapNotNull { label(it) }
            .distinctBy { it.lowercase() }
            .take(MAX_BUTTONS)
}
