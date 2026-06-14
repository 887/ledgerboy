package com.eight87.ledgerboy.plugins

import com.eight87.ledgerboy.plugins.csvimport.CsvDialects
import com.eight87.ledgerboy.plugins.csvimport.CsvParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase B — CSV parsing, amount math (no float), and dedup hashing. */
class CsvParserTest {

  @Test
  fun `generic dialect parses date payee amount`() {
    val csv = """
      Date,Payee,Amount,Memo
      2026-01-05,Acme Savings,10.50,salary
      2026-01-06,Globex,-2.95,coffee
    """.trimIndent()
    val rows = CsvParser.parse(csv, CsvDialects.GENERIC, accountRef = "acct-1")
    assertEquals(2, rows.size)
    assertEquals(1050, rows[0].amountMinor)
    assertEquals("Acme Savings", rows[0].payee)
    assertEquals(-295, rows[1].amountMinor)
    assertEquals("acct-1", rows[0].accountRef)
  }

  @Test
  fun `amount parsing handles separators and signs without float`() {
    // dot-decimal, comma-thousands
    assertEquals(123456789L, CsvParser.parseAmountToMinor("1,234,567.89", '.', 2))
    // comma-decimal, dot-thousands (DE)
    assertEquals(123456789L, CsvParser.parseAmountToMinor("1.234.567,89", ',', 2))
    // negative forms
    assertEquals(-500L, CsvParser.parseAmountToMinor("-5.00", '.', 2))
    assertEquals(-500L, CsvParser.parseAmountToMinor("(5.00)", '.', 2))
    assertEquals(-500L, CsvParser.parseAmountToMinor("5.00-", '.', 2))
    // fewer / more fraction digits than the currency
    assertEquals(500L, CsvParser.parseAmountToMinor("5", '.', 2))
    assertEquals(123L, CsvParser.parseAmountToMinor("1.234", '.', 2)) // truncates extra digits
    // zero-fraction currency (JPY)
    assertEquals(1050L, CsvParser.parseAmountToMinor("1050", '.', 0))
    // garbage
    assertNull(CsvParser.parseAmountToMinor("n/a", '.', 2))
  }

  @Test
  fun `quoted fields with embedded delimiter are respected`() {
    val cols = CsvParser.splitCsvLine("2026-01-05,\"Acme, Inc\",10.00", ',')
    assertEquals(listOf("2026-01-05", "Acme, Inc", "10.00"), cols)
  }

  @Test
  fun `content hash is stable for the same transaction and differs across`() {
    val csv = "Date,Payee,Amount,Memo\n2026-01-05,Acme,10.50,x"
    val a = CsvParser.parse(csv, CsvDialects.GENERIC, "acct-1").first().externalId
    val b = CsvParser.parse(csv, CsvDialects.GENERIC, "acct-1").first().externalId
    val c = CsvParser.parse(csv, CsvDialects.GENERIC, "acct-2").first().externalId
    assertEquals(a, b)            // deterministic → dedup works on re-import
    assertTrue(a != c)           // account ref participates in the hash
  }

  @Test
  fun `sparkasse dialect parses german shape`() {
    // Sparkasse: ';' delimited, ',' decimal, dd.MM.yy; cols date=1, purpose=4, payee=11, amount=14.
    val header = "Auftragskonto;Buchungstag;Valutadatum;Buchungstext;Verwendungszweck;c5;c6;c7;c8;c9;c10;Beguenstigter;c12;c13;Betrag;Waehrung"
    val row =    "DE00;05.01.26;05.01.26;LASTSCHRIFT;Miete Januar;;;;;;;Westvale Properties;;;-850,00;EUR"
    val rows = CsvParser.parse("$header\n$row", CsvDialects.SPARKASSE_DE, "miete-acct")
    assertEquals(1, rows.size)
    assertEquals(-85000, rows[0].amountMinor)
    assertEquals("Westvale Properties", rows[0].payee)
    assertEquals("Miete Januar", rows[0].description)
  }
}
