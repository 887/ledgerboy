package com.eight87.ledgerboy.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.eight87.ledgerboy.R
import kotlinx.serialization.Serializable

/**
 * A.5 — Navigation 3 destinations for ledgerboy.
 *
 * Six top-level destinations sit behind the vertical navigation rail
 * (Overview / Accounts / Budgets / Assets / Charts / Settings).
 * Everything else (settings sub-pages, future detail screens) is pushed
 * onto the same single stack. The shell is a vertical rail + top bar —
 * the locked family shape (ui-shell.md); destinations land inside the
 * rail, they never modify it.
 *
 * All keys are `@Serializable` so the Navigation 3 back stack can
 * round-trip through `SavedStateHandle` for process-death survival.
 */
sealed interface Destination : NavKey

// ── Top-level rail destinations ──
@Serializable data object Overview : Destination
@Serializable data object Accounts : Destination
@Serializable data object Budgets : Destination
@Serializable data object Assets : Destination
@Serializable data object Charts : Destination
@Serializable data object SettingsRoot : Destination

// ── Settings sub-pages (pushed above SettingsRoot) ──
@Serializable data object SettingsAbout : Destination
@Serializable data object SettingsLicenses : Destination

/**
 * The rail items, in display order. Each maps a top-level [Destination]
 * to its rail icon + label. Adding a destination is one entry here plus
 * one arm in the [com.eight87.ledgerboy.ui.nav.LedgerboyApp] dispatcher —
 * the rail itself is closed against modification.
 */
enum class RailItem(
  val destination: Destination,
  val icon: ImageVector,
  val labelRes: Int,
) {
  OVERVIEW(Overview, Icons.Filled.Dashboard, R.string.dest_overview),
  ACCOUNTS(Accounts, Icons.Filled.AccountBalance, R.string.dest_accounts),
  BUDGETS(Budgets, Icons.Filled.AccountBalanceWallet, R.string.dest_budgets),
  ASSETS(Assets, Icons.Filled.Diamond, R.string.dest_assets),
  CHARTS(Charts, Icons.Filled.InsertChart, R.string.dest_charts),
  SETTINGS(SettingsRoot, Icons.Filled.Settings, R.string.dest_settings),
}
