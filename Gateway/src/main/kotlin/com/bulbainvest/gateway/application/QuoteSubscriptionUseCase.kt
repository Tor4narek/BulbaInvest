package com.bulbainvest.gateway.application

import com.bulbainvest.gateway.domain.MarketQuotesStream
import com.bulbainvest.gateway.domain.StockQuote
import io.ktor.http.Parameters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.mapNotNull

class QuoteSubscriptionUseCase(
    private val marketQuotesStream: MarketQuotesStream,
) {
    suspend fun currentQuotes(rawTickers: List<String>): List<StockQuote> {
        val tickers = normalizeTickers(rawTickers)
        if (tickers.isEmpty()) return emptyList()

        return buildList {
            for (ticker in tickers) {
                marketQuotesStream.currentQuote(ticker)?.let(::add)
            }
        }
    }

    fun updatesFor(rawTickers: List<String>): Flow<List<StockQuote>> {
        val tickers = normalizeTickers(rawTickers)
        if (tickers.isEmpty()) return emptyFlow()

        val subscribed = tickers.toSet()
        return marketQuotesStream.updates.mapNotNull { event ->
            event.quotes.filter { quote ->
                quote.ticker.uppercase() in subscribed
            }.takeIf { it.isNotEmpty() }
        }
    }

    fun requestTickers(parameters: Parameters): List<String> {
        val repeatedTickerParams = parameters.getAll("ticker").orEmpty()
        val explicitTickers = parameters.getAll("tickers").orEmpty()
            .flatMap { value -> value.split(',') }
        return normalizeTickers(repeatedTickerParams + explicitTickers)
    }

    fun normalizeTickers(rawTickers: List<String>): List<String> {
        val normalized = LinkedHashSet<String>()
        rawTickers.forEach { rawTicker ->
            normalizeTicker(rawTicker)?.let(normalized::add)
        }
        return normalized.toList()
    }

    fun normalizeTicker(rawTicker: String?): String? =
        rawTicker?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
}
