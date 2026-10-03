package com.lavara.system.accessibility

import android.content.Context

/**
 * Apps donde La Vara puede tocar botones con Accesibilidad. Empieza vacía; el usuario agrega cada app
 * desde el editor y la quita desde el menú lateral. Se aplica dos veces: Android no le manda a La Vara
 * avisos de apps fuera de la lista, y [TapService] se niega a leer o tocar una pantalla de otra app.
 */
class AllowedApps(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("accesibilidad", Context.MODE_PRIVATE)

    fun get(): Set<String> = prefs.getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    fun add(packageName: String) = save(get() + packageName)

    fun remove(packageName: String) = save(get() - packageName)

    private fun save(apps: Set<String>) {
        prefs.edit().putStringSet(KEY, apps).apply()
        TapService.current()?.applyAllowed(apps)
    }

    private companion object {
        const val KEY = "apps_permitidas"
    }
}
