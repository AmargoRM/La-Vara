package com.lavara.system.device

import android.content.Context

/**
 * Qué avisos quiere ver el usuario. Son tres interruptores; con los tres apagados, las automatizaciones no
 * muestran ninguna notificación (todo queda en Historial). Los avisos fijos de los servicios en primer plano
 * los exige Android y no dependen de esto.
 */
class NotificationPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("notificaciones", Context.MODE_PRIVATE)

    /**
     * Mostrar las notificaciones de la acción "Mostrar notificación" (siempre discretas: no flotan ni suenan).
     * Quien venía de "Solo avisar si algo sale mal" (0.1.99) conserva su elección.
     */
    var showOwn: Boolean
        get() = prefs.getBoolean("mostrar_propias", !prefs.getBoolean("solo_errores", true))
        set(value) = prefs.edit().putBoolean("mostrar_propias", value).apply()

    /** Aviso discreto cuando una automatización falla. Encendido por defecto. */
    var notifyErrors: Boolean
        get() = prefs.getBoolean("avisar_errores", true)
        set(value) = prefs.edit().putBoolean("avisar_errores", value).apply()

    /** Mostrar "Tocá para abrir" cuando una app no se pudo abrir sola. Apagado por defecto. */
    var tapToOpen: Boolean
        get() = prefs.getBoolean("tocar_para_abrir", false)
        set(value) = prefs.edit().putBoolean("tocar_para_abrir", value).apply()
}
