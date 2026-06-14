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
      is FxRateRow -> Unit // Phase D — FX cache sink
      is ValuationEventRow -> Unit // Phase I/J — valuation history sink
    }
  }
}
