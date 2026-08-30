package com.vibeapp.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vibration_patterns",
    indices = [Index(value = ["slot"], unique = true)]
)
data class VibrationPatternEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Fixed slot 1–10 for the 10 configurable patterns */
    @ColumnInfo(name = "slot")
    val slot: Int,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "enabled")
    val enabled: Boolean = true,

    /** JSON array of PatternStep: [{type:"VIBRATE",durationMs:100},{type:"PAUSE",durationMs:100},...] */
    @ColumnInfo(name = "pattern_data")
    val patternData: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
