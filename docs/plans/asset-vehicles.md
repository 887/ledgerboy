# ledgerboy — asset class: vehicles

## Status: DECISION — manual-only, with a local depreciation-curve helper at revaluation time

Per-class research deliverable for the **vehicles** asset class (cars / motorcycles / boats / similar). Part of the manual-valuation cluster (see [`asset-realestate.md`](asset-realestate.md), [`asset-generic.md`](asset-generic.md)).

## What it is

A motor vehicle the user owns: car, motorcycle, scooter, boat, occasionally a caravan or trailer. Typical user shape: one or two vehicles, valued in the local currency of the country of registration, revalued annually or whenever the user goes through the "what's this worth now" mental exercise (insurance renewal, considering selling, end-of-year net-worth check).

Distinguishing properties:

- **Depreciating asset.** Unlike real estate, the expected trajectory is *down*, not up. The user knows roughly what the depreciation curve looks like for their vehicle type; surfacing a default curve makes the manual revaluation easier without needing an API.
- **VIN / registration plate is identifying PII.** Same posture as a property address: encrypted at rest, never transmitted, optional field.
- **Make / model / year is much less sensitive** than a VIN — it's a class descriptor, not a unique identifier — but still kept on-device.

## Source candidates

All evaluated automated-lookup helpers fail the ledgerboy gates. Documented so future agents don't re-grep this ground.

### Germany

| Source | Type | ToS / cost | Verdict |
| --- | --- | --- | --- |
| **Schwacke** | Commercial residual-value provider | B2B only; pricing on request, expect four-figure EUR/year for any meaningful access. [schwacke.de](https://www.schwacke.de/) | **Out.** Not a personal-use shape. |
| **DAT (Deutsche Automobil Treuhand)** | Commercial valuation provider | B2B only; same shape. [dat.de](https://www.dat.de/) | **Out.** |
| **Autoscout24** | Listing scraping target | ToS forbids automated harvesting ([autoscout24.de AGB §11](https://www.autoscout24.de/help/agb)). | **Out.** |
| **mobile.de** | Listing scraping target | ToS forbids automated harvesting ([mobile.de AGB §6](https://www.mobile.de/service/agb.html)). | **Out.** |

### United Kingdom

| Source | Type | ToS / cost | Verdict |
| --- | --- | --- | --- |
| **Glass's Guide** | Commercial residual-value provider | B2B only, paid. [glass.co.uk](https://glass.co.uk/) | **Out.** |
| **Cap HPI** | Commercial residual-value provider | B2B only, paid. [cap-hpi.com](https://www.cap-hpi.com/) | **Out.** |
| **Autotrader UK** | Listing scraping target | ToS forbids scraping ([autotrader.co.uk terms](https://www.autotrader.co.uk/content/terms/website-terms-of-use)). Valuation widget is web-form-only, no API. | **Out.** |
| **DVLA vehicle enquiry API** | Government, free | Returns MOT / tax status, not value. [gov.uk dvla-vehicle-enquiry-service](https://developer-portal.driver-vehicle-licensing.api.gov.uk/) | **Out for valuation;** noted for completeness — not useful here. |

### United States

| Source | Type | ToS / cost | Verdict |
| --- | --- | --- | --- |
| **Kelley Blue Book** | The reference US residual-value source | Public web form only for personal use; no free public API. Their API ("Manheim DataHub") is commercial-partner-only. ToS forbids scraping ([kbb.com terms-of-service](https://www.kbb.com/company/terms-of-service/)). | **Out.** |
| **Edmunds** | Same shape | Public web form; API discontinued for general use. [edmunds.com terms](https://www.edmunds.com/about/visitor-agreement.html) forbids scraping. | **Out.** |
| **NADA Guides** | Same shape | Subscription / partner. | **Out.** |
| **NHTSA VIN decoder API** | Government, free, public | Decodes a VIN to make/model/year/specs; **does not value**. [vpic.nhtsa.dot.gov/api](https://vpic.nhtsa.dot.gov/api/) | **Out for valuation;** could be a post-v1 helper to auto-fill make/model/year from a typed VIN, but the VIN never leaves the device so this is irrelevant unless we ship the VIN decoder library locally (NHTSA only offers an HTTP API, not a downloadable library). Reject for v1 on the "never transmit VIN" rule. |

### Cross-border / global

| Source | Type | Verdict |
| --- | --- | --- |
| **Wikipedia / Wikidata** | Per-model price-when-new info | **Maybe.** Free, Creative Commons. Coverage is patchy and not real-time, but Wikidata has structured `priceWhenNew` data for many vehicle models. Could seed a "what did this cost new" default. Phase J+ stretch — would need a packaged extract bundled with the app, since we don't want to call Wikidata live for a VIN-shaped query. Defer. |

### Summary

There is no per-vehicle automated-valuation source that:

1. Has a public free API,
2. Permits client-side personal use under its ToS, and
3. Returns a residual value for a make/model/year without a B2B partner agreement.

Same conclusion as real estate: the **personal-finance shape and the residual-value-vendor shape do not overlap.** No connector ships.

## Local helper: depreciation-curve estimator

In place of an API, ledgerboy ships a **pure-local depreciation helper** invoked from the revalue flow. No network calls.

Default model: exponential decay parameterized per vehicle type, with a steeper first-year drop.

```
value(t) ≈ purchase_price × (1 − first_year_drop) × decay_rate^(t − 1)
where t = years since purchase (t = 1 → value at end of year 1)
```

Per-class defaults (industry rules-of-thumb, not citations):

| Vehicle type | First-year drop | Subsequent decay (per year) |
| --- | --- | --- |
| New car | 20% | 0.85 (i.e. lose ~15%/yr) |
| Used car (3+ yrs old at purchase) | 10% | 0.90 |
| Motorcycle | 15% | 0.88 |
| Boat (motorboat) | 15% | 0.92 |
| Boat (sailboat) | 10% | 0.95 |
| EV (new) | 25% | 0.82 (battery-curve uncertainty; flagged in the helper UI) |

The helper presents "**Estimated current value: €X**" as a *prefilled but editable* number on the revalue flow. The user is told the formula is a rough heuristic, not a market quote. **All numbers above are defaults, not authoritative.** The user can adjust both `first_year_drop` and `decay_rate` per-asset in advanced settings if they have a better local read.

Sources for the heuristic defaults (informal, used as starting points only — these are rules-of-thumb floating around consumer-finance writing, not load-bearing citations):

- Carfax US "depreciation by car age" — <https://www.carfax.com/blog/car-depreciation>
- ADAC Wertverlust-Tabelle (Germany) — <https://www.adac.de/rund-ums-fahrzeug/auto-kaufen-verkaufen/gebrauchtwagenkauf/wertverlust/>
- WhatCar UK depreciation — <https://www.whatcar.com/news/depreciation-guide/>

These are not API sources; they're calibration references for the bundled default curve. The curve itself is computed locally, no network.

## Lookup shape

There is no remote lookup. The user opens the asset and either:

- Types a fresh value, or
- Taps "**Estimate from depreciation**" to prefill the value from the local helper, then adjusts.

## Revaluation cadence

User-configurable per-asset, with defaults:

- **Annually** (default). Tax-renewal / insurance-renewal time tends to be when people think about it.
- **Quarterly** (offered for users in active sell-considering mode).
- **On demand only** (no reminder).

The depreciation helper can also be invoked **without a cadence reminder** — the user can pop into the revalue flow whenever they want a fresh estimate.

## Fit recommendation

**Manual-only**, with a **local depreciation-curve helper** at the revalue moment. No network calls for any vehicle data, ever.

## UX features

### Add-asset wizard (vehicle flow)

1. **Class pick:** Vehicle.
2. **Sub-class pick:** New car / Used car / Motorcycle / Boat (motor) / Boat (sail) / EV / Other. Selects the depreciation defaults.
3. **Name:** free text (e.g. "Golf", "Yamaha", "the boat"). Private, never transmitted.
4. **Make / model / year** (optional, recommended): three free-text fields. Used for display only; never transmitted; not used to seed any API call.
5. **VIN / registration plate** (optional, encrypted at rest, never transmitted): same posture as a property address.
6. **Currency:** ISO-4217 picker, defaults to user's base currency.
7. **Purchase price** (optional, recommended): integer minor units. Seeds the depreciation helper.
8. **Purchase date** (optional, recommended): date picker. Seeds the depreciation helper.
9. **Initial valuation:** integer minor units. If purchase price + date are provided, the depreciation helper offers a prefilled default ("Estimated current value: €X — edit if you have a better number").
10. **Revaluation cadence:** annually (default) / quarterly / on demand.
11. **Save:** writes the asset row and the initial `ValuationEvent(date=today, value=initial, source="manual")`.

### Revalue flow

1. From the asset detail screen, tap **Revalue now**.
2. The depreciation helper computes an estimate from purchase price + purchase date + sub-class defaults (or per-asset overrides) and presents it as the prefilled default.
3. The user accepts, edits, or replaces the prefilled number.
4. Optional note (free text, private, never transmitted).
5. **Save:** writes `ValuationEvent(date=today, value=new, source="manual" or "manual_depreciation_helper", note=…)`.

The `source` field records whether the user accepted the helper's prefilled value verbatim (`manual_depreciation_helper`) or overrode it (`manual`). This is purely for the user's own future "how have I been valuing this?" introspection; no analytics leave the device.

### Revaluation reminder

Same WorkManager pattern as real estate. Notification → opens the revalue flow with the depreciation helper's estimate pre-computed.

### Asset history view

Same shape as real estate:

- Line chart of value-over-time.
- Tabular list of `ValuationEvent` rows.
- Optional overlay (post-v1): the depreciation-curve projection line, so the user can see "what the formula expected vs. what I've actually entered."

## Privacy posture

Vehicle metadata privacy tiers, per `CLAUDE.md`:

- **VIN / registration plate:** highest sensitivity, same tier as addresses. Encrypted at rest under the SQLCipher key. Never transmitted. Never in logs (`Logcat` extension redacts as `tier=vin`). Never in screenshots used in repo / store listing. Backup-export `include VINs` toggle defaults to off, warning banner when toggled on.
- **Make / model / year:** medium sensitivity. Encrypted at rest along with the rest of the asset row. Never transmitted. Sample data uses obviously fictional combinations ("Acme Demo Car 2024").
- **Purchase price, purchase date, valuations:** standard money-privacy tier. Encrypted at rest. Never transmitted. Sample data uses obviously round fictional numbers (€20,000.00, 2026-01-01).

Crucially: **the depreciation helper never sends any vehicle data anywhere.** The curve is bundled into the APK as a static lookup table; no model-specific data is fetched. The helper's input is (sub-class, purchase price minor units, purchase date) — all already on-device.

## Decision

**Manual-only**, with a **pure-local depreciation-curve helper** at the revalue moment. No automated valuation connector. No network calls for vehicle data. VIN / registration plate treated as PII tier=vin.

### Phase J implementation sub-steps (vehicles)

Shares the `AssetEntity` / `ValuationEventEntity` / wizard scaffolding with real estate and generic (see `J.RE.1`–`J.RE.4` in [`asset-realestate.md`](asset-realestate.md) — these land once, all three classes use them). Per-class sub-steps below cover the vehicle-specific additions.

- [ ] **J.V.1** `AssetEntity` extensions for `class=vehicle`: `vehicle_subclass` (enum: `new_car` / `used_car` / `motorcycle` / `boat_motor` / `boat_sail` / `ev` / `other`), `make_nullable`, `model_nullable`, `year_nullable`, `vin_encrypted_nullable`, `purchase_price_minor_nullable`, `purchase_date_nullable`. VIN field encrypted at rest under the SQLCipher key.
- [ ] **J.V.2** `DepreciationHelper` pure-Kotlin class: takes `(subclass, purchase_price_minor, purchase_date, today)`, returns estimated current value. No network. No I/O. Pure function. Per-asset overrides supported via two optional fields on `AssetEntity` (`first_year_drop_override_nullable`, `decay_rate_override_nullable`). Unit-tested exhaustively.
- [ ] **J.V.3** `AddAssetWizard` vehicle flow: class-pick → sub-class pick → name → make/model/year → VIN → currency → purchase price → purchase date → initial value (with depreciation-helper prefill) → cadence → save. Reuses the wizard shell from `J.RE.4`. Strings in `values/strings.xml` under `asset_vehicle_*` namespace.
- [ ] **J.V.4** `RevalueAssetFlow` extension: for `class=vehicle`, the prefilled value comes from `DepreciationHelper` instead of "previous value." User can override. `ValuationEvent.source` records `manual_depreciation_helper` if user accepts verbatim, else `manual`.
- [ ] **J.V.5** Per-asset advanced settings: `first_year_drop_override` and `decay_rate_override` editable in a per-asset settings sheet, accessed from the detail screen. Defaults shown next to the override input; clearing the input returns to defaults.
- [ ] **J.V.6** Asset history view extension: post-v1 overlay of the depreciation-curve projection line on the value-over-time chart. Defer behind a "show projected curve" toggle.
- [ ] **J.V.7** Privacy audit: `Logcat` redaction extension treats `vin_encrypted` as `tier=vin`, treats `make`/`model`/`year` as `tier=vehicle_class`. Backup-export UI shows separate "include VINs" and "include make/model" toggles; both default to off. Sample data fixture uses fictional model names.
- [ ] **J.V.8** Unit tests (Robolectric): `DepreciationHelper` produces expected values across all sub-classes for a synthetic purchase-price/date matrix; per-asset overrides take precedence over sub-class defaults; SQLCipher decryption of `vin_encrypted` returns plaintext; Logcat redaction extension produces no plaintext VIN or model; `ValuationEvent` with `source=manual_depreciation_helper` round-trips correctly.
- [ ] **J.V.9** Sample data fixture (fictional): one asset row "Acme Demo Car", sub-class `used_car`, currency EUR, purchase price €20,000.00 dated 2024-06-01, initial value (helper-estimated) ~€14,580.00 dated 2026-01-01. Lives in `app/src/test/resources/fixtures/asset-vehicle-sample.json`.
- [ ] **J.V.10** No-network audit: assert via Robolectric test that constructing an `AssetEntity` with `class=vehicle` and invoking `DepreciationHelper` + `RevalueAssetFlow` + `AssetDetailScreen` does not open any network sockets. (Same shape as the "no telemetry" smoke tests elsewhere in the family.)

### References

- Schwacke (DE B2B residual-value, paid) — <https://www.schwacke.de/>
- DAT (DE B2B residual-value, paid) — <https://www.dat.de/>
- Autoscout24 AGB (ToS forbids scraping) — <https://www.autoscout24.de/help/agb>
- mobile.de AGB (ToS forbids scraping) — <https://www.mobile.de/service/agb.html>
- Glass's Guide (UK B2B residual-value, paid) — <https://glass.co.uk/>
- Cap HPI (UK B2B residual-value, paid) — <https://www.cap-hpi.com/>
- Autotrader UK terms — <https://www.autotrader.co.uk/content/terms/website-terms-of-use>
- Kelley Blue Book terms — <https://www.kbb.com/company/terms-of-service/>
- Edmunds terms — <https://www.edmunds.com/about/visitor-agreement.html>
- NHTSA VIN decoder API (free, but HTTP-only; rejected on never-transmit-VIN) — <https://vpic.nhtsa.dot.gov/api/>
- Depreciation heuristic reference: Carfax — <https://www.carfax.com/blog/car-depreciation>
- Depreciation heuristic reference: ADAC Wertverlust — <https://www.adac.de/rund-ums-fahrzeug/auto-kaufen-verkaufen/gebrauchtwagenkauf/wertverlust/>
- Depreciation heuristic reference: WhatCar — <https://www.whatcar.com/news/depreciation-guide/>
