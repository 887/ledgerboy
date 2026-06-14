package com.eight87.ledgerboy.ui.settings

/**
 * R.1 — pure-Kotlin state machine for the build-version easter egg.
 *
 * The user taps the build row in About; the first two taps surface a
 * prompt, the third reveals the easter egg (a placeholder slot until the
 * user provides the image at R.3). Taps more than [windowMillis] after
 * the previous one reset the counter. Framework-free so a Robolectric
 * test can drive it with a synthetic clock — the owning Compose layer
 * passes the wall clock into [tap].
 */
class EasterEggController(
  private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
) {
  sealed class Outcome {
    data object FirstPromptSnackbar : Outcome()
    data object SecondPromptSnackbar : Outcome()
    data object Reveal : Outcome()
  }

  private var counter: Int = 0
  private var lastTapAtMillis: Long = 0L

  fun tap(nowMillis: Long): Outcome {
    if (counter > 0 && (nowMillis - lastTapAtMillis) > windowMillis) {
      counter = 0
    }
    counter += 1
    lastTapAtMillis = nowMillis
    return when (counter) {
      1 -> Outcome.FirstPromptSnackbar
      2 -> Outcome.SecondPromptSnackbar
      else -> {
        counter = 0
        Outcome.Reveal
      }
    }
  }

  internal fun debugCount(): Int = counter

  companion object {
    const val DEFAULT_WINDOW_MILLIS: Long = 5_000L
  }
}
