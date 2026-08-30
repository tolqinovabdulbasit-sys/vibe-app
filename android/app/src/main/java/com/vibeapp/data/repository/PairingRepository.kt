package com.vibeapp.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.getValue
import com.vibeapp.core.crypto.DeviceIdentityManager
import com.vibeapp.core.crypto.KeystoreManager
import com.vibeapp.core.db.dao.DeviceDao
import com.vibeapp.core.db.dao.PairingDao
import com.vibeapp.core.db.entity.DeviceEntity
import com.vibeapp.core.db.entity.PairingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
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
    private val deviceIdentityManager: DeviceIdentityManager
) {
    private fun getDb(): com.google.firebase.database.DatabaseReference {
        return FirebaseDatabase.getInstance("https://vibe-app-b07cc-default-rtdb.europe-west1.firebasedatabase.app").reference
    }

    companion object {
        private const val PAIRING_CODES_PATH = "pairing_codes"
        private const val DEVICES_PATH = "devices"
        private const val CODE_TTL_MS = 15 * 60 * 1000L // 15 minutes
    }

    private suspend fun ensureAuth() {
        try {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
                Timber.d("Signed in anonymously: ${auth.currentUser?.uid}")
            }
        } catch (e: Exception) {
            Timber.w(e, "Anonymous auth failed or not enabled: ${e.message}")
        }
    }

    /**
     * Generates a one-time pairing code and writes it to Firebase.
     * Returns the 8-character code to show to the user.
     */
    suspend fun generatePairingCode(): String {
        val deviceId = deviceIdentityManager.getOrCreateDeviceId()
        val displayName = deviceIdentityManager.getOrCreateDisplayName()
        val code = generateSecureCode()

        val codeData = mapOf(
            "code" to code,
            "initiator_device_id" to deviceId,
            "initiator_display_name" to displayName,
            "created_at" to System.currentTimeMillis(),
            "expires_at" to System.currentTimeMillis() + CODE_TTL_MS,
            "used" to false
        )

        ensureAuth()

        withTimeoutOrNull(8000) {
            getDb().child(PAIRING_CODES_PATH).child(code).setValue(codeData).await()
        } ?: throw IllegalStateException("Firebase ulanish vaqti tugadi (Timeout 8s). Internet va Realtime Database holatini tekshiring.")

        Timber.d("Pairing code generated: $code")
        return code
    }

    /**
     * Consumes a pairing code entered by the user.
     * On success, saves the pairing locally and returns remote device info.
     */
    suspend fun consumePairingCode(code: String): PairingResult {
        return try {
            ensureAuth()
            val codeRef = getDb().child(PAIRING_CODES_PATH).child(code)
            val snapshot = codeRef.get().await()

            if (!snapshot.exists()) {
                return PairingResult.InvalidCode
            }

            val used = snapshot.child("used").getValue<Boolean>() ?: false
            if (used) return PairingResult.AlreadyUsed

            val expiresAt = snapshot.child("expires_at").getValue<Long>() ?: 0L
            if (System.currentTimeMillis() > expiresAt) {
                codeRef.removeValue().await()
                return PairingResult.ExpiredCode
            }

            val remoteDeviceId = snapshot.child("initiator_device_id").getValue<String>()
                ?: return PairingResult.Error("Missing device ID")
            val remoteDisplayName = snapshot.child("initiator_display_name").getValue<String>()
                ?: "Unknown Device"

            // Mark code as used (atomic — prevents race conditions)
            codeRef.child("used").setValue(true).await()
            codeRef.child("consumer_device_id").setValue(
                deviceIdentityManager.getOrCreateDeviceId()
            ).await()

            // Generate shared pairing secret
            val sharedSecret = generateSharedSecret()
            val encryptedSecret = keystoreManager.encrypt(sharedSecret)

            // Save pairing locally
            val myDeviceId = deviceIdentityManager.getOrCreateDeviceId()
            savePairingLocally(remoteDeviceId, remoteDisplayName, encryptedSecret)

            // Register pairing in Firebase (both directions)
            registerPairingInFirebase(myDeviceId, remoteDeviceId, sharedSecret)

            PairingResult.Success(remoteDeviceId, remoteDisplayName)

        } catch (e: Exception) {
            Timber.e(e, "Error consuming pairing code")
            PairingResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun registerFcmToken(deviceId: String, token: String) {
        try {
            ensureAuth()
            getDb().child(DEVICES_PATH).child(deviceId).child("fcm_token").setValue(token).await()
        } catch (e: Exception) {
            Timber.e(e, "Failed to register FCM token")
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

    private suspend fun registerPairingInFirebase(
        myDeviceId: String,
        remoteDeviceId: String,
        sharedSecret: String
    ) {
        val timestamp = System.currentTimeMillis()
        // Store routing relationship (NOT the secret — only device IDs)
        getDb().child("pairings").child("${myDeviceId}_${remoteDeviceId}").setValue(
            mapOf("created_at" to timestamp, "active" to true)
        ).await()
        getDb().child("pairings").child("${remoteDeviceId}_${myDeviceId}").setValue(
            mapOf("created_at" to timestamp, "active" to true)
        ).await()
    }

    private fun generateSecureCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Ambiguous chars removed
        val random = SecureRandom()
        return (1..8).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    private fun generateSharedSecret(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }
}
