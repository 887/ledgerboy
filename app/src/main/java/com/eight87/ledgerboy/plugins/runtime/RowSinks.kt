package com.eight87.ledgerboy.plugins.runtime

import com.eight87.ledgerboy.plugins.api.NormalizedRow
import com.eight87.ledgerboy.plugins.api.RowSink

/**
 * Phase X interim sink. The real sink that maps normalized rows into the
 * Room ledger / FX cache / valuation history lands in Phase C/D; until
 * then there are no plugins to emit rows, so this collects them in memory
 * (and is the unit-test seam for plugin fetch logic). Swapping in the
 * Room-backed sink touches only the AppGraph wiring (DIP).
 */
class InMemoryRowSink : RowSink {
  private val _rows = mutableListOf<NormalizedRow>()
  val rows: List<NormalizedRow> get() = _rows.toList()
  override suspend fun write(row: NormalizedRow) { _rows.add(row) }
  fun clear() { _rows.clear() }
}
