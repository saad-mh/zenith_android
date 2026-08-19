package com.saadm.zenith.model

import java.time.LocalDate
import java.util.UUID

enum class RecurrenceFrequency {
    DAILY, WEEKLY, MONTHLY, YEARLY
}

/**
 * Template for generating recurring transactions. Generated transactions are
 * independent, mutable copies — editing one never rewrites the rule and vice versa.
 */
data class RecurrenceRule(
    val id: UUID,
    val frequency: RecurrenceFrequency,
    val interval: Int,
    val nextOccurrence: LocalDate,
    val endDate: LocalDate? = null,
    val templateAccountId: UUID,
    val templateCategoryId: UUID? = null,
    val templateAmount: Long,
    val templateDirection: TransactionDirection,
    val templateNote: String? = null,
    val isActive: Boolean,
)
