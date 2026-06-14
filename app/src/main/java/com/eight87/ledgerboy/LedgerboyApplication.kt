package com.eight87.ledgerboy

import android.app.Application
import kotlinx.coroutines.launch

/**
 * A.5 — Application subclass. Owns the single [AppGraph] composition root
 * for the process lifetime; the activity reads it from here. No
 * network / IO / telemetry happens at startup — out-of-the-box ledgerboy
 * is silent on the wire (network posture, CLAUDE.md).
 */
class LedgerboyApplication : Application() {
  lateinit var graph: AppGraph
    private set

  override fun onCreate() {
    super.onCreate()
    graph = AppGraph(applicationContext = this)
    // Restore persisted plugin lifecycle states so an enabled/active
    // plugin survives a restart (no-op while the manifest is empty).
    graph.applicationScope.launch {
      graph.pluginConfigStore.restoreAllStates().forEach { (id, state) ->
        graph.pluginRuntime.restore(id, state)
      }
    }
  }
}
