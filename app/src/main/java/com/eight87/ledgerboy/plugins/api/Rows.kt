package com.eight87.ledgerboy.plugins.api

/**
 * X.1 — the normalized-row contract plugins emit into the host.
 *
 * These are the *interchange* types between a plugin and the host's
 * row-write path — deliberately decoupled from the Room entities
 * (Phase C/D) so plugins never depend on the database schema (DIP). The
 * host maps these into ledger / FX / valuation rows, de-duplicates, and
 * persists. Fields are intentionally minimal for Phase X and grow as the
 * Phase C ledger schema and Phase D FX cache land.
 *
 * Money is carried as `(minorUnits: Long, currency: ISO-4217 String)` —
 * never a floating type (CLAUDE.md money rule). The host wraps it into
 * the `Money` value class on the way into Room.
 */
sealed interface NormalizedRow

/** A bank/credit/cash transaction from a banking or import plugin. */
data class NormalizedTransaction(
  /** Stable per-source id used for de-duplication across re-fetches. */
  val externalId: String,
  val bookedAtEpochMillis: Long,
  val amountMinor: Long,
  val currency: String,
  val payee: String?,
  val description: String?,
  /** Source account identifier (IBAN, account number, or import-file label). */
  val accountRef: String,
) : NormalizedRow

/** An FX reference rate from an FX plugin (Phase D). */
data class FxRateRow(
  val baseCurrency: String,
  val quoteCurrency: String,
  /** Rate scaled by 1e8 and carried as a Long to avoid float drift. */
  val rateScaled1e8: Long,
  val asOfEpochMillis: Long,
) : NormalizedRow

/** A mark-to-market valuation event from an asset-price / regional-index plugin (Phase I/J). */
data class ValuationEventRow(
  val assetRef: String,
  val valueMinor: Long,
  val currency: String,
  val asOfEpochMillis: Long,
  val source: String,
) : NormalizedRow

/**
 * The sink a plugin writes normalized rows into during [ConnectorPlugin.fetch].
 * The host implementation maps each row into Room and de-duplicates;
 * an in-memory test implementation lets plugin fetch logic be unit-tested
 * without a database.
 */
interface RowSink {
  suspend fun write(row: NormalizedRow)
}
