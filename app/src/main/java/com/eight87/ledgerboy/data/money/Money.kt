package com.eight87.ledgerboy.data.money

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * C.1 — the money type. Amounts are **integer minor units** (cents, yen,
 * fils, …) carried as a [Long], never a floating type and never
 * BigDecimal (CLAUDE.md money rule — the single thing finance apps get
 * wrong most often). The currency travels with the amount so a bare
 * `Long` can never be added to the wrong currency by accident.
 *
 * Arithmetic across currencies is rejected (you must convert through the
 * Phase D `MoneyConverter` first). Formatting goes through
 * `NumberFormat.getCurrencyInstance(locale)` with the explicit currency
 * so sign placement, separators, and minor-unit digits are correct per
 * locale — never a hand-rolled string template.
 *
 * Pure Kotlin, zero Android deps → fully JVM-unit-testable.
 */
data class Money(
  val minorUnits: Long,
  val currency: CurrencyCode,
) : Comparable<Money> {

  init {
    // No silent cross-currency state; the currency must be well-formed.
    require(currency.code.length == 3) { "currency must be a 3-letter ISO-4217 code, got '${currency.code}'" }
  }

  operator fun plus(other: Money): Money {
    requireSameCurrency(other)
    return copy(minorUnits = Math.addExact(minorUnits, other.minorUnits))
  }

  operator fun minus(other: Money): Money {
    requireSameCurrency(other)
    return copy(minorUnits = Math.subtractExact(minorUnits, other.minorUnits))
  }

  operator fun unaryMinus(): Money = copy(minorUnits = Math.negateExact(minorUnits))

  /** Scale by an integer factor (e.g. quantity × unit price already in minor units). */
  operator fun times(factor: Long): Money = copy(minorUnits = Math.multiplyExact(minorUnits, factor))

  val isZero: Boolean get() = minorUnits == 0L
  val isNegative: Boolean get() = minorUnits < 0L

  override fun compareTo(other: Money): Int {
    requireSameCurrency(other)
    return minorUnits.compareTo(other.minorUnits)
  }

  /**
   * Format for display in [locale] using the carried currency. The minor
   * units are converted to major units using the currency's own
   * fraction-digit count (2 for EUR/USD, 0 for JPY, 3 for BHD, …).
   */
  fun format(locale: Locale = Locale.getDefault()): String {
    val javaCurrency = Currency.getInstance(currency.code)
    val fmt = NumberFormat.getCurrencyInstance(locale).apply {
      this.currency = javaCurrency
      minimumFractionDigits = javaCurrency.defaultFractionDigits
      maximumFractionDigits = javaCurrency.defaultFractionDigits
    }
    val divisor = pow10(javaCurrency.defaultFractionDigits)
    val major = minorUnits.toDouble() / divisor
    return fmt.format(major)
  }

  private fun requireSameCurrency(other: Money) {
    require(currency == other.currency) {
      "cannot combine ${currency.code} with ${other.currency.code} — convert first"
    }
  }

  companion object {
    fun zero(currency: CurrencyCode) = Money(0L, currency)

    private fun pow10(n: Int): Long {
      var r = 1L
      repeat(n) { r *= 10 }
      return r
    }
  }
}

/**
 * ISO-4217 currency code wrapper. A value class so it costs nothing at
 * runtime but is type-distinct from a bare String (you can't pass an
 * account id where a currency is expected).
 */
@JvmInline
value class CurrencyCode(val code: String) {
  companion object {
    val EUR = CurrencyCode("EUR")
    val USD = CurrencyCode("USD")
    val GBP = CurrencyCode("GBP")
    val CHF = CurrencyCode("CHF")
    val JPY = CurrencyCode("JPY")

    /** Minor-unit digits for [code] per ISO-4217 (delegates to java.util.Currency). */
    fun fractionDigits(code: CurrencyCode): Int =
      runCatching { Currency.getInstance(code.code).defaultFractionDigits }.getOrDefault(2)
  }
}
