package com.eight87.ledgerboy.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** C.4 — account DAO. */
@Dao
interface AccountDao {
  @Upsert
  suspend fun upsert(account: AccountEntity)

  @Query("SELECT * FROM accounts ORDER BY displayOrder, institution, displayName")
  fun observeAll(): Flow<List<AccountEntity>>

  @Query("SELECT * FROM accounts WHERE id = :id")
  suspend fun byId(id: String): AccountEntity?

  @Query("SELECT COUNT(*) FROM accounts")
  suspend fun count(): Int
}

/** C.4 — transaction DAO. Dedup insert + latest-revision reads. */
@Dao
interface TransactionDao {
  /**
   * Insert ignoring a duplicate (accountId, externalId) — the connector
   * dedup path. Returns the rowid, or -1 when the unique index rejected
   * a re-fetch of an already-stored transaction.
   */
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertIgnoringDuplicate(txn: TransactionEntity): Long

  @Upsert
  suspend fun upsert(txn: TransactionEntity)

  @Query("SELECT * FROM transactions WHERE accountId = :accountId AND externalId = :externalId LIMIT 1")
  suspend fun byExternalId(accountId: String, externalId: String): TransactionEntity?

  /**
   * Latest-revision view: a row is current when no other row points at it
   * via [TransactionEntity.originalId]. Newest first.
   */
  @Query(
    """
    SELECT * FROM transactions t
    WHERE NOT EXISTS (SELECT 1 FROM transactions r WHERE r.originalId = t.id)
    ORDER BY t.bookedAt DESC
    """,
  )
  fun observeCurrent(): Flow<List<TransactionEntity>>

  @Query(
    """
    SELECT * FROM transactions t
    WHERE t.accountId = :accountId
      AND NOT EXISTS (SELECT 1 FROM transactions r WHERE r.originalId = t.id)
    ORDER BY t.bookedAt DESC
    """,
  )
  fun observeCurrentForAccount(accountId: String): Flow<List<TransactionEntity>>

  /** Sum of current-revision minor units for an account (single currency assumed per account). */
  @Query(
    """
    SELECT COALESCE(SUM(t.amountMinor), 0) FROM transactions t
    WHERE t.accountId = :accountId
      AND NOT EXISTS (SELECT 1 FROM transactions r WHERE r.originalId = t.id)
    """,
  )
  suspend fun balanceMinor(accountId: String): Long

  @Query(
    """
    SELECT COUNT(*) FROM transactions t
    WHERE t.categoryId IS NULL
      AND NOT EXISTS (SELECT 1 FROM transactions r WHERE r.originalId = t.id)
    """,
  )
  fun observeUncategorizedCount(): Flow<Int>
}

/** C.4 — category DAO. */
@Dao
interface CategoryDao {
  @Upsert
  suspend fun upsert(category: CategoryEntity)

  @Query("SELECT * FROM categories ORDER BY name")
  fun observeAll(): Flow<List<CategoryEntity>>

  @Query("SELECT * FROM categories WHERE id = :id")
  suspend fun byId(id: String): CategoryEntity?
}
