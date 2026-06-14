package com.eight87.ledgerboy.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eight87.ledgerboy.data.db.LedgerDatabase
import com.eight87.ledgerboy.data.db.TransactionEntity
import com.eight87.ledgerboy.data.money.CurrencyCode
import com.eight87.ledgerboy.data.money.Money
import com.eight87.ledgerboy.plugins.api.NormalizedTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** C.7 — ledger store: dedup, balance, and latest-revision semantics. */
@RunWith(RobolectricTestRunner::class)
class LedgerRepositoryTest {

  private lateinit var db: LedgerDatabase
  private lateinit var repo: RoomLedgerRepository

  @Before fun setUp() {
    db = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      LedgerDatabase::class.java,
    ).allowMainThreadQueries().build()
    repo = RoomLedgerRepository(db.accountDao(), db.transactionDao(), db.categoryDao()) { 1_000L }
  }

  @After fun tearDown() = db.close()

  private fun txn(externalId: String, amountMinor: Long) = NormalizedTransaction(
    externalId = externalId,
    bookedAtEpochMillis = 1_700_000_000_000L,
    amountMinor = amountMinor,
    currency = "EUR",
    payee = "Acme Savings",
    description = "demo",
    accountRef = "acct-1",
  )

  @Test
  fun `writing a transaction auto-creates the account`() = runTest {
    assertTrue(repo.writeNormalizedTransaction(txn("t1", 1050)))
    val accounts = repo.observeAccounts().first()
    assertEquals(1, accounts.size)
    assertEquals("acct-1", accounts.first().id)
  }

  @Test
  fun `re-fetching the same transaction is de-duplicated`() = runTest {
    assertTrue(repo.writeNormalizedTransaction(txn("t1", 1050)))
    assertFalse(repo.writeNormalizedTransaction(txn("t1", 1050)))
    val txns = repo.observeTransactions().first()
    assertEquals(1, txns.size)
  }

  @Test
  fun `balance sums current revisions`() = runTest {
    repo.writeNormalizedTransaction(txn("t1", 1050))
    repo.writeNormalizedTransaction(txn("t2", -300))
    assertEquals(Money(750, CurrencyCode.EUR), repo.accountBalance("acct-1"))
  }

  @Test
  fun `a revision supersedes the original in the current view`() = runTest {
    repo.writeNormalizedTransaction(txn("t1", 1050))
    val original = db.transactionDao().byExternalId("acct-1", "t1")!!
    // Land a revision pointing at the original (an edit) with a corrected amount.
    db.transactionDao().upsert(
      original.copy(id = original.id + "#r2", externalId = "t1#r2", amountMinor = 999, originalId = original.id),
    )
    val current = repo.observeTransactions().first()
    assertEquals(1, current.size)
    assertEquals(999, current.first().amount.minorUnits)
  }

  @Test
  fun `uncategorized count tracks current rows without a category`() = runTest {
    repo.writeNormalizedTransaction(txn("t1", 1050))
    repo.writeNormalizedTransaction(txn("t2", 2000))
    assertEquals(2, repo.observeUncategorizedCount().first())
  }
}
