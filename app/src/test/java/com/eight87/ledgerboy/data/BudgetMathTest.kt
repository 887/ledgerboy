package com.eight87.ledgerboy.data

import com.eight87.ledgerboy.data.budget.BudgetMath
import com.eight87.ledgerboy.data.budget.MonthRange
import com.eight87.ledgerboy.data.money.CurrencyCode
import com.eight87.ledgerboy.data.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/** H — budget progress math + month-range bounds. */
class BudgetMathTest {

  private val limit = Money(50000, CurrencyCode.EUR) // €500.00

  @Test
  fun `spent is the flipped expense sign and fraction clamps`() {
    // net -200.00 (expense) against a €500 limit → 40% spent, not over.
    val p = BudgetMath.progress("c", "Groceries", limit, netMinor = -20000)
    assertEquals(20000, p.spent.minorUnits)
    assertEquals(0.4f, p.fraction, 0.0001f)
    assertFalse(p.over)
  }

  @Test
  fun `income does not count as negative spend`() {
    // net +100.00 (refund/income) → spent 0.
    val p = BudgetMath.progress("c", "Groceries", limit, netMinor = 10000)
    assertEquals(0, p.spent.minorUnits)
    assertEquals(0f, p.fraction, 0.0001f)
  }

  @Test
  fun `over budget is flagged and the bar clamps to full`() {
    val p = BudgetMath.progress("c", "Groceries", limit, netMinor = -60000) // €600 spent
    assertTrue(p.over)
    assertEquals(1f, p.fraction, 0.0001f)
  }

  @Test
  fun `month range covers the whole calendar month`() {
    // 2026-01-15 UTC → [2026-01-01, 2026-02-01).
    val midJan = java.time.LocalDate.of(2026, 1, 15)
      .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val (start, end) = MonthRange.of(midJan, ZoneOffset.UTC)
    assertEquals(
      java.time.LocalDate.of(2026, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
      start,
    )
    assertEquals(
      java.time.LocalDate.of(2026, 2, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
      end,
    )
  }
}
