package com.lavara.data

/** Ajustes sueltos guardados en la tabla settings. */
class SettingsRepository(private val dao: SettingDao) {
    suspend fun get(key: String): String? = dao.get(key)
    suspend fun set(key: String, value: String) = dao.set(SettingEntity(key, value))
}
