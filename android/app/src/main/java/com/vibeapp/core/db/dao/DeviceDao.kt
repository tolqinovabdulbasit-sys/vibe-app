package com.vibeapp.core.db.dao

import androidx.room.*
import com.vibeapp.core.db.entity.DeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices WHERE device_id = :deviceId LIMIT 1")
    suspend fun getByDeviceId(deviceId: String): DeviceEntity?

    @Query("SELECT * FROM devices ORDER BY last_seen_at DESC")
    fun getAllFlow(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices ORDER BY last_seen_at DESC")
    suspend fun getAll(): List<DeviceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: DeviceEntity): Long

    @Update
    suspend fun update(device: DeviceEntity)

    @Query("UPDATE devices SET status = :status, last_seen_at = :lastSeenAt WHERE device_id = :deviceId")
    suspend fun updateStatus(deviceId: String, status: String, lastSeenAt: Long)

    @Query("DELETE FROM devices WHERE device_id = :deviceId")
    suspend fun deleteByDeviceId(deviceId: String)
}
