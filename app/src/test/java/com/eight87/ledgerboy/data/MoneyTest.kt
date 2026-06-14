package com.eight87.ledgerboy.data

import com.eight87.ledgerboy.data.money.CurrencyCode
import com.eight87.ledgerboy.data.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** C.7 — Money is integer-exact, currency-safe, and locale-formatted. */
class MoneyTest {

  private val eur = CurrencyCode.EUR

  @Test
  fun `addition and subtraction stay in minor units`() {
    val a = Money(1050, eur) // €10.50
    val b = Money(295, eur)  // €2.95
    assertEquals(Money(1345, eur), a + b)
    assertEquals(Money(755, eur), a - b)
  }

  @Test
  fun `scaling by integer factor`() {
    assertEquals(Money(300, eur), Money(100, eur) * 3)
  }

  @Test
  fun `negation and predicates`() {
    val neg = -Money(500, eur)
    assertEquals(Money(-500, eur), neg)
    assertTrue(neg.isNegative)
    assertTrue(Money.zero(eur).isZero)
  }

  @Test
  fun `cross-currency arithmetic is rejected`() {
    val e = assertThrows(IllegalArgumentException::class.java) {
      Money(100, CurrencyCode.EUR) + Money(100, CurrencyCode.USD)
    }
    assertTrue(e.message!!.contains("convert first"))
  }

  @Test
  fun `comparison requires same currency`() {
    assertTrue(Money(200, eur) > Money(100, eur))
    assertThrows(IllegalArgumentException::class.java) {
      Money(1, CurrencyCode.EUR).compareTo(Money(1, CurrencyCode.JPY))
    }
  }

  @Test
  fun `overflow throws rather than wrapping`() {
    assertThrows(ArithmeticException::class.java) {
      Money(Long.MAX_VALUE, eur) + Money(1, eur)
    }
  }

  @Test
  fun `formatting uses currency fraction digits`() {
    // EUR has 2 fraction digits → 1050 minor = 10.50.
    val eurStr = Money(1050, CurrencyCode.EUR).format(Locale.GERMANY)
    assertTrue("got '$eurStr'", eurStr.contains("10,50"))
    // JPY has 0 fraction digits → 1050 minor = ¥1,050.
    val jpyStr = Money(1050, CurrencyCode.JPY).format(Locale.JAPAN)
    assertTrue("got '$jpyStr'", jpyStr.contains("1,050"))
  }

  @Test
  fun `malformed currency code is rejected`() {
    assertThrows(IllegalArgumentException::class.java) {
      Money(1, CurrencyCode("EURO"))
    }
  }
}
