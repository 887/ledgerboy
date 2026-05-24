# ledgerboy — EBICS connector research

## Status: RESEARCH COMPLETE — Decision: **REJECT for v1, defer indefinitely; fall back to FinTS for DACH personal banking**

> Parent: [`banking-research.md`](banking-research.md) · Sibling: `banking-fints.md` (parallel research).
> TL;DR: Every actively-maintained EBICS Java library is **LGPL-2.1**, which **fails the ledgerboy license gate**. The two permissively-licensed alternatives are foreign-language (Node MIT, Go MIT-structs-only) and would require a full Kotlin port. **And** EBICS in Germany is a business-banking surface — Sparkasse / Volksbank / DKB / ING-DiBa do **not** offer EBICS to personal customers. The user, in DE, will not reach a single one of their own personal accounts via EBICS. Connector reaches roughly zero target users on day one. **Reject for v1.** FinTS (parallel agent) is the correct DACH personal-banking connector.

---

## What is it?

EBICS (Electronic Banking Internet Communication Standard) is a SOAP/XML over HTTPS protocol used by banks in the DACH region (DE, CH, AT) and France for secure exchange of payment orders and account statements. The protocol is built around per-user RSA key pairs (three keys: A005 / signature, X002 / authentication, E002 / encryption) where the user generates the keys client-side, sends an "INI letter" (a printed PDF carrying the public-key fingerprints) by physical mail to the bank, the bank manually toggles the user's key into ACTIVE state, and from then on the client signs every request with the A005 key. Account statements come back as MT940 (legacy SWIFT format) or CAMT.053 (ISO 20022 XML). EBICS exists in versions 2.4, 2.5 (H004) and 3.0 (H005); German banks are obliged to offer 3.0 since November 2021 ([SAP community](https://community.sap.com/t5/financial-management-q-a/transitioning-from-ebics-2-5-to-ebics-btf-3-0-in-sap-multi-bank/qaq-p/14279798)). In Germany, the standard is **explicitly aimed at business customers** — Sparkassen's own marketing page says EBICS is the "binding standard in communication between companies and German credit institutions" ([Sparkasse](https://www.sparkasse.de/fk/produkte/konto-und-zahlungsverkehr/electronic-banking/ebics.html)). Private retail customers in DE use FinTS instead. In Switzerland (PostFinance, Raiffeisen, UBS) EBICS is more widely available to small-business and some private customers; in France EBICS-T is the standard corporate channel.

---

## Library candidates

### 1. `ebics-java/ebics-java-client` (the canonical fork)

- **SPDX:** **LGPL-2.1** ([repo](https://github.com/ebics-java/ebics-java-client)) — **FAILS license gate**
- **Latest release:** v2.0.0, 2025-11-19 (active)
- **EBICS version:** 3.0 supported per repo description
- **Dialect coverage:** "Support for French, German and Swiss banks" — Austrian not mentioned
- **Pure Java:** Yes, 100% Java per GitHub language stats
- **INI letter generation:** Yes (`--ini` CLI flag generates the printable letter)
- **Crypto:** Bouncy Castle (pure Java; pulled transitively via `bcprov-jdk15on`) — Android-compatible
- **JDK floor:** JDK 8+ historically; v2.x likely raises this — would need to verify against minSdk 28 desugaring
- **Verdict:** **Rejected on license.** LGPL-2.1's "library exception" is famously ambiguous on static linking, and bundling LGPL code in an Android APK (which is effectively static linking — Android doesn't have dynamic-library swap-out the way desktop GNU/Linux does) is the textbook compatibility problem. The ledgerboy [`oss-licenses.md`](oss-licenses.md) gate is **no LGPL**, period. No exception for "but it's the only one."

### 2. `spaced/ebics-web-client`

- **SPDX:** **LGPL-2.1** ([repo](https://github.com/spaced/ebics-web-client)) — **FAILS license gate**
- **Mixed Kotlin (49%) + Java (29%) + Vue/TS frontend** — interesting because the core API was refactored to Kotlin, but it's a Spring-Boot web app, not a library
- **EBICS version:** 2.5 (H004) + 3.0 (H005)
- **Crypto:** `bcprov-jdk15on`
- **Verdict:** **Rejected on license.** Also wrong shape (web service, not embeddable library) — even if license matched we'd be ripping out the Spring layer.

### 3. `element36-io/ebics-java-client`

- **SPDX:** **LGPL-2.1** ([Maven Central](https://central.sonatype.com/artifact/io.github.element36-io/ebics-cli/1.3)) — **FAILS license gate**
- **Maven coordinates:** `io.github.element36-io:ebics-cli`, latest version 1.5, last published 2021-05 (per Maven Central timestamp ts=1622109408000 → 2021-05-27) — **dormant by maintenance gate** (>18 months since last release)
- **Verdict:** **Rejected on license AND maintenance.**

### 4. `QJonny/ebics-java-library`

- **SPDX:** **LGPL-2.1** — **FAILS license gate**
- 121 commits, no recent activity visible
- **Verdict:** **Rejected on license.**

### 5. `kbank4j`

- **Cannot find under that name** on GitHub or Maven Central in 2026. The seed's "kbank4j" appears to be a misremembered or stale reference. If a future agent finds an actual `kbank4j` repository, re-evaluate — but as of this research round, treat as nonexistent.
- **Verdict:** **N/A — does not exist (or is too obscure to find).**

### 6. `node-ebics/node-ebics-client` (cross-language reference, not for direct use)

- **SPDX:** **MIT** ([repo](https://github.com/node-ebics/node-ebics-client)) — passes license gate
- **Latest:** v4.1.0, 2024-04-30 (active enough)
- **INI letter:** Yes (`examples/bankLetter.js`)
- **EBICS version:** Aims for "100% ISO 20022 compliant" — version coverage not explicit
- **Verdict:** **Useful as a reference for a roll-your-own port**, but is Node.js — not directly usable from an Android Kotlin app. Cost of porting would be high (the lib is ~4 years of evolution).
- **Footnote:** Uses RSA_PKCS1_PADDING — modern Node requires `--security-revert=CVE-2023-46809` to run it. Whatever we port from it must use OAEP / PSS where the EBICS spec allows.

### 7. `openyard/ebics` (Go)

- **SPDX:** **MIT** ([repo](https://github.com/openyard/ebics)) — passes license gate
- **Scope:** XML structs only (EBICS 2.5 + 3.0 directories + XSDs). **Not a full client** — no transport, no crypto, no key management, no INI flow.
- 11 commits total; effectively a starter kit.
- **Verdict:** Useless as a library, but the embedded XSDs are public-domain anyway (EBICS spec is published openly at [ebics.org](https://www.ebics.org/)). A roll-your-own port would just consume the XSDs directly, not this repo.

### 8. `ebics-api/ebics-client-php` (PHP — reference only)

- **SPDX:** **MIT** ([repo](https://github.com/ebics-api/ebics-client-php)) — passes license gate
- **EBICS versions:** 2.4, 2.5, 3.0
- **INI letter:** Yes, generates as PDF via an `EbicsBankLetter` class
- **Verdict:** **The best architectural reference for a hypothetical Kotlin port.** Mature, MIT, supports 3.0, clean separation of key-management / orders / models. If the roll-your-own path is ever taken, this is what to read first.

### 9. Roll-your-own EBICS subset (read-only, statement download)

- **What it would cover:** HEV (version probing) → INI / HIA (key submission) → HPB (bank-key download once activated) → HAA (available order types) → C53 / Z53 / STA (statement download). Skip HCT / HCE / FUL (payment submission), skip HCA / HCS / HVx (order management).
- **Crypto surface:** RSA-2048 keys (T/X/E ladder), AES-128 for transaction-key encryption, SHA-256. Pure Java via Bouncy Castle — already Android-supported, no native deps.
- **XML surface:** XSDs are openly published at [ebics.org](https://www.ebics.org/de/home). Generate Kotlin data classes via `kotlinx.serialization-xml` or hand-roll via `XmlPullParser`.
- **Wire surface:** SOAP-over-HTTPS, signed XML body, base64 payloads, optional zlib compression. ~10–15 ktoclient request/response shapes for the read-only subset.
- **Effort estimate (eyeballed):** 4–8 weeks of focused work for one engineer to a sandbox-passing v1, plus another 2–4 weeks of per-bank dialect fixing. **High risk** (banking crypto bugs are the worst kind), **moderate test coverage** (libeufin sandbox available, see below), **zero target users** for ledgerboy's actual user (the user is in DE, and no DE personal account uses EBICS).
- **Verdict:** **Reject for v1.** The cost/benefit is upside-down: high effort, high risk, zero user reach in the user's actual market.

---

## Concrete EBICS-specific questions (from seed)

### 1. EBICS 3.0 vs 2.5 coverage

German banks are **obliged to offer 3.0 since November 2021** ([SAP community](https://community.sap.com/t5/financial-management-q-a/transitioning-from-ebics-2-5-to-ebics-btf-3-0-in-sap-multi-bank/qaq-p/14279798)) but cross-compatibility with 2.5 will remain essential "for the coming years" ([Generix](https://www.generixgroup.com/en/blog/ebics-30-bank-data-transfer)) — banks must offer **both** versions simultaneously. Swiss banks are slower; many PostFinance / Raiffeisen / Maerki Baumann deployments are still 2.5 first ([GNU Taler libeufin docs](https://docs.taler.net/libeufin/setup-ebics-at-postfinance.html) — libeufin tests against PostFinance EBICS 3.0 specifically, suggesting that path is at least available). Austrian (Raiffeisen, Erste) and French (Crédit Agricole, BNP) banks vary by institution. **Practical guidance for a hypothetical ledgerboy EBICS connector:** support 3.0 first, fall back to 2.5 via the HEV (Host Environment Version) probe. The canonical `ebics-java-client` does this.

### 2. Country dialect handling

EBICS has DE / CH / FR / AT order-type vocabularies that overlap heavily but diverge on edge cases (e.g. French banks require `IS_CERTIFIED=true` for 3.0; Swiss banks use additional order types like Z01 / Z53 / Z54 for ISO 20022; Austrian banks lean heavily on the DE vocabulary). The `ebics-java` fork advertises DE+CH+FR but **not AT** explicitly. The `ebics-client-php` library handles 3.0 + French via the `IS_CERTIFIED` flag. A roll-your-own implementation would need to pick one dialect first (DE if we cared about DE-business; CH if we cared about Swiss-personal) and grow others as users actually hit them — premature multi-dialect support is a dead-end.

### 3. TLS pinning per bank

None of the surveyed libraries expose explicit per-bank TLS pinning hooks. They rely on the JVM's default `SSLContext` and the system trust store. For Android, this means the system CA store — which is fine for major banks (Deutsche Bank, Commerzbank, PostFinance all use mainstream CAs) but offers no defence against CA compromise. **To add pinning on Android we'd need to wrap the HTTP transport in OkHttp with a `CertificatePinner` per host.** This is straightforward but every surveyed library bakes in `HttpURLConnection`-style transport — pinning means patching the lib (further reason to roll our own if we ever wanted pinning to be first-class).

### 4. Crypto — pure Java or native?

**All evaluated Java libraries use Bouncy Castle (`bcprov-jdk15on`) — pure Java, Android-compatible.** EBICS uses RSA-2048 (T = transport key, X = authentication key, E = encryption key, plus A = signature key) and AES-128 for session-key wrapping. None of this requires native code. Android-specific gotchas: the bundled Bouncy Castle on Android is an older, partial fork and can shadow class-loading order — the standard mitigation is to register a fresh BC provider at app startup via `Security.insertProviderAt(BouncyCastleProvider(), 1)` (proven pattern from tonearmboy / whisperboy). **No EBICS lib is the source of an Android-incompatibility risk here.**

### 5. INI letter generation

The bank requires the user to print a piece of paper carrying their three public-key fingerprints (SHA-256 hex), sign it physically, and mail it. Both `ebics-java-client` (Java) and `node-ebics-client` (Node) generate this letter — the Java client via the `--ini` CLI flag, the Node client via `examples/bankLetter.js`. The PHP `ebics-client-php` library generates a **PDF** via an `EbicsBankLetter` class. For a hypothetical ledgerboy connector this would be **a small templated PDF** — we could reuse the pageboy PDF stack (researched in parallel) or fall back to rendering an HTML letter to PDF via Android's `PdfDocument` + WebView. The content is ~1 page: header, customer ID, EBICS host, three key fingerprints in hex with 8-char groupings, signature/date fields. ~200 LOC of generation, mostly layout. **Not a blocker.**

### 6. Bank-side activation flow

After the user mails the INI letter, **a human at the bank checks the fingerprints, manually toggles the customer's key into ACTIVE state, then the customer can fetch the bank's own public keys via HPB** (and from then on every request is bilaterally signed). The lag is **anywhere from 1 to 14 business days** depending on the bank. The UX flow for ledgerboy would be:

1. User generates keys on device → INI letter rendered as PDF → user prints + mails.
2. App shows "Awaiting bank activation" state, polls HEV / HPB on a schedule (once an hour is plenty — this is a multi-day wait).
3. On first successful HPB, app stores bank public keys (Strongbox-wrapped same as user's own keys), advances to "Active" state, surfaces "Run first statement download" CTA.
4. From then on, periodic C53 / Z53 / STA pulls feed `NormalizedTransaction` rows.

This is a heavy UX surface — multi-day onboarding interleaved with physical mail is something a personal-finance app needs to handle gracefully (push notifications when polling flips to active, clear status copy, retry-friendly state machine). Doable, but expensive in dev time for a connector that reaches zero users in our actual market.

### 7. PSD2 / SCA awareness

PSD2 SCA in EBICS 3.0 manifests via the distinct-T/distinct-TS signature class system (T = transport-only, no payment; TS = transport + signature, can authorise payments) and the H005 schema's user-permission grants. **For statement download (the read-only AISP-equivalent surface) the EBICS-T flow is sufficient — no second factor required at runtime once the key is active**, because the initial INI letter + bank-side activation is itself the strong customer authentication ceremony (knowledge=passphrase locally + possession=key + inherence=biometric=optional Strongbox biometric prompt). This makes EBICS-T attractive for a read-only app: **no per-session second-factor friction**, unlike FinTS where every session can demand a TAN. The trade-off is the multi-day onboarding (point 6). The surveyed libraries do not call this out specifically — they implement the protocol, the SCA-shape is implicit in the choice of signature class.

### 8. Test bank / sandbox availability

**LibEuFin** ([docs.taler.net/libeufin](https://docs.taler.net/libeufin/index.html)) ships a free open-source EBICS sandbox: `docker-compose up` brings up a fake bank speaking EBICS 2.5 + 3.0, accepts INI letters automatically (no manual activation lag), and round-trips MT940 / CAMT.053. Default endpoints `http://localhost:5016` (sandbox) / `http://localhost:5000` (nexus). **LibEuFin itself is AGPL**, but that's the *server* — we don't link to it, we just run it as a black-box test target, same way we'd run a mock HTTPS server. **For ledgerboy smoke tests, this is the right sandbox.** Additionally, `pytest-ebics-sandbox` on PyPI packages this as a Python test fixture if a pure-CI path is wanted later. PostFinance also operates a public EBICS test platform reachable from libeufin tutorials. **Commercial sandboxes** (Business-Logics BL Banking demo, EBICS Box) exist but are not needed for an indie/personal project.

---

## Sample bank coverage (the load-bearing constraint)

For **Germany**, the user's actual market, EBICS is **business-banking only** at every major bank — Sparkasse, Volksbank, DKB, Commerzbank, Deutsche Bank, ING-DiBa all gate EBICS behind a business-customer account contract ([Sparkasse marketing](https://www.sparkasse.de/fk/produkte/konto-und-zahlungsverkehr/electronic-banking/ebics.html), [DKB business](https://www.dkb.de/geschaeftskunden/electronic-banking), [Berliner Volksbank](https://www.berliner-volksbank.de/firmenkunden/produkte-und-loesungen/ebics.html)). A retail consumer (the ledgerboy user) cannot enrol for EBICS at their normal personal Girokonto. **FinTS is the connector** for DE personal banking. **Practical reach of an EBICS connector for the user's actual accounts: zero.**

For **Switzerland**, EBICS reach for personal customers is better (PostFinance offers EBICS to retail customers with E-Finance Plus; some cantonal banks offer it). For **Austria**, also business-leaning (Raiffeisen, Erste). For **France**, EBICS-T is the standard corporate channel but rare for personal accounts.

**ledgerboy is a personal-finance app for a DE user.** EBICS is the wrong tool for that surface area. Build FinTS first; revisit EBICS only if the user opens a business account or moves to CH/AT.

---

## Credential shape

- Three RSA-2048 key pairs generated client-side (A005 signature, X002 authentication, E002 encryption).
- Customer ID + User ID + Partner ID + Host ID (issued by the bank when the EBICS contract is signed).
- EBICS host URL (per-bank, e.g. `https://ebics.deutsche-bank.de/ebicsweb/ebicsweb` for DE, `https://isotest.postfinance.ch/ebicsweb/ebicsweb` for PostFinance sandbox).
- INI letter — printed PDF carrying the SHA-256 fingerprints of the three public keys, signed by hand, mailed to the bank.
- Optional passphrase encrypting the keys on device (in addition to Strongbox key-wrapping).

Storage: keys live in Room (or a small dedicated key blob), wrapped via Android Keystore (Strongbox where available — see [`security-research.md`](security-research.md)). Customer/User/Partner/Host IDs and host URL live in Room. Passphrase, if used, gates key decryption per-session.

---

## Cost

- **Library:** free (all options are FOSS, license being the load-bearing axis).
- **Bank-side:** EBICS contracts in DE typically cost **EUR 5–50 per month** as part of a business banking package — a personal customer cannot buy this standalone. This alone reinforces the "wrong tool for this app" verdict.
- **Per-transaction:** typically free within the EBICS contract; some banks meter SEPA submission separately. Read-only statement download is generally bundled.

---

## ToS / legal posture

EBICS itself has no ToS — it's an open standard published by [Die Deutsche Kreditwirtschaft](https://www.ebics.de/). The bank-side contract (per-bank, per-customer) does impose ToS — typically anti-fraud clauses, retention of communication logs, indemnity for misuse of keys. For a personal-finance app whose user has an EBICS contract: nothing in those ToS bars an indie tool from talking to the bank; the keys belong to the user. No redistribution issue — we ship the protocol implementation, not a service.

---

## Fit-for-ledgerboy

**REJECT for v1.** Three independent reasons, any one of which would justify the call:

1. **License gate fails** for every actively-maintained Java/Kotlin EBICS lib (all LGPL-2.1). The only permissively-licensed options are foreign-language (Node MIT, PHP MIT) and would require a months-long pure port.
2. **Bank coverage in user's market is zero.** Every DE retail bank (Sparkasse, Volksbank, DKB, Commerzbank, Deutsche Bank, ING-DiBa) gates EBICS behind a business contract. The user cannot use EBICS to reach their personal Girokonto.
3. **The right connector for DACH personal banking is FinTS** (parallel agent's research file). EBICS competes for engineering time with FinTS at zero added user benefit *for this user, this app, this market*.

**Defer indefinitely.** Revisit if and only if (a) the user opens a business account that exposes an EBICS endpoint, *and* (b) a permissively-licensed (MIT/Apache/BSD/MPL) Kotlin EBICS library appears, *or* (c) ledgerboy's user base expands to Swiss retail customers (PostFinance) where EBICS-for-personal is real.

---

## Decision

**Connector:** none — EBICS rejected for v1.
**Fallback:** FinTS for DACH personal banking (see parallel agent's `banking-fints.md`).
**Library pick (if ever revisited):** roll-your-own Kotlin port modelled on the architecture of `ebics-api/ebics-client-php` (MIT, mature, supports 3.0, generates INI PDF), with Bouncy Castle for crypto, OkHttp for transport (with `CertificatePinner` for per-bank pinning), and `XmlPullParser` for the SOAP envelope.
**Minimum v1 surface (if ever revisited):** read-only only — HEV → INI / HIA → HPB → HAA → C53 / Z53 / STA. No payment submission (HCT / HCE / FUL), no order management (HCA / HCS / HVx). Statement download only, emitting `Money(long, currency)` rows via the `BankConnector` interface.
**Test target (if ever revisited):** libeufin sandbox via Docker for unit/integration tests; PostFinance EBICS 3.0 test platform for end-to-end smoke.

---

## Phase B EBICS implementation sub-step checklist

> **Status: blocked — gated on "revisit EBICS" trigger above. Do not pick up these steps until the trigger fires.**

If the trigger fires (permissive Kotlin EBICS lib appears, user moves to CH retail, or user opens a DE business account), pick up:

- [ ] **B-ebics.1** Re-survey the EBICS Java/Kotlin ecosystem on Maven Central — confirm whether a permissive (MIT/Apache/BSD/MPL) library has appeared since this research round. If yes, re-evaluate; if no, proceed to B-ebics.2.
- [ ] **B-ebics.2** Spin up libeufin sandbox locally (`docker-compose up` against `git.taler.net/libeufin.git`); verify reachable from a smoke-test JVM harness.
- [ ] **B-ebics.3** Draft the Kotlin port skeleton: `EbicsClient` interface, `EbicsKeySet` value class wrapping the three RSA pairs, `EbicsHost` config record.
- [ ] **B-ebics.4** Implement HEV (version probe) — smallest end-to-end request, validates SOAP envelope + transport.
- [ ] **B-ebics.5** Implement RSA-2048 key generation via Bouncy Castle (`KeyPairGenerator.getInstance("RSA", "BC")` after `Security.insertProviderAt(BouncyCastleProvider(), 1)`).
- [ ] **B-ebics.6** Implement Android Keystore key-wrapping for the three private keys (Strongbox-preferred, fallback to TEE) per `security-research.md`.
- [ ] **B-ebics.7** Implement INI letter PDF generator (1-page template, SHA-256 fingerprints, customer ID, host, signature/date fields). Reuse pageboy PDF stack if available, else `android.graphics.pdf.PdfDocument`.
- [ ] **B-ebics.8** Implement INI / HIA order submission (sends public keys + INI letter generation triggers).
- [ ] **B-ebics.9** Implement HPB (bank public-key download once activated) + activation-polling state machine (HEV check on a 1-hour cadence with WorkManager).
- [ ] **B-ebics.10** Implement HAA (available order types) — informs which statement-download order types are valid for this bank.
- [ ] **B-ebics.11** Implement C53 (CAMT.053) statement download + zlib decompression + ISO 20022 XML parsing into `NormalizedTransaction` rows emitting `Money(long, currency)`.
- [ ] **B-ebics.12** Implement Z53 (Swiss CAMT.053 variant) for PostFinance / Swiss bank coverage.
- [ ] **B-ebics.13** Implement STA (MT940 legacy SWIFT statement) for banks that haven't rolled CAMT.053.
- [ ] **B-ebics.14** Wire OkHttp `CertificatePinner` per known bank host (PostFinance, GLS, etc.) — pin SHA-256 of the leaf cert with a fallback pin for the issuing intermediate.
- [ ] **B-ebics.15** Smoke-test the full flow against libeufin sandbox in Robolectric.
- [ ] **B-ebics.16** Smoke-test the full flow against PostFinance EBICS 3.0 test platform on the AVD + a real phone (Strongbox path).
- [ ] **B-ebics.17** Register `EbicsConnector : BankConnector` in `AppGraph`; expose via the connector-selection UI behind a feature flag.
- [ ] **B-ebics.18** Update `oss-licenses.md` and `LicensesScreen` with the EBICS library SPDX (whichever was chosen in B-ebics.1) and the Bouncy Castle SPDX.
- [ ] **B-ebics.19** Tick `main.md` Phase B sub-steps; mark `banking-ebics.md` `## Status: ✅ DONE` with the jj change ID.

> **Note:** This checklist exists as a future map, not a current commitment. The Decision above is **REJECT for v1**. Current Phase B work focuses on FinTS (DE personal) + GoCardless PSD2 (EU broad) + file import (universal fallback). Revisit this file only on a trigger.
