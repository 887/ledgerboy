# ledgerboy — connector plugin architecture

## Status: 🔒 LOCKED — 2026-05-24

This file locks the connector architecture for ledgerboy. Every banking
connector, every asset-price connector, every FX source, every import
parser, every regional-index helper is a **plugin** — a self-contained
module that is **disabled by default** and only fetches data after the
user has explicitly enabled AND configured it.

Locked from the original research round on the user's redirect. No
aggregator middlemen (GoCardless, Tink, Plaid, Salt Edge, TrueLayer),
no paid-SaaS pricing services (Alpha Vantage, finnhub.io, CoinGecko,
CoinMarketCap). Direct-to-source-of-truth only, via open standards and
client-style protocol implementations.

## Locked decisions

1. **Packaging — single APK, in-tree opt-in.** Every plugin
   implementation ships in the ledgerboy APK as a Kotlin module under
   `app/src/main/java/com/eight87/ledgerboy/plugins/<plugin-id>/`. No
   dynamic class loading, no separate plugin APKs, no Gradle product
   flavors per connector combo. Single Obtainium target. APK size cost
   of carrying disabled-by-default plugins is the price of distribution
   simplicity.
2. **License gate unchanged.** MIT / Apache-2.0 / BSD-2 / BSD-3 /
   MPL-2.0 only. Anything else disqualifies the plugin entirely
   (because the whole APK ships under one set of licenses; an LGPL
   plugin would infect the whole APK). See [`oss-licenses.md`](oss-licenses.md).
3. **Disabled by default — always.** A freshly-installed ledgerboy
   ships with zero plugins enabled. The user enables each plugin
   explicitly via Settings → Plugins. There is no "starter pack",
   no "recommended set", no first-run wizard that flips anything on
   for you.
4. **Configure-before-fetch.** An enabled plugin that is not yet
   configured (no credentials / no API key / no bank picked) does not
   make network calls. The state machine is `disabled → enabled →
   configured → active`, and only `active` plugins fetch.
5. **Nothing phones home until active.** No telemetry, no startup
   pings, no plugin-list-version-check call, no remote
   plugin-discovery endpoint. The plugin manifest is compiled in. The
   first network packet to any third party comes from an `active`
   plugin's explicit user-triggered or schedule-triggered fetch.
6. **Per-plugin transparency.** Settings → Plugins shows every
   plugin's status (disabled / enabled / configured / active), last
   fetch timestamp, last fetch bytes-sent / bytes-received, last error.
   The user can see exactly what is calling out, when, with what
   volume, to which endpoint.
7. **Per-plugin disable kill-switch.** Disabling a plugin
   unconditionally halts all its network activity within one tick of
   the scheduler. There is no "grace window" for an in-flight call to
   complete.

## The plugin taxonomy

Five categories. Per-category UX grouping in Settings → Plugins.

### 1. Banking plugins

Direct-to-bank protocol clients. No aggregators. Each plugin talks one
protocol to one bank or one bank family.

- **PSD2-direct-per-bank.** One plugin per bank that publishes a direct
  Berlin Group NextGenPSD2 API. Each plugin owns one bank's OAuth flow
  + endpoint URLs + the bank's specific NextGenPSD2 dialect. The user
  must obtain consent through the bank's own consent portal.
- **FinTS-client (roll-own).** Single plugin covering the FinTS / HBCI
  protocol used by Sparkassen, Volksbanken, and most other German
  retail banks. Implemented from scratch (MIT) because no permissively-
  licensed JVM FinTS client exists; the FinTS research file estimates
  ~3,300 LOC for the minimum viable surface (HKSAL balance + HKKAZ
  transactions + pushTAN/decoupled SCA).
- **EBICS — rejected, not shipped.** See [`banking-ebics.md`](banking-ebics.md)
  for the rationale (DACH retail banks gate EBICS behind business
  contracts; the user has no personal-banking reach). Not a plugin
  candidate.

### 2. Asset-price plugins

Direct-to-broker, direct-to-exchange, or direct-to-community-open-data.
Per-source, off by default.

- **Per-broker brokerage plugins.** IBKR (TWS/Gateway socket), Trade
  Republic (unofficial Mobile API), Comdirect, etc. — one plugin per
  broker. User provides their own broker credentials. Securities
  research file enumerates concrete brokers.
- **Per-exchange crypto plugins.** Kraken REST, Coinbase REST, Binance
  REST — one plugin per exchange. User provides their own exchange API
  key (read-only scope only).
- **On-chain RPC plugins.** Direct JSON-RPC to a public Ethereum /
  Bitcoin / etc. node URL the user supplies (their own node, or a
  trusted public endpoint they chose). Not aggregator-mediated.
- **Community-open-data plugins** — Scryfall (MTG), pokemontcg.io
  (Pokémon), YGOPRODeck (Yu-Gi-Oh!). Free, direct-from-device,
  no aggregator. Off by default because **every** plugin is off by
  default.

### 3. FX plugins

- **ECB-FX plugin** — fetches `eurofxref-daily.xml` directly from the
  ECB. Off by default; user enables it if they want auto-FX.
- **Bundesbank-FX plugin** — alternative direct-from-source. Off by
  default.
- **Manual + per-bank-statement-FX** — intrinsic (no plugin needed).
  Bank statements that ship per-transaction FX use that. User can also
  type rates manually.

### 4. Import plugins

Each format is a plugin (off by default; user enables the format(s)
their bank exports).

- **CSV** plugin + per-bank-dialect sub-plugins (Sparkasse CSV dialect,
  Commerzbank CSV dialect, etc.).
- **CAMT.053** plugin (ISO 20022 XML).
- **MT940** plugin (SWIFT).
- **OFX** plugin.
- **QIF** plugin.

### 5. Regional-index plugins

Public open-data series for asset-class revaluation hints. All off by
default.

- **Häuserpreisindex** (DE federal house-price index from Destatis).
- **BORIS Bodenrichtwerte** (DE per-state land-value indexes).
- **UK Land Registry Price Paid + UK HPI**.
- **US FHFA HPI**.

Each fetches a small periodic open-data file from a government open-data
endpoint; user enables which series they care about.

## The plugin interface (Kotlin sketch)

```kotlin
interface ConnectorPlugin {
    val id: String                  // stable, e.g. "fints-client"
    val displayName: String         // localized, user-facing
    val category: PluginCategory    // Banking / AssetPrice / Fx / Import / RegionalIndex
    val description: StringResource // one-paragraph, localized
    val licenseSpdx: String         // for the OSS-licenses screen + audit
    val sourceUrl: String?          // homepage of the upstream protocol/data source
    val privacyStatement: StringResource // what data leaves the device, to where

    val state: StateFlow<PluginState>  // Disabled / Enabled / Configured / Active

    @Composable
    fun ConfigScreen()              // plugin owns its config UI
                                    // never invoked until state == Enabled

    suspend fun fetch(window: FetchWindow): FetchResult
                                    // never invoked until state == Active
                                    // emits NormalizedTransaction / ValuationEvent / FxRate rows
                                    // depending on category
}

enum class PluginState { Disabled, Enabled, Configured, Active }

sealed class FetchResult {
    data class Ok(val rowsWritten: Int, val bytesIn: Long, val bytesOut: Long): FetchResult()
    data class Error(val message: String, val retryAfter: Duration?): FetchResult()
}
```

The interface is intentionally narrow. Plugins do their work; the host
runs the scheduler, the network-usage tracking, the row-write
deduplication, and the UI shell. Plugins never touch the Room database
directly — they emit normalized rows that the host writes.

## The plugin manifest

Compiled in. A single `PluginManifest` value at app start enumerates
every plugin the APK ships with. Adding a plugin = adding a Kotlin
module + adding a line to the manifest.

```kotlin
object PluginManifest {
    val all: List<ConnectorPlugin> = listOf(
        FintsClientPlugin,
        Psd2SparkasseDePlugin,
        Psd2DkbPlugin,
        // ...
        EcbFxPlugin,
        BundesbankFxPlugin,
        CamtImportPlugin,
        Mt940ImportPlugin,
        // ...
        ScryfallPlugin,
        PokemonTcgIoPlugin,
        // ...
    )
}
```

No remote manifest. No "plugin store". The user gets the plugins the
APK was built with.

## Discovery + UX

### Settings → Plugins

Top-level Settings entry. Opens a screen with five accordion
categories (Banking / Asset prices / FX / Import / Regional indexes),
each containing the plugins in that category as cards.

Each card shows:
- Icon (per plugin)
- Display name
- One-line source description ("Sparkasse via NextGenPSD2", "Scryfall MTG API")
- License SPDX badge
- State chip (Disabled / Enabled / Configured / Active)
- Last fetch timestamp + bytes-in/out summary (when active)
- Toggle: enable / disable
- "Configure…" button (only when enabled)

Disabled plugins are visible (so the user can discover what's
available) but greyed-out beyond the toggle.

### Per-plugin configuration

When the user enables a plugin and taps Configure, the plugin's own
`@Composable ConfigScreen()` opens. The host provides:
- A Material 3 Expressive theme (already applied).
- A SAF picker hook (for plugins that import files).
- A Keystore-wrapping helper for credential storage (per
  [`security-decisions.md`](security-decisions.md)).
- A localized strings helper.

The plugin owns:
- Form fields (bank picker, API key field, OAuth flow trigger, etc.).
- Validation.
- A "Test connection" button that performs one read-only fetch and
  reports success/failure.
- Persistence of its own config to its own DataStore namespace.

After successful Test, the plugin transitions to `Active` and starts
appearing in the periodic-fetch scheduler.

### Per-plugin transparency

The Settings → Plugins screen shows a "Network usage" sub-screen with:
- Per-plugin totals (last 24h, last 7d, all time): bytes-in, bytes-out,
  fetch count, error count.
- Per-plugin endpoint list (the actual URLs each plugin called).
- Per-plugin "what data leaves the device" statement (from the
  plugin's `privacyStatement`).

User can revoke / disable any plugin at any time. Disable
unconditionally halts further fetches.

## Privacy posture

Reaffirms and tightens the existing `CLAUDE.md` Privacy posture
section:

- **No network calls before the user enables a plugin.** This is
  enforceable at the OkHttp layer: a single shared client wrapped by
  `PluginNetworkGuard` rejects any request not originating from an
  `Active` plugin.
- **No network calls during app start.** No crash-reporters, no
  analytics, no plugin-update-check, no remote-manifest fetch.
- **No background fetch unless the user opted into background sync per
  plugin.** Plugins default to fetch-on-user-action; background-sync
  is a per-plugin opt-in.
- **Per-plugin network logs are user-visible** (see Transparency
  above).

## License gate — reaffirmed for plugins

Because every plugin compiles into the single APK:

- MIT, Apache-2.0, BSD-2, BSD-3, MPL-2.0 dependencies are acceptable.
- LGPL is disqualifying (would infect the whole APK).
- GPL / AGPL are disqualifying.
- The Licensee plugin (Phase A.4 from [`oss-licenses.md`](oss-licenses.md))
  catches gate violations at configuration time.

This is why FinTS ships as a roll-own client (~3,300 LOC) rather than
wrapping `hbci4java` (LGPL). The per-plugin license SPDX surfaces in
the Plugins screen so the user can see what's underneath each.

## Phase work — what this adds to `main.md`

**Phase X — plugin runtime** (new, lands before Phase B). This is the
host-side scaffolding every connector plugin builds on:

- [ ] **X.1** `ConnectorPlugin` interface + `PluginCategory` enum +
      `PluginState` state-machine in `app/.../plugins/api/`.
- [ ] **X.2** `PluginManifest` singleton listing every shipped plugin
      (initially empty — populated as plugins land in Phase B+).
- [ ] **X.3** Per-plugin DataStore namespace + `PluginConfigStore`
      helper for plugins to persist config without colliding.
- [ ] **X.4** `PluginNetworkGuard` OkHttp `Interceptor` rejecting
      requests not originating from `Active` plugins; per-plugin
      bytes-in/out / fetch-count / error-count accumulators.
- [ ] **X.5** `PluginScheduler` (WorkManager) — per-plugin
      schedule-or-manual; respects the per-plugin background-sync
      opt-in; honours disable kill-switch within one tick.
- [ ] **X.6** Settings → Plugins screen — accordion by category,
      per-plugin cards with state chip + toggle + Configure button;
      built on the Settings catalog DSL from [`ui-shell.md`](ui-shell.md).
- [ ] **X.7** Settings → Plugins → Network usage sub-screen — per-plugin
      totals + endpoint list + privacy statement; user-visible audit.
- [ ] **X.8** Per-plugin Configure flow shell — opens the plugin's
      `@Composable ConfigScreen()`, provides the SAF / Keystore /
      strings helpers; "Test connection" runs one fetch with explicit
      user confirmation before transitioning state to `Active`.
- [ ] **X.9** Robolectric tests: state-machine transitions, network-
      guard rejection of disabled plugins, scheduler kill-switch
      latency.

After Phase X lands, every connector plugin becomes a small Phase B+
sub-phase implementing `ConnectorPlugin` for one source.

## What this changes in existing research files

This pivot was applied AFTER the first research round landed. The
following files need rewrites to reflect the plugin-architecture
locks. Tracked here so the rewrites are coordinated.

- [`banking-research.md`](banking-research.md) — reframe from
  "evaluate aggregator + direct + import" to "every source is a
  plugin; pick the protocols and the per-bank coverage; no
  aggregators." Drop the GoCardless recommendation; pivot to
  per-bank PSD2-direct + FinTS-roll-own + EBICS-rejected.
- [`banking-psd2.md`](banking-psd2.md) — full rewrite. GoCardless out.
  Pivot to per-bank PSD2-direct via Berlin Group NextGenPSD2. Per-bank
  research subplans (Sparkasse / DKB / Commerzbank / ING-DiBa / etc.)
  scoped after the host runtime lands.
- [`banking-fints.md`](banking-fints.md) — partial rewrite. The
  "DEFER v1 — no permissive lib" conclusion stands for **wrapping**
  options; pivot to "ship roll-our-own minimal FinTS client as the
  `fints-client` plugin in Phase B." The ~3,300 LOC estimate is now
  v1 work, not v2 work.
- [`banking-import.md`](banking-import.md) — minor rewrite. Each
  format = a plugin (CSV / CAMT.053 / MT940 / OFX / QIF). Library
  picks (`pw-swift-core` for MT940, `ofx4j` for OFX) carry over.
- [`banking-fx.md`](banking-fx.md) — partial rewrite. Frankfurter
  removed (it's a third-party proxy of ECB data — third party). ECB
  direct (`eurofxref-daily.xml`) and Bundesbank direct as separate
  plugins, both off by default.
- [`banking-ebics.md`](banking-ebics.md) — minor addendum: the
  rejection stands, would have been plugin-only if reconsidered.
- [`asset-securities.md`](asset-securities.md) — full rewrite. Alpha
  Vantage out, finnhub.io out. Pivot to per-broker plugins (IBKR
  TWS/Gateway, Trade Republic, etc.) + manual + CSV import (broker
  statement formats).
- [`asset-crypto.md`](asset-crypto.md) — full rewrite. CoinGecko out,
  CoinMarketCap out. Pivot to per-exchange plugins (Kraken / Coinbase
  / Binance) + on-chain RPC plugins (user-supplied node URL) + manual
  + CSV import.
- [`asset-collectibles.md`](asset-collectibles.md) — partial rewrite.
  Scryfall / pokemontcg.io / YGOPRODeck stay (free, direct, open-data)
  but each is a plugin off by default; no auto-enable.
- [`asset-realestate.md`](asset-realestate.md),
  [`asset-vehicles.md`](asset-vehicles.md),
  [`asset-generic.md`](asset-generic.md) — minor addendum: the
  regional-index helpers (Häuserpreisindex, BORIS, UK HPI, FHFA HPI)
  are plugins off by default.
- [`asset-research.md`](asset-research.md) — reframe the per-class
  template to "is this a candidate for a plugin, and if so what's the
  protocol/source?"

`main.md` adds Phase X before Phase B; every subsequent Phase B+ is
re-scoped as "implement plugin X built on the host runtime."

`README.md` adds a "Plugin architecture" section explaining the
disabled-by-default posture and pointing here. `CLAUDE.md` adds a
"Plugin architecture" section under Architectural decisions.

## What this is NOT

- **Not a plugin marketplace.** No remote discovery, no remote
  download. The user gets the plugins the APK was built with.
- **Not dynamic class loading.** Plugins are compiled in. Adding a
  plugin is a source-tree contribution + rebuild + reinstall.
- **Not configuration-from-the-web.** Plugin config is on-device only,
  encrypted via the security cluster.
- **Not opt-out.** Every plugin starts disabled. There is no
  "ledgerboy starter pack" or "enable recommended."
- **Not a path for shipping LGPL.** LGPL plugins would infect the
  single APK. The license gate is the gate; the plugin model does not
  weaken it.
