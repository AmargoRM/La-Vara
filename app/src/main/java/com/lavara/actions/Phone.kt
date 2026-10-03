package com.lavara.actions

import java.net.URLEncoder

/** Números de teléfono tal como los escribe o elige el usuario ("+506 8888-7777"). Lógica pura. */
object Phone {

    /** Para marcar o enviar SMS: solo dígitos y un "+" inicial si lo tenía. */
    fun dialable(input: String): String {
        val digits = input.filter { it.isDigit() }
        return if (input.trim().startsWith("+")) "+$digits" else digits
    }

    /** Para WhatsApp: el número internacional sin "+" ni "00" inicial ("50688887777"). */
    fun international(input: String): String {
        val digits = input.filter { it.isDigit() }
        return if (!input.trim().startsWith("+") && digits.startsWith("00")) digits.drop(2) else digits
    }

    /** Si parece llevar código de país. Un número de Costa Rica sin código tiene 8 dígitos. */
    fun hasCountryCode(input: String): Boolean = international(input).length >= 9

    /** Cómo se nombra en pantalla y en los registros: el contacto ("Mamá"), o los últimos 4 dígitos ("…7777"). */
    fun label(phone: String, contactName: String): String {
        if (contactName.isNotBlank()) return contactName.trim()
        val digits = phone.filter { it.isDigit() }
        return if (digits.length <= 4) "(sin número)" else "…${digits.takeLast(4)}"
    }

    /** Texto listo para ir dentro de un enlace (espacios como %20, no como +). */
    fun encode(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
}

/** Enlace de WhatsApp que abre el chat con el texto escrito. */
fun Action.WhatsAppMessage.link(): String =
    "https://api.whatsapp.com/send?phone=${Phone.international(phone)}&text=${Phone.encode(text)}"

/** A quién va: el contacto o los últimos dígitos. */
val Action.WhatsAppMessage.recipient: String get() = Phone.label(phone, contactName)
val Action.DialNumber.recipient: String get() = Phone.label(phone, contactName)
val Action.SendSms.recipient: String get() = Phone.label(phone, contactName)

private val COORDINATES = Regex("""^\s*(-?\d{1,3}(?:\.\d+)?)\s*,\s*(-?\d{1,3}(?:\.\d+)?)\s*$""")

/** Enlace que abre la navegación hacia el destino en la app elegida. */
fun Action.Navigate.link(): String {
    val coords = COORDINATES.find(destination)?.let { "${it.groupValues[1]},${it.groupValues[2]}" }
    return when (app) {
        NavigationApp.WAZE ->
            if (coords != null) "https://waze.com/ul?ll=${Phone.encode(coords)}&navigate=yes"
            else "https://waze.com/ul?q=${Phone.encode(destination.trim())}&navigate=yes"
        NavigationApp.GOOGLE_MAPS -> "google.navigation:q=${Phone.encode(coords ?: destination.trim())}"
    }
}
