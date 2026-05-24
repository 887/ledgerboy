# ledgerboy — asset valuation research

## Status: Research round 1 complete; pivot to plugin architecture applied 2026-05-24. See [`connector-plugins.md`](connector-plugins.md).

This file was the seed prompt for the first round of asset-valuation research. Round 1 landed six per-class decision files (see Decisions below); on 2026-05-24 the user issued a course-correction locked in [`connector-plugins.md`](connector-plugins.md): **every connector is a plugin, disabled by default, no paid-SaaS pricing services, direct-to-source-of-truth only.** The per-class decision files have been rewritten to reflect that posture; this seed has been reframed to match. Phase J of [`main.md`](main.md) expands sub-steps as each per-class plugin is implemented on top of the Phase X runtime.

## Constraints (same as banking-research.md, plus)

1. **Per-class connector plugins, off by default.** Each `asset-<class>` decision lands as a `ConnectorPlugin` in the `AssetPrice` or `RegionalIndex` category. No paid-SaaS pricing services (Alpha Vantage, finnhub.io, CoinGecko, CoinMarketCap). Direct-to-source-of-truth only: per-broker plugins for securities, per-exchange + on-chain RPC plugins for crypto, community-open-data plugins (Scryfall / pokemontcg.io / YGOPRODeck) for collectibles, regional-index helpers (Häuserpreisindex / BORIS / UK HPI / FHFA HPI) for real estate. Architecture locked in [`connector-plugins.md`](connector-plugins.md).
2. **Local-first.** Valuation lookups happen direct device → broker / exchange / open-data endpoint. Nothing transits a server we operate; nothing transits a paid SaaS we don't operate either.
3. **License gate.** MIT / Apache 2.0 / BSD / MPL 2.0 — applies to plugin deps the same as to core deps (single APK, see `connector-plugins.md`).
4. **ToS posture.** Scraping is grey-area. Per-class research must explicitly evaluate whether a source's ToS permits client-side automated lookup for personal use, and whether attribution is required. If a source's ToS forbids scraping, the plugin is **manual-only** (the user enters the valuation; we don't auto-fetch).
5. **Money is integer minor units in the asset's denominated currency.** A trading card in EUR, a stock in USD, a house in GBP — each tracked in its native currency, displayed via the FX cache.
6. **Asset valuations are historical.** Every revaluation lands as a `ValuationEvent(date, value, source)` row. The current value is just `latestValuationEvent.value`. This shape supports both manual revaluations and automated ones; do not collapse them.

## Per-class questions to answer

For each asset class, the decision file answers:

- **Is this a candidate for a plugin, and if so what's the protocol / source?** Direct-to-broker (per-broker plugin), direct-to-exchange (per-exchange plugin), direct on-chain RPC, direct community-open-data, direct regional-index open-data endpoint, or manual-only?
- **What is it?** One-paragraph plain-language summary.
- **Plugin shape.** `ConnectorPlugin` id, category (`AssetPrice` or `RegionalIndex`), what `ConfigScreen()` collects (per-broker credentials? exchange API key with read-only scope? RPC node URL? nothing — open-data?), what `fetch()` emits, what the privacy statement says about data leaving the device.
- **Source posture.** Direct-to-source only. SPDX licence (if a library is involved) or ToS posture (if it's a remote API or a community open-data endpoint). Coverage. Rate limits. Attribution requirements. **No paid-SaaS aggregators or pricing services.**
- **Lookup shape.** What does the user have to provide? (Card name + condition? Ticker + exchange + broker account? Postal address?) Is it deterministic (same input → same output) or approximate?
- **Revaluation cadence.** Is auto-revaluation appropriate (daily for securities via the user's broker), or does it only make sense manually (once-a-quarter for real estate)? Background-sync is per-plugin opt-in.
- **Fit-for-ledgerboy.** Recommendation: **plugin (automated)** / **plugin (manual-only with lookup helper)** / **manual-only, no plugin**.

## Per-class reading order (post-pivot)

1. [`connector-plugins.md`](connector-plugins.md) — the architecture lock.
2. [`asset-collectibles.md`](asset-collectibles.md) — Scryfall / pokemontcg.io / YGOPRODeck plugins (free, direct, open-data with attribution); each off by default. Likely first plugin to land in Phase J.
3. [`asset-securities.md`](asset-securities.md) — per-broker plugins (IBKR TWS/Gateway, Trade Republic Mobile API, Comdirect, etc.); user supplies their own broker credentials. Manual + broker-statement-CSV-import as the universal fallback.
4. [`asset-crypto.md`](asset-crypto.md) — per-exchange plugins (Kraken REST, Coinbase REST, Binance REST — read-only API keys) + on-chain RPC plugins (user-supplied node URL); manual + CSV import as the fallback.
5. [`asset-realestate.md`](asset-realestate.md) — manual + periodic; optional regional-index plugins (Häuserpreisindex / BORIS / UK HPI / FHFA HPI) as revaluation hints, each off by default.
6. [`asset-vehicles.md`](asset-vehicles.md) — manual + periodic.
7. [`asset-generic.md`](asset-generic.md) — manual catch-all.

## What "done" looks like for this research round

Round 1 has landed. The six per-class decision files exist and have been rewritten to reflect the plugin architecture (see Decisions below). Subsequent rounds add per-broker / per-exchange / per-regional-index plugin decisions one at a time (each as its own `asset-<class>-<source>.md` decision file as that source is researched).

## Decisions

Post-pivot per-class files:

- [`asset-collectibles.md`](asset-collectibles.md) — Scryfall / pokemontcg.io / YGOPRODeck shipped as plugins, all off by default; everything else manual.
- [`asset-securities.md`](asset-securities.md) — per-broker plugins (IBKR / Trade Republic / Comdirect) + manual + CSV import; Alpha Vantage / finnhub.io rejected.
- [`asset-crypto.md`](asset-crypto.md) — per-exchange plugins (Kraken / Coinbase / Binance, read-only API keys) + on-chain RPC plugins (user-supplied node URL) + manual + CSV import; CoinGecko / CoinMarketCap rejected.
- [`asset-realestate.md`](asset-realestate.md) — manual + periodic; optional regional-index plugins (Häuserpreisindex / BORIS / UK HPI / FHFA HPI) off by default.
- [`asset-vehicles.md`](asset-vehicles.md) — manual + periodic; no plugin.
- [`asset-generic.md`](asset-generic.md) — manual catch-all; no plugin.

[`main.md`](main.md) Phase X (plugin runtime) lands before Phase J; Phase J implements per-class plugins one at a time on top of the runtime. Likely first to land: Scryfall (MTG) — lowest friction, free, direct community-open-data, the user's explicit example.
