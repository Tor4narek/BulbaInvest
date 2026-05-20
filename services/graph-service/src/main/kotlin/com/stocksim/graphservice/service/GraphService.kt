package com.stocksim.graphservice.service

import com.stocksim.graphservice.model.Granularity
import com.stocksim.graphservice.model.LatestPriceResponse
import com.stocksim.graphservice.model.StatsResponse
import com.stocksim.graphservice.repository.QuoteRepository
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class GraphService(private val repo: QuoteRepository) {

    fun getStats(ticker: String, granularity: Granularity): StatsResponse {
        logger.debug { "getStats ticker=$ticker granularity=$granularity" }
        val candles = repo.getCandles(ticker.uppercase(), granularity)
        return StatsResponse(
            ticker = ticker.uppercase(),
            granularity = granularity.apiValue,
            candles = candles
        )
    }

    fun getLatestPrice(ticker: String): LatestPriceResponse? {
        return repo.getLatestPrice(ticker.uppercase())
    }

    fun getKnownTickers(): List<String> = repo.getKnownTickers()
}
