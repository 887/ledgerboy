# ledgerboy — banking connectivity research

## Status: SEED — research-round agents pick up from here

This is a **seed prompt** for the next round of research agents. The goal is to evaluate every plausible bank-connectivity option and produce a per-source decision file (`docs/plans/banking-<source>.md`). Phase B of [`main.md`](main.md) expands sub-steps after the first source is decided.

## Constraints (load-bearing — do not relax without asking)

1. **Local-first.** The connector runs on the device. No traffic transits a server that we operate. If a candidate aggregator (Plaid, Tink, Salt Edge, TrueLayer, Nordigen, GoCardless) is on the table, evaluate it as a third-party SaaS — the user opts in per-aggregator, credentials flow direct device ↔ aggregator, and the aggregator's data residency / retention / ToS get documented.
2. **License gate.** MIT / Apache 2.0 / BSD / MPL 2.0 only. **No GPL / AGPL / LGPL.** See [`oss-licenses.md`](oss-licenses.md). If a candidate fails the gate, document the alternative.
3. **Maintenance gate.** Last release within the last 18 months, OR active commit cadence, OR active fork. Dormant libraries get flagged; if they're dormant *and* security-relevant (any banking crypto), they're out.
4. **Android compatibility.** Compile-SDK floor 28. Transitive deps must not pull JDK-9+-only APIs that break on Android. Coroutines-friendly is preferred but not required (we can wrap blocking APIs).
5. **Money is integer minor units.** Connectors emit `Money` (long, currency) — never `BigDecimal` or `Double` for amounts. Conversion happens at the connector boundary.

## Per-source questions to answer

For each connectivity source, the research deliverable (`docs/plans/banking-<source>.md`) answers:

- **What is it?** One-paragraph plain-language summary.
- **Library candidates.** For each: SPDX licence, latest release date, last commit date, dep weight (in MB on the APK), Android compatibility (compile-SDK floor, problematic transitive deps).
- **Sample bank coverage.** Which real-world banks does this source cover for this app? (DACH-focused for EBICS/FinTS; UK/EU-broad for PSD2.)
- **Credential shape.** What does the user have to provide? Keys / certificates / PIN / OAuth flow / username+password?
- **Sandbox / test bank.** Is there a public test bank we can wire smoke tests against?
- **Cost.** Per-call billing? Per-user billing? Free tier?
- **ToS / legal posture.** Does the source require a commercial licence for personal-use redistribution? Does it require KYC of the redistributor?
- **Fit-for-ledgerboy.** Recommendation: **adopt** / **defer** / **reject**, with one paragraph of rationale.

## Sources to research

### `banking-ebics.md` — EBICS (Electronic Banking Internet Communication Standard)

DACH-region banking standard. Per-user key generation, INI letter, bank-side activation handshake, then SEPA payments + account statements (MT940 / CAMT.053) + order submission.

Library candidates to evaluate (non-exhaustive starting list — research-round agent should grep Maven Central and GitHub for more):

- `ebics-java` (active fork on GitHub) — verify SPDX, maintenance, Android compat
- `kbank4j` — verify SPDX, last release
- Various unmaintained forks of the original `ebics-java`

Concrete questions:

- Does the chosen lib support EBICS 3.0 (the current spec) or only 2.5?
- Does it support German + Swiss + French + Austrian dialects (each EBICS bank has slightly different order-type vocabularies)?
- Does it support TLS pinning per bank?
- Is the crypto (RSA key generation, EBICS T / X / E signature schemes) implemented in pure Java or does it pull a native lib (bad for Android)?
- INI letter generation: does the lib produce a printable PDF / HTML, or do we generate the letter ourselves?
- How is the bank-side activation flow handled (the bank manually toggles the user's key into ACTIVE state — the app polls)?

### `banking-fints.md` — FinTS / HBCI

Older German banking standard, still widely deployed by Sparkassen / Volksbanken / smaller German banks.

Library candidates to evaluate:

- `hbci4java` — historically **LGPL**. **License gate fails — document the alternative.** Permissive forks? Reimplementation of a subset?
- Any Apache / MIT FinTS libraries (sparse — likely the bottleneck)

Concrete questions:

- Is there *any* permissively-licensed FinTS library on Maven Central?
- If not: what's the minimum subset of FinTS we need (account balance + transaction list — no SEPA submission in v1)? How big is "build it ourselves" at that subset?
- Does the chosen approach handle PSD2 strong customer authentication (SCA) for German banks (which is mandatory for FinTS as of 2019)?
- TAN media flows: photoTAN, pushTAN, smsTAN, chipTAN — which do we need to support?

### `banking-psd2.md` — PSD2 Open Banking (via aggregator)

UK / EU bank-agnostic. Always mediated by an aggregator because direct PSD2 access requires the app to be a PSD2-licensed AISP.

Aggregator candidates to evaluate (license-gate doesn't apply here — aggregators are SaaS, not redistributed libraries — but cost / ToS / data residency do):

- **Tink** (Visa-owned, broad EU coverage, paid)
- **TrueLayer** (UK + EU, paid)
- **Plaid** (US-focused but expanding into UK/EU, paid)
- **Salt Edge** (broad EU coverage, paid)
- **GoCardless Bank Account Data** (formerly Nordigen — free tier, EU/UK, the most-likely default for a personal-use app)
- **Direct via the user's own bank's PSD2 API** (some banks publish OAuth-based PSD2 APIs directly — sparse, per-bank effort)

Concrete questions:

- Free tier limits (calls/month, accounts/user, retention window)?
- Onboarding friction (does the user need to create their own developer account at the aggregator)?
- Data residency (EU-only? US transit?)
- ToS for personal-use redistribution
- OAuth flow shape (system browser? in-app browser?)
- Refresh-token storage requirements (consent revalidation cadence)

GoCardless Bank Account Data is the most likely default — free, EU/UK, AISP-licensed, simple OAuth flow — but it has a 90-day consent window and a calls/day cap. Document the trade-offs.

### `banking-import.md` — file import (CSV / QIF / OFX / MT940 / CAMT.053)

For banks where no API is available, and for one-off historical loads. SAF picker → parser → `NormalizedTransaction` rows.

Library / approach candidates per format:

- **CSV** — bank exports are wildly heterogeneous. Likely roll our own parser with a small library of per-bank dialects + a generic fallback that the user maps columns for. No third-party CSV lib needed beyond `okio` / stdlib.
- **QIF** — Quicken Interchange Format, line-oriented. Roll our own (it's ~100 LOC).
- **OFX** — SGML-flavoured. Library candidates exist on Maven Central; check SPDX per candidate.
- **MT940** — SWIFT format, line-oriented but with structured tags. Library candidates exist; many are Apache 2.0. Spot-check.
- **CAMT.053** — XML, ISO 20022 schema. Cleanest approach: generate Kotlin data classes from the public XSD with `kotlinx.serialization-xml` or an XSD-to-Kotlin codegen step. No third-party lib needed.

Concrete questions per format:

- Best library or roll-our-own?
- Per-bank dialect quirks (CSV especially — German banks ship semicolon-delimited; French banks ship comma-decimal-with-semicolon-separator; UK banks ship comma-decimal-with-comma-separator)?
- Encoding (UTF-8? Windows-1252? ISO-8859-1?)
- Round-trip integrity: hash each imported transaction so re-importing the same file doesn't double-count.

### `banking-fx.md` — FX rate source

Default expectation: ECB daily reference rates (free, public, EUR-centric, published as a small daily XML).

Alternatives:

- `exchangerate.host` (free, broader currency coverage, less authoritative)
- Per-bank FX rates (each EBICS connection could pull its own FX table — heterogeneous)
- A paid commercial source (TrueFX, OANDA) — unlikely needed

Concrete questions:

- Refresh cadence (daily is fine for non-trading purposes — confirm)
- Historical rates for back-dated transactions: ECB publishes a 30-year XML; cache locally on first install
- Non-EUR base-currency support: ECB rates are EUR-paired only; cross-rates computed (USD/JPY = USD/EUR × EUR/JPY). Document the precision implications.
- Caching strategy: TTL, eviction, persistence across reinstalls

## What "done" looks like for this research round

- Five (or so) decision files: `banking-ebics.md`, `banking-fints.md`, `banking-psd2.md`, `banking-import.md`, `banking-fx.md`.
- Each file ends with a **Decision** section: which library / aggregator / approach we adopt, why, and what the v1 surface looks like.
- This file (`banking-research.md`) gets a "Decisions" section appended pointing at the five decision files.
- [`main.md`](main.md) Phase B (and the gates on Phases C / D) get sub-step checkboxes added based on the decisions.
- The first connector to land in Phase B is **whichever has the lowest friction-to-first-real-transaction-row** — likely either GoCardless PSD2 (lowest setup friction for a developer with a UK / EU bank) or file-import CSV (lowest setup friction generally — works for any bank that publishes any export).
