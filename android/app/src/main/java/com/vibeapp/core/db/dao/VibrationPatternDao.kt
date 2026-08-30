package com.vibeapp.core.db.dao

import androidx.room.*
import com.vibeapp.core.db.entity.VibrationPatternEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VibrationPatternDao {
    @Query("SELECT * FROM vibration_patterns ORDER BY slot ASC")
    fun getAllFlow(): Flow<List<VibrationPatternEntity>>

    @Query("SELECT * FROM vibration_patterns ORDER BY slot ASC")
    suspend fun getAll(): List<VibrationPatternEntity>

    @Query("SELECT * FROM vibration_patterns WHERE slot = :slot LIMIT 1")
    suspend fun getBySlot(slot: Int): VibrationPatternEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(patterns: List<VibrationPatternEntity>)

    @Update
    suspend fun update(pattern: VibrationPatternEntity)

    @Query("UPDATE vibration_patterns SET name = :name WHERE slot = :slot")
    suspend fun updateName(slot: Int, name: String)

    @Query("UPDATE vibration_patterns SET enabled = :enabled WHERE slot = :slot")
    suspend fun updateEnabled(slot: Int, enabled: Boolean)

    @Query("UPDATE vibration_patterns SET pattern_data = :patternData, updated_at = :updatedAt WHERE slot = :slot")
    suspend fun updatePatternData(slot: Int, patternData: String, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM vibration_patterns")
    suspend fun getCount(): Int
}
