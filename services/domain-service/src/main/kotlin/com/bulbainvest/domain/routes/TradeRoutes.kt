package com.bulbainvest.domain.routes

import com.bulbainvest.domain.api.BadRequestException
import com.bulbainvest.domain.api.CompanyTradeRequest
import com.bulbainvest.domain.api.toTradeDto
import com.bulbainvest.domain.plugins.USER_AUTH
import com.bulbainvest.domain.plugins.userId
import com.bulbainvest.domain.service.TradeService
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.math.BigDecimal
import java.util.UUID

fun Route.tradeRoutes(trades: TradeService) {
    authenticate(USER_AUTH) {
        post("/api/trades/company/buy") {
            val body = call.receive<CompanyTradeRequest>()
            val qty = body.quantity.toBigDecimalOrError()
            val walletId = body.walletId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            val ticker = body.ticker.trim().uppercase().ifBlank { throw BadRequestException("ticker required") }
            val trade = trades.buyFromCompany(call.userId(), ticker, qty, walletId)
            call.respond(trade.toTradeDto())
        }

        post("/api/trades/company/sell") {
            val body = call.receive<CompanyTradeRequest>()
            val qty = body.quantity.toBigDecimalOrError()
            val walletId = body.walletId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            val ticker = body.ticker.trim().uppercase().ifBlank { throw BadRequestException("ticker required") }
            val trade = trades.sellToCompany(call.userId(), ticker, qty, walletId)
            call.respond(trade.toTradeDto())
        }

        get("/api/trades") {
            val list = trades.listMyTrades(call.userId()).map { it.toTradeDto() }
            call.respond(list)
        }
    }
}

internal fun String.toBigDecimalOrError(): BigDecimal =
    runCatching { BigDecimal(this.trim()) }.getOrElse { throw BadRequestException("invalid number: $this") }
