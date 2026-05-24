# ledgerboy — asset class: generic (catch-all manual valuation)

## Status: DECISION — manual-only, ownership-fraction supported, no automated source

Per-class research deliverable for the **generic** asset class — the catch-all bucket for assets that don't fit a more specific class. Part of the manual-valuation cluster (see [`asset-realestate.md`](asset-realestate.md), [`asset-vehicles.md`](asset-vehicles.md)). Distinct from the automated-feed cluster (collectibles / securities / crypto).

## What it is

Anything the user wants to track on their net-worth ledger that doesn't have a more specific class. The user names it, picks a sub-type from a small enum (or "Other"), enters a value, and revalues it whenever they feel like it. No source feed, no formula helper, just a manual ledger row whose value moves only when the user moves it.

Typical examples (drives the sub-type enum):

- **Jewelry** — wedding ring, inherited watch, valuables in a safe. Real valuation requires an appraisal; no API exists for personal use.
- **Art** — a painting, a print, a sculpture. Auction comps are gallery-grey-market data; not a personal-use API surface.
- **Business stakes** — a share of an LLC / GmbH the user partly owns; a stake in a friend's startup. Book-value or last-funding-round value, manually maintained.
- **Co-owned assets** — "half of this thing with my sibling": a holiday home held with a sibling, a boat shared with a partner, a piece of land jointly inherited. Same shape as the matched specific class, plus an ownership fraction.
- **Pension funds** (when not API-reachable as securities) — defined-benefit pensions, employer pension schemes with no API, accrued state pension entitlements. Estimated current value from statements.
- **Receivables / loans-you-made** — money owed to the user by a person or entity; manually tracked at face value, optionally with a haircut for collectibility doubt.
- **Other** — anything else.

Distinguishing properties:

- **No common shape across instances.** A jewelry row and a business-stake row have nothing in common except "the user types a value." So the schema is deliberately minimal.
- **Ownership fraction is first-class.** Unlike real estate and vehicles, "I own 33% of this" is the common case for the generic bucket (inherited assets split among siblings, business stakes, jointly-held holiday homes). The net-worth aggregate applies the fraction.
- **Notes field carries more weight here.** Without a structured shape for "what is this thing", a free-form notes field is how the user remembers context. Treated as high-sensitivity PII.

## Source candidates

**None.** This is the catch-all class precisely because nothing automated applies.

Adjacent sources surveyed and rejected:

| Source | Type | ToS / cost | Verdict |
| --- | --- | --- | --- |
| **GIA / IGI appraisal databases** | Jewelry valuation | Appraisal-shop tools, not consumer-facing. No public API. | **Out.** |
| **Artnet Price Database** | Art auction comps | Paid, subscription. ToS forbids scraping ([artnet.com terms](https://www.artnet.com/about/terms-and-conditions.aspx)). | **Out.** |
| **Artprice.com** | Art auction comps | Paid, subscription. | **Out.** |
| **MutualArt** | Art auction comps | Paid, subscription. | **Out.** |
| **WatchCharts / Chrono24** | Watch market data (jewelry-adjacent) | Chrono24 has a partner API ([chrono24.com partner](https://www.chrono24.com/info/api.htm)), commercial / dealer-only, ToS forbids scraping public listings. WatchCharts is public-web only, no API. | **Out for v1.** Watches sit awkwardly between "jewelry" and "collectibles." If the user has a meaningful watch portfolio, the *collectibles* cluster (parallel research) is the more natural home; this file punts on watches. |
| **Crunchbase / PitchBook** | Startup-stake valuation | Crunchbase has a paid Enterprise API; PitchBook is enterprise-only. ToS forbids scraping. | **Out.** And even if accessible, last-round valuations aren't a per-share valuation for a personal stake — the conversion math is its own can of worms. |
| **Pension provider portals** | Per-provider, no common shape | The user logs in to their pension provider's web portal and reads the value off a statement. No standard API. | **Out for v1.** Per-provider scraping is a non-starter both technically (each provider differs) and contractually (logging in on the user's behalf would require credential storage and a TOTP path; way outside Phase J scope). |

### Summary

There is no automated source for this bucket — by definition. The catch-all class exists precisely to absorb things that don't have one. If a future automated source appears for some sub-type (e.g. if Chrono24 ever opens a personal-use API for watch valuations), the migration path is to add a new dedicated class file (`asset-watches.md`) and migrate matching rows.

## Lookup shape

There is no lookup. The user opens the asset and types a number.

The notes field — high-sensitivity free-text — is where the user records *why* the number is what it is ("appraised at €4,200 by jeweler X in 2025", "founder's offer of $0.40/share at Series A close", "Aunt's estate distribution Q3 2026"). Never transmitted.

## Revaluation cadence

User-configurable per-asset, with defaults:

- **Annually** (default). Once-a-year mental check-in is the realistic rhythm for most of this bucket.
- **Quarterly** (offered for sub-types that move faster, e.g. business stakes where the user has cap-table updates).
- **On demand only** (no reminder). Default for receivables (revalued only when collected / written down) and inherited items the user doesn't think about often.

The wizard offers a sensible default per sub-type:

- Jewelry / Art / Other → annually
- Business → quarterly
- Co-owned → annually (matching the underlying asset's natural cadence)
- Pension → annually (most statements arrive annually)
- Receivables → on demand only

## Fit recommendation

**Manual-only.** No automated source. No formula helper (unlike the vehicle depreciation curve, there's no analogous local-computable estimate that generalizes across jewelry / art / business / receivables).

## UX features

### Add-asset wizard (generic flow)

1. **Class pick:** Other / Generic.
2. **Sub-type pick:** Jewelry / Art / Business / Co-owned / Pension / Receivable / Other. Selects the default cadence.
3. **Name:** free text. Private, never transmitted.
4. **Currency:** ISO-4217 picker, defaults to user's base currency.
5. **Ownership fraction:** float 0–1, default 1.0 (100%). Editable; UI presents it as a percentage with two decimal places. The net-worth aggregate multiplies the latest valuation by the ownership fraction before FX-converting.
6. **Acquisition date** (optional): date picker.
7. **Acquisition cost** (optional): integer minor units, same currency. The user's share, not the full asset's cost — clarified in copy.
8. **Initial valuation** (integer minor units): the asset's *full* value, not the user's share. The ownership-fraction is applied at aggregate time, not at entry time. Clarified in copy ("Enter the full value of the thing; we'll apply your X% share automatically in the net-worth view.").
9. **Notes** (optional, encrypted at rest, never transmitted): free-form text.
10. **Revaluation cadence:** sub-type default, editable.
11. **Save:** writes the asset row and the initial `ValuationEvent(date=today, value=initial, source="manual")`.

### Revalue flow

1. From the asset detail screen, tap **Revalue now**.
2. Enter the new full value (with the same "we apply your ownership share automatically" reminder if fraction < 1).
3. Optional note (free text, private, never transmitted). Worth using for this class — the user's future self will want context.
4. **Save:** writes a new `ValuationEvent(date=today, value=new, source="manual", note=…)`.

### Ownership-fraction-aware aggregation

The net-worth view aggregates `latestValuationEvent.value × ownership_fraction` per asset, FX-converts to the user's base currency, and sums. The per-asset detail screen shows both the full value ("Full value: €100,000.00") and the user's share ("Your share: 33% → €33,000.00").

If ownership fraction is exactly 1.0 (the default), the "Your share" line is hidden — no need to clutter the common case.

### Revaluation reminder

Same WorkManager pattern as real estate / vehicles. Notification → opens the revalue flow.

For receivables specifically: the default cadence is **on demand only**, so no reminder fires. Instead, the asset detail screen has a prominent "Mark as paid" action that writes a final `ValuationEvent(value=0, note="paid/collected")` and optionally archives the asset.

### Asset history view

Same shape as real estate / vehicles:

- Line chart of value-over-time.
- Tabular list of `ValuationEvent` rows.
- Notes are visible inline in the history list (they're often where the rationale lives).
- Ownership fraction is shown in the header, with the multiplier rendered next to each value.

### Co-ownership specifics

For sub-type `Co-owned`, two small UX additions:

- A **co-owner name** field (free text, optional, encrypted at rest, never transmitted). Useful for "this is the holiday home with my sister" rather than "Co-owned asset #3."
- A copy reminder in the wizard: "We track your share only. Your co-owner is responsible for their own tracking, in their own ledger."

### Receivables specifics

For sub-type `Receivable`:

- An optional **counterparty** field (free text, encrypted at rest, never transmitted) — who owes you.
- An optional **expected collection date** (date picker) — informational, no automation hung on it.
- An optional **collectibility haircut** (0–100%, default 0%) — for "I'm owed €5,000 but realistically I'll see €3,000 of it." If set, the net-worth aggregate applies `latest_value × (1 − haircut) × ownership_fraction`. The detail screen surfaces both the face value and the haircut-adjusted value.

## Privacy posture

Generic-asset metadata privacy tiers, per `CLAUDE.md`:

- **Name, notes, co-owner name, counterparty:** highest sensitivity, treated as PII. Encrypted at rest under the SQLCipher key. Never transmitted. Never in logs (`Logcat` redacts as `tier=generic_pii`). Never in screenshots used in repo / store listing.
- **Sub-type, currency, ownership fraction, dates, money values:** standard tier. Encrypted at rest. Never transmitted.
- **Backup-export UI** has an "include notes and co-owner / counterparty names" toggle, defaulting to off, with a banner warning when toggled on. The user explicitly opts in to including the most-sensitive free-text fields in a backup.
- **Sample data fixture** uses obviously fictional names ("Acme Demo Asset", "Sample Co-owned Boat with Demo Sibling", "Test Receivable from Sample Person").

The notes field in particular is the most-likely-to-leak surface across the manual-valuation cluster — it's free-text, the user puts narrative context in it, and that context is often the most identifying thing in the entire database ("loan to Bob for the kitchen renovation"). The privacy audit step (J.G.9 below) treats the notes field as the highest-tier and confirms it never leaves the device by default.

## Decision

**Manual-only.** No automated source. Ownership fraction is first-class. Sub-type enum drives the default cadence and a few per-sub-type UX touches (co-owner name for `Co-owned`, counterparty + collectibility-haircut for `Receivable`). Notes field is treated as the highest-tier PII in the asset row.

### Phase J implementation sub-steps (generic)

Shares the `AssetEntity` / `ValuationEventEntity` / wizard scaffolding with real estate and vehicles (see `J.RE.1`–`J.RE.4` in [`asset-realestate.md`](asset-realestate.md)). Per-class sub-steps below cover the generic-specific additions.

- [ ] **J.G.1** `AssetEntity` extensions for `class=generic`: `generic_subtype` (enum: `jewelry` / `art` / `business` / `coowned` / `pension` / `receivable` / `other`), `ownership_fraction` (Double, default 1.0, validated 0.0 < x ≤ 1.0), `co_owner_name_encrypted_nullable`, `counterparty_encrypted_nullable`, `expected_collection_date_nullable`, `collectibility_haircut_nullable` (Double, 0.0–1.0), `notes_encrypted_nullable`. All `_encrypted_` fields wrapped under the SQLCipher key.
- [ ] **J.G.2** `AddAssetWizard` generic flow: class-pick → sub-type pick (drives cadence default + per-sub-type extra fields) → name → currency → ownership fraction → acquisition (optional) → initial value (full value, not user's share — clarified in copy) → notes → cadence → save. Reuses the wizard shell from `J.RE.4`. Strings in `values/strings.xml` under `asset_generic_*` namespace.
- [ ] **J.G.3** Per-sub-type wizard branches: `coowned` adds the co-owner name field + the "we track your share only" copy reminder; `receivable` adds counterparty + expected-collection-date + collectibility-haircut fields and overrides the cadence default to `on_demand`.
- [ ] **J.G.4** `AssetDetailScreen` generic-class extensions: always render ownership fraction in the header when `< 1.0`; render "Full value / Your share" rows; for `receivable` sub-type render the haircut-adjusted value next to the face value; surface notes inline in the history list.
- [ ] **J.G.5** `RevalueAssetFlow` generic-class extensions: the entered value is the *full* value (with copy reminder when fraction `< 1.0`); the haircut and fraction are applied at aggregate time, not at entry time.
- [ ] **J.G.6** Net-worth aggregator: per-asset contribution is `latestValuationEvent.value × ownership_fraction × (1 − collectibility_haircut ?: 0)`, FX-converted via the FX cache. Unit-tested across the full sub-type matrix.
- [ ] **J.G.7** `MarkAsPaidFlow` (receivables only): one-tap action that writes `ValuationEvent(value=0, source="manual", note="paid")` and offers to archive the asset (soft-delete with a tombstone — recoverable from settings for at least one revaluation cycle, then hard-deletable).
- [ ] **J.G.8** Revaluation reminder (same WorkManager shape as J.RE.8 / J.V.x). For `receivable` with cadence `on_demand`, no worker is scheduled.
- [ ] **J.G.9** Privacy audit: `Logcat` redaction extension treats `name`, `notes_encrypted`, `co_owner_name_encrypted`, `counterparty_encrypted` as `tier=generic_pii`, never logs decrypted values. Backup-export UI shows separate toggles for "include notes" and "include co-owner / counterparty names"; both default to off, both carry warning banners when toggled on. Sample data fixture uses fictional names throughout.
- [ ] **J.G.10** Unit tests (Robolectric): ownership-fraction aggregator returns expected values across the sub-type matrix; collectibility-haircut only applies for `receivable`; SQLCipher decryption of every `_encrypted` field round-trips; Logcat redaction extension produces no plaintext PII; `MarkAsPaidFlow` writes the expected terminal `ValuationEvent` and tombstones the asset; FX-converted contribution to the net-worth aggregate matches expectation.
- [ ] **J.G.11** Sample data fixtures (fictional): one of each sub-type — "Acme Sample Jewelry (€2,000.00)", "Demo Art Print (€800.00, 50% share)", "Test Business Stake (€50,000.00, 33% share)", "Sample Co-owned Boat (€30,000.00, 50% share)", "Demo Pension (€100,000.00)", "Test Receivable from Sample Person (€5,000.00, 20% haircut)". Lives in `app/src/test/resources/fixtures/asset-generic-sample.json`.
- [ ] **J.G.12** No-network audit: assert via Robolectric test that creating, revaluing, and aggregating any generic-class asset performs zero network I/O. Same shape as `J.V.10`.

### References

- Artnet terms (price database, paid, scraping-forbidden) — <https://www.artnet.com/about/terms-and-conditions.aspx>
- Chrono24 partner API (watches, commercial / dealer-only) — <https://www.chrono24.com/info/api.htm>
- Cluster-mate decisions: [`asset-realestate.md`](asset-realestate.md), [`asset-vehicles.md`](asset-vehicles.md)
- Parallel cluster (automated feeds): [`asset-collectibles.md`](asset-collectibles.md), [`asset-securities.md`](asset-securities.md), [`asset-crypto.md`](asset-crypto.md) (in flight)
- Seed: [`asset-research.md`](asset-research.md)
