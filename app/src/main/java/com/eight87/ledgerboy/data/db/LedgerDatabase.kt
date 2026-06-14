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
    CategoryRuleEntity::class,
    BudgetEntity::class,
    AssetEntity::class,
    ValuationEventEntity::class,
  ],
  version = 5,
  exportSchema = true,
)
@TypeConverters(LedgerConverters::class)
abstract class LedgerDatabase : RoomDatabase() {
  abstract fun accountDao(): AccountDao
  abstract fun transactionDao(): TransactionDao
  abstract fun categoryDao(): CategoryDao
  abstract fun fxRateDao(): FxRateDao
  abstract fun categoryRuleDao(): CategoryRuleDao
  abstract fun budgetDao(): BudgetDao
  abstract fun assetDao(): AssetDao
  abstract fun valuationEventDao(): ValuationEventDao

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

    /** G — v2→v3 adds the auto-categorization rule table. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
        connection.execSQL(
          "CREATE TABLE IF NOT EXISTS `category_rules` (" +
            "`id` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
            "`pattern` TEXT NOT NULL, `priority` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
            "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_category_rules_categoryId` ON `category_rules` (`categoryId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_category_rules_priority` ON `category_rules` (`priority`)")
      }
    }

    /** H — v3→v4 adds the budgets table (category-monthly). */
    val MIGRATION_3_4 = object : Migration(3, 4) {
      override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
        connection.execSQL(
          "CREATE TABLE IF NOT EXISTS `budgets` (" +
            "`id` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `limitMinor` INTEGER NOT NULL, " +
            "`currency` TEXT NOT NULL, PRIMARY KEY(`id`), " +
            "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_categoryId` ON `budgets` (`categoryId`)")
      }
    }

    /** I — v4→v5 adds the asset registry + valuation history. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
      override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
        connection.execSQL(
          "CREATE TABLE IF NOT EXISTS `assets` (" +
            "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `assetClass` TEXT NOT NULL, " +
            "`currency` TEXT NOT NULL, `acquisitionCostMinor` INTEGER NOT NULL, " +
            "`acquisitionDate` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        connection.execSQL(
          "CREATE TABLE IF NOT EXISTS `valuation_events` (" +
            "`id` TEXT NOT NULL, `assetId` TEXT NOT NULL, `valueMinor` INTEGER NOT NULL, " +
            "`currency` TEXT NOT NULL, `asOf` INTEGER NOT NULL, `source` TEXT NOT NULL, " +
            "PRIMARY KEY(`id`), FOREIGN KEY(`assetId`) REFERENCES `assets`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_valuation_events_assetId_asOf` ON `valuation_events` (`assetId`, `asOf`)")
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

  @TypeConverter
  fun ruleKindToString(kind: RuleKind): String = kind.name

  @TypeConverter
  fun stringToRuleKind(value: String): RuleKind =
    runCatching { RuleKind.valueOf(value) }.getOrDefault(RuleKind.PayeeContains)

  @TypeConverter
  fun assetClassToString(value: AssetClass): String = value.name

  @TypeConverter
  fun stringToAssetClass(value: String): AssetClass =
    runCatching { AssetClass.valueOf(value) }.getOrDefault(AssetClass.Generic)
}
