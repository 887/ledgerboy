package com.eight87.ledgerboy.ui.nav

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey

/**
 * A.5 — single-rooted back stack for ledgerboy.
 *
 * The rail switches the *root* destination (resetting the stack to that
 * single key); sub-pages push on top. Pressing back pops; back at a root
 * lets the activity finish. Same shape as tonearmboy's back stack, minus
 * the music-specific helpers.
 */
class LedgerboyBackStack(rootKey: Destination = Overview) {

  /** Flat back stack — what `NavDisplay` consumes. */
  val backStack: SnapshotStateList<NavKey> = mutableStateListOf<NavKey>(rootKey)

  /** Currently visible destination. */
  val current: NavKey
    get() = backStack.last()

  /** Push any destination onto the stack. */
  fun push(key: NavKey) {
    backStack.add(key)
  }

  /**
   * Pop the current entry. Used as `NavDisplay.onBack`. The root entry
   * cannot be popped — back at the root is a no-op so the system
   * back-press handler lets the activity finish.
   */
  fun pop() {
    if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
  }

  /**
   * Switch the rail root: collapse the whole stack down to [key] so the
   * rail acts like top-level tab switching (no accumulating history
   * across rail taps).
   */
  fun switchRoot(key: Destination) {
    backStack.clear()
    backStack.add(key)
  }
}
