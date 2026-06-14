package com.eight87.ledgerboy.data.money

/**
 * Parse a user-entered major-unit amount ("12,50" / "12.5" / "1000")
 * into integer minor units for a currency with [fractionDigits] decimals.
 * Returns null on unparseable input. No floating point — string math.
 */
object MoneyInput {
  fun parseMajorToMinor(raw: String, fractionDigits: Int = 2): Long? {
    val s = raw.trim().replace(',', '.')
    if (s.isEmpty() || s.none { it.isDigit() }) return null
    val parts = s.split('.')
    if (parts.size > 2) return null
    val intPart = parts[0].ifEmpty { "0" }
    val fracRaw = parts.getOrNull(1) ?: ""
    if (!intPart.all { it.isDigit() } || !fracRaw.all { it.isDigit() }) return null
    val frac = when {
      fracRaw.length == fractionDigits -> fracRaw
      fracRaw.length > fractionDigits -> fracRaw.substring(0, fractionDigits)
      else -> fracRaw.padEnd(fractionDigits, '0')
    }
    return (intPart + frac).toLongOrNull()
  }
}
