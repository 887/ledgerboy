# ledgerboy — seed prompt

## Family context

Ledgerboy is the sixth member of a family of Android apps named `<object-from-the-medium>boy`. The siblings live under `/home/laragana/workspace/`:

- **tonearmboy** — music player
- **whisperboy** — audiobook player
- **shutterboy** — photo gallery
- **strictlykeptboy** — git-backed calendar + todos
- **pageboy** — e-reader / document viewer
- **ledgerboy** — this app: personal finance / budget / multi-asset tracker

The family shares a stack (Kotlin + Jetpack Compose + Material 3 Expressive + Room), a build philosophy (CLI-only — no Android Studio), a release pipeline (GitHub Releases + Obtainium, zero CI minutes by default), and a UI shell (vertical navigation rail + top bar + settings catalog DSL). It does **not** share a codebase — each app is independent. See `docs/plans/sharing-analysis.md` for the cross-family shared-code analysis (likely TBD until ledgerboy ships Phase A).

If you are a research-round agent walking into this repo cold: read `CLAUDE.md` first (architectural decisions + privacy posture), then this file, then `docs/plans/main.md`, then the per-area research file you've been assigned.

## The original brief (2026-05-24, verbatim)

> Budget/finance tracker — high utility, interesting data visualization challenge / ebics client, tracking multiple currencies and accounts, setting budgets, tracking multiple assets (house/apparment value, collectibles (trading cards) multiple banking apis

That's the whole thing. Six commas of intent. The brief expands to the following inferred surface:

### Banking connectivity

- **EBICS client** — Electronic Banking Internet Communication Standard. Heavy in DACH (Germany / Austria / Switzerland). SEPA payments, account statements (MT940 / CAMT.053), order submission. Requires per-user key generation, INI letter, bank-side activation handshake.
- **Multiple banking APIs beyond EBICS:** FinTS / HBCI (older German standard, still widely deployed); PSD2 Open Banking APIs (UK / EU, bank-agnostic, usually mediated by an aggregator — Tink, TrueLayer, Plaid, Salt Edge, GoCardless, Nordigen); screen-scrape adapters as a last resort.
- **File import:** CSV / QIF / OFX / MT940 / CAMT.053 — for banks that publish nothing else, and for one-off historical loads.

### Multi-currency

- **Tracking** in the transaction's native currency; **display** in a user-chosen base currency, converted through a cached FX rate table.
- **FX rate source:** likely ECB daily reference rates (free, public, EUR-centric); alternatives evaluated at research time (exchangerate.host, FX rates from banks themselves, etc.).
- **Refresh cadence:** research decision. Daily probably fine for non-trading purposes.

### Multiple accounts

- Checking, savings, credit, brokerage, cash, crypto — all on one ledger surface, grouped by bank in the UI.
- Per-account hidden-from-net-worth flag (for accounts the user wants tracked but doesn't want skewing the headline number — e.g. a corporate-card reimbursement holding account).

### Budgets

- Shape is a design decision: envelope / category-monthly / rolling / per-account / mix-and-match. Pick after a short design pass; the schema accommodates more than one.
- Forecasting is a stretch (project this month's spend on category X from N days of data).

### Asset tracking

- **Real estate** — house / apartment valuations with periodic mark-to-market. Probably manual valuation events; automated lookup is regional and legally grey (Immobilienscout24 scraping etc.).
- **Collectibles** — trading cards is the user's example. Pokémon / MTG / sports cards. Pricing feeds: TCGplayer, Cardmarket, Scryfall (MTG), pkmnTCG (Pokémon), comc (sports). Per-source ToS evaluation at research time.
- **Securities** — stocks, ETFs, funds. Yahoo Finance equivalents, Alpha Vantage, IEX, ECB equity data.
- **Vehicles** — likely manual + periodic, mirroring real estate.
- **Generic "other asset with manual valuation history"** — catch-all for things that don't fit a class.

### Data visualization

The brief calls this out as the "interesting challenge". Charts to land:

- Net-worth-over-time (line, with stacked overlay per asset class)
- Cashflow Sankey (income → categories)
- Category treemap (current month or arbitrary range)
- Currency-exposure donut
- Asset-class allocation (donut / bar)
- Budget-progress bars
- Transaction histogram (frequency × amount)
- Drill-down charts (tap a category in the treemap → time-series for that category)

Compose charting library to pick at research time — see `dataviz-research.md`.

## What the seed delivered

This initial commit ships:

- `LICENSE` (MIT, 2026 887)
- `.gitignore` (Android baseline + bank-statement-extension denylist with explicit fixtures-folder negation)
- `.mcp.json` (mobile + android-skills, project scope)
- `README.md` (user-facing — install via Obtainium, dev prerequisites, sample-data rule)
- `CLAUDE.md` (agent-facing — architectural decisions, privacy posture, test loop, subagent dispatching)
- `docs/plans/main.md` (phased plan, Phase 0 / A with sub-steps, B+ as research-gated stubs)
- `docs/plans/seed-prompt.md` (this file)
- `docs/plans/ui-shell.md` (locked: copy tonearmboy's shell)
- `docs/plans/m3-expressive.md` (inherited gotchas)
- `docs/plans/oss-licenses.md` (Licensee plugin + LicensesScreen + finance-lib-license gate)
- `docs/plans/banking-research.md` (next-round prompt — connectivity)
- `docs/plans/asset-research.md` (next-round prompt — asset valuation)
- `docs/plans/dataviz-research.md` (next-round prompt — charts)
- `docs/plans/security-research.md` (next-round prompt — credential storage)
- `docs/plans/sharing-analysis.md` (stub — revisit after Phase A)

No Kotlin code. No Gradle files. No `app/`, no `gradle/`. That's Phase A and belongs to the next round.

## What the next round does

Four research streams can run in parallel (different worktrees, different agents):

1. **Banking research** → `docs/plans/banking-research.md` → produces `docs/plans/banking-<source>.md` per connectivity source (ebics / fints / psd2 / import / fx). When a source is picked, Phase B of `main.md` can expand sub-steps and become buildable.
2. **Asset research** → `docs/plans/asset-research.md` → produces `docs/plans/asset-<class>.md` per asset class. Gates Phase J.
3. **Dataviz research** → `docs/plans/dataviz-research.md` → produces `docs/plans/dataviz-decision.md`. Gates Phase K.
4. **Security research** → `docs/plans/security-research.md` → produces `docs/plans/security-decisions.md`. Touches Phase A.3 (manifest permissions), Phase C (encrypted Room), Phase B (credential storage for connectors).

After the four research streams land, a separate agent can do **Phase A scaffold** — `android create … empty-activity`, package rename, theme rename, UI shell copy from tonearmboy, M3E wiring, Licensee plugin. Phase A is mechanical once the research has happened.
