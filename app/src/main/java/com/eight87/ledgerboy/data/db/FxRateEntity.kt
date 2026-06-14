package com.eight87.ledgerboy.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Query
import androidx.room.Upsert

/**
 * D.1 — the FX rate cache (banking-fx.md). Append-only daily reference
 * rates, EUR-paired (the ECB publishes EUR→X only; cross-rates are
 * computed via the EUR pivot in [com.eight87.ledgerboy.data.money.MoneyConverter]).
 *
 * The rate is carried as an integer **scaled by 1e8** (`Long`), never a
 * float — `1 EUR = rateScaled1e8 / 1e8 quote`. A transaction dated `t`
 * converts using the latest rate on-or-before `t`, so back-dated imports
 * convert correctly.
 */
@Entity(
  tableName = "fx_rates",
  primaryKeys = ["base", "quote", "asOf"],
  indices = [Index(value = ["base", "quote", "asOf"])],
)
data class FxRateEntity(
  val base: String,
  val quote: String,
  val rateScaled1e8: Long,
  val asOf: Long,
)

@Dao
interface FxRateDao {
  @Upsert
  suspend fun upsert(rate: FxRateEntity)

  /** Latest rate for [base]→[quote] on-or-before [asOf]; null if none cached. */
  @Query(
    """
    SELECT rateScaled1e8 FROM fx_rates
    WHERE base = :base AND quote = :quote AND asOf <= :asOf
    ORDER BY asOf DESC LIMIT 1
    """,
  )
  suspend fun rateOnOrBefore(base: String, quote: String, asOf: Long): Long?

  @Query("SELECT COUNT(*) FROM fx_rates")
  suspend fun count(): Int
}
