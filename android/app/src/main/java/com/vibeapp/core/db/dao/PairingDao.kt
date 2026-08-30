package com.vibeapp.core.db.dao

import androidx.room.*
import com.vibeapp.core.db.entity.PairingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PairingDao {
    @Query("SELECT * FROM pairings WHERE status = 'ACTIVE' ORDER BY last_connected_at DESC")
    fun getActivePairingsFlow(): Flow<List<PairingEntity>>

    @Query("SELECT * FROM pairings WHERE status = 'ACTIVE' ORDER BY last_connected_at DESC")
    suspend fun getActivePairings(): List<PairingEntity>

    @Query("SELECT * FROM pairings WHERE remote_device_id = :deviceId LIMIT 1")
    suspend fun getByDeviceId(deviceId: String): PairingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pairing: PairingEntity): Long

    @Update
    suspend fun update(pairing: PairingEntity)

    @Query("UPDATE pairings SET alias = :alias WHERE remote_device_id = :deviceId")
    suspend fun updateAlias(deviceId: String, alias: String)

    @Query("UPDATE pairings SET status = :status, last_connected_at = :timestamp WHERE remote_device_id = :deviceId")
    suspend fun updateStatus(deviceId: String, status: String, timestamp: Long)

    @Query("DELETE FROM pairings WHERE remote_device_id = :deviceId")
    suspend fun deleteByDeviceId(deviceId: String)

    @Query("SELECT COUNT(*) FROM pairings WHERE status = 'ACTIVE'")
    suspend fun getActivePairingCount(): Int
}
