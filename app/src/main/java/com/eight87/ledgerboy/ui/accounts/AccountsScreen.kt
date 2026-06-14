package com.eight87.ledgerboy.ui.accounts

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.ledgerboy.R
import com.eight87.ledgerboy.data.LedgerRepository
import com.eight87.ledgerboy.data.model.Account
import com.eight87.ledgerboy.ui.settings.catalog.SettingsCard
import com.eight87.ledgerboy.ui.settings.catalog.SettingsDimens
import com.eight87.ledgerboy.ui.settings.catalog.SettingsRowDivider

/**
 * E.1 — the Accounts screen. Lists every account across every bank,
 * grouped by institution, each with its live balance in the account's
 * native currency. Empty until the user imports a CSV (Phase B) or
 * enables a banking connector. Renders inside the rail shell (the shell
 * owns the "Accounts" top bar).
 */
@Composable
fun AccountsScreen(
  ledger: LedgerRepository,
  modifier: Modifier = Modifier,
  onAccountClick: (Account) -> Unit = {},
) {
  val accounts by ledger.observeAccounts().collectAsStateWithLifecycle(initialValue = emptyList())

  if (accounts.isEmpty()) {
    EmptyAccounts(modifier)
    return
  }

  val byInstitution = accounts.groupBy { it.institution.ifBlank { it.displayName } }
  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = SettingsDimens.PagePadding),
    verticalArrangement = Arrangement.spacedBy(SettingsDimens.CardSpacing),
  ) {
    Spacer(Modifier.height(4.dp))
    byInstitution.forEach { (institution, group) ->
      SettingsCard(title = institution) {
        group.forEachIndexed { index, account ->
          AccountRow(account, onClick = { onAccountClick(account) })
          if (index < group.size - 1) SettingsRowDivider()
        }
      }
    }
    Spacer(Modifier.height(8.dp))
  }
}

@Composable
private fun AccountRow(account: Account, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = SettingsDimens.RowHorizontalPadding, vertical = SettingsDimens.RowVerticalPadding),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = account.displayName,
      style = MaterialTheme.typography.titleSmall,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f, fill = false),
    )
    Spacer(Modifier.height(0.dp))
    Text(
      text = account.balance.format(),
      style = MaterialTheme.typography.titleSmall,
      color = if (account.balance.isNegative) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      softWrap = false,
      modifier = Modifier.padding(start = 12.dp),
    )
  }
}

@Composable
private fun EmptyAccounts(modifier: Modifier) {
  Column(
    modifier = modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      text = stringResource(R.string.accounts_empty_title),
      style = MaterialTheme.typography.titleMedium,
    )
    Text(
      text = stringResource(R.string.accounts_empty_body),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 8.dp),
    )
  }
}
