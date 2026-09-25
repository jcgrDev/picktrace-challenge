package com.jcgrdev.picktracechallenge.feature.events.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcgrdev.picktracechallenge.core.data.FieldEventRepository
import com.jcgrdev.picktracechallenge.core.model.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** A view of the Room Flow; holds no copy of persisted data of its own (Principle I). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EventListViewModel @Inject constructor(repository: FieldEventRepository) : ViewModel() {

    private val filter = MutableStateFlow<SyncStatus?>(null)

    val uiState: StateFlow<EventListUiState> = filter
        .flatMapLatest { selected ->
            repository.observeEvents(selected).map { events ->
                EventListUiState(
                    filter = selected,
                    events = events.map {
                        EventRow(it.id.toString(), it.workerId.value, it.blockId.value, it.quantity, it.timestamp, it.status)
                    },
                    loading = false,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventListUiState())

    fun onFilterChange(status: SyncStatus?) {
        filter.value = status
    }
}
