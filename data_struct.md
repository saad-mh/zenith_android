# Finance Management App — Data Model

Inspired by [Dime](https://github.com/rafsoh/dimeApp), extended with shared/split transactions via lightweight Payees.

**Scope assumptions locked in:**
- Single-user ledger (one owner per instance of this schema; multi-user is a future extension via `linked_user_id`)
- Payees are lightweight contacts by default, optionally linkable to a real user account later
- Splits support `equal`, `exact`, and `percentage` methods
- All money stored as integer minor units (cents/paise), never floats
- Every mutable entity soft-deletes (`deleted_at`) if other rows reference it historically

**Open decision (not yet locked):** how transfers between two accounts are modeled — two linked `Transaction` rows (current default, described below) vs. one row with a `to_account_id`. Flagged again at the bottom.

---

## Entity-Relationship Overview

```
Account ──1───────*── Transaction ──*───────1── Category
                          │                         │
                          │ (self-ref: transfer)     └─* Category (subcategories, self-ref)
                          │
                          1
                          │
                          *
                    TransactionSplit ──*───1── Payee ──*───1── User (future, nullable link)

Budget ──*───1── Category

RecurrenceRule ──1───*── Transaction (traceability only)
```

---

## 1. Account

The financial container a transaction is recorded against (bank, cash, wallet, credit card).

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID (PK) | No | |
| `name` | TEXT | No | e.g. "HDFC Savings" |
| `type` | ENUM(`cash`,`bank`,`credit_card`,`wallet`,`investment`) | No | |
| `currency` | CHAR(3) | No | ISO 4217, e.g. `INR` |
| `starting_balance` | BIGINT | No | Minor units. Fixed at creation, never mutated by transaction posting |
| `is_archived` | BOOLEAN | No | Default `false`. Replaces delete for accounts with history |
| `created_at` | TIMESTAMPTZ | No | |
| `updated_at` | TIMESTAMPTZ | No | |

**Invariants**
- `starting_balance` is set once at creation and is never recalculated or overwritten by posting activity.

**Derived (never stored)**
```
current_balance(account) =
    starting_balance
    + Σ( transaction.signed_amount
         WHERE account_id = account.id AND deleted_at IS NULL )
```

---

## 2. Category

Hierarchical classification, self-referencing for subcategories (e.g. Food → Groceries, Dining).

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID (PK) | No | |
| `parent_id` | UUID (FK → `Category.id`) | Yes | `NULL` = top-level category |
| `name` | TEXT | No | |
| `kind` | ENUM(`income`,`expense`) | No | A category is one or the other, never both |
| `icon` | TEXT | Yes | Presentation only |
| `color` | TEXT | Yes | Presentation only |
| `sort_order` | INT | No | User-defined display order |
| `deleted_at` | TIMESTAMPTZ | Yes | Soft delete |

**Invariants**
- `parent_id` must not create a cycle.
- Depth is limited to 2 levels (category → subcategory), matching Dime's actual UX — enforce via application logic or a trigger, not unbounded recursion.

---

## 3. Payee

A lightweight, user-owned contact representing who a transaction is shared with. Not a login/account by default.

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID (PK) | No | |
| `owner_user_id` | UUID (FK → `User.id`) | No | The ledger owner this contact belongs to |
| `display_name` | TEXT | No | e.g. "Aditi", "Roommate — Raj" |
| `linked_user_id` | UUID (FK → `User.id`) | Yes | **Future hook**: set once this contact claims a real account. Historical `TransactionSplit` rows never need to change when this is set |
| `avatar_color` | TEXT | Yes | Presentation only |
| `deleted_at` | TIMESTAMPTZ | Yes | Soft delete — splits keep referencing the payee historically |

**Why `linked_user_id` is separate from `id`:** keeping Payee as its own row with a *pointer* to `User` means the FK target used everywhere else (`payee_id`) never changes — only what it resolves to gains an identity later.

**Derived (never stored)**
```
balance_with(payee) =
    Σ( split.owed_amount WHERE split.direction = 'they_owe_me' )
  − Σ( split.owed_amount WHERE split.direction = 'i_owe_them' )
```
*(See `TransactionSplit` below for how direction is actually represented — this is the conceptual formula; the concrete field is `owed_amount` signed relative to the owner's own row.)*

---

## 4. Transaction

The atomic financial event.

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID (PK) | No | |
| `account_id` | UUID (FK → `Account.id`) | No | |
| `category_id` | UUID (FK → `Category.id`) | Yes | Nullable only for `transfer` direction |
| `amount` | BIGINT | No | **Always positive**, minor units |
| `direction` | ENUM(`income`,`expense`,`transfer`) | No | Sign is derived from this — never store a signed amount |
| `currency` | CHAR(3) | No | Copied at creation time — see note below |
| `split_method` | ENUM(`none`,`equal`,`exact`,`percentage`) | No | Default `none`. Governs how `TransactionSplit` rows are interpreted |
| `occurred_at` | DATE | No | The real-world transaction date — user-editable |
| `created_at` | TIMESTAMPTZ | No | Row insertion time — immutable, audit trail |
| `updated_at` | TIMESTAMPTZ | No | |
| `note` | TEXT | Yes | |
| `transfer_pair_id` | UUID (FK → `Transaction.id`, self-ref) | Yes | Links the two legs of an account-to-account transfer |
| `recurrence_rule_id` | UUID (FK → `RecurrenceRule.id`) | Yes | Set if this instance was generated from a recurring template |
| `deleted_at` | TIMESTAMPTZ | Yes | Soft delete |

**Why `currency` is copied here and not just inferred from `Account`:** if the account's currency setting is ever edited later, historical transactions must not retroactively change value. This is point-in-time source data, not redundant derived data.

**Invariants**
- `amount > 0` always.
- If `direction = 'transfer'`, `transfer_pair_id` must point to exactly one reciprocal `Transaction` row.
- `category_id` is `NULL` only when `direction = 'transfer'`; required for `income`/`expense`.
- If `split_method != 'none'`, the sum of related `TransactionSplit.owed_amount` must equal `amount` (enforced at write time).

**Derived (never stored)**
```
signed_amount = CASE direction
    WHEN 'income'   THEN +amount
    WHEN 'expense'  THEN -amount
    WHEN 'transfer' THEN ±amount   -- sign depends on which leg this row is
END
```

---

## 5. TransactionSplit

Records how a single transaction is divided among people. This is the join entity that enables "shared transactions" without duplicating the Transaction itself.

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID (PK) | No | |
| `transaction_id` | UUID (FK → `Transaction.id`) | No | Compositional — cascade delete with the transaction |
| `payee_id` | UUID (FK → `Payee.id`) | Yes | `NULL` = the ledger owner's own share |
| `share_input` | NUMERIC | Yes | Raw user input. Exact amount if `split_method = 'exact'`, basis points (0–10000) if `percentage`, `NULL` if `equal` |
| `owed_amount` | BIGINT | No | **Frozen computed result** — see invariant below |

**Why `owed_amount` is stored instead of always derived:** splits are computed against the transaction's amount *at the time the split was made*. If the transaction is later edited, historical percentage/equal math must not silently reflow — that would rewrite what someone actually owed on a past date. This is the same category of exception as `Transaction.currency`: a frozen point-in-time calculation, not a live derivation.

**Invariants**
- `Σ(owed_amount)` across all splits for a given `transaction_id` must equal `Transaction.amount`.
- `split_method` lives on `Transaction`, not repeated per split row, so all splits for one transaction can never disagree about method.
- If `Transaction.amount` is edited after splits exist, splits must be explicitly re-entered — never silently auto-rescaled.

---

## 6. Budget

A spending cap over a category, per period.

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID (PK) | No | |
| `category_id` | UUID (FK → `Category.id`) | No | |
| `amount` | BIGINT | No | Cap, minor units |
| `period` | ENUM(`weekly`,`monthly`,`yearly`,`custom`) | No | |
| `start_date` | DATE | No | Anchor for period calculation |
| `end_date` | DATE | Yes | `NULL` = ongoing/recurring |
| `created_at` | TIMESTAMPTZ | No | |
| `updated_at` | TIMESTAMPTZ | No | |

**Invariants**
- Only one *active* budget per `category_id` per overlapping period — prevents ambiguity about which budget applies at query time.

**Derived (never stored — do not create a `BudgetPeriod` table with a stored `spent` column)**
```
spent(budget, period_instance) =
    Σ( transaction.amount
       WHERE transaction.category_id = budget.category_id
       AND transaction.direction = 'expense'
       AND transaction.occurred_at BETWEEN period_instance.start AND period_instance.end
       AND transaction.deleted_at IS NULL )
```
Period boundaries are computed from `period` + `start_date` at query time, not materialized — that's a caching decision to revisit later if performance demands it, not a modeling one.

---

## 7. RecurrenceRule

Template for generating recurring transactions.

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID (PK) | No | |
| `frequency` | ENUM(`daily`,`weekly`,`monthly`,`yearly`) | No | |
| `interval` | INT | No | Every N frequency-units |
| `next_occurrence` | DATE | No | Mutable — advances after each generation |
| `end_date` | DATE | Yes | |
| `template_account_id` | UUID (FK → `Account.id`) | No | |
| `template_category_id` | UUID (FK → `Category.id`) | Yes | |
| `template_amount` | BIGINT | No | |
| `template_direction` | ENUM(`income`,`expense`,`transfer`) | No | |
| `template_note` | TEXT | Yes | |
| `is_active` | BOOLEAN | No | |

**Invariants**
- Generated `Transaction` rows are independent, mutable copies. Editing a past generated transaction never rewrites the rule; editing the rule never retroactively rewrites past transactions.
- `Transaction.recurrence_rule_id` is for traceability/grouping only — not live inheritance.

---

## Cardinality Summary

| Relationship | Cardinality | Ownership behavior |
|---|---|---|
| Account → Transaction | 1 → * | Soft-delete account; never hard-delete while transactions reference it |
| Category → Transaction | 1 → * (0 allowed for transfers) | |
| Category → Category (`parent_id`) | 1 → * | Self-referential, depth-limited to 2 |
| Transaction → TransactionSplit | 1 → * | Compositional — cascade delete |
| Payee → TransactionSplit | 1 → * | Referential, not compositional — soft-delete payee, splits remain valid |
| Category → Budget | 1 → * (time-bounded) | |
| RecurrenceRule → Transaction | 1 → * | Traceability only, not compositional |
| Transaction → Transaction (`transfer_pair_id`) | 1 → 0..1 | Self-referential, symmetric |
| User → Payee | 1 → * | `owner_user_id` (required) |
| User → Payee | 1 → 0..1 per row | `linked_user_id` (optional, future) |

---

## Migration-Friendliness Notes

- All foreign keys reference UUIDs, never names — renaming a category or payee is a single-row update with zero cascades.
- `deleted_at`-style soft deletes allow adding "restore" functionality later without a schema change.
- `Payee.linked_user_id` is already in place for the future multi-user path — adding a `User`/auth table later populates this column without touching `TransactionSplit`.
- `Transaction.currency` (not just `Account.currency`) means multi-currency accounts are a non-breaking future addition.
- `split_method` lives on `Transaction`, not duplicated per split row — one less place for future migrations to introduce inconsistency.

---

## Open Question — Transfers

Two ways to model account-to-account transfers, not yet locked:

**(a) Two linked `Transaction` rows** (current default above) — one `expense`-like leg, one `income`-like leg, joined via `transfer_pair_id`. Fits the existing `direction` enum cleanly; keeps "amount per account" queries simple.

**(b) Single `Transaction` row** with both `account_id` and a `to_account_id`. More compact, but complicates the `signed_amount` derivation and makes `TransactionSplit`'s relationship ambiguous (which account's split is it?).

