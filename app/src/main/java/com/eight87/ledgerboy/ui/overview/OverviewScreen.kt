package com.eight87.ledgerboy.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.ledgerboy.R
import com.eight87.ledgerboy.data.LedgerRepository
import com.eight87.ledgerboy.data.money.CurrencyCode
import com.eight87.ledgerboy.data.money.Money
import com.eight87.ledgerboy.ui.settings.catalog.SettingsCard
import com.eight87.ledgerboy.ui.settings.catalog.SettingsDimens
import com.eight87.ledgerboy.ui.settings.catalog.SettingsRowDivider

/**
 * E.2 — the Overview. Shows balances summed **per currency** (honest:
 * cross-currency net worth in one base currency needs FX rates, which
 * only exist once the user enables the ECB plugin — a Phase K/D-follow-up
 * surfaces the converted total; here we never show a wrong number),
 * plus the account count and the uncategorized-transaction count.
 */
@Composable
fun OverviewScreen(
  ledger: LedgerRepository,
  modifier: Modifier = Modifier,
) {
  val accounts by ledger.observeAccounts().collectAsStateWithLifecycle(initialValue = emptyList())
  val uncategorized by ledger.observeUncategorizedCount().collectAsStateWithLifecycle(initialValue = 0)

  if (accounts.isEmpty()) {
    Column(
      modifier = modifier.fillMaxSize().padding(32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Text(stringResource(R.string.overview_empty_title), style = MaterialTheme.typography.titleMedium)
      Text(
        stringResource(R.string.overview_empty_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp),
      )
    }
    return
  }

  // Sum balances per currency (no cross-currency mixing without rates).
  val perCurrency: Map<String, Money> = accounts
    .filterNot { it.hiddenFromNetWorth }
    .groupBy { it.balance.currency.code }
    .mapValues { (code, list) ->
      list.fold(Money.zero(CurrencyCode(code))) { acc, a -> acc + a.balance }
    }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = SettingsDimens.PagePadding),
    verticalArrangement = Arrangement.spacedBy(SettingsDimens.CardSpacing),
  ) {
    Spacer(Modifier.height(4.dp))
    SettingsCard(title = stringResource(R.string.overview_balances_title)) {
      val entries = perCurrency.entries.toList()
      entries.forEachIndexed { index, (code, total) ->
        StatRow(label = code, value = total.format())
        if (index < entries.size - 1) SettingsRowDivider()
      }
    }
    SettingsCard {
      StatRow(stringResource(R.string.overview_stat_accounts), accounts.size.toString())
      SettingsRowDivider()
      StatRow(stringResource(R.string.overview_stat_uncategorized), uncategorized.toString())
    }
    Spacer(Modifier.height(8.dp))
  }
}

@Composable
private fun StatRow(label: String, value: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = SettingsDimens.RowHorizontalPadding, vertical = SettingsDimens.RowVerticalPadding),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(label, style = MaterialTheme.typography.titleSmall)
    Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
