package com.bulbainvest.gateway.domain

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.Serializable

@Serializable
data class StockQuote(
    val ticker: String,
    val price: Double,
    val availableQuantity: Long,
    val updatedAt: Long,
    val volatility: Double? = null,
)

@Serializable
data class QuotesUpdatedEvent(
    val type: String,
    val quotes: List<StockQuote>,
)

interface MarketQuotesStream {
    val updates: SharedFlow<StockQuote>

    suspend fun currentQuote(ticker: String): StockQuote?

    fun start()

    fun stop()
}
