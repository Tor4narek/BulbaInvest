package com.stocksim.graphservice.model

import kotlinx.serialization.Serializable

/**
 * Envelope from the Redis channel (published by StocksMarketAPI / Go service).
 *
 * JSON format:
 * {"type":"MARKET_QUOTES_UPDATED","quotes":[{"ticker":"AAPL","price":210.50,"availableQuantity":10000,"volatility":0.02,"updatedAt":1779228346},...]}
 */
@Serializable
data class MarketQuotesEnvelope(
    val type: String,
    val quotes: List<PriceTick>
)

/**
 * Raw price tick received from Redis broker.
 * Published by StocksMarketAPI (Go service).
 *
 * JSON format on the channel:
 * {"ticker":"AAPL","price":210.50,"availableQuantity":10000,"volatility":0.02,"updatedAt":1779228346}
 *
 * Note: updatedAt is a Unix epoch timestamp in **seconds**.
 */
@Serializable
data class PriceTick(
    val ticker: String,
    val price: Double,
    val availableQuantity: Long,
    val volatility: Double,
    /** Unix epoch seconds */
    val updatedAt: Long
)

/**
 * One row stored in ClickHouse `quotes` table.
 * timestamp is Unix epoch seconds (ClickHouse DateTime stores seconds).
 */
data class QuoteRow(
    val ticker: String,
    val price: Double,
    val availableQuantity: Long,
    val volatility: Double,
    /** Unix epoch seconds */
    val timestamp: Long
)

/**
 * Aggregated OHLCV candle returned by the stats endpoint.
 */
@Serializable
data class Candle(
    /** Candle open timestamp, ISO-8601 string, e.g. "2024-01-15T10:00:00Z" */
    val time: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
)

/**
 * Granularity of candles requested by the client.
 */
enum class Granularity(
    val apiValue: String,
    val clickhouseInterval: String,
    val lookbackValue: Int,
    val lookbackUnit: String,
) {
    TEN_MINUTES(
        apiValue = "10m",
        clickhouseInterval = "toStartOfSecond",
        lookbackValue = 10,
        lookbackUnit = "MINUTE",
    ),
    HOUR(
        apiValue = "hour",
        clickhouseInterval = "toStartOfMinute",
        lookbackValue = 1,
        lookbackUnit = "HOUR",
    ),
    DAY(
        apiValue = "day",
        clickhouseInterval = "toStartOfFiveMinutes",
        lookbackValue = 24,
        lookbackUnit = "HOUR",
    ),
    WEEK(
        apiValue = "week",
        clickhouseInterval = "toStartOfHour",
        lookbackValue = 7,
        lookbackUnit = "DAY",
    ),
    MONTH(
        apiValue = "month",
        clickhouseInterval = "toStartOfDay",
        lookbackValue = 30,
        lookbackUnit = "DAY",
    );

    companion object {
        fun from(value: String): Granularity =
            entries.firstOrNull {
                it.apiValue.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true)
            } ?: throw IllegalArgumentException("Unknown granularity: '$value'. Allowed: 10m, hour, day, week, month")
    }
}

/**
 * Response envelope for /api/quotes/{ticker}/stats
 */
@Serializable
data class StatsResponse(
    val ticker: String,
    val granularity: String,
    val candles: List<Candle>
)

/**
 * Latest price snapshot for a ticker.
 * timestamp is Unix epoch milliseconds (converted from ClickHouse seconds).
 */
@Serializable
data class LatestPriceResponse(
    val ticker: String,
    val price: Double,
    val availableQuantity: Long,
    val volatility: Double,
    val timestamp: Long
)
