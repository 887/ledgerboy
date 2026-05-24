# ledgerboy — FX rate source decision

## Status: RESEARCH — recommendation locked, Phase B implementation pending

> Parent: [`banking-research.md`](banking-research.md) · sibling decisions: [`banking-psd2.md`](banking-psd2.md) · [`banking-ebics.md` (pending)](banking-research.md) · [`banking-fints.md` (pending)](banking-research.md) · [`banking-import.md` (pending)](banking-research.md)

## What it is

Ledgerboy holds accounts and transactions denominated in multiple currencies (EUR savings, USD brokerage, CHF cash, GBP credit card, JPY mobile wallet, etc.) and renders aggregates — per-account balance, per-budget envelope, net worth, category spend over time — in a single user-selected **base currency**. Doing that arithmetic correctly requires an FX rate table that the app can:

1. Look up at a specific historical date (transactions are dated; converting them to base currency uses the rate on the transaction date, not today's rate — otherwise net-worth charts swing with current FX noise instead of reflecting real holdings movement).
2. Refresh on a regular cadence (daily is fine — we are not trading).
3. Cache locally and persistently, including a long historical backlog (so back-dated imports from years ago convert correctly), with append-only writes.

Because ledgerboy is local-first and runs on-device, the FX source must be a public free HTTP endpoint with permissive usage terms — no per-API-key throttling layer that requires a server we operate.

## Source candidates

### ECB daily euro foreign-exchange reference rates — **recommended primary**

- **What:** The European Central Bank publishes daily reference rates for the euro against the 30 most-traded currencies. The reference rates are concertation-based fixings published around 16:00 CET on every TARGET banking day (no rates on weekends or TARGET holidays). The data is **public domain** (ECB explicitly waives reuse restrictions for the reference rates) and served as small XML feeds with no API key.
- **Endpoints:**
  - Daily (today's rates only): <https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml> (~2–3 KB).
  - 90 days history: <https://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist-90d.xml> (~70 KB).
  - Full history (since 1999-01-04, 30 years): <https://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist.xml> (~4 MB).
- **Refresh cadence:** Once per TARGET banking day, around 16:00 CET. Polling more often than that returns the same data.
- **Coverage:** EUR ↔ {AUD, BGN, BRL, CAD, CHF, CNY, CZK, DKK, GBP, HKD, HUF, IDR, ILS, INR, ISK, JPY, KRW, MXN, MYR, NOK, NZD, PHP, PLN, RON, SEK, SGD, THB, TRY, USD, ZAR}. Notably **missing**: RUB (suspended 2022), exotic / pegged currencies, most African + Middle Eastern currencies (apart from ZAR, ILS), most LATAM currencies (apart from BRL, MXN). For the user's likely currency mix (EUR-centric with USD/CHF/GBP/JPY/SEK/NOK/DKK exposure typical for a DACH-region user) coverage is complete.
- **Base currency:** **EUR-paired only.** Cross-rates (e.g. USD → JPY) are computed as `(EUR→JPY) / (EUR→USD)`. See "Cross-rate precision" below.
- **Cost:** Free, no key, no rate limit beyond reasonable use. Source: <https://www.ecb.europa.eu/stats/policy_and_exchange_rates/euro_reference_exchange_rates/html/index.en.html> ("This information may be obtained free of charge").
- **ToS:** Public domain reuse. Attribution requested ("Source: European Central Bank") but not legally required for the reference rates. Source: <https://www.ecb.europa.eu/services/disclaimer/html/index.en.html>.
- **Format:** XML. The daily file is a 3-element nested structure (`<Cube time="…"><Cube currency="USD" rate="…"/>…</Cube>`). Easily parsed with `XmlPullParser` (Android stdlib) or `kotlinx-serialization-xml`. No need for a third-party XML library.
- **Maintenance gate:** It's the ECB. Not going anywhere on any timescale relevant to this app.

### Frankfurter — **recommended HTTP surface for ECB data**

- **What:** Open-source REST proxy in front of the ECB feeds. Provides JSON responses and a friendlier query surface (date-range queries, single-pair lookups, base-currency selection) than the ECB's raw XML. Hosted free at `https://api.frankfurter.dev/v1/` (the legacy `api.frankfurter.app` host is being deprecated in favour of `.dev`).
- **Endpoints:**
  - Latest: <https://api.frankfurter.dev/v1/latest?base=EUR>.
  - Historical day: `https://api.frankfurter.dev/v1/2025-01-15?base=EUR&symbols=USD,JPY,GBP`.
  - Range: `https://api.frankfurter.dev/v1/2025-01-01..2025-12-31?base=EUR`.
- **Coverage:** Identical to ECB (Frankfurter proxies the same fixings) — 30 currencies, weekday-only.
- **Cost:** Free, no API key, no documented rate limit beyond reasonable use. Source: <https://www.frankfurter.dev/>.
- **License:** Frankfurter's source is open-source (the **hosted service** is free; we don't redistribute Frankfurter's code so the license-gate doesn't apply, but for the record the project is MIT-licensed). Source: <https://github.com/lineofflight/frankfurter>.
- **Maintenance gate:** Active, MIT, regular maintenance commits in 2025–2026. Self-hosting fallback exists if the public instance ever goes away (we don't plan to, but it's a safety net).
- **Risk:** Single-operator free service. If frankfurter.dev disappears, we fall back to parsing ECB's raw XML directly — same data, slightly more code. Implement both paths and let the connector failover.

### `exchangerate.host`

- **What:** Free FX REST API with broader currency coverage than ECB (170+ currencies, including crypto and some pegged / exotic currencies).
- **Cost:** Free tier exists but has shifted to requiring an `access_key` as of late 2024 (was previously totally open). Free plan is limited to 100 calls/month and HTTP-only (no HTTPS) which is fatal for an app handling financial data. Source: <https://exchangerate.host/>.
- **Authority:** Less authoritative than ECB — the source data origin is opaque, with discrepancies vs. ECB fixings observed for minor pairs.
- **Fit:** **Reject.** The HTTPS-paid-only and key-required changes make it unsuitable for a no-server-we-operate app, and the data is less authoritative than ECB for the currencies we actually need.

### Per-bank FX rates from PSD2 / EBICS statements

- **What:** Each bank-supplied transaction in a foreign currency carries the bank's own settlement-rate FX in the statement payload (e.g. PSD2 returns `currencyExchange` blocks; CAMT.053 has `<CcyXchg>` per-entry tags). These rates are bank-accurate for **that specific transaction at that specific settlement moment** — the rate the user actually paid.
- **Fit:** **Adopt as a per-transaction override.** When a transaction carries a bank-supplied FX rate, store it on the `TransactionEntity` as the per-row override; aggregates over date ranges (net worth, balance) still use the daily ECB table. Best of both worlds: transactions show the true rate paid, time-series aggregates use a single coherent rate series.
- **Heterogeneity:** Different banks supply the rate at different precision levels and sometimes without all the fields populated. The connector layer normalises into an optional `Money` rate field on `NormalizedTransaction`; the ledger writer stores it if present, falls back to the ECB lookup if not.

### Paid sources (OANDA, TrueFX, Open Exchange Rates)

- **OANDA Exchange Rates API:** Paid subscription, no free tier suitable for an app. Authoritative for trading-grade rates but overkill for a personal-finance app. Source: <https://www.oanda.com/business-solutions/exchange-rates/>.
- **TrueFX:** Real-time tick-level FX for trading, paid. Out of scope. Source: <https://www.truefx.com/>.
- **Open Exchange Rates:** Free tier of 1,000 calls/month with USD base only and historical data behind paid tiers. Less authoritative than ECB and the USD-base default is awkward for a EUR-centric DACH user. Source: <https://openexchangerates.org/signup>.
- **Fit:** **Reject all.** No personal-use app needs trading-grade FX, and the free-tier constraints on hobbyist services are worse than ECB's "free, public, no key" baseline.

## Source summary

| Source | Cost | Currencies | History | Authority | Verdict |
|---|---|---|---|---|---|
| **ECB XML** | Free, no key | 30 EUR pairs | 30y (since 1999-01-04) | Central bank reference | **Adopt (fallback)** |
| **Frankfurter** | Free, no key | 30 EUR pairs (ECB) | Same as ECB | ECB proxy | **Adopt (primary HTTP)** |
| Per-tx bank FX | Free, embedded | Per-transaction | n/a | Bank settlement rate | **Adopt (override)** |
| exchangerate.host | Paid key | 170+ | Yes | Opaque | Reject |
| OANDA / TrueFX | Paid | Wide | Yes | Trading-grade | Reject |
| Open Exchange Rates | Free 1k/mo | Wide, USD-base | Paid only | Medium | Reject |

## Concrete questions (answered)

- **Q: Refresh cadence?** Daily, after 16:30 CET to give the ECB fixing time to publish. Scheduled via WorkManager. A skipped day (TARGET holiday, weekend) leaves the previous rate in place; the lookup function falls back to "most recent rate on or before date X" so weekend transactions are valued at Friday's close, which is the convention.
- **Q: Historical rates for back-dated transactions?** Fetch the full ECB 30-year history (`eurofxref-hist.xml`, ~4 MB) on first install. Parse and bulk-insert into the `fx_rates` Room table. Subsequent runs only need the daily incremental.
- **Q: Cache size?** 30 currencies × ~7,000 TARGET business days × (4-byte date key + 8-byte rate as fixed-point) ≈ ~2.5 MB in Room (with row overhead, expect 3–5 MB on disk). Negligible for an Android app.
- **Q: Non-EUR base currency cross-rates?** Compute on the fly: `rate(A→B, date) = rate(EUR→B, date) / rate(EUR→A, date)`. Pre-computation is not worth it — the cache is small and the math is one division per lookup. **Precision:** store ECB rates as `Long` fixed-point with 8 decimal places of scale (i.e. `rate * 10^8`); cross-rate arithmetic in `Double` is acceptable for display but **the canonical conversion of a `Money` value uses `BigDecimal` once at the boundary**, rounded to the target currency's minor-unit precision per ISO 4217. The `Money` value itself stays integer minor units; the FX conversion is the one place `BigDecimal` is permitted, and only as a transient intermediate.
- **Q: Caching strategy?** Persist forever, append-only. Room table `fx_rates(date: Long, currency: String, ratePerEurFixed: Long, source: String, primaryKey(date, currency))`. No eviction — 5 MB is cheap and the historical rates never change. On reinstall, re-fetch the full history if the table is empty.
- **Q: TTL?** None on stored rows (historical rates are immutable). The "freshness" concept applies only to "do we need to fetch today's rate yet?" — controlled by a single `last_refresh_at` value in DataStore Preferences.
- **Q: What if Frankfurter is down?** Fall through to ECB XML directly. Both paths normalise to the same `fx_rates` row shape, so the consumer code doesn't know or care which path served the data.
- **Q: What if a transaction is in a currency ECB doesn't cover (e.g. TRY before 2022 or a pegged-to-USD exotic)?** Surface "unsupported currency for base-currency conversion — using bank-supplied FX rate if available, else 1:1 placeholder with a warning badge." Don't silently invent a rate.

## Fit for ledgerboy — adopt / defer / reject

- **ECB (via Frankfurter as the primary HTTP surface, ECB XML as the fallback): adopt** as the v1 FX rate source. Free, public, central-bank authoritative, sufficient currency coverage for the realistic user mix, 30-year history available on first install, no server we operate.
- **Per-transaction bank-supplied FX rates: adopt as an override.** When a connector emits a `NormalizedTransaction` with a `currencyExchange` block, store it on the transaction row and use it for that one transaction's conversion. Aggregates still use the daily ECB table.
- **exchangerate.host / OANDA / TrueFX / Open Exchange Rates: reject.** None of them improve on the ECB-via-Frankfurter baseline for a personal-finance app.

## Decision

**Adopt ECB reference rates served via Frankfurter, with ECB XML as the fallback transport, and per-transaction bank-supplied FX as a row-level override.**

Surface in code:

- Module: stays in the single app module (the FX surface is small — one repository, one Room table, one WorkManager job).
- Interface: `FxRateSource` with `suspend fun rate(from: Currency, to: Currency, at: LocalDate): FxRate?` and `suspend fun refresh(): RefreshResult`. Two concrete implementations: `FrankfurterFxSource` (primary) and `EcbXmlFxSource` (fallback). A `CachingFxRateSource` wraps both, hitting Room first.
- HTTP client: reuse the OkHttp + kotlinx-serialization-json stack from `banking-psd2.md` for Frankfurter. ECB XML path uses `XmlPullParser` (Android stdlib) — no extra dep.
- Storage: Room table `fx_rates`. Schema: `(date INTEGER, currency TEXT, rate_per_eur_fixed INTEGER, source TEXT)`, PK `(date, currency)`. `rate_per_eur_fixed` is the rate × 10^8 stored as `Long` to avoid float arithmetic at rest.
- Initial backfill: on first run, fetch `eurofxref-hist.xml` (~4 MB), parse, bulk-insert in a single Room transaction. Show a one-time "Loading historical exchange rates…" progress bar on the splash screen.
- Daily refresh: WorkManager daily job at 16:45 local time (after the 16:00 CET fixing publishes), fetches `eurofxref-daily.xml` or Frankfurter `/latest`, upserts. If the network is down, the job retries with WorkManager's standard exponential backoff.
- Settings: Settings → Currency → "Base currency" (dropdown of the 30 ECB-covered currencies + EUR), "FX source" (ECB-via-Frankfurter — single choice for v1, kept as a setting so we can add alternatives later without a settings-screen reshuffle), "Last updated" (read-only timestamp).
- Per-transaction override: if `NormalizedTransaction.fxRate` is non-null, store on `TransactionEntity.fxRateOverridePerEurFixed`. The conversion function checks the per-row override first, then falls back to the `fx_rates` table for the transaction date.

## Phase B implementation sub-steps

- [ ] **B.fx.1** Define `FxRateSource` interface + `FxRate(from: Currency, to: Currency, at: LocalDate, ratePerEurFixed: Long, source: String)` data class.
- [ ] **B.fx.2** Add Room `fx_rates` entity + DAO. Migration plan: this table is added in the initial schema (no migration needed since it lands pre-1.0).
- [ ] **B.fx.3** Implement `EcbXmlFxSource` using `XmlPullParser`. Two methods: `fetchLatest()` (daily XML) + `fetchFullHistory()` (30-year XML). Returns `List<FxRate>` normalised to per-EUR rates.
- [ ] **B.fx.4** Implement `FrankfurterFxSource` using OkHttp + kotlinx-serialization-json. Endpoints: `/v1/latest?base=EUR`, `/v1/<date>?base=EUR`. Returns the same `List<FxRate>` shape.
- [ ] **B.fx.5** Implement `CachingFxRateSource`: tries Room first (`fx_rates` lookup for the requested date + currency), falls back to "most recent rate on or before date" if no exact match, returns null if no rate within 14 days (then UI shows "no rate available" badge). Network sources are only hit by the `refresh()` path, not by per-lookup calls.
- [ ] **B.fx.6** First-install backfill: in `AppGraph`'s init, if `fx_rates` is empty, kick off a background job that fetches `eurofxref-hist.xml`, parses, bulk-inserts. Show progress on the splash screen.
- [ ] **B.fx.7** WorkManager daily refresh job. Tries Frankfurter first, falls back to ECB XML. Upserts. Updates `last_refresh_at` DataStore preference.
- [ ] **B.fx.8** `Money.convertTo(target: Currency, at: LocalDate, source: FxRateSource): Money?` extension. Implementation: look up `rate(money.currency, target, at)`, multiply via `BigDecimal` intermediate, round to target's ISO 4217 minor-unit precision, return new `Money`. Returns null if no rate available (caller decides whether to fall back to 1:1 with a warning or to refuse to render).
- [ ] **B.fx.9** Robolectric unit tests:
  - ECB XML parser against canned XML fixtures (today's file + a 90-day file).
  - Frankfurter JSON deserialisation against canned fixtures.
  - Cross-rate computation: `USD → JPY` via `EUR → USD` / `EUR → JPY` matches a hand-computed expected value within rounding.
  - `Money.convertTo` rounds correctly for every ISO 4217 minor-unit count (0 for JPY, 2 for USD/EUR, 3 for KWD, 4 for CLF).
  - Lookup falls back to most-recent-prior-day across a TARGET holiday gap.

## Phase C implementation sub-steps

- [ ] **C.fx.1** Wire `FxRateSource` into the net-worth / balance / budget aggregator services (defined Phase C in `main.md`). Aggregators compute in base currency; per-account balances render in account-native currency with base-currency tooltip.
- [ ] **C.fx.2** Add per-transaction FX override support: if `NormalizedTransaction.fxRate` is non-null from a connector, store on `TransactionEntity`. Conversion path checks override first.
- [ ] **C.fx.3** Settings → Currency panel: base-currency picker, FX-source dropdown (single choice for v1), last-updated timestamp, "Refresh now" button.
- [ ] **C.fx.4** Smoke test on the AVD: change base currency from EUR to USD, watch a fictional sample dataset re-aggregate. Screenshot for the PR.

## References

- ECB euro reference rates landing page: <https://www.ecb.europa.eu/stats/policy_and_exchange_rates/euro_reference_exchange_rates/html/index.en.html>
- ECB daily XML feed: <https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml>
- ECB 90-day history XML: <https://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist-90d.xml>
- ECB full history XML (since 1999-01-04): <https://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist.xml>
- ECB disclaimer / reuse terms: <https://www.ecb.europa.eu/services/disclaimer/html/index.en.html>
- Frankfurter project: <https://www.frankfurter.dev/>
- Frankfurter source (MIT): <https://github.com/lineofflight/frankfurter>
- ISO 4217 minor-unit counts: <https://www.iso.org/iso-4217-currency-codes.html>
- TARGET calendar (ECB business days): <https://www.ecb.europa.eu/ecb/educational/explainers/tell-me/html/target_balance.en.html>
- exchangerate.host (rejected): <https://exchangerate.host/>
- OANDA Exchange Rates API (rejected): <https://www.oanda.com/business-solutions/exchange-rates/>
- Open Exchange Rates (rejected): <https://openexchangerates.org/signup>
