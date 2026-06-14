package com.eight87.ledgerboy.plugins.coinbase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.ledgerboy.R
import com.eight87.ledgerboy.data.money.CurrencyCode
import com.eight87.ledgerboy.plugins.api.ConnectorPlugin
import com.eight87.ledgerboy.plugins.api.FetchResult
import com.eight87.ledgerboy.plugins.api.FetchWindow
import com.eight87.ledgerboy.plugins.api.PluginCategory
import com.eight87.ledgerboy.plugins.api.PluginHost
import com.eight87.ledgerboy.plugins.api.RowSink
import com.eight87.ledgerboy.plugins.api.ValuationEventRow
import com.eight87.ledgerboy.plugins.runtime.taggedFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * J — a representative asset-price connector (asset-research.md /
 * connector-plugins.md): fetches a crypto spot price **directly** from
 * Coinbase's public, keyless endpoint (a direct exchange, not an
 * aggregator) and emits a `ValuationEventRow` into the asset's valuation
 * history (Phase I). Off by default; the user names which asset to
 * revalue and the pair.
 *
 * This proves the AssetPrice → valuation path on the Phase X runtime.
 * Remaining per-class connectors (per-broker securities, on-chain RPC,
 * Scryfall/pokemontcg.io/YGOPRODeck collectibles, regional house-price
 * indexes) follow the same shape, one plugin at a time.
 *
 * Config keys: `asset` (existing asset id), `pair` (e.g. BTC-EUR).
 */
object CoinbaseCryptoPlugin : ConnectorPlugin {
  override val id = "coinbase-crypto"
  override val displayNameRes = R.string.coinbase_name
  override val category = PluginCategory.AssetPrice
  override val descriptionRes = R.string.coinbase_desc
  override val licenseSpdx = "MIT" // our client; Coinbase public price data
  override val sourceUrl = "https://api.coinbase.com/v2/prices"
  override val privacyStatementRes = R.string.coinbase_privacy

  private const val KEY_ASSET = "asset"
  private const val KEY_PAIR = "pair"

  @Composable
  override fun ConfigScreen(host: PluginHost) {
    val scope = rememberCoroutineScope()
    var asset by remember { mutableStateOf("") }
    var pair by remember { mutableStateOf("BTC-EUR") }
    LaunchedEffect(Unit) {
      asset = host.config.getString(id, KEY_ASSET) ?: ""
      pair = host.config.getString(id, KEY_PAIR) ?: "BTC-EUR"
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      OutlinedTextField(
        value = asset,
        onValueChange = { asset = it; scope.launch { host.config.putString(id, KEY_ASSET, it) } },
        label = { Text(stringResource(R.string.coinbase_asset_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = pair,
        onValueChange = { pair = it.uppercase(); scope.launch { host.config.putString(id, KEY_PAIR, it.uppercase()) } },
        label = { Text(stringResource(R.string.coinbase_pair_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }

  override suspend fun fetch(window: FetchWindow, host: PluginHost, sink: RowSink): FetchResult {
    val asset = host.config.getString(id, KEY_ASSET)?.ifBlank { null }
      ?: return FetchResult.Error("No asset id set")
    val pair = host.config.getString(id, KEY_PAIR)?.ifBlank { null }
      ?: return FetchResult.Error("No pair set")

    val request = Request.Builder()
      .url("https://api.coinbase.com/v2/prices/$pair/spot")
      .taggedFor(id)
      .build()
    val (body, bytesIn) = withContext(Dispatchers.IO) {
      runCatching {
        host.http.newCall(request).execute().use { response ->
          if (!response.isSuccessful) return@runCatching null
          response.body?.string()?.let { it to it.length.toLong() }
        }
      }.getOrNull()
    } ?: return FetchResult.Error("Could not reach Coinbase for $pair")

    val quoteCurrency = pair.substringAfter('-', "EUR")
    val price = CoinbasePriceParser.parse(body, CurrencyCode.fractionDigits(CurrencyCode(quoteCurrency)))
      ?: return FetchResult.Error("Coinbase price could not be parsed")

    sink.write(
      ValuationEventRow(
        assetRef = asset,
        valueMinor = price.valueMinor,
        currency = price.currency,
        asOfEpochMillis = System.currentTimeMillis(),
        source = id,
      ),
    )
    return FetchResult.Ok(rowsWritten = 1, bytesIn = bytesIn, bytesOut = 0)
  }
}
