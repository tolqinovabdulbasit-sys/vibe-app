package com.vibeapp.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pairings",
    foreignKeys = [
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["device_id"],
            childColumns = ["remote_device_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("remote_device_id")]
)
data class PairingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "remote_device_id")
    val remoteDeviceId: String,

    /** AES-GCM encrypted pairing secret — never shown to user */
    @ColumnInfo(name = "pairing_secret_encrypted")
    val pairingSecretEncrypted: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "status")
    val status: String = "ACTIVE", // ACTIVE, DISCONNECTED, BLOCKED

    @ColumnInfo(name = "last_connected_at")
    val lastConnectedAt: Long? = null,

    @ColumnInfo(name = "alias")
    val alias: String? = null // user-given name like "Wife's phone"
)
