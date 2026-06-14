package com.eight87.ledgerboy.plugins.api

import com.eight87.ledgerboy.plugins.runtime.PluginConfigStore
import okhttp3.OkHttpClient

/**
 * X.8 — the host surface a plugin sees, in its `ConfigScreen` and during
 * `fetch`. Narrow by design (ISP): a plugin gets exactly the host
 * capabilities the architecture sanctions and nothing more.
 *
 *  - [config] — namespaced persistence for the plugin's own settings
 *    (never collides with other plugins).
 *  - [credentials] — Keystore-wrapped secret storage (security-decisions.md).
 *  - [http] — the shared OkHttp client wrapped by [PluginNetworkGuard];
 *    the ONLY sanctioned way for a plugin to reach the network, so every
 *    call is gated + accounted.
 *  - [pickImportFile] — SAF one-shot document picker hook, for import plugins.
 */
interface PluginHost {
  /** Per-plugin namespaced config store. */
  val config: PluginConfigStore

  /** Keystore-wrapped credential store for secrets (API keys, tokens). */
  val credentials: CredentialStore

  /**
   * The guarded shared HTTP client. Requests issued through it are
   * tagged with the plugin id, gated on `state == Active`, and have
   * their bytes / counts accounted by [PluginNetworkGuard]. A plugin
   * must not construct its own [OkHttpClient].
   */
  val http: OkHttpClient

  /**
   * Launch the SAF document picker for an import file and suspend until
   * the user picks one (or cancels → null). The returned string is a
   * content URI the plugin can re-open via the host. Import plugins
   * only; banking / FX / asset plugins do not call this.
   */
  suspend fun pickImportFile(mimeTypes: List<String>): String?
}

/**
 * Keystore-wrapped secret storage seam (security-decisions.md). The
 * Phase X scaffold provides the interface + a basic Android Keystore
 * impl; the full Strongbox + biometric-gating wiring lands in the
 * security phase. Test seam: an in-memory impl for unit tests.
 */
interface CredentialStore {
  suspend fun put(pluginId: String, key: String, secret: String)
  suspend fun get(pluginId: String, key: String): String?
  suspend fun remove(pluginId: String, key: String)
}
