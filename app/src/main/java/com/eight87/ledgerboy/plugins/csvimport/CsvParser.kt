package com.eight87.ledgerboy.plugins.csvimport

import com.eight87.ledgerboy.plugins.api.NormalizedTransaction
import java.nio.charset.Charset
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Phase B — roll-our-own CSV import core (banking-import.md). The
 * structural variance across banks lives in the column-mapping layer, not
 * the delimiter-parsing layer, so a third-party CSV lib buys nothing; the
 * line splitter is ~RFC-4180 (quoted fields, doubled-quote escaping,
 * configurable delimiter) in a few dozen lines, stdlib-only.
 *
 * A [CsvDialect] declares one bank's shape (delimiter, decimal separator,
 * charset, date format, preamble length, and which columns hold the date /
 * payee / amount). [CsvParser.parse] turns file text + a dialect + an
 * account ref into [NormalizedTransaction] rows, each with a stable
 * content-hash `externalId` so re-importing the same file (or overlapping
 * date ranges) never double-counts.
 *
 * Pure Kotlin, JVM-unit-testable. No floating point in the amount path —
 * the decimal string is parsed straight into integer minor units.
 */
data class CsvDialect(
  val name: String,
  val charset: Charset = Charsets.UTF_8,
  val delimiter: Char = ',',
  val decimalSeparator: Char = '.',
  /** Lines to skip before the header row (bank preambles). */
  val preambleLines: Int = 0,
  /** Whether row[preambleLines] is a header row to skip. */
  val hasHeader: Boolean = true,
  val dateColumn: Int,
  val payeeColumn: Int,
  val amountColumn: Int,
  /** Optional purpose / memo column folded into the description. */
  val purposeColumn: Int? = null,
  val dateFormat: String = "yyyy-MM-dd",
  /** ISO-4217 currency the file is denominated in. */
  val currency: String = "EUR",
  /** Minor-unit digits for [currency] (2 for EUR/USD, 0 for JPY, …). */
  val fractionDigits: Int = 2,
)

object CsvParser {

  /** Parse [content] under [dialect] into normalized rows for [accountRef]. */
  fun parse(content: String, dialect: CsvDialect, accountRef: String): List<NormalizedTransaction> {
    val lines = content.split("\r\n", "\n", "\r").filter { it.isNotBlank() }
    val dataStart = dialect.preambleLines + if (dialect.hasHeader) 1 else 0
    if (lines.size <= dataStart) return emptyList()
    val formatter = DateTimeFormatter.ofPattern(dialect.dateFormat)

    return lines.drop(dataStart).mapNotNull { line ->
      val cols = splitCsvLine(line, dialect.delimiter)
      val maxCol = maxOf(dialect.dateColumn, dialect.payeeColumn, dialect.amountColumn)
      if (cols.size <= maxCol) return@mapNotNull null

      val bookedAt = runCatching {
        LocalDate.parse(cols[dialect.dateColumn].trim(), formatter)
          .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
      }.getOrNull() ?: return@mapNotNull null

      val amountMinor = parseAmountToMinor(
        cols[dialect.amountColumn], dialect.decimalSeparator, dialect.fractionDigits,
      ) ?: return@mapNotNull null

      val payee = cols[dialect.payeeColumn].trim().ifEmpty { null }
      val purpose = dialect.purposeColumn
        ?.let { cols.getOrNull(it)?.trim()?.ifEmpty { null } }

      NormalizedTransaction(
        externalId = contentHash(accountRef, bookedAt, amountMinor, dialect.currency, payee, purpose),
        bookedAtEpochMillis = bookedAt,
        amountMinor = amountMinor,
        currency = dialect.currency,
        payee = payee,
        description = purpose,
        accountRef = accountRef,
      )
    }
  }

  /** RFC-4180-ish split: quoted fields, doubled-quote escaping, [delimiter]-separated. */
  internal fun splitCsvLine(line: String, delimiter: Char): List<String> {
    val out = mutableListOf<String>()
    val sb = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
      val c = line[i]
      when {
        inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
        c == '"' -> inQuotes = !inQuotes
        c == delimiter && !inQuotes -> { out.add(sb.toString()); sb.setLength(0) }
        else -> sb.append(c)
      }
      i++
    }
    out.add(sb.toString())
    return out
  }

  /**
   * Parse a money string into integer minor units WITHOUT floating point.
   * Handles the dialect decimal separator, the opposite thousands
   * separator, a leading/trailing sign, and parenthesised negatives.
   * Returns null on an unparseable amount.
   */
  internal fun parseAmountToMinor(raw: String, decimalSeparator: Char, fractionDigits: Int): Long? {
    var s = raw.trim()
    if (s.isEmpty()) return null
    var negative = false
    if (s.startsWith("(") && s.endsWith(")")) { negative = true; s = s.substring(1, s.length - 1) }
    if (s.startsWith("-")) { negative = true; s = s.substring(1) }
    else if (s.startsWith("+")) s = s.substring(1)
    if (s.endsWith("-")) { negative = true; s = s.dropLast(1) }

    val thousands = if (decimalSeparator == ',') '.' else ','
    s = s.filterNot { it == thousands || it.isWhitespace() }
    // Strip a trailing currency symbol / code if present.
    s = s.trimEnd { !it.isDigit() && it != decimalSeparator }
    if (s.none { it.isDigit() }) return null

    val parts = s.split(decimalSeparator)
    if (parts.size > 2) return null
    val intPart = parts[0].ifEmpty { "0" }
    val fracPartRaw = parts.getOrNull(1) ?: ""
    if (!intPart.all { it.isDigit() }) return null
    if (!fracPartRaw.all { it.isDigit() }) return null

    val frac = when {
      fracPartRaw.length == fractionDigits -> fracPartRaw
      fracPartRaw.length > fractionDigits -> fracPartRaw.substring(0, fractionDigits)
      else -> fracPartRaw.padEnd(fractionDigits, '0')
    }
    val combined = (intPart + frac).ifEmpty { "0" }
    val magnitude = combined.toLongOrNull() ?: return null
    return if (negative) -magnitude else magnitude
  }

  /** SHA-256 of the dedup-relevant fields, first 16 bytes hex. */
  internal fun contentHash(
    accountRef: String,
    bookedAt: Long,
    amountMinor: Long,
    currency: String,
    payee: String?,
    purpose: String?,
  ): String {
    val material = "$accountRef|$bookedAt|$amountMinor|$currency|${payee.orEmpty()}|${purpose.orEmpty()}"
    val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
    return digest.take(16).joinToString("") { "%02x".format(it) }
  }
}
