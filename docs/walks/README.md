# ledgerboy — walks

**Walks** are persona-driven, replayable end-to-end QA scenarios — the
same concept as the sister app [strictlykeptboy](https://github.com/887/strictlykeptboy)'s
`docs/walks/`. Each walk puts a fictional user with a concrete goal in
front of ledgerboy and scripts the journey step by step (`taps X →
expects Y`), flagging confusion points as **[QA RISK]**. Together they
are the manual-QA corpus: a human (or an agent driving the AVD via
`mobile-mcp`) replays a walk and records what actually happened.

## Why walks

Unit + Robolectric tests prove the *logic*; walks prove the *experience*.
A walk catches the things tests miss — a balance that overflows a row, a
"coming soon" that should be wired, an empty state that dead-ends, an
import that silently does nothing. They also keep the app honest against
the **privacy posture**: every walk uses fictional data and verifies the
zero-network-calls-by-default promise where relevant.

## Structure

```
docs/walks/<persona-slug>/
├── personality.md          # who the user is, their devices, what they want
└── walk-<task>.md          # one numbered, replayable scenario per file
```

Personas span countries and finance situations (DE / US / UK / CH …) so
the walks exercise locale-specific shapes — German Sparkasse CSV dialects,
multi-currency freelancing, real-estate + vehicle assets, a privacy-first
manual-only user.

## Hard rules (inherited from CLAUDE.md)

- **Fictional only.** Persona names, banks, balances, holdings are
  invented and obviously synthetic (round numbers, `Acme Savings`,
  `Westvale Bank`, `€10,000.00`). **Never** the user's real name, email,
  employer, or real financial posture — money is private.
- **No real bank names** in sample data unless a sandbox example is
  unavoidable for a config explanation (then use a published sandbox URL).
- Demo dates live in the demo year (2026).

## Personas

| Persona | Country | Exercises |
|---|---|---|
| [de-sparkasse-saver](de-sparkasse-saver/) | DE | CSV import (Sparkasse dialect) → categories → rules → monthly budget |
| [us-freelancer-multicurrency](us-freelancer-multicurrency/) | US | EUR+USD accounts, ECB FX plugin, cashflow charts |
| [uk-property-investor](uk-property-investor/) | UK | Asset registry (real estate + vehicle), valuation history |
| [ch-privacy-minimalist](ch-privacy-minimalist/) | CH | Zero-network posture: install → confirm nothing phones home → manual entry |

## Running a walk

1. Build + install the debug APK on the AVD (`emulator-5554`) or a
   wifi-adb phone (see CLAUDE.md test loop).
2. Open the walk file and follow the numbered steps, driving the UI via
   `mobile-mcp` (tap-by-label) or `adb` taps.
3. Record outcomes inline / in a dated `RESULTS-<date>.md`; file any
   `[QA RISK]` that fires as a defect.
4. A walk is "green" only when a clean replay surfaces nothing new
   (QA + dev iterate until clean — CLAUDE.md).

## Status

Seed set authored 2026-06-14 (4 personas). Replays land as the screen
phases get on-device capture (the shared AVD is currently busy with a
sibling app's QA). See `docs/plans/main.md` Phase W.
