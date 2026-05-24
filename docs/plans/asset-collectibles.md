# ledgerboy — asset: collectibles (trading cards focus)

## Status: REWRITTEN 2026-05-24 — each automated source reframed as a plugin off by default per [`connector-plugins.md`](connector-plugins.md); Scryfall / pokemontcg.io / YGOPRODeck remain the v1 candidates because they are free, direct, community-open-data; no auto-enable

Per-class decision file spawned from [`asset-research.md`](asset-research.md). Covers
the **collectibles** asset class with **trading cards** as the v1 driver
(user's explicit example) and a forward path for adjacent collectible
sub-classes (coins, comics, watches, sneakers).

The technical analysis of each card-data source (Scryfall, pokemontcg.io,
YGOPRODeck) below is preserved verbatim from the original research; the
*recommendation* is reframed in plugin vocabulary. **No source is enabled
by default.** Every source ships as a `ConnectorPlugin` in the
asset-price category, disabled by default, configured per-user. See
[`connector-plugins.md`](connector-plugins.md) for the architectural
authority (plugin lifecycle, Configure flow, Test connection, per-plugin
transparency, network kill-switch).

## What it is

A *collectible* in ledgerboy is a physical, fungible-with-a-grade,
secondhand-market-priced object that the user holds primarily for
value rather than utility. Trading cards are the cleanest case: each
holding is identified by **game + set + collector number + finish + condition**,
priced by an out-of-band marketplace (TCGplayer in the US, Cardmarket
in the EU, Yahoo Auctions in Japan), and revalued whenever the user
opens the app. Coins / comics / watches / sneakers share the shape but
lack a free public price feed.

Each holding is one `AssetEntity` row. Each price snapshot is one
`ValuationEvent(date, value, source)` row (per
[`asset-research.md`](asset-research.md) constraint 5 — historical, never
collapsed). The current value is `latestValuationEvent.value`. The
denomination currency lives on the holding (a Cardmarket-priced card is
`Money(long, EUR)`, a TCGplayer-priced card is `Money(long, USD)`);
display conversion goes through the FX cache (researched by the parallel
agent).

## Source candidates

### Trading cards — Magic: The Gathering

#### Scryfall — **adopt**, v1 default

- **What:** Community-maintained MTG card database with TCGplayer +
  Cardmarket prices synced daily.
- **API:** `https://api.scryfall.com/` — REST + JSON.
- **Auth:** none required.
- **Rate limit:** 10 req/sec for lightweight endpoints (cards by ID,
  sets, bulk data, autocomplete); 2 req/sec for heavier endpoints
  (Search, Named, Random, Collection). Scryfall asks for 50–100 ms
  spacing between requests as a courtesy floor, and **HTTP 429
  responses lead to access being limited**. Source:
  https://scryfall.com/docs/api/rate-limits .
- **Required headers:** every request must include `User-Agent` (e.g.
  `Ledgerboy/0.1`) **and** `Accept: application/json`. Source:
  https://scryfall.com/docs/api .
- **ToS posture:** Scryfall publishes data "free of charge for the
  primary purpose of creating additional Magic software, performing
  research, or creating community content about Magic" under the WotC
  Fan Content Policy. Personal-use client automation is explicitly
  in-scope. **Automated client-side lookup is permitted.**
- **Attribution requirement:** required where card data / images are
  displayed. Surface: ledgerboy Settings → "Data sources" entry +
  per-card a small "via Scryfall" caption on the asset-detail screen.
- **Price-history availability:** **current price only.** Scryfall does
  not expose a price-history endpoint. The Card object carries a
  `prices` map (`usd`, `usd_foil`, `usd_etched`, `eur`, `eur_foil`,
  `tix`) reflecting the most recent sync. Source:
  https://scryfall.com/docs/api/cards .
- **What we build the history from:** **our own
  `ValuationEvent` rows.** Every fetch we make appends one row per
  holding — that *is* the history.
- **Coverage:** every printed MTG set, all paper finishes, every
  language.
- **Card identification flow:** Scryfall's canonical key is the
  set code + collector number, both lowercase
  (`/cards/{set}/{collector_number}`). The user enters set + collector
  number; we cache the resulting `id` (UUID) on the `AssetEntity` row
  and use the cheaper `/cards/{id}` for revaluations.
- **Condition grading:** Scryfall returns **mint-equivalent** prices
  only (no per-condition breakdown). We store the user's grade
  (NM / LP / MP / HP / DMG) on the holding and apply a configurable
  multiplier table for display estimates (NM = 1.0 default, LP = 0.85,
  MP = 0.70, HP = 0.50, DMG = 0.30 — user-editable in Settings).
  Document that the multiplier is an estimate, not a measurement.

#### Cardmarket API — **defer pending paid tier**

- **What:** EU's dominant secondhand-card marketplace.
- **API:** OAuth 1.0a, available only to "professional sellers" and
  approved integrators. Personal-use clients are out of scope.
- **License gate (effectively):** API access is paywalled and gated on
  approval, so it does not displace Scryfall for v1. If the user
  becomes a Cardmarket merchant later, revisit. Documented for
  completeness; **not part of v1.**

#### TCGplayer API — **defer pending paid tier**

- **What:** US's dominant secondhand-card marketplace.
- **API:** REST, OAuth — partner programme, approval-gated. Sellers
  use it; personal-use clients are not the target audience.
- **License gate (effectively):** same shape as Cardmarket. **Not part
  of v1.**

### Trading cards — Pokémon

#### pokemontcg.io — **adopt**, v1.x

- **What:** Community-run Pokémon TCG database with TCGplayer +
  Cardmarket prices.
- **API:** `https://api.pokemontcg.io/v2/` — REST + JSON.
- **Auth:** API key required for usable limits (free, user-provided —
  the user registers at https://dev.pokemontcg.io). Unauthenticated
  requests have "drastically reduced rate limits" per
  https://docs.pokemontcg.io/getting-started/rate-limits/ .
- **Rate limit:** **20,000 requests/day** with an API key. Source:
  https://docs.pokemontcg.io/getting-started/rate-limits/ .
- **ToS posture:** automated client-side lookup permitted; attribution
  expected. The Pokémon TCG API joined the Scrydex umbrella in 2025
  but the v2 endpoint and free tier remain in place — verify on the
  doc landing page https://docs.pokemontcg.io/ before locking the
  connector.
- **Attribution:** required. Same surface as Scryfall (Settings →
  "Data sources", asset-detail caption).
- **Price availability:** TCGplayer prices (USD) and Cardmarket prices
  (EUR) on the card object. TCGplayer payload breaks down by finish
  (`normal`, `reverseHolofoil`, `holofoil`, `1stEditionHolofoil`,
  `1stEditionNormal`) with `low` / `mid` / `high` / `market` /
  `directLow` per finish. Cardmarket payload includes `averageSellPrice`,
  `lowPrice`, `trendPrice`, and 1/7/30-day averages. **Price history is
  partial** (rolling-window averages, not a full timeline) — we still
  build our own `ValuationEvent` series from each fetch.
- **Coverage:** every printed Pokémon TCG set, all major finishes.
- **Card identification flow:** set ID + card number
  (`/v2/cards/{set}-{number}`, e.g. `xy1-1`). User enters set + number;
  we cache the API ID on the holding.
- **Condition grading:** same approach as Scryfall — user enters
  LP / MP / HP grade; we apply the configurable multiplier table.

### Trading cards — Yu-Gi-Oh!

#### YGOPRODeck — **adopt**, v1.x

- **What:** Community-run Yu-Gi-Oh! card database with TCGplayer +
  Cardmarket + ebay-aggregate prices.
- **API:** `https://db.ygoprodeck.com/api/v7/cardinfo.php` — REST + JSON.
- **Auth:** none.
- **Rate limit:** **20 requests/second**; exceeding it bans the IP for
  one hour. Source: https://ygoprodeck.com/api-guide/ .
- **ToS posture:** YGOPRODeck explicitly approves automated apps,
  Discord bots, deck builders, and mobile apps. The robots-exclusion
  clause refers to the website (db.ygoprodeck.com web frontend), not
  the API. **Automated client-side lookup is permitted** subject to
  two hard rules:
  1. **Cache all card data locally.** "Failure to do so may result in
     your IP address being blacklisted." We comply via Room storage
     of every card we ever touch — same cache as Scryfall.
  2. **Do not hotlink card images.** Download and re-host (in our case:
     cache to the app's private file storage and serve from there).
- **Attribution:** expected. Same surface (Settings → "Data sources",
  asset-detail caption).
- **Price availability:** `card_prices` array with `cardmarket_price`,
  `tcgplayer_price`, `ebay_price`, `amazon_price`, `coolstuffinc_price`
  per print. Current snapshot only — we build history from
  `ValuationEvent` rows.
- **Coverage:** every printed Yu-Gi-Oh! card across OCG + TCG +
  Speed Duel + Rush Duel.
- **Card identification flow:** card name or numeric `id`. For
  ledgerboy we ask for set + collector number from the user, look up
  the matching print, and cache the numeric `id`.
- **Condition grading:** same multiplier-table approach.

### Trading cards — sports (Topps / Panini / etc.)

#### COMC / PSA / sportscardspro — **manual-only**

- **What:** Secondhand graded-sports-card marketplaces.
- **API posture:** **paid APIs only**; the public sites' ToS forbid
  scraping. PSA's price-guide API is partner-gated.
- **ToS gate:** **automated client-side lookup is forbidden** for
  personal-use clients. Connector is **manual-only**: user enters a
  valuation manually, app reminds them every N months.

### Other collectibles

#### Coins (numismatics) — **manual-only**

- No general-purpose free price-feed API. NGC and PCGS publish
  reference catalogues but their pricing data is paywalled and not
  exposed via a public API. Specialist marketplaces (MA-Shops,
  VCoins) have closed APIs.
- **Recommendation:** **manual-only with optional revaluation reminder.**

#### Comics — **manual-only**

- GCD (Grand Comics Database) is a free metadata database (no prices).
  ComicsPriceGuide / GoCollect price data is paywalled.
- **Recommendation:** **manual-only with optional revaluation reminder.**

#### Watches — **manual-only**

- Chrono24 and WatchCharts have indices but no free personal-use API.
  Scraping is ToS-hostile.
- **Recommendation:** **manual-only with optional revaluation reminder.**

#### Sneakers — **manual-only**

- StockX historically had an unofficial API; the official API is
  enterprise-only and the unofficial one is ToS-hostile.
- **Recommendation:** **manual-only with optional revaluation reminder.**

## Lookup shape (the universal trading-card shape)

The user enters:

1. **Game** (`MTG` / `POKEMON` / `YGO` — sealed-class enum)
2. **Set code** (e.g. `mh3`, `xy1`, `LOB`)
3. **Collector number** (e.g. `137`, `1`, `EN001`)
4. **Finish** (normal / foil / etched / reverse holo / 1st edition —
   game-dependent enum)
5. **Condition** (NM / LP / MP / HP / DMG)
6. **Quantity** (Int)
7. **Acquisition currency + cost** (optional, for cost-basis tracking)

ledgerboy resolves (game, set, collector number) against the source
to obtain a stable upstream ID, caches it on the `AssetEntity`, and
uses that ID for all subsequent revaluation fetches. Lookup is
**deterministic** — same input always resolves to the same card.

Display price = `upstreamPrice(finish) × conditionMultiplier × quantity`.

## Revaluation cadence

- **Automatic refresh:** once per day, on app open, if the cached
  `latestValuationEvent.date` is older than the source's TTL.
- **TTLs:** MTG (Scryfall) = 1 day; Pokémon = 1 day; Yu-Gi-Oh! = 1 day.
  Card prices move on day-scale, not hour-scale; daily is plenty for
  net-worth tracking. (Crypto's hourly cadence does not transfer here.)
- **Manual refresh:** "refresh prices" button on the Assets screen
  forces a fetch regardless of TTL.
- **Bulk refresh:** the per-source rate limit governs how fast we can
  refresh a large collection. A 1000-card MTG collection at Scryfall's
  10 req/sec lightweight tier is ~100 s with the 100 ms spacing
  Scryfall asks for as a courtesy. A bulk refresh runs in a
  WorkManager job, not on the UI thread.
- **Bulk-data shortcut (Scryfall only):** Scryfall publishes a
  daily-refreshed bulk JSON of all cards. For collections > 100 cards,
  prefer fetching the bulk dump once and indexing locally over
  per-card fetches. Documented; Phase J.4 sub-step.

## Fit recommendation

Every source below is a `ConnectorPlugin` in category `AssetPrice`,
**disabled by default**. Users enable per source via Settings → Plugins,
then Configure (where Configure is a no-op or a single API-key field),
then Test connection. Only after Test passes does the plugin transition
to `Active` and start fetching.

- **MTG via Scryfall (plugin `scryfall`):** v1 plugin, ships first.
  Disabled by default. No API key. Configure flow is a no-op (just a
  privacy-statement screen + a Test connection button that resolves a
  known-stable card id). Off until the user enables it.
- **Pokémon via pokemontcg.io (plugin `pokemontcg-io`):** v1.x plugin,
  after Scryfall lands. Disabled by default. Configure flow asks the
  user for their free dev.pokemontcg.io API key.
- **Yu-Gi-Oh! via YGOPRODeck (plugin `ygoprodeck`):** v1.x plugin.
  Disabled by default. No API key. Configure flow is a no-op +
  privacy-statement screen + Test connection.
- **Sports cards (COMC / PSA / etc.):** **manual-only** (ToS-disqualified).
  Not a plugin candidate.
- **Cardmarket / TCGplayer direct:** **deferred pending paid tier
  access**, not v1. Would be plugins off by default if ever reconsidered.
- **Coins / comics / watches / sneakers:** **manual-only**, no free
  feed exists. Not plugin candidates today.

## Cross-cutting

### `CollectibleSource` interface (plugin-internal)

Each collectible source is one `ConnectorPlugin` per the lock in
[`connector-plugins.md`](connector-plugins.md). The plugin internally
exposes the narrow card-data shape below; the host only sees the
`ConnectorPlugin` surface (state machine, ConfigScreen, fetch). The
internal interface lives inside each plugin's package and is not
public API.

```kotlin
// Internal to each card-data plugin.
interface CollectibleSource {
    val id: String                // "scryfall", "pokemontcg-io", "ygoprodeck"
    val attribution: AttributionLine
    val rateLimit: RateLimitPolicy
    val ttl: Duration             // 1.days for all three v1 plugins

    suspend fun resolve(query: CardQuery): Result<UpstreamCardId>
    suspend fun fetchPrice(id: UpstreamCardId, finish: Finish): Result<Money>
    suspend fun fetchBulkPrices(ids: List<UpstreamCardId>): Result<Map<UpstreamCardId, Money>>
}
```

Each plugin's implementation lives in its own module under
`com.eight87.ledgerboy.plugins.<plugin-id>/`. Selection at fetch time
is sealed-class + when over the holding's `game`, per the SOLID
Open/Closed bullet in `CLAUDE.md`. Adding a fourth card game = a new
plugin module + a new manifest entry; the host code does not change.

### Caching

All resolved card metadata and every `fetchPrice` result lands in Room
on the way out. Eviction policy: **never** — `ValuationEvent` rows are
append-only and form the per-asset history. Card-metadata cache may be
re-fetched once per release-version (in case set names or finishes get
corrected upstream); that is a "freshness sweep", not eviction.

### Rate-limit discipline

A shared `RateLimitedClient` per source enforces request spacing via a
token bucket. Defaults:

| Source        | Spacing      | Burst | Notes                                     |
| ------------- | ------------ | ----- | ----------------------------------------- |
| Scryfall      | 100 ms       | 10    | Per Scryfall's courtesy ask + 10 req/sec  |
| pokemontcg.io | 50 ms        | 20    | Inside 20,000/day; spacing matters less   |
| YGOPRODeck    | 60 ms        | 16    | Below their 20 req/sec hard limit         |

Bulk-refresh jobs (a watch-list with 1000 holdings) honor the spacing
even though `WorkManager` could fire requests faster. The token bucket
sits *inside* `OkHttpClient` as an `Interceptor`, so every call route
through the source's client is throttled — including any future
non-price metadata fetches.

### Attribution surface

Per Scryfall / pokemontcg.io / YGOPRODeck ToS, attribution is visible.
Per the plugin architecture's transparency requirement, every plugin
already surfaces its `attribution` + `privacyStatement` in
**Settings → Plugins**. The collectibles plugins reuse that channel
rather than maintaining a parallel "Data sources" screen:

1. **Settings → Plugins → Asset prices** (the plugin-categorised
   accordion from [`connector-plugins.md`](connector-plugins.md)) lists
   every plugin (disabled or active) with state chip, license SPDX, a
   one-line credit, and a link to its upstream site. This subsumes the
   previously-planned "Settings → Data sources" screen.
2. **Asset-detail screen** shows a small "via Scryfall" / "via
   pokemontcg.io" / "via YGOPRODeck" caption next to the latest price
   line — drawn from the active plugin's `attribution` line.
3. **About → Open-source licenses** (the existing
   [`oss-licenses.md`](oss-licenses.md) screen) is for redistributed
   libraries, not for remote APIs. Per-plugin attribution lives in
   Settings → Plugins.

All three attribution lines live in `strings.xml` under the
`plugin_<id>_attribution` naming convention (per the i18n discipline
rule in CLAUDE.md), matching the per-plugin string layout used by the
banking and FX plugins.

### Money shape

Upstream prices arrive as JSON strings (`"4.99"`, `"3.50"`); the
connector parses to `Money(long, currency)` at the boundary — never
`Double`, never `BigDecimal` in the storage layer. Currency follows
the source: Scryfall `usd` → `Money(_, USD)`, `eur` → `Money(_, EUR)`;
pokemontcg.io TCGplayer → USD, Cardmarket → EUR; YGOPRODeck per
sub-field. Cross-currency display goes through the FX cache.

## Decision

- **v1 plugin (first to ship):** `scryfall` for MTG. Lowest friction
  (no API key), permissive ToS, the user's explicit example.
  **Disabled by default**; user enables in Settings → Plugins → Asset
  prices.
- **v1.x plugins:** `pokemontcg-io` for Pokémon TCG (user-provided
  API key in Configure flow) and `ygoprodeck` for Yu-Gi-Oh!
  (no API key). Both disabled by default.
- **Manual-only:** sports cards, coins, comics, watches, sneakers.
  Not plugin candidates.
- **Deferred:** Cardmarket direct, TCGplayer direct (paid).

### `ValuationEvent` write path

```
user opens Assets screen
  → ViewModel reads holdings
  → for each holding whose latestValuationEvent.date < now() - source.ttl:
      → RateLimitedClient.fetchPrice(upstreamId, finish)
      → insert ValuationEvent(date = now(),
                              value = upstreamPrice * conditionMultiplier,
                              source = "scryfall" | "pokemontcg" | "ygoprodeck")
  → display latestValuationEvent.value per holding
```

`fetchPrice` failures (network down, 429, 5xx) **do not** insert a
`ValuationEvent` row — they leave the most recent row in place and
surface a non-blocking "stale price" badge on the holding. Per the
"calm, factual" editorial rule in CLAUDE.md, the badge reads
"Last updated 3 days ago" and not "Price refresh failed!".

### Phase J implementation sub-steps

These all build on the **Phase X host runtime** from
[`connector-plugins.md`](connector-plugins.md). Phase X lands the
`ConnectorPlugin` interface, the manifest, the `PluginNetworkGuard`,
the `PluginScheduler`, the Settings → Plugins screen. Each sub-step
below is then a single-source plugin implementation on top.

- [ ] **J.1** Define the internal `CollectibleSource` interface, the
  `RateLimitedClient`, the `AttributionLine` data class, the
  `RateLimitPolicy` data class — all inside the plugins package, not
  in the host. No plugin impls yet. JVM unit tests for the token-bucket
  spacing logic (Robolectric not required).
- [ ] **J.2** Implement the `scryfall` plugin on top of Phase X.
  Plugin id `scryfall`, category `AssetPrice`, license `MIT`
  (Scryfall API is free for personal use, no library license to inherit
  beyond our own client code). Implements `ConnectorPlugin`:
  `ConfigScreen()` is a no-op screen showing the privacy statement +
  a Test connection button; `fetch()` wraps an internal
  `ScryfallSource` against `https://api.scryfall.com/` with the
  required `User-Agent` + `Accept` headers, the 100 ms spacing token
  bucket, and the `/cards/{set}/{collector_number}` resolution path.
  Unit-test the JSON parsing against a fixture
  (`app/src/test/resources/fixtures/`, fictional card IDs only).
  **Disabled by default**; registers in `PluginManifest.all`.
- [ ] **J.3** Wire the `scryfall` plugin into the `AssetEntity` model:
  new asset class enum value `COLLECTIBLE_MTG`, new fields for
  `set_code`, `collector_number`, `finish`, `condition_grade`. Room
  migration if schema already shipped; otherwise additive change. The
  "Add trading card" form refuses to save an MTG card if the `scryfall`
  plugin is not `Active` (with a one-tap "Enable Scryfall plugin" link
  to Settings → Plugins).
- [ ] **J.4** Bulk-data fetch path inside the `scryfall` plugin: a
  `WorkManager` job (scheduled via `PluginScheduler`) downloads
  Scryfall's daily bulk JSON, indexes it into a Room table keyed by
  `(set, collector_number)`, and the internal `fetchBulkPrices` uses
  the index. Documented for collections of > 100 cards.
- [ ] **J.5** UI: Assets screen adds an "Add trading card" entry; the
  add-card form asks for game + set + collector number + finish +
  condition + quantity. On save, if the relevant plugin is `Active`,
  the plugin resolves the upstream ID, fetches the current price, and
  the host writes the first `ValuationEvent`. If the plugin is not
  `Active`, the form saves the holding with no price and surfaces an
  enable-the-plugin hint.
- [ ] **J.6** (Superseded.) Settings → Data sources is replaced by
  Settings → Plugins → Asset prices from Phase X.6. No separate
  screen lands; per-plugin attribution rides on the plugin card.
- [ ] **J.7** Implement the `pokemontcg-io` plugin on top of Phase X.
  Plugin id `pokemontcg-io`, category `AssetPrice`, license `MIT`.
  `ConfigScreen()` asks the user for their free dev.pokemontcg.io API
  key (encrypted at rest via the host's Keystore-wrapping helper, per
  [`security-research.md`](security-research.md)). Test connection
  performs one card lookup. **Disabled by default.**
- [ ] **J.8** Implement the `ygoprodeck` plugin on top of Phase X.
  Plugin id `ygoprodeck`, category `AssetPrice`, license `MIT`. No
  API key. Enforce the local-image-cache rule (download card images
  to private storage, never hotlink) inside the plugin. Test
  connection performs one card lookup. **Disabled by default.**
- [ ] **J.9** Condition multiplier table in Settings → app-level
  (not per-plugin, because it applies to all card games uniformly).
  Defaults documented above; user can edit per game.
- [ ] **J.10** Manual-collectible flow (coins / comics / watches /
  sneakers / sports cards): user names the asset, picks a currency,
  enters a value, sets a "remind me to revalue every N months"
  cadence. Shares the `ValuationEvent` schema; just no plugin
  involvement. Lands alongside the generic asset class in the parallel
  agent's manual-cluster Phase J work.
