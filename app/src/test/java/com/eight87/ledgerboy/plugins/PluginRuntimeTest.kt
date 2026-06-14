package com.eight87.ledgerboy.plugins

import com.eight87.ledgerboy.plugins.api.PluginState
import com.eight87.ledgerboy.plugins.runtime.PluginRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** X.9 — lifecycle state-machine transitions + per-plugin counters. */
class PluginRuntimeTest {

  private val id = "test-plugin"

  @Test
  fun `happy path walks disabled to active`() {
    val rt = PluginRuntime(listOf(id))
    assertEquals(PluginState.Disabled, rt.currentState(id))
    assertTrue(rt.enable(id))
    assertEquals(PluginState.Enabled, rt.currentState(id))
    assertTrue(rt.markConfigured(id))
    assertEquals(PluginState.Configured, rt.currentState(id))
    assertTrue(rt.activate(id))
    assertEquals(PluginState.Active, rt.currentState(id))
    assertTrue(rt.isActive(id))
  }

  @Test
  fun `cannot activate before configured`() {
    val rt = PluginRuntime(listOf(id))
    rt.enable(id)
    // Skipping markConfigured: activate must be rejected.
    assertFalse(rt.activate(id))
    assertEquals(PluginState.Enabled, rt.currentState(id))
  }

  @Test
  fun `cannot enable from a non-disabled state`() {
    val rt = PluginRuntime(listOf(id))
    rt.enable(id)
    rt.markConfigured(id)
    // enable() is only legal from Disabled.
    assertFalse(rt.enable(id))
    assertEquals(PluginState.Configured, rt.currentState(id))
  }

  @Test
  fun `disable is always legal and is the kill switch`() {
    val rt = PluginRuntime(listOf(id))
    rt.enable(id); rt.markConfigured(id); rt.activate(id)
    assertTrue(rt.isActive(id))
    assertTrue(rt.disable(id))
    assertEquals(PluginState.Disabled, rt.currentState(id))
    assertFalse(rt.isActive(id))
  }

  @Test
  fun `restore sets state without enforcing the path`() {
    val rt = PluginRuntime()
    rt.restore(id, PluginState.Active)
    assertEquals(PluginState.Active, rt.currentState(id))
  }

  @Test
  fun `counters accumulate traffic and dedupe endpoints`() {
    val rt = PluginRuntime(listOf(id))
    rt.recordTraffic(id, bytesIn = 100, bytesOut = 20, endpoint = "https://ecb.example/rates")
    rt.recordTraffic(id, bytesIn = 50, bytesOut = 10, endpoint = "https://ecb.example/rates")
    rt.recordError(id)
    val c = rt.counters(id).value
    assertEquals(150, c.bytesIn)
    assertEquals(30, c.bytesOut)
    assertEquals(2, c.fetchCount)
    assertEquals(1, c.errorCount)
    assertEquals(listOf("https://ecb.example/rates"), c.endpoints)
  }
}
