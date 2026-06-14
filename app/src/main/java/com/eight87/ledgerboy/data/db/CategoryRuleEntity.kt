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
 * G — auto-categorization rule. Each rule maps a match against a
 * transaction to a category. Rules are tried in ascending [priority];
 * the first match wins (see `CategoryRuleEngine`). Open/closed: new match
 * kinds are new [RuleKind] cases, not edits to a `when` chain at call
 * sites (the engine's `when` is the one exhaustive place).
 */
@Entity(
  tableName = "category_rules",
  foreignKeys = [
    ForeignKey(
      entity = CategoryEntity::class,
      parentColumns = ["id"],
      childColumns = ["categoryId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["categoryId"]), Index(value = ["priority"])],
)
data class CategoryRuleEntity(
  @PrimaryKey val id: String,
  val categoryId: String,
  val kind: RuleKind,
  /** The pattern: a substring, a regex, or an exact account id depending on [kind]. */
  val pattern: String,
  val priority: Int = 100,
)

enum class RuleKind { PayeeContains, PayeeRegex, AccountEquals }

@Dao
interface CategoryRuleDao {
  @Upsert
  suspend fun upsert(rule: CategoryRuleEntity)

  @Query("DELETE FROM category_rules WHERE id = :id")
  suspend fun delete(id: String)

  @Query("SELECT * FROM category_rules ORDER BY priority, id")
  fun observeAll(): Flow<List<CategoryRuleEntity>>

  @Query("SELECT * FROM category_rules ORDER BY priority, id")
  suspend fun all(): List<CategoryRuleEntity>
}
