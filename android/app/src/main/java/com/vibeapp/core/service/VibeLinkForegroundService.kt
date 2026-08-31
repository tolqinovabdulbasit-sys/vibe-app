package com.vibeapp.core.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.vibeapp.MainActivity
import com.vibeapp.R
import com.vibeapp.core.crypto.DeviceIdentityManager
import com.vibeapp.core.network.ConnectionState
import com.vibeapp.core.network.MqttManager
import com.vibeapp.core.notification.NotificationChannels
import com.vibeapp.core.vibration.VibrationEngine
import com.vibeapp.data.model.*
import com.vibeapp.data.repository.CommandRepository
import com.vibeapp.data.repository.PairingRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

/**
 * Persistent foreground service for VibeLink.
 * Connects to ultra-fast MQTT broker for real-time vibration streaming and execution.
 */
@AndroidEntryPoint
class VibeLinkForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.vibeapp.START"
        const val ACTION_STOP = "com.vibeapp.STOP"
        const val NOTIFICATION_ID = 1001
    }

    @Inject lateinit var mqttManager: MqttManager
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
            else -> initializeConnection()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        vibrationEngine.stopAll()
        mqttManager.disconnect()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun initializeConnection() {
        serviceScope.launch {
            try {
                deviceId = deviceIdentityManager.getOrCreateDeviceId()
                mqttManager.connect(deviceId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize MQTT connection")
            }
        }
    }

    private fun observeConnectionState() {
        serviceScope.launch {
            mqttManager.connectionState.collectLatest { state ->
                Timber.d("MQTT Connection state: $state")
                updateNotification(state)

                if (state == ConnectionState.DISCONNECTED ||
                    state == ConnectionState.RECONNECTING) {
                    vibrationEngine.stopManual()
                }
            }
        }
    }

    private fun observeIncomingMessages() {
        serviceScope.launch {
            mqttManager.incomingMessages.collect { message ->
                val topic = message.topic
                val payload = message.payload

                if (topic == "vibeapp/cmd/$deviceId") {
                    processIncomingCommand(payload)
                } else if (topic == "vibeapp/ack/$deviceId") {
                    processIncomingAck(payload)
                }
            }
        }
    }

    private suspend fun processIncomingCommand(payloadJson: String) {
        try {
            val command = json.decodeFromString<VibrationCommand>(payloadJson)
            Timber.d("Incoming MQTT command: ${command.commandId} type=${command.commandType}")

            val isPaired = pairingRepository.isPaired(command.sourceDeviceId)
            if (!isPaired) {
                Timber.w("Command from unpaired device ${command.sourceDeviceId} — rejected")
                return
            }

            if (!vibrationEngine.tryMarkProcessed(command.commandId)) {
                sendAck(command.commandId, command.sourceDeviceId, DeliveryStatus.VIBRATION_STARTED)
                return
            }

            commandRepository.saveIncomingCommand(command)

            when (command.commandType) {
                CommandType.PATTERN -> {
                    val pattern = commandRepository.getPattern(command.vibrationPatternSlot)
                    if (pattern != null) {
                        vibrationEngine.playPattern(pattern)
                        sendAck(command.commandId, command.sourceDeviceId, DeliveryStatus.VIBRATION_STARTED)
                        commandRepository.updateStatus(command.commandId, DeliveryStatus.VIBRATION_STARTED)
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
            Timber.e(e, "Error processing MQTT command")
        }
    }

    private fun processIncomingAck(payloadJson: String) {
        serviceScope.launch {
            try {
                val ack = json.decodeFromString<AckMessage>(payloadJson)
                commandRepository.updateStatus(ack.commandId, ack.status)
            } catch (e: Exception) {
                Timber.e(e, "Error processing MQTT ACK")
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
        mqttManager.publish("vibeapp/ack/$targetDeviceId", json.encodeToString(ack))
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification("Ulanyapti...")
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        )
    }

    private fun updateNotification(state: ConnectionState) {
        val statusText = when (state) {
            ConnectionState.CONNECTED -> "● Ulangan (MQTT Instant)"
            ConnectionState.CONNECTING -> "⟳ Ulanmoqda..."
            ConnectionState.RECONNECTING -> "⟳ Qayta ulanmoqda..."
            ConnectionState.DISCONNECTED -> "○ Oflayn"
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
