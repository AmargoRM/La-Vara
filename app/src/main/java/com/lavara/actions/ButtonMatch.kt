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
