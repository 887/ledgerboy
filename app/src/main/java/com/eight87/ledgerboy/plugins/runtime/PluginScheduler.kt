package com.eight87.ledgerboy.plugins.runtime

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.eight87.ledgerboy.LedgerboyApplication
import com.eight87.ledgerboy.plugins.api.FetchResult
import com.eight87.ledgerboy.plugins.api.FetchWindow
import com.eight87.ledgerboy.plugins.api.PluginState
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * X.5 — per-plugin background-sync scheduler over WorkManager.
 *
 * Scheduling is per-plugin and opt-in (plugins default to fetch-on-user-
 * action; background sync is a per-plugin flag in [PluginConfigStore]).
 * The kill-switch (connector-plugins.md §7) is honoured within one tick:
 * the worker re-reads the live [PluginState] at run time and does nothing
 * if the plugin is no longer [PluginState.Active], so disabling a plugin
 * stops its next fetch even if its periodic work is still enqueued.
 *
 * The run/skip decision is [shouldRun] — a pure function unit-tested
 * without WorkManager.
 */
class PluginScheduler(
  private val workManager: WorkManager,
) {
  /** Enqueue (or replace) a plugin's periodic sync. */
  fun schedule(pluginId: String, interval: Duration) {
    val request = PeriodicWorkRequestBuilder<PluginSyncWorker>(
      interval.toMinutes().coerceAtLeast(MIN_INTERVAL_MINUTES), TimeUnit.MINUTES,
    )
      .setConstraints(
        Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build(),
      )
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
      .setInputData(workDataOf(KEY_PLUGIN_ID to pluginId))
      .addTag(tagFor(pluginId))
      .build()
    workManager.enqueueUniquePeriodicWork(
      uniqueName(pluginId),
      ExistingPeriodicWorkPolicy.UPDATE,
      request,
    )
  }

  /** Cancel a plugin's periodic sync (also part of the kill-switch). */
  fun cancel(pluginId: String) {
    workManager.cancelUniqueWork(uniqueName(pluginId))
  }

  companion object {
    const val KEY_PLUGIN_ID = "plugin_id"
    private const val MIN_INTERVAL_MINUTES = 15L
    private fun uniqueName(pluginId: String) = "plugin-sync-$pluginId"
    fun tagFor(pluginId: String) = "plugin-sync:$pluginId"

    /**
     * Pure kill-switch decision: a scheduled fetch runs only when the
     * plugin is Active AND the user opted into background sync. Any other
     * state → skip (no fetch, no network).
     */
    fun shouldRun(state: PluginState, backgroundSyncOptIn: Boolean): Boolean =
      state == PluginState.Active && backgroundSyncOptIn
  }
}

/**
 * The WorkManager worker that performs one scheduled fetch. Re-checks the
 * live plugin state on every run so a disabled plugin's enqueued work
 * becomes a no-op (kill-switch within one tick).
 */
class PluginSyncWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val pluginId = inputData.getString(PluginScheduler.KEY_PLUGIN_ID) ?: return Result.failure()
    val graph = (applicationContext as LedgerboyApplication).graph

    val plugin = graph.pluginManifest.byId(pluginId) ?: return Result.success()
    val state = graph.pluginRuntime.currentState(pluginId)
    val bgOptIn = graph.pluginConfigStore.backgroundSyncEnabled(pluginId)
    if (!PluginScheduler.shouldRun(state, bgOptIn)) return Result.success()

    return when (
      plugin.fetch(FetchWindow(), graph.pluginHost(pluginId), graph.rowSink)
    ) {
      is FetchResult.Ok -> Result.success()
      is FetchResult.Error -> Result.retry()
    }
  }
}
