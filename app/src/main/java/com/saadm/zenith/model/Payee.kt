package com.saadm.zenith.model

import java.time.Instant
import java.util.UUID

/**
 * A lightweight, user-owned contact representing who a transaction is shared with.
 * Not a login/account by default; `linkedUserId` is a future hook for when the
 * contact claims a real account.
 */
data class Payee(
    val id: UUID,
    val ownerUserId: UUID,
    val displayName: String,
    val linkedUserId: UUID? = null,
    val avatarColor: String? = null,
    val deletedAt: Instant? = null,
)
