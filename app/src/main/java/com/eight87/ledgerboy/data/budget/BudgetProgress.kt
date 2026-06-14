package com.eight87.ledgerboy.data.budget

import com.eight87.ledgerboy.data.money.Money
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * H — budget progress for one category-monthly budget in a period.
 * [spent] is positive money out (the budget layer flips the negative
 * transaction sign); [fraction] is spent/limit clamped to [0,1] for the
 * bar, while [over] flags an exceeded budget regardless of the clamp.
 */
data class BudgetProgress(
  val categoryId: String,
  val categoryName: String,
  val limit: Money,
  val spent: Money,
  val fraction: Float,
  val over: Boolean,
)

object BudgetMath {
  /**
   * Compute progress from a budget limit and the net booked amount.
   * [netMinor] is the signed sum for the category (expenses negative);
   * spent = max(0, -net) so income/refunds don't show as negative spend.
   */
  fun progress(
    categoryId: String,
    categoryName: String,
    limit: Money,
    netMinor: Long,
  ): BudgetProgress {
    val spentMinor = (-netMinor).coerceAtLeast(0L)
    val fraction = if (limit.minorUnits <= 0L) 0f
      else (spentMinor.toDouble() / limit.minorUnits.toDouble()).coerceIn(0.0, 1.0).toFloat()
    return BudgetProgress(
      categoryId = categoryId,
      categoryName = categoryName,
      limit = limit,
      spent = Money(spentMinor, limit.currency),
      fraction = fraction,
      over = spentMinor > limit.minorUnits,
    )
  }
}

/** [start, end) epoch-millis bounds of the calendar month containing [epochMillis], in [zone]. */
object MonthRange {
  fun of(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
    val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val ym = YearMonth.from(date)
    val start = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val end = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return start to end
  }
}
