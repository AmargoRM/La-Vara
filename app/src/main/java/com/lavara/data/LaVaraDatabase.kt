package com.lavara.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de datos de La Vara. Reglas que no se negocian (CLAUDE.md):
 * - Nunca `fallbackToDestructiveMigration`: borraría todas las automatizaciones del usuario.
 * - Cada cambio de esquema sube [version], lleva una Migration escrita a mano y un test.
 * - El esquema de cada versión queda en app/schemas (lo exporta Room al compilar).
 */
@Database(
    entities = [AutomationEntity::class, AutomationRunEntity::class, LogEntity::class, SettingEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class LaVaraDatabase : RoomDatabase() {
    abstract fun automationDao(): AutomationDao
    abstract fun runDao(): RunDao
    abstract fun logDao(): LogDao
    abstract fun settingDao(): SettingDao

    companion object {
        const val NAME = "lavara.db"

        fun create(context: Context): LaVaraDatabase =
            Room.databaseBuilder(context, LaVaraDatabase::class.java, NAME).build()
    }
}
