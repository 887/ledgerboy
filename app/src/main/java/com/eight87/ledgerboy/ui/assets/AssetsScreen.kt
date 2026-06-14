@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.eight87.ledgerboy.ui.assets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.ledgerboy.R
import com.eight87.ledgerboy.data.LedgerRepository
import com.eight87.ledgerboy.data.db.AssetClass
import com.eight87.ledgerboy.data.db.AssetEntity
import com.eight87.ledgerboy.data.model.AssetSummary
import com.eight87.ledgerboy.data.money.MoneyInput
import com.eight87.ledgerboy.ui.settings.catalog.SettingsCard
import com.eight87.ledgerboy.ui.settings.catalog.SettingsDimens
import com.eight87.ledgerboy.ui.settings.catalog.SettingsRowDivider
import kotlinx.coroutines.launch

/**
 * I — the Assets screen (rail destination). Lists assets grouped by class
 * with their current value (latest valuation, or acquisition cost until a
 * valuation is recorded); tap an asset for its valuation history. Add an
 * asset by name + class + acquisition cost.
 */
@Composable
fun AssetsScreen(
  ledger: LedgerRepository,
  modifier: Modifier = Modifier,
  onAssetClick: (AssetSummary) -> Unit = {},
  clock: () -> Long = { System.currentTimeMillis() },
) {
  val assets by ledger.observeAssets().collectAsStateWithLifecycle(initialValue = emptyList())
  val scope = rememberCoroutineScope()
  var name by remember { mutableStateOf("") }
  var pickedClass by remember { mutableStateOf(AssetClass.Generic) }
  var cost by remember { mutableStateOf("") }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = SettingsDimens.PagePadding),
    verticalArrangement = Arrangement.spacedBy(SettingsDimens.CardSpacing),
  ) {
    Spacer(Modifier.height(4.dp))

    if (assets.isEmpty()) {
      Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.assets_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
          stringResource(R.string.assets_empty_body),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(top = 8.dp),
        )
      }
    } else {
      assets.groupBy { it.assetClass }.forEach { (cls, group) ->
        SettingsCard(title = stringResource(assetClassLabel(cls))) {
          group.forEachIndexed { index, asset ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onAssetClick(asset) }
                .padding(horizontal = SettingsDimens.RowHorizontalPadding, vertical = SettingsDimens.RowVerticalPadding),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(asset.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
              Text(asset.currentValue.format(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 12.dp))
            }
            if (index < group.size - 1) SettingsRowDivider()
          }
        }
      }
    }

    SettingsCard(title = stringResource(R.string.assets_add_title)) {
      Column(Modifier.padding(16.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text(stringResource(R.string.asset_name_hint)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          AssetClass.entries.forEach { cls ->
            FilterChip(
              selected = pickedClass == cls,
              onClick = { pickedClass = cls },
              label = { Text(stringResource(assetClassLabel(cls))) },
            )
          }
        }
        OutlinedTextField(
          value = cost,
          onValueChange = { cost = it },
          label = { Text(stringResource(R.string.asset_cost_hint)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Button(
          enabled = name.isNotBlank() && MoneyInput.parseMajorToMinor(cost) != null,
          onClick = {
            val costMinor = MoneyInput.parseMajorToMinor(cost) ?: return@Button
            val ts = clock()
            val id = "asset-" + name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-') + "-" + ts
            scope.launch {
              ledger.upsertAsset(
                AssetEntity(
                  id = id, name = name.trim(), assetClass = pickedClass,
                  currency = "EUR", acquisitionCostMinor = costMinor, acquisitionDate = ts,
                ),
              )
            }
            name = ""; cost = ""; pickedClass = AssetClass.Generic
          },
          modifier = Modifier.padding(top = 8.dp),
        ) { Text(stringResource(R.string.assets_add)) }
      }
    }
    Spacer(Modifier.height(8.dp))
  }
}

internal fun assetClassLabel(cls: AssetClass): Int = when (cls) {
  AssetClass.RealEstate -> R.string.asset_class_RealEstate
  AssetClass.Vehicle -> R.string.asset_class_Vehicle
  AssetClass.Security -> R.string.asset_class_Security
  AssetClass.Crypto -> R.string.asset_class_Crypto
  AssetClass.Collectible -> R.string.asset_class_Collectible
  AssetClass.Generic -> R.string.asset_class_Generic
}
