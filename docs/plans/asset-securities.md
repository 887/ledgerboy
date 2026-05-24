# ledgerboy — asset: securities (stocks / ETFs / mutual funds)

## Status: RESEARCH — decisions locked, Phase J sub-steps queued

Per-class decision file spawned from [`asset-research.md`](asset-research.md).
Covers **listed securities**: stocks, ETFs, mutual funds. Crypto lives in
[`asset-crypto.md`](asset-crypto.md); FX rates live in the banking-research
cluster.

## What it is

A *security* in ledgerboy is a holding identified by **ticker + exchange**
that has a daily end-of-day price published by an exchange (or NAV for
mutual funds). The user enters the position; ledgerboy fetches the price
once a day and writes one `ValuationEvent(date, value, source)` row per
fetch. Total position value =
`unitPrice × quantity` in the security's quote currency, converted to
the user's display currency via the FX cache (researched by the parallel
agent).

End-of-day pricing is sufficient. Real-time / intraday is not a goal —
this is a net-worth tracker, not a trading app.

## Source candidates

### Alpha Vantage — **adopt**, v1 default

- **What:** Free + paid stock / ETF / FX / crypto data API. Long-lived,
  broad coverage, user-provided API key.
- **API:** `https://www.alphavantage.co/query?` — REST + JSON.
- **Auth:** **user-provided free API key** (the user registers at
  https://www.alphavantage.co/support/#api-key — no card required).
- **Rate limit (2026, free tier):** **25 requests / day** at **5
  requests / minute**. This is a hard drop from the historical 500/day
  cap (the seed prompt's expectation of 500/day is stale). Sources:
  https://www.alphavantage.co/premium/ ,
  https://alphalog.ai/blog/alphavantage-api-complete-guide .
- **What 25/day means for ledgerboy:** with one end-of-day refresh per
  holding per day, the free tier comfortably tracks **up to ~25
  positions per user**. For larger portfolios, the user either upgrades
  to Alpha Vantage's $50/mo tier (75 req/min, no daily cap) or falls
  back to finnhub.io for the overflow. We do not pay for Alpha
  Vantage; the API key is the user's.
- **ToS posture:** automated client-side use with a registered API key
  is the intended use case. Personal-use clients are explicitly
  in-scope. **Automated client-side lookup is permitted.**
- **Attribution:** required where data is displayed. Surface: Settings
  → "Data sources" + per-position caption on the asset-detail screen,
  same shape as the collectibles cluster.
- **Coverage:** US (NYSE, NASDAQ, AMEX), London (LSE), Frankfurt
  (Xetra), Toronto (TSX), Hong Kong (HKEX), Tokyo (TSE), plus most EU
  national exchanges via the `GLOBAL_QUOTE` and `TIME_SERIES_DAILY`
  endpoints. Mutual funds covered via NAV symbols (the symbol is the
  fund ticker, e.g. `VTSAX`). ETFs covered as regular tickers.
- **Symbol resolution:** ticker + exchange suffix
  (e.g. `SAP.DE` for SAP SE on Xetra vs. `SAP` for SAP Inc. on NYSE,
  `BARC.LON` for Barclays on LSE). The `SYMBOL_SEARCH` endpoint
  disambiguates user input ("SAP" → list of matches with exchange,
  region, currency) and we cache the canonical symbol on the
  `AssetEntity`. Same shape Yahoo Finance uses.
- **Real-time vs. EOD:** **end-of-day** on the free tier (the
  `TIME_SERIES_DAILY` endpoint returns yesterday's close on weekdays,
  Friday's close on weekends). Real-time US quotes are paid-tier-only.
  Fine for net-worth.
- **Mutual funds:** priced once daily at NAV — exactly the cadence
  Alpha Vantage's daily endpoint serves. No special path needed.
- **ETFs:** intraday on the exchange, but we only use EOD.
- **Maintenance:** continuously active, frequent doc updates through
  2026.

### finnhub.io — **adopt as fallback / overflow**

- **What:** Free + paid real-time stock / forex / crypto API. Sharper
  free-tier rate limit than Alpha Vantage, narrower symbol coverage on
  the free tier (free tier is US-stock-heavy; non-US coverage often
  paid-gated).
- **API:** `https://finnhub.io/api/v1/` — REST + JSON.
- **Auth:** user-provided free API key (https://finnhub.io/register).
- **Rate limit (2026, free tier):** **60 requests / minute.** Sources:
  https://finnhub.io/pricing ,
  https://dev.to/nexgendata/best-free-stock-market-apis-and-data-tools-in-2026-a-developers-honest-comparison-1926 .
- **What 60/min means:** plenty of headroom for any realistic personal
  portfolio. The constraint is symbol coverage on the free tier, not
  request budget.
- **ToS posture:** automated client-side use with a registered API key
  is the intended use case. **Automated client-side lookup is
  permitted.**
- **Attribution:** required where data is displayed.
- **Coverage (free tier):** US stocks (NYSE, NASDAQ, AMEX) — full.
  International exchanges (LSE, Xetra, TSX, HKEX, etc.) — **paid
  tier only** on most endpoints; the free `/quote` endpoint covers US
  symbols and some major EU ETFs, but not the full Xetra list. Verify
  per-holding before relying on it for a non-US position.
- **Symbol resolution:** `/search?q=...` returns ticker + exchange +
  type. Cache the canonical symbol on the `AssetEntity`.
- **Real-time vs. EOD:** real-time available for US stocks on the free
  tier. EOD also available. ledgerboy uses EOD only.
- **Maintenance:** active.

### Polygon.io — **defer**, paid-tier dominant

- **API:** `https://api.polygon.io/` .
- **Free tier (2026):** 5 req/min, **15-minute-delayed data**, 2 years
  of daily history. Sources: https://polygon.io/pricing ,
  https://tradingtoolshub.com/review/polygon-io/ .
- **License gate (effectively):** the free tier's 5 req/min cap and
  15-min delay are workable for net-worth, but Polygon's real strength
  is real-time + tick data which is paid-only. For ledgerboy's
  net-worth-only use case Alpha Vantage's free tier strictly dominates
  on symbol coverage; Polygon's free tier is US-only. **Not a v1
  default.** Documented as an alternative if Alpha Vantage's 25/day
  cap becomes binding and the user wants a free-tier fallback.

### IEX Cloud — **rejected, shut down**

- IEX Cloud shut down in **August 2024**. The Alpha Vantage migration
  guide (https://www.alphavantage.co/iexcloud_shutdown_analysis_and_migration/)
  documents this. **Not an option in 2026.**

### Yahoo Finance (unofficial) — **rejected**, ToS-disqualified

- The yfinance / yahoo_fin libraries scrape Yahoo's frontend. Yahoo's
  ToS forbids automated extraction; the endpoint shape breaks
  periodically when Yahoo redesigns the frontend.
- **ToS gate fails.** Documented; **not used.**

### Frankfurter — **not a securities source**

- Free ECB-backed FX API (`https://api.frankfurter.app/`). FX-only.
  Lives in the banking / FX cluster, not this one. Mentioned here only
  because the seed listed it; not in scope for this file.

### ECB equity data — **rejected, sparse**

- ECB publishes aggregate equity indices, not per-security quotes.
  Not useful for ledgerboy.

### Twelve Data — **document, defer**

- **API:** `https://api.twelvedata.com/` .
- **Free tier (2026):** 800 credits/day on the Basic plan, 8 requests
  per minute. Source: https://twelvedata.com/pricing .
- **Coverage:** stocks (US + most international exchanges), forex,
  crypto, ETFs.
- **Fit:** plausible second-fallback if both Alpha Vantage *and*
  finnhub.io become unworkable. Documented as a known alternative;
  not a v1 connector. The 800/day credit budget covers ~50 holdings
  per day with the `/price` endpoint (1 credit each), so it would also
  be a viable Alpha-Vantage replacement for users with portfolios in
  the 25–50 range.

### Marketstack — **document, defer**

- `https://marketstack.com/` — free tier, 70+ global exchanges. Free
  tier limits are tight (100 req/month historically). Documented;
  defer.

### Direct from exchange APIs — **out of scope**

- Xetra, LSE, NYSE publish data via paid SIP / proprietary feeds. Not
  appropriate for a personal-use app.

## Lookup shape

The user enters:

1. **Symbol** (e.g. `SAP`, `VTSAX`, `IWDA`)
2. **Exchange** (sealed enum: `XETRA`, `NASDAQ`, `NYSE`, `LSE`, `TSX`,
   `HKEX`, `TSE`, plus an `OTHER` with a free-text MIC code)
3. **Quote currency** (autodetected from the resolved upstream symbol;
   user can override if mis-resolved)
4. **Quantity** (Decimal-as-Long minor units — half-shares from
   fractional brokers are real)
5. **Cost basis** (optional Money, for return-tracking)

ledgerboy calls `SYMBOL_SEARCH` (or the source's equivalent) at
add-time to confirm the (symbol, exchange) pair resolves to exactly
one upstream security. Cached canonical symbol lives on the
`AssetEntity`. The (symbol, exchange) combination is necessary because
the seed's example holds: `SAP.DE` (SAP SE, Xetra) ≠ `SAP`
(SAP Inc., NYSE).

Lookup is **deterministic**: same (symbol, exchange) always resolves to
the same security.

## Revaluation cadence

- **Automatic refresh:** once per day, on app open, if the cached
  `latestValuationEvent.date` is older than 1 day.
- **TTL:** 1 day (end-of-day pricing — refreshing more often gains
  nothing on the free tier and burns request budget).
- **Manual refresh:** "refresh prices" on the Assets screen forces a
  fetch.
- **Bulk refresh:** Alpha Vantage's 5/min cap means a 25-holding
  portfolio refresh takes ~5 minutes; a `WorkManager` job runs it in
  the background with spacing. finnhub.io's 60/min cap means a
  100-holding portfolio refreshes in ~2 minutes.
- **Weekend / holiday awareness:** the EOD price doesn't change on
  weekends or exchange holidays. The connector consults a small per-
  exchange holiday calendar; on a known-closed day, it skips the fetch
  and keeps the most recent `ValuationEvent`. Phase J.x sub-step.

## Fit recommendation

- **Alpha Vantage:** **automated connector, v1 default.** User-provided
  API key. Fits up to ~25 positions on the free tier.
- **finnhub.io:** **automated connector, v1 fallback / overflow.** For
  US-heavy portfolios and for portfolios > 25 positions on the free
  tier. User-provided API key.
- **Twelve Data:** documented alternative, not v1.
- **Polygon.io:** documented alternative, not v1.
- **IEX Cloud:** dead, do not use.
- **Yahoo Finance unofficial:** ToS-disqualified.
- **Frankfurter / ECB:** wrong cluster.

The user picks the source per holding (or accepts the per-source
default: Alpha Vantage primary, finnhub.io if Alpha Vantage
doesn't cover the symbol). The choice lives on the `AssetEntity` row,
not as a global setting — different holdings can use different sources
and the `ValuationEvent.source` field already accommodates this.

## Cross-cutting

### `SecuritySource` interface

```kotlin
interface SecuritySource {
    val id: String                  // "alphavantage", "finnhub"
    val attribution: AttributionLine
    val rateLimit: RateLimitPolicy
    val ttl: Duration               // 1.days

    suspend fun search(query: String): Result<List<SecurityMatch>>
    suspend fun fetchPrice(symbol: CanonicalSymbol): Result<Money>
}

data class CanonicalSymbol(val symbol: String, val exchange: Exchange, val currency: Currency)
```

Each impl in `com.eight87.ledgerboy.asset.securities.<source>`. Per
the SOLID Open/Closed rule, adding finnhub.io after Alpha Vantage =
new impl + new registry entry, not an `if (source == ...)` chain.

### Caching

Same shape as collectibles: every `fetchPrice` result writes one
`ValuationEvent` row. Append-only; no eviction. The
`SecurityMatch` results from `search` are cached for 30 days (symbol
disambiguation rarely changes).

### Rate-limit discipline

| Source        | Spacing | Burst | Notes                                       |
| ------------- | ------- | ----- | ------------------------------------------- |
| Alpha Vantage | 12 s    | 5     | 5/min ≈ 12 s spacing; daily cap 25 req      |
| finnhub.io    | 1 s     | 60    | 60/min; spacing matters less                |
| Twelve Data   | 8 s     | 8     | 8/min on free tier                          |
| Polygon.io    | 12 s    | 5     | 5/min on free tier                          |

The shared `RateLimitedClient` (defined in the collectibles file;
reused here verbatim) enforces these via an `OkHttpClient`
`Interceptor`. Alpha Vantage's 25/day cap is enforced as a separate
daily-budget counter persisted to DataStore — when the budget is
exhausted, scheduled refreshes pause until midnight UTC and the UI
shows a non-blocking "Daily price-fetch budget reached" badge.

### Attribution surface

- **Settings → Data sources** lists Alpha Vantage and (if active)
  finnhub.io with one-line credit + link, same screen as the
  collectibles sources.
- **Asset-detail screen** shows "via Alpha Vantage" / "via finnhub.io"
  next to the latest-price line.
- Strings live in `strings.xml` per the i18n discipline.

### API-key storage

The user-provided API keys are stored encrypted (Android Keystore
wrap, per [`security-research.md`](security-research.md)). Settings →
Data sources → Alpha Vantage shows a "Set API key" entry. Empty key
disables the source — the connector throws `NoApiKeyException` which
the UI translates to "Add an Alpha Vantage API key in Settings to
enable automatic price refresh".

### Money shape

Quoted prices arrive as JSON numbers (e.g. `"165.42"` from Alpha
Vantage `GLOBAL_QUOTE`). The connector parses to `Money(long,
currency)` at the boundary, minor-units precision (currency-aware —
JPY has zero decimals, KWD has three). Quantity is stored as long
with 6-decimal precision (`1234567` = `1.234567` shares) to handle
fractional shares from brokers like Trade Republic; this is *not* a
Money — it's a `Quantity` value class. Position value =
`unitPrice × quantity / 10^6`, computed via integer math, never
floating point.

## Decision

- **v1 automated connector:** **Alpha Vantage** with user-provided
  API key. Symbol resolution via `SYMBOL_SEARCH`, EOD pricing via
  `TIME_SERIES_DAILY` or `GLOBAL_QUOTE`. 25 req/day free-tier cap
  fits ~25 positions; surfaced honestly to the user.
- **v1 fallback:** **finnhub.io** with user-provided API key for
  portfolios that exceed Alpha Vantage's daily cap or that hold
  symbols Alpha Vantage doesn't cover. US-symbol-strong, international
  coverage weaker on the free tier.
- **No paid integrations in v1.** IEX Cloud is dead. Polygon.io,
  Twelve Data, Marketstack documented for future.
- **No scraped Yahoo Finance.** ToS-disqualified.

### `ValuationEvent` write path

```
WorkManager daily-refresh job runs (or user taps "Refresh prices"):
  for each holding:
    source = holding.preferredSource ?? defaultSource(holding.exchange)
    if budgetRemaining(source) == 0:
      skip; surface "daily budget reached" badge
    if not exchangeOpenSinceLastFetch(holding.exchange, latestValuationEvent.date):
      skip; weekend / holiday
    price = source.fetchPrice(holding.canonicalSymbol)
    if success:
      insert ValuationEvent(date = now(), value = price * quantity,
                            source = source.id)
    else:
      surface "stale price" badge, keep latest row
  done
```

### Phase J implementation sub-steps

- [ ] **J.11** Define the `SecuritySource` interface, `CanonicalSymbol`,
  `Exchange` sealed enum, `Quantity` value class. JVM unit tests for
  fractional-share quantity math + the daily-budget counter logic.
- [ ] **J.12** Implement `AlphaVantageSource` against
  `https://www.alphavantage.co/query` with the 12 s spacing
  + 25/day daily budget. `SYMBOL_SEARCH` for resolution,
  `GLOBAL_QUOTE` for EOD pricing (cheaper than `TIME_SERIES_DAILY` for
  a single-day fetch). Fixture-based JSON parser test against
  fictional symbols.
- [ ] **J.13** Wire `AlphaVantageSource` into the `AssetEntity` model:
  new asset class enum value `SECURITY`, new fields for `symbol`,
  `exchange`, `quote_currency`, `quantity`, `cost_basis_money`. Room
  additive migration.
- [ ] **J.14** Settings → Data sources → Alpha Vantage entry: API key
  input (encrypted via the security cluster's keystore wrapping),
  "test connection" button, attribution copy from strings.xml. Lands
  in J.14 with Alpha Vantage only.
- [ ] **J.15** UI: Assets screen "Add security" entry. Form asks for
  symbol + exchange + quantity + cost basis. On save, `SYMBOL_SEARCH`
  confirms the resolution; the user picks the right match from the
  list if multiple; first `ValuationEvent` written immediately.
- [ ] **J.16** Per-exchange holiday calendar (small static data: NYSE,
  Xetra, LSE, TSX, HKEX, TSE). `WorkManager` skips fetches on closed
  days. Static data committed in `app/src/main/assets/holidays/`.
- [ ] **J.17** Implement `FinnhubSource` against
  `https://finnhub.io/api/v1/quote`. Adds the second source; the
  source-picker UI on the per-holding settings now offers a choice.
- [ ] **J.18** Daily-budget UI: a "Today's price-fetch usage: 18 / 25"
  line in Settings → Data sources → Alpha Vantage. Resets at the
  source's local-time midnight (UTC for Alpha Vantage).
- [ ] **J.19** Bulk-refresh `WorkManager` job: runs once daily at a
  user-configurable time (default: 23:00 local), iterates holdings,
  respects spacing + budgets. Persisted across reboots via
  `setPeriodicWork`.
- [ ] **J.20** Mutual-fund quirks: mutual funds price once daily at
  market close; document that the price the user sees the morning
  after is yesterday's NAV. No code change — just a one-line caption
  on mutual-fund holdings ("priced daily at fund close").
