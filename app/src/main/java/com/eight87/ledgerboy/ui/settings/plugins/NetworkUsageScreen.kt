@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.eight87.ledgerboy.ui.settings.plugins

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.ledgerboy.AppGraph
import com.eight87.ledgerboy.R
import com.eight87.ledgerboy.plugins.runtime.NetworkCounters
import com.eight87.ledgerboy.ui.settings.catalog.SettingsCard
import com.eight87.ledgerboy.ui.settings.catalog.SettingsDimens

/**
 * X.7 — Settings → Plugins → Network usage. The user-visible audit: every
 * connector's bytes-in/out, fetch count, error count, and the actual
 * endpoints it called. With an empty manifest (or no traffic), it states
 * plainly that ledgerboy has sent nothing — the network posture made
 * legible (connector-plugins.md transparency §).
 */
@Composable
fun NetworkUsageScreen(
  graph: AppGraph,
  onBack: () -> Unit,
) {
  val plugins = graph.pluginManifest.all
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.network_usage_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.cd_back),
            )
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
      Spacer(Modifier.height(8.dp))
      if (plugins.isEmpty()) {
        Text(
          text = stringResource(R.string.network_usage_empty),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        plugins.forEach { plugin ->
          val counters by graph.pluginRuntime.counters(plugin.id).collectAsStateWithLifecycle()
          SettingsCard(title = stringResource(plugin.displayNameRes)) {
            Column(Modifier.padding(16.dp)) {
              UsageStat(R.string.network_usage_bytes_in, formatBytes(counters.bytesIn))
              UsageStat(R.string.network_usage_bytes_out, formatBytes(counters.bytesOut))
              UsageStat(R.string.network_usage_fetches, counters.fetchCount.toString())
              UsageStat(R.string.network_usage_errors, counters.errorCount.toString())
              EndpointList(counters)
            }
          }
        }
      }
      Spacer(Modifier.height(8.dp))
    }
  }
}

@Composable
private fun UsageStat(labelRes: Int, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
    Text(
      value,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun EndpointList(counters: NetworkCounters) {
  if (counters.endpoints.isEmpty()) return
  Spacer(Modifier.height(8.dp))
  Text(
    stringResource(R.string.network_usage_endpoints),
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.primary,
  )
  counters.endpoints.forEach { endpoint ->
    Text(
      endpoint,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 2.dp),
    )
  }
}

private fun formatBytes(bytes: Long): String = when {
  bytes < 1024 -> "$bytes B"
  bytes < 1024 * 1024 -> "${bytes / 1024} KB"
  else -> "${bytes / (1024 * 1024)} MB"
}
