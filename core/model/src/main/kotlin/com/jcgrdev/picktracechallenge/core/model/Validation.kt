package com.jcgrdev.picktracechallenge.core.model

enum class ValidationError { WorkerIdMissing, BlockIdMissing, QuantityNotANumber, QuantityNotPositive }

sealed interface ValidationResult {
    data class Valid(val workerId: WorkerId, val blockId: BlockId, val quantity: Int) : ValidationResult
    data class Invalid(val errors: List<ValidationError>) : ValidationResult
}

sealed interface QuantityResult {
    data class Valid(val quantity: Int) : QuantityResult
    data class Invalid(val error: ValidationError) : QuantityResult
}

/** FR-006. Reports every error at once so the form can show them all. */
object FieldEventValidator {

    fun validate(draft: FieldEventDraft): ValidationResult {
        val workerId = draft.workerId.trim()
        val blockId = draft.blockId.trim()
        val quantity = validateQuantity(draft.quantity)
        val errors = buildList {
            if (workerId.isEmpty()) add(ValidationError.WorkerIdMissing)
            if (blockId.isEmpty()) add(ValidationError.BlockIdMissing)
            if (quantity is QuantityResult.Invalid) add(quantity.error)
        }
        return if (errors.isEmpty()) {
            ValidationResult.Valid(WorkerId(workerId), BlockId(blockId), (quantity as QuantityResult.Valid).quantity)
        } else {
            ValidationResult.Invalid(errors)
        }
    }

    fun validateQuantity(raw: String): QuantityResult {
        val quantity = raw.trim().toIntOrNull() ?: return QuantityResult.Invalid(ValidationError.QuantityNotANumber)
        return if (quantity >= 1) QuantityResult.Valid(quantity) else QuantityResult.Invalid(ValidationError.QuantityNotPositive)
    }
}
