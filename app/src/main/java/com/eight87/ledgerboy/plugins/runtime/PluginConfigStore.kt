package com.eight87.ledgerboy.plugins.runtime

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.eight87.ledgerboy.plugins.api.PluginState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * X.3 — per-plugin namespaced config persistence. Every key is prefixed
 * with the plugin id (`plugin.<id>.<key>`) so two plugins can both use a
 * key named "username" without colliding. Non-secret config only —
 * secrets go through [com.eight87.ledgerboy.plugins.api.CredentialStore].
 *
 * Also persists each plugin's lifecycle [PluginState] (key
 * `plugin.<id>.__state`) so an enabled/active plugin survives a restart.
 *
 * Backed by a single Preferences [DataStore] injected from the AppGraph;
 * a fake in-memory DataStore makes this Robolectric-testable.
 */
class PluginConfigStore(
  private val dataStore: DataStore<Preferences>,
) {
  private fun ns(pluginId: String, key: String) = "plugin.$pluginId.$key"
  private fun stateKey(pluginId: String) = stringPreferencesKey(ns(pluginId, STATE_KEY))

  suspend fun putString(pluginId: String, key: String, value: String) {
    dataStore.edit { it[stringPreferencesKey(ns(pluginId, key))] = value }
  }

  fun stringFlow(pluginId: String, key: String): Flow<String?> =
    dataStore.data.map { it[stringPreferencesKey(ns(pluginId, key))] }

  suspend fun getString(pluginId: String, key: String): String? =
    stringFlow(pluginId, key).first()

  suspend fun putBoolean(pluginId: String, key: String, value: Boolean) {
    dataStore.edit { it[booleanPreferencesKey(ns(pluginId, key))] = value }
  }

  suspend fun getBoolean(pluginId: String, key: String, default: Boolean = false): Boolean =
    dataStore.data.map { it[booleanPreferencesKey(ns(pluginId, key))] ?: default }.first()

  suspend fun putLong(pluginId: String, key: String, value: Long) {
    dataStore.edit { it[longPreferencesKey(ns(pluginId, key))] = value }
  }

  suspend fun getLong(pluginId: String, key: String, default: Long = 0L): Long =
    dataStore.data.map { it[longPreferencesKey(ns(pluginId, key))] ?: default }.first()

  // ── Lifecycle-state persistence ──────────────────────────────────

  suspend fun persistState(pluginId: String, state: PluginState) {
    dataStore.edit { it[stateKey(pluginId)] = state.name }
  }

  /** Read every persisted plugin state so the runtime can be restored on boot. */
  suspend fun restoreAllStates(): Map<String, PluginState> {
    val prefs = dataStore.data.first()
    val out = mutableMapOf<String, PluginState>()
    prefs.asMap().forEach { (k, v) ->
      val name = k.name
      if (name.startsWith("plugin.") && name.endsWith(".$STATE_KEY")) {
        val pluginId = name.removePrefix("plugin.").removeSuffix(".$STATE_KEY")
        val state = runCatching { PluginState.valueOf(v as String) }.getOrNull()
        if (state != null) out[pluginId] = state
      }
    }
    return out
  }

  /** Per-plugin background-sync opt-in flag (default off — fetch-on-action). */
  suspend fun setBackgroundSync(pluginId: String, enabled: Boolean) =
    putBoolean(pluginId, BACKGROUND_SYNC_KEY, enabled)

  suspend fun backgroundSyncEnabled(pluginId: String): Boolean =
    getBoolean(pluginId, BACKGROUND_SYNC_KEY, default = false)

  private companion object {
    const val STATE_KEY = "__state"
    const val BACKGROUND_SYNC_KEY = "__bg_sync"
  }
}
