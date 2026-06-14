package com.eight87.ledgerboy.plugins

import com.eight87.ledgerboy.plugins.coinbase.CoinbasePriceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** J — Coinbase spot-price parsing into integer minor units. */
class CoinbasePriceParserTest {

  @Test
  fun `parses amount and currency into minor units`() {
    val body = """{"data":{"amount":"61234.50","base":"BTC","currency":"EUR"}}"""
    val price = CoinbasePriceParser.parse(body, fractionDigits = 2)!!
    assertEquals(6123450, price.valueMinor)
    assertEquals("EUR", price.currency)
  }

  @Test
  fun `respects quote currency fraction digits`() {
    // JPY has 0 fraction digits → amount is already whole-unit minor.
    val body = """{"data":{"amount":"9000000","base":"BTC","currency":"JPY"}}"""
    val price = CoinbasePriceParser.parse(body, fractionDigits = 0)!!
    assertEquals(9000000, price.valueMinor)
    assertEquals("JPY", price.currency)
  }

  @Test
  fun `garbage and missing fields yield null`() {
    assertNull(CoinbasePriceParser.parse("not json"))
    assertNull(CoinbasePriceParser.parse("""{"data":{"base":"BTC"}}"""))
  }
}
