package com.eight87.ledgerboy.data.charts

import com.eight87.ledgerboy.data.model.Transaction
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * K — pure chart-aggregation logic (no Compose, no Android), so the
 * numbers behind every chart are unit-tested independently of rendering.
 *
 * v0 aggregates a single [currencyCode] (default base EUR); transactions
 * in other currencies are excluded until the base-currency conversion
 * (Phase D `MoneyConverter` + a chosen base in Phase M) feeds the chart
 * layer. Documented limitation, not a wrong-number: mixed currencies are
 * never summed together.
 */
data class CategorySlice(val categoryId: String?, val label: String, val amountMinor: Long)
data class MonthCashflow(val label: String, val incomeMinor: Long, val expenseMinor: Long)

object ChartsData {

  /** Expense magnitude per category in [start,end) for [currencyCode], largest first. */
  fun spendingByCategory(
    transactions: List<Transaction>,
    categoryNames: Map<String, String>,
    startEpochMillis: Long,
    endEpochMillis: Long,
    currencyCode: String,
    uncategorizedLabel: String,
  ): List<CategorySlice> =
    transactions
      .filter { it.amount.currency.code == currencyCode }
      .filter { it.bookedAtEpochMillis in startEpochMillis until endEpochMillis }
      .filter { it.amount.minorUnits < 0 }
      .groupBy { it.categoryId }
      .map { (catId, txns) ->
        CategorySlice(
          categoryId = catId,
          label = catId?.let { categoryNames[it] } ?: uncategorizedLabel,
          amountMinor = txns.sumOf { -it.amount.minorUnits },
        )
      }
      .filter { it.amountMinor > 0 }
      .sortedByDescending { it.amountMinor }

  /** Income vs expense per month for the last [months] months ending at [nowEpochMillis]'s month. */
  fun monthlyCashflow(
    transactions: List<Transaction>,
    nowEpochMillis: Long,
    months: Int,
    currencyCode: String,
    zone: ZoneId = ZoneId.systemDefault(),
  ): List<MonthCashflow> {
    val nowYm = YearMonth.from(Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate())
    val fmt = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
    val inCurrency = transactions.filter { it.amount.currency.code == currencyCode }
    return (months - 1 downTo 0).map { back ->
      val ym = nowYm.minusMonths(back.toLong())
      val start = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
      val end = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
      val inMonth = inCurrency.filter { it.bookedAtEpochMillis in start until end }
      MonthCashflow(
        label = ym.atDay(1).format(fmt),
        incomeMinor = inMonth.filter { it.amount.minorUnits > 0 }.sumOf { it.amount.minorUnits },
        expenseMinor = inMonth.filter { it.amount.minorUnits < 0 }.sumOf { -it.amount.minorUnits },
      )
    }
  }
}
