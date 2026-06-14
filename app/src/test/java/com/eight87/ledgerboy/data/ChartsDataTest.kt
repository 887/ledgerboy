package com.eight87.ledgerboy.data

import com.eight87.ledgerboy.data.charts.ChartsData
import com.eight87.ledgerboy.data.money.CurrencyCode
import com.eight87.ledgerboy.data.money.Money
import com.eight87.ledgerboy.data.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** K — chart aggregation logic. */
class ChartsDataTest {

  private fun txn(id: String, date: LocalDate, minor: Long, cat: String?, cur: String = "EUR") = Transaction(
    id = id, accountId = "a1",
    bookedAtEpochMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    amount = Money(minor, CurrencyCode(cur)), payee = id, description = null, categoryId = cat,
  )

  private val jan = LocalDate.of(2026, 1, 15)
  private val janStart = LocalDate.of(2026, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
  private val febStart = LocalDate.of(2026, 2, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

  @Test
  fun `spending by category sums expenses, ignores income and other currencies`() {
    val txns = listOf(
      txn("t1", jan, -3000, "food"),
      txn("t2", jan, -2000, "food"),
      txn("t3", jan, -1000, "rent"),
      txn("t4", jan, 5000, "food"),          // income — ignored
      txn("t5", jan, -9999, "food", "USD"),  // other currency — excluded
      txn("t6", LocalDate.of(2026, 2, 2), -7777, "food"), // out of range
    )
    val slices = ChartsData.spendingByCategory(
      txns, mapOf("food" to "Food", "rent" to "Rent"), janStart, febStart, "EUR", "Uncategorized",
    )
    assertEquals(2, slices.size)
    assertEquals("Food", slices[0].label) // largest first
    assertEquals(5000, slices[0].amountMinor)
    assertEquals(1000, slices[1].amountMinor)
  }

  @Test
  fun `uncategorized expenses get the fallback label`() {
    val slices = ChartsData.spendingByCategory(
      listOf(txn("t1", jan, -1200, null)), emptyMap(), janStart, febStart, "EUR", "Uncategorized",
    )
    assertEquals("Uncategorized", slices.single().label)
  }

  @Test
  fun `monthly cashflow splits income and expense per month`() {
    val nowMs = jan.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val txns = listOf(
      txn("t1", jan, 250000, "salary"),
      txn("t2", jan, -3000, "food"),
      txn("t3", jan, -2000, "rent"),
    )
    val flow = ChartsData.monthlyCashflow(txns, nowMs, months = 3, currencyCode = "EUR", zone = ZoneOffset.UTC)
    assertEquals(3, flow.size)
    val current = flow.last() // most recent month = January
    assertEquals(250000, current.incomeMinor)
    assertEquals(5000, current.expenseMinor)
  }
}
