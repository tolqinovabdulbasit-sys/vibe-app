package com.vibeapp.core.network

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class MqttReceivedMessage(
    val topic: String,
    val payload: String
)

@Singleton
class MqttManager @Inject constructor() {
    companion object {
        // Public HiveMQ MQTT Broker - 100% Free, Zero auth, ultra-fast latency (<50ms)
        private const val BROKER_URL = "tcp://broker.hivemq.com:1883"
        private const val RECONNECT_DELAY_MS = 3_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<MqttReceivedMessage>(replay = 0, extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<MqttReceivedMessage> = _incomingMessages.asSharedFlow()

    private var client: MqttAsyncClient? = null
    private var myDeviceId: String = ""
    private var reconnectJob: Job? = null

    fun connect(deviceId: String) {
        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.CONNECTING) return

        this.myDeviceId = deviceId
        openConnection()
    }

    fun disconnect() {
        reconnectJob?.cancel()
        try {
            client?.disconnect()
            client?.close()
        } catch (e: Exception) {
            Timber.e(e, "Error disconnecting MQTT")
        }
        client = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun subscribe(topic: String) {
        scope.launch {
            try {
                if (client?.isConnected == true) {
                    client?.subscribe(topic, 1)
                    Timber.d("Subscribed to MQTT topic: $topic")
                } else {
                    Timber.w("Cannot subscribe to $topic — MQTT client not connected")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to subscribe to topic: $topic")
            }
        }
    }

    fun publish(topic: String, message: String, qos: Int = 1) {
        scope.launch {
            try {
                if (client?.isConnected == true) {
                    val mqttMessage = MqttMessage(message.toByteArray(Charsets.UTF_8)).apply {
                        this.qos = qos
                    }
                    client?.publish(topic, mqttMessage)
                    Timber.d("MQTT published to [$topic]: $message")
                } else {
                    Timber.w("Cannot publish to [$topic] — MQTT client not connected")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to publish to topic: $topic")
            }
        }
    }

    private fun openConnection() {
        if (myDeviceId.isEmpty()) return

        _connectionState.value = ConnectionState.CONNECTING
        val clientId = "VibeApp_${myDeviceId.take(12)}_${System.currentTimeMillis() % 10000}"

        try {
            client = MqttAsyncClient(BROKER_URL, clientId, MemoryPersistence())
            
            val options = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = 10
                keepAliveInterval = 30
                isAutomaticReconnect = true
            }

            client?.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    Timber.d("MQTT connected to $serverURI (reconnect=$reconnect)")
                    _connectionState.value = ConnectionState.CONNECTED
                    // Re-subscribe to my device topic
                    subscribe("vibeapp/cmd/$myDeviceId")
                    subscribe("vibeapp/ack/$myDeviceId")
                }

                override fun connectionLost(cause: Throwable?) {
                    Timber.w(cause, "MQTT connection lost")
                    _connectionState.value = ConnectionState.RECONNECTING
                    scheduleReconnect()
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    if (topic != null && message != null) {
                        val payloadStr = String(message.payload, Charsets.UTF_8)
                        Timber.d("MQTT message arrived on [$topic]: $payloadStr")
                        scope.launch {
                            _incomingMessages.emit(MqttReceivedMessage(topic, payloadStr))
                        }
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    // Message delivered
                }
            })

            client?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Timber.d("MQTT connect request succeeded")
                    _connectionState.value = ConnectionState.CONNECTED
                    subscribe("vibeapp/cmd/$myDeviceId")
                    subscribe("vibeapp/ack/$myDeviceId")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Timber.e(exception, "MQTT connect request failed")
                    _connectionState.value = ConnectionState.RECONNECTING
                    scheduleReconnect()
                }
            })

        } catch (e: Exception) {
            Timber.e(e, "Error initializing MQTT client")
            _connectionState.value = ConnectionState.RECONNECTING
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (_connectionState.value != ConnectionState.CONNECTED) {
                Timber.d("Retrying MQTT connection...")
                openConnection()
            }
        }
    }
}
