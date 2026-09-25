package com.jcgrdev.picktracechallenge.feature.events.list

import app.cash.turbine.test
import com.jcgrdev.picktracechallenge.core.model.SyncStatus
import com.jcgrdev.picktracechallenge.core.testing.MainDispatcherRule
import com.jcgrdev.picktracechallenge.feature.events.FakeFieldEventRepository
import com.jcgrdev.picktracechallenge.feature.events.event
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** US4-1, US4-2. */
class EventListViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val repository = FakeFieldEventRepository().apply {
        events.value = listOf(
            event(4, SyncStatus.PENDING), event(3, SyncStatus.FAILED), event(2, SyncStatus.SYNCED), event(1, SyncStatus.PENDING),
        )
    }

    private fun ids(state: EventListUiState) = state.events.map { it.id.takeLast(1) }

    @Test
    fun everyEventIsShownNewestFirstWithItsFields() = runTest {
        EventListViewModel(repository).uiState.test {
            assertEquals(true, awaitItem().loading)
            val state = awaitItem()
            assertEquals(false, state.loading)
            assertEquals(listOf("4", "3", "2", "1"), ids(state))
            val row = state.events.first()
            assertEquals("w_004", row.workerId)
            assertEquals("block_4", row.blockId)
            assertEquals(4, row.quantity)
            assertEquals(SyncStatus.PENDING, row.status)
        }
    }

    @Test
    fun filteringShowsOnlyMatchingStatuses() = runTest {
        val vm = EventListViewModel(repository)
        vm.uiState.test {
            skipItems(2)
            vm.onFilterChange(SyncStatus.PENDING)
            assertEquals(SyncStatus.PENDING, expectMostRecentItemAfter { it.filter == SyncStatus.PENDING && ids(it) == listOf("4", "1") }.filter)
            vm.onFilterChange(SyncStatus.SYNCED)
            expectMostRecentItemAfter { ids(it) == listOf("2") }
            vm.onFilterChange(SyncStatus.FAILED)
            expectMostRecentItemAfter { ids(it) == listOf("3") }
            vm.onFilterChange(null)
            expectMostRecentItemAfter { ids(it) == listOf("4", "3", "2", "1") }
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<EventListUiState>.expectMostRecentItemAfter(
        predicate: (EventListUiState) -> Boolean,
    ): EventListUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }
}
