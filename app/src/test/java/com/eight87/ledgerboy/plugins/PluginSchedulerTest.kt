package com.eight87.ledgerboy.plugins

import com.eight87.ledgerboy.plugins.api.PluginState
import com.eight87.ledgerboy.plugins.runtime.PluginScheduler
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** X.9 — the scheduler kill-switch decision. */
class PluginSchedulerTest {

  @Test
  fun `runs only when active and background sync opted in`() {
    assertTrue(PluginScheduler.shouldRun(PluginState.Active, backgroundSyncOptIn = true))
  }

  @Test
  fun `does not run when active but background sync off`() {
    assertFalse(PluginScheduler.shouldRun(PluginState.Active, backgroundSyncOptIn = false))
  }

  @Test
  fun `does not run when not active even with background sync on`() {
    assertFalse(PluginScheduler.shouldRun(PluginState.Configured, backgroundSyncOptIn = true))
    assertFalse(PluginScheduler.shouldRun(PluginState.Enabled, backgroundSyncOptIn = true))
    assertFalse(PluginScheduler.shouldRun(PluginState.Disabled, backgroundSyncOptIn = true))
  }
}
