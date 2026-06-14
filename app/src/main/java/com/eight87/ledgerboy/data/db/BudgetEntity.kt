package com.eight87.ledgerboy.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * H — a budget. v0 shape (per the Phase H design pass): **category-monthly**
 * — a recurring monthly spending limit on one category. Envelope and
 * rolling shapes are deferred behind the same `BudgetEntity` + a future
 * `period` discriminator; monthly is the one that pays its way first and
 * composes directly with the Phase G categories.
 */
@Entity(
  tableName = "budgets",
  foreignKeys = [
    ForeignKey(
      entity = CategoryEntity::class,
      parentColumns = ["id"],
      childColumns = ["categoryId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["categoryId"], unique = true)],
)
data class BudgetEntity(
  @PrimaryKey val id: String,
  val categoryId: String,
  /** Monthly limit in integer minor units. */
  val limitMinor: Long,
  val currency: String,
)

@Dao
interface BudgetDao {
  @Upsert
  suspend fun upsert(budget: BudgetEntity)

  @Query("DELETE FROM budgets WHERE id = :id")
  suspend fun delete(id: String)

  @Query("SELECT * FROM budgets")
  fun observeAll(): Flow<List<BudgetEntity>>

  /**
   * Net amount (minor units) booked to [categoryId] in [start, end) over
   * current-revision transactions. Expenses are negative; the budget
   * layer flips the sign to "spent".
   */
  @Query(
    """
    SELECT COALESCE(SUM(t.amountMinor), 0) FROM transactions t
    WHERE t.categoryId = :categoryId
      AND t.bookedAt >= :startEpochMillis AND t.bookedAt < :endEpochMillis
      AND NOT EXISTS (SELECT 1 FROM transactions r WHERE r.originalId = t.id)
    """,
  )
  suspend fun netForCategoryInRange(categoryId: String, startEpochMillis: Long, endEpochMillis: Long): Long
}
