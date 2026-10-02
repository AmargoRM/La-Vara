package com.lavara.system.update

import android.content.Context
import java.time.LocalDate

/** Resultado de la última revisión, guardado para mostrarlo en pantalla. */
data class UpdateStatus(
    val lastCheckMillis: Long = 0L,
    val lastMessage: String = "Todavía no se revisó.",
    val available: ReleaseInfo? = null,
    val tokenExpiry: LocalDate? = null,
)

class UpdateStatusStore(context: Context) {
    private val prefs = context.getSharedPreferences("actualizaciones", Context.MODE_PRIVATE)

    fun load(): UpdateStatus {
        val code = prefs.getInt("disp_code", -1)
        val available = if (code > 0) {
            ReleaseInfo(
                versionCode = code,
                versionName = prefs.getString("disp_name", "") ?: "",
                apkApiUrl = prefs.getString("disp_url", "") ?: "",
                apkSizeBytes = prefs.getLong("disp_size", 0L),
            )
        } else {
            null
        }
        return UpdateStatus(
            lastCheckMillis = prefs.getLong("ultima_revision", 0L),
            lastMessage = prefs.getString("mensaje", null) ?: UpdateStatus().lastMessage,
            available = available,
            tokenExpiry = prefs.getString("token_vence", null)?.let { TokenExpiry.parse(it) },
        )
    }

    fun save(status: UpdateStatus) {
        prefs.edit().apply {
            putLong("ultima_revision", status.lastCheckMillis)
            putString("mensaje", status.lastMessage)
            val a = status.available
            if (a != null) {
                putInt("disp_code", a.versionCode)
                putString("disp_name", a.versionName)
                putString("disp_url", a.apkApiUrl)
                putLong("disp_size", a.apkSizeBytes)
            } else {
                remove("disp_code"); remove("disp_name"); remove("disp_url"); remove("disp_size")
            }
            if (status.tokenExpiry != null) putString("token_vence", status.tokenExpiry.toString()) else remove("token_vence")
        }.apply()
    }

    /** Para no repetir la misma notificación cada 12 horas. */
    var lastNotifiedVersionCode: Int
        get() = prefs.getInt("notificada", 0)
        set(value) = prefs.edit().putInt("notificada", value).apply()

    var lastExpiryWarning: String?
        get() = prefs.getString("aviso_vence", null)
        set(value) = prefs.edit().putString("aviso_vence", value).apply()
}
