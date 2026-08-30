package com.vibeapp.core.db.dao

import androidx.room.*
import com.vibeapp.core.db.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY created_at DESC LIMIT 200")
    fun getRecentFlow(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getPaged(limit: Int, offset: Int): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE command_id = :commandId LIMIT 1")
    suspend fun getByCommandId(commandId: String): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: HistoryEntity): Long

    @Query("""
        UPDATE history SET 
            delivery_status = :status,
            sent_at = CASE WHEN :status = 'SENT' THEN :timestamp ELSE sent_at END,
            received_at = CASE WHEN :status = 'RECEIVED' THEN :timestamp ELSE received_at END,
            vibration_started_at = CASE WHEN :status = 'VIBRATION_STARTED' THEN :timestamp ELSE vibration_started_at END,
            latency_ms = :latencyMs
        WHERE command_id = :commandId
    """)
    suspend fun updateStatus(commandId: String, status: String, timestamp: Long, latencyMs: Long?)

    @Query("DELETE FROM history")
    suspend fun clearAll()

    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY created_at DESC LIMIT 500)")
    suspend fun pruneOldEntries()
}
