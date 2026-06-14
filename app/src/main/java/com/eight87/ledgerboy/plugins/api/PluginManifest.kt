package com.eight87.ledgerboy.plugins.api

/**
 * X.2 — the compiled-in plugin manifest. Enumerates every plugin the APK
 * ships with. There is no remote manifest, no plugin store, no dynamic
 * loading — adding a plugin is adding a class + a line here
 * (connector-plugins.md).
 *
 * Populated as plugins land in Phase B+ (`csv-import` shipped in Phase B;
 * `ecb-fx` next in Phase D).
 */
object PluginManifest {
  val all: List<ConnectorPlugin> = listOf(
    com.eight87.ledgerboy.plugins.csvimport.CsvImportPlugin,
    com.eight87.ledgerboy.plugins.ecbfx.EcbFxPlugin,
    com.eight87.ledgerboy.plugins.coinbase.CoinbaseCryptoPlugin,
  )

  fun byId(id: String): ConnectorPlugin? = all.firstOrNull { it.id == id }

  fun byCategory(category: PluginCategory): List<ConnectorPlugin> =
    all.filter { it.category == category }
}
