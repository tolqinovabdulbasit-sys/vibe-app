package com.vibeapp.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibeapp.core.crypto.DeviceIdentityManager
import com.vibeapp.core.network.ConnectionState
import com.vibeapp.core.network.MqttManager
import com.vibeapp.core.vibration.VibrationEngine
import com.vibeapp.data.model.*
import com.vibeapp.data.repository.CommandRepository
import com.vibeapp.data.repository.HistoryEntry
import com.vibeapp.data.repository.PairedDevice
import com.vibeapp.data.repository.PairingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

data class MainUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val selectedDeviceIds: Set<String> = emptySet(),
    val pairedDevices: List<PairedDevice> = emptyList(),
    val lastCommandStatus: Map<String, DeliveryStatus> = emptyMap(), // commandId → status
    val lastStatusMessage: String = "",
    val isManualActive: Boolean = false,
    val history: List<HistoryEntry> = emptyList(),
    val mainButtons: List<MainButton> = defaultMainButtons(),
    val sideButtons: List<SideButton> = defaultSideButtons()
)

data class MainButton(
    val id: Int,       // 1-4
    val label: String,
    val patternSlot: Int
)

data class SideButton(
    val id: Int,       // 11-16
    val label: String,
    val patternSlot: Int
)

fun defaultMainButtons() = listOf(
    MainButton(1, "Да", 1),
    MainButton(2, "Нет", 2),
    MainButton(3, "Позвони", 3),
    MainButton(4, "Срочно", 9)
)

fun defaultSideButtons() = listOf(
    SideButton(11, "·", 1),
    SideButton(12, "—", 2),
    SideButton(13, "··", 3),
    SideButton(14, "—·", 4),
    SideButton(15, "·—", 5),
    SideButton(16, "···", 6)
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val webSocketManager: WebSocketManager,
    private val commandRepository: CommandRepository,
    private val pairingRepository: PairingRepository,
    private val deviceIdentityManager: DeviceIdentityManager,
    private val vibrationEngine: VibrationEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // Track per-command delivery statuses
    private val deliveryStatuses = mutableMapOf<String, MutableStateFlow<DeliveryStatus>>()

    init {
        observeConnectionState()
        observePairedDevices()
        observeHistory()
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            mqttManager.connectionState.collectLatest { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
    }

    private fun observePairedDevices() {
        viewModelScope.launch {
            pairingRepository.getActivePairingsFlow().collectLatest { devices ->
                _uiState.update { state ->
                    val selected = if (state.selectedDeviceIds.isEmpty() && devices.isNotEmpty()) {
                        setOf(devices.first().deviceId)
                    } else {
                        state.selectedDeviceIds
                    }
                    state.copy(pairedDevices = devices, selectedDeviceIds = selected)
                }
            }
        }
    }

    private fun observeHistory() {
        viewModelScope.launch {
            commandRepository.getHistoryFlow().collectLatest { history ->
                _uiState.update { it.copy(history = history) }
            }
        }
    }

    fun selectDevice(deviceId: String, multiSelect: Boolean = false) {
        _uiState.update { state ->
            val newSelection = if (multiSelect) {
                if (state.selectedDeviceIds.contains(deviceId)) {
                    state.selectedDeviceIds - deviceId
                } else {
                    state.selectedDeviceIds + deviceId
                }
            } else {
                setOf(deviceId)
            }
            state.copy(selectedDeviceIds = newSelection.ifEmpty { setOf(deviceId) })
        }
    }

    fun sendPattern(patternSlot: Int, patternName: String) {
        val targets = _uiState.value.selectedDeviceIds
        if (targets.isEmpty()) return

        viewModelScope.launch {
            val myDeviceId = deviceIdentityManager.getOrCreateDeviceId()
            val sequence = System.currentTimeMillis()

            targets.forEach { targetDeviceId ->
                val commandId = UUID.randomUUID().toString()
                val command = VibrationCommand(
                    commandId = commandId,
                    sourceDeviceId = myDeviceId,
                    targetDeviceId = targetDeviceId,
                    commandType = CommandType.PATTERN,
                    vibrationPatternSlot = patternSlot,
                    vibrationPatternName = patternName,
                    timestamp = System.currentTimeMillis(),
                    sequence = sequence
                )

                _uiState.update { it.copy(lastStatusMessage = "Sending...") }
                commandRepository.saveOutgoingCommand(command)
                mqttManager.publish("vibeapp/cmd/$targetDeviceId", json.encodeToString(command))
            }
        }
    }

    fun startManualVibration() {
        val targets = _uiState.value.selectedDeviceIds
        if (targets.isEmpty() || _uiState.value.isManualActive) return

        _uiState.update { it.copy(isManualActive = true) }

        viewModelScope.launch {
            val myDeviceId = deviceIdentityManager.getOrCreateDeviceId()

            targets.forEach { targetDeviceId ->
                val commandId = UUID.randomUUID().toString()
                val command = VibrationCommand(
                    commandId = commandId,
                    sourceDeviceId = myDeviceId,
                    targetDeviceId = targetDeviceId,
                    commandType = CommandType.MANUAL_START,
                    vibrationPatternSlot = 0,
                    vibrationPatternName = "Manual",
                    timestamp = System.currentTimeMillis(),
                    sequence = System.currentTimeMillis()
                )
                commandRepository.saveOutgoingCommand(command)
                mqttManager.publish("vibeapp/cmd/$targetDeviceId", json.encodeToString(command))
            }
        }
    }

    fun stopManualVibration() {
        if (!_uiState.value.isManualActive) return
        _uiState.update { it.copy(isManualActive = false) }

        viewModelScope.launch {
            val myDeviceId = deviceIdentityManager.getOrCreateDeviceId()

            _uiState.value.selectedDeviceIds.forEach { targetDeviceId ->
                val commandId = UUID.randomUUID().toString()
                val command = VibrationCommand(
                    commandId = commandId,
                    sourceDeviceId = myDeviceId,
                    targetDeviceId = targetDeviceId,
                    commandType = CommandType.MANUAL_STOP,
                    vibrationPatternSlot = 0,
                    vibrationPatternName = "Manual Stop",
                    timestamp = System.currentTimeMillis(),
                    sequence = System.currentTimeMillis()
                )
                commandRepository.saveOutgoingCommand(command)
                mqttManager.publish("vibeapp/cmd/$targetDeviceId", json.encodeToString(command))
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            commandRepository.clearHistory()
        }
    }

    fun updateMainButtonLabel(buttonId: Int, newLabel: String) {
        _uiState.update { state ->
            state.copy(
                mainButtons = state.mainButtons.map {
                    if (it.id == buttonId) it.copy(label = newLabel) else it
                }
            )
        }
    }
}
