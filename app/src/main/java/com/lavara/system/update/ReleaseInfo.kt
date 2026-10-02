package com.lavara.system.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.LocalDate

/** Versión publicada en el Release "ultima" de GitHub. */
data class ReleaseInfo(
    val versionCode: Int,
    val versionName: String,
    /** URL de la API para bajar el APK (requiere el token). */
    val apkApiUrl: String,
    val apkSizeBytes: Long,
)

/**
 * Lógica pura (sin Android) para interpretar la respuesta de la API de GitHub.
 * Así se prueba con tests JVM.
 */
object ReleaseParser {
    const val APK_NAME = "La-Vara.apk"

    private val json = Json { ignoreUnknownKeys = true }

    // El workflow escribe esta marca invisible en las notas del Release.
    private val marker = Regex("""versionCode=(\d+)""")

    // Respaldo: el título del Release es "La Vara 0.1.N", con N = versionCode.
    private val nameRegex = Regex("""(\d+\.\d+\.(\d+))""")

    /** Devuelve null si la respuesta no trae un APK o un número de versión legible. */
    fun parse(body: String): ReleaseInfo? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val name = root.string("name").orEmpty()
        val notes = root.string("body").orEmpty()

        val nameMatch = nameRegex.find(name)
        val versionCode = marker.find(notes)?.groupValues?.get(1)?.toIntOrNull()
            ?: nameMatch?.groupValues?.get(2)?.toIntOrNull()
            ?: return null
        val versionName = nameMatch?.groupValues?.get(1) ?: "0.1.$versionCode"

        val asset = runCatching { root["assets"]?.jsonArray }.getOrNull()
            ?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            ?.firstOrNull { it.string("name") == APK_NAME }
            ?: return null
        val url = asset.string("url") ?: return null
        val size = asset["size"]?.jsonPrimitive?.longOrNull ?: 0L

        return ReleaseInfo(versionCode, versionName, url, size)
    }

    fun isNewer(release: ReleaseInfo, installedVersionCode: Int): Boolean =
        release.versionCode > installedVersionCode

    private fun JsonObject.string(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
}

/** Mensajes en español para cada error de GitHub. Nunca incluyen el token. */
object UpdateErrors {
    fun forHttpCode(code: Int): String = when (code) {
        401 -> "GitHub rechazó el token: está vencido, borrado o mal copiado. Creá uno nuevo y pegalo de nuevo."
        403 -> "El token no tiene permiso para leer La Vara, o GitHub limitó las consultas. Revisá que el token tenga acceso de lectura a Contents del repositorio La-Vara."
        404 -> "GitHub no encontró el Release \"ultima\". Puede que el token no tenga acceso al repositorio La-Vara, o que todavía no haya ninguna versión publicada."
        in 500..599 -> "GitHub tiene problemas en este momento (error $code). Se vuelve a intentar más tarde."
        else -> "GitHub respondió con un error inesperado ($code)."
    }

    const val NO_TOKEN = "Falta el token de GitHub. Pegalo en la sección Actualizaciones."
    const val NO_NETWORK = "No hay conexión a internet o GitHub no respondió."
    const val BAD_RESPONSE = "La respuesta de GitHub no trae La-Vara.apk o no indica el número de versión."
}

/** Vencimiento del token, leído del encabezado github-authentication-token-expiration. */
object TokenExpiry {
    const val WARNING_DAYS = 14L

    /** Acepta "2027-10-02 12:00:00 UTC" o "2027-10-02 12:00:00 -0600": solo importa la fecha. */
    fun parse(header: String?): LocalDate? =
        header?.trim()?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun daysLeft(expiry: LocalDate, today: LocalDate): Long = today.until(expiry, java.time.temporal.ChronoUnit.DAYS)

    fun shouldWarn(expiry: LocalDate, today: LocalDate): Boolean = daysLeft(expiry, today) <= WARNING_DAYS
}
