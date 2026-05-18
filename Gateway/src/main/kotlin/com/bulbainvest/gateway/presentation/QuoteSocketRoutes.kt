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
        val ticker = quoteSubscriptionUseCase.normalizeTicker(call.request.queryParameters["ticker"])
        if (ticker == null) {
            close(
                CloseReason(
                    CloseReason.Codes.CANNOT_ACCEPT,
                    "ticker query parameter is required",
                )
            )
            return@webSocket
        }

        quoteSubscriptionUseCase.currentQuote(ticker)?.let { quote ->
            sendQuote(quote)
        }

        quoteSubscriptionUseCase.updatesFor(ticker).collect { quote ->
            sendQuote(quote)
        }
    }
}

private suspend fun WebSocketSession.sendQuote(quote: StockQuote) {
    send(Frame.Text(quoteSocketJson.encodeToString(quote)))
}
