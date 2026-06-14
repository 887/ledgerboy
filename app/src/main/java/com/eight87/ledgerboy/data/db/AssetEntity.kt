package com.eight87.ledgerboy.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * I — the asset registry. An asset (real estate / vehicle / security /
 * collectible / generic) carries an acquisition cost and a history of
 * [ValuationEventEntity]s (date + value + source). The current value is
 * the latest valuation event; manual revaluation lands a `source="manual"`
 * event, automated revaluation (Phase J connectors) lands a
 * `source="<connector>"` event.
 */
@Entity(tableName = "assets")
data class AssetEntity(
  @PrimaryKey val id: String,
  val name: String,
  val assetClass: AssetClass,
  val currency: String,
  val acquisitionCostMinor: Long,
  val acquisitionDate: Long,
)

enum class AssetClass { RealEstate, Vehicle, Security, Crypto, Collectible, Generic }

@Entity(
  tableName = "valuation_events",
  foreignKeys = [
    ForeignKey(
      entity = AssetEntity::class,
      parentColumns = ["id"],
      childColumns = ["assetId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["assetId", "asOf"])],
)
data class ValuationEventEntity(
  @PrimaryKey val id: String,
  val assetId: String,
  val valueMinor: Long,
  val currency: String,
  val asOf: Long,
  val source: String,
)

/** Asset joined with its latest valuation value (or acquisition cost if none yet). */
data class AssetWithValue(
  @Embedded val asset: AssetEntity,
  val currentValueMinor: Long,
)

@Dao
interface AssetDao {
  @Upsert
  suspend fun upsert(asset: AssetEntity)

  @Query("DELETE FROM assets WHERE id = :id")
  suspend fun delete(id: String)

  @Query("SELECT * FROM assets WHERE id = :id")
  suspend fun byId(id: String): AssetEntity?

  /** Assets with their current value = latest valuation, falling back to acquisition cost. */
  @Query(
    """
    SELECT a.*, COALESCE((
      SELECT v.valueMinor FROM valuation_events v
      WHERE v.assetId = a.id ORDER BY v.asOf DESC LIMIT 1
    ), a.acquisitionCostMinor) AS currentValueMinor
    FROM assets a
    ORDER BY a.assetClass, a.name
    """,
  )
  fun observeAssetsWithValue(): Flow<List<AssetWithValue>>
}

@Dao
interface ValuationEventDao {
  @Upsert
  suspend fun upsert(event: ValuationEventEntity)

  @Query("SELECT * FROM valuation_events WHERE assetId = :assetId ORDER BY asOf DESC")
  fun observeForAsset(assetId: String): Flow<List<ValuationEventEntity>>
}
