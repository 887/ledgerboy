# banking-import — file-import plugins (CSV / QIF / OFX / MT940 / CAMT.053)

## Status: RESEARCH — recommendation rewritten 2026-05-24 after the connector-plugin architecture lock. Each format is its own plugin off by default; CSV further spawns per-bank-dialect sub-plugins. Library picks carry over.

> Parent: [`banking-research.md`](banking-research.md) · Architectural authority: [`connector-plugins.md`](connector-plugins.md) · siblings: [`banking-psd2.md`](banking-psd2.md) · [`banking-fints.md`](banking-fints.md) · [`banking-fx.md`](banking-fx.md) · [`banking-ebics.md`](banking-ebics.md)

## Pivot summary

Per [`connector-plugins.md`](connector-plugins.md), every importer is a **`ConnectorPlugin` of category `Import`**, disabled by default. The user enables the format(s) their bank exports. Library picks from the earlier research round are unchanged — the pivot is purely architectural (each format = a plugin), not technical.

**Headline decisions (unchanged from prior research, now framed as plugins):**

- **CSV plugin** — **roll-our-own** parser core + per-bank-dialect **sub-plugins** (~300 LOC core + ~50 LOC per dialect sub-plugin). stdlib + okio only. **Sub-plugin model:** each known-bank-CSV-dialect is its own plugin entry (Sparkasse-CSV-dialect plugin, DKB-CSV-dialect plugin, Commerzbank-CSV-dialect plugin, ING-DiBa-CSV-dialect plugin, N26-CSV-dialect plugin, Revolut-CSV-dialect plugin, Wise-CSV-dialect plugin), plus a **generic-mapping CSV plugin** for any bank not covered (the user maps columns in the plugin's `ConfigScreen()`, the map persists in DataStore).
- **QIF plugin** — **roll-our-own** (~150 LOC). No mature permissively-licensed JVM lib; format is trivial.
- **OFX plugin** — **adopt `com.webcohesion.ofx4j:ofx4j`** (Apache-2.0).
- **MT940 plugin** — **adopt `com.prowidesoftware:pw-swift-core`** (Apache-2.0), narrowed to the MT940 message class.
- **CAMT.053 plugin** — **roll-our-own** XmlPullParser-streaming walker. No third-party runtime dep.

**v1 priority order:** **(1) CSV first** (universal fallback, works for every bank that publishes any export), **(2) CAMT.053 second** (cleanest structured data, growing dominant for DE / EU SEPA), **(3) MT940 third** (still common from DE / EU corporate-ish accounts and inside FinTS HKKAZ responses, prowide gives it to us cheaply, and the FinTS plugin reuses the same `Mt940Importer` per [`banking-fints.md`](banking-fints.md)), **(4) OFX fourth** (US / UK / Canada — adoption-heavy outside DACH), **(5) QIF last** (legacy, low cost to add once anyone asks).

Each importer plugin is `Disabled` by default. The user enables the format(s) they actually use; configuration is "pick a file via SAF" (no credentials, no network), so `Configured` collapses into "Enabled with a SAF picker shortcut configured" and `Active` triggers the moment the user actually picks a file. Per [`connector-plugins.md`](connector-plugins.md), import plugins never make network calls, so the `PluginNetworkGuard` is a no-op for them — they only consume SAF-picked content and emit `NormalizedTransaction` rows for the host to write.

All importer plugins produce `NormalizedTransaction` rows with a **stable per-row content hash** so re-importing the same file or overlapping date ranges doesn't double-count. Hash = SHA-256 of `(account_iban || value_date || posting_date || amount_minor_units || currency || normalized_purpose_text || counterparty_iban)` — first 16 bytes, hex.

---

## CSV

### What it is

The lowest-common-denominator export. Every bank web banking portal offers it. Format is wildly inconsistent — delimiter, decimal separator, encoding, column count, column order, header presence, date format, signed-amount-vs-debit-credit-columns, encoded line endings all differ per bank. There is no spec; banks ship what they ship.

### Library candidates

No third-party library candidate is a net win for our use case — the structural variance lives at the **column-mapping layer**, not the **delimiter-parsing layer**. Apache Commons CSV / opencsv would handle delimiter quirks but we then still need per-bank column maps. The roll-our-own delimiter parser is ~50 LOC and avoids the (Apache-2.0 but JVM-y) transitive deps.

Candidates considered:

- **Apache Commons CSV** — Apache-2.0, JVM-friendly, [maven](https://commons.apache.org/proper/commons-csv/). Would work; the structural variance isn't here. **Defer / not adopted.**
- **opencsv** — Apache-2.0, [opencsv.sf.net](https://opencsv.sourceforge.net/). Likewise. **Defer.**
- **kotlinx-csv** (Kotlin Multiplatform) — Apache-2.0 forks exist but none with the maintenance + KMP-Android story sorted. **Defer.**

### Roll-our-own architecture

```
BankCsvDialect (interface)
  ├─ name(): String                       // "Sparkasse 2026", "DKB CSV"
  ├─ matches(headerRow: List<String>, sampleRows: List<List<String>>): Boolean
  ├─ encoding(): Charset                  // UTF_8, ISO_8859_1, WINDOWS_1252
  ├─ delimiter(): Char                    // ',', ';', '\t'
  ├─ decimalSeparator(): Char             // '.', ','
  ├─ headerRowIndex(): Int                // skip the n preamble rows
  ├─ map(row: List<String>): NormalizedTransaction?
```

Built-in dialect **sub-plugins** ship for: Sparkasse (DE, `;`-delimited, `,`-decimal, `ISO-8859-1`), DKB (DE, `;`, `,`, `UTF-8`), ING-DiBa (DE), Commerzbank (DE), N26 (`,`, `.`, `UTF-8`), Revolut, Wise. Each is its own `PluginManifest` entry of category `Import`, off by default. The user enables the dialect their bank uses; the dialect's `ConfigScreen()` is "tap to pick a CSV via SAF" plus an optional column-map override.

For any bank not covered by a built-in dialect sub-plugin, the **generic-CSV-mapping plugin** opens a column-mapper UI when the user picks a file: the user identifies which columns are date / amount / counterparty / purpose, the map persists per-user-bank-pair in DataStore, and subsequent imports for the same bank reuse it.

Encoding detection: read first 4 bytes for BOM; fall back to chardet-style heuristic on the first 4KB (just check for high-byte distribution patterns that distinguish UTF-8 from Windows-1252 / ISO-8859-1 — ~30 LOC, no third-party dep).

### Per-bank dialect quirks (verified)

- **DE (Sparkasse, Volksbank, DKB, Commerzbank, ING-DiBa)**: `;` delimiter, `,` decimal, `ISO-8859-1` or `WINDOWS-1252` (newer DKB exports `UTF-8`). Often 5-15 lines of preamble (header info: "Kontonummer: ...") before the actual header row.
- **FR (BNP Paribas, Société Générale, Crédit Mutuel)**: `;` delimiter, `,` decimal, `WINDOWS-1252`. Closer to DE shape.
- **UK (Barclays, HSBC, NatWest, Starling, Monzo)**: `,` delimiter, `.` decimal, `UTF-8`. Field with embedded `,` is double-quoted per RFC 4180.
- **US (Chase, Citibank)**: `,` delimiter, `.` decimal, `UTF-8`. RFC 4180.
- **N26, Revolut, Wise**: `,` delimiter, `.` decimal, `UTF-8`. RFC 4180. ISO-8601 dates.

### Decision

**Roll-our-own, packaged as one `csv-core` shared module + seven per-bank `csv-<bank>` sub-plugins + one `csv-generic` mapping sub-plugin.** All `Import`-category, all off by default, all registered individually in `PluginManifest` per [`connector-plugins.md`](connector-plugins.md). Core parser ~300 LOC; ~50 LOC per built-in dialect sub-plugin; generic-mapper UI ~200 LOC + DataStore-persisted column-map per user-defined dialect. Encoding detection ~30 LOC. **Total ~1,000 LOC** for v1 covering the seven built-in dialect sub-plugins plus the generic-mapping sub-plugin.

---

## QIF

### What it is

Quicken Interchange Format. Line-oriented, 1980s-era flat text. Each record is a stack of `<TypeChar><Value>` lines terminated by `^`. Eight or so type characters (`D` date, `T` amount, `P` payee, `M` memo, `N` cheque#, `L` category, `C` cleared-status). No standard date format (US `MM/DD/YYYY` vs EU `DD.MM.YYYY` collide). [QIF spec, archive.org capture of original Intuit spec](https://web.archive.org/web/20100222214101/http://web.intuit.com/support/quicken/docs/d_qif.html).

### Library candidates

- **`fracpete/quicken4j`** — **GPL-3.0** ([repo](https://github.com/fracpete/quicken4j/blob/master/pom.xml)). **REJECTED on license.**
- **`yogeshjoshi/QIFParser`** — unclear SPDX (no LICENSE file as of last check), abandoned. **REJECTED.**
- Other JVM QIF parsers on GitHub: all dormant, mostly GPL. None pass the gate + maintenance gate.

### Decision

**Roll-our-own.** ~150 LOC. Trivial line-oriented loop, date-format probing (sample first 10 records, pick the format that parses cleanest), one `NormalizedTransaction` per `^`-terminated block. v1 covers `!Type:Bank`, `!Type:CCard`, `!Type:Cash` — defer investment (`!Type:Invst`) until anyone asks.

---

## OFX

### What it is

Open Financial Exchange. SGML-flavoured (v1) or XML (v2). US / UK / Canada dominant. Quicken / Microsoft Money legacy + modern Plaid/Tink/aggregator exports often produce it. Specification at [ofx.net](https://www.ofx.net/).

### Library candidates

- **`com.webcohesion.ofx4j:ofx4j` (`stoicflame/ofx4j`)** — **Apache-2.0** ([Maven](https://central.sonatype.com/artifact/com.webcohesion.ofx4j/ofx4j), [GitHub](https://github.com/stoicflame/ofx4j)). Latest **1.39** (April 2024 series; check Maven for 2026 patch versions). Pure Java, no native deps. Single transitive: NanoXML (also Apache-2.0). Dep weight ~200 KB on the APK. minSdk-28 compatible (no JDK9+-only APIs in the message-parser surface — the HTTP-connector portion uses `javax.net.ssl` which is fine on Android, but ledgerboy uses OkHttp for transport and only consumes the parser surface).
- **`net.sf.ofx4j:ofx4j`** — original upstream from SourceForge, dormant since 2014. webcohesion fork is the active line. **REJECTED in favour of the fork.**

### Decision

**Adopt `com.webcohesion.ofx4j:ofx4j`** at the latest stable on Maven Central. Use only the parser surface (`OFXReader` + the SGML `BankStatementResponse` walker). Wrap in a thin `OfxImporter` that emits `NormalizedTransaction`. **~150 LOC** of glue.

---

## MT940

### What it is

SWIFT FIN message type 940 — "Customer Statement". Tag-structured text, fixed-width-ish. Tag `:25:` = account, `:60F:` = opening balance, `:61:` = transaction line, `:86:` = transaction narrative, `:62F:` = closing balance. Heavily used inside DE/EU corporate banking and historically wrapped inside FinTS HKKAZ responses. [SWIFT MT940 spec](https://www.iotafinance.com/en/SWIFT-ISO15022-Message-type-MT940.html).

### Library candidates

- **`com.prowidesoftware:pw-swift-core` (Prowide Core, `prowide/prowide-core`)** — **Apache-2.0** ([GitHub](https://github.com/prowide/prowide-core), [Prowide product page](https://www.prowidesoftware.com/products/open-source/core)). Latest **SRU2025-10.3.14** (May 2026). Active, used by 100+ FIs in production. Pure Java. Dep weight: the full JAR is ~5 MB but R8 / proguard with `keep` rules narrowed to `com.prowidesoftware.swift.model.mt.mt9xx.MT940` and its transitive model classes shrinks to ~800 KB after minification. minSdk-28 compatible (parser surface uses standard `java.util.*`; no JPA/Hibernate-only classes need to ship — exclude the `com.prowidesoftware.swift.model.jpa` package via R8 keep rules / classes-to-strip in the licensee gradle config).
- **`rasheedamir/pw-swift-core`** — abandoned fork from 2015. **REJECTED.**
- Hand-roll: feasible (~400 LOC core + ~200 LOC per `:86:` German-banking subfield-decoder for the field-32-style Geschäftsvorfallcode + colon-prefixed subfields `?20`/`?21`/.../`?32`/`?33`). Prowide already does this with battle-tested tag parsing — not worth re-implementing.

### Decision

**Adopt Prowide Core.** Use only `MT940` + its transitive model classes. R8 minify down to ~800 KB. Wrap in `Mt940Importer` that emits `NormalizedTransaction` with the `:86:` subfields parsed into `purpose`, `counterparty_name`, `counterparty_iban`. **~250 LOC** of glue plus a per-DE-bank `:86:` subfield decoder.

---

## CAMT.053

### What it is

ISO 20022 `camt.053.001.xx` — "Bank-to-Customer Statement". XML. Replaces MT940 in modern EU/SEPA bank exports. Schema versions in the wild: `001.02` (legacy SEPA), `001.04` (common), `001.08` (current EBA), `001.13` (latest 2024). [ISO 20022 catalogue](https://www.iso20022.org/iso-20022-message-definitions?search=camt.053).

Document shape: `Document` → `BkToCstmrStmt` → `Stmt+` → (`Acct`, `Bal+`, `Ntry+`) → `Ntry` → `NtryDtls/TxDtls+` → per-tx amount / counterparty / remittance.

### Library candidates

- **`tjeerdnet/CAMT053Parser`** — Java + JAXB. Unclear SPDX (no LICENSE file as of last check), single-version (`001.02`) only, last commit ~2018. **REJECTED on maintenance + version coverage.**
- **`plusminapp/camt053parser`** — **MIT** ([GitHub](https://github.com/plusminapp/camt053parser)). JAXB-based. **Android-incompatible**: JAXB doesn't ship on Android, requires either pulling `jakarta.xml.bind` + a JAXB runtime (~3 MB transitive, including `com.sun.istack` and `org.glassfish.jaxb` which both leak `java.beans.*` classes that Android doesn't provide) or a SimpleXML rewrite. **REJECTED on Android compat.**
- **Prowide ISO 20022 (`com.prowidesoftware:pw-iso20022`)** — **Apache-2.0** ([Prowide product page](https://www.prowidesoftware.com/products/open-source/iso20022)). Covers camt.052/053/054/056. Pure Java, no JAXB runtime requirement (uses Jackson XML internally). Dep weight: full JAR ~12 MB; R8-minified to ~2 MB if we only consume `camt.053.001.08`. Worth a closer look — could be the import-side analogue of prowide-core for MT940. **Candidate: adopt with R8 narrowing.** Verify Android compat before committing (the README mentions Jackson, which is Android-friendly; `pw-iso20022` may pull `javax.activation` which needs the `jakarta.activation:jakarta.activation-api` shim on Android).

### Roll-our-own (XSD-driven codegen)

The XSD is public ([camt.053.001.13 ZIP from ISO 20022](https://www.iso20022.org/iso-20022-message-definitions?search=camt.053)). Two viable codegen paths:

- **KAXB** (`SixRQ/KAXB`) — generates native Kotlin from XSD, [GitHub](https://github.com/SixRQ/KAXB). Less battle-tested. Manageable risk if we only run it once per schema version.
- **XJC + post-process to Kotlin** — run JAXB's `xjc` at build time against the XSD, produce Java classes, hand-write a thin Kotlin wrapper. Build-time only — the XJC output is plain `@XmlElement`-annotated Java that we'd need to either (a) ship along with a JAXB-light runtime (bad on Android) or (b) post-process into kotlinx.serialization-xml form (the cleaner path).
- **schema-gen** (`reaster/schema-gen`) — XSD to Swift / Kotlin / Java codegen. **Apache-2.0** ([GitHub](https://github.com/reaster/schema-gen)). Underused but actively designed for cross-target codegen. Worth a prototype.
- **kotlinx.serialization-xml** (community module) — runtime XML support for kotlinx.serialization. Would let us write the data classes by hand from the schema (cumbersome — camt.053 is ~300 elements) OR codegen them once with schema-gen + the kotlinx.serialization XML format module. Apache-2.0.
- **XmlPullParser walker** — Android stdlib, no dep at all. Hand-roll a streaming parser that walks `BkToCstmrStmt/Stmt/Ntry` and emits `NormalizedTransaction` per `Ntry`. Skip everything else. **~400 LOC.** Wins on dep weight (zero), loses on completeness (won't validate against the XSD, won't catch new optional-element additions).

### Decision

**Roll-our-own, XmlPullParser-streaming.** Reasons:

1. We don't need 95% of the schema — we need transactions (`Ntry`), amount, value date, posting date, counterparty IBAN, remittance text. ~15 elements, not 300.
2. Zero runtime dep, zero R8 minify cost, zero JAXB / Android-incompatibility risk.
3. Schema version compatibility (handling `001.02` through `001.13` simultaneously) is easier with a tolerant streaming walker than with a generated bound-class hierarchy that errors on unknown elements.
4. If we ever need full schema coverage (e.g. to support outgoing payment-status report `pain.002`), we revisit and pull in `pw-iso20022` then.

**~400 LOC** total: ~300 for the streaming walker, ~100 for per-version field-presence quirks (e.g. `001.02` has `RltdPties/Dbtr/Nm` where `001.08` adds an extra `Pty` wrapper level).

---

## NormalizedTransaction + dedup contract

Every importer produces:

```kotlin
data class NormalizedTransaction(
  val accountIban: String?,           // null for cash / card statements
  val accountKey: String,             // fallback identifier when IBAN is absent
  val valueDate: LocalDate,
  val postingDate: LocalDate,
  val amount: Money,                  // integer minor units + Currency
  val counterpartyName: String?,
  val counterpartyIban: String?,
  val purpose: String,                // normalized: trimmed, internal whitespace collapsed
  val sourceFormat: ImportFormat,     // CSV / QIF / OFX / MT940 / CAMT053
  val sourceFileHash: String,         // SHA-256 of the source file (for traceability)
  val rowHash: String,                // SHA-256 first 16 bytes of the dedup tuple
)
```

`rowHash` is the dedup key. Re-importing the same file is a no-op. Importing overlapping date ranges deduplicates per-row. Two different exports of the same transaction (one CAMT.053, one CSV) ideally produce the same `rowHash` — this requires that the CSV dialect normalises the same fields the CAMT.053 walker normalises (amount, dates, IBANs, purpose-text). Best-effort, not guaranteed; we surface "looks like a duplicate of `<txid>`?" prompts in the UI when the heuristic match without an exact hash match crosses a confidence threshold (future Phase F+).

## Encoding handling

Every importer must:

1. **BOM-sniff** the first 4 bytes. UTF-8 BOM (`EF BB BF`), UTF-16 BOM (`FE FF` / `FF FE`).
2. **Fall back to charset heuristic** on the first 4 KB (high-byte distribution: pure-ASCII → UTF-8; presence of bytes in `0x80–0x9F` with no UTF-8 continuation pattern → Windows-1252; bytes in `0xA0–0xFF` with UTF-8 continuation pattern → UTF-8; else ISO-8859-1 as last resort).
3. **Allow user override** via the per-dialect config UI for CSV — DataStore-persisted.

CAMT.053 XML always declares its encoding in the XML prolog — honour it. OFX v2 (XML) likewise. OFX v1 (SGML) inherits the CSV-shaped uncertainty — apply the same heuristic.

## License-gate summary

| Format plugin | Library | SPDX | Verdict |
|---|---|---|---|
| CSV (`csv-core` + per-bank sub-plugins) | roll-our-own | (n/a, our code) | adopt |
| QIF | roll-our-own | (n/a, our code) | adopt |
| OFX | `com.webcohesion.ofx4j:ofx4j` | Apache-2.0 | adopt |
| MT940 | `com.prowidesoftware:pw-swift-core` | Apache-2.0 | adopt |
| CAMT.053 | roll-our-own (XmlPullParser walker) | (n/a, our code) | adopt |

All adoptions pass the [`oss-licenses.md`](oss-licenses.md) gate. Zero LGPL / AGPL / GPL deps land in the APK. Each plugin's own `licenseSpdx` surfaces in Settings → Plugins per [`connector-plugins.md`](connector-plugins.md).

## Phase X (plugin runtime) — gating

Per [`connector-plugins.md`](connector-plugins.md), Phase X lands before any import plugin. The runtime supplies:

- `ConnectorPlugin` interface + state machine (X.1).
- `PluginManifest` (X.2) — each importer registers as its own entry.
- Per-plugin DataStore (X.3) — used by the CSV generic-mapping plugin to persist column maps per user-bank pair.
- Settings → Plugins UI (X.6 / X.8) — each importer's `ConfigScreen()` ships the SAF picker shortcut.
- SAF-picker hook from the host (per [`connector-plugins.md`](connector-plugins.md) Configure flow).

**Do not start any importer plugin sub-phase below until Phase X is complete.**

## Phase B implementation sub-step checklist

### B.import-common — Foundation (must land first, common to all import plugins)

- [ ] **B.import-common.1** Land `NormalizedTransaction` data class + `Money` value class (integer minor units) per CLAUDE.md money-handling rule.
- [ ] **B.import-common.2** Land `rowHash` computation + Room unique-constraint on `(account_id, row_hash)` so re-imports are idempotent at the DB level.
- [ ] **B.import-common.3** Land `ImportFormat` enum + internal `BankImporter` interface (`import(input: InputStream, accountKey: String): Result<List<NormalizedTransaction>>`) — each importer plugin wraps a `BankImporter` and emits its rows via the standard `ConnectorPlugin.fetch()` path.
- [ ] **B.import-common.4** Land SAF picker integration via the host's plugin SAF hook (`ACTION_OPEN_DOCUMENT` with MIME filter, persisted URI permissions only for the watch-folder optional Phase F+ flow).
- [ ] **B.import-common.5** Land BOM-sniff + charset-heuristic helper (`CharsetDetect.kt`, ~80 LOC).

### B.import-csv — CSV plugins (v1 priority 1)

- [ ] **B.import-csv.1** Land `csv-core` shared module: `BankCsvDialect` interface + core CSV-line splitter (delimiter param, quoted-field handling per RFC 4180). Used by every CSV sub-plugin.
- [ ] **B.import-csv.2** Ship each built-in dialect as its own plugin under `plugins/import-csv-<bank>/`: Sparkasse, DKB, ING-DiBa, Commerzbank, N26, Revolut, Wise. Each implements `ConnectorPlugin` (category `Import`), registers in `PluginManifest`, off by default.
- [ ] **B.import-csv.3** Ship the **generic-CSV-mapping plugin** (`plugins/import-csv-generic/`) with a column-mapper `ConfigScreen()` for unknown dialects; per-user-bank column map persisted in the plugin's DataStore namespace (per [`connector-plugins.md`](connector-plugins.md) X.3).
- [ ] **B.import-csv.4** Robolectric tests against fictional-sample fixtures (`app/src/test/resources/fixtures/`, gitignored per CLAUDE.md money-privacy rule — fixtures stay local with synthetic data only).

### B.import-camt053 — CAMT.053 plugin (v1 priority 2)

- [ ] **B.import-camt053.1** Land XmlPullParser streaming walker (`CamtImporter`, ~300 LOC) handling versions `001.02` / `001.04` / `001.08` / `001.13`.
- [ ] **B.import-camt053.2** Per-version field-presence shim (~100 LOC; e.g. `001.02` debtor-name path vs `001.08`).
- [ ] **B.import-camt053.3** Wrap as `Camt053ImportPlugin : ConnectorPlugin` under `plugins/import-camt053/`. Register in `PluginManifest`. Off by default.
- [ ] **B.import-camt053.4** Robolectric tests against fictional `acme-savings-2026-q1.camt053.xml` fixture.

### B.import-mt940 — MT940 plugin (v1 priority 3)

- [ ] **B.import-mt940.1** Add `com.prowidesoftware:pw-swift-core` to `app/build.gradle.kts` `implementation` deps; verify SPDX with `./gradlew :app:licenseeAndroidDebug`.
- [ ] **B.import-mt940.2** R8 keep rules narrowed to `com.prowidesoftware.swift.model.mt.mt9xx.MT940` + transitive model; exclude JPA/Hibernate model package. Verify minified APK delta < 1 MB.
- [ ] **B.import-mt940.3** Land `Mt940Importer` (~150 LOC) wrapping `MT940.parse(text)`, walking `:61:`/`:86:` tag pairs into `NormalizedTransaction`. Per-DE-bank `:86:` subfield decoder (`?20`/`?21`/`?32`/`?33`). Also consumed by the `fints-client` plugin per [`banking-fints.md`](banking-fints.md) (HKKAZ payload is MT940).
- [ ] **B.import-mt940.4** Wrap as `Mt940ImportPlugin : ConnectorPlugin` under `plugins/import-mt940/`. Register in `PluginManifest`. Off by default. `licenseSpdx` declares both our MIT wrapper and the Apache-2.0 prowide dep.
- [ ] **B.import-mt940.5** Robolectric tests against fictional `acme-savings-2026-q1.mt940` fixture.

### B.import-ofx — OFX plugin (v1 priority 4)

- [ ] **B.import-ofx.1** Add `com.webcohesion.ofx4j:ofx4j` to `implementation`; verify SPDX.
- [ ] **B.import-ofx.2** Land `OfxImporter` (~150 LOC) using `OFXReader` against `BankStatementResponse`.
- [ ] **B.import-ofx.3** Wrap as `OfxImportPlugin : ConnectorPlugin` under `plugins/import-ofx/`. Register in `PluginManifest`. Off by default.
- [ ] **B.import-ofx.4** Robolectric tests against fictional `test-brokerage-2026-jan.ofx` fixture.

### B.import-qif — QIF plugin (v1 priority 5, deferable)

- [ ] **B.import-qif.1** Land `QifImporter` (~150 LOC) — line-oriented walker, date-format probe, `!Type:Bank` / `!Type:CCard` / `!Type:Cash` support.
- [ ] **B.import-qif.2** Wrap as `QifImportPlugin : ConnectorPlugin` under `plugins/import-qif/`. Register in `PluginManifest`. Off by default.
- [ ] **B.import-qif.3** Robolectric tests against fictional `demo-2026.qif` fixture.

### B.import-integration

- [ ] **B.import-integration.1** Confirm each importer plugin flows rows through the host's standard `ConnectorPlugin.fetch()` → host-write pipeline; the data layer doesn't distinguish file-import plugins from live-connector plugins (FinTS / PSD2-per-bank).
- [ ] **B.import-integration.2** Surface import-result toast with dedup count ("Imported 47 new transactions, skipped 12 duplicates") — calm-factual copy per CLAUDE.md editorial rule.
- [ ] **B.import-integration.3** Verify SAF persisted-URI behaviour on `adb install -r --user 0` per CLAUDE.md SAF-specific test-loop notes.

## Decision summary

| Plugin | Approach | License | LOC est. | v1 priority |
|---|---|---|---|---|
| CSV (`csv-core` + per-bank sub-plugins + generic-mapping) | roll-our-own | (ours) | ~1,000 | 1 |
| CAMT.053 plugin | roll-our-own XmlPullParser walker | (ours) | ~400 | 2 |
| MT940 plugin | adopt `pw-swift-core` | Apache-2.0 | ~250 glue | 3 |
| OFX plugin | adopt `com.webcohesion.ofx4j:ofx4j` | Apache-2.0 | ~150 glue | 4 |
| QIF plugin | roll-our-own | (ours) | ~150 | 5 (deferable) |

Total new-code budget for v1 import-plugin surface: **~2,000 LOC Kotlin** + two well-scoped Apache-2.0 deps. Covers every plausible bank export shape the user will encounter in DE / EU / UK / US. Every plugin off by default per [`connector-plugins.md`](connector-plugins.md).
