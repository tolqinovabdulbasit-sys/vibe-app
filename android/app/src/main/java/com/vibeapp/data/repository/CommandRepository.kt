package com.vibeapp.data.repository

import com.vibeapp.core.db.dao.HistoryDao
import com.vibeapp.core.db.dao.VibrationPatternDao
import com.vibeapp.core.db.entity.HistoryEntity
import com.vibeapp.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandRepository @Inject constructor(
    private val historyDao: HistoryDao,
    private val vibrationPatternDao: VibrationPatternDao,
    private val pairingRepository: PairingRepository,
    private val json: Json
) {
    // In-memory device status map (updated from WebSocket STATUS messages)
    private val deviceStatusMap = mutableMapOf<String, String>()

    fun getHistoryFlow(): Flow<List<HistoryEntry>> {
        return historyDao.getRecentFlow().map { entities ->
            entities.map { it.toHistoryEntry() }
        }
    }

    suspend fun saveOutgoingCommand(command: VibrationCommand) {
        historyDao.insert(
            HistoryEntity(
                commandId = command.commandId,
                targetDeviceId = command.targetDeviceId,
                vibrationPatternSlot = command.vibrationPatternSlot,
                vibrationPatternName = command.vibrationPatternName,
                direction = "OUTGOING",
                sentAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun saveIncomingCommand(command: VibrationCommand) {
        historyDao.insert(
            HistoryEntity(
                commandId = command.commandId,
                targetDeviceId = command.sourceDeviceId,
                vibrationPatternSlot = command.vibrationPatternSlot,
                vibrationPatternName = command.vibrationPatternName,
                direction = "INCOMING",
                receivedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateStatus(commandId: String, status: DeliveryStatus) {
        val now = System.currentTimeMillis()
        val existing = historyDao.getByCommandId(commandId)
        val latency = if (existing?.sentAt != null && status == DeliveryStatus.VIBRATION_STARTED) {
            now - existing.sentAt
        } else null

        historyDao.updateStatus(commandId, status.name, now, latency)
        Timber.d("Command $commandId status → $status (latency: ${latency}ms)")
    }

    suspend fun getPattern(slot: Int): VibrationPattern? {
        val entity = vibrationPatternDao.getBySlot(slot) ?: return null
        return try {
            val steps = json.decodeFromString<List<PatternStep>>(entity.patternData)
            VibrationPattern(
                slot = entity.slot,
                name = entity.name,
                steps = steps,
                enabled = entity.enabled
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse pattern data for slot $slot")
            null
        }
    }

    fun updateDeviceStatus(deviceId: String, status: String) {
        deviceStatusMap[deviceId] = status
    }

    fun getDeviceStatus(deviceId: String): String {
        return deviceStatusMap[deviceId] ?: "UNKNOWN"
    }

    suspend fun clearHistory() {
        historyDao.clearAll()
    }

    private fun HistoryEntity.toHistoryEntry() = HistoryEntry(
        commandId = commandId,
        targetDeviceId = targetDeviceId,
        patternSlot = vibrationPatternSlot,
        patternName = vibrationPatternName,
        direction = direction,
        createdAt = createdAt,
        deliveryStatus = deliveryStatus,
        latencyMs = latencyMs,
        deviceAlias = deviceAlias
    )
}

data class HistoryEntry(
    val commandId: String,
    val targetDeviceId: String,
    val patternSlot: Int,
    val patternName: String,
    val direction: String,
    val createdAt: Long,
    val deliveryStatus: String,
    val latencyMs: Long?,
    val deviceAlias: String?
)
