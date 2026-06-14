@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.eight87.ledgerboy.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.eight87.ledgerboy.AppGraph
import com.eight87.ledgerboy.R
import com.eight87.ledgerboy.ui.accounts.AccountsScreen
import com.eight87.ledgerboy.ui.categories.CategoriesScreen
import com.eight87.ledgerboy.ui.overview.OverviewScreen
import com.eight87.ledgerboy.ui.screens.PlaceholderScreen
import com.eight87.ledgerboy.ui.settings.AboutScreen
import com.eight87.ledgerboy.ui.settings.LicensesScreen
import com.eight87.ledgerboy.ui.settings.SettingsScreen
import com.eight87.ledgerboy.ui.settings.plugins.NetworkUsageScreen
import com.eight87.ledgerboy.ui.settings.plugins.PluginConfigScreen
import com.eight87.ledgerboy.ui.settings.plugins.PluginsScreen
import com.eight87.ledgerboy.ui.transactions.TransactionsScreen

/**
 * A.5 — root composable. The locked family shell: a vertical navigation
 * rail on the left, a top app bar per root destination, and a
 * Navigation 3 [NavDisplay] hosting the destination bodies.
 *
 * The rail switches the active *root* (collapsing the stack); sub-pages
 * (About, Licenses) push on top and bring their own full-screen
 * Scaffold + back arrow. Adding a destination is one [RailItem] entry +
 * one `entry<>` arm here — the rail and the dispatcher stay closed
 * against modification of their existing arms (OCP).
 *
 * [graph] is the composition root handed in from the activity; the
 * plugin screens read the runtime/manifest from it, and future surfaces
 * (Phase C+) pull their repositories the same way.
 */
@Composable
fun LedgerboyApp(graph: AppGraph) {
  val backStack = remember { LedgerboyBackStack() }
  // The active root for rail highlighting is the bottom of the stack;
  // sub-pages push above it but keep their root selected.
  val activeRoot = backStack.backStack.first()

  Row(modifier = Modifier.fillMaxSize()) {
    NavigationRail {
      RailItem.entries.forEach { item ->
        NavigationRailItem(
          selected = activeRoot == item.destination,
          onClick = { backStack.switchRoot(item.destination) },
          icon = { Icon(item.icon, contentDescription = null) },
          label = { Text(stringResource(item.labelRes)) },
        )
      }
    }

    Box(modifier = Modifier.fillMaxSize()) {
      NavDisplay(
        backStack = backStack.backStack,
        onBack = { backStack.pop() },
        modifier = Modifier.fillMaxSize(),
        entryProvider = entryProvider {
          entry<Overview> {
            RootScaffold(R.string.dest_overview) {
              OverviewScreen(ledger = graph.ledgerRepository, modifier = it)
            }
          }
          entry<Accounts> {
            RootScaffold(R.string.dest_accounts) {
              AccountsScreen(
                ledger = graph.ledgerRepository,
                modifier = it,
                onAccountClick = { account ->
                  backStack.push(AccountTransactions(account.id, account.displayName))
                },
              )
            }
          }
          entry<Budgets> {
            RootScaffold(R.string.dest_budgets) {
              PlaceholderScreen(
                icon = RailItem.BUDGETS.icon,
                title = stringResource(R.string.dest_budgets),
                description = stringResource(R.string.placeholder_budgets),
                modifier = it,
              )
            }
          }
          entry<Assets> {
            RootScaffold(R.string.dest_assets) {
              PlaceholderScreen(
                icon = RailItem.ASSETS.icon,
                title = stringResource(R.string.dest_assets),
                description = stringResource(R.string.placeholder_assets),
                modifier = it,
              )
            }
          }
          entry<Charts> {
            RootScaffold(R.string.dest_charts) {
              PlaceholderScreen(
                icon = RailItem.CHARTS.icon,
                title = stringResource(R.string.dest_charts),
                description = stringResource(R.string.placeholder_charts),
                modifier = it,
              )
            }
          }
          entry<SettingsRoot> {
            RootScaffold(R.string.dest_settings) {
              SettingsScreen(
                onOpenAbout = { backStack.push(SettingsAbout) },
                onOpenPlugins = { backStack.push(SettingsPlugins) },
                onOpenCategories = { backStack.push(SettingsCategories) },
                modifier = it,
              )
            }
          }
          entry<SettingsAbout> {
            AboutScreen(
              onBack = { backStack.pop() },
              onOpenLicenses = { backStack.push(SettingsLicenses) },
            )
          }
          entry<SettingsLicenses> {
            LicensesScreen(onBack = { backStack.pop() })
          }
          entry<SettingsPlugins> {
            PluginsScreen(
              graph = graph,
              onBack = { backStack.pop() },
              onConfigure = { id -> backStack.push(SettingsPluginConfig(id)) },
              onOpenNetworkUsage = { backStack.push(SettingsNetworkUsage) },
            )
          }
          entry<SettingsNetworkUsage> {
            NetworkUsageScreen(graph = graph, onBack = { backStack.pop() })
          }
          entry<SettingsPluginConfig> { key ->
            PluginConfigScreen(
              graph = graph,
              pluginId = key.pluginId,
              onBack = { backStack.pop() },
            )
          }
          entry<AccountTransactions> { key ->
            TransactionsScreen(
              ledger = graph.ledgerRepository,
              accountId = key.accountId,
              accountName = key.accountName,
              onBack = { backStack.pop() },
            )
          }
          entry<SettingsCategories> {
            CategoriesScreen(ledger = graph.ledgerRepository, onBack = { backStack.pop() })
          }
        },
      )
    }
  }
}

/**
 * Top-bar scaffold for a root destination. The title is the destination
 * label; the body gets the scaffold's content padding via the [Modifier]
 * passed to [content].
 */
@Composable
private fun RootScaffold(
  titleRes: Int,
  content: @Composable (Modifier) -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(title = { Text(stringResource(titleRes)) })
    },
  ) { innerPadding ->
    content(Modifier.fillMaxSize().padding(innerPadding))
  }
}
