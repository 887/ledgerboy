package com.eight87.ledgerboy.plugins

import com.eight87.ledgerboy.plugins.ecbfx.EcbFxParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** D.3 — ECB eurofxref-daily.xml parsing. */
class EcbFxParserTest {

  private val sample = """
    <?xml version="1.0" encoding="UTF-8"?>
    <gesmes:Envelope xmlns:gesmes="http://www.gesmes.org/xml/2002-08-01" xmlns="http://www.ecb.int/vocabulary/2002-08-01/eurofxref">
      <Cube>
        <Cube time="2026-01-05">
          <Cube currency="USD" rate="1.0856"/>
          <Cube currency="JPY" rate="161.23"/>
          <Cube currency="GBP" rate="0.8423"/>
        </Cube>
      </Cube>
    </gesmes:Envelope>
  """.trimIndent()

  @Test
  fun `parses date and eur-paired rates`() {
    val parsed = EcbFxParser.parse(sample)
    assertNotNull(parsed)
    val expectedDate = LocalDate.parse("2026-01-05")
      .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    assertEquals(expectedDate, parsed!!.asOfEpochMillis)
    assertEquals(3, parsed.rates.size)
    val usd = parsed.rates.first { it.currency == "USD" }
    assertEquals(108_560_000L, usd.rateScaled1e8)
    assertTrue(parsed.rates.any { it.currency == "JPY" })
  }

  @Test
  fun `garbage returns null`() {
    assertNull(EcbFxParser.parse("not xml at all"))
    assertNull(EcbFxParser.parse("<Cube></Cube>"))
  }
}
