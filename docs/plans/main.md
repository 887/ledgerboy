# ledgerboy — main build plan

## Status: 🟢 BUILDING — Phases 0 / A / X / C / B / D / E shipped; Phase F (transactions) next

_Shipped + verified on 2026-06-14 (42 unit tests green): Phase 0 (host), A (scaffold + UI shell), X (connector plugin runtime), C (ledger schema + Money + transaction store), B (CSV import connector), D (multi-currency FX cache + MoneyConverter + ECB-direct FX plugin), E (accounts + overview screens on real ledger data). ledgerboy went from zero code to a working multi-currency ledger app: plugin runtime with the zero-calls guard, CSV import → ledger, FX conversion, and data-wired Accounts/Overview. Next: Phase F (transactions list + filters + tap-through detail), then G (categories + rules), H (budgets), I/J (assets), K (charts), L (onboarding), M (settings), O (release). Screen phases are Robolectric-verified now; on-device capture lands when the shared `medium_phone` AVD (currently driven by a concurrent strictlykeptboy QA session) frees._

## Stack (locked)

- **Language:** Kotlin
- **UI:** Jetpack Compose + **Material 3 Expressive** (M3E from day one — inherited gotchas in [`m3-expressive.md`](m3-expressive.md))
- **UI shell:** vertical navigation rail + top bar with buttons + settings catalog DSL + AboutScreen + LicensesScreen — **copied from tonearmboy** ([`ui-shell.md`](ui-shell.md))
- **Data:** Room (ledger: accounts, transactions, asset registry, asset valuations, budgets, FX rate cache), DataStore (preferences), SAF / `DocumentFile` (import files — no MediaStore, no `READ_EXTERNAL_STORAGE`). Encrypted database is research-gated ([`security-research.md`](security-research.md)).
- **Connectors:** Every connector is a **plugin, disabled by default** — banking, asset prices, FX, file import, regional indexes. No aggregator middlemen, no paid-SaaS pricing services, direct-to-source-of-truth only. Locked authority: [`connector-plugins.md`](connector-plugins.md).
- **Banking:** Direct-from-device, direct-to-bank. Per-bank PSD2-direct plugins (Berlin Group NextGenPSD2) + roll-our-own FinTS-client plugin + per-format import plugins (CSV / QIF / OFX / MT940 / CAMT.053). Per-source research-gated ([`banking-research.md`](banking-research.md) + per-source `banking-*.md`).
- **Asset valuation:** Per-class plugin connectors, all off by default ([`asset-research.md`](asset-research.md) + per-class `asset-*.md`). Real estate / vehicles / generic manual + periodic (regional-index helpers as optional plugins); securities via per-broker plugins; crypto via per-exchange + on-chain RPC plugins; collectibles via Scryfall / pokemontcg.io / YGOPRODeck plugins.
- **Charts:** Compose-native, research-gated ([`dataviz-research.md`](dataviz-research.md)).
- **Money:** integer minor units on a `Money` value class — never `Float` / `Double` / `BigDecimal` for amounts.
- **Build front-end:** Google's [Android CLI](https://developer.android.com/tools/agents/android-cli) (`android` command, launched April 2026). Wraps Gradle, SDK, install, run. **No Android Studio.**
- **Build back-end:** Gradle (driven via the Android CLI). The repo includes a Gradle wrapper.
- **Unit tests:** Robolectric (JVM-only, zero device).
- **UI tests:** [mobile-mcp](https://github.com/mobile-next/mobile-mcp) over ADB. Current target: headless AVD `medium_phone` (Android 16 / API 36, RSS ~3.2 GB) — see Phase 0.
- **Knowledge:** `android docs search` for live Android API guidance. `android-skills-mcp` for the official Android skills inside Claude Code.

## Plan-file discipline

Per the user's global CLAUDE.md rule:

1. Numbered phases using typeable letters: `Phase A`, `Phase B`, … No `§` characters.
2. Sub-step checkboxes inside each phase: `- [x] **A.1** …`. A phase without sub-step checkboxes is forbidden — if you can't articulate sub-steps, write the design first.
3. Tick as you ship: `- [x]` AND a `shipped in commit <id>` note on the phase header at the same time the work lands. Do not batch ticks until the end. (In jj repos, use jj change IDs — alphabetic prefix — not git commit hashes; jj change IDs survive rebases.)
4. Mark the plan `## Status: ✅ DONE` once every phase is ticked. Obsolete plans get `## Status: OBSOLETE — superseded by …` instead.
5. Research-round agents produce one `docs/plans/<area>-decision.md` (or `<area>-<source>.md`) per gate; the parent research file gets a "Decision: see …" pointer at the top when the decision lands.

---

## Phase 0 — prerequisites (one-time, on the host) — shipped 2026-06-14

Goal: a buildable host. These run once per developer machine. Tracked here so the environment is verifiable before agents go to work. Same machine as the rest of the family, so most are no-ops on this user's box, but tick them for completeness on a fresh checkout.

- [x] **0.1** Install Google's Android CLI: `curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/android -o ~/.local/bin/android && chmod +x ~/.local/bin/android`. Likely already present from tonearmboy / whisperboy — verify with `android --version`.
- [x] **0.2** `android sdk install platforms/android-34 build-tools/34.0.0` (and confirm `android-36` / `build-tools/36.0.0` present, since the project targets API 36 like its siblings). Likely already present.
- [x] **0.3** JDK 17+ available on the host for direct `./gradlew` invocations (the bundled JDK 21 in the Android CLI is fine for `android run`, but direct Gradle calls need a full JDK — see CLAUDE.md). `/usr/lib/jvm/java-26-openjdk` is the working path on this user's box.
- [x] **0.4** `mobile` MCP server registered at project scope in `.mcp.json` — already committed in the initial skeleton; verify with `claude mcp list` from inside the repo.
- [x] **0.5** `android-skills` MCP server registered at project scope in `.mcp.json` — already committed in the initial skeleton; verify with `claude mcp list` from inside the repo.
- [x] **0.6** Test target: headless AVD `medium_phone` (shared with the rest of the family). Created via `android emulator create --profile=medium_phone`. Should already exist; if not, `emulator -list-avds` will say so.

---

## Phase A — scaffold — shipped in commit ae06cf8

Goal: a buildable, sideload-able APK that boots into a blank Compose screen wired to the vertical-rail shell from tonearmboy. Everything that follows assumes this exists. Mirror whisperboy Phase A almost exactly — same template choice, same gradle setup, same package convention — plus the tonearmboy UI-shell copy from [`ui-shell.md`](ui-shell.md).

- [x] **A.0** Browse `android create list` and pick the closest official template. Default expectation: `empty-activity` (same template tonearmboy / whisperboy used).
- [x] **A.1** `android create --name=ledgerboy --output=. <template>` from inside the repo root. Verify the generated layout: `app/`, `gradle/wrapper/`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`. Rename package from `com.example.ledgerboy` to `com.eight87.ledgerboy`. Rename theme to `LedgerboyTheme`. Rename activity to `LedgerboyActivity`.
- [x] **A.2** Add baseline deps to `app/build.gradle.kts` via `libs.versions.toml`: Compose BOM (Material 3 Expressive override per [`m3-expressive.md`](m3-expressive.md)), Material Icons Extended, Lifecycle (runtime + viewmodel + compose), Activity Compose, Navigation 3, Room (runtime + ktx + KSP), DataStore Preferences, `androidx.documentfile`, `kotlinx-serialization-json`, Licensee Gradle plugin (per [`oss-licenses.md`](oss-licenses.md)).
- [x] **A.3** `AndroidManifest.xml` adds: `INTERNET` (for banking APIs), `USE_BIOMETRIC` (for credential unlock — research-gated, declared early). No `READ_EXTERNAL_STORAGE` — SAF only. `minSdk = 28` (matching the family floor). `compileSdk = 36`, `targetSdk = 36`.
- [x] **A.4** `.gitignore` covers Android build artifacts plus the bank-statement file extensions denied at repo root — already committed in the initial skeleton; verify the Phase-A-generated `app/.gitignore` (with `/build`) coexists cleanly with the root `.gitignore`.
- [x] **A.5** Copy the **vertical-navigation-rail + top-bar** shell from tonearmboy per [`ui-shell.md`](ui-shell.md): `LedgerboyApp.kt`, `LedgerboyBackStack.kt`, `Destinations.kt`, `RouteScope.kt`, `RouteScopeFactory.kt`, `SectionTitle.kt`. Adapt content to ledgerboy's destinations (`Overview`, `Accounts`, `Budgets`, `Assets`, `Charts`, `Settings`). The destinations themselves are placeholder Composables in Phase A — real surfaces land in later phases.
- [x] **A.6** Copy the **settings catalog DSL** scaffold from tonearmboy per [`ui-shell.md`](ui-shell.md): `SettingsCardDsl.kt`, `SettingsCatalog.kt`, `SettingsPagesRender.kt`, `SettingsScreen.kt`, `SettingsSubPages.kt`, plus a `catalog/sections/` directory with empty stub sections. AboutScreen + LicensesScreen ship at A.7.
- [x] **A.7** Copy **AboutScreen.kt** and **LicensesScreen.kt** from tonearmboy. Wire LicensesScreen to the Licensee-generated `assets/licenses/artifacts.json` per [`oss-licenses.md`](oss-licenses.md).
- [x] **A.8** Material 3 Expressive theming entry: `LedgerboyTheme.kt` with `MaterialExpressiveTheme(...)` wiring per [`m3-expressive.md`](m3-expressive.md) — apply the five inherited gotchas before they bite.
- [x] **A.9** Build verification: `./gradlew assembleDebug` succeeds. APK lands at `app/build/outputs/apk/debug/ledgerboy-debug.apk`.
- [x] **A.10** Install verification: `android run --apks=app/build/outputs/apk/debug/ledgerboy-debug.apk` launches `LedgerboyActivity` on `emulator-5554`. Vertical rail renders with the six placeholder destinations. Settings root opens, About opens, Licenses opens and shows the auto-generated dep inventory.

---

## Phase X — connector plugin runtime — shipped in commit d06dcb9

**Goal:** the host-side scaffolding every connector plugin builds on. Lands before any individual connector plugin so that Phase B+ are each just "implement `ConnectorPlugin` for one source." Locked architecture in [`connector-plugins.md`](connector-plugins.md).

- [x] **X.1** `ConnectorPlugin` interface + `PluginCategory` enum + `PluginState` state-machine in `app/.../plugins/api/`.
- [x] **X.2** `PluginManifest` singleton listing every shipped plugin (initially empty — populated as plugins land in Phase B+).
- [x] **X.3** Per-plugin DataStore namespace + `PluginConfigStore` helper for plugins to persist config without colliding.
- [x] **X.4** `PluginNetworkGuard` OkHttp `Interceptor` rejecting requests not originating from `Active` plugins; per-plugin bytes-in/out / fetch-count / error-count accumulators.
- [x] **X.5** `PluginScheduler` (WorkManager) — per-plugin schedule-or-manual; respects the per-plugin background-sync opt-in; honours disable kill-switch within one tick.
- [x] **X.6** Settings → Plugins screen — accordion by category, per-plugin cards with state chip + toggle + Configure button; built on the Settings catalog DSL from [`ui-shell.md`](ui-shell.md).
- [x] **X.7** Settings → Plugins → Network usage sub-screen — per-plugin totals + endpoint list + privacy statement; user-visible audit.
- [x] **X.8** Per-plugin Configure flow shell — opens the plugin's `@Composable ConfigScreen()`, provides the SAF / Keystore / strings helpers; "Test connection" runs one fetch with explicit user confirmation before transitioning state to `Active`.
- [x] **X.9** Robolectric tests: state-machine transitions, network-guard rejection of disabled plugins, scheduler kill-switch latency.

---

## Phase B — first connector plugin: CSV import — shipped in commit 618c647

**Goal:** at least one `ConnectorPlugin` wired end-to-end on the Phase X runtime, fetching a transaction list and writing it into the Phase C ledger as `NormalizedTransaction` rows. First-to-ship is the **CSV import** plugin (banking-import.md priority #1: lowest setup friction, no network, no credentials, universal — works for any bank that exports a CSV), ahead of the credentialed banking connectors.

**Decision:** picked CSV import over `fints-client` as the first plugin — it exercises the full Configure→fetch→RowSink→ledger path with the least surface (no OAuth/PIN/cert-pinning), validating Phase X + Phase C together. FinTS / per-bank PSD2-direct land next on the same runtime. Per [`banking-import.md`](banking-import.md) + [`connector-plugins.md`](connector-plugins.md).

- [x] **B.1** Host SAF read seam: `PluginHost.readImportFile(uri, charset)` via ContentResolver (plugins never touch ContentResolver directly) + the Configure-shell import-picker plumbing (`OpenDocument` launcher registered while the ConfigScreen is composed).
- [x] **B.2** Roll-our-own CSV core (`CsvParser`): RFC-4180 line splitter (quoted fields, doubled-quote escaping, configurable delimiter); integer-exact amount parser (decimal/thousands separators, signed / parenthesised / trailing-minus) with **no floating point**; SHA-256 content-hash `externalId` (first 16 bytes) for re-import dedup. stdlib-only.
- [x] **B.3** `CsvDialect` model + presets: `GENERIC` (RFC-4180, comma/dot, ISO date, UTF-8) + `SPARKASSE_DE` (`;`/`,`, ISO-8859-1, dd.MM.yy) proving the per-bank-dialect sub-plugin model.
- [x] **B.4** `csv-import` generic-mapping plugin (`ConnectorPlugin`, category Import): ConfigScreen (account name + format chips + SAF "Pick CSV file", persisted to the per-plugin namespace); `fetch` reads on-device, parses with the chosen dialect, emits rows. Registered in `PluginManifest`.
- [x] **B.5** End-to-end on the Phase X runtime: Configure → "Test connection" activates → `fetch` → `RoomRowSink` → ledger, de-duplicated on `(accountRef, externalId)`. Network guard is a no-op for import (no calls), consistent with the zero-calls posture.
- [x] **B.6** Tests: `CsvParserTest` (6 — generic + Sparkasse parse, amount math/edge cases, quoted fields, dedup-hash determinism). 30 unit tests green overall. Plugins screen verified live (CSV import registered under Import). _ConfigScreen live-capture deferred: the AVD was concurrently driven by another session; the config/fetch path is covered by tests + the parser/ledger ends._

**Remaining banking connectors (future Phase B sub-plugins, same runtime):** CAMT.053 import, MT940 import, OFX import, QIF import; `fints-client`; per-bank PSD2-direct.

---

## Phase C — ledger schema + transaction store — shipped in commit 57f634f

**Goal:** Room schema for `AccountEntity`, `TransactionEntity`, `CategoryEntity`, and the join tables. `Money` value class implemented (integer minor units, currency-aware). Transactions are write-once-ish — edits land as new revisions with an `originalId` pointer.

**Gated on:** Phase B (so the schema accommodates real connector output) and partly on [`security-research.md`](security-research.md) (so encryption is baked in, not retrofitted). Built ahead of Phase B because the first connector (CSV import) needs the ledger to write into; the schema already carries the connector `externalId` dedup key + `accountRef`, so it accommodates real connector output.

- [x] **C.1** `Money` type — integer minor units (`Long`) + `CurrencyCode` value class; exact `+`/`-`/`*`/unary-minus via `Math.*Exact` (overflow throws, never wraps); cross-currency arithmetic rejected; `format()` via `NumberFormat.getCurrencyInstance(locale)` with the currency's own fraction digits. Pure Kotlin, no Android deps.
- [x] **C.2** Currency minor-unit digits resolved through `java.util.Currency` (EUR/USD 2, JPY 0, BHD 3, …); no hand-rolled tables.
- [x] **C.3** Room entities: `AccountEntity` (stable id = source accountRef, type/institution/hidden/order), `TransactionEntity` (money as `amountMinor`+`currency` columns for query power; unique `(accountId, externalId)` dedup index; `originalId` revision pointer), `CategoryEntity` (one-level parent hierarchy). FK + indices.
- [x] **C.4** DAOs: `AccountDao`, `TransactionDao` (dedup `INSERT OR IGNORE`, latest-revision views via `NOT EXISTS originalId`, balance sum, uncategorized count), `CategoryDao`.
- [x] **C.5** `LedgerDatabase` (v1, exportSchema) + enum `TypeConverters`; money/time stored as primitive columns (no converters). SQLCipher `openHelperFactory` slots in at the security phase, transparent to the schema.
- [x] **C.6** `LedgerRepository` interface + `RoomLedgerRepository`; domain models carry `Money`; `RoomRowSink` maps connector `NormalizedTransaction` → stored row, de-duplicated, auto-creating a minimal account. Wired into `AppGraph` (replaces the Phase X in-memory sink).
- [x] **C.7** Tests: `MoneyTest` (8 — arithmetic/overflow/cross-currency/format) + Robolectric `LedgerRepositoryTest` (5 — dedup, balance, latest-revision, uncategorized count, auto-account). 25 unit tests green overall.

---

## Phase D — multi-currency + FX rate cache — shipped in commit 01ad9d4

**Goal:** every `Money` round-trips through the FX cache when displayed in the user's base currency. FX rates fetched by whichever FX plugin(s) the user enables (ECB-direct shipped; Bundesbank-direct next — both off by default). Base-currency conversion is a `MoneyConverter`; the stored amount stays in the transaction's native currency. No FX plugin enabled → manual / per-transaction rates (intrinsic, no plugin).

- [x] **D.1** FX cache: `FxRateEntity` (EUR-paired, rate as **1e8-scaled Long** — no float) + `FxRateDao` (`upsert`, `rateOnOrBefore` dated lookup) + Room v1→v2 `MIGRATION_1_2` creating `fx_rates`.
- [x] **D.2** `MoneyConverter`: EUR-pivot cross-rates (`FROM→TO = (EUR→TO)/(EUR→FROM)`, inverse for `*→EUR`), latest-rate-on-or-before-the-transaction-date lookup, integer rate composition (only the final minor-unit scaling rounds); missing rate → null (UI shows a "no rate" badge, never a wrong number). `parseRateScaled1e8` parses decimal rate strings without float drift.
- [x] **D.3** `fx-ecb` plugin (`Fx` category): fetches the daily euro-reference-rates file **directly** from the ECB (no proxy) through the guarded HTTP client (tagged + accounted), parses it with an XXE-hardened JVM-stdlib DOM walker, emits EUR-paired `FxRateRow`s. No config (public keyless feed). Registered in `PluginManifest`.
- [x] **D.4** `RoomRowSink` routes `FxRateRow` → FX cache; repository `writeFxRate` / `fxRateOnOrBefore`.
- [x] **D.5** Tests: `MoneyConverterTest` (6 — identity, direct, inverse, cross-with-minor-digits, missing-rate, rate parse), `EcbFxParserTest` (2), FX-cache on-or-before (1). 39 unit tests green overall. _Live ECB-fetch capture deferred: AVD concurrently driven by another session; the network path is covered by the guard test, parse by the parser test, cache + conversion by their tests._

**Next FX source (future):** `fx-bundesbank` plugin (SDMX/CSV, broader pairs), same `Fx` category.

---

## Phase E — accounts + overview screens — shipped in commit dca21d3

**Goal:** lists every account across every bank, grouped by institution, with current balance per account (native currency); the Overview surfaces balances + activity. Tap into an account → transaction list (the tap-through detail is Phase F).

- [x] **E.1** Accounts screen: live per-account balances via an `AccountWithBalance` DAO projection (sum of current-revision transactions), grouped by institution, negative balances in the error colour, long names ellipsised. Empty state points to CSV import / connectors. Wired into the rail.
- [x] **E.2** Overview screen: balances summed **per currency** (no cross-currency net-worth without FX rates — never a wrong number; the converted total arrives once the ECB plugin is enabled), plus account count + uncategorized count. Replaces the Overview placeholder.
- [x] **E.3** Tests: Robolectric Compose screen tests (3 — accounts list shows name+balance, accounts empty state, overview per-currency + uncategorized count) against a fake repository. 42 unit tests green overall. _Live AVD capture deferred: the shared `medium_phone` is being driven by a concurrent strictlykeptboy QA session; screens are Robolectric-verified and land for on-device capture when it frees._

_Carry-forward (Phase F):_ tap-through account→transaction-list detail + base-currency per-bank subtotal once a base currency is chosen (Phase M) and FX rates are cached (Phase D plugin enabled).

---

## Phase F — transactions screen

**Goal:** chronological transaction list, filterable by account / category / date range / amount range. Each transaction row shows date, payee, category, amount (native + base-currency). Tap → detail sheet for category edit / note / split.

Sub-steps expand once Phases C / E land.

---

## Phase G — categories + categorization

**Goal:** the user can categorize transactions. Rules engine for auto-categorization (regex on payee, predicate on account, etc.). Uncategorized count is surfaced on the Overview.

Sub-steps expand once Phase F lands. Categorization rule engine is research-gated — it could be a rule sealed-type with hand-authored rules, or it could be a more elaborate predicate combinator; pick at design time.

---

## Phase H — budgets (research-gated)

**Goal:** the user can define budgets — envelope, category-monthly, or rolling — and see progress per period.

**Gated on:** an early design pass picking the budget shape(s) we support. Could be one shape or more than one. Sub-steps expand once the shape is chosen.

---

## Phase I — asset registry

**Goal:** the user can add an asset (real estate, vehicle, security, collectible, generic-with-history). Each asset has a current value, an acquisition cost, and a `ValuationEvent` history (date + value + source). Manual revaluation lands an event; automated revaluations from per-class connectors land events with `source = "<connector>"`.

**Gated on:** [`asset-research.md`](asset-research.md) and the per-class decision docs.

---

## Phase J — asset valuation connector plugins (research-gated)

**Goal:** per asset class, a `ConnectorPlugin` (in the `AssetPrice` or `RegionalIndex` category) that either fetches a current price (securities via per-broker plugins, crypto via per-exchange or on-chain RPC plugins, collectibles via Scryfall / pokemontcg.io / YGOPRODeck) or surfaces a regional-index hint at the manual revaluation moment (real estate via Häuserpreisindex / BORIS / UK HPI / FHFA HPI). All plugins ship disabled by default. Plugins land one at a time on top of the Phase X runtime, gated on per-class decision docs from [`asset-research.md`](asset-research.md) and the per-class `asset-*.md` files.

---

## Phase K — data visualization (research-gated)

**Goal:** the chart surfaces — net-worth-over-time line, cashflow Sankey, category treemap, currency-exposure donut, asset-class allocation, budget-progress bars, transaction histogram. Each chart is its own Composable; theming threads M3E tokens.

**Gated on:** [`dataviz-research.md`](dataviz-research.md) and its `dataviz-decision.md` output.

---

## Phase L — onboarding flow

**Goal:** first launch walks the user through: pick base currency → add first account (manual or via a chosen connector) → see the empty overview. Editorial register: plain, factual, useful — no vibes copy.

Sub-steps expand once Phases B / C / E land.

---

## Phase M — settings (full catalog)

**Goal:** every research-decision surface lands as a settings entry — base currency, **Settings → Plugins** (the per-plugin enable/configure/state UI from Phase X.6 — this is now the canonical home for every banking / asset / FX / import / regional-index connector's credential management and per-source toggles), **Settings → Plugins → Network usage** (the audit surface from Phase X.7), import-watch folders, theming, biometric-gate toggle, sample-data toggle (for screenshots / store listings).

Sub-steps expand alongside the research-round outputs.

---

## Phase N — home-screen widget (optional)

**Goal:** a small widget showing net-worth today, biggest income / biggest expense in the current month. Tap → opens the app.

Mirrors tonearmboy's Phase M. Sub-steps expand once Phase K's chart components stabilize.

---

## Phase O — release / Obtainium / GitHub Actions

**Goal:** `scripts/build-release-apk.sh --gh-release` ships a signed APK to a GitHub Release that Obtainium auto-detects. Same pipeline as tonearmboy / whisperboy / shutterboy — copy and adapt.

Sub-steps:

- [ ] **O.1** Copy `scripts/build-release-apk.sh` from whisperboy; rename `whisperboy` → `ledgerboy` everywhere.
- [ ] **O.2** Copy `.github/workflows/release.yml` (tag-only, self-disabling) from whisperboy; rename.
- [ ] **O.3** First release ships: `scripts/build-release-apk.sh --gh-release` produces `ledgerboy-<version>-<sha7>.apk` on the releases page.
- [ ] **O.4** Obtainium pickup verified end-to-end against the source URL in the README.

---

## Phase P — polish + edge cases

**Goal:** the unglamorous one. Long account names wrap correctly. Long amounts in non-base currency don't overflow the row. Empty states for every screen. Cold-start to first-paint stays under a research-decision budget. Accessibility audit (TalkBack on the rail, on the transaction list, on the chart legends).

Sub-steps expand once everything above lands.

---

## Phase Q — translations

**Goal:** first non-English locale lands. German (DACH, since the FinTS-client plugin + per-bank PSD2-direct DACH plugins are the v1 banking surface) is the natural first target.

Mirrors tonearmboy / whisperboy translations workflow — user + Claude per-language, English canonical, missing keys fall back to English at runtime, no third-party translation service.

Sub-steps expand once Phase L's onboarding strings stabilize.

---

## Phase R — easter egg

**Goal:** triple-tap on the launcher icon in About / Settings unlocks a small easter egg. Same shape as tonearmboy. Icon + easter egg image to be provided by the user later — leave a placeholder slot.

Sub-steps:

- [ ] **R.1** Triple-tap detector on the launcher-icon row (`AboutScreen` header), copied from tonearmboy's `EasterEggController.kt`.
- [ ] **R.2** Placeholder easter egg image at `app/src/main/res/drawable/easter_egg_placeholder.xml`.
- [ ] **R.3** User-provided icon + image swap-in.
