package com.eight87.ledgerboy.data

import com.eight87.ledgerboy.plugins.api.FxRateRow
import com.eight87.ledgerboy.plugins.api.NormalizedRow
import com.eight87.ledgerboy.plugins.api.NormalizedTransaction
import com.eight87.ledgerboy.plugins.api.RowSink
import com.eight87.ledgerboy.plugins.api.ValuationEventRow

/**
 * C.6 — the real row sink: maps connector-emitted normalized rows into
 * the ledger. Replaces the Phase X in-memory placeholder. Transaction
 * rows land de-duplicated; FX rates (Phase D) and valuation events
 * (Phase I/J) get their sinks as those phases land.
 */
class RoomRowSink(
  private val ledger: LedgerRepository,
) : RowSink {
  override suspend fun write(row: NormalizedRow) {
    when (row) {
      is NormalizedTransaction -> ledger.writeNormalizedTransaction(row)
      is FxRateRow -> ledger.writeFxRate(row.baseCurrency, row.quoteCurrency, row.rateScaled1e8, row.asOfEpochMillis)
      is ValuationEventRow -> ledger.addValuation(
        com.eight87.ledgerboy.data.db.ValuationEventEntity(
          id = "${row.assetRef}:${row.asOfEpochMillis}:${row.source}",
          assetId = row.assetRef,
          valueMinor = row.valueMinor,
          currency = row.currency,
          asOf = row.asOfEpochMillis,
          source = row.source,
        ),
      )
    }
  }
}
