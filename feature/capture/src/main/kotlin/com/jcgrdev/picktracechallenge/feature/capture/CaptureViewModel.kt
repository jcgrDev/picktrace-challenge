package com.jcgrdev.picktracechallenge.feature.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcgrdev.picktracechallenge.core.data.FieldEventRepository
import com.jcgrdev.picktracechallenge.core.data.RecordResult
import com.jcgrdev.picktracechallenge.core.model.FieldEventDraft
import com.jcgrdev.picktracechallenge.core.model.FieldEventValidator
import com.jcgrdev.picktracechallenge.core.model.ValidationError
import com.jcgrdev.picktracechallenge.core.model.ValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/** Holds only unsaved form input; everything persisted is read from Room elsewhere (Principle I). */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val repository: FieldEventRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState(timestamp = clock.instant()))
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    fun onWorkerIdChange(value: String) =
        _uiState.update { it.copy(workerId = value, errors = it.errors - ValidationError.WorkerIdMissing) }

    fun onBlockIdChange(value: String) =
        _uiState.update { it.copy(blockId = value, errors = it.errors - ValidationError.BlockIdMissing) }

    fun onQuantityChange(value: String) = _uiState.update {
        it.copy(quantity = value, errors = it.errors - ValidationError.QuantityNotANumber - ValidationError.QuantityNotPositive)
    }

    fun onSave() {
        val state = _uiState.value
        if (state.saving) return
        val draft = FieldEventDraft(state.workerId, state.blockId, state.quantity, state.timestamp)
        val validation = FieldEventValidator.validate(draft)
        if (validation is ValidationResult.Invalid) {
            _uiState.update { it.copy(errors = validation.errors.toSet()) }
            return
        }
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val result = repository.record(draft)) {
                is RecordResult.Recorded -> _uiState.update {
                    it.copy(quantity = "", timestamp = clock.instant(), saving = false, message = UserMessage.Saved)
                }
                is RecordResult.Invalid -> _uiState.update { it.copy(errors = result.errors.toSet(), saving = false) }
                RecordResult.StorageFull -> _uiState.update { it.copy(saving = false, message = UserMessage.StorageFull) }
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
}
