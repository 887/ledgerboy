# ledgerboy — asset class: real estate

## Status: DECISION — manual-only, with optional revaluation reminders and (optional, post-v1) regional-index hints from public open data

Per-class research deliverable for the **real estate** asset class (house / apartment / land). Part of the manual-valuation cluster (see [`asset-vehicles.md`](asset-vehicles.md), [`asset-generic.md`](asset-generic.md)). Cluster-mate to the automated-feed cluster in [`asset-collectibles.md`](asset-collectibles.md) / [`asset-securities.md`](asset-securities.md) / [`asset-crypto.md`](asset-crypto.md).

## What it is

A residential or commercial property the user owns (in whole or in part). Typical user shape: one or two properties, valued in the local currency of the property (a Berlin flat in EUR, a London terrace in GBP, a Brooklyn co-op in USD), revalued once a quarter or once a year, or whenever a comparable unit nearby actually sells and the user updates the "what's it worth" anchor.

Distinguishing properties:

- **Slow-moving.** Daily price feed is meaningless; market only "prices" on actual transactions, which for any one property happen years apart.
- **Per-property uniqueness.** Even within the same building, two units differ in floor, exposure, renovation state. Automated AVMs (Automated Valuation Models, the "Zestimate" shape) try to interpolate, with known accuracy issues on the order of 5–20% off true market value at sale.
- **High sensitivity PII.** A property's address is one of the most identifying pieces of metadata a user could store; treat per-property metadata with the same posture as bank account numbers.

## Source candidates

All evaluated lookup helpers fail at least one of the ledgerboy gates (license / ToS / cost / coverage). Documented here so future agents don't re-grep this ground.

### Germany

| Source | Type | ToS / cost | Verdict |
| --- | --- | --- | --- |
| **Immobilienscout24** | Listing scraping target | ToS forbids automated access; rate-limit + Cloudflare gate; valuation feature ("ImmoScout24 Marktwert") behind login + premium tier. See [imscout24-Nutzungsbedingungen §3.3](https://www.immobilienscout24.de/anbieten/agb.html) ("automatisierte Verfahren … sind nicht gestattet"). | **Out.** Scraping violates ToS; no personal-use API. |
| **Immowelt** | Listing scraping target | Same shape. ToS [§4.1](https://www.immowelt.de/agb) forbids automated harvesting. No public valuation API. | **Out.** |
| **Wertermittler / Sprengnetter / VALUE AG** | Commercial AVM providers | B2B only. Pricing on request, expect four-figure EUR minimums. | **Out.** Not a personal-use shape. |
| **Bodenrichtwerte (BORIS)** | **Public open data** — per-Bundesland land-value indexes published by each state's Gutachterausschuss. Federated portal at [BORIS-D](https://www.bodenrichtwerte-boris.de/). | Open data, mostly free, license varies per Bundesland (most are DL-DE / CC-BY). | **Maybe (post-v1).** Per-square-meter land-value reference, *not* per-property valuation. Useful as a "regional index trend since your last revaluation" hint, never as a current value. License audit per-Bundesland required before shipping. |
| **Statistisches Bundesamt — Häuserpreisindex** | Quarterly nationwide / metro-area index | Open data, free. [destatis.de/Häuserpreisindex](https://www.destatis.de/DE/Themen/Wirtschaft/Preise/Baupreise-Immobilienpreisindex/_inhalt.html) | **Maybe (post-v1).** Index value, not absolute. Useful for "your house may have moved X% since you last revalued" prompts. |

### United Kingdom

| Source | Type | ToS / cost | Verdict |
| --- | --- | --- | --- |
| **Zoopla** | Listing + Zed-Index AVM | ToS forbids scraping ([Zoopla terms §3](https://www.zoopla.co.uk/about/legal/terms/)). Public API discontinued for personal use; commercial-partner-only since 2020. | **Out.** |
| **Rightmove** | Listing site | ToS forbids scraping ([rightmove.co.uk/terms](https://www.rightmove.co.uk/this-site/terms-of-use.html)). No personal-use API. | **Out.** |
| **HM Land Registry — Price Paid Data** | **Public open data** — every residential sale in England & Wales since 1995, postcode-level. [gov.uk/government/statistical-data-sets/price-paid-data-downloads](https://www.gov.uk/government/statistical-data-sets/price-paid-data-downloads) | Open Government Licence v3.0 (compatible). CSV monthly updates. | **Maybe (post-v1).** Per-postcode comparable-sales lookup; we could surface "five recent sales in your postcode, here's the median £/sqft" as a revaluation aid. Sales-only, not for-sale-asking. Not a valuation — a reference. |
| **UK House Price Index (ONS / Land Registry)** | Regional / national index | Open Government Licence. [gov.uk/government/collections/uk-house-price-index-reports](https://www.gov.uk/government/collections/uk-house-price-index-reports) | **Maybe (post-v1).** Same shape as the German Häuserpreisindex. |

### United States

| Source | Type | ToS / cost | Verdict |
| --- | --- | --- | --- |
| **Zillow Zestimate API ("Bridge Interactive")** | AVM | Commercial-only as of 2021; requires MLS membership or paid partner agreement; [bridgedataoutput.com pricing](https://bridgedataoutput.com/pricing). | **Out.** Not personal-use. |
| **Redfin Estimate** | AVM | No public API. Scraping ToS-forbidden ([redfin.com/about/terms-of-use](https://www.redfin.com/about/terms-of-use)). | **Out.** |
| **Realtor.com / Trulia** | Listings | No public valuation API; ToS-hostile to scraping. | **Out.** |
| **Federal Housing Finance Agency — House Price Index** | Public open data, metro-area | Public domain. [fhfa.gov/data/hpi](https://www.fhfa.gov/data/hpi). | **Maybe (post-v1).** Index-only. |
| **County assessor data** | Public records per county | Varies wildly per county; no national API; format inconsistent. | **Out for v1.** Could be a per-county niche later, not Phase J. |

### Cross-border / global

| Source | Type | Verdict |
| --- | --- | --- |
| **OpenStreetMap** | Geo data, not valuation | **N/A.** Useful for address normalization, but irrelevant to value. |
| **Eurostat House Price Index** | EU-wide index | **Maybe (post-v1).** Indicator only. |

### Summary

There is no per-property automated-valuation source that:

1. Has a public free API,
2. Permits client-side personal use under its ToS, and
3. Returns a price for a property without an MLS / partner agreement.

Every paid commercial source is priced for brokerages, not for individuals tracking one or two homes. The **personal-finance shape and the AVM-vendor shape do not overlap.**

The only sources that pass the ledgerboy gates are **public open-data regional indexes** (Häuserpreisindex / UK HPI / FHFA HPI) and **public sold-price registries** (UK Land Registry Price Paid, German BORIS) — and those are *reference* data, not per-property valuations. Useful as a hint ("prices in your region moved +X% since your last revaluation, consider re-anchoring"), never as the value itself.

## Lookup shape

There is no lookup. The user opens the asset and types a number.

Optional post-v1 reference rendering, if the regional-index sources land:

- Pick the relevant index (Häuserpreisindex / UK HPI / FHFA HPI) from a per-country mapping based on the property's currency or a user-set region tag.
- Snapshot the index value at the time of each `ValuationEvent`.
- On the asset detail screen, show "Regional index since your last revaluation: +3.4%."
- **Never** auto-update the asset's value from the index; this is a prompt to the user to consider revaluing, not a substitute for revaluation.

## Revaluation cadence

User-configurable per-asset, with sensible defaults:

- **Quarterly** (default for active markets / recent purchases / properties the user actively follows).
- **Annually** (default for long-held primary residences where the user only thinks about it once a year).
- **On demand only** (no reminder; the user revalues when they feel like it).
- **On comparable sale** (manual trigger; the user enters a comparable-sale event as a "consider revaluing" prompt — see UX features).

The default for a freshly-added asset is **annually**. Quarterly is offered as a one-tap alternative on the add-asset wizard's final step.

## Fit recommendation

**Manual-only.** No automated valuation connector. Optional post-v1: a regional-index *hint* surfaced from public open data, never as a value substitute.

## UX features

### Add-asset wizard (real-estate flow)

1. **Class pick:** Real estate.
2. **Name:** free text (e.g. "Berlin flat", "Mum's house in Cornwall"). Private, never sent anywhere, never in screenshots.
3. **Address (optional, encrypted at rest, never transmitted):** free-text, single field; we do not parse, geocode, or normalize. Stored in the Room DB encrypted under the SQLCipher-wrapped key (see `security-research.md`). The user is told in copy that the address never leaves the device.
4. **Currency:** ISO-4217 picker, defaults to the property's country's currency if the user typed a country in the address; otherwise defaults to the user's base currency from DataStore prefs.
5. **Initial valuation:** integer minor units. UI presents a localized currency input (see ledgerboy's `Money` value class).
6. **Acquisition date** (optional): date picker. Used for cost-basis display and gain/loss in the detail screen.
7. **Acquisition cost** (optional): integer minor units, same currency. Used for cost-basis display.
8. **Revaluation cadence:** annually (default) / quarterly / on demand. Backed by a WorkManager periodic job.
9. **Save:** writes the asset row and the initial `ValuationEvent(date=today, value=initial, source="manual")`.

### Revalue flow

1. From the asset detail screen, tap **Revalue now**.
2. Enter the new value (integer minor units, asset's currency).
3. Optional note (free text, private, never transmitted).
4. **Save:** writes a new `ValuationEvent(date=today, value=new, source="manual", note=…)`. The asset's "current value" is `latestValuationEvent.value`.

### Comparable-sales flow (manual)

The user can record what neighbours sold for, separately from their own revaluations:

1. From the asset detail screen, tap **Add comparable sale**.
2. Date, sale price (integer minor units, asset's currency), optional note (e.g. "ground floor, same floorplan, needs renovation").
3. Comparable sales are stored against the asset but do **not** update its value. They render in a list on the detail screen as decision-support context.
4. Comparable sales never trigger automated revaluations; they only render as a list and, optionally (post-v1), as scatter points on the value-over-time chart.

### Revaluation reminder

- WorkManager periodic job per asset, cadence from the asset row.
- On fire: foreground notification "Revalue Berlin flat?" → tap → opens the revalue flow with the most-recent value pre-filled.
- User can snooze (1 week / 1 month) or skip (next reminder at next cadence boundary).

### Asset history view

- Line chart of value-over-time (uses the charting library from `dataviz-decision.md`).
- Tabular list of all `ValuationEvent` rows, newest first, with date / value / source / note.
- Optional overlay: comparable-sale dots (different color), and (post-v1) regional-index percentage line normalised to the initial valuation.
- Net-worth view aggregates the latest valuation across all assets, FX-converted to the user's base currency via the FX cache.

## Privacy posture

Real-estate metadata is the most sensitive non-credential data ledgerboy stores. **Per `CLAUDE.md` "money is private" rule, extended:**

- **Addresses are never transmitted off-device.** No geocoding, no map preview that fetches tiles for the address, no "share asset" feature that includes the address. The address field is encrypted at rest under the SQLCipher key.
- **Addresses are never in screenshots used in the README / store listing / plan docs / PRs.** Sample-data property names are obviously fictional: "Acme Sample House", "Test Apartment", "Demo Property #1".
- **Addresses are never in logs.** `Logcat` calls that touch the asset row redact the address field; the redaction is enforced by a `Logcat` extension that knows about the per-field privacy tier (similar shape to the banking connector's credential redaction).
- **Backups** (when Phase L exports land): the address field is **opt-in** per export, defaulting to off. A backup with addresses included carries a banner warning in the export UI.
- **PII tier label:** `tier=address`. The encrypted-DB research (`security-research.md`) covers the key-wrapping path; ensure the asset address field is included in the per-field encryption audit.

## Decision

**Manual-only.** No automated valuation source clears the license / ToS / cost / coverage gates simultaneously. Optional post-v1 enhancement: regional-index hints from public open data (German Häuserpreisindex, UK HPI, US FHFA HPI), surfaced as decision-support context, never as a value substitute.

### Phase J implementation sub-steps (real estate)

These ride alongside the vehicle / generic sub-steps in [`asset-vehicles.md`](asset-vehicles.md) / [`asset-generic.md`](asset-generic.md). Shared scaffolding lands once, per-class flows reuse it.

- [ ] **J.RE.1** `AssetEntity` Room row supports `class=real_estate` with the per-class metadata fields: `name`, `address_encrypted`, `currency`, `acquisition_date_nullable`, `acquisition_cost_minor_nullable`, `revaluation_cadence` (enum: `annually` / `quarterly` / `on_demand`), `created_at`, `updated_at`. Address field is encrypted at rest under the SQLCipher key.
- [ ] **J.RE.2** `ValuationEventEntity` Room row: `asset_id`, `date`, `value_minor`, `currency`, `source` (enum: `manual` / `manual_comparable` / `connector_<name>`), `note_nullable`. Foreign key + index on `asset_id`.
- [ ] **J.RE.3** `ComparableSaleEntity` Room row: `asset_id`, `date`, `price_minor`, `currency`, `note_nullable`. Separate table so comparable sales never accidentally flow into the net-worth aggregate.
- [ ] **J.RE.4** `AddAssetWizard` composable: class-pick → name → address → currency → initial value → acquisition (optional) → cadence → save. Uses the locked UI-shell patterns (vertical-rail navigation, top-bar actions). Strings in `values/strings.xml` under `asset_realestate_*` namespace.
- [ ] **J.RE.5** `AssetDetailScreen` composable: header (name, current value, latest revaluation date), value-over-time chart (deferred to `dataviz-decision.md`), revaluation history list, comparable-sales list, action buttons (Revalue / Add comparable / Edit).
- [ ] **J.RE.6** `RevalueAssetFlow` composable + ViewModel: pre-fills latest value, takes new value + optional note, writes `ValuationEvent`. Cancellation is safe (no partial write).
- [ ] **J.RE.7** `AddComparableSaleFlow` composable + ViewModel: date + price + optional note, writes `ComparableSaleEntity`. Separate flow from revaluation; does not touch `ValuationEvent`.
- [ ] **J.RE.8** `RevaluationReminderWorker` (WorkManager): periodic per-asset job, scheduled / rescheduled when the cadence field changes. Notification taps deep-link to `RevalueAssetFlow`. Snooze (1w / 1m) and skip actions handled in the notification.
- [ ] **J.RE.9** Privacy audit: `Logcat` redaction extension treats `address_encrypted` as `tier=address`, never logs decrypted value. Backup-export UI for assets shows the "include addresses" toggle defaulting to off, with a banner warning when toggled on. Address field excluded from any crash-report payload (which is off-by-default anyway per `CLAUDE.md`).
- [ ] **J.RE.10** Unit tests (Robolectric): `ValuationEvent` write produces correct current-value read; cadence enum round-trips; SQLCipher decryption of `address_encrypted` returns plaintext; Logcat redaction extension produces no plaintext address; FX-conversion of latest valuation into base currency via the FX cache returns expected `Money`.
- [ ] **J.RE.11** Sample data fixture (fictional only): one asset row "Acme Sample House", currency EUR, initial value €500,000.00 dated 2026-01-01, second revaluation €510,000.00 dated 2026-04-01, one comparable sale €495,000.00 dated 2026-02-15. Lives in `app/src/test/resources/fixtures/asset-realestate-sample.json` (under the gitignore exception).
- [ ] **J.RE.12** (Post-v1, gated) Regional-index hint: per-currency picker maps EUR→Häuserpreisindex / GBP→UK HPI / USD→FHFA HPI. Index value snapshot at each `ValuationEvent` write. Asset detail screen renders "Regional index since your last revaluation: +X%." Strictly informational; never auto-updates value. Defer until at least one public-open-data source is licence-audited and the UI ergonomics question ("does the user actually want this surface?") is answered against the running app.

### References

- BORIS-D federated portal — <https://www.bodenrichtwerte-boris.de/>
- Destatis Häuserpreisindex — <https://www.destatis.de/DE/Themen/Wirtschaft/Preise/Baupreise-Immobilienpreisindex/_inhalt.html>
- HM Land Registry Price Paid Data — <https://www.gov.uk/government/statistical-data-sets/price-paid-data-downloads>
- UK House Price Index (ONS) — <https://www.gov.uk/government/collections/uk-house-price-index-reports>
- FHFA House Price Index — <https://www.fhfa.gov/data/hpi>
- Immobilienscout24 AGB (ToS) — <https://www.immobilienscout24.de/anbieten/agb.html>
- Immowelt AGB (ToS) — <https://www.immowelt.de/agb>
- Zoopla terms — <https://www.zoopla.co.uk/about/legal/terms/>
- Rightmove terms — <https://www.rightmove.co.uk/this-site/terms-of-use.html>
- Zillow Bridge Interactive (commercial AVM access) — <https://bridgedataoutput.com/pricing>
- Redfin terms — <https://www.redfin.com/about/terms-of-use>
