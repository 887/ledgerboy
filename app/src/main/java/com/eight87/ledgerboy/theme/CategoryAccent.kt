package com.eight87.ledgerboy.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * m3-expressive Phase C — pair of `(container, onContainer)` colours for
 * the per-row coloured circle row-icon avatars. Mirrors Material 3's
 * `primaryContainer` / `onPrimaryContainer` token shape so the runtime
 * use site reads identically to a built-in colour role.
 *
 * Hand-picked spread (sky blue, green, purple, peach, orange, pink) maps
 * to ledgerboy's settings groups (General / Banking / Import / Budgets /
 * Assets / About). Rows pick by hashing their stable id ([accentFor]) so
 * the palette stays consistent across sessions / reinstalls without a
 * hand-mapping table. NOT driven off `dynamicDarkColorScheme()` — letting
 * the wallpaper steamroll the per-category intent defeats the colour
 * coding (m3-expressive.md, pattern 3).
 */
data class CategoryAccent(
  val container: Color,
  val onContainer: Color,
)

/** Dark / AMOLED-leaning palette: quietly-saturated tiles, bright glyphs. */
private val DarkPalette = listOf(
  CategoryAccent(container = Color(0xFF003D5C), onContainer = Color(0xFF8FCEFF)), // sky blue (General)
  CategoryAccent(container = Color(0xFF1F4D2E), onContainer = Color(0xFF9CDDB4)), // green (Banking)
  CategoryAccent(container = Color(0xFF3F2C73), onContainer = Color(0xFFD0BCFF)), // purple (Import)
  CategoryAccent(container = Color(0xFF5C3D24), onContainer = Color(0xFFFFC59A)), // peach (Budgets)
  CategoryAccent(container = Color(0xFF5C3300), onContainer = Color(0xFFFFB877)), // orange (Assets)
  CategoryAccent(container = Color(0xFF5C2940), onContainer = Color(0xFFFFB1C8)), // pink (About)
)

/** Light palette — same hues, brighter containers, darker glyphs. */
private val LightPalette = listOf(
  CategoryAccent(container = Color(0xFFCFE8FF), onContainer = Color(0xFF003047)),
  CategoryAccent(container = Color(0xFFC8E8D2), onContainer = Color(0xFF002912)),
  CategoryAccent(container = Color(0xFFE8DEF8), onContainer = Color(0xFF21005D)),
  CategoryAccent(container = Color(0xFFFFE2C9), onContainer = Color(0xFF2B1900)),
  CategoryAccent(container = Color(0xFFFFDDB6), onContainer = Color(0xFF2B1700)),
  CategoryAccent(container = Color(0xFFFFD8E5), onContainer = Color(0xFF3F0026)),
)

/**
 * Pick an accent for the row identified by [id]. Hash-based so the
 * mapping is stable across runs without a hand-built id→accent map.
 * `(raw + N) % N` avoids Kotlin's sign-preserving `%` on negative hashes.
 */
@Composable
@ReadOnlyComposable
fun accentFor(id: String): CategoryAccent {
  val palette = if (isSystemInDarkTheme()) DarkPalette else LightPalette
  val raw = id.hashCode() % palette.size
  val idx = (raw + palette.size) % palette.size
  return palette[idx]
}
