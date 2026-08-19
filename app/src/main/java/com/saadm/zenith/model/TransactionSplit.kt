package com.saadm.zenith.model

import java.util.UUID

/**
 * Records how a single transaction is divided among people. `owedAmount` is a
 * frozen computed result — it must not silently reflow if the parent transaction
 * is edited later. `payeeId` null means the ledger owner's own share.
 */
data class TransactionSplit(
    val id: UUID,
    val transactionId: UUID,
    val payeeId: UUID? = null,
    val shareInput: Double? = null,
    val owedAmount: Long,
)
