package com.lavara.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automations ORDER BY priority DESC, name")
    suspend fun all(): List<AutomationEntity>

    @Query("SELECT * FROM automations ORDER BY priority DESC, name")
    fun observeAll(): Flow<List<AutomationEntity>>

    @Query("SELECT * FROM automations WHERE id = :id")
    suspend fun find(id: String): AutomationEntity?

    @Upsert
    suspend fun upsert(entity: AutomationEntity)

    @Query("DELETE FROM automations WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface RunDao {
    @Insert
    suspend fun insert(run: AutomationRunEntity): Long

    @Query("SELECT * FROM automation_runs ORDER BY started_at DESC, id DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<AutomationRunEntity>>

    @Query("SELECT * FROM automation_runs ORDER BY started_at DESC, id DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<AutomationRunEntity>

    /** Borra las más viejas y deja solo las últimas [keep]. */
    @Query("DELETE FROM automation_runs WHERE id NOT IN (SELECT id FROM automation_runs ORDER BY started_at DESC, id DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}

@Dao
interface LogDao {
    @Insert
    suspend fun insert(log: LogEntity): Long

    @Query("SELECT * FROM logs ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<LogEntity>>

    @Query("SELECT * FROM logs ORDER BY timestamp DESC, id DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<LogEntity>

    @Query("SELECT COUNT(*) FROM logs")
    suspend fun count(): Int

    /** Borra los más viejos y deja solo los últimos [keep]. */
    @Query("DELETE FROM logs WHERE id NOT IN (SELECT id FROM logs ORDER BY timestamp DESC, id DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}

@Dao
interface SettingDao {
    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(setting: SettingEntity)
}
