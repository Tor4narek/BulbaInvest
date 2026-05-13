package com.bulbainvest.domain.routes

import com.bulbainvest.domain.api.BadRequestException
import com.bulbainvest.domain.api.BuyFromOrderBookRequest
import com.bulbainvest.domain.api.CreateSellOrderRequest
import com.bulbainvest.domain.api.OrderBookDto
import com.bulbainvest.domain.api.toSellOrderDto
import com.bulbainvest.domain.plugins.USER_AUTH
import com.bulbainvest.domain.plugins.userId
import com.bulbainvest.domain.service.OrderService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

fun Route.orderRoutes(orders: OrderService) {
    // Order book is public-ish, but we still require auth to be consistent.
    authenticate(USER_AUTH) {
        get("/api/order-book/{ticker}") {
            val ticker = call.parameters["ticker"]?.trim()?.uppercase()
                ?: throw BadRequestException("ticker required")
            val list = orders.orderBook(ticker).map { it.toSellOrderDto() }
            call.respond(OrderBookDto(ticker, list))
        }

        post("/api/orders/sell") {
            val body = call.receive<CreateSellOrderRequest>()
            val ticker = body.ticker.trim().uppercase().ifBlank { throw BadRequestException("ticker required") }
            val qty = body.quantity.toBigDecimalOrError()
            val price = body.price.toBigDecimalOrError()
            val row = orders.createSellOrder(call.userId(), ticker, qty, price)
            call.respond(HttpStatusCode.Created, row.toSellOrderDto())
        }

        post("/api/orders/buy") {
            val body = call.receive<BuyFromOrderBookRequest>()
            val ticker = body.ticker.trim().uppercase().ifBlank { throw BadRequestException("ticker required") }
            val qty = body.quantity.toBigDecimalOrError()
            val maxPrice = body.maxPrice.toBigDecimalOrError()
            val walletId = body.walletId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            orders.buyFromBook(call.userId(), ticker, qty, maxPrice, walletId)
            call.respond(HttpStatusCode.OK, mapOf("status" to "executed"))
        }

        route("/api/orders/sell/{orderId}") {
            post("/buy") {
                val orderId = parseUuid(call.parameters["orderId"])
                orders.buySpecificOrder(call.userId(), orderId, null)
                call.respond(HttpStatusCode.OK, mapOf("status" to "executed"))
            }
            post("/cancel") {
                val orderId = parseUuid(call.parameters["orderId"])
                orders.cancel(call.userId(), orderId)
                call.respond(HttpStatusCode.OK, mapOf("status" to "cancelled"))
            }
        }

        get("/api/orders/my") {
            val list = orders.listMyOrders(call.userId()).map { it.toSellOrderDto() }
            call.respond(list)
        }
    }
}
