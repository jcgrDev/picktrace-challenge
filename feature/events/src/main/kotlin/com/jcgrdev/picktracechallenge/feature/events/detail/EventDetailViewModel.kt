package com.jcgrdev.picktracechallenge.feature.events.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jcgrdev.picktracechallenge.core.data.FieldEventRepository
import com.jcgrdev.picktracechallenge.core.data.MutationResult
import com.jcgrdev.picktracechallenge.feature.events.navigation.EventDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * The event comes from its Room Flow; the ViewModel only holds unsaved edit input and one-shot
 * messages (Principle I). Every mutation goes through the repository, which enforces the rules.
 */
@HiltViewModel
class EventDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FieldEventRepository,
) : ViewModel() {

    private val id: UUID = UUID.fromString(savedStateHandle.toRoute<EventDetailRoute>().id)

    private data class Local(
        val editingQuantity: String? = null,
        val errors: Set<com.jcgrdev.picktracechallenge.core.model.ValidationError> = emptySet(),
        val message: UserMessage? = null,
        val deletedHere: Boolean = false,
    )

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<EventDetailUiState> =
        combine(repository.observeEvent(id), local) { detail, l ->
            EventDetailUiState(
                detail = detail,
                editingQuantity = l.editingQuantity,
                errors = l.errors,
                message = l.message,
                deleted = l.deletedHere || detail == null,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventDetailUiState())

    fun onEdit() {
        val quantity = uiState.value.detail?.event?.quantity ?: return
        local.update { it.copy(editingQuantity = quantity.toString(), errors = emptySet()) }
    }

    fun onQuantityChange(value: String) = local.update { it.copy(editingQuantity = value, errors = emptySet()) }

    fun onCancelEdit() = local.update { it.copy(editingQuantity = null, errors = emptySet()) }

    fun onSaveEdit() {
        val quantity = local.value.editingQuantity ?: return
        viewModelScope.launch {
            when (val result = repository.updateQuantity(id, quantity)) {
                MutationResult.Success -> local.update { it.copy(editingQuantity = null, errors = emptySet()) }
                is MutationResult.Invalid -> local.update { it.copy(errors = result.errors.toSet()) }
                else -> refused(result)
            }
        }
    }

    fun onDelete() {
        viewModelScope.launch {
            when (val result = repository.delete(id)) {
                MutationResult.Success -> local.update { it.copy(deletedHere = true) }
                else -> refused(result)
            }
        }
    }

    fun onMessageShown() = local.update { it.copy(message = null) }

    private fun refused(result: MutationResult) = local.update {
        when (result) {
            MutationResult.ReadOnlySynced -> it.copy(editingQuantity = null, message = UserMessage.ReadOnlySynced)
            MutationResult.InFlight -> it.copy(editingQuantity = null, message = UserMessage.InFlight)
            MutationResult.StorageFull -> it.copy(message = UserMessage.StorageFull)
            MutationResult.NotFound -> it.copy(deletedHere = true)
            else -> it
        }
    }
}
