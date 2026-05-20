package com.bulbainvest.domain.routes

import com.bulbainvest.domain.api.BadRequestException
import com.bulbainvest.domain.api.DepositWalletRequest
import com.bulbainvest.domain.api.toWalletDto
import com.bulbainvest.domain.plugins.USER_AUTH
import com.bulbainvest.domain.plugins.userId
import com.bulbainvest.domain.service.WalletService
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.util.UUID

fun Route.walletRoutes(wallets: WalletService) {
    authenticate(USER_AUTH) {
        get("/api/wallets/me") {
            val list = wallets.list(call.userId()).map { it.toWalletDto() }
            call.respond(list)
        }

        post("/api/wallets/me/deposit") {
            val body = call.receive<DepositWalletRequest>()
            val amount = body.amount.toBigDecimalOrError()
            val row = wallets.deposit(call.userId(), amount)
            call.respond(row.toWalletDto())
        }
    }
}

internal fun parseUuid(s: String?): UUID =
    runCatching { UUID.fromString(s) }.getOrElse { throw BadRequestException("invalid uuid") }
