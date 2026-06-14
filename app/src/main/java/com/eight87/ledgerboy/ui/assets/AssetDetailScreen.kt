@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.eight87.ledgerboy.ui.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.ledgerboy.R
import com.eight87.ledgerboy.data.LedgerRepository
import com.eight87.ledgerboy.data.db.ValuationEventEntity
import com.eight87.ledgerboy.data.money.CurrencyCode
import com.eight87.ledgerboy.data.money.Money
import com.eight87.ledgerboy.data.money.MoneyInput
import com.eight87.ledgerboy.ui.settings.catalog.SettingsCard
import com.eight87.ledgerboy.ui.settings.catalog.SettingsDimens
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * I — asset detail. Shows the valuation history and records a manual
 * revaluation (a `source="manual"` valuation event). The asset's current
 * value on the Assets list updates to the newest event.
 */
@Composable
fun AssetDetailScreen(
  ledger: LedgerRepository,
  assetId: String,
  assetName: String,
  currency: String,
  onBack: () -> Unit,
  clock: () -> Long = { System.currentTimeMillis() },
) {
  val history by ledger.observeValuations(assetId).collectAsStateWithLifecycle(initialValue = emptyList())
  val scope = rememberCoroutineScope()
  var value by remember { mutableStateOf("") }
  val manualSource = stringResource(R.string.asset_valuation_source_manual)

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(assetName) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = SettingsDimens.PagePadding),
      verticalArrangement = Arrangement.spacedBy(SettingsDimens.CardSpacing),
    ) {
      Spacer4()
      SettingsCard(title = stringResource(R.string.asset_detail_history)) {
        Column(Modifier.padding(16.dp)) {
          OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text(stringResource(R.string.asset_detail_revalue_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          Button(
            enabled = MoneyInput.parseMajorToMinor(value) != null,
            onClick = {
              val minor = MoneyInput.parseMajorToMinor(value) ?: return@Button
              val ts = clock()
              scope.launch {
                ledger.addValuation(
                  ValuationEventEntity(
                    id = "val-$assetId-$ts", assetId = assetId, valueMinor = minor,
                    currency = currency, asOf = ts, source = manualSource,
                  ),
                )
              }
              value = ""
            },
            modifier = Modifier.padding(top = 8.dp),
          ) { Text(stringResource(R.string.asset_detail_add_valuation)) }

          if (history.isEmpty()) {
            Text(
              stringResource(R.string.asset_detail_no_history),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(top = 12.dp),
            )
          } else {
            history.forEach { event ->
              Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  "${formatDate(event.asOf)} · ${event.source}",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  Money(event.valueMinor, CurrencyCode(event.currency)).format(),
                  style = MaterialTheme.typography.titleSmall,
                )
              }
              HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
          }
        }
      }
      Spacer4()
    }
  }
}

@Composable private fun Spacer4() = androidx.compose.foundation.layout.Spacer(Modifier.padding(2.dp))

private fun formatDate(epochMillis: Long): String =
  DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate())
