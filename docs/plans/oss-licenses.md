# ledgerboy — open-source licenses

## Status: PLANNED — Phase A lands plugin + UI

## Why

Ledgerboy ships under MIT (`LICENSE` in repo root, 2026 887). Every dependency that ships in the APK must be redistributable under terms compatible with MIT. Apache 2.0 §4 requires that downstream binary distributions preserve copyright + NOTICE entries from upstream artifacts. The Android-conventional way to satisfy this is an "Open-source licenses" sub-page on the About screen: a list of every shipping dep with name, version, license SPDX, and license body.

The licenses surface is also user-friendly: people who install ledgerboy from F-Droid / Obtainium and look at the About screen want to know what's underneath.

**Extra concern unique to ledgerboy:** banking and finance libraries on the JVM are disproportionately LGPL or GPL. **No GPL / AGPL / LGPL deps land in this app.** Every research-round candidate library gets its SPDX checked before adoption. If a candidate fails the gate, document the alternative (a different library, an upstream fork at a permissive licence, or build-it-ourselves).

## Approach (locked)

Same as tonearmboy / whisperboy:

- **Build-time inventory, zero runtime deps.** Use the [`app.cash.licensee`](https://github.com/cashapp/licensee) Gradle plugin. It walks the resolved `releaseRuntimeClasspath` at configuration time and writes a JSON inventory; nothing is added to the APK at runtime. Licensee is Apache 2.0 itself, build-time only.
- **Compose-rendered sub-screen.** A new `LicensesScreen.kt` reads the generated `artifacts.json` from `assets/licenses/` and renders a `LazyColumn` of M3-Expressive cards. Tapping a row reveals the license body. License bodies (Apache-2.0, MIT, BSD-3-Clause, MPL-2.0 if needed) ship as raw text assets — finite set, three to five at most.
- **Robolectric-driven catalog test.** Parses the generated JSON, asserts non-empty, asserts every entry has a known SPDX and a backing license-text asset, asserts a known sample of shipping deps is present. Catches accidental dep removal and unknown-license additions.
- **Allowlist:** `Apache-2.0`, `MIT`, `BSD-2-Clause`, `BSD-3-Clause`, **`MPL-2.0`** (the last is new vs. tonearmboy — included because at least one plausible finance / parsing library — possibly an OFX parser — is MPL 2.0. MPL 2.0 is file-level copyleft, compatible with MIT distribution as long as MPL files aren't modified in the binary. Adopt MPL deps only when there is no MIT / Apache / BSD alternative, and document the decision in the research file).
- **No GPL / AGPL / LGPL** — hard gate. If Licensee surfaces one in the inventory, the build fails the audit step (see Phase C).

## Phase A — Licensee plugin + generated inventory

**Why:** every later phase reads the JSON this phase generates.

- [ ] **A.1** Add Licensee plugin to `gradle/libs.versions.toml` (current stable line — verify at scaffold time).
- [ ] **A.2** Apply `alias(libs.plugins.licensee)` in `app/build.gradle.kts`.
- [ ] **A.3** Configure `licensee { allow("Apache-2.0"); allow("MIT"); allow("BSD-2-Clause"); allow("BSD-3-Clause"); allow("MPL-2.0") }`. Reporting-only at first; `failOnDisallowed` left default in Phase A and flipped to `true` in Phase C.
- [ ] **A.4** Wire `androidComponents.onVariants` to a per-variant `Copy` task that depends on `licenseeAndroid<Variant>` and feeds `merge<Variant>Assets`. Copies `build/reports/licensee/android<Variant>/artifacts.json` into `src/main/assets/licenses/`.
- [ ] **A.5** Source `Apache-2.0.txt`, `MIT.txt`, `BSD-3-Clause.txt` (and `MPL-2.0.txt`, `BSD-2-Clause.txt` as needed) from `https://spdx.org/licenses/<id>.txt` (canonical SPDX). Drop into `app/src/main/assets/licenses/`.
- [ ] **A.6** `:app:assembleDebug` clean. `assets/licenses/artifacts.json` parses, every entry has a SPDX in the allowlist. Spot-check a known shipping dep.

## Phase B — `LicensesScreen` Compose UI

**Why:** the user-facing surface that fulfils the Apache 2.0 NOTICE requirement.

- [ ] **B.1** Copy `LicensesScreen.kt` from tonearmboy (`app/src/main/java/com/eight87/tonearmboy/ui/settings/LicensesScreen.kt`). Rename package.
- [ ] **B.2** Load `assets/licenses/artifacts.json` once via `remember(context)`. No ViewModel — data is immutable per build.
- [ ] **B.3** `LicenseEntry { groupId, artifactId, version, spdxId, licenseText: String? }` — `licenseText` resolved by `assets/licenses/<spdx>.txt` lookup at construction; unknown SPDX → `licenseText = null` + row renders `licenses_unknown_spdx`.
- [ ] **B.4** `LazyColumn` of single-row `SettingsCard`s. Card title: `<artifactId> <version>`. Row label: `<groupId>`. Row subtitle: SPDX id. Tap → `AlertDialog` with monospaced license body, scrollable.
- [ ] **B.5** Navigation: `SettingsLicenses` route in `Destinations.kt`, wired into `LedgerboyApp.kt` + `SettingsSubPages.kt`. AboutScreen gains an `onLicenses` parameter and a `SettingsRow` "Open-source licenses".
- [ ] **B.6** Strings: `settings_about_licenses_label`, `settings_about_licenses_subtitle`, `licenses_screen_title`, `licenses_row_supporting`, `licenses_unknown_spdx`, `licenses_empty`, `licenses_dialog_close`. All in `values/strings_settings.xml`.
- [ ] **B.7** AVD smoke: About → Open-source licenses → list renders → tap a row → dialog shows monospaced license body.

## Phase C — Tests + audit discipline + the GPL gate

**Why:** keep the inventory honest as deps churn; enforce the GPL gate.

- [ ] **C.1** `LicensesCatalogTest` (Robolectric, JVM-only): parses `assets/licenses/artifacts.json`; asserts non-empty; asserts every entry has a SPDX from the allowlist; asserts a backing license-text asset exists; asserts the catalog contains known shipping samples.
- [ ] **C.2** `LicensesScreenTest` (Compose UI test under Robolectric): renders the screen, taps the first Apache-2.0 row, asserts the dialog body shows the SPDX text.
- [ ] **C.3** Flip `licensee { failOnDisallowed = true }` once the v1 inventory has been reviewed. Disallowed SPDX includes GPL-* / AGPL-* / LGPL-*.
- [ ] **C.4** AVD smoke: Settings → About → Open-source licenses → list renders → tap row → dialog text appears in monospace.
- [ ] **C.5** Document the new-dep workflow in `CLAUDE.md`: `./gradlew :app:licenseeAndroidDebug` after every new `implementation` dep; SPDX confirmed in allowlist; if a candidate fails the allowlist, the research file for that area documents the decision (alternative library / fork / build-our-own).

## Finance-library specific worries

This is the section that distinguishes ledgerboy's licenses plan from its siblings.

- **EBICS client libraries.** A research-time scan turned up `ebics-java` (active fork — Apache 2.0, last release recent), `kbank4j` (older — check SPDX), various unmaintained forks. The research file (`banking-ebics.md`) records SPDX per candidate.
- **FinTS / HBCI libraries.** `hbci4java` historically — LGPL. **That's a no.** Forks at permissive licences or building a thin subset of FinTS ourselves are the alternatives. Document in `banking-fints.md`.
- **OFX parsers.** Several mature parsers exist on Maven Central; some Apache 2.0, some MPL 2.0, occasionally LGPL. Document in `banking-import.md` per candidate.
- **CAMT.053 / MT940 parsers.** SWIFT-format parsers exist; check SPDX. Often the cleanest path is XSD-driven generation from the public ISO 20022 schemas (zero runtime dep — Apache 2.0 generated code lives in the repo).
- **Charting libraries.** Vico is Apache 2.0; YCharts is Apache 2.0; KMP-charts varies by fork. Document in `dataviz-decision.md`.
- **SQLCipher.** BSD-3-Clause. In the allowlist.

## Out of scope (revisit if pain emerges)

- A separate "Acknowledgements" screen for non-binary attributions (icon sets, font samples, FX-rate-source attributions). Add if/when one lands.
- Multi-locale license text. SPDX bodies are English-only by upstream convention.
- A DEPENDENCIES.md / NOTICE file in repo root duplicating the runtime list. The runtime screen is the canonical surface.
