@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.eight87.ledgerboy.ui.settings.plugins

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.ledgerboy.AppGraph
import com.eight87.ledgerboy.R
import com.eight87.ledgerboy.plugins.api.FetchResult
import com.eight87.ledgerboy.plugins.api.FetchWindow
import com.eight87.ledgerboy.plugins.api.PluginState
import com.eight87.ledgerboy.ui.settings.catalog.SettingsCard
import com.eight87.ledgerboy.ui.settings.catalog.SettingsDimens
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

/**
 * X.8 — the Configure-flow shell. Hosts a single plugin's own
 * `ConfigScreen`, supplies the host capabilities (config / credentials /
 * guarded http / SAF picker), and renders the license + privacy
 * statement header so the user sees what's underneath and what leaves
 * the device before activating.
 *
 * "Test connection" runs ONE real fetch (with the plugin briefly
 * activated so the network guard lets the call through); on success the
 * plugin transitions to [PluginState.Active] and the persisted state is
 * written. On failure it falls back to [PluginState.Configured] and the
 * error is shown — no silent activation.
 *
 * The SAF import picker is wired here: while this screen is composed, the
 * host's [AppGraph.importPicker] points at a launcher; it's cleared on
 * dispose so off-screen code can't trigger a picker.
 */
@Composable
fun PluginConfigScreen(
  graph: AppGraph,
  pluginId: String,
  onBack: () -> Unit,
) {
  val plugin = graph.pluginManifest.byId(pluginId)
  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  // SAF import picker plumbing (X.8). A pending deferred is completed by
  // the launcher callback so the plugin's suspend pickImportFile resumes.
  val pending = remember { mutableListOf<CompletableDeferred<String?>>() }
  val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
  ) { uri ->
    pending.removeFirstOrNull()?.complete(uri?.toString())
  }
  DisposableEffect(pluginId) {
    graph.importPicker = { mimeTypes ->
      val deferred = CompletableDeferred<String?>()
      pending.add(deferred)
      launcher.launch(mimeTypes.toTypedArray())
      deferred.await()
    }
    onDispose { graph.importPicker = null }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            plugin?.let { stringResource(it.displayNameRes) }
              ?: stringResource(R.string.plugin_config_title),
          )
        },
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
    snackbarHost = { SnackbarHost(snackbar) },
  ) { innerPadding ->
    if (plugin == null) {
      Text(
        text = stringResource(R.string.plugins_empty_category),
        modifier = Modifier.padding(innerPadding).padding(SettingsDimens.PagePadding),
      )
      return@Scaffold
    }

    val host = remember(pluginId) { graph.pluginHost(pluginId) }
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = SettingsDimens.PagePadding),
      verticalArrangement = Arrangement.spacedBy(SettingsDimens.CardSpacing),
    ) {
      // License + privacy statement header.
      SettingsCard {
        Column(Modifier.padding(16.dp)) {
          Text(
            stringResource(R.string.plugin_config_license_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(plugin.licenseSpdx, style = MaterialTheme.typography.bodyMedium)
          Text(
            stringResource(R.string.plugin_config_privacy_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp),
          )
          Text(
            stringResource(plugin.privacyStatementRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // The plugin's own config UI.
      plugin.ConfigScreen(host)

      // Test connection → one real fetch → activate on success.
      Button(
        onClick = {
          scope.launch {
            // Briefly mark configured+active so the guard admits the test call.
            graph.pluginRuntime.markConfigured(pluginId)
            graph.pluginRuntime.activate(pluginId)
            val result = plugin.fetch(FetchWindow(), host, graph.rowSink)
            when (result) {
              is FetchResult.Ok -> {
                graph.pluginConfigStore.persistState(pluginId, PluginState.Active)
                snackbar.showSnackbar("OK · ${result.rowsWritten} rows")
              }
              is FetchResult.Error -> {
                graph.pluginRuntime.markConfigured(pluginId)
                graph.pluginConfigStore.persistState(pluginId, PluginState.Configured)
                snackbar.showSnackbar(result.message)
              }
            }
          }
        },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(stringResource(R.string.plugins_test_connection))
      }
    }
  }
}
