package com.jcgrdev.picktracechallenge.feature.events.detail

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.jcgrdev.picktracechallenge.core.data.MutationResult
import com.jcgrdev.picktracechallenge.core.model.SyncStatus
import com.jcgrdev.picktracechallenge.core.model.ValidationError
import com.jcgrdev.picktracechallenge.core.testing.MainDispatcherRule
import com.jcgrdev.picktracechallenge.feature.events.FakeFieldEventRepository
import com.jcgrdev.picktracechallenge.feature.events.detail
import com.jcgrdev.picktracechallenge.feature.events.event
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class EventDetailViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val id = UUID(0, 7)
    private val repository = FakeFieldEventRepository().apply {
        details.value = mapOf(id to detail(event(7, SyncStatus.PENDING, quantity = 3)))
    }

    private fun viewModel() = EventDetailViewModel(SavedStateHandle(mapOf("id" to id.toString())), repository)

    @Test
    fun theDetailLoadsFromTheRepository() = runTest {
        viewModel().uiState.test {
            val loaded = expectMostRecentItemAfterIdle()
            assertEquals(3, loaded.detail!!.event.quantity)
            assertEquals(false, loaded.deleted)
        }
    }

    @Test
    fun editStartsWithTheCurrentQuantityAndCanBeCancelled() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            expectMostRecentItemAfterIdle()
            vm.onEdit()
            assertEquals("3", expectMostRecentItemAfterIdle().editingQuantity)
            vm.onCancelEdit()
            assertEquals(null, expectMostRecentItemAfterIdle().editingQuantity)
        }
    }

    @Test
    fun savingAnEditCallsTheRepositoryAndLeavesEditMode() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            expectMostRecentItemAfterIdle()
            vm.onEdit()
            vm.onQuantityChange("9")
            vm.onSaveEdit()
            val state = expectMostRecentItemAfterIdle()
            assertEquals(null, state.editingQuantity)
            assertEquals(listOf(id to "9"), repository.updates)
        }
    }

    @Test
    fun anInvalidEditShowsTheErrorAndStaysInEditMode() = runTest {
        repository.updateResult = MutationResult.Invalid(listOf(ValidationError.QuantityNotPositive))
        val vm = viewModel()
        vm.uiState.test {
            expectMostRecentItemAfterIdle()
            vm.onEdit()
            vm.onQuantityChange("0")
            vm.onSaveEdit()
            val state = expectMostRecentItemAfterIdle()
            assertEquals("0", state.editingQuantity)
            assertEquals(setOf(ValidationError.QuantityNotPositive), state.errors)
        }
    }

    @Test
    fun aReadOnlyRefusalIsReportedWithoutCorruptingTheState() = runTest {
        repository.updateResult = MutationResult.ReadOnlySynced
        val vm = viewModel()
        vm.uiState.test {
            val loaded = expectMostRecentItemAfterIdle()
            vm.onEdit()
            vm.onQuantityChange("9")
            vm.onSaveEdit()
            val state = expectMostRecentItemAfterIdle()
            assertEquals(UserMessage.ReadOnlySynced, state.message)
            assertEquals(loaded.detail, state.detail)
            assertEquals(null, state.editingQuantity)
            assertEquals(false, state.deleted)
            vm.onMessageShown()
            assertEquals(null, expectMostRecentItemAfterIdle().message)
        }
    }

    @Test
    fun aSuccessfulDeleteMarksTheScreenDeleted() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            expectMostRecentItemAfterIdle()
            vm.onDelete()
            assertEquals(true, expectMostRecentItemAfterIdle().deleted)
            assertEquals(listOf(id), repository.deletes)
        }
    }

    @Test
    fun theEventDisappearingMarksTheScreenDeleted() = runTest {
        viewModel().uiState.test {
            expectMostRecentItemAfterIdle()
            repository.details.value = emptyMap()
            assertEquals(true, expectMostRecentItemAfterIdle().deleted)
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<EventDetailUiState>.expectMostRecentItemAfterIdle(): EventDetailUiState {
        main.testDispatcher.scheduler.advanceUntilIdle()
        return expectMostRecentItem()
    }
}
