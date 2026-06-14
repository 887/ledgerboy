package com.eight87.ledgerboy.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * C.3 — the ledger schema.
 *
 * Money is stored as two columns — `amountMinor: Long` (integer minor
 * units) + `currency: String` (ISO-4217) — rather than one opaque
 * converted column, so `WHERE amountMinor > ?` style queries keep their
 * power. The repository maps those columns to/from the [com.eight87.ledgerboy.data.money.Money]
 * value type at the boundary.
 *
 * Time is epoch-millis Longs throughout (no converters, locale-free).
 */

/** A bank / credit / cash / brokerage / crypto account. */
@Entity(tableName = "accounts")
data class AccountEntity(
  /** Stable id — for connector-sourced accounts this is the source accountRef (IBAN / number / file label). */
  @PrimaryKey val id: String,
  val displayName: String,
  /** Bank / institution this account belongs to (for grouping on the Accounts screen). */
  val institution: String,
  val currency: String,
  val type: AccountType,
  /** Excluded from net-worth roll-ups when true. */
  val hiddenFromNetWorth: Boolean = false,
  val displayOrder: Int = 0,
)

enum class AccountType { Checking, Savings, Credit, Cash, Brokerage, Crypto, Other }

/**
 * A transaction. Write-once-ish: an edit lands as a NEW row whose
 * [originalId] points at the row it supersedes, so history is preserved
 * (the repository surfaces only the latest revision per chain). Connector
 * de-duplication is on (`accountId`, `externalId`) — re-fetching the same
 * statement never double-books.
 */
@Entity(
  tableName = "transactions",
  foreignKeys = [
    ForeignKey(
      entity = AccountEntity::class,
      parentColumns = ["id"],
      childColumns = ["accountId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = CategoryEntity::class,
      parentColumns = ["id"],
      childColumns = ["categoryId"],
      onDelete = ForeignKey.SET_NULL,
    ),
  ],
  indices = [
    Index(value = ["accountId", "externalId"], unique = true),
    Index(value = ["bookedAt"]),
    Index(value = ["categoryId"]),
    Index(value = ["originalId"]),
  ],
)
data class TransactionEntity(
  @PrimaryKey val id: String,
  val accountId: String,
  /** Stable per-source id for de-duplication across re-fetches. */
  val externalId: String,
  val bookedAt: Long,
  val amountMinor: Long,
  val currency: String,
  val payee: String?,
  val description: String?,
  val categoryId: String? = null,
  /** Non-null on a revision: the id of the transaction this one supersedes. */
  val originalId: String? = null,
  val createdAt: Long,
)

/** A spend / income category. Optional [parentId] gives a one-level hierarchy. */
@Entity(
  tableName = "categories",
  indices = [Index(value = ["parentId"])],
)
data class CategoryEntity(
  @PrimaryKey val id: String,
  val name: String,
  val parentId: String? = null,
  /** ARGB colour for the category chip / chart slice; null = theme default. */
  val colorArgb: Long? = null,
)
