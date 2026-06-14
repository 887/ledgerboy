package com.eight87.ledgerboy.plugins.coinbase

import com.eight87.ledgerboy.data.money.MoneyInput
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * J — parser for Coinbase's public spot-price endpoint
 * `https://api.coinbase.com/v2/prices/<PAIR>/spot` (keyless, direct, no
 * aggregator). Response: `{"data":{"amount":"...","base":"BTC","currency":"EUR"}}`.
 *
 * Pure Kotlin (kotlinx.serialization), JVM-unit-testable. The decimal
 * amount is parsed straight into integer minor units (no float) using the
 * quote currency's fraction digits.
 */
object CoinbasePriceParser {

  @Serializable private data class Resp(val data: Spot? = null)
  @Serializable private data class Spot(val amount: String? = null, val base: String? = null, val currency: String? = null)

  data class Price(val valueMinor: Long, val currency: String)

  private val json = Json { ignoreUnknownKeys = true }

  /** Parse the spot response; [fractionDigits] = quote currency's minor digits. */
  fun parse(body: String, fractionDigits: Int = 2): Price? {
    val spot = runCatching { json.decodeFromString<Resp>(body) }.getOrNull()?.data ?: return null
    val currency = spot.currency?.takeIf { it.length == 3 } ?: return null
    val minor = spot.amount?.let { MoneyInput.parseMajorToMinor(it, fractionDigits) } ?: return null
    return Price(minor, currency)
  }
}
