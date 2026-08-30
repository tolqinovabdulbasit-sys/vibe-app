package com.vibeapp.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "history",
    indices = [Index(value = ["command_id"], unique = true)]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "command_id")
    val commandId: String,

    @ColumnInfo(name = "target_device_id")
    val targetDeviceId: String,

    @ColumnInfo(name = "vibration_pattern_slot")
    val vibrationPatternSlot: Int, // 0 = manual, 1-10 = pattern slots, 11-16 = side buttons

    @ColumnInfo(name = "vibration_pattern_name")
    val vibrationPatternName: String,

    /** OUTGOING or INCOMING */
    @ColumnInfo(name = "direction")
    val direction: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "sent_at")
    val sentAt: Long? = null,

    @ColumnInfo(name = "received_at")
    val receivedAt: Long? = null,

    @ColumnInfo(name = "vibration_started_at")
    val vibrationStartedAt: Long? = null,

    /** CREATED, SENT, RECEIVED, VIBRATION_STARTED, CONFIRMED, FAILED, TIMEOUT */
    @ColumnInfo(name = "delivery_status")
    val deliveryStatus: String = "CREATED",

    @ColumnInfo(name = "latency_ms")
    val latencyMs: Long? = null,

    @ColumnInfo(name = "device_alias")
    val deviceAlias: String? = null
)
