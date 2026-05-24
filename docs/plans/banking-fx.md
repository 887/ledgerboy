# ledgerboy — FX rate plugin research

## Status: RESEARCH — recommendation rewritten 2026-05-24 after the connector-plugin architecture lock. Frankfurter dropped (third-party proxy of ECB data). ECB-direct and Bundesbank-direct each ship as a separate plugin off by default.

> Parent: [`banking-research.md`](banking-research.md) · Architectural authority: [`connector-plugins.md`](connector-plugins.md) · siblings: [`banking-psd2.md`](banking-psd2.md) · [`banking-fints.md`](banking-fints.md) · [`banking-ebics.md`](banking-ebics.md) · [`banking-import.md`](banking-import.md)

## Pivot summary

The earlier version of this file recommended **Frankfurter as the primary HTTP surface for ECB data**, with ECB XML as the fallback. **That recommendation is withdrawn.** Per [`connector-plugins.md`](connector-plugins.md), ledgerboy talks **direct to source-of-truth** — no proxies, no aggregators, no third-party intermediaries. Frankfurter is a third party between the user and the ECB even though it serves the same data; it's out.

Both ECB and Bundesbank publish their FX feeds directly as small XML files with no key, no auth, no rate limit. Two plugins:

- **`fx-ecb` plugin** — direct ECB euro foreign-exchange reference rates from `https://www.ecb.europa.eu/...`. Primary EUR-paired FX source for ledgerboy.
- **`fx-bundesbank` plugin** — direct Bundesbank rates from `https://www.bundesbank.de/...`. Alternative / wider-coverage source for currency pairs the ECB doesn't publish.

Both plugins are `Disabled` by default per the architecture. The user enables `fx-ecb` if they want auto-FX (a typical EUR-centric user enables this); enables `fx-bundesbank` as well if they hold currencies the ECB doesn't cover.

The **per-transaction bank-supplied FX** path remains intrinsic — it's a property of `NormalizedTransaction` rows emitted by the banking plugins, not its own plugin. When a banking plugin reports a `currencyExchange` block on a transaction, that rate is used for that transaction. The `fx-ecb` plugin (or `fx-bundesbank`) only feeds the daily series consumed for aggregates and for transactions that don't carry a per-row override. **Manual rate entry** is also intrinsic — the user can always type a rate; no plugin needed.

## What FX is for in ledgerboy (recap)

Ledgerboy holds accounts and transactions denominated in multiple currencies and renders aggregates in a user-selected base currency. The aggregator needs:

1. A historical-dated rate (transactions are dated; converting them uses the rate on the transaction date, not today's rate).
2. A regular refresh cadence (daily is fine).
3. A persistent local cache including a long historical backlog (so back-dated imports convert correctly), append-only writes.

Because every plugin is off by default, a user with no FX plugin enabled gets:

- Per-transaction bank-supplied FX where the banking plugin provides it.
- Manual rate entry for anything else.
- A "no rate available, base-currency aggregate unavailable" badge for any transaction without either of the above.

That degraded mode is intentional — no plugin means no network fetch for FX.

## Source candidates

### ECB direct — primary FX plugin (`fx-ecb`)

- **What:** The European Central Bank publishes daily reference rates for the euro against the 30 most-traded currencies. Concertation-based fixings, published around 16:00 CET on every TARGET banking day. Public domain (ECB explicitly waives reuse restrictions for the reference rates). Served as small flat XML feeds with no API key.
- **Endpoints (direct, no proxy):**
  - Daily (today's rates only): <https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml> (~2–3 KB).
  - 90 days history: <https://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist-90d.xml> (~70 KB).
  - Full history (since 1999-01-04, 30 years): <https://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist.xml> (~4 MB) — also served as a ZIP at <https://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist.zip> (~1.5 MB compressed) for the first-install backfill.
- **Refresh cadence:** Once per TARGET banking day, around 16:00 CET. Polling more often than that returns the same data.
- **Coverage:** EUR ↔ {AUD, BGN, BRL, CAD, CHF, CNY, CZK, DKK, GBP, HKD, HUF, IDR, ILS, INR, ISK, JPY, KRW, MXN, MYR, NOK, NZD, PHP, PLN, RON, SEK, SGD, THB, TRY, USD, ZAR}. Notably missing: RUB (suspended 2022), most exotic / pegged currencies, most African + Middle Eastern currencies (apart from ZAR, ILS), most LATAM currencies (apart from BRL, MXN). For a typical EUR-centric DACH user, coverage is complete.
- **Base currency:** **EUR-paired only.** Cross-rates (e.g. USD → JPY) are computed as `(EUR→JPY) / (EUR→USD)`.
- **Cost:** Free, no key, no rate limit beyond reasonable use. Source: <https://www.ecb.europa.eu/stats/policy_and_exchange_rates/euro_reference_exchange_rates/html/index.en.html> ("This information may be obtained free of charge").
- **ToS:** Public domain reuse. Attribution requested ("Source: European Central Bank") but not legally required for the reference rates. Source: <https://www.ecb.europa.eu/services/disclaimer/html/index.en.html>.
- **Format:** XML. The daily file is a 3-element nested structure (`<Cube time="…"><Cube currency="USD" rate="…"/>…</Cube>`). Parsed with **`XmlPullParser` (Android stdlib)** — zero dependency. For the historical ZIP, **`ZipInputStream` (Android stdlib)** to unwrap then the same `XmlPullParser` walker over the embedded XML.
- **Maintenance gate:** It's the ECB. Not going anywhere on any timescale relevant to this app.

### Bundesbank direct — alternative FX plugin (`fx-bundesbank`)

- **What:** The Deutsche Bundesbank publishes its own daily FX rate set, broader than the ECB on some currency pairs (notably some Asian currencies and a few currency pairs the ECB does not publish a fixing for). Same legal posture: public-sector data under German Open Data terms, free, no key.
- **Endpoint:** Bundesbank Statistical Data Warehouse, **SDMX 2.1 REST API** — base URL `https://api.statistiken.bundesbank.de/rest/`. FX series live under flow `BBEX3` (e.g. `https://api.statistiken.bundesbank.de/rest/data/BBEX3/D.USD.EUR.BB.AC.000?format=csv&lang=de` for daily USD/EUR). XML (SDMX-ML) and CSV formats both available; CSV is simpler for our purposes.
- **Coverage:** broader than ECB — includes some pegged currencies and additional Asian / Eastern European pairs. Bundesbank also publishes some non-EUR-paired rates directly (e.g. USD/JPY observed at the same time as EUR/JPY).
- **Refresh cadence:** Daily on Bundesbank business days (~14:30 CET publication for the day's reference fixings).
- **Cost:** Free, no key, no rate limit beyond reasonable use. Source: <https://www.bundesbank.de/en/statistics/time-series-databases>.
- **ToS:** German Datenlizenz Deutschland 2.0 (open data licence, redistribution-permitted with attribution). Source: <https://www.govdata.de/dl-de/by-2-0>.
- **Format:** SDMX 2.1 (XML or CSV). CSV is the simpler path; one column per observation. Parsed via the CSV-core code from [`banking-import.md`](banking-import.md) or a small hand-rolled CSV walker if dependency-sharing is awkward.
- **Maintenance gate:** It's the Bundesbank. Same "not going anywhere" argument as the ECB.

### Frankfurter — **DROPPED**

The previous research round adopted [Frankfurter](https://www.frankfurter.dev/) as a friendlier HTTP surface for the ECB data. **Frankfurter is out**: per the connector-plugin architecture lock, ledgerboy does not sit a third party between the user and the source of truth, even when the third party is a free, open-source, well-maintained proxy of the same data. The ECB serves the same data directly as small XML files we parse in-app; there is no operational reason to route through a proxy.

### Other rejected sources (unchanged from prior research)

- **exchangerate.host** — API key required since 2024, HTTPS-paid-only on free tier, opaque source data. **Reject** — and it was never going to fit the plugin-architecture's direct-to-source posture anyway.
- **OANDA / TrueFX** — paid trading-grade. **Reject.**
- **Open Exchange Rates** — free tier USD-base only with historical behind paid. **Reject.**

## Source summary

| Source | Direct to source? | Cost | Currencies | History | Authority | Plugin verdict |
|---|---|---|---|---|---|---|
| **ECB XML (direct)** | yes | free, no key | 30 EUR pairs | 30y (since 1999-01-04) | central bank reference | **adopt as `fx-ecb` plugin** |
| **Bundesbank SDMX (direct)** | yes | free, no key | broader, includes some non-EUR pairs | multi-decade | central bank reference | **adopt as `fx-bundesbank` plugin** |
| Per-tx bank FX (intrinsic, not a plugin) | yes (via banking plugin) | free, embedded | per-transaction | n/a | bank settlement rate | **adopt (intrinsic, in banking plugins)** |
| Manual entry (intrinsic, not a plugin) | n/a | n/a | per-user | per-user | user | **adopt (intrinsic)** |
| Frankfurter | **no — third-party proxy** | n/a | n/a | n/a | n/a | **dropped** |
| exchangerate.host / OANDA / TrueFX / Open Exchange Rates | no | various | various | various | various | **reject** |

## Concrete questions (answered)

- **Q: Refresh cadence?** Daily, after 16:30 CET (ECB) / 14:30 CET (Bundesbank). WorkManager via the plugin scheduler (per [`connector-plugins.md`](connector-plugins.md) X.5). A skipped day (TARGET holiday, weekend) leaves the previous rate in place; the lookup function falls back to "most recent rate on or before date X" so weekend transactions are valued at Friday's close, which is the convention.
- **Q: Historical rates for back-dated transactions?** On first enable of `fx-ecb`, the plugin fetches `eurofxref-hist.xml` (~4 MB) or its ZIP variant (~1.5 MB), parses, bulk-inserts into the `fx_rates` Room table. Subsequent runs only need the daily incremental.
- **Q: Cache size?** 30 currencies × ~7,000 TARGET business days × (4-byte date key + 8-byte rate as fixed-point) ≈ ~2.5 MB in Room (with row overhead, expect 3–5 MB on disk). Negligible.
- **Q: Non-EUR base currency cross-rates?** Compute on the fly: `rate(A→B, date) = rate(EUR→B, date) / rate(EUR→A, date)`. Pre-computation is not worth it. **Precision:** store ECB rates as `Long` fixed-point with 8 decimal places of scale (i.e. `rate * 10^8`); cross-rate arithmetic in `Double` is acceptable for display but **the canonical conversion of a `Money` value uses `BigDecimal` once at the boundary**, rounded to the target currency's minor-unit precision per ISO 4217. The `Money` value itself stays integer minor units; the FX conversion is the one place `BigDecimal` is permitted, and only as a transient intermediate.
- **Q: Caching strategy?** Persist forever, append-only. Room table `fx_rates(date: Long, currency: String, ratePerEurFixed: Long, source: String, primaryKey(date, currency))`. No eviction — 5 MB is cheap and the historical rates never change. The `source` column distinguishes `"ecb"` from `"bundesbank"` so the user can see which plugin contributed a row (in Settings → Plugins → Network usage).
- **Q: TTL?** None on stored rows (historical rates are immutable). The "freshness" concept applies only to "do we need to fetch today's rate yet?" — controlled by a per-plugin `last_refresh_at` in the plugin's DataStore namespace.
- **Q: What if ECB is down?** If `fx-bundesbank` is also enabled, the host's plugin scheduler retries via Bundesbank on its next tick — but each plugin only fetches from its own source; we don't cross-wire. The user enables both plugins explicitly if they want failover.
- **Q: What if a transaction is in a currency neither plugin covers (e.g. RUB after 2022)?** Surface "no rate available — using bank-supplied FX if present, else manual entry, else no aggregate." Don't silently invent a rate.

## Plugin shape — `fx-ecb`

Per [`connector-plugins.md`](connector-plugins.md), lives at `app/src/main/java/com/eight87/ledgerboy/plugins/fx-ecb/`, implements `ConnectorPlugin`, registered in `PluginManifest`.

- **`id = "fx-ecb"`** · **`category = Fx`** · `displayName` localised ("ECB euro reference rates") · `licenseSpdx = "MIT"` (our code) · `sourceUrl = "https://www.ecb.europa.eu/stats/policy_and_exchange_rates/euro_reference_exchange_rates/html/index.en.html"`.
- **`description`**: "Daily euro foreign-exchange reference rates from the European Central Bank, served directly from <https://www.ecb.europa.eu/stats/eurofxref/>. 30 currency pairs, fetched after the daily 16:00 CET fixing."
- **`privacyStatement`**: "Data leaves the device to: `https://www.ecb.europa.eu/stats/eurofxref/*.xml`. No third party. ECB sees: device user agent, request timestamp."
- **State machine**: `Disabled` by default. User enables → `Enabled`. The `ConfigScreen()` has **zero config** beyond a single "Enable" toggle and a "Fetch now (with 30-year history backfill on first run)" button. After the first successful fetch → `Active`.
- **`ConfigScreen()`**:
  - One paragraph explaining what the plugin does + source URL.
  - "Fetch now" button. First fetch pulls the 30-year ZIP historical archive (~1.5 MB compressed → ~4 MB XML → ~5 MB Room). Subsequent fetches pull the daily XML (~2–3 KB).
  - Progress bar during the first-install backfill.
- **`fetch(window)`**: if the cache is empty, fetch the historical archive; else fetch the daily XML. Parse via `XmlPullParser` (and `ZipInputStream` for the archive). Bulk-upsert into `fx_rates` with `source = "ecb"`.
- **`PluginNetworkGuard`**: per [`connector-plugins.md`](connector-plugins.md) X.4, the plugin's HTTPS calls to `ecb.europa.eu` only happen when the plugin is `Active` (or in the `Configured`→`Active` transition fetch triggered by the user explicitly).

## Plugin shape — `fx-bundesbank`

Sibling of `fx-ecb` under `app/src/main/java/com/eight87/ledgerboy/plugins/fx-bundesbank/`.

- **`id = "fx-bundesbank"`** · **`category = Fx`** · `displayName` localised ("Bundesbank exchange rates") · `licenseSpdx = "MIT"` (our code) · `sourceUrl = "https://www.bundesbank.de/en/statistics/time-series-databases"`.
- **`description`**: "Daily reference rates from the Deutsche Bundesbank Statistical Data Warehouse, served directly from `api.statistiken.bundesbank.de`. Broader coverage than ECB on some pairs (e.g. additional Asian / Eastern European currencies)."
- **`privacyStatement`**: "Data leaves the device to: `https://api.statistiken.bundesbank.de/rest/data/BBEX3/*`. No third party. Bundesbank sees: device user agent, request timestamp."
- **State machine**: same as `fx-ecb`, zero config beyond enable.
- **`ConfigScreen()`**: explains the relationship to `fx-ecb` (broader coverage, secondary source) + "Fetch now" button. Multi-select of which currency pairs to subscribe to (so we don't pull every BBEX3 series — only the user's actual currencies, derived from their accounts).
- **`fetch(window)`**: per currency pair the user holds, GET the BBEX3 CSV, parse, bulk-upsert into `fx_rates` with `source = "bundesbank"`. The lookup path prefers `source = "ecb"` over `source = "bundesbank"` when both have a rate for the same `(date, currency)`, so Bundesbank only fills gaps.

## Decision

**Adopt two FX plugins, both off by default:**

- **`fx-ecb` plugin** — direct from <https://www.ecb.europa.eu/stats/eurofxref/>, primary EUR-paired source, 30-year historical backfill on first enable, daily refresh via `PluginScheduler`. Zero dep — uses `XmlPullParser` + `ZipInputStream` from Android stdlib.
- **`fx-bundesbank` plugin** — direct from `https://api.statistiken.bundesbank.de/rest/data/BBEX3/`, alternative source for currency pairs ECB doesn't publish. Zero dep — uses the project's CSV-core walker.

**Per-transaction bank-supplied FX** stays intrinsic: a property of `NormalizedTransaction` rows from banking plugins, used as a per-transaction override before consulting `fx_rates`.

**Manual rate entry** stays intrinsic: the user can always type a rate; no plugin needed.

**Frankfurter is out.** It was a third-party proxy of the same ECB data, incompatible with the direct-to-source-of-truth posture locked in [`connector-plugins.md`](connector-plugins.md).

## Phase X (plugin runtime) — gating

Per [`connector-plugins.md`](connector-plugins.md), Phase X lands before either FX plugin. The runtime supplies:

- `ConnectorPlugin` interface + state machine (X.1).
- `PluginManifest` (X.2) — both `fx-ecb` and `fx-bundesbank` register.
- Per-plugin DataStore (X.3) — for `last_refresh_at` and (for `fx-bundesbank`) the subscribed-currency-pair list.
- `PluginNetworkGuard` OkHttp interceptor (X.4) — gates all `ecb.europa.eu` and `bundesbank.de` calls.
- `PluginScheduler` (X.5) — for the daily refresh job, per-plugin background-sync opt-in (default OFF).
- Settings → Plugins UI (X.6 / X.7 / X.8).

**Do not start the FX plugin sub-phases below until Phase X is complete.**

## Phase B implementation sub-step checklist

### B.fx-common — shared FX storage + math (lands once, before either plugin)

- [ ] **B.fx-common.1** Define `FxRate(currency: String, at: LocalDate, ratePerEurFixed: Long, source: String)` data class.
- [ ] **B.fx-common.2** Add Room `fx_rates` entity + DAO. Schema: `(date INTEGER, currency TEXT, rate_per_eur_fixed INTEGER, source TEXT)`, PK `(date, currency)`, `source` column distinguishes `"ecb"` from `"bundesbank"`.
- [ ] **B.fx-common.3** `FxRateLookup`: tries Room first; falls back to "most recent rate on or before date" if no exact match; returns null if no rate within 14 days (caller decides whether to fall back to manual / bank-supplied / refuse-render). Network sources are never hit here — that's the plugin's `fetch()` path only.
- [ ] **B.fx-common.4** `Money.convertTo(target: Currency, at: LocalDate): Money?` extension. Look up `rate(money.currency, target, at)`, multiply via `BigDecimal` intermediate, round to target's ISO 4217 minor-unit precision, return new `Money`. Returns null if no rate available.
- [ ] **B.fx-common.5** Cross-rate computation `(EUR→B) / (EUR→A)` for non-EUR base. Robolectric tests cover every ISO 4217 minor-unit count (0/2/3/4) and verify rounding.

### B.fx-ecb — ECB plugin

- [ ] **B.fx-ecb.1** Implement `EcbFxPlugin : ConnectorPlugin` under `plugins/fx-ecb/`. Register in `PluginManifest`. SPDX `MIT`.
- [ ] **B.fx-ecb.2** ECB XML parser via `XmlPullParser` (Android stdlib). Two paths: `fetchDaily()` (daily XML) + `fetchHistorical()` (30-year ZIP, unwrap via `ZipInputStream`, parse the embedded XML).
- [ ] **B.fx-ecb.3** `ConfigScreen()`: enable toggle + Fetch-now button + progress bar for first-install backfill. All strings in `values/strings.xml` per CLAUDE.md i18n discipline.
- [ ] **B.fx-ecb.4** `fetch()` implementation: if `fx_rates` empty for `source="ecb"`, kick off `fetchHistorical()`; else `fetchDaily()`. Bulk-upsert. Update plugin DataStore `last_refresh_at`.
- [ ] **B.fx-ecb.5** Privacy statement string (single endpoint family).
- [ ] **B.fx-ecb.6** Robolectric tests: parse canned daily XML, parse canned historical XML excerpt, ZIP unwrap.

### B.fx-bundesbank — Bundesbank plugin

- [ ] **B.fx-bundesbank.1** Implement `BundesbankFxPlugin : ConnectorPlugin` under `plugins/fx-bundesbank/`. Register in `PluginManifest`. SPDX `MIT`.
- [ ] **B.fx-bundesbank.2** BBEX3 CSV fetcher via OkHttp; CSV parser via the CSV-core walker from [`banking-import.md`](banking-import.md) (or a small inline parser if dep-sharing is awkward).
- [ ] **B.fx-bundesbank.3** `ConfigScreen()`: enable toggle + multi-select for currency pairs (defaults to the union of currencies present in the user's accounts; user can override) + Fetch-now button.
- [ ] **B.fx-bundesbank.4** `fetch()` implementation: per subscribed pair, GET the BBEX3 CSV, parse, bulk-upsert with `source = "bundesbank"`. Update plugin DataStore `last_refresh_at`.
- [ ] **B.fx-bundesbank.5** Privacy statement string.
- [ ] **B.fx-bundesbank.6** Robolectric tests: parse canned BBEX3 CSV fixture, verify lookup ordering when both `"ecb"` and `"bundesbank"` have rows for the same `(date, currency)` (ECB wins).

## Phase C implementation sub-steps

- [ ] **C.fx.1** Wire `FxRateLookup` into the net-worth / balance / budget aggregator services. Aggregators compute in base currency; per-account balances render in account-native currency with base-currency tooltip.
- [ ] **C.fx.2** Per-transaction FX override path: if `NormalizedTransaction.fxRate` is non-null from a banking plugin, store on `TransactionEntity` and use for that one transaction; aggregates over date ranges still use `FxRateLookup`.
- [ ] **C.fx.3** Manual-rate-entry UI: per-transaction "Override rate" + per-account "Custom rate for this currency" — both intrinsic, no plugin.
- [ ] **C.fx.4** Settings → Currency panel: base-currency picker. The FX-source choice surfaces via Settings → Plugins → Fx category (per [`connector-plugins.md`](connector-plugins.md) X.6), not a duplicated picker here.
- [ ] **C.fx.5** Smoke test on the AVD: enable `fx-ecb`, watch first-install backfill, change base currency, watch aggregates re-render. Screenshot for the PR.

## References

- Connector plugin architecture (authority): [`connector-plugins.md`](connector-plugins.md)
- ECB euro reference rates landing page: <https://www.ecb.europa.eu/stats/policy_and_exchange_rates/euro_reference_exchange_rates/html/index.en.html>
- ECB daily XML feed: <https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml>
- ECB 90-day history XML: <https://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist-90d.xml>
- ECB full history XML (since 1999-01-04): <https://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist.xml>
- ECB full history ZIP: <https://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist.zip>
- ECB disclaimer / reuse terms: <https://www.ecb.europa.eu/services/disclaimer/html/index.en.html>
- Bundesbank time-series databases: <https://www.bundesbank.de/en/statistics/time-series-databases>
- Bundesbank SDMX REST base: <https://api.statistiken.bundesbank.de/rest/>
- Datenlizenz Deutschland 2.0: <https://www.govdata.de/dl-de/by-2-0>
- ISO 4217 minor-unit counts: <https://www.iso.org/iso-4217-currency-codes.html>
- TARGET calendar (ECB business days): <https://www.ecb.europa.eu/ecb/educational/explainers/tell-me/html/target_balance.en.html>
