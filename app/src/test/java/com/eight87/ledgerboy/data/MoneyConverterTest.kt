package com.eight87.ledgerboy.data

import com.eight87.ledgerboy.data.money.CurrencyCode
import com.eight87.ledgerboy.data.money.Money
import com.eight87.ledgerboy.data.money.MoneyConverter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** D.2 — multi-currency conversion via the EUR pivot. */
class MoneyConverterTest {

  // EUR→USD = 1.10, EUR→JPY = 160.00 (scaled 1e8).
  private val rates = mapOf(
    "USD" to 110_000_000L,
    "JPY" to 16_000_000_000L,
  )
  private val converter = MoneyConverter { base, quote, _ ->
    if (base == "EUR") rates[quote] else null
  }

  @Test
  fun `same currency is identity`() = runTest {
    val m = Money(1050, CurrencyCode.EUR)
    assertEquals(m, converter.convert(m, CurrencyCode.EUR, 0L))
  }

  @Test
  fun `eur to usd uses the direct rate`() = runTest {
    // €10.00 → $11.00
    val out = converter.convert(Money(1000, CurrencyCode.EUR), CurrencyCode.USD, 0L)
    assertEquals(Money(1100, CurrencyCode.USD), out)
  }

  @Test
  fun `usd to eur uses the inverse rate`() = runTest {
    // $11.00 → €10.00
    val out = converter.convert(Money(1100, CurrencyCode.USD), CurrencyCode.EUR, 0L)
    assertEquals(Money(1000, CurrencyCode.EUR), out)
  }

  @Test
  fun `usd to jpy crosses through eur and respects minor-unit digits`() = runTest {
    // $11.00 = €10.00 = ¥1600 (JPY has 0 fraction digits).
    val out = converter.convert(Money(1100, CurrencyCode.USD), CurrencyCode.JPY, 0L)
    assertEquals(CurrencyCode.JPY, out!!.currency)
    assertEquals(1600L, out.minorUnits)
  }

  @Test
  fun `missing rate yields null rather than a wrong number`() = runTest {
    val out = converter.convert(Money(1000, CurrencyCode.EUR), CurrencyCode("GBP"), 0L)
    assertNull(out)
  }

  @Test
  fun `rate string parses to 1e8 scaled without float drift`() {
    assertEquals(108_560_000L, MoneyConverter.parseRateScaled1e8("1.0856"))
    assertEquals(16_000_000_000L, MoneyConverter.parseRateScaled1e8("160"))
    assertEquals(100_000_000L, MoneyConverter.parseRateScaled1e8("1.00"))
    assertNull(MoneyConverter.parseRateScaled1e8("n/a"))
  }
}
