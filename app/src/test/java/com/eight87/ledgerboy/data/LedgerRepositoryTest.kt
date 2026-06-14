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
    repo = RoomLedgerRepository(
      db.accountDao(), db.transactionDao(), db.categoryDao(), db.fxRateDao(),
      db.categoryRuleDao(), db.budgetDao(),
    ) { 1_000L }
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

  @Test
  fun `assigning a category clears the uncategorized count`() = runTest {
    repo.writeNormalizedTransaction(txn("t1", 1050))
    repo.upsertCategory(com.eight87.ledgerboy.data.db.CategoryEntity(id = "cat-rent", name = "Rent"))
    assertEquals(1, repo.observeUncategorizedCount().first())
    val txnId = db.transactionDao().byExternalId("acct-1", "t1")!!.id
    repo.assignCategory(txnId, "cat-rent")
    assertEquals(0, repo.observeUncategorizedCount().first())
    assertEquals("cat-rent", repo.observeTransactions().first().first().categoryId)
  }

  @Test
  fun `import auto-categorizes via a matching rule`() = runTest {
    repo.upsertCategory(com.eight87.ledgerboy.data.db.CategoryEntity(id = "cat-rent", name = "Rent"))
    repo.upsertRule(
      com.eight87.ledgerboy.data.db.CategoryRuleEntity(
        id = "r1", categoryId = "cat-rent",
        kind = com.eight87.ledgerboy.data.db.RuleKind.PayeeContains, pattern = "Westvale",
      ),
    )
    repo.writeNormalizedTransaction(
      NormalizedTransaction("t1", 1_700_000_000_000L, -85000, "EUR", "Westvale Properties", "rent", "acct-1"),
    )
    assertEquals("cat-rent", repo.observeTransactions().first().first().categoryId)
    assertEquals(0, repo.observeUncategorizedCount().first())
  }

  @Test
  fun `apply rules to uncategorized categorizes existing rows`() = runTest {
    // Import first (no rules yet → uncategorized).
    repo.writeNormalizedTransaction(
      NormalizedTransaction("t1", 1_700_000_000_000L, -4200, "EUR", "ARAL Station", "fuel", "acct-1"),
    )
    assertEquals(1, repo.observeUncategorizedCount().first())
    repo.upsertCategory(com.eight87.ledgerboy.data.db.CategoryEntity(id = "cat-fuel", name = "Fuel"))
    repo.upsertRule(
      com.eight87.ledgerboy.data.db.CategoryRuleEntity(
        id = "r1", categoryId = "cat-fuel",
        kind = com.eight87.ledgerboy.data.db.RuleKind.PayeeContains, pattern = "ARAL",
      ),
    )
    assertEquals(1, repo.applyRulesToUncategorized())
    assertEquals(0, repo.observeUncategorizedCount().first())
  }

  @Test
  fun `budget progress reflects categorized spending in the month`() = runTest {
    repo.upsertCategory(com.eight87.ledgerboy.data.db.CategoryEntity(id = "cat-food", name = "Food"))
    // Two expenses in Jan 2026, one categorized to Food.
    val jan10 = java.time.LocalDate.of(2026, 1, 10)
      .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    repo.writeNormalizedTransaction(
      NormalizedTransaction("t1", jan10, -3000, "EUR", "Market", "groceries", "acct-1"),
    )
    val txnId = db.transactionDao().byExternalId("acct-1", "t1")!!.id
    repo.assignCategory(txnId, "cat-food")
    repo.upsertBudget(
      com.eight87.ledgerboy.data.db.BudgetEntity(
        id = "b1", categoryId = "cat-food", limitMinor = 10000, currency = "EUR",
      ),
    )
    val (start, end) = com.eight87.ledgerboy.data.budget.MonthRange.of(jan10, java.time.ZoneOffset.UTC)
    val progress = repo.observeBudgetProgress(start, end).first()
    assertEquals(1, progress.size)
    assertEquals(3000, progress.first().spent.minorUnits) // €30 spent of €100
  }

  @Test
  fun `fx cache returns latest rate on or before a date`() = runTest {
    repo.writeFxRate("EUR", "USD", 108_000_000L, asOfEpochMillis = 1_000L)
    repo.writeFxRate("EUR", "USD", 110_000_000L, asOfEpochMillis = 3_000L)
    // Before the first rate → null.
    assertEquals(null, repo.fxRateOnOrBefore("EUR", "USD", 500L))
    // Between the two → the earlier rate.
    assertEquals(108_000_000L, repo.fxRateOnOrBefore("EUR", "USD", 2_000L))
    // On-or-after the second → the later rate.
    assertEquals(110_000_000L, repo.fxRateOnOrBefore("EUR", "USD", 5_000L))
  }
}
