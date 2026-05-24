# ledgerboy — asset valuation research

## Status: SEED — research-round agents pick up from here

Seed prompt for the next round of research agents on **asset valuation connectors**. Each agent produces a per-class decision file (`docs/plans/asset-<class>.md`). Phase J of [`main.md`](main.md) expands sub-steps after the first asset-class connector is decided.

## Constraints (same as banking-research.md, plus)

1. **Local-first.** Valuation lookups happen direct device → upstream API.
2. **License gate.** MIT / Apache 2.0 / BSD / MPL 2.0.
3. **ToS posture.** Scraping is grey-area. Per-class research must explicitly evaluate whether a source's ToS permits client-side automated lookup for personal use, and whether attribution is required. If a source's ToS forbids scraping, the connector is **manual-only** (the user enters the valuation; we don't auto-fetch).
4. **Money is integer minor units in the asset's denominated currency.** A trading card in EUR, a stock in USD, a house in GBP — each tracked in its native currency, displayed via the FX cache.
5. **Asset valuations are historical.** Every revaluation lands as a `ValuationEvent(date, value, source)` row. The current value is just `latestValuationEvent.value`. This shape supports both manual revaluations and automated ones; do not collapse them.

## Per-class questions to answer

For each asset class, the research deliverable answers:

- **What is it?** One-paragraph plain-language summary.
- **Source candidates.** For each: SPDX licence (if it's a library) or ToS posture (if it's a remote API). Coverage (which assets does it actually price?). Rate limits. Free tier vs. paid.
- **Lookup shape.** What does the user have to provide? (Card name + condition? Ticker + exchange? Postal address?) Is it deterministic (same input → same output) or approximate?
- **Revaluation cadence.** Is auto-revaluation appropriate (daily for securities), or does it only make sense manually (once-a-quarter for real estate)?
- **Fit-for-ledgerboy.** Recommendation: **automated connector** / **manual-only with optional lookup helper** / **manual-only**.

## Asset classes to research

### `asset-realestate.md` — real estate (house / apartment)

Almost certainly **manual + periodic**. The user enters a house value once, revalues quarterly or annually (or whenever a comparable sells in their building).

Concrete questions:

- Is there any reasonable automated lookup? In Germany, Immobilienscout24 / Immowelt / Wertermittler — all **scraping-grey** and ToS-hostile.
- In the UK: Zoopla / Rightmove — same.
- In the US: Zillow's "Zestimate" API exists but requires a paid commercial agreement.
- **Recommendation default: manual-only.** Document this loudly in the decision file. Optional: a "remind me to revalue every N months" notification.

### `asset-collectibles.md` — collectibles (trading cards is the user's example)

Most interesting class. Per-game sources:

- **MTG (Magic: The Gathering)** — **Scryfall** (free, MIT-friendly API, ToS allows automated lookup with attribution). Likely the cleanest connector to ship first.
- **Pokémon** — **pokemontcg.io** (free API, attribution required). Cardmarket also has a paid API.
- **Sports cards** — **comc** / **PSA** — paid APIs, scraping ToS-hostile.
- **Yu-Gi-Oh!** — **YGOPRODeck** (free API, attribution required).
- **Cardmarket** (EU cards-broad) — paid API tier.
- **TCGplayer** (US cards-broad) — paid API tier.

Concrete questions:

- For Scryfall + pokemontcg.io + YGOPRODeck (the free tier): rate limits, attribution requirements, price-history availability (current price only? 7-day average? all-time?), card-identification flow (set + collector number + condition).
- For other collectibles (coins, comics, watches, sneakers): are there equivalent free APIs? Likely not — manual-only.
- Condition grading: the user enters condition (NM / LP / MP / HP / DMG for cards); the price feed returns prices per condition; ledgerboy displays the appropriate one.
- **Recommendation default: Scryfall + pokemontcg.io + YGOPRODeck shipped as the automated connectors; everything else manual.**

### `asset-securities.md` — stocks / ETFs / funds

Mature space, paid is the norm but free tiers exist:

- **Yahoo Finance** — unofficial API, ToS-hostile, occasionally broken by upstream changes. Don't rely on.
- **Alpha Vantage** — free tier (5 calls / minute, 500 / day), generous enough for personal use. API key required.
- **IEX Cloud** — paid since 2023.
- **Polygon.io** — paid.
- **finnhub.io** — free tier (60 calls / minute).
- **Frankfurter** (FX-only, ECB-backed, free) — listed because it's FX, not securities, but the same shape.
- **ECB equity data** — sparse, EU-only.

Concrete questions:

- Coverage: does the chosen source cover the exchanges the user cares about (Xetra / LSE / NYSE / NASDAQ at minimum)?
- Symbol resolution: same ticker means different things on different exchanges (`SAP` is SAP SE on Xetra, SAP Inc. on NYSE). Per-position the user specifies ticker + exchange.
- Real-time vs. end-of-day: end-of-day is fine for net-worth tracking; real-time isn't needed.
- Mutual funds vs. ETFs: mutual funds usually price once daily (NAV); ETFs are intraday.
- Crypto-tracked-as-security vs. crypto-tracked-as-asset-class (see next section).

### `asset-crypto.md` — cryptocurrencies

Either treat as a separate asset class with its own price feed, or treat each holding as a "security" with the crypto exchange as the "exchange." Pick one shape.

Sources:

- **CoinGecko** — free API, generous limits, broad coverage.
- **CoinMarketCap** — free tier, broad coverage.
- **Per-exchange API** — only if the user wants live balances from an exchange account (Kraken, Coinbase, Binance, etc.). That's a Phase J+ stretch.

Concrete questions:

- Just price tracking (user enters holdings, app values them) or also balance fetching from exchange accounts?
- v1 default: price tracking only.

### `asset-vehicles.md` — vehicles

Almost certainly **manual + periodic**, like real estate. Possibly a Kelley Blue Book / Schwacke / Autoscout lookup helper at the manual revaluation moment, but ToS-grey.

**Recommendation default: manual-only.**

### `asset-generic.md` — other asset with manual valuation history

Catch-all. The user names the asset, picks a currency, enters a current value, and revalues whenever they want. No automated source. Useful for: jewelry, art, business stakes, "I-own-half-of-this-thing-with-my-sibling."

## What "done" looks like for this research round

- Six (or so) decision files: `asset-realestate.md`, `asset-collectibles.md`, `asset-securities.md`, `asset-crypto.md`, `asset-vehicles.md`, `asset-generic.md`.
- Each file ends with a Decision section.
- This file gets a "Decisions" section appended pointing at the six.
- [`main.md`](main.md) Phases I / J get sub-steps based on the decisions.
- The first connector to land in Phase J is **Scryfall** (MTG cards) — lowest friction, free, MIT-friendly, the user's explicit example. The second is probably **Alpha Vantage** for securities.
