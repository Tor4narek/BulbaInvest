// app/src/main/java/com/bulbainvest/models/MarketModels.kt
package com.bulbainvest.models

// Ответ со списком тикеров
data class TickerListResponse(
    val tickers: List<String>
)

// Котировка
data class Quote(
    val ticker: String,
    val buyPrice: Double,
    val sellPrice: Double,
    val midPrice: Double,
    val timestamp: Long
)

// Свеча для графика
data class Candle(
    val time: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
)

// Статистика котировок
data class QuoteStats(
    val ticker: String,
    val granularity: String,
    val candles: List<Candle>
)

// Запись в стакане
data class OrderBookEntry(
    val price: Double,
    val quantity: Int
)

// Рыночный стакан (для MarketScreen)
data class OrderBook(
    val ticker: String,
    val asks: List<OrderBookEntry>,
    val bids: List<OrderBookEntry>
)