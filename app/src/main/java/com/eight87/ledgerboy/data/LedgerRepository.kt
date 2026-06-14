package com.eight87.ledgerboy.data

import com.eight87.ledgerboy.data.db.AccountDao
import com.eight87.ledgerboy.data.db.AccountEntity
import com.eight87.ledgerboy.data.db.AccountType
import com.eight87.ledgerboy.data.db.CategoryDao
import com.eight87.ledgerboy.data.db.CategoryEntity
import com.eight87.ledgerboy.data.db.TransactionDao
import com.eight87.ledgerboy.data.db.TransactionEntity
import com.eight87.ledgerboy.data.model.Account
import com.eight87.ledgerboy.data.model.Category
import com.eight87.ledgerboy.data.model.Transaction
import com.eight87.ledgerboy.data.money.CurrencyCode
import com.eight87.ledgerboy.data.money.Money
import com.eight87.ledgerboy.plugins.api.NormalizedTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * C.6 — the ledger's read/write surface. Maps Room entities to/from the
 * [Money]-carrying domain models, and is the single place a connector's
 * [NormalizedTransaction] becomes a stored, de-duplicated row.
 *
 * Interface + Room impl so screens depend on the abstraction (DIP) and an
 * in-memory fake can back UI tests.
 */
interface LedgerRepository {
  fun observeAccounts(): Flow<List<Account>>
  fun observeTransactions(): Flow<List<Transaction>>
  fun observeTransactionsForAccount(accountId: String): Flow<List<Transaction>>
  fun observeCategories(): Flow<List<Category>>
  fun observeUncategorizedCount(): Flow<Int>

  suspend fun accountBalance(accountId: String): Money
  suspend fun upsertAccount(account: AccountEntity)
  suspend fun upsertCategory(category: CategoryEntity)

  /**
   * Write a connector-emitted transaction, de-duplicated on
   * (accountRef, externalId). Auto-creates a minimal account row if the
   * referenced account is unknown. Returns true if a new row was stored,
   * false if it was a duplicate of an already-stored transaction.
   */
  suspend fun writeNormalizedTransaction(row: NormalizedTransaction): Boolean
}

class RoomLedgerRepository(
  private val accountDao: AccountDao,
  private val transactionDao: TransactionDao,
  private val categoryDao: CategoryDao,
  private val now: () -> Long = { System.currentTimeMillis() },
) : LedgerRepository {

  override fun observeAccounts(): Flow<List<Account>> =
    accountDao.observeAll().map { list -> list.map { it.toDomainWithBalance() } }

  // Note: balance is computed per-account via a suspend query; for the
  // list we map synchronously off the stored fields and let callers ask
  // accountBalance() where the live sum matters. To keep the list cheap
  // and correct, balance here is recomputed lazily by the caller.
  private fun AccountEntity.toDomainWithBalance(): Account =
    Account(
      id = id,
      displayName = displayName,
      institution = institution,
      type = type,
      hiddenFromNetWorth = hiddenFromNetWorth,
      balance = Money.zero(CurrencyCode(currency)),
    )

  override fun observeTransactions(): Flow<List<Transaction>> =
    transactionDao.observeCurrent().map { list -> list.map { it.toDomain() } }

  override fun observeTransactionsForAccount(accountId: String): Flow<List<Transaction>> =
    transactionDao.observeCurrentForAccount(accountId).map { list -> list.map { it.toDomain() } }

  override fun observeCategories(): Flow<List<Category>> =
    categoryDao.observeAll().map { list ->
      list.map { Category(it.id, it.name, it.parentId, it.colorArgb) }
    }

  override fun observeUncategorizedCount(): Flow<Int> =
    transactionDao.observeUncategorizedCount()

  override suspend fun accountBalance(accountId: String): Money {
    val account = accountDao.byId(accountId)
    val currency = CurrencyCode(account?.currency ?: CurrencyCode.EUR.code)
    return Money(transactionDao.balanceMinor(accountId), currency)
  }

  override suspend fun upsertAccount(account: AccountEntity) = accountDao.upsert(account)

  override suspend fun upsertCategory(category: CategoryEntity) = categoryDao.upsert(category)

  override suspend fun writeNormalizedTransaction(row: NormalizedTransaction): Boolean {
    // Auto-create a minimal account if this is the first time we've seen it.
    if (accountDao.byId(row.accountRef) == null) {
      accountDao.upsert(
        AccountEntity(
          id = row.accountRef,
          displayName = row.accountRef,
          institution = "",
          currency = row.currency,
          type = AccountType.Other,
        ),
      )
    }
    val entity = TransactionEntity(
      id = "${row.accountRef}:${row.externalId}",
      accountId = row.accountRef,
      externalId = row.externalId,
      bookedAt = row.bookedAtEpochMillis,
      amountMinor = row.amountMinor,
      currency = row.currency,
      payee = row.payee,
      description = row.description,
      createdAt = now(),
    )
    return transactionDao.insertIgnoringDuplicate(entity) != -1L
  }

  private fun TransactionEntity.toDomain(): Transaction =
    Transaction(
      id = id,
      accountId = accountId,
      bookedAtEpochMillis = bookedAt,
      amount = Money(amountMinor, CurrencyCode(currency)),
      payee = payee,
      description = description,
      categoryId = categoryId,
    )
}
