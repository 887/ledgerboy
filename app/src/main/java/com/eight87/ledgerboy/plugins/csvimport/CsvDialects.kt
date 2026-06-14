package com.eight87.ledgerboy.plugins.csvimport

/**
 * Phase B — built-in CSV dialect presets (banking-import.md). The generic
 * RFC-4180 dialect is the universal fallback; the Sparkasse preset proves
 * the per-bank-dialect model (DE: `;`-delimited, `,`-decimal, ISO-8859-1,
 * preamble rows). More dialect sub-plugins (DKB / ING-DiBa / N26 / Revolut
 * / Wise) land as one preset each as users ask.
 */
object CsvDialects {

  /** RFC-4180 generic: comma-delimited, dot-decimal, ISO-8601 dates, UTF-8. */
  val GENERIC = CsvDialect(
    name = "Generic (RFC 4180)",
    charset = Charsets.UTF_8,
    delimiter = ',',
    decimalSeparator = '.',
    preambleLines = 0,
    hasHeader = true,
    dateColumn = 0,
    payeeColumn = 1,
    amountColumn = 2,
    purposeColumn = 3,
    dateFormat = "yyyy-MM-dd",
    currency = "EUR",
    fractionDigits = 2,
  )

  /** Sparkasse (DE) CAMT-CSV-ish export shape. */
  val SPARKASSE_DE = CsvDialect(
    name = "Sparkasse (DE)",
    charset = Charsets.ISO_8859_1,
    delimiter = ';',
    decimalSeparator = ',',
    preambleLines = 0,
    hasHeader = true,
    dateColumn = 1,
    payeeColumn = 11,
    amountColumn = 14,
    purposeColumn = 4,
    dateFormat = "dd.MM.yy",
    currency = "EUR",
    fractionDigits = 2,
  )

  val all = listOf(GENERIC, SPARKASSE_DE)

  fun byName(name: String?): CsvDialect = all.firstOrNull { it.name == name } ?: GENERIC
}
