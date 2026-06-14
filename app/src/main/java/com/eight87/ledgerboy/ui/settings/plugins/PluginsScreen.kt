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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.ledgerboy.AppGraph
import com.eight87.ledgerboy.R
import com.eight87.ledgerboy.plugins.api.ConnectorPlugin
import com.eight87.ledgerboy.plugins.api.PluginCategory
import com.eight87.ledgerboy.plugins.api.PluginState
import com.eight87.ledgerboy.ui.settings.catalog.SettingsCard
import com.eight87.ledgerboy.ui.settings.catalog.SettingsDimens
import com.eight87.ledgerboy.ui.settings.catalog.SettingsRow

/**
 * X.6 — Settings → Plugins. One section per [PluginCategory], each
 * holding the manifest plugins in that category as cards: state chip,
 * enable/disable toggle, and Configure. A privacy banner restates the
 * disabled-by-default / zero-calls posture. A top-bar action opens the
 * X.7 Network-usage audit.
 *
 * Built on the catalog DSL (ui-shell.md). Driven entirely by the
 * manifest + runtime, so when plugins land (Phase B+) cards appear with
 * no change here.
 */
@Composable
fun PluginsScreen(
  graph: AppGraph,
  onBack: () -> Unit,
  onConfigure: (String) -> Unit,
  onOpenNetworkUsage: () -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.plugins_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.cd_back),
            )
          }
        },
        actions = {
          IconButton(onClick = onOpenNetworkUsage) {
            Icon(
              Icons.Filled.NetworkCheck,
              contentDescription = stringResource(R.string.plugins_network_usage),
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
      Spacer(Modifier.height(4.dp))
      PrivacyBanner()

      PluginCategory.entries.forEach { category ->
        val plugins = graph.pluginManifest.byCategory(category)
        SettingsCard(title = stringResource(categoryLabel(category))) {
          if (plugins.isEmpty()) {
            SettingsRow(
              id = "plugins.empty.${category.name}",
              icon = categoryIcon(category),
              label = stringResource(R.string.plugins_empty_category),
            )
          } else {
            plugins.forEach { plugin ->
              PluginRow(graph, plugin, onConfigure)
            }
          }
        }
      }

      // Network usage entry also reachable as a row (mirrors the action).
      SettingsCard {
        SettingsRow(
          id = "plugins.network_usage",
          icon = Icons.Filled.NetworkCheck,
          label = stringResource(R.string.plugins_network_usage),
          subtitle = stringResource(R.string.plugins_network_usage_sub),
          onClick = onOpenNetworkUsage,
        )
      }
      Spacer(Modifier.height(8.dp))
    }
  }
}

@Composable
private fun PrivacyBanner() {
  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.secondaryContainer,
    ),
  ) {
    Row(modifier = Modifier.padding(16.dp)) {
      Icon(
        Icons.Outlined.Extension,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSecondaryContainer,
      )
      Spacer(Modifier.padding(horizontal = 8.dp))
      Text(
        text = stringResource(R.string.plugins_privacy_banner),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
      )
    }
  }
}

@Composable
private fun PluginRow(
  graph: AppGraph,
  plugin: ConnectorPlugin,
  onConfigure: (String) -> Unit,
) {
  val state by graph.pluginRuntime.state(plugin.id).collectAsStateWithLifecycle()
  SettingsRow(
    id = "plugin.${plugin.id}",
    icon = categoryIcon(plugin.category),
    label = stringResource(plugin.displayNameRes),
    subtitle = stringResource(stateLabel(state)),
    trailing = {
      Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        if (state != PluginState.Disabled) {
          TextButton(onClick = { onConfigure(plugin.id) }) {
            Text(stringResource(R.string.plugins_configure))
          }
        }
        Switch(
          checked = state != PluginState.Disabled,
          onCheckedChange = { enabled ->
            if (enabled) graph.pluginRuntime.enable(plugin.id)
            else graph.pluginRuntime.disable(plugin.id)
          },
        )
      }
    },
  )
}

internal fun categoryLabel(category: PluginCategory): Int = when (category) {
  PluginCategory.Banking -> R.string.plugins_category_banking
  PluginCategory.AssetPrice -> R.string.plugins_category_asset_price
  PluginCategory.Fx -> R.string.plugins_category_fx
  PluginCategory.Import -> R.string.plugins_category_import
  PluginCategory.RegionalIndex -> R.string.plugins_category_regional_index
}

internal fun categoryIcon(category: PluginCategory) = when (category) {
  PluginCategory.Banking -> Icons.Filled.AccountBalance
  PluginCategory.AssetPrice -> Icons.Filled.Diamond
  PluginCategory.Fx -> Icons.Filled.CurrencyExchange
  PluginCategory.Import -> Icons.Filled.UploadFile
  PluginCategory.RegionalIndex -> Icons.Filled.Map
}

internal fun stateLabel(state: PluginState): Int = when (state) {
  PluginState.Disabled -> R.string.plugins_state_disabled
  PluginState.Enabled -> R.string.plugins_state_enabled
  PluginState.Configured -> R.string.plugins_state_configured
  PluginState.Active -> R.string.plugins_state_active
}
