package com.eight87.ledgerboy.ui.nav

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf

/**
 * Per-section title shown in the root top app bar. Each destination sets
 * its own title on entry; the root scaffold reads this value and
 * re-renders. The default is the app name, overwritten before the user
 * sees anything because the root destination installs its title
 * immediately.
 */
val LocalSectionTitle = compositionLocalOf<MutableState<String>> {
  mutableStateOf("ledgerboy")
}
