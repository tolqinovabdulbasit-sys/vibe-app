package com.vibeapp.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibeapp.data.repository.PairingRepository
import com.vibeapp.data.repository.PairingResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

sealed class PairingUiState {
    object Idle : PairingUiState()
    object GeneratingCode : PairingUiState()
    data class ShowingCode(val code: String, val expiresInSeconds: Int) : PairingUiState()
    object EnteringCode : PairingUiState()
    object Verifying : PairingUiState()
    data class Success(val remoteAlias: String) : PairingUiState()
    data class Error(val message: String) : PairingUiState()
    object ExpiredCode : PairingUiState()
    object InvalidCode : PairingUiState()
}

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val pairingRepository: PairingRepository
) : ViewModel() {

    private val _state = MutableStateFlow<PairingUiState>(PairingUiState.Idle)
    val state: StateFlow<PairingUiState> = _state.asStateFlow()

    private var countdownJob: Job? = null
    private val CODE_TTL_SECONDS = 15 * 60

    fun generateCode() {
        viewModelScope.launch {
            _state.value = PairingUiState.GeneratingCode
            try {
                val code = pairingRepository.generatePairingCode()
                _state.value = PairingUiState.ShowingCode(code, CODE_TTL_SECONDS)
                startCountdown(code)
            } catch (e: Exception) {
                _state.value = PairingUiState.Error("Failed to generate code: ${e.message}")
            }
        }
    }

    fun enterCode(code: String) {
        if (code.length < 6) return
        viewModelScope.launch {
            _state.value = PairingUiState.Verifying
            when (val result = pairingRepository.consumePairingCode(code.uppercase().trim())) {
                is PairingResult.Success -> _state.value = PairingUiState.Success(result.remoteAlias)
                is PairingResult.InvalidCode -> _state.value = PairingUiState.InvalidCode
                is PairingResult.ExpiredCode -> _state.value = PairingUiState.ExpiredCode
                is PairingResult.AlreadyUsed -> _state.value = PairingUiState.Error("Code already used")
                is PairingResult.Error -> _state.value = PairingUiState.Error(result.message)
            }
        }
    }

    fun reset() {
        countdownJob?.cancel()
        _state.value = PairingUiState.Idle
    }

    private fun startCountdown(code: String) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = CODE_TTL_SECONDS
            while (remaining > 0 && isActive) {
                delay(1000)
                remaining--
                val current = _state.value
                if (current is PairingUiState.ShowingCode) {
                    _state.value = current.copy(expiresInSeconds = remaining)
                }
            }
            if (_state.value is PairingUiState.ShowingCode) {
                _state.value = PairingUiState.ExpiredCode
            }
        }
    }
}
