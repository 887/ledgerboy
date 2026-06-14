package com.eight87.ledgerboy

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * A.5 — composition root. The single place that knows concrete types;
 * everything above the data layer depends on the abstractions wired here
 * (DIP). Hand-rolled (no DI framework in v1, per CLAUDE.md).
 *
 * Phase A holds only the application context + an app-lifetime
 * coroutine scope. Repositories, the plugin runtime (Phase X), Room DAOs
 * (Phase C), and the FX cache (Phase D) are constructed here as they
 * land — destinations receive them through this graph, never by reaching
 * for singletons.
 */
class AppGraph(
  val applicationContext: Context,
) {
  /** App-lifetime scope for fire-and-forget work that outlives a screen. */
  val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob())
}
