package com.vibeapp.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class CommandType {
    PATTERN,        // One-shot pattern
    MANUAL_START,   // Start continuous vibration
    MANUAL_STOP     // Stop continuous vibration
}

@Serializable
enum class DeliveryStatus {
    CREATED,
    SENT,
    RECEIVED,
    VIBRATION_STARTED,
    ACK_SENT,
    CONFIRMED,
    FAILED,
    TIMEOUT
}

@Serializable
data class VibrationCommand(
    val commandId: String,
    val sourceDeviceId: String,
    val targetDeviceId: String,
    val commandType: CommandType,
    val vibrationPatternSlot: Int,        // 0 for manual
    val vibrationPatternName: String,
    val timestamp: Long,
    val sequence: Long,
    /** AES-GCM encrypted payload (pattern data) — backend only routes, never reads */
    val encryptedPayload: String? = null
)

@Serializable
data class AckMessage(
    val commandId: String,
    val sourceDeviceId: String,
    val status: DeliveryStatus,
    val timestamp: Long
)

@Serializable
data class WebSocketMessage(
    val type: String, // "COMMAND" | "ACK" | "HEARTBEAT" | "STATUS" | "PAIRING_COMPLETE"
    val payload: String  // JSON-encoded inner object
)

@Serializable
data class DeviceStatusMessage(
    val deviceId: String,
    val status: String, // ONLINE | OFFLINE
    val timestamp: Long
)

@Serializable
data class HeartbeatMessage(
    val deviceId: String,
    val timestamp: Long
)
