package com.eight87.ledgerboy.plugins.runtime

import com.eight87.ledgerboy.plugins.api.CredentialStore
import com.eight87.ledgerboy.plugins.api.PluginHost
import okhttp3.OkHttpClient

/**
 * X.8 — concrete [PluginHost] handed to a single plugin. Bound to one
 * plugin id so the plugin can't reach another plugin's config/secrets,
 * and so SAF picking is routed through the host.
 *
 * [importPicker] is a settable hook the Configure-flow shell registers
 * (via `rememberLauncherForActivityResult`) while the plugin's
 * `ConfigScreen` is composed; off the UI (e.g. the background worker)
 * it is null and [pickImportFile] returns null — banking/FX/asset
 * plugins never call it anyway.
 */
class AppPluginHost(
  private val pluginId: String,
  override val config: PluginConfigStore,
  override val credentials: CredentialStore,
  override val http: OkHttpClient,
  private val importPickerProvider: () -> (suspend (List<String>) -> String?)?,
) : PluginHost {

  override suspend fun pickImportFile(mimeTypes: List<String>): String? =
    importPickerProvider()?.invoke(mimeTypes)
}
