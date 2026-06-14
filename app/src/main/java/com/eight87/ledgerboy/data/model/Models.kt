package com.eight87.ledgerboy.data.model

import com.eight87.ledgerboy.data.db.AccountType
import com.eight87.ledgerboy.data.money.Money

/**
 * C.6 — domain models exposed to the UI. They carry [Money] (not raw
 * `Long` + `String` columns) so screens never re-derive currency
 * handling. Mapped from the Room entities at the repository boundary.
 */
data class Account(
  val id: String,
  val displayName: String,
  val institution: String,
  val type: AccountType,
  val hiddenFromNetWorth: Boolean,
  val balance: Money,
)

data class Transaction(
  val id: String,
  val accountId: String,
  val bookedAtEpochMillis: Long,
  val amount: Money,
  val payee: String?,
  val description: String?,
  val categoryId: String?,
)

data class Category(
  val id: String,
  val name: String,
  val parentId: String?,
  val colorArgb: Long?,
)
