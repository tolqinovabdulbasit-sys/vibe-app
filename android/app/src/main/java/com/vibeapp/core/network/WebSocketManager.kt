package com.vibeapp.core.network

import com.vibeapp.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

@Singleton
class WebSocketManager @Inject constructor(
    private val json: Json
) {
    companion object {
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
        private const val RECONNECT_BASE_DELAY_MS = 1_000L
        private const val RECONNECT_MAX_DELAY_MS = 30_000L
        private const val MAX_RECONNECT_ATTEMPTS = 20
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // No timeout for WebSocket
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<WebSocketMessage>(replay = 0)
    val incomingMessages: SharedFlow<WebSocketMessage> = _incomingMessages.asSharedFlow()

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private var deviceId: String = ""
    private var authToken: String = ""

    // Firebase Realtime Database WebSocket URL (set after Firebase init)
    // Format: wss://<project-id>-default-rtdb.firebaseio.com/.json?auth=<token>
    // In production: replace with your actual Firebase Realtime DB URL
    private var wsUrl: String = ""

    fun configure(deviceId: String, authToken: String, wsUrl: String) {
        this.deviceId = deviceId
        this.authToken = authToken
        this.wsUrl = wsUrl
    }

    fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.CONNECTING) return

        reconnectAttempts = 0
        openConnection()
    }

    fun disconnect() {
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun sendCommand(command: VibrationCommand) {
        val msg = WebSocketMessage(
            type = "COMMAND",
            payload = json.encodeToString(command)
        )
        sendRaw(json.encodeToString(msg))
    }

    fun sendAck(ack: AckMessage) {
        val msg = WebSocketMessage(
            type = "ACK",
            payload = json.encodeToString(ack)
        )
        sendRaw(json.encodeToString(msg))
    }

    private fun sendRaw(text: String): Boolean {
        val ws = webSocket ?: return false
        return ws.send(text)
    }

    private fun openConnection() {
        if (wsUrl.isEmpty()) {
            Timber.w("WebSocket URL not configured yet")
            return
        }

        _connectionState.value = ConnectionState.CONNECTING

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("X-Device-Id", deviceId)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Timber.d("WebSocket connected")
                reconnectAttempts = 0
                _connectionState.value = ConnectionState.CONNECTED
                startHeartbeat()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch {
                    try {
                        val msg = json.decodeFromString<WebSocketMessage>(text)
                        _incomingMessages.emit(msg)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to parse WebSocket message: $text")
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Timber.e(t, "WebSocket failure (attempt $reconnectAttempts)")
                heartbeatJob?.cancel()
                _connectionState.value = ConnectionState.RECONNECTING
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Timber.d("WebSocket closed: $code $reason")
                heartbeatJob?.cancel()
                if (code != 1000) {
                    _connectionState.value = ConnectionState.RECONNECTING
                    scheduleReconnect()
                } else {
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            }
        })
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                val hb = HeartbeatMessage(deviceId = deviceId, timestamp = System.currentTimeMillis())
                val msg = WebSocketMessage(type = "HEARTBEAT", payload = json.encodeToString(hb))
                sendRaw(json.encodeToString(msg))
            }
        }
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Timber.w("Max reconnect attempts reached")
            _connectionState.value = ConnectionState.DISCONNECTED
            return
        }

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delay = minOf(
                RECONNECT_BASE_DELAY_MS * (1L shl reconnectAttempts.coerceAtMost(10)),
                RECONNECT_MAX_DELAY_MS
            )
            Timber.d("Reconnecting in ${delay}ms (attempt ${reconnectAttempts + 1})")
            delay(delay)
            reconnectAttempts++
            openConnection()
        }
    }
}
