package com.jcgrdev.picktracechallenge.feature.events.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jcgrdev.picktracechallenge.core.model.SyncStatus
import com.jcgrdev.picktracechallenge.feature.events.detail
import com.jcgrdev.picktracechallenge.feature.events.event
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The action-enablement table in contracts/ui.md, plus the delete confirmation. */
@RunWith(AndroidJUnit4::class)
class EventDetailScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aPendingEventCanBeEditedAndDeleted() {
        compose.setContent { EventDetailScreen(EventDetailUiState(detail = detail(event(1, SyncStatus.PENDING), attempts = 2))) }
        compose.onNodeWithText("Edit quantity").assertIsEnabled()
        compose.onNodeWithText("Delete").assertIsEnabled()
        compose.onNodeWithText("Delivery attempts: 2").assertIsDisplayed()
        compose.onNodeWithText("Pending").assertIsDisplayed()
    }

    @Test
    fun anInFlightEventShowsSyncingNowAndDisablesActions() {
        compose.setContent { EventDetailScreen(EventDetailUiState(detail = detail(event(1, SyncStatus.PENDING), inFlight = true))) }
        compose.onNodeWithText("Syncing now, try again when it finishes").assertIsDisplayed()
        compose.onNodeWithText("Edit quantity").assertIsNotEnabled()
        compose.onNodeWithText("Delete").assertIsNotEnabled()
    }

    @Test
    fun aSyncedEventIsReadOnly() {
        compose.setContent { EventDetailScreen(EventDetailUiState(detail = detail(event(1, SyncStatus.SYNCED)))) }
        compose.onNodeWithText("Synced events are read-only").assertIsDisplayed()
        compose.onNodeWithText("Edit quantity").assertIsNotEnabled()
        compose.onNodeWithText("Delete").assertIsNotEnabled()
    }

    @Test
    fun deleteAsksForConfirmationFirst() {
        var deletes = 0
        compose.setContent {
            EventDetailScreen(EventDetailUiState(detail = detail(event(1, SyncStatus.PENDING))), onDelete = { deletes++ })
        }
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete this event?").assertIsDisplayed()
        assertEquals(0, deletes)
        compose.onNodeWithText("Delete event").performClick()
        assertEquals(1, deletes)
    }

    @Test
    fun editModeShowsTheQuantityFieldAndItsError() {
        compose.setContent {
            EventDetailScreen(
                EventDetailUiState(
                    detail = detail(event(1, SyncStatus.PENDING)),
                    editingQuantity = "0",
                    errors = setOf(com.jcgrdev.picktracechallenge.core.model.ValidationError.QuantityNotPositive),
                ),
            )
        }
        compose.onNodeWithText("Quantity must be at least 1").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
    }
}
