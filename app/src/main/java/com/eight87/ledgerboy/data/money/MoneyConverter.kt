package com.eight87.ledgerboy.data.money

/**
 * D.2 — converts a [Money] amount into a base currency for net-worth /
 * aggregate display, using the FX cache (banking-fx.md).
 *
 * The ECB publishes EUR-paired rates only, so cross-rates go through the
 * EUR pivot: `FROM→TO = (EUR→TO) / (EUR→FROM)`. The dated lookup uses the
 * latest rate **on-or-before** the transaction date, so back-dated
 * imports convert at the right historical rate.
 *
 * Stored amounts never pass through floating point. The *converted*
 * value is a derived display aggregate; the rate composition is integer
 * (scaled 1e8) and only the final minor-unit scaling uses a double +
 * round — the canonical stored amount stays integer.
 *
 * [rateLookup] returns the cached `base→quote` rate scaled by 1e8 for a
 * date, or null if uncached (→ conversion returns null, the UI shows a
 * "no rate" badge rather than a wrong number).
 */
class MoneyConverter(
  private val rateLookup: suspend (base: String, quote: String, asOfEpochMillis: Long) -> Long?,
) {
  suspend fun convert(amount: Money, to: CurrencyCode, asOfEpochMillis: Long): Money? {
    if (amount.currency == to) return amount
    val rateScaled = fromToRateScaled1e8(amount.currency.code, to.code, asOfEpochMillis) ?: return null

    val fromDigits = CurrencyCode.fractionDigits(amount.currency)
    val toDigits = CurrencyCode.fractionDigits(to)
    val majorFrom = amount.minorUnits.toDouble() / pow10(fromDigits)
    val majorTo = majorFrom * (rateScaled.toDouble() / SCALE)
    val toMinor = Math.round(majorTo * pow10(toDigits))
    return Money(toMinor, to)
  }

  /** Compose a FROM→TO rate (scaled 1e8) from EUR-paired cache entries. */
  private suspend fun fromToRateScaled1e8(from: String, to: String, asOf: Long): Long? {
    if (from == to) return SCALE
    if (from == EUR) return rateLookup(EUR, to, asOf)
    if (to == EUR) {
      val eurToFrom = rateLookup(EUR, from, asOf) ?: return null
      // FROM→EUR = 1 / (EUR→FROM)
      return SCALE * SCALE / eurToFrom
    }
    val eurToFrom = rateLookup(EUR, from, asOf) ?: return null
    val eurToTo = rateLookup(EUR, to, asOf) ?: return null
    // FROM→TO = (EUR→TO) / (EUR→FROM)
    return eurToTo * SCALE / eurToFrom
  }

  private fun pow10(n: Int): Double {
    var r = 1.0
    repeat(n) { r *= 10.0 }
    return r
  }

  companion object {
    const val SCALE = 100_000_000L // 1e8
    private const val EUR = "EUR"

    /** Parse a decimal rate string (e.g. "1.0856") into a 1e8-scaled Long, no float drift. */
    fun parseRateScaled1e8(raw: String): Long? {
      val s = raw.trim()
      if (s.isEmpty() || s.none { it.isDigit() }) return null
      val parts = s.split('.')
      if (parts.size > 2) return null
      val intPart = parts[0].ifEmpty { "0" }
      val fracRaw = parts.getOrNull(1) ?: ""
      if (!intPart.all { it.isDigit() } || !fracRaw.all { it.isDigit() }) return null
      val frac = when {
        fracRaw.length == 8 -> fracRaw
        fracRaw.length > 8 -> fracRaw.substring(0, 8)
        else -> fracRaw.padEnd(8, '0')
      }
      return (intPart + frac).toLongOrNull()
    }
  }
}
