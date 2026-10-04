package com.lavara.actions

import com.lavara.conditions.Condition
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

    /** Enciende ([on] = true) o apaga la linterna. No pide permisos. */
    @Serializable
    @SerialName("flashlight")
    data class Flashlight(
        val on: Boolean = true,
    ) : Action

    /** Pone el volumen de [stream] en [percent] % (0 a 100). */
    @Serializable
    @SerialName("set_volume")
    data class SetVolume(
        val stream: VolumeStream = VolumeStream.MEDIA,
        val percent: Int = 50,
    ) : Action {
        init {
            require(percent in 0..100) { "percent debe estar entre 0 y 100, no $percent" }
        }
    }

    /** Cambia el modo de sonido del teléfono. [RingerMode.SILENT] necesita "Acceso a No molestar". */
    @Serializable
    @SerialName("set_ringer_mode")
    data class SetRingerMode(
        val mode: RingerMode = RingerMode.VIBRATE,
    ) : Action

    /** Enciende No molestar en el modo [mode], o lo apaga con [DndMode.OFF]. Necesita "Acceso a No molestar". */
    @Serializable
    @SerialName("do_not_disturb")
    data class DoNotDisturb(
        val mode: DndMode = DndMode.PRIORITY,
    ) : Action

    /**
     * Cambia el brillo de la pantalla: automático si [auto] es true; si no, fijo en [percent] % (1 a 100).
     * Necesita el permiso "Modificar ajustes del sistema".
     */
    @Serializable
    @SerialName("set_brightness")
    data class SetBrightness(
        val percent: Int = 50,
        val auto: Boolean = false,
    ) : Action {
        init {
            require(percent in 1..100) { "percent debe estar entre 1 y 100, no $percent" }
        }
    }

    /**
     * Abre el interruptor de una función que Android no deja cambiar a las apps (Wi-Fi, datos, Bluetooth…).
     * El usuario lo toca; La Vara no puede encenderla ni apagarla sola (ver docs/LIMITES_ANDROID.md).
     */
    @Serializable
    @SerialName("open_system_panel")
    data class OpenSystemPanel(
        val panel: SystemPanel = SystemPanel.WIFI,
    ) : Action

    /**
     * Abre WhatsApp en el chat de [phone] con [text] ya escrito. El usuario toca Enviar: WhatsApp no deja
     * enviar sin tocar. [phone] lleva el código de país (506 para Costa Rica). [text] acepta variables.
     * [contactName] es solo para mostrar ("Mamá"); en los registros va el nombre o los últimos dígitos.
     */
    @Serializable
    @SerialName("whatsapp_message")
    data class WhatsAppMessage(
        val phone: String = "",
        val text: String = "",
        val contactName: String = "",
    ) : Action

    /** Abre el marcador del teléfono con [phone] escrito. El usuario toca Llamar. */
    @Serializable
    @SerialName("dial_number")
    data class DialNumber(
        val phone: String = "",
        val contactName: String = "",
    ) : Action

    /** Abre [app] navegando hacia [destination]: una dirección, un lugar o coordenadas "9.93,-84.08". */
    @Serializable
    @SerialName("navigate")
    data class Navigate(
        val destination: String = "",
        val app: NavigationApp = NavigationApp.WAZE,
    ) : Action

    /**
     * Envía un SMS a [phone] sin tocar nada. Necesita el permiso de SMS. [text] acepta variables.
     * El texto del mensaje nunca se escribe en los registros.
     */
    @Serializable
    @SerialName("send_sms")
    data class SendSms(
        val phone: String = "",
        val text: String = "",
        val contactName: String = "",
    ) : Action

    /**
     * Toca el botón [button] (su texto o su nombre, ej.: "Enviar") dentro de la app [packageName], usando
     * el permiso de Accesibilidad. Solo funciona si esa app está en la lista de apps permitidas que el usuario
     * eligió en La Vara. Espera hasta [waitSeconds] segundos a que la app y el botón aparezcan.
     */
    @Serializable
    @SerialName("tap_in_app")
    data class TapInApp(
        val packageName: String = "",
        val button: String = "",
        val waitSeconds: Int = 5,
    ) : Action

    /** Hace vibrar el teléfono [millis] milisegundos. No pide permisos especiales. */
    @Serializable
    @SerialName("vibrate")
    data class Vibrate(
        val millis: Long = 500,
    ) : Action {
        init {
            require(millis in 1..10_000) { "millis debe estar entre 1 y 10000, no $millis" }
        }
    }

    /** Copia [text] al portapapeles. [text] acepta variables. El texto nunca se escribe en los registros. */
    @Serializable
    @SerialName("copy_to_clipboard")
    data class CopyToClipboard(
        val text: String = "",
    ) : Action

    /** Abre el menú Compartir de Android con [text], para elegir a qué app mandarlo. [text] acepta variables. */
    @Serializable
    @SerialName("share_text")
    data class ShareText(
        val text: String = "",
    ) : Action

    /**
     * Controla la música o el video. Sin [packageName], como los botones de los audífonos: lo recibe la app que
     * esté sonando o la última que sonó. Con [packageName], le habla directo a esa app de música, aunque esté
     * cerrada (vacío = como antes).
     */
    @Serializable
    @SerialName("media_control")
    data class MediaControl(
        val command: MediaCommand = MediaCommand.PLAY_PAUSE,
        val packageName: String = "",
    ) : Action

    /**
     * Si se cumplen [conditions] (todas si [matchAll], alguna si no), hace [then]; si no, hace [otherwise].
     * Lista de condiciones vacía = se cumple. Dentro de un "si", las esperas son de 10 segundos como máximo.
     */
    @Serializable
    @SerialName("if")
    data class IfElse(
        val conditions: List<Condition> = emptyList(),
        val matchAll: Boolean = true,
        val then: List<Action> = emptyList(),
        val otherwise: List<Action> = emptyList(),
    ) : Action
}

/** Botón de música de [Action.MediaControl]. */
@Serializable
enum class MediaCommand(val label: String) {
    @SerialName("play_pause") PLAY_PAUSE("reproducir o pausar"),
    @SerialName("play") PLAY("reproducir"),
    @SerialName("pause") PAUSE("pausar"),
    @SerialName("next") NEXT("siguiente"),
    @SerialName("previous") PREVIOUS("anterior"),
}

/** Las acciones de adentro de un "si" (las dos ramas); para las demás, ninguna. */
val Action.nested: List<Action>
    get() = if (this is Action.IfElse) then + otherwise else emptyList()

/** App con la que navega [Action.Navigate]. */
@Serializable
enum class NavigationApp(val label: String) {
    @SerialName("waze") WAZE("Waze"),
    @SerialName("google_maps") GOOGLE_MAPS("Google Maps"),
}

/** Si la acción abre otra app o pantalla: con La Vara cerrada necesita "Mostrar sobre otras apps". */
fun Action.opensScreen(): Boolean = when (this) {
    is Action.OpenApp, is Action.OpenUrl, is Action.OpenSystemPanel,
    is Action.WhatsAppMessage, is Action.DialNumber, is Action.Navigate, is Action.ShareText -> true
    is Action.IfElse -> nested.any { it.opensScreen() }
    else -> false
}

/** Si la acción necesita el teléfono desbloqueado: abre algo en pantalla o toca botones. */
fun Action.needsUnlock(): Boolean = opensScreen() || this is Action.TapInApp || nested.any { it.needsUnlock() }

/** Si la acción necesita el permiso "Acceso a No molestar" (No molestar o modo silencio). */
fun Action.needsDndAccess(): Boolean =
    this is Action.DoNotDisturb || (this is Action.SetRingerMode && mode == RingerMode.SILENT) || nested.any { it.needsDndAccess() }

/** La acción y, si es un "si", todas las de adentro (para revisar permisos). */
fun Action.flatten(): List<Action> = listOf(this) + nested.flatMap { it.flatten() }

/** Qué volumen cambia [Action.SetVolume]. */
@Serializable
enum class VolumeStream(val label: String) {
    @SerialName("media") MEDIA("multimedia"),
    @SerialName("ring") RING("timbre"),
    @SerialName("notification") NOTIFICATION("notificaciones"),
    @SerialName("alarm") ALARM("alarma"),
}

/** Modo de sonido del teléfono. */
@Serializable
enum class RingerMode(val label: String) {
    @SerialName("normal") NORMAL("sonido"),
    @SerialName("vibrate") VIBRATE("vibrar"),
    @SerialName("silent") SILENT("silencio"),
}

/** Modo de No molestar. */
@Serializable
enum class DndMode(val label: String) {
    /** Apagado: suena todo. */
    @SerialName("off") OFF("apagado"),

    /** Solo lo que está en "Prioridad" en los ajustes de No molestar. */
    @SerialName("priority") PRIORITY("solo prioridad"),

    /** Solo las alarmas. */
    @SerialName("alarms") ALARMS("solo alarmas"),

    /** Silencio total, ni alarmas. */
    @SerialName("silence") SILENCE("silencio total"),
}

/** Interruptores del sistema que La Vara solo puede abrir. */
@Serializable
enum class SystemPanel(val label: String) {
    @SerialName("wifi") WIFI("Wi-Fi"),
    @SerialName("mobile_data") MOBILE_DATA("datos móviles"),
    @SerialName("bluetooth") BLUETOOTH("Bluetooth"),
    @SerialName("location") LOCATION("ubicación"),
    @SerialName("nfc") NFC("NFC"),
    @SerialName("airplane_mode") AIRPLANE_MODE("modo avión"),
}

/** Resultado de una acción hecha por un [ActionExecutor]. */
sealed interface ActionResult {
    data object Success : ActionResult
    data class Failure(val message: String) : ActionResult
}

/**
 * Hace las acciones que tocan el teléfono (notificación, abrir app, volumen…). La implementación con
 * Android está en AndroidActionExecutor; el motor solo conoce esta interfaz. Delay y RunAutomation los resuelve el motor.
 */
fun interface ActionExecutor {
    suspend fun execute(action: Action): ActionResult
}

/** "30 s", "10 min", "2 h", "1 h 30 min": cuánto dura una espera, para mostrar. */
fun waitText(seconds: Long): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return listOfNotNull(
        if (h > 0) "$h h" else null,
        if (m > 0) "$m min" else null,
        if (s > 0 || seconds == 0L) "$s s" else null,
    ).joinToString(" ")
}
