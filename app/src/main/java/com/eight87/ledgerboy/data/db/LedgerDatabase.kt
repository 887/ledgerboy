package com.eight87.ledgerboy.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.execSQL

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
    FxRateEntity::class,
  ],
  version = 2,
  exportSchema = true,
)
@TypeConverters(LedgerConverters::class)
abstract class LedgerDatabase : RoomDatabase() {
  abstract fun accountDao(): AccountDao
  abstract fun transactionDao(): TransactionDao
  abstract fun categoryDao(): CategoryDao
  abstract fun fxRateDao(): FxRateDao

  companion object {
    /** D.1 — v1→v2 adds the FX rate cache (banking-fx.md). */
    val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
        connection.execSQL(
          "CREATE TABLE IF NOT EXISTS `fx_rates` (" +
            "`base` TEXT NOT NULL, `quote` TEXT NOT NULL, " +
            "`rateScaled1e8` INTEGER NOT NULL, `asOf` INTEGER NOT NULL, " +
            "PRIMARY KEY(`base`, `quote`, `asOf`))",
        )
        connection.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_fx_rates_base_quote_asOf` " +
            "ON `fx_rates` (`base`, `quote`, `asOf`)",
        )
      }
    }
  }
}

/** Enum converters. Money/time are stored as primitive columns, so no converters needed for them. */
class LedgerConverters {
  @TypeConverter
  fun accountTypeToString(type: AccountType): String = type.name

  @TypeConverter
  fun stringToAccountType(value: String): AccountType =
    runCatching { AccountType.valueOf(value) }.getOrDefault(AccountType.Other)
}
