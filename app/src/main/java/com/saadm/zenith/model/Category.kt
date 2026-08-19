package com.saadm.zenith.model

import java.time.Instant
import java.util.UUID

enum class CategoryKind {
    INCOME, EXPENSE
}

/**
 * Hierarchical classification, self-referencing for subcategories
 * (e.g. Food -> Groceries, Dining). Depth is limited to 2 levels.
 */
data class Category(
    val id: UUID,
    val parentId: UUID? = null,
    val name: String,
    val kind: CategoryKind,
    val icon: String? = null,
    val color: String? = null,
    val sortOrder: Int,
    val deletedAt: Instant? = null,
)
