package com.eight87.ledgerboy.plugins.api

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable

/**
 * X.1 — the connector plugin contract (connector-plugins.md, locked).
 *
 * Every banking / asset-price / FX / import / regional-index source is a
 * [ConnectorPlugin]: self-contained, compiled into the single APK,
 * disabled by default, and only fetching once the user has enabled AND
 * configured AND activated it.
 *
 * The interface is intentionally narrow. Plugins supply metadata, own
 * their config UI, and fetch when asked. The *host* owns everything
 * else — the lifecycle state machine ([PluginRuntime]), per-plugin
 * network accounting ([PluginNetworkGuard]), scheduling
 * ([PluginScheduler]), and the row writes. Plugins never touch Room
 * directly; they emit normalized rows through the [RowSink] the host
 * hands to [fetch] (DIP — the plugin depends on the abstract sink, not
 * the concrete database).
 *
 * Note vs. the connector-plugins.md Kotlin *sketch*: that sketch put
 * `val state: StateFlow<PluginState>` on the plugin. We moved lifecycle
 * state to the host [PluginRuntime] so the runtime is the single owner
 * of transitions (the doc explicitly calls it a sketch and says "the
 * host runs the scheduler / accounting / writes"). Plugins observe their
 * own state via `runtime.state(id)` when they need it.
 */
interface ConnectorPlugin {
  /** Stable id, e.g. "csv-import", "ecb-fx", "fints-client". */
  val id: String

  /** User-facing display name. */
  @get:StringRes
  val displayNameRes: Int

  val category: PluginCategory

  /** One-paragraph, localized description of the source. */
  @get:StringRes
  val descriptionRes: Int

  /** SPDX id of the plugin's own license, for the OSS-licenses audit. */
  val licenseSpdx: String

  /** Homepage of the upstream protocol / data source, if any. */
  val sourceUrl: String?

  /** Localized "what data leaves the device, to where" statement. */
  @get:StringRes
  val privacyStatementRes: Int

  /**
   * The plugin's own configuration UI. Never invoked by the host until
   * the plugin's state is at least [PluginState.Enabled]. The [host]
   * exposes the SAF picker, credential store, and config persistence
   * the plugin needs.
   */
  @Composable
  fun ConfigScreen(host: PluginHost)

  /**
   * Fetch data for [window] and emit normalized rows into [sink]. Never
   * invoked by the host until the plugin is [PluginState.Active]. Must
   * issue network calls only through `host.http` so the
   * [PluginNetworkGuard] can account for and gate them.
   */
  suspend fun fetch(window: FetchWindow, host: PluginHost, sink: RowSink): FetchResult
}

/** The five plugin categories — the Settings → Plugins accordion groups. */
enum class PluginCategory {
  Banking,
  AssetPrice,
  Fx,
  Import,
  RegionalIndex,
}

/**
 * Lifecycle state machine (connector-plugins.md §4). Only [Active]
 * plugins fetch or pass the network guard.
 *
 *  Disabled → Enabled → Configured → Active
 *
 * Disabling from any state returns to [Disabled] and unconditionally
 * halts network activity (the kill-switch).
 */
enum class PluginState {
  Disabled,
  Enabled,
  Configured,
  Active,
}

/** The time window a fetch should cover. Nulls mean "as far back / forward as the source allows". */
data class FetchWindow(
  val sinceEpochMillis: Long? = null,
  val untilEpochMillis: Long? = null,
)

/** Outcome of a [ConnectorPlugin.fetch]. */
sealed interface FetchResult {
  data class Ok(
    val rowsWritten: Int,
    val bytesIn: Long,
    val bytesOut: Long,
  ) : FetchResult

  data class Error(
    val message: String,
    val retryAfterMillis: Long? = null,
  ) : FetchResult
}
