package com.vibeapp.ui.settings.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibeapp.data.repository.PairedDevice
import com.vibeapp.data.repository.PairingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val pairingRepository: PairingRepository
) : ViewModel() {

    val pairedDevices: StateFlow<List<PairedDevice>> =
        pairingRepository.getActivePairingsFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun renameDevice(deviceId: String, newAlias: String) {
        viewModelScope.launch {
            pairingRepository.renameDevice(deviceId, newAlias)
        }
    }

    fun removeDevice(deviceId: String) {
        viewModelScope.launch {
            pairingRepository.removeDevice(deviceId)
        }
    }
}
