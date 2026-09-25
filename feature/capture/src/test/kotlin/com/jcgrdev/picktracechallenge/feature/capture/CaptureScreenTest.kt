package com.jcgrdev.picktracechallenge.feature.capture

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jcgrdev.picktracechallenge.core.model.ValidationError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class CaptureScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val t0 = Instant.parse("2025-06-10T08:32:00Z")

    @Test
    fun theThreeFieldsAndTheTimestampAreShown() {
        compose.setContent { CaptureScreen(state = CaptureUiState(timestamp = t0)) }
        compose.onNodeWithText("Worker ID").assertIsDisplayed()
        compose.onNodeWithText("Block ID").assertIsDisplayed()
        compose.onNodeWithText("Quantity").assertIsDisplayed()
        compose.onNodeWithText("Recorded at", substring = true).assertIsDisplayed()
    }

    @Test
    fun eachErrorIsShownUnderItsField() {
        val errors = setOf(ValidationError.WorkerIdMissing, ValidationError.BlockIdMissing, ValidationError.QuantityNotANumber)
        compose.setContent { CaptureScreen(state = CaptureUiState(timestamp = t0, errors = errors)) }
        compose.onNodeWithText("Enter a worker ID").assertIsDisplayed()
        compose.onNodeWithText("Enter a block ID").assertIsDisplayed()
        compose.onNodeWithText("Enter a whole number").assertIsDisplayed()
    }

    @Test
    fun typingAndSavingCallBackIntoTheViewModel() {
        var quantity = ""
        var saves = 0
        compose.setContent {
            CaptureScreen(
                state = CaptureUiState(timestamp = t0),
                onQuantityChange = { quantity = it },
                onSave = { saves++ },
            )
        }
        compose.onNodeWithText("Quantity").performTextInput("7")
        compose.onNodeWithText("Save").performClick()
        assertEquals("7", quantity)
        assertEquals(1, saves)
    }
}
