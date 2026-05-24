# ledgerboy — data visualization research

## Status: SEED — research-round agent picks up from here

Seed prompt for the next round of research on **Compose charting library**. Produces a single decision file: `docs/plans/dataviz-decision.md`. Phase K of [`main.md`](main.md) expands sub-steps after the decision lands.

The brief calls visualization "the interesting challenge." It's the surface the user is most explicitly excited about. The chart library choice cascades into every chart Composable; lock it before Phase K starts.

## Constraints

1. **Compose-native.** No `AndroidView`-wrapped legacy chart libs (MPAndroidChart and friends). The chart Composables must read M3E theme tokens and integrate with the rest of the UI.
2. **License gate.** MIT / Apache 2.0 / BSD / MPL 2.0. See [`oss-licenses.md`](oss-licenses.md).
3. **Maintenance gate.** Last release within 12 months OR active commit cadence.
4. **Dep weight.** APK size budget for the chart lib alone: ~500 KB. Anything larger needs to justify it.
5. **Custom interaction.** Tap to drill down, long-press to inspect a data point, pinch to zoom on time-series. The lib must allow these, ideally without monkey-patching.
6. **Accessibility.** Chart Composables must expose a `contentDescription` and a TalkBack-friendly summary (e.g. "Net worth chart, 12 data points, ranging from €X to €Y, last data point €Z"). Some libs make this easy; others don't.
7. **Theme integration.** Chart colours come from `MaterialTheme.colorScheme.*` tokens, not hard-coded. Per-asset-class accent colours come from ledgerboy's `CategoryAccent` (see [`m3-expressive.md`](m3-expressive.md)).

## Charts we know we need

The library has to comfortably support all of these:

- **Line chart** — net worth over time. Stacked-area variant for per-asset-class composition.
- **Sankey diagram** — cashflow (income → categories → out). **The hardest one** — most libs don't ship a Sankey. Could be its own canvas-rendered Composable on top of a generic-shapes lib.
- **Treemap** — category spending (current month or arbitrary range). Drill-down (tap a category → time-series for that category).
- **Donut / pie** — currency exposure, asset-class allocation.
- **Bar chart** — budget-progress bars (multi-series: budgeted vs. spent vs. remaining), transaction-frequency histogram.
- **Stacked bar** — monthly category breakdown over a year.
- **Scatter / bubble** — transaction-amount vs. date (optional; nice for outlier-spotting).

## Library candidates

(Starting list — research-round agent should grep Maven Central + GitHub for current alternatives.)

### Vico (`com.patrykandpatrick.vico`)

- License: Apache 2.0.
- Compose-native, Material 3 integration, active.
- Strong on line / bar / column / candlestick.
- Treemap, Sankey, donut: **not first-class**, would need custom Canvas.
- Verdict-direction: Yes for the basic charts; pair with custom Canvas Composables for Sankey/treemap.

### YCharts (`co.yml.charts`)

- License: Apache 2.0.
- Compose-native, broader chart-type coverage than Vico.
- Has donut/pie out of the box.
- Still no Sankey; treemap not standard.
- Verdict-direction: Plausible alternative to Vico if its theming is M3E-friendly.

### Compose Charts (`io.github.ehsannarmani.compose-charts`)

- License: check — could be MIT.
- Smaller / less mature.
- Verdict-direction: Long-shot; verify SPDX, maintenance, chart coverage.

### KMP-charts / cross-platform charting libs

- Various SPDX; check per fork.
- Multiplatform isn't a benefit for ledgerboy (Android-only) but doesn't hurt.

### Write our own with `Canvas`

- License: trivially MIT (it's ours).
- Heaviest implementation cost but maximum control. Sankey + treemap likely need this anyway.
- Verdict-direction: **Hybrid expected** — adopt a library (Vico or YCharts) for line/bar/donut, write Sankey and treemap from scratch on top of `Canvas` + `Modifier.pointerInput`.

## Questions for the research file

- For each candidate library: SPDX, latest release, last commit, dep size, M3E theming readiness, custom-interaction story, accessibility story.
- Concrete proof-of-concept: in the research file, sketch the API call that would render the **net-worth-over-time line chart** with the chosen lib. One-screen Compose snippet, theme tokens wired.
- For the Sankey + treemap (the libraries don't ship these): sketch the Canvas-Composable approach. Tile-laying algorithm for the treemap (squarified-treemap is the standard). Flow-routing algorithm for the Sankey.
- Accessibility: how does the chosen lib (or our Canvas Composable) expose data to TalkBack?

## Decision criteria (in priority order)

1. License + maintenance (gate; either it passes or it's out).
2. Coverage of line + bar + donut (the basic three — must be first-class in the library).
3. M3E theming integration (theme tokens flow through).
4. Custom-interaction friendliness (tap-to-drill, long-press inspect).
5. Dep weight (under ~500 KB ideally).
6. Sankey + treemap (we expect to write these ourselves anyway; library support is a bonus).

## What "done" looks like for this research round

- One decision file: `docs/plans/dataviz-decision.md`.
- The decision file picks a primary library (or "write our own"), justifies, sketches the net-worth-line API call, sketches the Sankey + treemap Canvas approach, and lists the strings/tokens/dimens we'll need.
- This file gets a "Decision: see dataviz-decision.md" pointer at the top.
- [`main.md`](main.md) Phase K gets sub-step checkboxes based on the decision.
