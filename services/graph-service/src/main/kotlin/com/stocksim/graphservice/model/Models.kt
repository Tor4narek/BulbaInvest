package com.stocksim.graphservice.model

import kotlinx.serialization.Serializable

/**
 * Raw price tick received from Redis broker.
 * Published by StocksMarketAPI (Go service).
 *
 * JSON format on the channel:
 * {"ticker":"AAPL","buyPrice":120.50,"sellPrice":119.80,"timestamp":1700000000000}
 */
@Serializable
data class PriceTick(
    val ticker: String,
    val buyPrice: Double,
    val sellPrice: Double,
    /** Unix epoch milliseconds */
    val timestamp: Long
)

/**
 * One row stored in ClickHouse `quotes` table.
 */
data class QuoteRow(
    val ticker: String,
    val buyPrice: Double,
    val sellPrice: Double,
    /** mid = (buy + sell) / 2 — stored for convenience */
    val midPrice: Double,
    /** Unix epoch milliseconds */
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
enum class Granularity(val clickhouseInterval: String, val lookbackHours: Long) {
    HOUR(clickhouseInterval = "toStartOfMinute", lookbackHours = 1),
    DAY(clickhouseInterval = "toStartOfFiveMinutes", lookbackHours = 24),
    WEEK(clickhouseInterval = "toStartOfHour", lookbackHours = 168),
    MONTH(clickhouseInterval = "toStartOfDay", lookbackHours = 720);

    companion object {
        fun from(value: String): Granularity =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown granularity: '$value'. Allowed: hour, day, week, month")
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
 */
@Serializable
data class LatestPriceResponse(
    val ticker: String,
    val buyPrice: Double,
    val sellPrice: Double,
    val midPrice: Double,
    val timestamp: Long
)
