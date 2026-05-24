# ledgerboy — banking connectivity research

## Status: Research round 1 complete; pivot to plugin architecture applied 2026-05-24. See [`connector-plugins.md`](connector-plugins.md).

This file was the seed prompt for the first round of banking-connectivity research. Round 1 landed five per-source decision files (see the Decisions section below); on 2026-05-24 the user issued a course-correction locked in [`connector-plugins.md`](connector-plugins.md): **every connector is a plugin, disabled by default, no aggregator middlemen, no paid-SaaS pricing services, direct-to-source-of-truth only.** The per-source decision files have been rewritten to reflect that posture; this seed has been reframed to match. Phase B of [`main.md`](main.md) expands sub-steps after the first per-source plugin is implemented on top of the Phase X runtime.

## Constraints (load-bearing — do not relax without asking)

1. **Every connector is a plugin disabled by default.** No aggregator middlemen. No paid-SaaS pricing services. Direct-to-source-of-truth via open standards and client-style protocol implementations. Architecture locked in [`connector-plugins.md`](connector-plugins.md). Each plugin implements `ConnectorPlugin` (in the `Banking`, `Fx`, or `Import` category), ships with `state = Disabled`, and only fetches once the user has explicitly enabled, configured, and reached `Active`.
2. **Local-first, direct-to-bank.** The connector runs on the device and talks directly to the bank (or the open-data source). Nothing transits a server we operate; nothing transits a third-party aggregator we don't operate either.
3. **License gate.** MIT / Apache 2.0 / BSD / MPL 2.0 only. **No GPL / AGPL / LGPL.** See [`oss-licenses.md`](oss-licenses.md). Because every plugin compiles into the single APK, an LGPL plugin would infect the whole APK — the gate applies to plugin deps the same as to core deps. If a candidate fails the gate, document the alternative (this is why FinTS ships as a roll-our-own client).
4. **Maintenance gate.** Last release within the last 18 months, OR active commit cadence, OR active fork. Dormant libraries get flagged; if they're dormant *and* security-relevant (any banking crypto), they're out.
5. **Android compatibility.** Compile-SDK floor 28. Transitive deps must not pull JDK-9+-only APIs that break on Android. Coroutines-friendly is preferred but not required (we can wrap blocking APIs).
6. **Money is integer minor units.** Connectors emit `Money` (long, currency) — never `BigDecimal` or `Double` for amounts. Conversion happens at the connector boundary.

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

## Per-source reading order (post-pivot)

The user's preference is locked: **PSD2-direct (per bank) + FinTS-roll-our-own** are the load-bearing v1 plugins; **CSV / CAMT.053 / MT940 imports** are the universal fallback for everything the API plugins don't reach. EBICS is rejected (out of personal-banking reach in DACH retail). The rejection of aggregators (GoCardless, Tink, Plaid, Salt Edge, TrueLayer) and the rejection of any paid-SaaS pricing route lives in the per-source decision files; the seed no longer enumerates them as candidates.

Read order for a fresh agent:

1. [`connector-plugins.md`](connector-plugins.md) — the architecture lock.
2. [`banking-psd2.md`](banking-psd2.md) — per-bank PSD2-direct via Berlin Group NextGenPSD2 (one plugin per bank, each owning its own OAuth flow + endpoint URLs + dialect).
3. [`banking-fints.md`](banking-fints.md) — roll-our-own FinTS-client plugin (~3,300 LOC, MIT) covering Sparkassen / Volksbanken / most German retail banks in a single plugin.
4. [`banking-import.md`](banking-import.md) — per-format import plugins (CSV / QIF / OFX / MT940 / CAMT.053) as the universal fallback.
5. [`banking-fx.md`](banking-fx.md) — ECB-direct and Bundesbank-direct FX plugins (both off by default; the user enables whichever they trust).
6. [`banking-ebics.md`](banking-ebics.md) — rejection rationale (would be plugin-only if ever reconsidered, but DACH retail banks gate EBICS behind business contracts the user can't reach).

## Per-source questions to answer

For each connector, the decision file answers:

- **What is it?** One-paragraph plain-language summary.
- **Plugin shape.** `ConnectorPlugin` id, category, what `ConfigScreen()` collects, what `fetch()` emits, what the privacy statement says about data leaving the device.
- **Library candidates (if any).** SPDX licence, latest release date, last commit date, dep weight (in MB on the APK), Android compatibility (compile-SDK floor, problematic transitive deps). Roll-our-own is on the table if no permissively-licensed lib exists (this is exactly what happened with FinTS).
- **Sample bank coverage.** Which real-world banks does this plugin reach? (PSD2-direct is per-bank — DACH banks that publish a Berlin Group NextGenPSD2 API directly; FinTS-client covers Sparkassen + Volksbanken + most German retail banks in one plugin.)
- **Credential shape.** What does the user have to provide? Bank-picker + OAuth flow / PIN + TAN / API key?
- **Sandbox / test bank.** Is there a public test bank we can wire smoke tests against?
- **Cost.** Direct-to-bank should be free for personal use; document any per-bank exceptions.
- **ToS / legal posture.** Does the bank's PSD2 portal require AISP licensing for the API consumer? (Direct PSD2 access usually does — document per-bank reality.)
- **Fit-for-ledgerboy.** Recommendation: **adopt as plugin** / **defer** / **reject**, with one paragraph of rationale.

## What "done" looks like for this research round

Round 1 has landed. The five per-source decision files exist and have been rewritten to reflect the plugin architecture (see Decisions below). Subsequent rounds add new per-bank PSD2-direct plugins one at a time (each as its own `banking-psd2-<bank>.md` decision file as that bank is researched).

## Decisions

Post-pivot per-source files:

- [`banking-psd2.md`](banking-psd2.md) — per-bank PSD2-direct via Berlin Group NextGenPSD2; aggregators rejected.
- [`banking-fints.md`](banking-fints.md) — roll-our-own FinTS-client plugin (~3,300 LOC, MIT); `hbci4java` rejected on license-gate.
- [`banking-import.md`](banking-import.md) — per-format import plugins (CSV / QIF / OFX / MT940 / CAMT.053); `pw-swift-core` for MT940, `ofx4j` for OFX carried over.
- [`banking-fx.md`](banking-fx.md) — ECB-direct (`eurofxref-daily.xml`) and Bundesbank-direct as separate plugins; Frankfurter rejected (third-party proxy of ECB data).
- [`banking-ebics.md`](banking-ebics.md) — rejected, not shipped; would be plugin-only if reconsidered.

[`main.md`](main.md) Phase X (plugin runtime) lands before Phase B; Phase B+ each implement one plugin on top of the runtime. Phase D's FX cache is now fed by whichever FX plugin(s) the user enables.
