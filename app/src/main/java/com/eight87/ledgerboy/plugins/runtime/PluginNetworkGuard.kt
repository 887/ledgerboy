package com.eight87.ledgerboy.plugins.runtime

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * X.4 — the transport-layer enforcement of "nothing phones home unless an
 * Active plugin asks" (connector-plugins.md privacy posture).
 *
 * Every request a plugin issues must be tagged with its plugin id (via
 * [taggedFor]). The interceptor:
 *  1. Reads the [PluginIdTag]; an untagged request is rejected outright
 *     (no host code is allowed to call out outside a plugin).
 *  2. Rejects the request unless the runtime reports that plugin
 *     [PluginState.Active] — this is the kill-switch at the wire.
 *  3. On a completed response, accounts bytes-in/out + fetch count into
 *     the runtime's per-plugin counters; on failure, the error count.
 *
 * A single shared [OkHttpClient] carries this interceptor; plugins
 * receive it through [com.eight87.ledgerboy.plugins.api.PluginHost.http]
 * and must not build their own client.
 */
class PluginNetworkGuard(
  private val runtime: PluginRuntime,
) : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val tag = request.tag(PluginIdTag::class.java)
      ?: throw IOException(
        "ledgerboy: rejected an untagged network request — all calls must " +
          "originate from a plugin via host.http.taggedFor(pluginId).",
      )
    val pluginId = tag.pluginId

    if (!runtime.isActive(pluginId)) {
      throw IOException(
        "ledgerboy: rejected network request from plugin '$pluginId' — it is " +
          "not Active (current state ${runtime.currentState(pluginId)}).",
      )
    }

    val bytesOut = request.body?.contentLength()?.coerceAtLeast(0L) ?: 0L
    return try {
      val response = chain.proceed(request)
      val bytesIn = response.body.contentLength().coerceAtLeast(0L)
      runtime.recordTraffic(
        pluginId = pluginId,
        bytesIn = bytesIn,
        bytesOut = bytesOut,
        endpoint = "${request.url.scheme}://${request.url.host}${request.url.encodedPath}",
      )
      response
    } catch (e: IOException) {
      runtime.recordError(pluginId)
      throw e
    }
  }

  companion object {
    /**
     * Build the shared guarded client. Plugins get this instance and
     * derive per-call tagged requests from it.
     */
    fun buildClient(runtime: PluginRuntime): OkHttpClient =
      OkHttpClient.Builder()
        .addInterceptor(PluginNetworkGuard(runtime))
        .build()
  }
}

/** OkHttp request tag carrying the originating plugin id. */
data class PluginIdTag(val pluginId: String)

/**
 * Tag a request builder with its owning plugin id so the
 * [PluginNetworkGuard] can gate + account it. Plugins call this on every
 * request they build from `host.http`.
 */
fun Request.Builder.taggedFor(pluginId: String): Request.Builder =
  tag(PluginIdTag::class.java, PluginIdTag(pluginId))
