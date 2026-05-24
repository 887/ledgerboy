# ledgerboy

Modern Android personal-finance / budget / multi-asset tracker. Built on Jetpack Compose + Room + a direct-from-device banking stack (FinTS/HBCI, PSD2 Open Banking direct-to-bank, file import) — every connector is a plugin, disabled by default. Same CLI-only build philosophy as the rest of the family: [tonearmboy](https://github.com/887/tonearmboy) (music), [whisperboy](https://github.com/887/whisperboy) (audiobooks), [shutterboy](https://github.com/887/shutterboy) (photos), [strictlykeptboy](https://github.com/887/strictlykeptboy) (calendar + todos), [pageboy](https://github.com/887/pageboy) (e-reader).

## Status

🟡 PRE-PHASE 0. Repo skeleton + research prompts only. See [`docs/plans/main.md`](docs/plans/main.md) for the phased build plan and [`docs/plans/seed-prompt.md`](docs/plans/seed-prompt.md) for the original brief. The detailed Phase B+ work is gated on banking / asset / dataviz / security research rounds — see the per-area `docs/plans/*-research.md` files.

## Goals

- **Track multiple accounts, multiple banks, multiple currencies.** Checking, savings, credit, brokerage, cash, crypto — all on one ledger surface.
- **Connect directly to banks where possible.** FinTS/HBCI (still widespread in Germany — shipped as a roll-our-own permissively-licensed client), per-bank PSD2-direct via Berlin Group NextGenPSD2 (each bank that publishes its own direct API gets its own plugin), file import (CSV / QIF / OFX / MT940 / CAMT.053) as the universal fallback. **No aggregator middlemen.**
- **Multi-currency.** FX rates from a published source — ECB daily reference rates direct from the ECB, or Bundesbank direct, both shipped as plugins disabled by default.
- **Budgets.** Envelope / category / rolling — the right shape gets picked after research; the data model accommodates more than one.
- **Asset tracking beyond cash.** Real estate, vehicles, securities, collectibles (e.g. trading cards), generic "asset with a manual valuation history" — each with periodic mark-to-market.
- **Data visualization is a first-class concern.** Net-worth-over-time, cashflow Sankey, category treemaps, currency-exposure, asset-class allocation, drill-down charts. Compose charting library is a research decision (see [`docs/plans/dataviz-research.md`](docs/plans/dataviz-research.md)).
- **Local-first.** Database lives on the device. Banking APIs are called directly from the device — nothing transits a server we operate.
- Modern stack: Kotlin + Compose + Material 3 Expressive + Room. No legacy Android Views, no Java.
- **Built entirely from the CLI**, no Android Studio required.

## Plugin architecture

Every connector — banking, asset prices, FX, file import, regional
indexes — is a plugin that ships disabled by default. Ledgerboy out
of the box makes zero network calls; the first call to any third
party comes from a plugin the user has explicitly enabled AND
configured.

- No aggregator middlemen (GoCardless, Tink, Plaid, Salt Edge,
  TrueLayer).
- No paid-SaaS pricing services (Alpha Vantage, finnhub.io,
  CoinGecko, CoinMarketCap).
- Direct-to-source-of-truth only — direct-to-bank PSD2, direct
  FinTS client, direct-to-broker, direct-to-exchange, direct
  on-chain RPC, ECB / Bundesbank direct, community open-data
  endpoints directly.

See [`docs/plans/connector-plugins.md`](docs/plans/connector-plugins.md)
for the architecture details.

## Non-goals (v1)

- Cloud sync. No accounts-on-our-server, no Plaid-style server side. Multi-device sync is a tracked stretch idea — see `docs/plans/main.md`.
- Investment recommendations / financial advice. Ledgerboy *records* and *visualizes*; it doesn't tell you what to do.
- Tax-filing integration. Out of scope. The data model should be exportable enough that other tools can pick it up.
- Crypto trading. Holdings tracking is in scope; placing orders is not.
- Web client / desktop client. Android-first; a desktop companion is a possible future.

## Privacy

Ledgerboy is **local-first by architectural commitment, not by accident.**

- **Nothing phones home until a plugin is enabled AND configured AND active.** Out of the box, a fresh install makes zero network calls — no telemetry, no startup pings, no plugin-update-check, no remote-manifest fetch. The first network packet to any third party comes from a plugin the user explicitly enabled, configured, and the scheduler (or a manual tap) triggered. See [`docs/plans/connector-plugins.md`](docs/plans/connector-plugins.md).
- The database lives on the device. The user can encrypt it (research-phase decision on SQLCipher vs. Android Keystore wrapping — see [`docs/plans/security-research.md`](docs/plans/security-research.md)).
- Banking API calls (FinTS / direct PSD2 per bank) go from the device **directly to the bank** — no aggregator middleman, no server we operate. Asset-price plugins go direct to broker / exchange / community open-data endpoints. FX plugins fetch direct from the ECB or Bundesbank.
- No telemetry, no analytics, no crash reporters phoning home by default. If a crash reporter ever lands, it's opt-in, and it ships with a clear list of what gets sent.
- Credentials (FinTS PINs, PSD2 OAuth refresh tokens, per-broker API keys) live in Android Keystore / SQLCipher-backed storage — never in plaintext, never in shared-preferences.
- Per-plugin transparency: Settings → Plugins shows every plugin's state, last fetch timestamp, bytes-in / bytes-out, and the actual endpoint URLs called. The user can audit exactly what is calling out, when, and to where.

If any of these change, this section gets revised in the same commit, not later. Local-first is a load-bearing claim and it has to stay honest.

## Why "ledgerboy"?

Sibling naming convention with **tonearmboy** (music), **whisperboy** (audiobooks), **shutterboy** (photos), **strictlykeptboy** (calendar + todos), **pageboy** (documents): *<object-from-the-medium> + boy*. The ledger is the central object of accounting — every banking API call, every transaction, every asset revaluation lands as a line in the ledger. "Kept in the ledger", "every line recorded", "accounted for" — the accounting register is natively kept-boy vocabulary. The "boy" suffix marks these apps as a family.

## Install on Android via Obtainium

[Obtainium](https://github.com/ImranR98/Obtainium) is an open-source Android
app store that pulls APKs directly from GitHub Releases. No Play Store, no
sideload dance, auto-update on every new release. Works on de-Googled Androids
(GrapheneOS / CalyxOS / LineageOS).

> **Note:** ledgerboy has not shipped a release yet. The instructions below
> describe the intended install path once Phase O lands.

### One-tap install (if Obtainium is already on your phone)

Tap this link on your phone:

[`obtainium://add/https%3A%2F%2Fgithub.com%2F887%2Fledgerboy`](obtainium://add/https%3A%2F%2Fgithub.com%2F887%2Fledgerboy)

Obtainium opens, prefills the source, and shows **Add**.

### Manual install

1. Install Obtainium from [F-Droid](https://f-droid.org/en/packages/dev.imranr.obtainium.fdroid/) or [its GitHub releases](https://github.com/ImranR98/Obtainium/releases/latest).

2. In Obtainium, tap **Add App** → paste this **Source URL**:

   ```
   https://github.com/887/ledgerboy
   ```

3. The other fields auto-detect, but if you need to set them by hand:

   | Field            | Value                  |
   | ---------------- | ---------------------- |
   | Source type      | GitHub                 |
   | APK filter regex | `^ledgerboy-.*\.apk$`  |
   | Update channel   | Releases               |

4. Tap **Add**. Obtainium fetches `ledgerboy-<version>-<sha7>.apk` from the latest release and offers Install. Future releases trigger an auto-update notification.

### Verifying a build

Each release ships a "Verify build" table in its notes with the APK SHA-256.
After installing, confirm what you got matches:

```bash
adb shell pm path com.eight87.ledgerboy           # find the installed APK on your device
adb pull <path-from-above> /tmp/installed.apk     # pull it back
sha256sum /tmp/installed.apk                      # compare to the release notes
```

---

The rest of this README is for **developers** — building locally, running the
AVD, running smoke tests, shipping a release. Skip if you just want the app.

## Prerequisites (one-time, Linux)

```bash
# Android CLI 0.7+
curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/android \
  -o ~/.local/bin/android && chmod +x ~/.local/bin/android
android --version          # self-bootstraps the runtime + bundled JDK 21

# SDK packages
android sdk install platforms/android-34 build-tools/34.0.0

# Test target — headless AVD
android emulator create --profile=medium_phone

# Mirror utility (optional but recommended for visual QA)
sudo pacman -S scrcpy      # Arch / Manjaro
# (or your distro's equivalent — Debian/Ubuntu: `sudo apt install scrcpy`)
```

The Android CLI also fetches the emulator binary and a system image on first
`android emulator create`. Expect ~600 MB of downloads on a fresh box.

## Run the AVD + scrcpy

```bash
scripts/start-avd.sh             # boot AVD if not running, then attach scrcpy
scripts/start-avd.sh --no-mirror # AVD only, no scrcpy window
scripts/start-avd.sh --kill      # stop both
```

The AVD boots headless (`-no-window -no-audio -no-snapshot`) for ~3 GB resident
RAM. `scrcpy` then mirrors the display to a host window (Wayland / X11) without
restarting the emulator. Once running, `adb devices` shows `emulator-5554`.

## Build + install

```bash
# Direct gradlew calls need both env vars (see CLAUDE.md for the why):
export JAVA_HOME=/usr/lib/jvm/java-26-openjdk
export ANDROID_HOME=$HOME/Android/Sdk

./gradlew assembleDebug
android run --apks=app/build/outputs/apk/debug/ledgerboy-debug.apk --device=emulator-5554
```

Or via the Android CLI directly (which handles the toolchain internally):

```bash
android run --apks=app/build/outputs/apk/debug/ledgerboy-debug.apk
```

## Build a release APK

The canonical happy path is **"phone-vibing"**: you're on your phone, you tell
Claude (in the Claude app) to ship a new build of ledgerboy. Claude opens a
session against this repo on your dev machine, runs:

```bash
scripts/build-release-apk.sh --gh-release
```

…and the new APK shows up on `https://github.com/887/ledgerboy/releases/latest`.
You then pull it to your phone via [Obtainium](#install-on-android-via-obtainium), which
auto-detects the new release and offers an in-place update. No Play Store, no
Android Studio, no manual `adb`.

## Sample data, screenshots, and the "money is private" rule

Every example, every fixture, every screenshot in this repo uses **fictional**
data. Round numbers, obviously-synthetic ("Acme Savings", "€10,000.00", "Test
Brokerage"). Never anything that could be mistaken for a real account.

If you (the developer / user) want to demo the app with your own data, that
happens locally — the demo data never gets committed. Sample-data generators
under `scripts/` emit synthetic figures only.

This is a hard rule, not a suggestion. See `CLAUDE.md` "Privacy posture — sample
data and screenshots" for the agent-facing version.

## Test

See [`CLAUDE.md`](CLAUDE.md) for the full Claude-driven test loop.

- **Unit / data layer** — Robolectric, JVM-only, zero device. Banking-format parsers (CAMT.053, MT940, OFX, QIF, CSV) and FX-conversion math are unit-test territory.
- **UI / integration** — [`mobile-mcp`](https://github.com/mobile-next/mobile-mcp) over ADB driving the headless AVD (or a real phone via wifi-adb).
- **Banking sandbox** — per-plugin decision. FinTS sandboxes, per-bank PSD2-direct sandboxes — never against real production credentials in automated tests.

## Translations

Translations are produced by the user + Claude per-language; English (`app/src/main/res/values/strings.xml`) is canonical and missing keys fall back to English at runtime. The table below is regenerated by `scripts/translation-progress.sh`.

<!-- TRANSLATIONS-START -->

| Language | Coverage | Status |
| --- | --- | --- |
| _(none yet — added per locale once Phase L lands)_ | — | — |

<!-- TRANSLATIONS-END -->

## License

MIT. See [`LICENSE`](LICENSE).
