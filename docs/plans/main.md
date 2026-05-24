# ledgerboy — main build plan

## Status: 🟡 PRE-PHASE 0 (skeleton + research prompts only)

_Repo skeleton, README, CLAUDE.md, this plan, and the per-area research prompts landed in the initial commit. No code yet. Phase 0 is the first phase that actually does anything; Phase B+ are gated on the research-round agents producing the per-area decision docs._

## Stack (locked)

- **Language:** Kotlin
- **UI:** Jetpack Compose + **Material 3 Expressive** (M3E from day one — inherited gotchas in [`m3-expressive.md`](m3-expressive.md))
- **UI shell:** vertical navigation rail + top bar with buttons + settings catalog DSL + AboutScreen + LicensesScreen — **copied from tonearmboy** ([`ui-shell.md`](ui-shell.md))
- **Data:** Room (ledger: accounts, transactions, asset registry, asset valuations, budgets, FX rate cache), DataStore (preferences), SAF / `DocumentFile` (import files — no MediaStore, no `READ_EXTERNAL_STORAGE`). Encrypted database is research-gated ([`security-research.md`](security-research.md)).
- **Banking:** Direct-from-device. EBICS + FinTS/HBCI + PSD2 + file import (CSV / QIF / OFX / MT940 / CAMT.053). Per-source research-gated ([`banking-research.md`](banking-research.md)).
- **Asset valuation:** Per-class research-gated ([`asset-research.md`](asset-research.md)). Real estate manual + periodic; collectibles / securities / vehicles each have their own connector decision.
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
2. Sub-step checkboxes inside each phase: `- [ ] **A.1** …`. A phase without sub-step checkboxes is forbidden — if you can't articulate sub-steps, write the design first.
3. Tick as you ship: `- [x]` AND a `shipped in commit <id>` note on the phase header at the same time the work lands. Do not batch ticks until the end. (In jj repos, use jj change IDs — alphabetic prefix — not git commit hashes; jj change IDs survive rebases.)
4. Mark the plan `## Status: ✅ DONE` once every phase is ticked. Obsolete plans get `## Status: OBSOLETE — superseded by …` instead.
5. Research-round agents produce one `docs/plans/<area>-decision.md` (or `<area>-<source>.md`) per gate; the parent research file gets a "Decision: see …" pointer at the top when the decision lands.

---

## Phase 0 — prerequisites (one-time, on the host)

Goal: a buildable host. These run once per developer machine. Tracked here so the environment is verifiable before agents go to work. Same machine as the rest of the family, so most are no-ops on this user's box, but tick them for completeness on a fresh checkout.

- [ ] **0.1** Install Google's Android CLI: `curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/android -o ~/.local/bin/android && chmod +x ~/.local/bin/android`. Likely already present from tonearmboy / whisperboy — verify with `android --version`.
- [ ] **0.2** `android sdk install platforms/android-34 build-tools/34.0.0` (and confirm `android-36` / `build-tools/36.0.0` present, since the project targets API 36 like its siblings). Likely already present.
- [ ] **0.3** JDK 17+ available on the host for direct `./gradlew` invocations (the bundled JDK 21 in the Android CLI is fine for `android run`, but direct Gradle calls need a full JDK — see CLAUDE.md). `/usr/lib/jvm/java-26-openjdk` is the working path on this user's box.
- [ ] **0.4** `mobile` MCP server registered at project scope in `.mcp.json` — already committed in the initial skeleton; verify with `claude mcp list` from inside the repo.
- [ ] **0.5** `android-skills` MCP server registered at project scope in `.mcp.json` — already committed in the initial skeleton; verify with `claude mcp list` from inside the repo.
- [ ] **0.6** Test target: headless AVD `medium_phone` (shared with the rest of the family). Created via `android emulator create --profile=medium_phone`. Should already exist; if not, `emulator -list-avds` will say so.

---

## Phase A — scaffold

Goal: a buildable, sideload-able APK that boots into a blank Compose screen wired to the vertical-rail shell from tonearmboy. Everything that follows assumes this exists. Mirror whisperboy Phase A almost exactly — same template choice, same gradle setup, same package convention — plus the tonearmboy UI-shell copy from [`ui-shell.md`](ui-shell.md).

- [ ] **A.0** Browse `android create list` and pick the closest official template. Default expectation: `empty-activity` (same template tonearmboy / whisperboy used).
- [ ] **A.1** `android create --name=ledgerboy --output=. <template>` from inside the repo root. Verify the generated layout: `app/`, `gradle/wrapper/`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`. Rename package from `com.example.ledgerboy` to `com.eight87.ledgerboy`. Rename theme to `LedgerboyTheme`. Rename activity to `LedgerboyActivity`.
- [ ] **A.2** Add baseline deps to `app/build.gradle.kts` via `libs.versions.toml`: Compose BOM (Material 3 Expressive override per [`m3-expressive.md`](m3-expressive.md)), Material Icons Extended, Lifecycle (runtime + viewmodel + compose), Activity Compose, Navigation 3, Room (runtime + ktx + KSP), DataStore Preferences, `androidx.documentfile`, `kotlinx-serialization-json`, Licensee Gradle plugin (per [`oss-licenses.md`](oss-licenses.md)).
- [ ] **A.3** `AndroidManifest.xml` adds: `INTERNET` (for banking APIs), `USE_BIOMETRIC` (for credential unlock — research-gated, declared early). No `READ_EXTERNAL_STORAGE` — SAF only. `minSdk = 28` (matching the family floor). `compileSdk = 36`, `targetSdk = 36`.
- [ ] **A.4** `.gitignore` covers Android build artifacts plus the bank-statement file extensions denied at repo root — already committed in the initial skeleton; verify the Phase-A-generated `app/.gitignore` (with `/build`) coexists cleanly with the root `.gitignore`.
- [ ] **A.5** Copy the **vertical-navigation-rail + top-bar** shell from tonearmboy per [`ui-shell.md`](ui-shell.md): `LedgerboyApp.kt`, `LedgerboyBackStack.kt`, `Destinations.kt`, `RouteScope.kt`, `RouteScopeFactory.kt`, `SectionTitle.kt`. Adapt content to ledgerboy's destinations (`Overview`, `Accounts`, `Budgets`, `Assets`, `Charts`, `Settings`). The destinations themselves are placeholder Composables in Phase A — real surfaces land in later phases.
- [ ] **A.6** Copy the **settings catalog DSL** scaffold from tonearmboy per [`ui-shell.md`](ui-shell.md): `SettingsCardDsl.kt`, `SettingsCatalog.kt`, `SettingsPagesRender.kt`, `SettingsScreen.kt`, `SettingsSubPages.kt`, plus a `catalog/sections/` directory with empty stub sections. AboutScreen + LicensesScreen ship at A.7.
- [ ] **A.7** Copy **AboutScreen.kt** and **LicensesScreen.kt** from tonearmboy. Wire LicensesScreen to the Licensee-generated `assets/licenses/artifacts.json` per [`oss-licenses.md`](oss-licenses.md).
- [ ] **A.8** Material 3 Expressive theming entry: `LedgerboyTheme.kt` with `MaterialExpressiveTheme(...)` wiring per [`m3-expressive.md`](m3-expressive.md) — apply the five inherited gotchas before they bite.
- [ ] **A.9** Build verification: `./gradlew assembleDebug` succeeds. APK lands at `app/build/outputs/apk/debug/ledgerboy-debug.apk`.
- [ ] **A.10** Install verification: `android run --apks=app/build/outputs/apk/debug/ledgerboy-debug.apk` launches `LedgerboyActivity` on `emulator-5554`. Vertical rail renders with the six placeholder destinations. Settings root opens, About opens, Licenses opens and shows the auto-generated dep inventory.

---

## Phase B — banking connectors (research-gated)

**Goal:** at least one bank connector is wired end-to-end, fetches a transaction list, and writes it into the ledger as `NormalizedTransaction` rows. The connector lives behind a narrow `BankConnector` interface; subsequent connectors land as new implementations.

**Gated on:** [`banking-research.md`](banking-research.md) and the per-source decision docs it spawns (`banking-ebics.md`, `banking-fints.md`, `banking-psd2.md`, `banking-import.md`). Sub-steps are deferred to the research output — when the first connector is chosen, expand the sub-step list here.

---

## Phase C — ledger schema + transaction store

**Goal:** Room schema for `AccountEntity`, `TransactionEntity`, `CategoryEntity`, and the join tables. `Money` value class implemented (integer minor units, currency-aware). Transactions are write-once-ish — edits land as new revisions with an `originalId` pointer.

**Gated on:** Phase B (so the schema accommodates real connector output) and partly on [`security-research.md`](security-research.md) (so encryption is baked in, not retrofitted). Sub-steps expand once Phase B's first connector decision is locked.

---

## Phase D — multi-currency + FX rate cache

**Goal:** every `Money` round-trips through the FX cache when displayed in the user's base currency. FX rates fetched on a schedule from the chosen source. Base-currency conversion is a `MoneyConverter` that the UI uses; the stored amount stays in the transaction's native currency.

**Gated on:** [`banking-research.md`](banking-research.md) (FX rate source decision lives in there). Sub-steps expand once the FX source is locked.

---

## Phase E — accounts screen

**Goal:** lists every account across every bank, grouped by bank, with current balance per account (in native currency) and a per-bank subtotal (in base currency). Tap into an account → transaction list.

Lands on top of the tonearmboy UI shell from [`ui-shell.md`](ui-shell.md). Sub-steps expand once Phases B–D's data shape is locked.

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

## Phase J — asset valuation connectors (research-gated)

**Goal:** per asset class, an `AssetValuationSource` connector that either fetches a current price (securities) or accepts a periodic manual revaluation (real estate / vehicles). Connectors land one at a time, gated on per-class decision docs from [`asset-research.md`](asset-research.md).

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

**Goal:** every research-decision surface lands as a settings entry — base currency, FX source + cadence, banking connectors (per-source credential management), import-watch folders, theming, biometric-gate toggle, sample-data toggle (for screenshots / store listings).

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

**Goal:** first non-English locale lands. German (DACH, since EBICS / FinTS heavy) is the natural first target.

Mirrors tonearmboy / whisperboy translations workflow — user + Claude per-language, English canonical, missing keys fall back to English at runtime, no third-party translation service.

Sub-steps expand once Phase L's onboarding strings stabilize.

---

## Phase R — easter egg

**Goal:** triple-tap on the launcher icon in About / Settings unlocks a small easter egg. Same shape as tonearmboy. Icon + easter egg image to be provided by the user later — leave a placeholder slot.

Sub-steps:

- [ ] **R.1** Triple-tap detector on the launcher-icon row (`AboutScreen` header), copied from tonearmboy's `EasterEggController.kt`.
- [ ] **R.2** Placeholder easter egg image at `app/src/main/res/drawable/easter_egg_placeholder.xml`.
- [ ] **R.3** User-provided icon + image swap-in.
