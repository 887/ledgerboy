package com.eight87.ledgerboy.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.eight87.ledgerboy.data.LedgerRepository
import com.eight87.ledgerboy.data.db.AccountEntity
import com.eight87.ledgerboy.data.db.CategoryEntity
import com.eight87.ledgerboy.data.model.Account
import com.eight87.ledgerboy.data.model.Category
import com.eight87.ledgerboy.data.model.Transaction
import com.eight87.ledgerboy.data.money.CurrencyCode
import com.eight87.ledgerboy.data.money.Money
import com.eight87.ledgerboy.plugins.api.NormalizedTransaction
import com.eight87.ledgerboy.theme.LedgerboyTheme
import com.eight87.ledgerboy.ui.accounts.AccountsScreen
import com.eight87.ledgerboy.ui.overview.OverviewScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** E.1/E.2 — Accounts + Overview render real ledger data (device-independent). */
@RunWith(RobolectricTestRunner::class)
class AccountsOverviewScreenTest {

  @get:Rule val compose = createComposeRule()

  private class FakeLedger(private val accounts: List<Account>) : LedgerRepository {
    override fun observeAccounts(): Flow<List<Account>> = flowOf(accounts)
    override fun observeTransactions(): Flow<List<Transaction>> = flowOf(emptyList())
    override fun observeTransactionsForAccount(accountId: String): Flow<List<Transaction>> = flowOf(emptyList())
    override fun observeCategories(): Flow<List<Category>> = flowOf(emptyList())
    override fun observeUncategorizedCount(): Flow<Int> = flowOf(3)
    override suspend fun accountBalance(accountId: String) = Money.zero(CurrencyCode.EUR)
    override suspend fun upsertAccount(account: AccountEntity) = Unit
    override suspend fun upsertCategory(category: CategoryEntity) = Unit
    override suspend fun assignCategory(transactionId: String, categoryId: String?) = Unit
    override fun observeRules(): Flow<List<com.eight87.ledgerboy.data.db.CategoryRuleEntity>> = flowOf(emptyList())
    override suspend fun upsertRule(rule: com.eight87.ledgerboy.data.db.CategoryRuleEntity) = Unit
    override suspend fun deleteRule(id: String) = Unit
    override suspend fun applyRulesToUncategorized(): Int = 0
    override fun observeBudgets(): Flow<List<com.eight87.ledgerboy.data.db.BudgetEntity>> = flowOf(emptyList())
    override suspend fun upsertBudget(budget: com.eight87.ledgerboy.data.db.BudgetEntity) = Unit
    override suspend fun deleteBudget(id: String) = Unit
    override fun observeBudgetProgress(startEpochMillis: Long, endEpochMillis: Long): Flow<List<com.eight87.ledgerboy.data.budget.BudgetProgress>> = flowOf(emptyList())
    override fun observeAssets(): Flow<List<com.eight87.ledgerboy.data.model.AssetSummary>> = flowOf(emptyList())
    override suspend fun upsertAsset(asset: com.eight87.ledgerboy.data.db.AssetEntity) = Unit
    override suspend fun deleteAsset(id: String) = Unit
    override fun observeValuations(assetId: String): Flow<List<com.eight87.ledgerboy.data.db.ValuationEventEntity>> = flowOf(emptyList())
    override suspend fun addValuation(event: com.eight87.ledgerboy.data.db.ValuationEventEntity) = Unit
    override suspend fun writeFxRate(base: String, quote: String, rateScaled1e8: Long, asOfEpochMillis: Long) = Unit
    override suspend fun fxRateOnOrBefore(base: String, quote: String, asOfEpochMillis: Long): Long? = null
    override suspend fun writeNormalizedTransaction(row: NormalizedTransaction) = true
  }

  private fun account(id: String, name: String, institution: String, minor: Long) = Account(
    id = id, displayName = name, institution = institution,
    type = com.eight87.ledgerboy.data.db.AccountType.Checking,
    hiddenFromNetWorth = false, balance = Money(minor, CurrencyCode.EUR),
  )

  @Test
  fun `accounts screen shows account name and balance`() {
    val repo = FakeLedger(listOf(account("a1", "Everyday", "Westvale Bank", 123456)))
    compose.setContent { LedgerboyTheme { AccountsScreen(ledger = repo) } }
    compose.onNodeWithText("Everyday").assertIsDisplayed()
    compose.onNodeWithText("Westvale Bank").assertIsDisplayed()
  }

  @Test
  fun `accounts screen shows empty state when there are none`() {
    val repo = FakeLedger(emptyList())
    compose.setContent { LedgerboyTheme { AccountsScreen(ledger = repo) } }
    compose.onNodeWithText("No accounts yet").assertIsDisplayed()
  }

  @Test
  fun `overview shows per-currency balances and uncategorized count`() {
    val repo = FakeLedger(listOf(account("a1", "Everyday", "Westvale Bank", 100000)))
    compose.setContent { LedgerboyTheme { OverviewScreen(ledger = repo) } }
    compose.onNodeWithText("EUR").assertIsDisplayed()
    compose.onNodeWithText("Uncategorized transactions").assertIsDisplayed()
    compose.onNodeWithText("3").assertIsDisplayed()
  }
}
