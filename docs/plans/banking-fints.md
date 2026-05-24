# banking-fints — FinTS / HBCI research

## Status: RESEARCH ROUND 1 — decision below

**Decision:** **Defer FinTS to Phase B+ — rely on file-import (CAMT.053 / MT940 / CSV) for German banks in v1; roll-our-own minimal PIN/TAN subset (HKSAL + HKKAZ) is the v2 path if/when account-level live access becomes a must-have.** Rationale at the bottom.

## What it is

FinTS (Financial Transaction Services), formerly HBCI (Home Banking Computer Interface), is *the* German personal-banking protocol. Maintained by Die Deutsche Kreditwirtschaft (DK, formerly ZKA — Zentraler Kreditausschuss). Deployed by every major DE retail bank: Sparkasse, Volksbank/Raiffeisenbank, Commerzbank, Deutsche Bank, ING-DiBa, DKB, Postbank, comdirect. Sister protocol EBICS handles corporate banking; FinTS handles retail.

Protocol shape: structured-segment messages (HBCI v2 / v3 use a colon-and-plus delimited text framing; FinTS v4 added XML but is barely deployed). PIN/TAN dialog opens with a `HKVVB` (Dialoginitialisierung), then issues business segments — `HKSAL` (Saldenabfrage / balance), `HKKAZ` (Kontoumsätze / transactions, returns MT940 inside), `HKCCS` (SEPA transfer submission), etc. Every business transaction that moves money is gated by an SCA TAN (since PSD2, September 2019). Read-only queries (balance, transactions) can use the "decoupled" SCA pathway (one TAN per 90 days, like PSD2 consent windows).

References:

- [FinTS Wikipedia overview](https://en.wikipedia.org/wiki/FinTS)
- [DK FinTS specification index (de)](https://www.fints.org/de/spezifikation/dokumente) — the canonical PDFs (Formals, Messages, Security, Geschäftsvorfälle)
- [phpFinTS Developer Guide](https://github.com/nemiah/phpFinTS/blob/master/DEVELOPER-GUIDE.md) — practitioner-level intro to segments, dialog, TAN flow

## Library candidates

### hbci4java (`org.kapott` / `com.github.hbci4j:hbci4j-core`)

- **SPDX:** **LGPL-2.1-only** — [hbci4java GitHub readme](https://github.com/hbci4j/hbci4java) (license file in the repo). **FAILS the license gate.** (Originally GPLv2, relicensed to LGPL on 2016-05-02 — see the linked GitHub note.)
- **Maintenance:** active, regular releases. Maven Central coordinate `com.github.hbci4j:hbci4j-core`. [Maven Central](https://mvnrepository.com/artifact/com.github.hbci4j/hbci4j-core).
- **Android compat:** runs on JVM; pulls `javax.xml.*` and JCE crypto. Workable on Android with desugaring but not validated.
- **Features:** complete — HKSAL, HKKAZ, HKCCS, chipTAN, pushTAN, smsTAN, photoTAN, HHD, Flickercode, PSD2 SCA. The reference implementation in practice.
- **Verdict:** **REJECTED.** LGPL section 4 demands that consumers be able to relink against a modified version of the library; on Android that requires either dynamic loading (which Android does not really support for JVM `.jar` deps) or shipping object files so end users can rebuild the APK. The ZIA / commercial-attribution analysis would also force us to inventory it as LGPL in `LicensesScreen`, which fails the [`oss-licenses.md`](oss-licenses.md) gate. Out.

### fints4k (`dankito/fints4k`, also `codinux/fints4k`)

- **SPDX:** **AGPL-3.0 OR commercial dual-license** — [GitHub README](https://github.com/dankito/fints4k) ("fints4k is dual licensed as AGPL / commercial software"). **FAILS the license gate** (AGPL is even stricter than LGPL — Sec. 13 network-use trigger plus standard GPLv3 copyleft).
- **Maintenance:** active; Kotlin Multiplatform, explicitly targets Android. Would have been the perfect technical fit.
- **Features:** HKSAL, HKKAZ, SEPA transfers, real-time transfers, chipTAN (manual / Flicker / QR / Matrix), pushTAN, smsTAN, appTAN.
- **Verdict:** **REJECTED on license.** A commercial license would bypass the AGPL, but the cost is unpublished and would land us at "negotiate per-deployment" which is incompatible with a personal F-OSS app shipped via GitHub Releases / Obtainium. Out.

### Subsembly FinTS API (.NET / Java)

- **SPDX:** **Proprietary commercial** — [Subsembly](https://subsembly.com/apidoc/fints_index.html). Token-licensed per deployment. **FAILS the gate.**
- **Verdict:** **REJECTED.**

### libfintx (.NET, `libfintx.FinTS`)

- **SPDX:** LGPL-3.0 — [NuGet](https://www.nuget.org/packages/libfintx.FinTS). Wrong runtime (CLR, not JVM) and wrong license. **REJECTED.**

### python-fints (`raphaelm/python-fints`)

- **SPDX:** LGPL-3.0 — [GitHub](https://github.com/raphaelm/python-fints). Wrong runtime and wrong license. Useful only as a **reference implementation** when reading the spec — the segment classes mirror the FinTS PDFs cleanly. **REJECTED as a dep**, **adopted as documentation cross-reference.**

### lib-fints (`robocode13/lib-fints`, TypeScript)

- **SPDX:** check via repo — [GitHub](https://github.com/robocode13/lib-fints). Wrong runtime (no plausible Kotlin/JVM port). Useful as a reference. **REJECTED as a dep.**

### fints-rs (`svenstaro/fints-rs`)

- **SPDX:** check via repo — Rust, would need JNI. Architecturally hostile to a Kotlin-only single-module Android app. **REJECTED.**

### Search for any Apache/MIT FinTS lib on Maven Central

Grepped Maven Central (`com.github.hbci4j`, `de.adorsys.opba`, `net.codinux.banking`) and GitHub (`fints` topic, ~30 repos as of [fints topic](https://github.com/topics/fints)). Every JVM-class implementation is LGPL or AGPL. There is **no permissively-licensed JVM FinTS client on Maven Central** as of 2026-05.

`de.adorsys.opba:hbci-protocol` is an adorsys repackaging of hbci4java — same LGPL upstream, no relief.

## Sample bank coverage (theoretical, were the gate not a problem)

FinTS via hbci4java / fints4k would reach: **Sparkasse** (all ~370 Sparkassen via DSGV's shared FinTS endpoint), **Volksbank / Raiffeisenbank** (Fiducia & GAD shared endpoint), **Commerzbank**, **Deutsche Bank**, **DKB**, **ING-DiBa**, **comdirect**, **Postbank**, **N26 (limited)**. Each bank's `BLZ` (Bankleitzahl, 8-digit routing code) maps to a FinTS endpoint URL via the public `FinTS-Institute.csv` (Subsembly hosts a maintained mirror, DK publishes the canonical list).

For ledgerboy v1, **CAMT.053 / MT940 import via SAF covers every one of these banks** — every DE bank that speaks FinTS also lets the user download the same data as a file from the web banking portal. The friction differs (per-statement-PDF-download vs. one-shot daily pull) but the coverage is identical.

## Credential shape

If we ever ship FinTS: per-account credentials = `BLZ` + user ID (`UserID`, often the customer number) + PIN. The first dialog negotiates a TAN method (the user picks from the bank's offered list — pushTAN, smsTAN, etc.). The PIN is long-lived; the TAN is one-shot per SCA-required transaction (or per 90-day "decoupled" window for read-only). Stored in the Android Keystore-wrapped credential blob — see [`security-research.md`](security-research.md).

## Sandbox / test bank

- **HBCI4Java test server:** `https://hbci.kreissparkasse-koeln.de/cgi-bin/hbciservlet` historically. No public sandbox bank that mimics SCA.
- **Subsembly FinTS Dummy:** [Subsembly's PDF](https://subsembly.com/download/FinTS.Server.Dummy.pdf) documents a test server but it's tied to their commercial product.
- **Reality:** no good public sandbox. Integration tests against a real bank account on a real BLZ are the only practical path. The user's own bank is the test bench.

## Cost

Free at the protocol level — banks expose FinTS at no cost as part of their PSD2 obligations. No per-call billing. Cost is purely the engineering cost on our side.

## ToS / legal posture

FinTS PSD2 access is the user's own legal right (PSD2 Art. 67 AISP access via the dedicated interface). No commercial licence required for personal-use redistribution. The protocol spec is public. Risk surface is purely on credential storage + correctness of SCA flow.

## Fit for ledgerboy

**Defer.** The license gate is the structural blocker, not the engineering effort. There is no permissively-licensed JVM FinTS client and there will not be one in the next twelve months — the protocol is too small a niche outside the existing LGPL/AGPL projects, and the legal cost of an Apache-fork of LGPL code is forbidding (LGPL forks must stay LGPL).

The two paths forward:

1. **Roll-our-own minimal subset.** Realistic — see "Roll-our-own LOC estimate" below.
2. **Rely on file-import.** v1 ships with CAMT.053 / MT940 / CSV import via SAF. Every DE bank lets the user download these from web banking. The user picks "import statement" once a month, ledgerboy dedups by row-hash. Zero protocol code in the APK.

For v1, path 2 wins because it lets us ship value to DE-bank users without writing or maintaining any FinTS code, and the per-bank UX delta (per-month download vs. one-tap pull) is small enough to defer until the rest of the app is in the user's hands. Path 1 is the v2 work item, gated on real user demand.

## SCA (Strong Customer Authentication) — required for any FinTS implementation

PSD2 mandates SCA on every PIN/TAN session since 2019-09-14 ([keyivr SCA primer](https://www.keyivr.com/us/knowledge/guides/what-is-psd2-and-sca/)). The bank's `HIPINS` (PIN Information Segment) response declares which TAN methods are supported; the client picks one and negotiates with `HKTAN`. TAN media flows we'd need:

- **pushTAN** (Sparkasse pushTAN-App, Commerzbank photoTAN-App push-mode) — out-of-band confirm in the bank's own app. Lowest UX friction, highest deployment.
- **smsTAN** — declining (banks are sunsetting it for cost reasons) but still common at smaller Volksbanken.
- **chipTAN** (USB / optical Flickercode / QR / photoTAN-Matrix-Code) — requires a separate hardware TAN generator. Power-user only.
- **photoTAN** (Commerzbank, Deutsche Bank) — colored Matrix code, separate app.
- **appTAN / decoupled** — for "read-only" (HKSAL/HKKAZ) sessions, the 90-day-window PSD2 carve-out applies — one SCA per 90 days, not per query.

**Minimum viable TAN coverage for v1 of a hypothetical roll-our-own:** pushTAN + decoupled-90-day for HKSAL/HKKAZ. smsTAN as fallback. Skip chipTAN and photoTAN (hardware-dependent, low adoption among the personal-use target).

## Roll-our-own LOC estimate (the v2 path)

Scope: **read-only PIN/TAN session, HKSAL (balance) + HKKAZ (transactions), pushTAN + decoupled SCA, no SEPA submission, no MT940-inside-HKKAZ-response parsing (delegated to the MT940 parser from `banking-import.md`).**

| Component | LOC est. | Notes |
|---|---|---|
| FinTS segment framing (DEG / DE / segment delimiters `:`/`+`/`'`, escape rules) | 400 | Pure text; well documented. Reference [phpFinTS guide](https://github.com/nemiah/phpFinTS/blob/master/DEVELOPER-GUIDE.md). |
| Message envelope (HNHBK / HNHBS / HNVSK / HNVSD encryption envelope) | 300 | PIN/TAN encryption is a stock JCE construction. |
| Dialog state machine (DialogInit `HKVVB` → BPD/UPD parse → business segments → DialogEnd `HKEND`) | 500 | The bank's UPD (User Parameter Data) tells us which accounts the user has + which TAN methods are allowed. |
| HKSAL v5/v6/v7 segment construction + HISAL response parsing | 200 | Three versions in the wild; bank declares the supported max in BPD. |
| HKKAZ v5/v6/v7 segment + HIKAZ response parsing (MT940 payload delegated) | 250 | Likewise. |
| HKTAN v6/v7 + HITANS handshake (TAN method negotiation, second-message flow, decoupled polling) | 600 | The trickiest piece — SCA conformance. |
| BLZ → endpoint resolver (load `FinTS-Institute.csv` from app assets, refresh quarterly) | 100 | DK publishes the CSV. |
| HTTPS transport (OkHttp), cert pinning per bank (DK publishes pins) | 150 | One pin per `BLZ`. |
| Unit tests (golden-file segment encode/decode, dialog state-machine) | 800 | Robolectric-friendly. |
| **Total** | **~3,300 LOC Kotlin** | One careful engineer-month, two with bank-side integration testing. |

This is a real but bounded cost. It's the v2 work item, NOT the v1 path.

## Decision

**v1: defer FinTS entirely.** German-bank users get coverage via [`banking-import.md`](banking-import.md) (CAMT.053 / MT940 / CSV, downloaded from web banking). UX-cost: one-tap-per-month vs one-tap-per-session. Zero protocol code in the APK. Zero license-gate ambiguity.

**v2: roll-our-own minimal PIN/TAN (HKSAL + HKKAZ + pushTAN + decoupled SCA).** ~3,300 LOC Kotlin. Gated on (a) real user demand expressed in an issue or commit, and (b) a stable v1 import pipeline that the FinTS responses can re-use (MT940 parser, NormalizedTransaction dedup).

**Never: adopt hbci4java or fints4k.** LGPL / AGPL fail [`oss-licenses.md`](oss-licenses.md) and the dual-commercial path is incompatible with personal F-OSS distribution.

## Phase B implementation sub-step checklist (placeholder for v2)

If/when the v2 roll-our-own work is greenlit, the sub-steps:

- [ ] **B-FinTS.1** Pull the canonical [DK FinTS spec PDFs](https://www.fints.org/de/spezifikation/dokumente) — Formals, Messages, Security, Geschäftsvorfälle HKSAL/HKKAZ/HKTAN. Cache in `docs/spec/fints/` (gitignored, large PDFs).
- [ ] **B-FinTS.2** Land FinTS segment framing parser + serializer (DE/DEG/segment delimiters, escape rules). Golden-file tests against captured-from-spec sample segments.
- [ ] **B-FinTS.3** Land HNHBK / HNHBS message envelope + HNVSK / HNVSD PIN/TAN encryption envelope. JCE-only crypto, no native deps.
- [ ] **B-FinTS.4** Land DialogInit (HKVVB) → BPD/UPD parse → DialogEnd (HKEND) state machine. Unit tests against captured BPD/UPD samples from python-fints fixtures (LGPL-incompatible to vendor, but reading is fine — re-derive our own test fixtures from the spec).
- [ ] **B-FinTS.5** Land HKTAN v6/v7 handshake (HITANS → method picker → two-step flow → decoupled polling). Wire pushTAN as the canonical happy path; smsTAN as fallback.
- [ ] **B-FinTS.6** Land HKSAL v5/v6/v7 + HISAL response → `Money` (integer minor units).
- [ ] **B-FinTS.7** Land HKKAZ v5/v6/v7 + HIKAZ response. Payload is MT940 — delegate to the MT940 parser from `banking-import.md`. Produce `NormalizedTransaction` rows with stable row-hashes for dedup.
- [ ] **B-FinTS.8** Land BLZ → endpoint resolver (asset-bundled `FinTS-Institute.csv` from DK; quarterly refresh job).
- [ ] **B-FinTS.9** Wire as a `BankConnector` implementation behind the narrow interface from `CLAUDE.md`. Credentials in Android Keystore-wrapped blob per [`security-research.md`](security-research.md).
- [ ] **B-FinTS.10** End-to-end test against the user's own bank. Document the per-bank quirks (every DE bank has at least one — segment-version preference, TAN-method declaration oddities, decoupled-window length variation).
