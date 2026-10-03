package com.lavara.automation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Respaldo: todas las automatizaciones en un archivo JSON, con el mismo formato que se guarda en Room
 * (docs/FORMATO_JSON.md). Lógica pura: se prueba sin Android.
 */
object Backup {
    const val FORMAT = "la-vara-respaldo"
    const val VERSION = 1

    @Serializable
    private data class File(
        val format: String = FORMAT,
        val version: Int = VERSION,
        /** Milisegundos desde 1970 (UTC) en que se hizo el respaldo. */
        val exportedAt: Long = 0,
        val automations: List<Automation> = emptyList(),
    )

    fun encode(automations: List<Automation>, now: Long): String =
        AutomationJson.json.encodeToString(File.serializer(), File(exportedAt = now, automations = automations))

    /** Lo que se pudo leer de un archivo: las automatizaciones válidas y cuántas no se entendieron. */
    data class Read(val automations: List<Automation>, val unreadable: Int)

    /**
     * Lee un respaldo. Acepta también una sola automatización o una lista suelta. Una automatización
     * dañada no impide leer las demás. Si el archivo entero no se entiende, lanza IllegalArgumentException.
     */
    fun decode(text: String): Read {
        val root = try {
            AutomationJson.json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("El archivo no es un respaldo de La Vara (no es JSON).")
        }
        val items = when {
            root is JsonArray -> root
            root is JsonObject && "automations" in root -> root["automations"]!!.jsonArray
            root is JsonObject && "trigger" in root -> JsonArray(listOf(root))
            else -> throw IllegalArgumentException("El archivo no es un respaldo de La Vara.")
        }
        var unreadable = 0
        val automations = items.mapNotNull { item ->
            runCatching { AutomationJson.json.decodeFromJsonElement(Automation.serializer(), item.jsonObject) }
                .onFailure { unreadable++ }
                .getOrNull()
        }
        return Read(automations, unreadable)
    }

    /** Qué hacer al importar: las que se agregan y cuántas ya estaban iguales. */
    data class Plan(val toSave: List<Automation>, val alreadyThere: Int, val copies: Int)

    /**
     * Nunca borra ni pisa nada: si ya existe una con el mismo id e igual contenido, se salta; si existe
     * pero es distinta, se agrega como copia desactivada (para que no se dispare dos veces), con id nuevo
     * ([newId]) y "(importada)" en el nombre.
     */
    fun plan(existing: List<Automation>, imported: List<Automation>, now: Long, newId: () -> String): Plan {
        val byId = existing.associateBy { it.id }.toMutableMap()
        val toSave = mutableListOf<Automation>()
        var same = 0
        var copies = 0
        for (automation in imported) {
            val current = byId[automation.id]
            when {
                current == null -> toSave += automation.copy(updatedAt = now).also { byId[it.id] = it }
                current.sameContent(automation) -> same++
                else -> {
                    copies++
                    toSave += automation.copy(id = newId(), name = "${automation.name} (importada)", enabled = false, createdAt = now, updatedAt = now)
                        .also { byId[it.id] = it }
                }
            }
        }
        return Plan(toSave, same, copies)
    }

    private fun Automation.sameContent(other: Automation) =
        name == other.name && trigger == other.trigger && conditions == other.conditions && actions == other.actions &&
            onError == other.onError && cooldownSeconds == other.cooldownSeconds && priority == other.priority
}
