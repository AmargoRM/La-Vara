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

        val host: String get() = runCatching { java.net.URI(url).host }.getOrNull() ?: "enlace"
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
