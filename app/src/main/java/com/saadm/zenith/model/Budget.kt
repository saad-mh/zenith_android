package com.saadm.zenith.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class BudgetPeriod {
    WEEKLY, MONTHLY, YEARLY, CUSTOM
}

/**
 * A spending cap over a category, per period. `spent` is always derived at
 * query time from transactions — never materialized on this model.
 */
data class Budget(
    val id: UUID,
    val categoryId: UUID,
    val amount: Long,
    val period: BudgetPeriod,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
