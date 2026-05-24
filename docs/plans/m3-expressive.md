# ledgerboy — Material 3 Expressive (M3E)

## Status: INHERITED — start with the bugs already fixed

Ledgerboy ships M3E from day one. The five gotchas below were paid for in tonearmboy's first ship; they are transcribed here so ledgerboy doesn't pay for them again. **Read these before touching `LedgerboyTheme.kt` or any settings-row rendering code.**

Original source: `/home/laragana/workspace/tonearmboy/docs/plans/m3-expressive.md` ("Findings from the first ship").

## Inherited from tonearmboy: five gotchas

1. **`material3:1.4.0` keeps `MaterialExpressiveTheme` / `expressive*ColorScheme` `internal`.** The Compose BOM `2026.03.01` resolves to `material3:1.4.0` but you can't actually call the expressive APIs from there — the Kotlin metadata marks them `internal`, even though the JVM bytecode is public. Override the BOM in `gradle/libs.versions.toml` with `composeMaterial3 = "1.5.0-alpha18"`, the alpha that promoted the APIs to public. `expressiveDarkColorScheme()` does NOT exist in 1.5.0-alpha18 — only the `light` factory ships. Dark mode stays on `darkColorScheme(...)` and inherits the surface-tier ladder. Drop the override once 1.5.0 stable lands.

2. **`surfaceContainer` is too quiet on AMOLED-leaning dark palettes.** Tonearmboy started with `containerColor = surfaceContainer` and it was barely a step above `surface`. The ship uses `surfaceContainerHigh`. Light mode with `expressiveLightColorScheme()` reads better at `surfaceContainer`, so revisit if/when light mode gets a polish pass. **Default for ledgerboy: `surfaceContainerHigh` for card grouping on dark.**

3. **Auto-derive accent from `id` at the `SettingsRow` layer, not per call site.** Tonearmboy initially passed `accent = accentFor(entry.id)` from every binding's `Render` impl + from `SettingsScreen.kt`. That left direct `SettingsRow(...)` callers like `AboutScreen` / `LicensesScreen` (which don't go through bindings) monochrome. The fix is one line: `SettingsRow` falls back to `accentFor(id)` internally when caller passes `accent = null` and a non-null `id`. **Bake this into ledgerboy's `SettingsRow` from the first commit.**

4. **Android 12+ splash icon is hard circle-clipped, period.** The layer-list `android:windowBackground` workaround does NOT work — the system splash paints `windowSplashScreenBackground` over the whole window during launch, covering anything you set on `windowBackground`. The working approach: ship a dedicated splash mipmap (`ic_launcher_splash.png`) where the design is shrunk so it inscribes inside the system circle. **70.7 % (1/√2) is too tight** — corners still graze the mask. **60 %** gave proper headroom on the user's device. Set `windowSplashScreenIconBackgroundColor = launcher_background` so the bigger 240-dp icon area kicks in.

5. **Album-art tint in `Theme.kt` blends `surface`/`surfaceVariant`/`background` but NOT the `surfaceContainer*` ladder.** That's why tonearmboy's library list / detail cards looked uniformly tinted with the album palette while the page surface drifted. Ledgerboy doesn't have album art, but the *same trap* applies to any "dominant tint from a screenshot" or "tint a chart by category accent" theming experiment — blend the whole ladder, not just `surface`/`background`. (Probably not a near-term concern for ledgerboy; flagged for the future-self who reaches for it.)

## Four M3E patterns to land in `LedgerboyTheme.kt`

Drawn directly from tonearmboy's shipped Phases A–C:

1. **Surface tier ladder.** Use `surfaceContainerLowest` < `surfaceContainerLow` < `surfaceContainer` < `surfaceContainerHigh` < `surfaceContainerHighest`. Page background = `surface`. Card grouping = `surfaceContainerHigh` (dark) / `surfaceContainer` (light, when light mode lands). Elevation stays `0.dp`; the tier token does the lift, not a shadow.

2. **`MaterialExpressiveTheme(colorScheme, shapes, typography, motionScheme)`** as the theme root, not `MaterialTheme`. With the 1.5.0-alpha18 override (gotcha 1), no `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` is needed.

3. **Coloured circular row-icon avatars.** `data class CategoryAccent(val container: Color, val onContainer: Color)` alongside the theme. ~6 accent pairs in dark + light: pick categories that match ledgerboy's settings — proposed: `General` (sky blue), `Banking` (green), `Import` (purple), `Budgets` (peach), `Assets` (orange), `About` (pink). Hand-pick HSLs; don't drive off `dynamicDarkColorScheme()` (a green wallpaper would tint the Banking accent off-green, defeating the colour-coding). `SettingsCategoryIcon(icon, accent, contentDescription)` composable renders a 40-dp `Box(clip = CircleShape, background = accent.container)` with a 24-dp filled `Icon(tint = accent.onContainer)`.

4. **Filled glyph icons** at the settings-row layer. `androidx.compose.material:material-icons-extended` (no longer transitive in `material3:1.4.0+`, add explicitly). `Icons.Filled.*` reads weak in thin glyphs but heavy on a coloured circle background — that's the M3E shape.

## What to copy verbatim from tonearmboy

When Phase A lands the scaffold:

- `app/src/main/java/com/eight87/tonearmboy/ui/theme/Theme.kt` → `app/src/main/java/com/eight87/ledgerboy/ui/theme/Theme.kt`. Rename `TonearmboyTheme` → `LedgerboyTheme`. Swap the `CategoryAccent` value list for the ledgerboy six.
- `app/src/main/java/com/eight87/tonearmboy/ui/theme/CategoryAccent.kt` (if it's its own file in the current tonearmboy layout) — copy as-is.
- `app/src/main/java/com/eight87/tonearmboy/ui/theme/SettingsCategoryIcon.kt` — copy as-is.
- The `gradle/libs.versions.toml` `composeMaterial3 = "1.5.0-alpha18"` override.

## Out of scope here

- Light-mode polish: ledgerboy starts dark-first like the rest of the family. Light mode gets a pass once the dark surface looks right.
- Per-asset-class accent tinting on charts: a charts-phase concern, not a theme-phase concern. See [`dataviz-research.md`](dataviz-research.md).
- Dynamic colour (Material You wallpaper extraction): explicitly off. Hand-picked accents.
