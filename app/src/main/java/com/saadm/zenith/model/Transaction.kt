package com.saadm.zenith.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class TransactionDirection {
    INCOME, EXPENSE, TRANSFER
}

enum class SplitMethod {
    NONE, EQUAL, EXACT, PERCENTAGE
}

/**
 * The atomic financial event. `amount` is always positive — sign is derived
 * from `direction`, never stored. `categoryId` is null only for transfers.
 */
data class Transaction(
    val id: UUID,
    val accountId: UUID,
    val categoryId: UUID? = null,
    val amount: Long,
    val direction: TransactionDirection,
    val currency: String,
    val splitMethod: SplitMethod = SplitMethod.NONE,
    val occurredAt: LocalDate,
    val createdAt: Instant,
    val updatedAt: Instant,
    val note: String? = null,
    val transferPairId: UUID? = null,
    val recurrenceRuleId: UUID? = null,
    val deletedAt: Instant? = null,
) {
    /** Signed relative to the account this row belongs to; transfer sign depends on which leg this is. */
    fun signedAmount(transferIsOutgoing: Boolean = true): Long = when (direction) {
        TransactionDirection.INCOME -> amount
        TransactionDirection.EXPENSE -> -amount
        TransactionDirection.TRANSFER -> if (transferIsOutgoing) -amount else amount
    }
}
