# ledgerboy — asset: cryptocurrencies

## Status: RESEARCH — decisions locked, Phase J sub-steps queued

Per-class decision file spawned from [`asset-research.md`](asset-research.md).
Covers **crypto holdings** as a distinct asset class with its own price
feed. Exchange-balance fetching (live balances pulled directly from
Kraken / Coinbase / Binance accounts) is out of scope for v1 and
documented as a clearly-bounded future phase.

## What it is

A *crypto holding* in ledgerboy is a quantity of a specific
cryptocurrency that the user owns (in a hardware wallet, hot wallet,
or on an exchange) and wants to count in their net worth. The user
enters the symbol (`BTC`, `ETH`, `SOL`) and a quantity; ledgerboy
fetches the current spot price in the user's preferred quote currency
(USD or EUR), values the position, and writes one
`ValuationEvent(date, value, source)` row per fetch.

Crypto is treated as a **distinct asset class**, not as "a security
where the exchange is the venue". The shape is similar to securities
but the cadence is faster (intraday volatility matters more) and the
identifier is simpler (a symbol, no exchange disambiguation — the
spot market is global enough).

## Source candidates

### CoinGecko — **adopt**, v1 default

- **What:** Free + paid crypto market-data API. Broadest coverage of
  any free-tier crypto API in 2026 (16,000+ coins indexed). Spot prices
  in 50+ fiat + crypto quote currencies.
- **API:** `https://api.coingecko.com/api/v3/` (Demo / free tier)
  and `https://pro-api.coingecko.com/api/v3/` (paid).
- **Auth (2026 update — this changed from the seed assumption):** the
  Demo plan now requires a free API key (`x-cg-demo-api-key` header).
  The fully-unauthenticated public endpoint of the past has been
  deprecated. Source:
  https://www.coingecko.com/en/api/pricing ,
  https://support.coingecko.com/hc/en-us/articles/4538771776153 .
- **Rate limit (2026, Demo plan):** **100 calls / minute**, **10,000
  calls / month**, **50+ endpoints accessible**. Source:
  https://docs.coingecko.com/docs/common-errors-rate-limit .
- **What 10,000/month means:** with 1-hour TTL on price fetches (see
  cadence below) and ~720 fetches/month per holding, the free tier
  comfortably tracks **up to ~13 holdings per user** on the hourly
  cadence, or hundreds of holdings on a daily cadence. Realistic
  personal portfolios fit.
- **ToS posture:** automated client-side use with a registered Demo
  API key is the intended use case. **Automated client-side lookup is
  permitted.**
- **Attribution:** required where data is displayed. Surface: Settings
  → "Data sources" + asset-detail caption, same shape as collectibles
  + securities clusters.
- **Coverage:** 16,000+ coins; 50+ quote currencies (USD, EUR, GBP,
  JPY, CHF, BTC, ETH, etc.). Includes long-tail tokens (ERC-20 / SPL /
  etc.) most other APIs skip. Historical OHLC available on free tier
  for a limited window.
- **Symbol resolution:** CoinGecko's canonical key is a slug
  (`bitcoin`, `ethereum`, `solana`), not the ticker — because
  tickers collide (multiple tokens use `UNI`, `BNB`, etc.). The
  `/search` endpoint returns slug + ticker + market-cap rank for any
  query; we cache the slug on the `AssetEntity`. The
  `/simple/price?ids={slug}&vs_currencies={fiat}` endpoint is the
  cheap per-holding price fetch.
- **Maintenance:** continuously active.

### CoinMarketCap — **adopt as fallback**

- **What:** Free + paid crypto market-data API. Broad coverage; the
  primary alternative to CoinGecko.
- **API:** `https://pro-api.coinmarketcap.com/v1/` (free Basic tier
  uses the same Pro endpoint with a free-tier API key).
- **Auth:** user-provided free API key
  (https://coinmarketcap.com/api/pricing/).
- **Rate limit (2026, Basic free tier):** **10,000 call credits /
  month**, 30 endpoints, **no historical data on the free tier**.
  Credits are tracked per "data point returned", not per HTTP call;
  bundled queries (multi-symbol fetches) cost multiple credits.
  Source: https://coinmarketcap.com/api/pricing/ ,
  https://pro.coinmarketcap.com/api/documentation/guides/errors-and-rate-limits .
- **ToS posture:** automated client-side use with API key permitted.
- **Attribution:** required.
- **Coverage:** similar to CoinGecko on the major coins; slightly
  narrower on long-tail tokens.
- **Fit:** **fallback** if CoinGecko's Demo plan rate-limits the user
  or if CoinGecko's coverage misses a specific token. User-provided
  API key, same shape as Alpha Vantage / finnhub.io in the securities
  cluster.

### Per-exchange APIs (Kraken / Coinbase / Binance) — **defer to post-v1**

- Per the seed prompt: per-exchange APIs are useful for **live balance
  fetching** from exchange accounts (read-only API key on the user's
  exchange account → ledgerboy pulls the current balances → reconciles
  with the user-entered holdings). **They are not a price source** —
  for that, CoinGecko / CoinMarketCap are simpler and more universal.
- **Out of scope for v1.** Documented as a Phase J+ stretch ("Crypto
  exchange-balance sync"), gated on:
  1. Per-exchange OAuth / API-key flow research (Kraken uses HMAC-SHA512
     query signing; Coinbase Advanced Trade uses an Ed25519 JWT;
     Binance uses HMAC-SHA256 — each is a separate connector).
  2. Per-exchange license review (Kraken's API is permissive; Binance's
     API ToS varies by region).
  3. A `CryptoExchangeConnector` interface parallel to `BankConnector`.

  The user enters holdings manually in v1; exchange-balance sync is a
  later enhancement, not a v1 promise.

### Other documented alternatives

- **CryptoCompare** — free tier exists (100k calls/month historically;
  verify current). Slightly less coverage than CoinGecko.
- **Messari** — paid-tier dominant; not a v1 candidate.
- **Nomics** — shut down in 2023; do not use.

## Lookup shape

The user enters:

1. **Symbol** (e.g. `BTC`, `ETH`, `SOL`) — ledgerboy resolves to a
   CoinGecko slug via `/search`.
2. **Quote currency** (USD or EUR by default; user-overridable per
   holding from CoinGecko's 50+ supported quotes).
3. **Quantity** (Decimal-as-Long, 8-decimal precision — BTC's smallest
   unit is the satoshi, 10⁻⁸ BTC; ETH's smallest unit is wei,
   10⁻¹⁸ ETH, but 8-decimal precision on the display layer is the
   universal floor and what ledgerboy stores).
4. **Cost basis** (optional Money).
5. **Custody label** (optional free-text: "Ledger Nano X", "Trezor",
   "Coinbase", "Kraken" — informational only, not connected to any
   exchange API in v1).

Lookup is **deterministic via the CoinGecko slug**: ticker may collide,
slug does not.

## Revaluation cadence

- **Automatic refresh:** **1 hour TTL.** Crypto is volatile enough
  that 1-day is too coarse for users who actually want to know their
  net worth at midday; 1-hour is the sweet spot between freshness and
  request-budget burn. Configurable in Settings (1h / 6h / 24h options).
- **Manual refresh:** "refresh prices" on the Assets screen forces a
  fetch.
- **Bulk refresh:** CoinGecko's `/simple/price` endpoint accepts
  multiple slugs in one call (`ids=bitcoin,ethereum,solana`). One HTTP
  request per refresh cycle for the entire crypto portfolio. This is
  why a 13-holding-on-hourly-cadence portfolio fits inside the 10,000
  calls/month free-tier budget: ~720 calls/month total, not 720 ×
  holdings.
- **Weekend awareness:** crypto markets are 24/7. No holiday calendar
  needed.

## Fit recommendation

- **CoinGecko (Demo plan):** **automated connector, v1 default.**
  User-provided free API key. Hourly default TTL. Bulk-fetch via
  `/simple/price`.
- **CoinMarketCap:** **automated connector, v1 fallback** for users
  whose CoinGecko key gets rate-limited or whose token isn't on
  CoinGecko.
- **CryptoCompare, Messari, Nomics, etc.:** documented; not v1.
- **Per-exchange APIs:** out of scope for v1. Future "exchange-balance
  sync" phase, gated on its own research file.

## Cross-cutting

### `CryptoSource` interface

```kotlin
interface CryptoSource {
    val id: String                  // "coingecko", "coinmarketcap"
    val attribution: AttributionLine
    val rateLimit: RateLimitPolicy
    val ttl: Duration               // 1.hours default, user-configurable

    suspend fun resolve(symbol: String): Result<List<CryptoMatch>>
    suspend fun fetchPrices(slugs: List<String>, quote: Currency): Result<Map<String, Money>>
}

data class CryptoMatch(val slug: String, val symbol: String, val name: String, val rank: Int?)
```

Impls in `com.eight87.ledgerboy.asset.crypto.<source>`. The `fetchPrices`
method takes a *list* of slugs because the source-of-truth endpoints
support bulk queries; this shape pays off enormously on rate-limit
budget vs. one-by-one fetches. (The securities `SecuritySource.fetchPrice`
is single-symbol because Alpha Vantage's `GLOBAL_QUOTE` is single-
symbol; the crypto interface earns the difference.)

### Caching

Same shape as collectibles + securities: every `fetchPrices` result
writes one `ValuationEvent` row **per holding** (not per fetch — the
fetch returns N prices, we write N events). Append-only; no eviction.
`CryptoMatch` resolution results cached for 30 days.

### Rate-limit discipline

| Source        | Spacing | Burst | Notes                                       |
| ------------- | ------- | ----- | ------------------------------------------- |
| CoinGecko     | 600 ms  | 100   | 100/min Demo plan; bulk endpoint preferred  |
| CoinMarketCap | varies  | —     | Credit-based; track credits/month budget    |

CoinMarketCap's "credits" model means the spacing matters less than
the per-month budget. Track via DataStore counter; reset monthly on
the user's billing-day-equivalent (or 1st of each month).

CoinGecko's 10,000-calls-per-month cap is also tracked in DataStore as
a monthly counter; the bulk-fetch shape keeps usage to ~720 calls/month
regardless of holding count, so the cap is rarely binding.

### Attribution surface

- **Settings → Data sources** lists CoinGecko (and CoinMarketCap if
  active) with one-line credit + link. Same screen as collectibles +
  securities sources.
- **Asset-detail screen** shows "via CoinGecko" next to the latest
  price.
- Strings in `strings.xml` under the `data_source_<id>_attribution`
  naming convention.

### API-key storage

Same as the securities cluster: API keys stored encrypted via Android
Keystore wrap (per [`security-research.md`](security-research.md)).
Settings → Data sources → CoinGecko shows a "Set API key" entry. Empty
key disables the source. CoinGecko's Demo plan key is free + no card —
surface that explicitly in the onboarding copy (the user thinks
"API key" means "paid service"; it doesn't here).

### Money shape

CoinGecko's `/simple/price` returns prices as JSON numbers
(`{"bitcoin": {"usd": 71234.56, "eur": 65890.12}}`). The connector
parses to `Money(long, currency)` at the boundary. Quantity is a
`Quantity` value class (8-decimal precision long), shared with the
securities cluster. Position value = `unitPrice × quantity / 10⁸`,
integer math throughout. Display conversion to the user's net-worth
base currency goes through the FX cache.

**JPY edge case:** CoinGecko quotes JPY without decimals (correct —
yen has zero minor units). The `Money(_, JPY)` value class respects
the per-currency minor-unit count.

## Decision

- **v1 automated connector:** **CoinGecko Demo plan** with
  user-provided free API key. Hourly TTL default. Bulk
  `/simple/price` fetches one HTTP request per refresh cycle. The
  10,000-calls-per-month free tier covers any realistic personal
  portfolio.
- **v1 fallback:** **CoinMarketCap Basic plan** with user-provided
  free API key. For users CoinGecko-rate-limited or holding tokens
  CoinGecko misses.
- **Out of scope for v1:** per-exchange balance fetching (Kraken,
  Coinbase, Binance). Documented as a future "Crypto exchange-balance
  sync" phase with its own research gate.
- **Treatment:** crypto is a distinct asset class with its own
  connector interface, not folded into `SecuritySource`. Different
  cadence (hourly vs. daily), different identifier (slug vs.
  symbol+exchange), different bulk-fetch shape — fits the SOLID
  Interface Segregation rule.

### `ValuationEvent` write path

```
WorkManager hourly-refresh job runs (or user taps "Refresh prices"):
  cryptoHoldings = assets.filter { it.assetClass == CRYPTO }
  if cryptoHoldings.isEmpty(): return
  if all holdings' latestValuationEvent.date < now() - source.ttl:
    quote = userBaseCurrency  // USD or EUR by default
    slugs = cryptoHoldings.map { it.coingeckoSlug }
    pricesPerSlug = CoinGeckoSource.fetchPrices(slugs, quote)
    if success:
      for each holding:
        price = pricesPerSlug[holding.coingeckoSlug]
        insert ValuationEvent(date = now(),
                              value = price * holding.quantity / 10^8,
                              source = "coingecko")
    else:
      surface "stale price" badge on the Crypto section, keep latest rows
  done
```

### Phase J implementation sub-steps

- [ ] **J.21** Define the `CryptoSource` interface, `CryptoMatch`
  data class, the bulk-fetch `fetchPrices(slugs, quote)` shape. JVM
  unit tests for the credit-budget counter logic and the slug-resolve
  caching.
- [ ] **J.22** Implement `CoinGeckoSource` against
  `https://api.coingecko.com/api/v3/`. `x-cg-demo-api-key` header
  attached via `OkHttpClient` `Interceptor`. `/search?query=` for
  resolution, `/simple/price?ids=...&vs_currencies=...` for bulk
  price fetch. Fixture-based JSON parser test against fictional slugs.
- [ ] **J.23** Wire `CoinGeckoSource` into the `AssetEntity` model:
  new asset class enum value `CRYPTO`, new fields for `coingecko_slug`,
  `symbol`, `quote_currency`, `quantity` (reuses the securities
  cluster's `Quantity` value class), `custody_label`. Room additive
  migration.
- [ ] **J.24** Settings → Data sources → CoinGecko entry: API key
  input (encrypted via security cluster keystore wrapping), "test
  connection" button, attribution copy from `strings.xml`, plain-copy
  reminder that the Demo key is free + no card.
- [ ] **J.25** UI: Assets screen "Add crypto" entry. Form asks for
  symbol → resolves to slug via `/search` → user picks from list if
  ambiguous → quantity → quote currency → optional custody label.
  First `ValuationEvent` written immediately.
- [ ] **J.26** Hourly `WorkManager` refresh job: runs every hour while
  the app is in normal foreground/background state, batches all crypto
  holdings into one `/simple/price` call, writes per-holding
  `ValuationEvent` rows. Configurable TTL (1h / 6h / 24h).
- [ ] **J.27** Implement `CoinMarketCapSource` as the fallback. Add
  the per-holding source-picker (default: CoinGecko; user can switch
  per holding to CoinMarketCap if needed). Credit-based budget counter
  tracks separately from CoinGecko's call counter.
- [ ] **J.28** Monthly budget UI: "This month's CoinGecko usage:
  4,820 / 10,000 calls" in Settings → Data sources → CoinGecko. Resets
  at month boundary.
- [ ] **J.29** Document the deferred "Crypto exchange-balance sync"
  phase in a fresh `docs/plans/asset-crypto-exchange-sync.md` stub
  (Phase J+ research gate, not v1) — empty stub with the three open
  questions listed above (Kraken auth, Coinbase JWT, Binance regional
  ToS).
