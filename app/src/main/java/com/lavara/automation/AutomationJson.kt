package com.lavara.automation

import kotlinx.serialization.json.Json

/**
 * Configuración única del JSON de La Vara: la usan Room (columna definition), importar/exportar
 * y docs/FORMATO_JSON.md. El campo "type" indica el tipo de trigger, condición o acción.
 */
object AutomationJson {
    val json = Json {
        classDiscriminator = "type"
        // Un archivo hecho por una versión más nueva puede traer campos que esta no conoce.
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(automation: Automation): String = json.encodeToString(Automation.serializer(), automation)

    fun decode(text: String): Automation = json.decodeFromString(Automation.serializer(), text)
}
