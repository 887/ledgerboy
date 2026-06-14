package com.eight87.ledgerboy.plugins.ecbfx

import com.eight87.ledgerboy.data.money.MoneyConverter
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.ZoneOffset
import javax.xml.parsers.DocumentBuilderFactory

/**
 * D.3 — parser for the ECB `eurofxref-daily.xml` feed (banking-fx.md).
 *
 * The feed is a flat nested structure:
 * ```
 * <Cube>
 *   <Cube time="2026-01-05">
 *     <Cube currency="USD" rate="1.0856"/>
 *     <Cube currency="JPY" rate="161.23"/>
 *   </Cube>
 * </Cube>
 * ```
 * All rates are EUR→currency. Parsed with the JVM-stdlib DOM builder
 * (works in plain JUnit, no Robolectric, no third-party dep). Rates are
 * carried as 1e8-scaled Longs — no float in the rate path.
 */
object EcbFxParser {

  data class Parsed(val asOfEpochMillis: Long, val rates: List<EurRate>)
  data class EurRate(val currency: String, val rateScaled1e8: Long)

  fun parse(xml: String): Parsed? {
    val doc = runCatching {
      val factory = DocumentBuilderFactory.newInstance().apply {
        // Harden against XXE — this is a remote feed.
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        isExpandEntityReferences = false
      }
      factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    }.getOrNull() ?: return null

    val cubes = doc.getElementsByTagName("Cube")
    var asOf: Long? = null
    val rates = mutableListOf<EurRate>()
    for (i in 0 until cubes.length) {
      val el = cubes.item(i) as? Element ?: continue
      val time = el.getAttribute("time")
      if (time.isNotEmpty()) {
        asOf = runCatching {
          LocalDate.parse(time).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
      }
      val currency = el.getAttribute("currency")
      val rate = el.getAttribute("rate")
      if (currency.isNotEmpty() && rate.isNotEmpty()) {
        MoneyConverter.parseRateScaled1e8(rate)?.let { rates.add(EurRate(currency, it)) }
      }
    }
    val date = asOf ?: return null
    if (rates.isEmpty()) return null
    return Parsed(date, rates)
  }
}
