package com.vibeapp.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "fcm_token")
    val fcmToken: String? = null,

    @ColumnInfo(name = "firebase_uid")
    val firebaseUid: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_seen_at")
    val lastSeenAt: Long? = null,

    @ColumnInfo(name = "status")
    val status: String = "UNKNOWN" // ONLINE, OFFLINE, UNKNOWN
)
