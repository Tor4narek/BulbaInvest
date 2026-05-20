package com.bulbainvest.gateway.presentation

import com.bulbainvest.gateway.application.QuoteSubscriptionUseCase
import com.bulbainvest.gateway.domain.StockQuote
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.send
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val quoteSocketJson = Json { encodeDefaults = true }

fun Route.quoteSocketRoutes(quoteSubscriptionUseCase: QuoteSubscriptionUseCase) {
    webSocket("/ws/quotes") {
        val tickers = quoteSubscriptionUseCase.requestTickers(call.request.queryParameters)
        if (tickers.isEmpty()) {
            close(
                CloseReason(
                    CloseReason.Codes.CANNOT_ACCEPT,
                    "at least one ticker query parameter is required",
                )
            )
            return@webSocket
        }

        val currentQuotes = quoteSubscriptionUseCase.currentQuotes(tickers)
        if (currentQuotes.isNotEmpty()) {
            sendQuotes(currentQuotes)
        }

        quoteSubscriptionUseCase.updatesFor(tickers).collect { quotes ->
            sendQuotes(quotes)
        }
    }
}

private suspend fun WebSocketSession.sendQuotes(quotes: List<StockQuote>) {
    send(Frame.Text(quoteSocketJson.encodeToString(quotes)))
}
