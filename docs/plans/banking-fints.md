# banking-fints — FinTS / HBCI plugin research

## Status: RESEARCH — recommendation rewritten 2026-05-24 after the connector-plugin architecture lock. The roll-our-own minimal FinTS client is now the **v1 `fints-client` plugin**, not the v2 work item.

> Parent: [`banking-research.md`](banking-research.md) · Architectural authority: [`connector-plugins.md`](connector-plugins.md) · siblings: [`banking-psd2.md`](banking-psd2.md) · [`banking-fx.md`](banking-fx.md) · [`banking-ebics.md`](banking-ebics.md) · [`banking-import.md`](banking-import.md)

## Pivot summary

The earlier version of this file recommended **deferring FinTS to v2** and shipping file-import-only for German retail banks in v1. **That deferral is reversed.** Per [`connector-plugins.md`](connector-plugins.md), every banking source is a plugin off by default; the FinTS protocol covers virtually every DE retail bank (Sparkasse, Volksbank/Raiffeisenbank, Commerzbank, Deutsche Bank, ING-DiBa, DKB, Postbank, comdirect, etc.) with **one** plugin implementation. That coverage breadth makes it the highest-value banking plugin to ship, and it moves to **v1** as the `fints-client` plugin.

The decision against wrapping `hbci4java` / `fints4k` stands — LGPL / AGPL still fail the licence gate, even in a plugin shipped inside the single ledgerboy APK. We roll our own from scratch (~3,300 LOC estimate carried over). The plugin model does not soften the gate.

**Decision:** Ship a roll-our-own minimal FinTS client as the **`fints-client` plugin** in Phase B, on top of the Phase X plugin runtime. Read-only surface (HKSAL balance + HKKAZ transactions + dialog + pushTAN / decoupled SCA). No transfer submission in v1.

## What it is

FinTS (Financial Transaction Services), formerly HBCI (Home Banking Computer Interface), is *the* German personal-banking protocol.

It is the protocol with the broadest single-implementation coverage for German retail banking: one FinTS-client plugin reaches almost every DE retail bank without per-bank dialect plugins (FinTS has bank-level quirks, but they live inside one shared protocol stack, not separate plugins). Maintained by Die Deutsche Kreditwirtschaft (DK, formerly ZKA — Zentraler Kreditausschuss). Deployed by every major DE retail bank: Sparkasse, Volksbank/Raiffeisenbank, Commerzbank, Deutsche Bank, ING-DiBa, DKB, Postbank, comdirect. Sister protocol EBICS handles corporate banking; FinTS handles retail.

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
- **Verdict:** **REJECTED.** LGPL section 4 demands that consumers be able to relink against a modified version of the library; on Android that requires either dynamic loading (which Android does not really support for JVM `.jar` deps) or shipping object files so end users can rebuild the APK. **The plugin model does not soften this.** Per [`connector-plugins.md`](connector-plugins.md), every plugin compiles into the single ledgerboy APK; LGPL in a plugin would infect the whole APK. The licence gate stands as-is for plugins. Out.

### fints4k (`dankito/fints4k`, also `codinux/fints4k`)

- **SPDX:** **AGPL-3.0 OR commercial dual-license** — [GitHub README](https://github.com/dankito/fints4k) ("fints4k is dual licensed as AGPL / commercial software"). **FAILS the license gate** (AGPL is even stricter than LGPL — Sec. 13 network-use trigger plus standard GPLv3 copyleft).
- **Maintenance:** active; Kotlin Multiplatform, explicitly targets Android. Would have been the perfect technical fit.
- **Features:** HKSAL, HKKAZ, SEPA transfers, real-time transfers, chipTAN (manual / Flicker / QR / Matrix), pushTAN, smsTAN, appTAN.
- **Verdict:** **REJECTED on license.** Same plugin-doesn't-soften-the-gate reasoning as hbci4java. A commercial license would bypass the AGPL, but the cost is unpublished and would land us at "negotiate per-deployment" which is incompatible with a personal F-OSS app shipped via GitHub Releases / Obtainium. Out.

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

## Sample bank coverage (the v1 reach of the `fints-client` plugin)

The single `fints-client` plugin reaches: **Sparkasse** (all ~370 Sparkassen via DSGV's shared FinTS endpoint), **Volksbank / Raiffeisenbank** (Fiducia & GAD shared endpoint), **Commerzbank**, **Deutsche Bank**, **DKB**, **ING-DiBa**, **comdirect**, **Postbank**, **N26 (limited)**. Each bank's `BLZ` (Bankleitzahl, 8-digit routing code) maps to a FinTS endpoint URL via the public `FinTS-Institute.csv` (Subsembly hosts a maintained mirror, DK publishes the canonical list).

That coverage is the load-bearing argument for moving this plugin to v1. The per-bank PSD2 plugins ([`banking-psd2.md`](banking-psd2.md)) are per-bank Kotlin modules each reaching one bank; the `fints-client` plugin is **one** Kotlin module reaching every DE retail bank that publishes a FinTS endpoint, which is essentially all of them.

For DE-bank users who would rather not run a live connector at all, the file-import plugins ([`banking-import.md`](banking-import.md)) cover the same banks via CAMT.053 / MT940 / CSV downloads from the web banking portal. Per the plugin architecture, the user opts in to whichever combination they prefer; the `fints-client` plugin is one of several options, never on by default.

## Credential shape

Per the plugin's `ConfigScreen()`, the user enters:

- **`BLZ`** (Bankleitzahl, 8-digit routing code) — drives the FinTS endpoint URL lookup via the bundled `FinTS-Institute.csv`.
- **`UserID`** (often the customer / login number).
- **PIN** (long-lived).

The first dialog negotiates a TAN method (the user picks from the bank's offered list — pushTAN, smsTAN, etc.). The PIN is long-lived; the TAN is one-shot per SCA-required transaction (or per 90-day "decoupled" window for read-only). Credentials are stored encrypted via the Android-Keystore-wrapping helper the host provides to plugins — see [`security-research.md`](security-research.md) and [`connector-plugins.md`](connector-plugins.md) (X.3 / X.8).

## Sandbox / test bank

- **HBCI4Java test server:** `https://hbci.kreissparkasse-koeln.de/cgi-bin/hbciservlet` historically. No public sandbox bank that mimics SCA.
- **Subsembly FinTS Dummy:** [Subsembly's PDF](https://subsembly.com/download/FinTS.Server.Dummy.pdf) documents a test server but it's tied to their commercial product.
- **Reality:** no good public sandbox. Integration tests against a real bank account on a real BLZ are the only practical path. The user's own bank is the test bench.

## Cost

Free at the protocol level — banks expose FinTS at no cost as part of their PSD2 obligations. No per-call billing. Cost is purely the engineering cost on our side.

## ToS / legal posture

FinTS PSD2 access is the user's own legal right (PSD2 Art. 67 AISP access via the dedicated interface). No commercial licence required for personal-use redistribution. The protocol spec is public. Risk surface is purely on credential storage + correctness of SCA flow.

## Fit for ledgerboy

**Adopt as the `fints-client` plugin in v1.** The licence gate rules out wrapping any third-party FinTS library, so we roll our own ~3,300 LOC minimal client. The plugin architecture means this work targets one `ConnectorPlugin` implementation, off by default, that any user with a DE retail bank account can enable; users who prefer not to live-connect can use the file-import plugins ([`banking-import.md`](banking-import.md)) instead.

The "rely on file-import only" path remains available **per user**, not as a project-wide choice — that's the point of the plugin model. The user picks their stack.

## SCA (Strong Customer Authentication) — required for any FinTS implementation

PSD2 mandates SCA on every PIN/TAN session since 2019-09-14 ([keyivr SCA primer](https://www.keyivr.com/us/knowledge/guides/what-is-psd2-and-sca/)). The bank's `HIPINS` (PIN Information Segment) response declares which TAN methods are supported; the client picks one and negotiates with `HKTAN`. TAN media flows we'd need:

- **pushTAN** (Sparkasse pushTAN-App, Commerzbank photoTAN-App push-mode) — out-of-band confirm in the bank's own app. Lowest UX friction, highest deployment.
- **smsTAN** — declining (banks are sunsetting it for cost reasons) but still common at smaller Volksbanken.
- **chipTAN** (USB / optical Flickercode / QR / photoTAN-Matrix-Code) — requires a separate hardware TAN generator. Power-user only.
- **photoTAN** (Commerzbank, Deutsche Bank) — colored Matrix code, separate app.
- **appTAN / decoupled** — for "read-only" (HKSAL/HKKAZ) sessions, the 90-day-window PSD2 carve-out applies — one SCA per 90 days, not per query.

**Minimum viable TAN coverage for the v1 `fints-client` plugin:** pushTAN + decoupled-90-day for HKSAL/HKKAZ. smsTAN as fallback. Skip chipTAN and photoTAN (hardware-dependent, low adoption among the personal-use target). A v1.1 sub-phase can add chipTAN / photoTAN if user demand surfaces.

## Roll-our-own LOC estimate (the v1 `fints-client` plugin)

Scope: **read-only PIN/TAN session, HKSAL (balance) + HKKAZ (transactions), pushTAN + decoupled SCA, no SEPA submission, no MT940-inside-HKKAZ-response parsing (delegated to the MT940 parser from [`banking-import.md`](banking-import.md)'s `Mt940Importer` — same parser, called from the `fints-client` plugin instead of from a SAF-imported file).**

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

This is a real but bounded cost. It's the v1 `fints-client` plugin work, gated on Phase X (plugin runtime) landing first.

## Plugin shape

Per [`connector-plugins.md`](connector-plugins.md), the `fints-client` plugin lives at `app/src/main/java/com/eight87/ledgerboy/plugins/fints-client/`, implements `ConnectorPlugin`, and is registered in `PluginManifest`. Surface:

- **`id = "fints-client"`** · **`category = Banking`** · `displayName` localised ("FinTS direct (German retail banks)") · `licenseSpdx = "MIT"` (our own code) · `sourceUrl = "https://www.fints.org/"`.
- **`description`**: one paragraph naming FinTS and listing the supported bank families (Sparkasse, Volksbank/Raiffeisenbank, Commerzbank, DKB, ING-DiBa, Comdirect, Postbank, etc., reached via the bundled `FinTS-Institute.csv`).
- **`privacyStatement`**: "Data leaves the device to: your bank's FinTS endpoint (looked up from your BLZ). No third party. Bank sees: your BLZ, your UserID, your PIN, your TAN responses, your transaction-query parameters."
- **State machine**: `Disabled` by default. User enables → `Enabled`. User runs `ConfigScreen()`, enters BLZ + UserID + PIN, picks a TAN method → `Configured`. User taps "Test connection" → plugin runs one HKSAL via the chosen TAN method → on success → `Active`.
- **`ConfigScreen()`**:
  - BLZ field (numeric, validated against the bundled institute list).
  - UserID field.
  - PIN field (masked).
  - TAN-method dropdown (populated from the bank's `HIPINS` after a one-shot dialog probe).
  - "Test connection" button → one HKSAL → success/failure.
- **`fetch(window)`**: runs HKKAZ for each account on the user's UPD (User Parameter Data), respecting the decoupled-SCA 90-day window. Emits `List<NormalizedTransaction>` via the shared `Mt940Importer` (the HKKAZ response payload is MT940 — same parser as the SAF file-import plugin).
- **Network guard**: all FinTS HTTPS calls flow through the host's `PluginNetworkGuard` (per [`connector-plugins.md`](connector-plugins.md) X.4). Disabled plugin = zero packets.

## Decision

**Adopt as the `fints-client` plugin in v1.** Roll our own minimal read-only PIN/TAN client (~3,300 LOC Kotlin: HKSAL + HKKAZ + pushTAN + decoupled SCA + segment framing + envelope + dialog). Wrapped as one `ConnectorPlugin` under the plugin runtime from Phase X.

**Never: adopt hbci4java or fints4k.** LGPL / AGPL fail [`oss-licenses.md`](oss-licenses.md). The plugin model does not soften this — every plugin compiles into the single ledgerboy APK, so an LGPL plugin would infect the whole APK. The gate stands.

## Phase X (plugin runtime) — gating

Per [`connector-plugins.md`](connector-plugins.md), Phase X lands before the `fints-client` plugin. The runtime supplies:

- `ConnectorPlugin` interface + state machine (X.1).
- `PluginManifest` registration (X.2).
- Per-plugin DataStore + encrypted-Room credential helpers (X.3).
- `PluginNetworkGuard` OkHttp interceptor (X.4) — every FinTS HTTPS call flows through this.
- `PluginScheduler` (X.5) — for opt-in background HKKAZ refresh, respecting the per-plugin background-sync opt-in (default OFF).
- Settings → Plugins UI surfaces (X.6 / X.7 / X.8) — the user enables the plugin, configures it, runs Test connection from here.

**Do not start the `fints-client` plugin sub-phases below until Phase X is complete.**

## Phase B implementation sub-step checklist — the `fints-client` plugin

> All sub-phases gated on Phase X (plugin runtime) being complete.

- [ ] **B.fints-client.1** Pull the canonical [DK FinTS spec PDFs](https://www.fints.org/de/spezifikation/dokumente) — Formals, Messages, Security, Geschäftsvorfälle HKSAL/HKKAZ/HKTAN. Cache in `docs/spec/fints/` (gitignored, large PDFs).
- [ ] **B.fints-client.2** Land FinTS segment framing parser + serializer (DE/DEG/segment delimiters, escape rules). Golden-file tests against captured-from-spec sample segments. ~400 LOC.
- [ ] **B.fints-client.3** Land HNHBK / HNHBS message envelope + HNVSK / HNVSD PIN/TAN encryption envelope. JCE-only crypto, no native deps. ~300 LOC.
- [ ] **B.fints-client.4** Land DialogInit (HKVVB) → BPD/UPD parse → DialogEnd (HKEND) state machine. Unit tests against captured BPD/UPD samples from python-fints fixtures (LGPL-incompatible to vendor, but reading is fine — re-derive our own test fixtures from the spec). ~500 LOC.
- [ ] **B.fints-client.5** Land HKTAN v6/v7 handshake (HITANS → method picker → two-step flow → decoupled polling). Wire pushTAN as the canonical happy path; smsTAN as fallback. ~600 LOC.
- [ ] **B.fints-client.6** Land HKSAL v5/v6/v7 + HISAL response → `Money` (integer minor units). ~200 LOC.
- [ ] **B.fints-client.7** Land HKKAZ v5/v6/v7 + HIKAZ response. Payload is MT940 — delegate to the `Mt940Importer` from [`banking-import.md`](banking-import.md). Produce `NormalizedTransaction` rows with stable row-hashes for dedup. ~250 LOC.
- [ ] **B.fints-client.8** Land BLZ → endpoint resolver (asset-bundled `FinTS-Institute.csv` from DK; quarterly refresh job). ~100 LOC.
- [ ] **B.fints-client.9** Implement `FintsClientPlugin : ConnectorPlugin`. Register in `PluginManifest`. Credentials in Android Keystore-wrapped blob per [`security-research.md`](security-research.md), accessed via the host's plugin credential helper (per [`connector-plugins.md`](connector-plugins.md) X.3).
- [ ] **B.fints-client.10** Plugin `ConfigScreen()`: BLZ / UserID / PIN fields + TAN-method dropdown + Test-connection button. All strings in `values/strings.xml` per CLAUDE.md i18n discipline.
- [ ] **B.fints-client.11** Privacy statement string + endpoint enumeration for Settings → Plugins → Network usage.
- [ ] **B.fints-client.12** Robolectric tests: segment encode/decode golden files, dialog state machine, HKSAL/HKKAZ round-trip, BLZ resolver. ~800 LOC of tests.
- [ ] **B.fints-client.13** End-to-end test against the user's own bank via the AVD (per CLAUDE.md UI verification rule). Document the per-bank quirks (every DE bank has at least one — segment-version preference, TAN-method declaration oddities, decoupled-window length variation).
