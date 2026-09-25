package com.jcgrdev.picktracechallenge.feature.capture

import app.cash.turbine.test
import com.jcgrdev.picktracechallenge.core.data.RecordResult
import com.jcgrdev.picktracechallenge.core.model.FieldEventDraft
import com.jcgrdev.picktracechallenge.core.model.ValidationError
import com.jcgrdev.picktracechallenge.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class CaptureViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private class SteppingClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?) = this
        override fun instant(): Instant = now
    }

    private val t0 = Instant.parse("2025-06-10T08:32:00Z")
    private val clock = SteppingClock(t0)
    private val repository = FakeFieldEventRepository()
    private fun viewModel() = CaptureViewModel(repository, clock)

    @Test
    fun fieldChangesUpdateTheState() = runTest {
        val vm = viewModel()
        vm.onWorkerIdChange("w_001")
        vm.onBlockIdChange("block_42")
        vm.onQuantityChange("3")
        val state = vm.uiState.value
        assertEquals("w_001", state.workerId)
        assertEquals("block_42", state.blockId)
        assertEquals("3", state.quantity)
        assertEquals(t0, state.timestamp)
    }

    @Test
    fun invalidInputShowsEveryErrorAndIsNotRecorded() = runTest {
        val vm = viewModel()
        vm.onQuantityChange("0")
        vm.onSave()
        advanceUntilIdle()

        assertEquals(
            setOf(ValidationError.WorkerIdMissing, ValidationError.BlockIdMissing, ValidationError.QuantityNotPositive),
            vm.uiState.value.errors,
        )
        assertEquals(emptyList<FieldEventDraft>(), repository.recorded)
    }

    @Test
    fun editingAFieldClearsItsError() = runTest {
        val vm = viewModel()
        vm.onSave()
        advanceUntilIdle()
        vm.onWorkerIdChange("w_001")
        assertTrue(ValidationError.WorkerIdMissing !in vm.uiState.value.errors)
        assertTrue(ValidationError.BlockIdMissing in vm.uiState.value.errors)
    }

    @Test
    fun successfulSaveKeepsTheIdsClearsQuantityAndRefreshesTheTimestamp() = runTest {
        val vm = viewModel()
        vm.onWorkerIdChange("w_001")
        vm.onBlockIdChange("block_42")
        vm.onQuantityChange("3")

        vm.uiState.test {
            assertEquals(null, awaitItem().message)
            clock.now = t0.plusSeconds(60)
            vm.onSave()
            val saved = expectMostRecentItemAfterIdle()
            assertEquals("w_001", saved.workerId)
            assertEquals("block_42", saved.blockId)
            assertEquals("", saved.quantity)
            assertEquals(t0.plusSeconds(60), saved.timestamp)
            assertEquals(UserMessage.Saved, saved.message)
            assertEquals(false, saved.saving)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(FieldEventDraft("w_001", "block_42", "3", t0)), repository.recorded)
    }

    @Test
    fun storageFullIsReportedAndTheInputIsKept() = runTest {
        repository.nextResult = RecordResult.StorageFull
        val vm = viewModel()
        vm.onWorkerIdChange("w_001")
        vm.onBlockIdChange("block_42")
        vm.onQuantityChange("3")
        vm.onSave()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(UserMessage.StorageFull, state.message)
        assertEquals("3", state.quantity)
    }

    @Test
    fun aShownMessageIsCleared() = runTest {
        val vm = viewModel()
        vm.onWorkerIdChange("w_001")
        vm.onBlockIdChange("block_42")
        vm.onQuantityChange("3")
        vm.onSave()
        advanceUntilIdle()
        vm.onMessageShown()
        assertEquals(null, vm.uiState.value.message)
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<CaptureUiState>.expectMostRecentItemAfterIdle(): CaptureUiState {
        main.testDispatcher.scheduler.advanceUntilIdle()
        return expectMostRecentItem()
    }
}
