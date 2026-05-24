# ledgerboy — asset: securities (stocks / ETFs / mutual funds)

## Status: REWRITTEN 2026-05-24 — Alpha Vantage / finnhub.io / Polygon / Twelve Data / Marketstack / Yahoo unofficial all OUT (paid-SaaS pricing intermediaries rejected per the "direct-to-source-of-truth only" lock); pivot to **per-broker plugins** + **manual** + **CSV import**, all disabled by default per [`connector-plugins.md`](connector-plugins.md). J.13 license verification resolved 2026-05-24: **`TwsApi.jar` REJECTED** (SPDX `PROPRIETARY-IBKR`, prohibits redistribution + non-commercial only); pivot to a clean-room Kotlin TWS socket client (~1,200 LOC) — see J.13.a–J.13.f.

Per-class decision file spawned from [`asset-research.md`](asset-research.md).
Covers **listed securities**: stocks, ETFs, mutual funds. Crypto lives in
[`asset-crypto.md`](asset-crypto.md); FX rates live in the banking-research
cluster.

## What it is

A *security* in ledgerboy is a holding identified by **ticker + exchange**
that has a daily end-of-day price published by an exchange (or NAV for
mutual funds). The user enters the position; the per-broker plugin (if
enabled and configured for that broker) reads balances + last-known
prices directly from the user's broker; alternatively the user imports
a broker CSV statement, or types positions manually. The host writes
one `ValuationEvent(date, value, source)` row per fetch / import. Total
position value = `unitPrice × quantity` in the security's quote currency,
converted to the user's display currency via the FX cache (researched
by the parallel agent).

End-of-day pricing is sufficient. Real-time / intraday is not a goal —
this is a net-worth tracker, not a trading app.

## Architectural framing

Every securities source is a `ConnectorPlugin` per
[`connector-plugins.md`](connector-plugins.md):

- Category: `AssetPrice`.
- **Off by default.** A fresh install has zero broker plugins enabled.
- One plugin per broker (`ibkr-tws-client`, `trade-republic`,
  `comdirect`, `dkb-securities`, `flatex`, `schwab`, `fidelity`,
  `vanguard`, `robinhood`, …). Each plugin talks the broker's own
  protocol; there is no aggregator middleman.
- Configure flow per plugin asks for broker-specific credentials
  (OAuth token, API key, IBKR gateway host:port, etc.), stored
  encrypted via the host's Keystore-wrapping helper.
- Test connection performs one **read-only balance** call.
- Privacy statement names the exact broker endpoint and what data
  leaves the device.
- License gate: MIT / Apache-2.0 / BSD-2 / BSD-3 / MPL-2.0 only (the
  whole APK ships under one license set).

Universal fallbacks shared across all brokers:

- **Manual entry.** Always available, no plugin needed.
- **CSV import.** Per-broker CSV dialect plugins under the import
  category (shape inherited from `banking-import.md`). Every broker
  exports statements; users can map columns + import.

## Rejected SaaS-aggregator sources (documented so future agents don't re-grep)

The previous round of research picked Alpha Vantage and finnhub.io as
v1 connectors. **Both are rejected** under the new "no paid-SaaS
pricing intermediaries" lock from [`connector-plugins.md`](connector-plugins.md).
The technical analysis is preserved here so the rejection is on the
record:

| Source | URL | Why rejected |
| --- | --- | --- |
| **Alpha Vantage** | <https://www.alphavantage.co/> | Third-party pricing aggregator, not source-of-truth. Free tier (25 req/day, 5 req/min per <https://www.alphavantage.co/premium/>) plus paid tiers. User would have to register and trust a US analytics company with their portfolio symbol list. **OUT** — violates "direct-to-source-of-truth only." |
| **finnhub.io** | <https://finnhub.io/> | Third-party pricing aggregator. Free tier (60 req/min per <https://finnhub.io/pricing>). Same shape as Alpha Vantage. **OUT.** |
| **Polygon.io** | <https://polygon.io/> | Third-party pricing aggregator. Free tier 5 req/min, 15-min-delayed per <https://polygon.io/pricing>. **OUT.** |
| **Twelve Data** | <https://twelvedata.com/> | Third-party pricing aggregator. Free tier 800 credits/day per <https://twelvedata.com/pricing>. **OUT.** |
| **Marketstack** | <https://marketstack.com/> | Third-party pricing aggregator. **OUT.** |
| **IEX Cloud** | (shut down 2024) | Dead per <https://www.alphavantage.co/iexcloud_shutdown_analysis_and_migration/>. Was an aggregator anyway. **OUT.** |
| **Yahoo Finance unofficial** (yfinance / yahoo_fin) | <https://finance.yahoo.com/> | Unofficial scraping; Yahoo ToS forbids automated extraction. **OUT** on ToS gate. |
| **Google Finance** | <https://www.google.com/finance> | No public API. **OUT.** |
| **ECB equity data** | <https://www.ecb.europa.eu/> | Publishes aggregate indices only, not per-security quotes. Not a fit. |

The lock is firm: **no third-party pricing aggregator ships as a
ledgerboy plugin, free or paid.** If a price isn't available from the
user's own broker, from a broker CSV, or from manual entry, it isn't
available. That's the trade-off the architecture makes for
"direct-to-source-of-truth only."

## Per-broker plugin candidates

### Openness gradient

A spectrum runs from "official, documented, ToS-clean broker API" at
one end to "no API exists, CSV import only" at the other. Per-broker
plugins are sorted on this gradient; v1 ships one or two reference
plugins from the official-API end, and additional brokers are small
Phase B sub-phases.

| Tier | Examples | Plugin posture |
| --- | --- | --- |
| **Official open API, ToS-clean** | IBKR, Comdirect, Schwab (US, post-Schwab/TDA merger), Fidelity (limited) | Ship a plugin. Configure asks for OAuth or API key. |
| **Unofficial reverse-engineered API, ToS-grey** | Trade Republic, Robinhood | Ship a plugin **with a ToS-risk privacy statement.** The user accepts the risk; the plugin can break at any time. |
| **No API at all** | Most German Sparkassen/Volksbanken securities accounts, smaller brokers | **CSV import only.** No automated plugin. |

### Interactive Brokers (IBKR) — **adopt as v1 reference plugin (official API tier)**

- **Plugin id:** `ibkr-tws-client`.
- **What:** IBKR publishes the TWS API (historically also known as the
  IB Gateway API). Open protocol, widely documented. The protocol is a
  socket-based wire format spoken by TWS (the desktop trading app) or
  IB Gateway (a smaller headless version of the same).
- **API:** TWS socket protocol. Docs:
  <https://interactivebrokers.github.io/tws-api/> .
- **Topology:** the user runs TWS or IB Gateway on their desktop (or
  on a server they control); the ledgerboy plugin connects over the
  LAN to that gateway's socket. **ledgerboy does not talk to IBKR
  servers directly** — it talks to the user's own TWS/Gateway, which
  in turn talks to IBKR. This matches the user's posture: the user
  already trusts their own machine.
- **Client library:** **`TwsApi.jar` REJECTED on license gate.**
  Verified 2026-05-24 via the official IBKR developer portal
  (<https://interactivebrokers.github.io/>) and the official GitHub
  mirror <https://github.com/InteractiveBrokers/tws-api-public>
  (`license: null` in the GitHub repo metadata — no SPDX-known OSS
  license declared). The TWS API is governed by the **TWS API
  Non-Commercial License Agreement** (SPDX: `PROPRIETARY-IBKR`; not a
  recognised OSI/SPDX OSS id), which grants "a personal, royalty-free,
  non-exclusive, non-sublicensable, non-transferable, restricted right
  and license to install, modify and use the API Code solely for
  Non-Commercial Purposes" and explicitly states: **"You agree not to
  publish, disseminate, or redistribute the API Code to any third
  party."** That redistribution prohibition alone disqualifies the JAR
  from shipping inside an MIT-licensed Android app published via
  GitHub Releases; the "Non-Commercial Purposes" gating compounds the
  problem. Pivot: implement a minimal Kotlin TWS socket client from
  scratch (the wire protocol + message-ID table are API surface, not
  copyrightable per *Oracle v. Google*, 593 U.S. ___ (2021)). LOC
  estimate ~1,200 for the read-only-balances scope; see sub-steps
  J.13.a–J.13.f below.
- **Configure flow:** the user enters gateway host (default
  `127.0.0.1` for local LAN), gateway port (default `7497` for TWS
  live or `4002` for IB Gateway live), client ID (any non-zero
  integer, must be unique per concurrent connection), and an optional
  read-only-API-only toggle (TWS exposes this; the plugin defaults it
  on so the plugin can never place an order). Credentials are not
  stored on-device because TWS itself holds the IBKR login.
- **Test connection:** the plugin connects to `host:port`, requests
  the account summary, and confirms a single read-only balance.
- **Privacy statement:** "Connects over your LAN to your own TWS or
  IB Gateway instance. No data leaves your network except the calls
  TWS itself makes to Interactive Brokers (which TWS does anyway when
  you use it). The plugin reads positions and balances only;
  read-only API mode is enforced."
- **ToS posture:** IBKR explicitly supports the TWS API for personal
  use. **Clean.**
- **Coverage:** every market IBKR routes to — US, EU (Xetra, LSE,
  Euronext), Asia (HKEX, TSE, SGX), etc.
- **Symbol resolution:** IBKR contracts use `(symbol, exchange,
  currency, secType)` — the plugin maps this to ledgerboy's
  `CanonicalSymbol`.

### Trade Republic — **adopt as v1.x plugin (unofficial-API tier)**

- **Plugin id:** `trade-republic`.
- **What:** German neobroker, mobile-first, no official public API.
- **API:** **unofficial reverse-engineered.** Reference Python client:
  `Zarathustra2/TradeRepublicApi`
  (<https://github.com/Zarathustra2/TradeRepublicApi>, MIT). The
  ledgerboy plugin reimplements the protocol in Kotlin (license-gate
  compliance + native networking).
- **ToS posture:** **grey.** Trade Republic's terms do not explicitly
  permit automated personal-use clients; they also do not explicitly
  forbid them. TR can break the unofficial API at any time and has
  historically tweaked it (rotated WebSocket message shapes,
  token-refresh schemes). The plugin's privacy statement must say so
  explicitly: "This plugin uses an unofficial API that Trade Republic
  has not publicly committed to. It may break without warning, and
  Trade Republic's terms of service may not permit automated access."
- **Configure flow:** the user enters their TR phone number + PIN +
  device-pairing flow (TR's mobile login uses a 4-digit PIN and a
  device-token bound to a one-time SMS challenge). The plugin stores
  the device token encrypted via the host's Keystore-wrapping helper.
- **Test connection:** reads the portfolio summary; confirms a single
  balance.
- **Coverage:** TR's full instrument universe (DE/EU stocks, ETFs,
  some US, crypto via TR's own wrapper).
- **Risk note:** documented in the plugin's privacy statement; surface
  to the user on enable.

### Comdirect — **adopt as v1.x plugin (official-API tier)**

- **Plugin id:** `comdirect`.
- **What:** German traditional bank + online broker (Commerzbank
  subsidiary).
- **API:** official REST API at `https://api.comdirect.de/api/` with
  OAuth 2.0. Docs:
  <https://developer.comdirect.de/> .
- **ToS posture:** clean. The API is published for personal-use
  clients; the OAuth flow is the user authorizing their own client.
- **Configure flow:** OAuth 2.0 authorization-code flow via system
  browser (Custom Tabs); the plugin stores the refresh token
  encrypted.
- **Test connection:** reads the depot summary; confirms one balance
  via the `/brokerage/v3/depots/{depotId}/positions` endpoint.
- **Coverage:** the user's Comdirect depot positions.

### DKB (Deutsche Kreditbank) — **investigate, defer**

- **Plugin id:** `dkb-securities` (planned).
- **What:** German direct bank with brokerage arm.
- **API status:** DKB's PSD2 API covers payment accounts (handled by
  the `dkb-psd2` banking plugin) but **does not cover securities
  depot positions**. DKB has no public securities API. Reverse-
  engineered clients exist for the customer-portal but they are
  ToS-hostile and break frequently.
- **Recommendation:** **CSV import only** for v1. Document an
  unofficial-API plugin candidate for later research; do not ship.

### Flatex — **investigate, defer**

- **Plugin id:** `flatex` (planned).
- **What:** German discount broker.
- **API status:** no public personal-use API. Some community
  reverse-engineering exists; ToS-grey.
- **Recommendation:** **CSV import only** for v1.

### Robinhood — **adopt as later plugin (unofficial-API tier)**

- **Plugin id:** `robinhood` (planned, US users).
- **What:** US neobroker.
- **API status:** **unofficial reverse-engineered.** Reference Python
  client: `robin-stocks` (<https://github.com/jmfernandes/robin_stocks>,
  MIT). Robinhood's ToS does not explicitly authorise automated
  personal clients; same grey-zone risk note as Trade Republic.
- **Recommendation:** ship as a Phase B+ plugin with the ToS-risk
  privacy statement. Not v1.

### Charles Schwab — **investigate (post-TDA merger), defer**

- **Plugin id:** `schwab` (planned).
- **What:** US broker; absorbed TD Ameritrade in 2023. Schwab's
  individual-developer trading API
  (<https://developer.schwab.com/>) is the successor to TDA's API
  with a partner-approval flow. Personal-use availability requires
  developer registration.
- **License posture:** Schwab provides REST + OAuth; client code is
  the plugin author's responsibility (no library license issue).
- **Recommendation:** ship as a Phase B+ plugin once the developer-
  registration ergonomics are understood. Not v1.

### Fidelity — **investigate, defer**

- **Plugin id:** `fidelity` (planned).
- **API status:** Fidelity does not publish a general personal-use
  trading API. Workplace-retirement and institutional APIs exist;
  personal brokerage uses the web/app only. **CSV import only** for
  v1.

### Vanguard — **CSV import only**

- **API status:** no public personal-use API. **CSV import only.**

### Other brokers — **CSV import only**

Every other broker not enumerated above (DEGIRO, eToro, Saxo,
ING-DiBa brokerage, etc.) ships as **CSV import only** for v1.
Per-broker plugins are small Phase B sub-phases that can land later if
the broker has an official API; brokers with no API are permanently
CSV-only.

## Manual + CSV import — universal fallbacks

### Manual entry

The user enters:

1. **Symbol** (e.g. `SAP`, `VTSAX`, `IWDA`)
2. **Exchange** (sealed enum: `XETRA`, `NASDAQ`, `NYSE`, `LSE`, `TSX`,
   `HKEX`, `TSE`, plus an `OTHER` with a free-text MIC code)
3. **Quote currency**
4. **Quantity** (Decimal-as-Long minor units — fractional brokers are
   real)
5. **Cost basis** (optional Money)
6. **Current price** (manual entry — the user types whatever they
   read off their broker statement)

Each manual revaluation writes one `ValuationEvent(source="manual")`.
This is always available with **zero plugins enabled.**

### CSV import (per-broker dialect plugins)

Every broker exports statements as CSV (or XLSX). The CSV import
plugins live under the `Import` plugin category from
[`connector-plugins.md`](connector-plugins.md), one plugin per broker
dialect:

- `ibkr-csv` — IBKR Activity Statement CSV.
- `trade-republic-csv` — TR's "Steuerübersicht" + transaction CSV.
- `comdirect-csv` — Comdirect "Umsätze" CSV (the same CSV the banking
  side already parses for transactions).
- `dkb-csv-securities` — DKB depot CSV export.
- `flatex-csv` — Flatex statement CSV.
- `schwab-csv`, `fidelity-csv`, `vanguard-csv` — US-broker CSVs.
- `degiro-csv`, `etoro-csv`, `saxo-csv` — pan-EU broker CSVs.

Each plugin is **disabled by default**. Configure flow is a one-time
column-mapping step (the plugin proposes a mapping based on header
detection; the user confirms). Import runs through the SAF picker.
Each imported row writes a `ValuationEvent(source="csv:<broker>")`.

The CSV import pattern shares scaffolding with
[`banking-import.md`](banking-import.md) — same parser harness, same
SAF picker hook, same encrypted-at-rest staging.

## Lookup shape

When a per-broker plugin is `Active`, the user enters or imports
positions and the plugin populates them; no symbol search needed (the
broker already knows the canonical symbol).

When using manual entry without a broker plugin, the user supplies
(symbol, exchange, quote currency) directly. **No `SYMBOL_SEARCH`
endpoint call is made — there is no third-party symbol-search service
in v1**, because every such service (Alpha Vantage, finnhub.io,
Yahoo Finance) violates the no-aggregator lock. The user disambiguates
by typing the exchange suffix themselves (e.g. `SAP.DE` for Xetra vs.
`SAP` for NYSE).

Lookup is **deterministic**: (symbol, exchange) always identifies one
security. The `AssetEntity` row stores the canonical tuple.

## Revaluation cadence

- **Automatic refresh:** once per day, on app open, if the cached
  `latestValuationEvent.date` is older than 1 day, **and a broker
  plugin is `Active` for that holding's source broker.** The
  `PluginScheduler` from Phase X runs this; per-plugin background-sync
  opt-in is respected.
- **Manual refresh:** "Refresh prices" on the Assets screen forces a
  fetch for any holding whose source plugin is `Active`.
- **No automatic refresh for holdings with no Active plugin.** They
  stay at the last manually-entered value until the user types a new
  one or imports an updated CSV.
- **Weekend / holiday awareness:** the broker plugin already respects
  the broker's own price-update schedule; a per-exchange holiday
  calendar (small static data) helps avoid useless fetches but is not
  load-bearing because brokers simply return stale prices on closed
  days.

## Fit recommendation

- **v1 reference plugin (official API tier):** `ibkr-tws-client`.
  Disabled by default. Configure asks for gateway host:port + client
  ID + read-only-only toggle.
- **v1 plugin (official API tier):** `comdirect`. Disabled by default.
  Configure runs the OAuth flow.
- **v1.x plugin (unofficial API tier):** `trade-republic`. Disabled by
  default. Privacy statement names the ToS-grey risk.
- **Universal v1 fallback:** **manual entry** (no plugin needed).
- **Universal v1 fallback:** **CSV import** via per-broker dialect
  plugins, disabled by default.
- **Phase B+ plugins:** `dkb-securities`, `flatex`, `robinhood`,
  `schwab`, `fidelity`, `vanguard`, additional EU brokers — each
  small. Built on the Phase X host runtime; no host changes needed.
- **Rejected (documented above):** Alpha Vantage, finnhub.io, Polygon,
  Twelve Data, Marketstack, IEX Cloud, Yahoo unofficial, Google
  Finance. No third-party pricing aggregator ships, ever.

## Cross-cutting

### Plugin-internal `SecuritySource` shape

Each per-broker plugin implements `ConnectorPlugin` (the host-visible
surface from [`connector-plugins.md`](connector-plugins.md)) and
internally exposes a narrower `SecuritySource` for its own balance /
price reads. The host only sees `ConnectorPlugin`.

```kotlin
// Internal to each per-broker plugin.
interface SecuritySource {
    val id: String                  // "ibkr", "comdirect", "trade-republic"
    val attribution: AttributionLine
    val ttl: Duration               // 1.days

    suspend fun readBalances(): Result<List<BrokerPosition>>
    suspend fun readPrice(symbol: CanonicalSymbol): Result<Money>
}

data class CanonicalSymbol(val symbol: String, val exchange: Exchange, val currency: Currency)
data class BrokerPosition(val symbol: CanonicalSymbol, val quantity: Quantity, val unitPrice: Money?)
```

Each plugin lives in `com.eight87.ledgerboy.plugins.<plugin-id>/`.
Adding a fourth broker = new plugin module + new manifest entry; the
host code does not change (SOLID Open/Closed).

### Caching

Every `readBalances` / `readPrice` result writes one or more
`ValuationEvent` rows. Append-only; no eviction.

### Rate-limit discipline

Each broker plugin owns its own rate-limit policy (broker APIs differ
wildly — IBKR uses pacing violation responses, Comdirect uses
per-minute quotas, Trade Republic uses WebSocket back-pressure).
Spacing lives inside each plugin; the host's `PluginNetworkGuard`
tracks bytes-in/out / fetch count / error count uniformly via the
Phase X interceptor.

### Attribution surface

Per-plugin attribution rides on the **Settings → Plugins → Asset
prices** card from Phase X.6. Each card shows the broker's display
name, license SPDX (the plugin's own code license; the broker is
attributed in the privacy-statement line), state chip, last fetch
timestamp + bytes summary.

Strings live in `strings.xml` under the `plugin_<id>_attribution` /
`plugin_<id>_privacy_statement` naming convention, matching the
per-plugin string layout used by the banking and FX plugins.

### Credential storage

Per-broker plugin credentials (OAuth refresh tokens, API keys, TR
device tokens) are stored encrypted via the host's Keystore-wrapping
helper, per [`security-research.md`](security-research.md). Each
plugin owns a private DataStore namespace from Phase X.3.

### Money + Quantity shape

Quoted prices arrive as broker-API JSON / wire-protocol numbers. The
plugin parses to `Money(long, currency)` at the boundary, minor-units
precision (currency-aware — JPY has zero decimals, KWD has three).
Quantity is stored as a `Quantity` value class (8-decimal long) to
handle fractional shares from brokers like Trade Republic; this is
*not* a Money — it's a `Quantity`. Position value =
`unitPrice × quantity / 10^8`, integer math, never floating point.

## Decision

- **v1 plugins (built on Phase X):** `ibkr-tws-client` (reference,
  official-API tier) and `comdirect` (official-API tier). Both
  disabled by default; user enables and configures per source.
- **v1.x plugins:** `trade-republic` (unofficial-API tier, ToS-grey
  risk surfaced).
- **Universal v1 fallbacks (no plugins required):** manual entry and
  per-broker CSV import (CSV plugins disabled by default; user enables
  the formats their broker exports).
- **No third-party pricing aggregator** — Alpha Vantage, finnhub.io,
  Polygon, Twelve Data, Marketstack, Yahoo unofficial all rejected
  (see table above).
- **CSV-only brokers (no automated plugin):** DKB securities, Flatex,
  Vanguard, Fidelity, DEGIRO, eToro, Saxo, ING-DiBa, every other
  broker without an official personal-use API.

### `ValuationEvent` write path (per-broker plugin)

```
PluginScheduler tick (or user taps "Refresh prices"):
  for each Active securities plugin:
    if plugin.state != Active: skip
    positions = plugin.readBalances()
    if success:
      for each position:
        insert ValuationEvent(date = now(),
                              value = position.unitPrice * position.quantity / 10^8,
                              source = "plugin:${plugin.id}")
    else:
      surface non-blocking "stale price" badge on holdings from this plugin
  done
```

### `ValuationEvent` write path (CSV import)

```
user invokes "Import broker statement" via SAF picker:
  pick a CSV-import plugin (must be Active)
  parse rows, write per-position ValuationEvent(source = "csv:${plugin.id}")
```

### `ValuationEvent` write path (manual)

```
user taps "Update price" on a holding:
  type new price → insert ValuationEvent(source = "manual")
```

### Phase J implementation sub-steps

These all build on the **Phase X host runtime** from
[`connector-plugins.md`](connector-plugins.md). Phase X lands the
`ConnectorPlugin` interface, the manifest, the `PluginNetworkGuard`,
the `PluginScheduler`, the Settings → Plugins screen, the per-plugin
Configure shell. Each sub-step below implements one plugin or one
piece of the manual / CSV scaffolding on top.

- [ ] **J.11** Define the internal `SecuritySource` interface,
  `CanonicalSymbol`, `Exchange` sealed enum, `Quantity` value class,
  `BrokerPosition` data class. All inside the plugins shared package.
  JVM unit tests for fractional-share `Quantity` math.
- [ ] **J.12** Wire securities into the `AssetEntity` model: new
  asset class enum value `SECURITY`, new fields for `symbol`,
  `exchange`, `quote_currency`, `quantity`, `cost_basis_money`,
  `source_broker_plugin_id_nullable`. Room additive migration.
- [x] **J.13** **License verdict on IBKR's official `TwsApi.jar`:
  REJECT.** Resolved 2026-05-24. The JAR is governed by the **TWS API
  Non-Commercial License Agreement** (SPDX: `PROPRIETARY-IBKR`), which
  prohibits redistribution to third parties and restricts use to
  Non-Commercial Purposes only — both clauses fail the MIT /
  Apache-2.0 / BSD-2 / BSD-3 / MPL-2.0 gate. Evidence:
  <https://interactivebrokers.github.io/> (IBKR's own license page
  quoting the agreement verbatim, including "You agree not to publish,
  disseminate, or redistribute the API Code to any third party") and
  <https://github.com/InteractiveBrokers/tws-api-public> (GitHub API
  reports `"license": null` for the repo — IBKR has not declared an
  OSS license). **Pivot:** implement the `ibkr-tws-client` plugin
  against a roll-our-own Kotlin TWS socket client; see J.13.a–J.13.f.
  The plugin id, category, default-disabled posture, and
  ConfigScreen() shape remain as previously specified (host
  `127.0.0.1`, port `7497` TWS live / `7496` TWS paper / `4001` IB
  Gateway live / `4002` IB Gateway paper, client ID, read-only-only
  toggle defaulted on).
- [ ] **J.13.a** **TWS wire-protocol skeleton.** Implement
  `IbkrSocketClient(host, port, clientId)` in
  `app/src/main/java/com/eight87/ledgerboy/plugins/ibkr-tws-client/wire/`.
  Wire shape: length-prefixed text messages over TCP. Each outgoing
  message is `[4-byte big-endian length][field1]\0[field2]\0...\0`;
  incoming messages have the same framing. Reader + writer live in
  separate coroutines on `Dispatchers.IO`; backpressure via a
  bounded `Channel<List<String>>` of parsed fields. JVM unit tests
  with a fake socket pair cover frame encode/decode, partial reads,
  and disconnect handling.
- [ ] **J.13.b** **Handshake.** Client sends API version string
  (`v100..187` range — the supported-versions list, plus optional
  connect-options) followed by `START_API` (message ID 71) with
  clientId and optional account list. Server replies with server
  version + connection time. Implement the version negotiation per
  the public protocol description; reference the message-ID constants
  from IBKR's open API documentation at
  <https://interactivebrokers.github.io/tws-api/> (the message IDs +
  field orders are API surface, not copyrightable code — per
  *Oracle v. Google* on Java APIs). **Do not copy any source from
  `TwsApi.jar`**; clean-room re-derive from the published wire-format
  documentation only. Document this posture in a code-comment block
  at the top of `IbkrSocketClient.kt`.
- [ ] **J.13.c** **Read-only message set (v1 scope).** Implement
  outbound `reqAccountUpdates(subscribe=true, accountCode)` (message
  ID 6) and `reqContractDetails(reqId, contract)` (message ID 9).
  Implement inbound parsers for `accountValue` (msg ID 6),
  `portfolioValue` (msg ID 7), `accountUpdateTime` (msg ID 8),
  `contractDetails` (msg ID 10), `contractDetailsEnd` (msg ID 52),
  and `errorMessage` (msg ID 4). Map portfolio rows to
  `BrokerPosition(CanonicalSymbol(symbol, exchange, currency),
  Quantity, Money(marketPrice))`. **No order submission, no
  market-data subscription, no streaming, no historical-data requests
  in v1** — keeps the LOC ~1,200 and the IBKR-facing surface minimal.
- [ ] **J.13.d** **`IbkrTwsClientPlugin` wrapper.** Implements
  `ConnectorPlugin` from Phase X. `fetch()` opens the socket via
  `IbkrSocketClient`, sends handshake + `reqAccountUpdates`, collects
  one full snapshot (terminated by `accountDownloadEnd`, msg ID 54),
  writes `ValuationEvent(source="plugin:ibkr-tws-client")` per
  position, then unsubscribes and closes. Test connection runs
  `fetch()` and asserts at least one `accountValue` row arrived
  (an empty account is still a valid connection). Plugin license
  `MIT` (our own clean-room code). **Disabled by default;** registers
  in `PluginManifest.all`.
- [ ] **J.13.e** **Robolectric + JVM tests.** Socket-pair fixtures
  replay captured wire dumps (synthetic, hand-crafted — not from a
  real IBKR account, per the privacy posture in `CLAUDE.md`). Cover
  happy-path snapshot, partial-frame reads, error-message handling,
  disconnect mid-snapshot, version-mismatch handshake failure.
- [ ] **J.13.f** **License-posture comment block** at the top of
  `IbkrSocketClient.kt` documenting (1) why `TwsApi.jar` was rejected,
  (2) the *Oracle v. Google* clean-room rationale for re-implementing
  the wire protocol against the public docs, (3) the explicit
  instruction to future contributors: do not paste any code from
  `TwsApi.jar` or any GPL/LGPL fork of it into this file. Same block
  cross-referenced from `docs/plans/oss-licenses.md` if that file's
  scope grows to cover clean-room policy.
- [ ] **J.14** Implement the **`comdirect` plugin**. Plugin id
  `comdirect`, category `AssetPrice`, license `MIT`. `ConfigScreen()`
  runs the OAuth 2.0 authorization-code flow against
  `https://api.comdirect.de/` via system browser (Custom Tabs);
  stores the refresh token encrypted. Test connection reads the depot
  summary. `fetch()` reads positions via
  `/brokerage/v3/depots/{depotId}/positions`. **Disabled by default.**
- [ ] **J.15** UI: Assets screen "Add security" entry. Form asks for
  symbol + exchange + quantity + cost basis + (optional) source-broker
  plugin to associate the holding with. If a broker plugin is `Active`
  for this holding, prices refresh from the plugin; otherwise the
  holding is manual.
- [ ] **J.16** Manual-revaluation flow for securities: same shape as
  the manual-class wizard from `asset-realestate.md` / `asset-vehicles.md`.
  "Update price" writes a `ValuationEvent(source="manual")`.
- [ ] **J.17** Implement the **`trade-republic` plugin** (v1.x,
  unofficial-API tier). Plugin id `trade-republic`, category
  `AssetPrice`, license `MIT` (reimplemented in Kotlin from the
  `Zarathustra2/TradeRepublicApi` MIT reference). `ConfigScreen()`
  runs the phone-number + PIN + SMS-device-pairing flow; stores the
  device token encrypted. **Privacy statement explicitly names the
  ToS-grey risk** ("This plugin uses an unofficial API that Trade
  Republic may break without warning"). **Disabled by default.**
- [ ] **J.18** Per-exchange holiday calendar (small static data: NYSE,
  Xetra, LSE, TSX, HKEX, TSE). The `PluginScheduler` skips fetches on
  closed days. Static data in `app/src/main/assets/holidays/`.
- [ ] **J.19** CSV-import plugins for the four v1 brokers:
  `ibkr-csv`, `comdirect-csv`, `trade-republic-csv`, plus one
  reference US broker CSV (`schwab-csv` or `fidelity-csv`). Each
  plugin is in category `Import`, disabled by default, with a
  one-time column-mapping Configure flow. Shares the parser harness
  from `banking-import.md`.
- [ ] **J.20** Phase B+ plugin stubs (file-only, no impl): document
  `dkb-securities` (CSV-only for v1, automated plugin gated on DKB
  publishing a securities API), `flatex` (CSV-only), `robinhood`
  (unofficial-API, US users), `schwab` (developer-registration-gated),
  `fidelity` (CSV-only), `vanguard` (CSV-only). Each gets one
  paragraph in a new `docs/plans/asset-securities-brokers.md` index
  file noting current API status + reason for v1 deferral.

### References

- Plugin architecture authority: [`connector-plugins.md`](connector-plugins.md)
- IBKR TWS API: <https://interactivebrokers.github.io/tws-api/>
- IBKR API hub: <https://www.interactivebrokers.com/en/trading/ib-api.php>
- IBKR TWS API license page (Non-Commercial License Agreement, verbatim): <https://interactivebrokers.github.io/>
- IBKR official GitHub mirror (no SPDX license declared): <https://github.com/InteractiveBrokers/tws-api-public>
- *Google LLC v. Oracle America, Inc.*, 593 U.S. ___ (2021) — Java API surface is fair use; basis for the clean-room re-implementation of the TWS wire protocol from public docs: <https://www.supremecourt.gov/opinions/20pdf/18-956_d18f.pdf>
- Trade Republic unofficial client (reference, MIT): <https://github.com/Zarathustra2/TradeRepublicApi>
- Comdirect developer portal: <https://developer.comdirect.de/>
- Schwab developer portal: <https://developer.schwab.com/>
- Robinhood unofficial client (reference, MIT): <https://github.com/jmfernandes/robin_stocks>
- Rejected SaaS sources (for the record): <https://www.alphavantage.co/>, <https://finnhub.io/>, <https://polygon.io/>, <https://twelvedata.com/>, <https://marketstack.com/>
- IEX Cloud shutdown notice: <https://www.alphavantage.co/iexcloud_shutdown_analysis_and_migration/>
