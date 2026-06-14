package com.eight87.ledgerboy.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * C.5 — the Room database. SQLCipher whole-DB encryption
 * (security-decisions.md) is layered in via `openHelperFactory` in the
 * security phase; the schema here is unchanged by that (SQLCipher is
 * transparent to Room).
 */
@Database(
  entities = [
    AccountEntity::class,
    TransactionEntity::class,
    CategoryEntity::class,
  ],
  version = 1,
  exportSchema = true,
)
@TypeConverters(LedgerConverters::class)
abstract class LedgerDatabase : RoomDatabase() {
  abstract fun accountDao(): AccountDao
  abstract fun transactionDao(): TransactionDao
  abstract fun categoryDao(): CategoryDao
}

/** Enum converters. Money/time are stored as primitive columns, so no converters needed for them. */
class LedgerConverters {
  @TypeConverter
  fun accountTypeToString(type: AccountType): String = type.name

  @TypeConverter
  fun stringToAccountType(value: String): AccountType =
    runCatching { AccountType.valueOf(value) }.getOrDefault(AccountType.Other)
}
