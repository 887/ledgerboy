package com.eight87.ledgerboy.plugins

import com.eight87.ledgerboy.plugins.runtime.PluginNetworkGuard
import com.eight87.ledgerboy.plugins.runtime.PluginRuntime
import com.eight87.ledgerboy.plugins.runtime.taggedFor
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** X.9 — the network guard gates calls on Active state and accounts traffic. */
class PluginNetworkGuardTest {

  private lateinit var server: MockWebServer
  private val id = "ecb-fx"

  @Before fun setUp() {
    server = MockWebServer()
    server.start()
  }

  @After fun tearDown() {
    server.shutdown()
  }

  private fun get(client: okhttp3.OkHttpClient, tagged: Boolean) {
    val builder = Request.Builder().url(server.url("/rates"))
    if (tagged) builder.taggedFor(id)
    client.newCall(builder.build()).execute().use { it.body?.string() }
  }

  @Test
  fun `disabled plugin request is rejected`() {
    val rt = PluginRuntime(listOf(id))
    val client = PluginNetworkGuard.buildClient(rt)
    server.enqueue(MockResponse().setBody("should not be served"))
    try {
      get(client, tagged = true)
      fail("expected the guard to reject a disabled plugin's request")
    } catch (e: IOException) {
      assertTrue(e.message!!.contains("not Active"))
    }
    // The request never hit the wire.
    assertEquals(0, server.requestCount)
  }

  @Test
  fun `untagged request is rejected`() {
    val rt = PluginRuntime(listOf(id))
    val client = PluginNetworkGuard.buildClient(rt)
    try {
      get(client, tagged = false)
      fail("expected the guard to reject an untagged request")
    } catch (e: IOException) {
      assertTrue(e.message!!.contains("untagged"))
    }
    assertEquals(0, server.requestCount)
  }

  @Test
  fun `active plugin request goes through and is accounted`() {
    val rt = PluginRuntime(listOf(id))
    rt.enable(id); rt.markConfigured(id); rt.activate(id)
    val client = PluginNetworkGuard.buildClient(rt)
    server.enqueue(MockResponse().setBody("rate-data"))

    get(client, tagged = true)

    assertEquals(1, server.requestCount)
    val c = rt.counters(id).value
    assertEquals(1, c.fetchCount)
    assertTrue(c.endpoints.any { it.endsWith("/rates") })
  }
}
