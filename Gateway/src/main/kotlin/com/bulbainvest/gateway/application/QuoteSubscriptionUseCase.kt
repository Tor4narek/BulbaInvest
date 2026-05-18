package com.bulbainvest.gateway.application

import com.bulbainvest.gateway.domain.MarketQuotesStream
import com.bulbainvest.gateway.domain.StockQuote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter

class QuoteSubscriptionUseCase(
    private val marketQuotesStream: MarketQuotesStream,
) {
    suspend fun currentQuote(rawTicker: String?): StockQuote? {
        val ticker = normalizeTicker(rawTicker) ?: return null
        return marketQuotesStream.currentQuote(ticker)
    }

    fun updatesFor(rawTicker: String?): Flow<StockQuote> {
        val ticker = normalizeTicker(rawTicker) ?: return kotlinx.coroutines.flow.emptyFlow()
        return marketQuotesStream.updates.filter { quote ->
            quote.ticker.equals(ticker, ignoreCase = true)
        }
    }

    fun normalizeTicker(rawTicker: String?): String? =
        rawTicker?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
}
