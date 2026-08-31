package com.vibeapp.data.repository

import com.vibeapp.core.crypto.DeviceIdentityManager
import com.vibeapp.core.crypto.KeystoreManager
import com.vibeapp.core.db.dao.DeviceDao
import com.vibeapp.core.db.dao.PairingDao
import com.vibeapp.core.db.entity.DeviceEntity
import com.vibeapp.core.db.entity.PairingEntity
import com.vibeapp.core.network.MqttManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

sealed class PairingResult {
    data class Success(val remoteDeviceId: String, val remoteAlias: String) : PairingResult()
    object InvalidCode : PairingResult()
    object ExpiredCode : PairingResult()
    object AlreadyUsed : PairingResult()
    data class Error(val message: String) : PairingResult()
}

data class PairedDevice(
    val deviceId: String,
    val alias: String,
    val status: String,
    val lastConnectedAt: Long?
)

@Singleton
class PairingRepository @Inject constructor(
    private val deviceDao: DeviceDao,
    private val pairingDao: PairingDao,
    private val keystoreManager: KeystoreManager,
    private val deviceIdentityManager: DeviceIdentityManager,
    private val mqttManager: MqttManager,
    private val json: Json
) {
    private val repositoryScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    /**
     * Generates a one-time 8-character pairing code and subscribes to the MQTT topic.
     * When Device B enters this code, it publishes its identity to vibeapp/pair/$code.
     */
    suspend fun generatePairingCode(): String {
        val myDeviceId = deviceIdentityManager.getOrCreateDeviceId()
        val myDisplayName = deviceIdentityManager.getOrCreateDisplayName()
        val code = generateSecureCode()

        mqttManager.connect(myDeviceId)
        val pairTopic = "vibeapp/pair/$code"
        mqttManager.subscribe(pairTopic)

        // Background listener for incoming consumer requests on this code
        mqttManager.incomingMessages
            .filter { it.topic == pairTopic }
            .take(1)
            .onEach { msg ->
                try {
                    val data = json.decodeFromString<Map<String, String>>(msg.payload)
                    val consumerDeviceId = data["consumer_device_id"] ?: return@onEach
                    val consumerDisplayName = data["consumer_display_name"] ?: "Unknown Device"

                    // Save pairing locally
                    val sharedSecret = generateSharedSecret()
                    val encryptedSecret = keystoreManager.encrypt(sharedSecret)
                    savePairingLocally(consumerDeviceId, consumerDisplayName, encryptedSecret)

                    // Reply back to consumer on vibeapp/pair/$code/ack
                    val ackData = mapOf(
                        "initiator_device_id" to myDeviceId,
                        "initiator_display_name" to myDisplayName
                    )
                    mqttManager.publish("vibeapp/pair/$code/ack", json.encodeToString(ackData))
                    Timber.d("Pairing complete via MQTT for code: $code")

                } catch (e: Exception) {
                    Timber.e(e, "Error processing MQTT pairing request")
                }
            }
            .launchIn(repositoryScope)

        Timber.d("Pairing code generated: $code")
        return code
    }

    /**
     * Consumes a pairing code entered by the user.
     * Publishes identity to vibeapp/pair/$code and waits for response.
     */
    suspend fun consumePairingCode(code: String): PairingResult {
        return try {
            val formattedCode = code.uppercase().trim()
            val myDeviceId = deviceIdentityManager.getOrCreateDeviceId()
            val myDisplayName = deviceIdentityManager.getOrCreateDisplayName()

            mqttManager.connect(myDeviceId)

            val pairTopic = "vibeapp/pair/$formattedCode"
            val ackTopic = "vibeapp/pair/$formattedCode/ack"

            mqttManager.subscribe(ackTopic)

            // Publish consumer info
            val consumerData = mapOf(
                "consumer_device_id" to myDeviceId,
                "consumer_display_name" to myDisplayName
            )
            mqttManager.publish(pairTopic, json.encodeToString(consumerData))

            // Wait up to 10s for initiator response
            val response = withTimeoutOrNull(10000) {
                mqttManager.incomingMessages
                    .filter { it.topic == ackTopic }
                    .first()
            }

            if (response == null) {
                return PairingResult.Error("Ulanish vaqti tugadi. Kodni qayta kiritib ko'ring.")
            }

            val ackData = json.decodeFromString<Map<String, String>>(response.payload)
            val remoteDeviceId = ackData["initiator_device_id"] ?: return PairingResult.Error("Missing device ID")
            val remoteDisplayName = ackData["initiator_display_name"] ?: "Unknown Device"

            val sharedSecret = generateSharedSecret()
            val encryptedSecret = keystoreManager.encrypt(sharedSecret)
            savePairingLocally(remoteDeviceId, remoteDisplayName, encryptedSecret)

            PairingResult.Success(remoteDeviceId, remoteDisplayName)

        } catch (e: Exception) {
            Timber.e(e, "Error consuming pairing code via MQTT")
            PairingResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun isPaired(remoteDeviceId: String): Boolean {
        return pairingDao.getByDeviceId(remoteDeviceId) != null
    }

    fun getActivePairingsFlow(): Flow<List<PairedDevice>> {
        return pairingDao.getActivePairingsFlow().map { entities ->
            entities.map { entity ->
                PairedDevice(
                    deviceId = entity.remoteDeviceId,
                    alias = entity.alias ?: entity.remoteDeviceId.take(8),
                    status = entity.status,
                    lastConnectedAt = entity.lastConnectedAt
                )
            }
        }
    }

    suspend fun renameDevice(deviceId: String, newAlias: String) {
        pairingDao.updateAlias(deviceId, newAlias)
    }

    suspend fun removeDevice(deviceId: String) {
        pairingDao.deleteByDeviceId(deviceId)
        deviceDao.deleteByDeviceId(deviceId)
    }

    suspend fun updateDeviceStatus(deviceId: String, status: String) {
        pairingDao.updateStatus(deviceId, status, System.currentTimeMillis())
        deviceDao.updateStatus(deviceId, status, System.currentTimeMillis())
    }

    private suspend fun savePairingLocally(
        remoteDeviceId: String,
        displayName: String,
        encryptedSecret: String
    ) {
        deviceDao.insert(
            DeviceEntity(
                deviceId = remoteDeviceId,
                displayName = displayName,
                lastSeenAt = System.currentTimeMillis()
            )
        )
        pairingDao.insert(
            PairingEntity(
                remoteDeviceId = remoteDeviceId,
                pairingSecretEncrypted = encryptedSecret,
                alias = displayName,
                lastConnectedAt = System.currentTimeMillis()
            )
        )
    }

    private fun generateSecureCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return (1..8).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    private fun generateSharedSecret(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }
}
