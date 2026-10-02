package com.lavara.core

/**
 * Texto de versión visible en pantalla. Sirve para que el usuario confirme,
 * sin herramientas, que el APK nuevo se instaló encima del anterior.
 */
object AppInfo {
    fun versionLabel(versionName: String, versionCode: Int): String =
        "Versión $versionName (compilación $versionCode)"
}
