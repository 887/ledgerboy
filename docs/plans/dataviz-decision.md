# ledgerboy — data-visualization decision

## Status: DECIDED — research round complete

> **Pick:** **[Vico](https://github.com/patrykandpatrick/vico) 3.1.0** (Apache-2.0) for line, column (stacked + side-by-side), pie, donut, and combo charts; **hand-rolled `Canvas` Composables** for Sankey, treemap, and bullet-style budget-progress bars. The hybrid keeps us on a single first-party-feel rendering stack: Vico's `CartesianChartHost` / `PieChart` Composables read M3 tokens via `rememberM3VicoTheme()` (a real, shipped function — see [`VicoTheme.kt`](https://github.com/patrykandpatrick/vico/blob/master/vico/compose-m3/src/commonMain/kotlin/com/patrykandpatrick/vico/compose/m3/common/VicoTheme.kt)), and our two custom Composables sit on top of the same `MaterialTheme.colorScheme` + `CategoryAccent` palette, so the visual language stays consistent. **Headline reason:** Vico is the only library in the candidate set that simultaneously clears the license gate, the maintenance gate (v3.1.0 shipped 5 Apr 2026, plus a 18 May 2026 patch on the compose-android module), the M3 gate (the M3 wrapper is a published artifact, not a community gist), and covers the four basic chart types we need first-class — and YCharts, the most plausible alternative, was **archived on 20 May 2026**.

## Library candidates evaluated

### Vico — `com.patrykandpatrick.vico:compose-android` (+ `:compose-m3-android`)

| Field | Value |
|---|---|
| SPDX | **Apache-2.0** ([POM](https://repo1.maven.org/maven2/com/patrykandpatrick/vico/compose-android/3.1.0/compose-android-3.1.0.pom), [SPDX](https://spdx.org/licenses/Apache-2.0.html)) |
| Latest release | **v3.1.0**, 5 Apr 2026 ([releases](https://github.com/patrykandpatrick/vico/releases)) |
| Last activity | compose-android / compose-m3-android updated 18 May 2026 ([README](https://github.com/patrykandpatrick/vico/blob/master/README.md)); master branch has 3,068+ commits |
| Dep weight | `compose-android-3.1.0.aar` = **935 KB** unminified on Maven Central; `compose-m3-android-3.1.0.aar` = 3.8 KB (theme wrapper only). Transitive deps are all already in our APK: Compose foundation/runtime/ui, `kotlinx.coroutines`, `kotlin-stdlib`, `androidx.annotation`. **R8-stripped contribution will be well under the 500 KB lib budget** since we only import line + column + pie layers. |
| M3 theming | First-class via [`rememberM3VicoTheme()`](https://github.com/patrykandpatrick/vico/blob/master/vico/compose-m3/src/commonMain/kotlin/com/patrykandpatrick/vico/compose/m3/common/VicoTheme.kt) — defaults are `MaterialTheme.colorScheme.{primary, secondary, tertiary}` for column/line/pie series, `colorScheme.outline` for axes, `colorScheme.onBackground` for text. Wrap chart subtree with `ProvideVicoTheme(rememberM3VicoTheme()) { ... }`. |
| Custom interaction | `CartesianChartHost` accepts `scrollState` / `zoomState`; markers via `CartesianMarker` with `rememberShowOnPress` / `toggleOnTap` hooks ([docs](https://guide.vico.patrykandpatrick.com/)). Pinch-to-zoom on time series works out of the box. Long-press inspect = stock marker behaviour. |
| Accessibility | **No first-class TalkBack support** — confirmed via docs query: "I cannot find any docs page for accessibility semantics specifically." We wrap each chart in our own `Modifier.semantics { contentDescription = … }` (pattern described in §7). |
| Chart coverage | Line, column (incl. stacked), candlestick, **pie / donut** (donut = pie with non-zero inner size, [guide](https://guide.vico.patrykandpatrick.com/compose/pie-charts)), combo. **No** sankey, treemap, scatter. |

### YCharts — `co.yml:ycharts`

| Field | Value |
|---|---|
| SPDX | Apache-2.0 |
| Latest release | 2.1.0, **30 Jun 2023** |
| Last activity | **Repository archived 20 May 2026, read-only** ([repo](https://github.com/codeandtheory/YCharts)) |
| **Verdict** | **Disqualified on maintenance gate.** Archived two weeks ago. Don't pick a deliberately-frozen finance-app dependency. |

### Compose Charts — `io.github.ehsannarmani:compose-charts`

| Field | Value |
|---|---|
| SPDX | **Apache-2.0** (per [GitHub](https://github.com/ehsannarmani/ComposeCharts); Maven Central shows MIT — discrepancy worth a fresh check at adoption time, but both pass our gate) |
| Latest release | 0.2.5, 12 Feb 2026 ([Maven Central](https://central.sonatype.com/artifact/io.github.ehsannarmani/compose-charts)) |
| Coverage | pie, line, column, row ([docs](https://ehsannarmani.github.io/ComposeCharts/)). **No donut listed**, no treemap, no sankey. |
| M3 theming | **Not documented.** Would need to verify via source. |
| Accessibility | **Not documented.** |
| **Verdict** | Smaller/younger (0.x), narrower chart-type coverage than Vico, no documented M3 integration. Not a reason to switch off Vico. |

### KoalaPlot — `io.github.koalaplot:koalaplot-core`

| Field | Value |
|---|---|
| SPDX | **MIT** ([repo](https://github.com/KoalaPlot/koalaplot-core)) |
| Latest release | v0.11.2, 3 May 2026 |
| Coverage | pie/donut, line, stacked-area, vertical bar, bullet, radar/polar/spider, **heatmap** ([Koala Plot](https://koalaplot.github.io/)). **No sankey, no treemap.** Bullet graph is nice for budget-progress — worth remembering. |
| M3 theming | Has its own `KoalaPlotTheme`, integrates with `MaterialTheme` — but no published `rememberM3KoalaPlotTheme()` equivalent; we'd map tokens manually. |
| **Verdict** | Strong second choice. Loses to Vico on M3 token integration ergonomics (no shipped wrapper) and on horizontal bar/column support being less developed for our budget-progress use case. Heatmap and bullet are nice bonuses but we don't need them in v1. Keep in pocket as fallback if Vico stalls. |

### Hand-rolled `Canvas` Composables (fallback)

| Field | Value |
|---|---|
| SPDX | trivially ours (Apache-2.0 to match the repo) |
| Coverage | unlimited (we write what we need) |
| **Verdict** | **Already in scope for Sankey + treemap regardless of library pick.** Pure-Canvas for all seven chart types would burn ~2-3 weeks of implementation for no real win — Vico's line/column/pie are battle-tested on millions of installs. Use Canvas only where Vico genuinely has no equivalent. |

## Decision rationale (scored against priority-ordered criteria)

| # | Criterion | Vico | YCharts | Compose Charts | KoalaPlot | Hand-rolled |
|---|---|---|---|---|---|---|
| 1 | License + maintenance (gate) | **PASS** (Apache-2.0, active May 2026) | FAIL (archived May 2026) | PASS (Apache-2.0, Feb 2026) | PASS (MIT, May 2026) | PASS |
| 2 | Line + bar/column + donut (must) | **YES** (all three first-class) | YES (but archived) | line + column + pie, **no donut** | YES (no horizontal bar) | DIY |
| 3 | M3 theming integration | **`rememberM3VicoTheme()` shipped** | none documented | none documented | manual mapping required | manual |
| 4 | Custom-interaction friendliness | **YES** (`scrollState`/`zoomState`/`rememberShowOnPress`) | basic | basic | YES (zoom/pan documented) | unlimited |
| 5 | Dep weight (<500 KB target) | 935 KB unminified AAR; **post-R8 well under 500 KB** for our subset | n/a | smaller | similar to Vico | zero |
| 6 | Sankey + treemap (bonus) | NO | NO | NO | NO | YES — and we have to write them anyway |

**Vico wins criteria 1-3 cleanly and is at-parity-or-better on 4-5. Criterion 6 is hand-rolled regardless.** Decision locks here.

## 4. Net-worth-over-time line chart — Compose snippet

Below is a complete, self-contained proof-of-concept. It assumes Vico 3.1.0 wired into `app/build.gradle.kts`:

```kotlin
// app/build.gradle.kts (excerpt)
dependencies {
    implementation("com.patrykandpatrick.vico:compose-android:3.1.0")
    implementation("com.patrykandpatrick.vico:compose-m3-android:3.1.0")
}
```

```kotlin
// app/src/main/kotlin/com/eight87/ledgerboy/ui/charts/NetWorthLineChart.kt
package com.eight87.ledgerboy.ui.charts

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eight87.ledgerboy.R
import com.eight87.ledgerboy.core.money.Money
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

/** A single net-worth observation. Money is integer minor units per CLAUDE.md. */
data class NetWorthPoint(val date: LocalDate, val amount: Money)

@Composable
fun NetWorthLineChart(
    points: List<NetWorthPoint>,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points) {
        modelProducer.runTransaction {
            lineModel {
                // Vico wants x as numeric; epoch-day keeps the time axis monotonic + sortable.
                series(
                    x = points.map { it.date.toEpochDay() },
                    y = points.map { it.amount.minorUnits.toDouble() / 100.0 },
                )
            }
        }
    }

    val a11ySummary = rememberNetWorthA11ySummary(points, locale)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .semantics { contentDescription = a11ySummary },
    ) {
        // rememberM3VicoTheme() pulls colorScheme.primary/secondary/tertiary as the
        // series palette and colorScheme.outline / onBackground for axes + labels.
        ProvideVicoTheme(rememberM3VicoTheme()) {
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(),
                    startAxis = VerticalAxis.rememberStart(),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        valueFormatter = { _, value, _ ->
                            LocalDate.ofEpochDay(value.toLong()).toString()
                        },
                    ),
                ),
                modelProducer = modelProducer,
                scrollState = rememberVicoScrollState(),
                zoomState = rememberVicoZoomState(),  // pinch-to-zoom on time-series
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun rememberNetWorthA11ySummary(
    points: List<NetWorthPoint>,
    locale: Locale,
): String {
    val ctx = LocalContext.current
    return remember(points, locale) {
        if (points.isEmpty()) {
            ctx.getString(R.string.chart_networth_a11y_empty)
        } else {
            val currency = Currency.getInstance(points.first().amount.currency.code)
            val fmt = NumberFormat.getCurrencyInstance(locale).apply { this.currency = currency }
            val min = points.minBy { it.amount.minorUnits }
            val max = points.maxBy { it.amount.minorUnits }
            val last = points.last()
            ctx.getString(
                R.string.chart_networth_a11y_summary,
                points.size,
                fmt.format(min.amount.minorUnits / 100.0),
                fmt.format(max.amount.minorUnits / 100.0),
                fmt.format(last.amount.minorUnits / 100.0),
            )
        }
    }
}

// Demo / @Preview wiring elsewhere uses fictional sample data:
//   val sample = listOf(
//     NetWorthPoint(LocalDate.parse("2026-01-01"), Money.eur(10_000_00L)),
//     NetWorthPoint(LocalDate.parse("2026-02-01"), Money.eur(10_500_00L)),
//     NetWorthPoint(LocalDate.parse("2026-03-01"), Money.eur( 9_800_00L)),
//     NetWorthPoint(LocalDate.parse("2026-04-01"), Money.eur(11_200_00L)),
//     NetWorthPoint(LocalDate.parse("2026-05-01"), Money.eur(11_750_00L)),
//   )
```

Required string-resource additions to `app/src/main/res/values/strings.xml`:

```xml
<!-- chart accessibility summaries -->
<string name="chart_networth_a11y_empty">Net worth chart, no data available.</string>
<string name="chart_networth_a11y_summary">Net worth chart, %1$d data points, ranging from %2$s to %3$s. Latest value %4$s.</string>
<string name="chart_sankey_a11y_summary">Cashflow diagram, %1$d sources flowing into %2$d destinations. Total flow %3$s.</string>
<string name="chart_treemap_a11y_summary">Spending treemap, %1$d categories totalling %2$s. Largest: %3$s at %4$s.</string>
```

## 5. Sankey Canvas-Composable sketch

A Sankey is a flow network: left-side nodes (income sources) and right-side nodes (spending categories), connected by curved bands whose thickness is proportional to flow value. Layout decomposes into (a) **node layout** — vertical position + height per node, (b) **band routing** — cubic Bezier from source-side stub to destination-side stub with thickness = value.

### Data model

```kotlin
data class SankeyNode(val id: String, val label: String, val accent: CategoryAccent)
data class SankeyFlow(val sourceId: String, val targetId: String, val amount: Money)

data class SankeyData(
    val sources: List<SankeyNode>,      // left column (income)
    val targets: List<SankeyNode>,      // right column (categories)
    val flows: List<SankeyFlow>,
) {
    val totalIn: Long get() = flows.sumOf { it.amount.minorUnits }
}
```

### Algorithm

1. **Compute node heights.** For each source node `s`, `heightPx(s) = totalAreaPx * (sum(flow.amount for flow.sourceId == s) / totalIn)`. Same for targets (sum of incoming flows). Apply a small inter-node gap (`gapDp = 4.dp`) so adjacent nodes don't visually fuse.
2. **Stack vertically.** Sort nodes by descending height on each side. Cumulatively place them top-to-bottom: `yTop(node_i) = sum(heightPx(node_j) + gapPx for j < i)`. Track each node's available "stub bar" so multiple flows out of one source stack within the source's vertical extent.
3. **Routing.** For each flow, pull a `(srcY, srcThickness)` stub off the source's remaining bar and a `(dstY, dstThickness)` stub off the destination's bar (equal thickness — they're the same flow). Draw a band as a closed path consisting of two cubic Beziers:
   - top edge: `(srcX, srcY) → C(srcX + dx, srcY, dstX - dx, dstY, dstX, dstY)`
   - bottom edge: `(dstX, dstY + thickness) → C(dstX - dx, dstY + thickness, srcX + dx, srcY + thickness, srcX, srcY + thickness)`
   - close path; fill with `flow.targetId` category accent at 0.55 alpha.

   `dx = (dstX - srcX) * 0.5` produces the classic smooth S-curve.

### Pseudocode

```kotlin
@Composable
fun SankeyChart(
    data: SankeyData,
    accents: CategoryAccentResolver,   // maps node.id → MaterialTheme.colorScheme-derived Color
    onFlowLongPress: (SankeyFlow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nodeWidth = 12.dp
    val gap = 4.dp
    val outline = MaterialTheme.colorScheme.outline
    val onSurface = MaterialTheme.colorScheme.onSurface
    val a11ySummary = rememberSankeyA11ySummary(data)

    Canvas(
        modifier
            .fillMaxSize()
            .semantics { contentDescription = a11ySummary }
            .pointerInput(data) {
                detectTapGestures(
                    onLongPress = { offset ->
                        // hit-test each band path; closest one wins
                        flowAt(offset, lastLayout)?.let(onFlowLongPress)
                    },
                )
            },
    ) {
        val layout = computeSankeyLayout(data, size, nodeWidth.toPx(), gap.toPx())
        // 1. draw node bars (left + right)
        layout.nodes.forEach { n ->
            drawRect(
                color = accents.colorFor(n.id),
                topLeft = Offset(n.x, n.y),
                size = Size(nodeWidth.toPx(), n.height),
            )
        }
        // 2. draw flow bands behind labels
        layout.flows.forEach { f ->
            drawPath(
                path = sankeyBandPath(f.srcX, f.srcY, f.srcThickness, f.dstX, f.dstY, f.dstThickness),
                color = accents.colorFor(f.targetId).copy(alpha = 0.55f),
            )
        }
        // 3. labels (drawText via nativeCanvas with onSurface colour)
    }
}
```

### Touch interaction

`Modifier.pointerInput { detectTapGestures(onLongPress = …) }` — on long-press, hit-test each band's `Path` via `android.graphics.Region(path).contains(x, y)`. The matched flow gets passed to a stateful overlay that shows source label → target label → formatted amount (calm copy, no exclamation marks, per the editorial rule in `CLAUDE.md`).

### M3 colour flow

Per-category accent comes from `CategoryAccent` (see `m3-expressive.md`), which derives from `MaterialTheme.colorScheme.{primary, secondary, tertiary, error}` plus a small palette of tonal variants. The Sankey uses the destination category's accent for the band (so the eye follows where money goes), with the source node bars in `colorScheme.primary` / `colorScheme.secondary` (income sources rarely number more than 2-3).

## 6. Treemap Canvas-Composable sketch

The standard layout is the **squarified treemap** ([Bruls, Huijing, van Wijk, 1999](https://www.win.tue.nl/~vanwijk/stm.pdf)), which lays out rectangles to keep aspect ratios near 1 — much more readable than slice-and-dice for finance category data where you want to compare proportions at a glance.

### Algorithm (squarified, paraphrased from the paper)

```
squarify(children: List<Item>, rect: Rect, row: List<Item> = []):
  if children empty: layoutRow(row, rect); return
  c = children.first()
  if row.isEmpty || worst(row + c, shortSide(rect)) <= worst(row, shortSide(rect)):
    squarify(children.drop(1), rect, row + c)
  else:
    layoutRow(row, rect)
    squarify(children, remainderRect(row, rect), [])

worst(row, shortSide):
  let s = sum(row.areas)
  let r_max = max(row.areas), r_min = min(row.areas)
  return max((shortSide^2 * r_max) / s^2, s^2 / (shortSide^2 * r_min))
```

`shortSide(rect)` = `min(rect.width, rect.height)`. `worst` is the maximum aspect ratio of the row if laid out along the short side — squarified minimizes it greedily.

### Data model + Composable

```kotlin
data class TreemapItem(
    val categoryId: String,
    val label: String,
    val amount: Money,
    val accent: Color,         // pre-resolved from CategoryAccent + MaterialTheme.colorScheme
)

@Composable
fun SpendingTreemap(
    items: List<TreemapItem>,
    onCategoryTap: (categoryId: String) -> Unit,   // navigates to per-category time-series
    modifier: Modifier = Modifier,
) {
    val sorted = remember(items) { items.sortedByDescending { it.amount.minorUnits } }
    val total = remember(sorted) { sorted.sumOf { it.amount.minorUnits } }
    val a11ySummary = rememberTreemapA11ySummary(sorted, total)
    val onSurface = MaterialTheme.colorScheme.onSurface

    var layout by remember { mutableStateOf<List<TreemapTile>>(emptyList()) }

    Canvas(
        modifier
            .fillMaxSize()
            .semantics { contentDescription = a11ySummary }
            .pointerInput(layout) {
                detectTapGestures(onTap = { offset ->
                    layout.firstOrNull { it.rect.contains(offset) }
                        ?.let { onCategoryTap(it.item.categoryId) }
                }),
            },
    ) {
        layout = squarify(sorted, Rect(Offset.Zero, size), total)
        layout.forEach { tile ->
            drawRect(color = tile.item.accent, topLeft = tile.rect.topLeft, size = tile.rect.size)
            drawRect(
                color = MaterialTheme.colorScheme.surface,  // 1px hairline gap
                topLeft = tile.rect.topLeft, size = tile.rect.size,
                style = Stroke(width = 1.dp.toPx()),
            )
            // drawText label only if tile.rect is big enough; truncate otherwise.
        }
    }
}

data class TreemapTile(val item: TreemapItem, val rect: Rect)
```

### M3 colour flow

Each tile fills with its category accent (resolved from the same `CategoryAccent` enum used elsewhere). Hairline gaps between tiles use `colorScheme.surface` to read against `surfaceContainerHigh` (the chart background). Labels in `colorScheme.onPrimaryContainer` (or `onSecondaryContainer` etc. matched to the accent role) for AA contrast.

### Drill-down

Tap → `onCategoryTap(categoryId)` → nav to `CategoryDetailScreen(categoryId)` which renders the same `NetWorthLineChart` Composable parameterised on that single category's monthly spend series. Same chart engine, same theme tokens — zero new visual language for the drill-down.

## 7. Accessibility plan

The rule across all chart Composables: **every chart exposes a single `contentDescription` summary string via `Modifier.semantics`.** TalkBack reads it as one block; we don't try to make individual data points navigable (that's a UX rat-hole on Canvas and Vico doesn't expose per-point semantics anyway).

| Chart | Hook | Summary template |
|---|---|---|
| Vico line / column / pie | `Modifier.semantics { contentDescription = … }` on the wrapping `Surface` (Vico has no per-series semantics) | `chart_networth_a11y_summary` etc. — count, min, max, last |
| Hand-rolled Sankey | `Modifier.semantics { contentDescription = … }` on the `Canvas` | `chart_sankey_a11y_summary` — source count, target count, total flow |
| Hand-rolled treemap | same | `chart_treemap_a11y_summary` — category count, total, largest category + its share |

For drill-down interactions we rely on standard tap semantics — `Modifier.clickable { onCategoryTap(…) }` (or `detectTapGestures` for the Canvas variants, with an explicit `Modifier.semantics { onClick(label = …) { … } }` so TalkBack announces "double-tap to view category detail").

Strings live in `values/strings.xml` per the i18n discipline in `CLAUDE.md`.

## 8. APK budget verification

| Item | Size estimate |
|---|---|
| `com.patrykandpatrick.vico:compose-android:3.1.0` AAR (unminified) | 935 KB |
| `com.patrykandpatrick.vico:compose-m3-android:3.1.0` AAR | 3.8 KB |
| Transitive deps (Compose / coroutines / kotlin-stdlib) | **0 KB net** — already pulled by the rest of the app |
| Vico contribution after R8 stripping (line + column + pie layers only; we don't import candlestick / combo) | **~250-400 KB realistic** |
| Hand-rolled Sankey Composable + layout algo (~250 LOC Kotlin) | ~15 KB compiled |
| Hand-rolled treemap Composable + squarify algo (~200 LOC Kotlin) | ~12 KB compiled |
| Hand-rolled bullet-graph for budget-progress bars (~120 LOC) | ~8 KB compiled |
| Chart accessibility-summary helpers (~100 LOC) | ~6 KB compiled |
| **Total chart-related APK contribution** | **~300-450 KB realistic, ~975 KB worst-case unminified** |

Comfortably under the ~1 MB ceiling. The R8 stripping assumption is load-bearing — Phase K should include a `--analyze` step on the release APK to confirm.

## 9. Phase K implementation sub-step checklist

This list goes verbatim (or near-verbatim) into [`main.md`](main.md) Phase K once a maintainer absorbs the decision.

- [ ] **K.1** Add Vico dependencies: `com.patrykandpatrick.vico:compose-android:3.1.0` and `com.patrykandpatrick.vico:compose-m3-android:3.1.0` to `app/build.gradle.kts`. Run `./gradlew :app:licenseeAndroidDebug`; confirm `Apache-2.0` lands in the allowlist.
- [ ] **K.2** Add chart-accessibility string keys to `values/strings.xml`: `chart_networth_a11y_empty`, `chart_networth_a11y_summary`, `chart_sankey_a11y_summary`, `chart_treemap_a11y_summary` (and the en-locale baseline values).
- [ ] **K.3** Create `ui/charts/ChartTheme.kt` — a single `@Composable fun LedgerboyChartTheme(content)` wrapper that calls `ProvideVicoTheme(rememberM3VicoTheme())` so every chart screen wraps in the same M3-token-bound theme.
- [ ] **K.4** Implement `ui/charts/NetWorthLineChart.kt` per §4 with sample-data `@Preview`. Verify on AVD via the canonical loop (build → install → screencap).
- [ ] **K.5** Implement `ui/charts/AssetAllocationDonutChart.kt` using Vico's `PieChart` Composable with `innerSize` > 0; per-asset-class accent colours from `CategoryAccent`.
- [ ] **K.6** Implement `ui/charts/BudgetProgressBar.kt` — hand-rolled `Canvas` (bullet-graph style: budgeted track in `surfaceContainerHighest`, spent fill in accent, "over-budget" portion in `colorScheme.error`).
- [ ] **K.7** Implement `ui/charts/MonthlyCategoryStackedBar.kt` using Vico's `ColumnCartesianLayer` in stacked mode; per-category accent colours.
- [ ] **K.8** Implement `ui/charts/Sankey/*` — the data model (`SankeyNode` / `SankeyFlow` / `SankeyData`), the layout algorithm (`computeSankeyLayout`), the band-path builder (`sankeyBandPath`), the `SankeyChart` Composable, the long-press overlay. Unit-test the layout algorithm on synthetic data.
- [ ] **K.9** Implement `ui/charts/Treemap/*` — squarified-treemap layout (`squarify`, `worst`, `layoutRow`), the `SpendingTreemap` Composable, tap-to-drill-down nav. Unit-test the squarify algorithm against the Bruls/Huijing/van Wijk reference example (the 7-item demo in the paper).
- [ ] **K.10** Wire all chart screens into `LedgerboyNavigation` (`InsightsScreen`, `CategoryDetailScreen`, etc., per `ui-shell.md`). Verify TalkBack announces each chart's summary on the AVD with `adb shell settings put secure enabled_accessibility_services com.android.talkback/.TalkBackService` (or via mobile-mcp a11y-tree dump).
- [ ] **K.11** Run a release-build APK size analysis (`./gradlew :app:bundleRelease` + `android` size analyzer) and confirm Vico's stripped contribution lands under 500 KB; if it doesn't, file a follow-up to ProGuard-shrink more aggressively (`-keep` rules on Vico's used classes only).
