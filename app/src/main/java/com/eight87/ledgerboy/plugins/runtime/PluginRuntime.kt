package com.eight87.ledgerboy.plugins.runtime

import com.eight87.ledgerboy.plugins.api.PluginState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * X.1 — host-owned lifecycle state machine for every plugin, plus the
 * X.4 per-plugin network counters.
 *
 * The runtime is the single owner of plugin state transitions
 * (connector-plugins.md §4). Valid moves:
 *
 *  Disabled →(enable)→ Enabled →(markConfigured)→ Configured →(activate)→ Active
 *
 * `disable()` is always legal and returns to [PluginState.Disabled] — it
 * is the kill-switch (§7); the [PluginNetworkGuard] reads the current
 * state on every request, so a disable halts in-flight scheduling within
 * one tick. Re-enabling a previously-configured plugin starts again from
 * [PluginState.Enabled]; whether its persisted config is still valid is
 * the plugin's concern (it may go straight to Configured via
 * [markConfigured] if its credentials are intact).
 *
 * Framework-free and synchronous so the transition logic is fully
 * Robolectric/JVM unit-testable. Persistence (writing the state back to
 * the per-plugin DataStore) is layered on top by the caller, not baked
 * in here (SRP).
 */
class PluginRuntime(
  knownPluginIds: Collection<String> = emptyList(),
) {
  private val states: MutableMap<String, MutableStateFlow<PluginState>> =
    knownPluginIds.associateWith { MutableStateFlow(PluginState.Disabled) }.toMutableMap()

  private val counters: MutableMap<String, MutableStateFlow<NetworkCounters>> =
    knownPluginIds.associateWith { MutableStateFlow(NetworkCounters()) }.toMutableMap()

  /** Emits a plugin id whenever its state changes — schedulers observe this. */
  val transitions = MutableSharedFlow<String>(extraBufferCapacity = 64)

  /** Register a plugin id discovered after construction (e.g. from the manifest). */
  fun register(pluginId: String, initial: PluginState = PluginState.Disabled) {
    states.getOrPut(pluginId) { MutableStateFlow(initial) }
    counters.getOrPut(pluginId) { MutableStateFlow(NetworkCounters()) }
  }

  fun state(pluginId: String): StateFlow<PluginState> =
    states.getOrPut(pluginId) { MutableStateFlow(PluginState.Disabled) }.asStateFlow()

  fun currentState(pluginId: String): PluginState =
    states[pluginId]?.value ?: PluginState.Disabled

  fun isActive(pluginId: String): Boolean = currentState(pluginId) == PluginState.Active

  // ── Transitions ──────────────────────────────────────────────────

  /** Disabled → Enabled. No-op if already past Enabled; illegal only from… nowhere (always allowed up from Disabled). */
  fun enable(pluginId: String): Boolean = transition(pluginId, PluginState.Enabled) {
    it == PluginState.Disabled
  }

  /** Enabled → Configured (the plugin reports valid config). */
  fun markConfigured(pluginId: String): Boolean = transition(pluginId, PluginState.Configured) {
    it == PluginState.Enabled || it == PluginState.Active
  }

  /** Configured → Active (Test connection succeeded). */
  fun activate(pluginId: String): Boolean = transition(pluginId, PluginState.Active) {
    it == PluginState.Configured
  }

  /** Any → Disabled. The kill-switch; always legal. */
  fun disable(pluginId: String): Boolean = transition(pluginId, PluginState.Disabled) { true }

  /** Restore a persisted state on boot without enforcing the step-by-step path. */
  fun restore(pluginId: String, state: PluginState) {
    val flow = states.getOrPut(pluginId) { MutableStateFlow(PluginState.Disabled) }
    flow.value = state
  }

  private inline fun transition(
    pluginId: String,
    target: PluginState,
    allowedFrom: (PluginState) -> Boolean,
  ): Boolean {
    val flow = states.getOrPut(pluginId) { MutableStateFlow(PluginState.Disabled) }
    if (!allowedFrom(flow.value)) return false
    if (flow.value == target) return true
    flow.value = target
    transitions.tryEmit(pluginId)
    return true
  }

  // ── X.4 network accounting ───────────────────────────────────────

  fun counters(pluginId: String): StateFlow<NetworkCounters> =
    counters.getOrPut(pluginId) { MutableStateFlow(NetworkCounters()) }.asStateFlow()

  /** Record one completed request's traffic. Called by [PluginNetworkGuard]. */
  fun recordTraffic(pluginId: String, bytesIn: Long, bytesOut: Long, endpoint: String) {
    val flow = counters.getOrPut(pluginId) { MutableStateFlow(NetworkCounters()) }
    val c = flow.value
    flow.value = c.copy(
      bytesIn = c.bytesIn + bytesIn,
      bytesOut = c.bytesOut + bytesOut,
      fetchCount = c.fetchCount + 1,
      endpoints = (c.endpoints + endpoint).toSortedSet().toList(),
    )
  }

  /** Record one failed request. Called by [PluginNetworkGuard]. */
  fun recordError(pluginId: String) {
    val flow = counters.getOrPut(pluginId) { MutableStateFlow(NetworkCounters()) }
    val c = flow.value
    flow.value = c.copy(errorCount = c.errorCount + 1)
  }

  /** Snapshot of every plugin's state — drives the Plugins screen list. */
  val allStates: StateFlow<Map<String, PluginState>>
    get() {
      // Built lazily from the live per-plugin flows; the Plugins screen
      // collects each plugin's own state() flow, so this convenience view
      // is a point-in-time snapshot used by tests / logs.
      val snapshot = states.mapValues { it.value.value }
      return MutableStateFlow(snapshot).asStateFlow()
    }
}

/** X.4 — per-plugin network accounting (connector-plugins.md transparency §). */
data class NetworkCounters(
  val bytesIn: Long = 0,
  val bytesOut: Long = 0,
  val fetchCount: Long = 0,
  val errorCount: Long = 0,
  val endpoints: List<String> = emptyList(),
)
