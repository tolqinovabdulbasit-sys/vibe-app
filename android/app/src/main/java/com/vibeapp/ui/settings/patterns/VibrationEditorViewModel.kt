package com.vibeapp.ui.settings.patterns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibeapp.core.db.dao.VibrationPatternDao
import com.vibeapp.core.db.entity.VibrationPatternEntity
import com.vibeapp.core.vibration.VibrationEngine
import com.vibeapp.data.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class PatternEditorState(
    val patterns: List<VibrationPattern> = emptyList(),
    val editingSlot: Int? = null,
    val isSaving: Boolean = false
)

@HiltViewModel
class VibrationEditorViewModel @Inject constructor(
    private val patternDao: VibrationPatternDao,
    private val vibrationEngine: VibrationEngine,
    private val json: Json
) : ViewModel() {

    private val _state = MutableStateFlow(PatternEditorState())
    val state: StateFlow<PatternEditorState> = _state.asStateFlow()

    init {
        loadPatterns()
    }

    private fun loadPatterns() {
        viewModelScope.launch {
            patternDao.getAllFlow().collectLatest { entities ->
                val patterns = entities.map { entity ->
                    val steps = try {
                        json.decodeFromString<List<PatternStep>>(entity.patternData)
                    } catch (e: Exception) { emptyList() }
                    VibrationPattern(
                        slot = entity.slot,
                        name = entity.name,
                        steps = steps,
                        enabled = entity.enabled
                    )
                }

                // If DB is empty, seed defaults
                if (patterns.isEmpty()) {
                    seedDefaultPatterns()
                } else {
                    _state.update { it.copy(patterns = patterns) }
                }
            }
        }
    }

    private suspend fun seedDefaultPatterns() {
        val entities = DefaultPatterns.all.map { pattern ->
            VibrationPatternEntity(
                slot = pattern.slot,
                name = pattern.name,
                enabled = pattern.enabled,
                patternData = json.encodeToString(pattern.steps)
            )
        }
        patternDao.insertAll(entities)
    }

    fun toggleEnabled(slot: Int, enabled: Boolean) {
        viewModelScope.launch {
            patternDao.updateEnabled(slot, enabled)
        }
    }

    fun updateName(slot: Int, name: String) {
        viewModelScope.launch {
            patternDao.updateName(slot, name)
        }
    }

    fun updatePattern(slot: Int, steps: List<PatternStep>) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            patternDao.updatePatternData(slot, json.encodeToString(steps))
            _state.update { it.copy(isSaving = false, editingSlot = null) }
        }
    }

    fun testPattern(pattern: VibrationPattern) {
        vibrationEngine.playPattern(pattern)
    }

    fun startEditing(slot: Int) {
        _state.update { it.copy(editingSlot = slot) }
    }

    fun cancelEditing() {
        _state.update { it.copy(editingSlot = null) }
    }
}
