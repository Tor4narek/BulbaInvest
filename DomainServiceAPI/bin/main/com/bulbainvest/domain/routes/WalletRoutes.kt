package com.bulbainvest.domain.routes

import com.bulbainvest.domain.api.BadRequestException
import com.bulbainvest.domain.api.CreateWalletRequest
import com.bulbainvest.domain.api.toWalletDto
import com.bulbainvest.domain.plugins.USER_AUTH
import com.bulbainvest.domain.plugins.userId
import com.bulbainvest.domain.service.WalletService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

fun Route.walletRoutes(wallets: WalletService) {
    authenticate(USER_AUTH) {
        get("/api/wallets/me") {
            val list = wallets.list(call.userId()).map { it.toWalletDto() }
            call.respond(list)
        }

        post("/api/wallets") {
            val body = call.receive<CreateWalletRequest>()
            val row = wallets.create(call.userId(), body.currency)
            call.respond(HttpStatusCode.Created, row.toWalletDto())
        }

        route("/api/wallets/{walletId}") {
            post("/make-default") {
                val walletId = parseUuid(call.parameters["walletId"])
                val row = wallets.makeDefault(call.userId(), walletId)
                call.respond(row.toWalletDto())
            }
            delete {
                val walletId = parseUuid(call.parameters["walletId"])
                wallets.delete(call.userId(), walletId)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

internal fun parseUuid(s: String?): UUID =
    runCatching { UUID.fromString(s) }.getOrElse { throw BadRequestException("invalid uuid") }
