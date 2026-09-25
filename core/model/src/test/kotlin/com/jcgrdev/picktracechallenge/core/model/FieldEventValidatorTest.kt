package com.jcgrdev.picktracechallenge.core.model

import com.jcgrdev.picktracechallenge.core.model.ValidationError.BlockIdMissing
import com.jcgrdev.picktracechallenge.core.model.ValidationError.QuantityNotANumber
import com.jcgrdev.picktracechallenge.core.model.ValidationError.QuantityNotPositive
import com.jcgrdev.picktracechallenge.core.model.ValidationError.WorkerIdMissing
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class FieldEventValidatorTest {

    private val at = Instant.parse("2025-06-10T08:32:00Z")

    private fun draft(worker: String = "w_001", block: String = "block_42", quantity: String = "3") =
        FieldEventDraft(worker, block, quantity, at)

    private fun errorsOf(draft: FieldEventDraft) =
        (FieldEventValidator.validate(draft) as ValidationResult.Invalid).errors

    @Test
    fun `a complete draft is valid with typed ids`() {
        assertEquals(
            ValidationResult.Valid(WorkerId("w_001"), BlockId("block_42"), 3),
            FieldEventValidator.validate(draft()),
        )
    }

    @Test
    fun `ids are stored trimmed`() {
        assertEquals(
            ValidationResult.Valid(WorkerId("w_001"), BlockId("block_42"), 3),
            FieldEventValidator.validate(draft(worker = "  w_001 ", block = "\tblock_42 ", quantity = " 3 ")),
        )
    }

    @Test
    fun `blank worker id is rejected`() {
        assertEquals(listOf(WorkerIdMissing), errorsOf(draft(worker = "   ")))
    }

    @Test
    fun `blank block id is rejected`() {
        assertEquals(listOf(BlockIdMissing), errorsOf(draft(block = "")))
    }

    @Test
    fun `non-numeric quantity is rejected`() {
        assertEquals(listOf(QuantityNotANumber), errorsOf(draft(quantity = "three")))
        assertEquals(listOf(QuantityNotANumber), errorsOf(draft(quantity = "")))
        assertEquals(listOf(QuantityNotANumber), errorsOf(draft(quantity = "2.5")))
        assertEquals(listOf(QuantityNotANumber), errorsOf(draft(quantity = "99999999999")))
    }

    @Test
    fun `zero and negative quantities are rejected`() {
        assertEquals(listOf(QuantityNotPositive), errorsOf(draft(quantity = "0")))
        assertEquals(listOf(QuantityNotPositive), errorsOf(draft(quantity = "-4")))
    }

    @Test
    fun `every error is reported at once`() {
        assertEquals(
            listOf(WorkerIdMissing, BlockIdMissing, QuantityNotPositive),
            errorsOf(draft(worker = "", block = " ", quantity = "0")),
        )
    }

    @Test
    fun `validateQuantity checks the quantity alone`() {
        assertEquals(QuantityResult.Valid(12), FieldEventValidator.validateQuantity(" 12 "))
        assertEquals(QuantityResult.Invalid(QuantityNotANumber), FieldEventValidator.validateQuantity("x"))
        assertEquals(QuantityResult.Invalid(QuantityNotPositive), FieldEventValidator.validateQuantity("0"))
    }
}
