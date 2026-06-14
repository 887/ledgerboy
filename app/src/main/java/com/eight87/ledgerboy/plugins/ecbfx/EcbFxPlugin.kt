package com.eight87.ledgerboy.plugins.ecbfx

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.ledgerboy.R
import com.eight87.ledgerboy.plugins.api.ConnectorPlugin
import com.eight87.ledgerboy.plugins.api.FetchResult
import com.eight87.ledgerboy.plugins.api.FetchWindow
import com.eight87.ledgerboy.plugins.api.FxRateRow
import com.eight87.ledgerboy.plugins.api.PluginCategory
import com.eight87.ledgerboy.plugins.api.PluginHost
import com.eight87.ledgerboy.plugins.api.RowSink
import com.eight87.ledgerboy.plugins.runtime.taggedFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * D.3 — the ECB FX connector (banking-fx.md). Fetches the daily
 * euro-reference-rates file **directly** from the ECB (no proxy, no
 * aggregator) through the guarded HTTP client, parses it, and emits
 * EUR-paired [FxRateRow]s into the FX cache.
 *
 * No configuration: ECB rates are public, keyless, rate-limit-free, so
 * the ConfigScreen is informational and "Test connection" performs the
 * first fetch. Off by default like every plugin — a user who wants
 * base-currency aggregates enables it.
 */
object EcbFxPlugin : ConnectorPlugin {
  override val id = "ecb-fx"
  override val displayNameRes = R.string.ecb_fx_name
  override val category = PluginCategory.Fx
  override val descriptionRes = R.string.ecb_fx_desc
  override val licenseSpdx = "MIT" // our client; the ECB data is public domain
  override val sourceUrl = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml"
  override val privacyStatementRes = R.string.ecb_fx_privacy

  private const val DAILY_URL = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml"

  @Composable
  override fun ConfigScreen(host: PluginHost) {
    Text(
      text = stringResource(R.string.ecb_fx_config_note),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(vertical = 8.dp),
    )
  }

  override suspend fun fetch(window: FetchWindow, host: PluginHost, sink: RowSink): FetchResult {
    val request = Request.Builder().url(DAILY_URL).taggedFor(id).build()
    val (xml, bytesIn) = withContext(Dispatchers.IO) {
      runCatching {
        host.http.newCall(request).execute().use { response ->
          if (!response.isSuccessful) return@runCatching null
          val body = response.body?.string()
          body?.let { it to it.length.toLong() }
        }
      }.getOrNull()
    } ?: return FetchResult.Error("Could not reach the ECB rates feed")

    val parsed = EcbFxParser.parse(xml)
      ?: return FetchResult.Error("ECB rates feed could not be parsed")

    parsed.rates.forEach { rate ->
      sink.write(
        FxRateRow(
          baseCurrency = "EUR",
          quoteCurrency = rate.currency,
          rateScaled1e8 = rate.rateScaled1e8,
          asOfEpochMillis = parsed.asOfEpochMillis,
        ),
      )
    }
    return FetchResult.Ok(rowsWritten = parsed.rates.size, bytesIn = bytesIn, bytesOut = 0)
  }
}
