package com.lavara.actions

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Algo que hace una automatización. Cada acción es independiente: no sabe qué automatización la usa.
 * Cada tipo tiene un @SerialName fijo que nunca se cambia (ver docs/FORMATO_JSON.md).
 */
@Serializable
sealed interface Action {

    /** Muestra una notificación. [title] y [text] aceptan variables como %battery. */
    @Serializable
    @SerialName("show_notification")
    data class ShowNotification(
        val title: String,
        val text: String = "",
    ) : Action

    /** Abre la app con el nombre de paquete [packageName] (ej.: "com.whatsapp"). */
    @Serializable
    @SerialName("open_app")
    data class OpenApp(
        val packageName: String,
    ) : Action {
        init {
            require(packageName.isNotBlank()) { "packageName no puede estar vacío" }
        }
    }

    /**
     * Abre un enlace web ([url], con http:// o https://) en el navegador o en la app que lo maneje.
     * En los registros solo se escribe el sitio ([host]), nunca el enlace completo: puede llevar claves.
     */
    @Serializable
    @SerialName("open_url")
    data class OpenUrl(
        val url: String,
    ) : Action {
        init {
            require(url.startsWith("http://") || url.startsWith("https://")) { "url debe empezar con http:// o https://" }
        }

        /** El sitio ("waze.com"), o "enlace" si no se reconoce. Tolera espacios y caracteres raros en el resto. */
        val host: String get() = HOST.find(url)?.groupValues?.get(1)?.lowercase() ?: "enlace"

        companion object {
            private val HOST = Regex("^https?://([^/?#:@\\s]+)", RegexOption.IGNORE_CASE)
            private val SCHEME = Regex("https?://", RegexOption.IGNORE_CASE)

            /**
             * Convierte lo que el usuario escribió o pegó en un enlace usable, o null si no hay ninguno.
             * Acepta texto compartido ("Mirá esto https://…"), el https:// repetido al pegar sobre el que ya
             * estaba, mayúsculas en "HTTPS://" y sitios sin https:// ("waze.com/ul?q=x").
             */
            fun normalize(input: String): String? {
                val text = input.trim()
                val start = SCHEME.find(text)?.range?.first
                var candidate = if (start != null) {
                    text.substring(start).split(Regex("\\s")).first()
                } else {
                    text.split(Regex("\\s")).firstOrNull { '.' in it && '@' !in it }?.let { "https://$it" } ?: return null
                }
                // "https://https://sitio" al pegar el enlace completo detrás del https:// que traía el campo.
                while (true) {
                    val first = SCHEME.find(candidate) ?: break
                    val rest = candidate.substring(first.range.last + 1)
                    if (SCHEME.find(rest)?.range?.first == 0) candidate = rest else break
                }
                val scheme = SCHEME.find(candidate)!!
                candidate = scheme.value.lowercase() + candidate.substring(scheme.range.last + 1)
                candidate = candidate.trimEnd('.', ',', ';', ')', '"', '\'', '>')
                val host = HOST.find(candidate)?.groupValues?.get(1) ?: return null
                return if ('.' in host && !host.startsWith('.') && !host.endsWith('.')) candidate else null
            }
        }
    }

    /** Espera [seconds] segundos antes de la acción siguiente, sin trabar el teléfono. */
    @Serializable
    @SerialName("delay")
    data class Delay(
        val seconds: Long,
    ) : Action {
        init {
            require(seconds >= 0) { "seconds no puede ser negativo, no $seconds" }
        }
    }

    /** Ejecuta otra automatización por su id. Los ciclos (A → B → A) se detectan y se detienen. */
    @Serializable
    @SerialName("run_automation")
    data class RunAutomation(
        val automationId: String,
    ) : Action
}

/** Resultado de una acción hecha por un [ActionExecutor]. */
sealed interface ActionResult {
    data object Success : ActionResult
    data class Failure(val message: String) : ActionResult
}

/**
 * Hace las acciones que tocan el teléfono (notificación, abrir app). La implementación con Android
 * llega en S3; el motor solo conoce esta interfaz. Delay y RunAutomation los resuelve el motor.
 */
fun interface ActionExecutor {
    suspend fun execute(action: Action): ActionResult
}
