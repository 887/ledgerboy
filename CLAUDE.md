# ledgerboy — Claude instructions

Modern Android personal-finance / budget / multi-asset tracker. Kotlin + Jetpack Compose + Material 3 Expressive + Room + DataStore + SAF + a direct-from-device, plugin-architected connector stack (FinTS roll-our-own + per-bank PSD2-direct + file import + per-broker / per-exchange / community-open-data asset connectors). Every connector is a plugin, disabled by default — see [`docs/plans/connector-plugins.md`](docs/plans/connector-plugins.md). Built entirely from the CLI, no Android Studio required, no QEMU emulator. Sibling app to [tonearmboy](https://github.com/887/tonearmboy), [whisperboy](https://github.com/887/whisperboy), [shutterboy](https://github.com/887/shutterboy), [strictlykeptboy](https://github.com/887/strictlykeptboy), [pageboy](https://github.com/887/pageboy) — same toolchain and conventions, different data model.

## Architectural decisions (locked)

- **Language:** Kotlin only. No Java.
- **UI:** Jetpack Compose. No Android Views.
- **Theming:** Material 3 Expressive (M3E) from day one. See [`docs/plans/m3-expressive.md`](docs/plans/m3-expressive.md) for the inherited tonearmboy gotchas — do not re-discover them.
- **UI shell, locked:** vertical navigation rail + top bar with action buttons + settings catalog DSL + AboutScreen / LicensesScreen. **Copy the patterns from tonearmboy verbatim** — see [`docs/plans/ui-shell.md`](docs/plans/ui-shell.md) for the file-path map. Research agents must not redesign these surfaces.
- **Data, store:** Room for the ledger (accounts, transactions, asset registry, asset valuations, budgets, FX rates). DataStore (Preferences) for user prefs. **Encrypted database is research-phase locked-pending** — SQLCipher vs. Android Keystore key wrapping, see [`docs/plans/security-research.md`](docs/plans/security-research.md). Default expectation: SQLCipher with the key wrapped in Android Keystore (Strongbox where available), biometric prompt gating optional.
- **Storage, import files:** **SAF only** (Storage Access Framework, `DocumentFile` + `OpenDocument` / `OpenDocumentTree`). No `READ_EXTERNAL_STORAGE`, no MediaStore. CSV / QIF / OFX / MT940 / CAMT.053 import files live wherever the user keeps them; SAF is the modern Android-correct path. See "Why SAF (not MediaStore)" below.
- **Settings, preferences:** DataStore (Preferences) for theme, default currency, base currency for net-worth view, FX rate source, refresh cadence, etc. Per-account settings (display order, hidden-from-net-worth flag, etc.) live on the `AccountEntity` row.
- **Connectors are plugins, disabled by default.** Every banking connector, asset-price connector, FX source, file-import parser, and regional-index helper is a plugin in the `app/src/main/java/com/eight87/ledgerboy/plugins/<plugin-id>/` namespace. Plugins are off by default; the user enables and configures per source. Nothing phones home until a plugin is `Active`. No aggregator middlemen (GoCardless, Tink, Plaid, Salt Edge, TrueLayer). No paid-SaaS pricing services (Alpha Vantage, finnhub.io, CoinGecko, CoinMarketCap). Direct-to-source-of-truth only. The license gate (MIT / Apache-2.0 / BSD / MPL-2.0) applies to plugin dependencies the same as to core deps because everything compiles into the single APK. See [`docs/plans/connector-plugins.md`](docs/plans/connector-plugins.md) for the full architecture.
- **Banking:** Direct-from-device, direct-to-bank. Per-bank PSD2-direct plugins (Berlin Group NextGenPSD2) + a single roll-our-own FinTS-client plugin (~3,300 LOC, permissively licensed) + per-format import plugins (CSV / QIF / OFX / MT940 / CAMT.053). Per-source decisions in [`docs/plans/banking-research.md`](docs/plans/banking-research.md) and the per-source `banking-*.md` files. Each connector implements the `ConnectorPlugin` interface (see `connector-plugins.md`) and emits normalized rows to the host.
- **FX rates:** Cache locally, refresh on a schedule. Two source plugins — ECB direct (`eurofxref-daily.xml`) and Bundesbank direct — both disabled by default. See `banking-fx.md`.
- **Asset valuation:** Per-class connector plugins. Real estate / vehicles / generic = manual + periodic (regional-index helpers like Häuserpreisindex / BORIS / UK HPI / FHFA HPI ship as plugins, off by default). Securities = per-broker plugins (IBKR TWS/Gateway, Trade Republic, Comdirect, etc.). Crypto = per-exchange plugins (Kraken / Coinbase / Binance read-only API keys) plus on-chain RPC plugins (user-supplied node URL). Collectibles = Scryfall / pokemontcg.io / YGOPRODeck plugins (free, direct, open-data — but still off by default). Each implements `ConnectorPlugin`; per-class research in [`docs/plans/asset-research.md`](docs/plans/asset-research.md) and the per-class `asset-*.md` files.
- **Charts:** Compose-native charting library, picked at research time — see [`docs/plans/dataviz-research.md`](docs/plans/dataviz-research.md). Candidates: Vico, YCharts, KMP-charts, Compose Charts, write-our-own with Canvas.
- **Build front-end:** [Google's Android CLI](https://developer.android.com/tools/agents/android-cli) (`android` command, launched April 2026). Wraps project creation, SDK management, build, install, and run. **Do not introduce Android Studio project files** (`.idea/`, `*.iml`).
- **Build back-end:** Gradle (driven by the Android CLI; the wrapper is committed to the repo).
- **Tests, unit:** Robolectric. JVM-only. No device required. Banking-format parsers (CAMT.053, MT940, OFX, QIF, CSV), FX math, ledger-balance computation, budget-rollover logic — all unit-test territory.
- **Tests, UI:** [mobile-mcp](https://github.com/mobile-next/mobile-mcp), Claude-driven over ADB. **Current target: headless AVD `medium_phone`** (Android 16 / API 36, RSS ~3.2 GB), started without window/audio/snapshot. Same AVD shared with the rest of the family. Phone via wifi-adb is the long-term home once biometric prompt / Strongbox / real-keystore behaviour matters.

### Why SAF (not MediaStore)

Import files (CSV / QIF / OFX / MT940 / CAMT.053) are typically downloaded into `/sdcard/Download/` or kept on SD card / OTG drives by the user. They are *opt-in*: the user wants to point ledgerboy at one specific export they just downloaded, not have the app scan every file on the device. MediaStore's all-or-nothing visibility is the wrong shape, and bank-statement filetypes aren't first-class MediaStore citizens anyway.

So: **SAF picker, persisted URI permissions where appropriate, `ACTION_OPEN_DOCUMENT` for one-shot imports, `ACTION_OPEN_DOCUMENT_TREE` for watch-this-folder workflows** (an optional Phase F+ idea — keep an eye on a Downloads folder for new statement files). Same pattern as whisperboy's SAF library; reuse the lessons (`CachedDocumentFile`-style caching if listing perf matters; persisted URI permissions survive app reinstall only if `--user 0` is preserved on `adb install -r`).

## Privacy posture — sample data and screenshots

**This is the load-bearing privacy rule for this repo. Read it. Future agents must inherit it.**

The user's personal financial posture (income, net worth, holdings, comp, bank relationships, asset values, debt) is **private**. None of it appears in anything public or LLM-readable. Ever.

Practical implications, enforced in every commit:

- **Sample data is fictional.** Always. Round numbers, obviously-synthetic names ("Acme Savings", "Test Brokerage", "€10,000.00", "Demo Card Collection"). Anything that could be confused with a real account, balance, or holding does not land in the repo.
- **Screenshots in the README, store listing, plan docs, and PR descriptions use the fictional sample data only.** If a screenshot accidentally contains real numbers it gets retaken before commit, not blurred.
- **Demos with real data happen locally.** The user may, on their own machine, point ledgerboy at their real bank export. That export, that database, those screenshots never enter version control. The `.gitignore` denies `*.csv` / `*.qif` / `*.ofx` / `*.mt940` / `*.camt053` outside `app/src/test/resources/fixtures/` precisely to make accidental commits hard.
- **Agents generating example transactions / budgets / asset values use obviously-fictional values.** No "looks plausible" amounts; explicit synthetic round numbers, brand-name placeholders, dates in the demo year (use 2026-01-01 through 2026-12-31).
- **No real bank names** in sample data unless the bank's own brand is unavoidable for a UI explanation (e.g. a PSD2-direct endpoint URL example for the per-bank plugin's `ConfigScreen()`). Even then, prefer the published sandbox example (e.g. `https://psd2-sandbox.example-bank.example/`).
- **No telemetry, ever, by default.** Crash reporting, usage analytics, "anonymous" feature usage — all opt-in, all transparent about payload contents, none of them on out of the box.

If a future agent surfaces real numbers — even accidentally, even in a comment, even in a unit test — strip them in the same review pass, no exceptions, no asking.

## Network posture — zero calls out of the box

Companion rule to the privacy posture, and the operational consequence of the plugin architecture (see [`docs/plans/connector-plugins.md`](docs/plans/connector-plugins.md)):

- **Out-of-the-box ledgerboy makes zero network calls.** No telemetry, no startup pings, no plugin-update-check, no remote-manifest fetch, no crash reporter, no analytics. A fresh install with no plugins enabled is, on the wire, completely silent.
- **The user enables every external endpoint by hand.** Plugins ship disabled. The state machine is `disabled → enabled → configured → active`, and only `active` plugins fetch. There is no "starter pack", no "recommended set", no first-run wizard that flips anything on automatically.
- **A `PluginNetworkGuard` OkHttp interceptor enforces this at the transport layer** — any HTTP request not originating from an `Active` plugin is rejected. This is the runtime guarantee that backs the documentation claim.
- **Per-plugin transparency is user-facing.** Settings → Plugins → Network usage shows per-plugin bytes-in / bytes-out / fetch count / error count / actual endpoint URLs called. Disable any plugin to unconditionally halt its network activity within one scheduler tick.
- **Agents must not introduce code that calls out without going through a plugin.** No `OkHttpClient` instances outside the guarded shared client. No "quick check" fetches in `Application.onCreate()`. No background telemetry. If a feature needs network, it lands as a plugin.

## Required CLIs and MCP servers

These are user-machine prerequisites. The plan tracks each in Phase 0.

### Android CLI

The new (April 2026) `android` command from Google wraps everything we need.

Install (userspace, this user's setup):

```bash
curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/android -o ~/.local/bin/android
chmod +x ~/.local/bin/android
android --version  # self-bootstraps the runtime on first call
```

The CLI bundles its own JDK 21 at `~/.android/cli/bundles/<hash>/jre/`. **Caveat:** the bundled JRE is *minimized* — it's missing modules including `java.rmi`, which Gradle 9.1's Kotlin DSL classpath fingerprinter loads. Direct `./gradlew` invocations against the bundled JRE will fail at configuration time with `java.lang.NoClassDefFoundError: java/rmi/Remote`. For direct Gradle calls, export `JAVA_HOME` to a full system JDK 17+ instead — for example `/usr/lib/jvm/java-26-openjdk` on this user's machine. Going through `android run` / other `android` subcommands is fine and uses the bundled toolchain internally.

Practical rule of thumb:
- `android run --apks=…` → just works.
- `./gradlew assembleDebug` → `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ANDROID_HOME=$HOME/Android/Sdk ./gradlew assembleDebug` (or equivalent system JDK 17+ path).

**Worktree caveat:** Gradle reads the SDK path from `local.properties`, which is gitignored. Worktrees created off `main` for subagents start without a `local.properties`, so direct `./gradlew` calls there will fail with `SDK location not found` unless `ANDROID_HOME` is exported (or `local.properties` is generated locally in the worktree). Always export `ANDROID_HOME=$HOME/Android/Sdk` alongside `JAVA_HOME` when invoking Gradle directly in an agent worktree.

Useful subcommands:

```bash
android create list                                  # browse project templates
android create --name=ledgerboy --output=. <template> # scaffold a new project
android sdk install platforms/android-34 build-tools/34.0.0
android run --apks=app/build/outputs/apk/debug/ledgerboy-debug.apk
android docs search <query>                          # query the Android Knowledge Base
android docs fetch <kb-url>                          # fetch a specific KB doc
android skills list --long                           # browse official Android skills
android info                                         # show detected SDK + version
```

`android docs search` is **the first place to look** when uncertain about Android APIs. It returns up-to-date guidance from the official Android Knowledge Base — beats grepping web search results, and is on-machine.

### `mobile` MCP server (UI driving)

Registered at **project scope** in `.mcp.json` (committed to the repo) and allowed in `.claude/settings.json` (also committed). When a Claude Code session starts in this repo with `enableAllProjectMcpServers: true` (set in the project settings), the `mcp__mobile__*` tools become available automatically.

To re-register on a fresh checkout if for any reason the project config drops the entry:

```bash
claude mcp add mobile --scope project -- npx -y @mobilenext/mobile-mcp@latest
```

What it gives you: list connected ADB targets, install APKs, launch the app, read the accessibility tree (the screen state, the way Playwright reads the DOM), tap by label / coordinates, assert UI state.

### `android-skills` MCP server (official Android skills)

Registered at **project scope** in `.mcp.json` and allowed in `.claude/settings.json`. Surfaces Google's official Android Skills (Compose migration, Navigation 3, Edge-to-Edge, AGP 9, R8 config, Material 3 Expressive patterns, etc.) as MCP tools inside Claude Code. **Consult these before hand-rolling any Android-specific pattern** that could be load-bearing on platform conventions.

To re-register on a fresh checkout:

```bash
claude mcp add android-skills --scope project -- npx -y android-skills-mcp
```

### Test target

One of:

- **wifi-adb to the user's phone** (preferred long-term — zero machine RAM cost, real Strongbox / biometric / Keystore behaviour, real SAF picker behaviour against actual storage):
  ```bash
  adb pair <ip>:<pair-port>      # pair once
  adb connect <ip>:<connect-port>
  adb devices                    # confirm
  ```
- **Headless AVD `medium_phone`** (Android 16 / API 36 — see Phase 0):
  ```bash
  ~/Android/Sdk/emulator/emulator -avd medium_phone \
    -no-window -no-audio -no-snapshot -no-boot-anim -gpu swiftshader_indirect &
  ```

For ledgerboy specifically, the AVD covers the ledger / charts / import / settings layer fine, but **biometric prompt + Strongbox-backed key generation + per-bank PSD2 OAuth + FinTS PIN/TAN handshake behaviour should be verified on a real phone** — the AVD's keystore is software-emulated and doesn't reflect Strongbox semantics; OAuth flows and per-bank certificate pinning against a sandbox bank work from the AVD but real-bank pinning + system-browser handoff are phone-only tests.

## Test loop

```bash
./gradlew assembleDebug
android run --apks=app/build/outputs/apk/debug/ledgerboy-debug.apk
# mobile-mcp tools take over for UI interaction
```

### UI changes are verified on the running AVD

Any change that touches Compose UI (layout, composable structure, navigation, theming, anything visible) MUST be verified by installing the rebuilt debug APK on the running headless AVD (`emulator-5554`) and inspecting the result — Robolectric unit tests do not catch real-device layout bugs (overflow on long account names / long amounts / long category labels, chart clipping, off-screen widgets under the nav rail, etc.).

Canonical loop:

```bash
JAVA_HOME=/usr/lib/jvm/java-26-openjdk ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app:assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/ledgerboy-debug.apk
adb -s emulator-5554 shell am start -n com.eight87.ledgerboy/.LedgerboyActivity
adb -s emulator-5554 exec-out screencap -p | magick - -resize 50% /tmp/ledgerboy.png   # then Read the PNG
```

The AVD is 1080x2400 native, which is too big to read comfortably — pipe screencaps through `magick - -resize 50%` to land at 540x1200 (quarter the pixels, easier to inspect, tap coords are still computed against the device's native 1080x2400, just multiply scaled image coords by 2). Skip the resize only when you genuinely need pixel-accurate detail.

Also: clean up `/tmp/*.png` periodically — these accumulate fast across sessions and a few hundred stale screenshots makes file listings noisy.

Prefer `mobile-mcp` tools when they're loaded in the session (they give the accessibility tree + tap-by-label, much more precise than coordinate input). When mobile-mcp isn't available, fall back to `adb exec-out screencap -p` + visual inspection of the PNG via the Read tool — it's lower-resolution evidence than the a11y tree but enough to confirm widget presence, position, and overflow behaviour.

Do not report a UI task as done on the strength of unit tests + a successful build alone.

For raw ADB inspection during dev:

```bash
adb logcat -s ledgerboy:* ledgerboy.scan:* ledgerboy.banking:*
adb shell am start -n com.eight87.ledgerboy/.LedgerboyActivity
```

### SAF-specific test-loop notes

Same caveats as whisperboy / strictlykeptboy: the SAF picker on the AVD is faithful but slow. Persisted URI permissions survive app reinstall *only if* `--user 0` is preserved on `adb install -r`. If a smoke test reports "tree URI permission lost", check that.

For ledgerboy specifically: import file fixtures live at `app/src/test/resources/fixtures/` (gitignored exception — see `.gitignore`). Each fixture is **fictional sample data**, named to make that obvious (`acme-savings-2026-q1.camt053.xml`, `test-brokerage-2026-jan.csv`).

## File conventions

- Single-module to start. Split into `:core` / `:data` / `:ui` / `:plugin-<id>` only when the single-module size warrants it; do not premature-modularize. Per-plugin sub-modules are the most plausible early split because individual connector plugins can pull non-trivial third-party deps; revisit after the first few plugins ship. Plugins themselves live under `app/src/main/java/com/eight87/ledgerboy/plugins/<plugin-id>/` either way — compiled in, listed in `PluginManifest`, never dynamically loaded.
- Package root: `com.eight87.ledgerboy`.
- Composable functions: PascalCase, no `@Composable` on private helpers unless they take a Modifier.
- ViewModels: one per screen, talk to the data layer via repository interfaces.
- No DI framework in v1 (Hilt/Koin/Metro) — pass dependencies as constructor params via a hand-rolled `AppGraph` composition root, the same pattern tonearmboy / whisperboy use. Add DI later if/when the manual wiring hurts.
- No reflection-based JSON. Use `kotlinx.serialization` if any serialization is needed. (CAMT.053 is XML — `kotlinx.serialization-xml` or a hand-rolled `XmlPullParser` walker; OFX is SGML-flavoured; QIF and CSV are line-oriented. Pick per-format at parser time.)
- **Money values are integer minor units** (`Long` cents / Yen / smallest-unit-per-ISO-4217). Never `Float`/`Double`/`BigDecimal` for amounts. FX conversion goes through a `Money` value class with a `Currency` field; converting between currencies returns a new `Money`, never mutates. This is the one thing finance apps screw up the most often; lock it from day one.

## Design principles — SOLID, applied to Kotlin + Compose

The codebase follows SOLID where it earns its keep. Kotlin + Compose change *how* the principles cash out (top-level functions instead of `interface ServiceImpl`, sealed types instead of Visitor, `Flow<T>` instead of Observer wiring), but the underlying tests still apply. **When introducing a new file or refactoring an existing one, sanity-check it against these five questions.** When in doubt, prefer the principle over the shortcut.

- **S — Single Responsibility.** A type / file / composable should have *one reason to change*. If you can describe what a class does without "and", "also", or "plus", you're probably fine. Soft heuristic: anything past ~500 LOC of non-trivial Kotlin deserves a second look; past ~800 LOC almost always needs splitting. The banking connectors are an early stress test — keep parser, transport, and credential-store concerns in separate files even within a single connector.
- **O — Open/Closed.** Prefer adding a new sealed-class case / new strategy implementation over modifying an existing `when`/`if` chain. The `BankConnector` interface + per-source sealed-class registry is the canonical example: new connectors land as new implementations + new registry entries, not as `if (source == "ebics") { ... }` chains.
- **L — Liskov Substitution.** Subtypes (or sealed-type variants) must honour the contract of the parent. Every `BankConnector` returns the same `List<NormalizedTransaction>` shape regardless of upstream format.
- **I — Interface Segregation.** Don't pass a fat type when a narrow one would do. The ViewModel that renders the budget screen takes a `BudgetSource` (a couple of methods), not the whole `LedgerRepository`.
- **D — Dependency Inversion.** High-level modules (UI, scheduled-refresh orchestration) depend on abstractions, not concrete classes. The `AppGraph` is the composition root — it's the *only* place that knows the concrete types (concrete `PluginManifest`, concrete `PluginNetworkGuard`, concrete `PluginScheduler`, concrete Room DAOs). Individual plugin instances are wired in via the manifest, not name-checked anywhere in the host.

## Open-source licenses

Every dep that ships in the APK is inventoried at build time by the [Licensee](https://github.com/cashapp/licensee) Gradle plugin (config block in `app/build.gradle.kts`); the resulting `artifacts.json` is copied into `app/src/main/assets/licenses/` and rendered by `LicensesScreen` (linked from About → "Open-source licenses"). Allowed SPDX ids: `Apache-2.0`, `MIT`, `BSD-2-Clause`, `BSD-3-Clause`, `MPL-2.0` (the last only if a banking library demands it and there's no Apache/MIT alternative — see [`docs/plans/oss-licenses.md`](docs/plans/oss-licenses.md)).

**License compatibility is load-bearing for ledgerboy more than for the other family members:** banking / finance libraries on the JVM are disproportionately LGPL or GPL. **No GPL/AGPL/LGPL deps land in this app — including transitive deps pulled in by any plugin.** Because every plugin compiles into the single APK (see [`docs/plans/connector-plugins.md`](docs/plans/connector-plugins.md)), an LGPL plugin dep would infect the whole APK. When evaluating a candidate connector library (FinTS client, OFX parser, MT940 parser, per-broker SDK), the SPDX gate is non-negotiable. If a candidate fails the gate, the research plan documents the alternative or the build-it-ourselves cost (this is exactly why FinTS ships as a roll-our-own client rather than wrapping `hbci4java`).

When adding a new `implementation` dep, run `./gradlew :app:licenseeAndroidDebug` and confirm the SPDX is in the allowlist. If not, either add it via `licensee { allow("…") }` (with a brief justification comment) or pick a different library.

## Plan files

- [`docs/plans/main.md`](docs/plans/main.md) — phased build plan. Phase 0 + Phase A have sub-step checkboxes; Phase X (plugin runtime) lands before Phase B; Phase B+ are stub headers gated on the per-area research plans.
- [`docs/plans/connector-plugins.md`](docs/plans/connector-plugins.md) — **locked authority** for the connector plugin architecture (disabled-by-default, no aggregators, no paid-SaaS pricing, direct-to-source). Every banking / asset / FX / import / regional-index decision threads through here.
- [`docs/plans/seed-prompt.md`](docs/plans/seed-prompt.md) — the user's original brief, verbatim, plus a family-context intro for fresh agents.
- [`docs/plans/ui-shell.md`](docs/plans/ui-shell.md) — prescriptive: copy tonearmboy's vertical-rail + top-bar + settings catalog DSL + AboutScreen + LicensesScreen. Research agents do not redesign these surfaces.
- [`docs/plans/m3-expressive.md`](docs/plans/m3-expressive.md) — inherited M3E gotchas from tonearmboy. Start with the bugs already fixed.
- [`docs/plans/oss-licenses.md`](docs/plans/oss-licenses.md) — Licensee plugin + LicensesScreen approach, with the extra license-compatibility gate for finance/banking libs.
- [`docs/plans/banking-research.md`](docs/plans/banking-research.md) — seed prompt for next-round research agents on bank connectivity. Produces one `docs/plans/banking-<source>.md` per source.
- [`docs/plans/asset-research.md`](docs/plans/asset-research.md) — seed prompt for next-round research agents on asset valuation. Produces one `docs/plans/asset-<class>.md` per class.
- [`docs/plans/dataviz-research.md`](docs/plans/dataviz-research.md) — seed prompt for next-round research agents on Compose charts. Produces `docs/plans/dataviz-decision.md`.
- [`docs/plans/security-research.md`](docs/plans/security-research.md) — seed prompt for next-round research agents on credential storage. Produces `docs/plans/security-decisions.md`.
- [`docs/plans/sharing-analysis.md`](docs/plans/sharing-analysis.md) — placeholder cross-family shared-code analysis. Revisit after Phase A.

When working on a phase:

- Tick its sub-steps (`- [x]`) in the same commit that lands the work.
- Add `shipped in commit <id>` to the phase header when *all* its sub-steps are ticked.
- Mark the whole plan `## Status: ✅ DONE` once every phase is ticked.
- If a phase header has no sub-step checkboxes, *write them first*. No vibes-based progress.

## Editorial — user-facing copy

The user follows Paul Graham's *Keep Your Identity Small*. App copy (settings descriptions, error messages, About text, onboarding, error toasts during bank handshakes) should be plain, factual, useful. No "vibes" copy, no personal opinions, no humor that pins identity. This applies double to anything that touches money — error messages around imports, balances, transactions, and bank connections must be calm, factual, and informative; no exclamation marks, no apology copy, no first-person commentary.

## i18n discipline + per-locale workflow

Same shape as tonearmboy / whisperboy. Two rules carry into every UI PR:

1. **Every user-facing string lands in `app/src/main/res/values/strings.xml` in the SAME COMMIT that introduces the surface.** No `Text("...")` with literal copy in `ui/**`. testTags / log tags / debug-only strings / format-string constants / internal sentinels stay literal — those are not user-facing.
2. **Naming scheme: `<surface>_<role>` lowercase snake** (e.g. `account_balance_label`, `budget_envelope_overspent`, `import_format_csv_description`). Surfaces are documented as a leading XML comment in `values/strings.xml`.

Currency formatting goes through Android's `NumberFormat.getCurrencyInstance(locale)` with the explicit `Currency` from the `Money` value class; never hand-format with a string template — sign placement, decimal separators, and minor-unit count vary by locale and currency.

## Release workflow

The user's intended pattern: **vibing from their phone with the Claude app**,
they tell Claude "ship a new build of ledgerboy." Claude opens a session against
this repo on the dev machine and runs the local build. The user then pulls the
APK via [Obtainium](https://github.com/ImranR98/Obtainium) on their phone,
which auto-detects the new GitHub Release.

**Local build is the primary path. Zero CI minutes by default.**

Canonical commands (mirroring the rest of the family — script lands in Phase O):

```bash
# Full one-shot: build + push to GH Releases + install on connected device
scripts/build-release-apk.sh --gh-release --install

# Just publish to GH Releases (Obtainium pulls from there)
scripts/build-release-apk.sh --gh-release

# Local APK only, no upload, no install
scripts/build-release-apk.sh
```

## Subagent dispatching

Subagents working on this repo run in worktrees. Each agent prompt must:

- name the phase + sub-steps it owns
- be told to tick checkboxes and add the commit ID (or jj change ID in jj repos) to the phase header as it lands work
- be told to keep the work scoped to its phase (no opportunistic refactors of unrelated code)
- be told to never modify `~/.claude/` files (those are not under this repo)
- be told to consult `android docs search <query>` before hitting general web search for Android API questions
- be told to consult the `android-skills` MCP for any pattern Google has codified (Compose migration, Navigation 3, edge-to-edge, etc.)
- be told that the **"money is private" rule (above) is non-negotiable** — fictional sample data only, no real numbers, no real bank names, no telemetry by default
- be told that the **UI shell is locked** — vertical rail / top bar / settings catalog DSL / AboutScreen / LicensesScreen all copy tonearmboy's shape; do not redesign these surfaces

For research-round agents specifically (the next wave dispatched into `docs/plans/banking-research.md` / `asset-research.md` / `dataviz-research.md` / `security-research.md`):

- Their deliverable is a markdown file under `docs/plans/`. No code yet.
- Every candidate library evaluated must include SPDX license, current maintenance status (last release date, last commit), Android compatibility (compile-SDK floor, transitive Android-incompatible deps), and a one-paragraph "fit for ledgerboy" summary.
- License gate: no GPL / AGPL / LGPL. MIT / Apache 2.0 / BSD / MPL 2.0 only. Document the gate decision in the research file.
- When the research delivers a recommendation, the recommendation lands as one `docs/plans/<area>-decision.md` (or `<area>-<source>.md`) and the parent research file gets a "Decision: see …" pointer at the top.
