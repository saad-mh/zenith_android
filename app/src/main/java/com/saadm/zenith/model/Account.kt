package com.saadm.zenith.model

import java.time.Instant
import java.util.UUID

enum class AccountType {
    CASH, BANK, CREDIT_CARD, WALLET, INVESTMENT
}

/**
 * The financial container a transaction is recorded against (bank, cash, wallet, credit card).
 * `startingBalance` is fixed at creation and never mutated by transaction posting —
 * current balance is always derived, never stored.
 */
data class Account(
    val id: UUID,
    val name: String,
    val type: AccountType,
    val currency: String,
    val startingBalance: Long,
    val isArchived: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)
