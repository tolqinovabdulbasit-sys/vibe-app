package com.vibeapp.core.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.vibeapp.MainActivity
import com.vibeapp.R
import com.vibeapp.core.crypto.DeviceIdentityManager
import com.vibeapp.core.network.ConnectionState
import com.vibeapp.core.network.WebSocketManager
import com.vibeapp.core.notification.NotificationChannels
import com.vibeapp.core.vibration.VibrationEngine
import com.vibeapp.data.model.*
import com.vibeapp.data.repository.CommandRepository
import com.vibeapp.data.repository.PairingRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

/**
 * Persistent foreground service using type=remoteMessaging.
 * Manages WebSocket connection, incoming command processing,
 * vibration execution, and ACK sending.
 *
 * Safety guarantee: stops manual vibration on connection loss.
 */
@AndroidEntryPoint
class VibeLinkForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.vibeapp.START"
        const val ACTION_STOP = "com.vibeapp.STOP"
        const val ACTION_FCM_WAKEUP = "com.vibeapp.FCM_WAKEUP"
        const val NOTIFICATION_ID = 1001
    }

    @Inject lateinit var webSocketManager: WebSocketManager
    @Inject lateinit var vibrationEngine: VibrationEngine
    @Inject lateinit var deviceIdentityManager: DeviceIdentityManager
    @Inject lateinit var commandRepository: CommandRepository
    @Inject lateinit var pairingRepository: PairingRepository
    @Inject lateinit var json: Json

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var deviceId = ""

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        observeConnectionState()
        observeIncomingMessages()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_FCM_WAKEUP -> handleFcmWakeup(intent)
            else -> initializeConnection()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // Stop any ongoing vibration on service destroy for safety
        vibrationEngine.stopAll()
        webSocketManager.disconnect()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ----------------------------------------------------------------
    // Initialization
    // ----------------------------------------------------------------

    private fun initializeConnection() {
        serviceScope.launch {
            try {
                deviceId = deviceIdentityManager.getOrCreateDeviceId()

                // Sign in anonymously with Firebase
                val firebaseUid = signInFirebase()

                // Get FCM token and WebSocket URL from Realtime Database
                val wsUrl = buildWebSocketUrl()
                val token = firebaseUid ?: ""

                webSocketManager.configure(
                    deviceId = deviceId,
                    authToken = token,
                    wsUrl = wsUrl
                )
                webSocketManager.connect()
                registerFcmToken()

            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize connection")
            }
        }
    }

    private suspend fun signInFirebase(): String? {
        return try {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                val result = auth.signInAnonymously().await()
                result.user?.uid
            } else {
                auth.currentUser?.getIdToken(false)?.await()?.token
            }
        } catch (e: Exception) {
            Timber.e(e, "Firebase auth failed")
            null
        }
    }

    private fun buildWebSocketUrl(): String {
        // Firebase Realtime Database WebSocket endpoint
        // Replace YOUR_PROJECT_ID with actual project from google-services.json
        val db = FirebaseDatabase.getInstance("https://vibe-app-b07cc-default-rtdb.europe-west1.firebasedatabase.app")
        val ref = db.reference
        // Use RTDB REST as WebSocket backing
        return ref.toString().replace("https://", "wss://") + "/.ws?v=5"
    }

    private fun registerFcmToken() {
        // FCM token registration to Firebase for this device
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                Timber.d("FCM token obtained, registering...")
                serviceScope.launch {
                    pairingRepository.registerFcmToken(deviceId, token)
                }
            }
    }

    // ----------------------------------------------------------------
    // Connection State Observer
    // ----------------------------------------------------------------

    private fun observeConnectionState() {
        serviceScope.launch {
            webSocketManager.connectionState.collectLatest { state ->
                Timber.d("Connection state: $state")
                updateNotification(state)

                // SAFETY: stop manual vibration on connection loss
                if (state == ConnectionState.DISCONNECTED ||
                    state == ConnectionState.RECONNECTING) {
                    vibrationEngine.stopManual()
                }
            }
        }
    }

    // ----------------------------------------------------------------
    // Incoming Message Processing
    // ----------------------------------------------------------------

    private fun observeIncomingMessages() {
        serviceScope.launch {
            webSocketManager.incomingMessages.collect { message ->
                when (message.type) {
                    "COMMAND" -> processIncomingCommand(message.payload)
                    "ACK" -> processIncomingAck(message.payload)
                    "STATUS" -> processDeviceStatus(message.payload)
                    "HEARTBEAT" -> { /* Handled by WebSocketManager */ }
                    else -> Timber.w("Unknown message type: ${message.type}")
                }
            }
        }
    }

    private suspend fun processIncomingCommand(payloadJson: String) {
        try {
            val command = json.decodeFromString<VibrationCommand>(payloadJson)
            Timber.d("Incoming command: ${command.commandId} type=${command.commandType}")

            // Verify this command is from a paired device
            val isPaired = pairingRepository.isPaired(command.sourceDeviceId)
            if (!isPaired) {
                Timber.w("Command from unpaired device ${command.sourceDeviceId} — rejected")
                return
            }

            // Deduplication — don't vibrate twice for same commandId
            if (!vibrationEngine.tryMarkProcessed(command.commandId)) {
                Timber.d("Duplicate command ${command.commandId} — ignored, sending existing ACK")
                sendAck(command.commandId, command.sourceDeviceId, DeliveryStatus.VIBRATION_STARTED)
                return
            }

            // Save to history
            commandRepository.saveIncomingCommand(command)

            // Execute vibration
            when (command.commandType) {
                CommandType.PATTERN -> {
                    val pattern = commandRepository.getPattern(command.vibrationPatternSlot)
                    if (pattern != null) {
                        vibrationEngine.playPattern(pattern)
                        sendAck(command.commandId, command.sourceDeviceId, DeliveryStatus.VIBRATION_STARTED)
                        commandRepository.updateStatus(command.commandId, DeliveryStatus.VIBRATION_STARTED)
                    } else {
                        Timber.w("Pattern slot ${command.vibrationPatternSlot} not found")
                    }
                }
                CommandType.MANUAL_START -> {
                    vibrationEngine.startManual()
                    sendAck(command.commandId, command.sourceDeviceId, DeliveryStatus.VIBRATION_STARTED)
                }
                CommandType.MANUAL_STOP -> {
                    vibrationEngine.stopManual()
                    sendAck(command.commandId, command.sourceDeviceId, DeliveryStatus.CONFIRMED)
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Error processing command")
        }
    }

    private fun processIncomingAck(payloadJson: String) {
        serviceScope.launch {
            try {
                val ack = json.decodeFromString<AckMessage>(payloadJson)
                Timber.d("ACK received for ${ack.commandId}: ${ack.status}")
                commandRepository.updateStatus(ack.commandId, ack.status)
            } catch (e: Exception) {
                Timber.e(e, "Error processing ACK")
            }
        }
    }

    private fun processDeviceStatus(payloadJson: String) {
        serviceScope.launch {
            try {
                val status = json.decodeFromString<DeviceStatusMessage>(payloadJson)
                commandRepository.updateDeviceStatus(status.deviceId, status.status)
            } catch (e: Exception) {
                Timber.e(e, "Error processing device status")
            }
        }
    }

    private fun sendAck(commandId: String, targetDeviceId: String, status: DeliveryStatus) {
        val ack = AckMessage(
            commandId = commandId,
            sourceDeviceId = deviceId,
            status = status,
            timestamp = System.currentTimeMillis()
        )
        webSocketManager.sendAck(ack)
    }

    // ----------------------------------------------------------------
    // FCM Wakeup
    // ----------------------------------------------------------------

    private fun handleFcmWakeup(intent: Intent) {
        Timber.d("FCM wakeup received")
        // Reconnect WebSocket if not connected
        if (webSocketManager.connectionState.value != ConnectionState.CONNECTED) {
            initializeConnection()
        }
        // If FCM carries an inline command payload, process it
        val payload = intent.getStringExtra("fcm_payload")
        if (payload != null) {
            serviceScope.launch {
                processIncomingCommand(payload)
            }
        }
    }

    // ----------------------------------------------------------------
    // Notification
    // ----------------------------------------------------------------

    private fun startForegroundWithNotification() {
        val notification = buildNotification("Connecting...")
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        )
    }

    private fun updateNotification(state: ConnectionState) {
        val statusText = when (state) {
            ConnectionState.CONNECTED -> "● Connected"
            ConnectionState.CONNECTING -> "⟳ Connecting..."
            ConnectionState.RECONNECTING -> "⟳ Reconnecting..."
            ConnectionState.DISCONNECTED -> "○ Offline"
        }
        val notification = buildNotification(statusText)
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(statusText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotificationChannels.CONNECTION_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("VibeLink")
            .setContentText(statusText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

// Extension to await Firebase Tasks in coroutines
suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) {} }
        addOnFailureListener { cont.resumeWithException(it) }
    }
