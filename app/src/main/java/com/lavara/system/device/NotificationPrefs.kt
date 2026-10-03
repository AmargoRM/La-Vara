package com.lavara.system.device

import android.content.Context

/**
 * Qué notificaciones quiere ver el usuario. Por pedido del usuario, la notificación "Tocá para abrir"
 * (cuando Android no deja abrir algo solo) viene apagada: la falla queda solo en Historial.
 */
class NotificationPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("notificaciones", Context.MODE_PRIVATE)

    /** Mostrar "Tocá para abrir" cuando una app no se pudo abrir sola. Apagado por defecto. */
    var tapToOpen: Boolean
        get() = prefs.getBoolean("tocar_para_abrir", false)
        set(value) = prefs.edit().putBoolean("tocar_para_abrir", value).apply()

    /** Las notificaciones de la acción "Mostrar notificación" salen flotando arriba (true) o discretas (false). */
    var floating: Boolean
        get() = prefs.getBoolean("flotantes", true)
        set(value) = prefs.edit().putBoolean("flotantes", value).apply()
}
