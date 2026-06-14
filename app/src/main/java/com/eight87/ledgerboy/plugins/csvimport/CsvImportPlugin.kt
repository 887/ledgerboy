package com.eight87.ledgerboy.plugins.csvimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import com.eight87.ledgerboy.plugins.api.ConnectorPlugin
import com.eight87.ledgerboy.plugins.api.FetchResult
import com.eight87.ledgerboy.plugins.api.FetchWindow
import com.eight87.ledgerboy.plugins.api.PluginCategory
import com.eight87.ledgerboy.plugins.api.PluginHost
import com.eight87.ledgerboy.plugins.api.RowSink
import kotlinx.coroutines.launch

/**
 * Phase B — the generic CSV-import connector (banking-import.md). The
 * universal fallback: works for any bank that exports a CSV. No network
 * (the [com.eight87.ledgerboy.plugins.runtime.PluginNetworkGuard] is a
 * no-op for it), no credentials. The user picks a file via SAF, names the
 * account, and chooses the format; `fetch` reads the file on-device,
 * parses it with the chosen [CsvDialect], and emits de-duplicated
 * [com.eight87.ledgerboy.plugins.api.NormalizedTransaction] rows.
 *
 * Config keys (per-plugin namespace): `uri`, `dialect`, `account`.
 */
object CsvImportPlugin : ConnectorPlugin {
  override val id = "csv-import"
  override val displayNameRes = R.string.csv_import_name
  override val category = PluginCategory.Import
  override val descriptionRes = R.string.csv_import_desc
  override val licenseSpdx = "MIT" // roll-our-own parser, stdlib only
  override val sourceUrl: String? = null
  override val privacyStatementRes = R.string.csv_import_privacy

  private const val KEY_URI = "uri"
  private const val KEY_DIALECT = "dialect"
  private const val KEY_ACCOUNT = "account"

  @OptIn(ExperimentalLayoutApi::class)
  @Composable
  override fun ConfigScreen(host: PluginHost) {
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf("") }
    var dialectName by remember { mutableStateOf(CsvDialects.GENERIC.name) }
    var pickedUri by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
      account = host.config.getString(id, KEY_ACCOUNT) ?: ""
      dialectName = host.config.getString(id, KEY_DIALECT) ?: CsvDialects.GENERIC.name
      pickedUri = host.config.getString(id, KEY_URI)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      OutlinedTextField(
        value = account,
        onValueChange = {
          account = it
          scope.launch { host.config.putString(id, KEY_ACCOUNT, it) }
        },
        label = { Text(stringResource(R.string.csv_import_account_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )

      Text(stringResource(R.string.csv_import_format_label), style = MaterialTheme.typography.labelLarge)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CsvDialects.all.forEach { dialect ->
          FilterChip(
            selected = dialectName == dialect.name,
            onClick = {
              dialectName = dialect.name
              scope.launch { host.config.putString(id, KEY_DIALECT, dialect.name) }
            },
            label = { Text(dialect.name, maxLines = 1, softWrap = false) },
          )
        }
      }

      Button(
        onClick = {
          scope.launch {
            val uri = host.pickImportFile(listOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
            if (uri != null) {
              pickedUri = uri
              host.config.putString(id, KEY_URI, uri)
            }
          }
        },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(stringResource(R.string.csv_import_pick))
      }

      Text(
        text = pickedUri?.let { stringResource(R.string.csv_import_selected, it.substringAfterLast('/')) }
          ?: stringResource(R.string.csv_import_none_selected),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
      )
    }
  }

  override suspend fun fetch(window: FetchWindow, host: PluginHost, sink: RowSink): FetchResult {
    val uri = host.config.getString(id, KEY_URI)
      ?: return FetchResult.Error("No CSV file selected")
    val account = host.config.getString(id, KEY_ACCOUNT)?.ifBlank { null }
      ?: return FetchResult.Error("No account name set")
    val dialect = CsvDialects.byName(host.config.getString(id, KEY_DIALECT))

    val content = host.readImportFile(uri, dialect.charset)
      ?: return FetchResult.Error("Could not read the selected file")

    val rows = CsvParser.parse(content, dialect, accountRef = account)
    if (rows.isEmpty()) return FetchResult.Error("No transactions found in the file")
    rows.forEach { sink.write(it) }
    return FetchResult.Ok(rowsWritten = rows.size, bytesIn = content.length.toLong(), bytesOut = 0)
  }
}
