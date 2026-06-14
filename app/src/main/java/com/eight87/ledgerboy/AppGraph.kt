package com.eight87.ledgerboy

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.WorkManager
import com.eight87.ledgerboy.plugins.api.PluginHost
import com.eight87.ledgerboy.plugins.api.PluginManifest
import com.eight87.ledgerboy.plugins.api.RowSink
import com.eight87.ledgerboy.plugins.runtime.AppPluginHost
import com.eight87.ledgerboy.plugins.runtime.InMemoryRowSink
import com.eight87.ledgerboy.plugins.runtime.KeystoreCredentialStore
import com.eight87.ledgerboy.plugins.runtime.PluginConfigStore
import com.eight87.ledgerboy.plugins.runtime.PluginNetworkGuard
import com.eight87.ledgerboy.plugins.runtime.PluginRuntime
import com.eight87.ledgerboy.plugins.runtime.PluginScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

private val Context.pluginDataStore: DataStore<Preferences> by preferencesDataStore(name = "plugins")

/**
 * A.5 / X — composition root. The single place that knows concrete types;
 * everything above the data layer depends on the abstractions wired here
 * (DIP). Hand-rolled (no DI framework in v1, per CLAUDE.md).
 *
 * Phase X wires the connector-plugin runtime: the lifecycle [PluginRuntime],
 * the namespaced [PluginConfigStore], the Keystore [credentialStore], the
 * guarded [http] client, and the WorkManager [pluginScheduler]. Room DAOs
 * (Phase C) and the FX cache (Phase D) join here as they land; the
 * [rowSink] becomes Room-backed at that point (today it's in-memory since
 * the manifest is empty).
 */
class AppGraph(
  val applicationContext: Context,
) {
  val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob())

  // ── Plugin runtime (Phase X) ──
  val pluginManifest = PluginManifest

  val pluginConfigStore = PluginConfigStore(applicationContext.pluginDataStore)

  val credentialStore = KeystoreCredentialStore(pluginConfigStore)

  val pluginRuntime = PluginRuntime(pluginManifest.all.map { it.id })

  val guardedHttpClient: OkHttpClient = PluginNetworkGuard.buildClient(pluginRuntime)

  val pluginScheduler = PluginScheduler(WorkManager.getInstance(applicationContext))

  /** Interim in-memory sink; becomes Room-backed in Phase C/D. */
  val rowSink: RowSink = InMemoryRowSink()

  /**
   * Settable SAF import-picker hook: the Configure-flow shell registers a
   * launcher while a plugin's ConfigScreen is composed; null off the UI.
   */
  @Volatile
  var importPicker: (suspend (List<String>) -> String?)? = null

  /** Build a plugin-bound [PluginHost]. */
  fun pluginHost(pluginId: String): PluginHost =
    AppPluginHost(
      pluginId = pluginId,
      config = pluginConfigStore,
      credentials = credentialStore,
      http = guardedHttpClient,
      importPickerProvider = { importPicker },
    )
}
