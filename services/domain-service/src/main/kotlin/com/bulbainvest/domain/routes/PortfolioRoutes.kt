package com.bulbainvest.domain.routes

import com.bulbainvest.domain.api.BadRequestException
import com.bulbainvest.domain.api.toPositionDto
import com.bulbainvest.domain.plugins.USER_AUTH
import com.bulbainvest.domain.plugins.userId
import com.bulbainvest.domain.service.PortfolioService
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.portfolioRoutes(portfolio: PortfolioService) {
    authenticate(USER_AUTH) {
        get("/api/portfolio") {
            val list = portfolio.list(call.userId()).map { it.toPositionDto() }
            call.respond(list)
        }
        get("/api/portfolio/{ticker}") {
            val ticker = call.parameters["ticker"]?.uppercase()
                ?: throw BadRequestException("ticker required")
            call.respond(portfolio.get(call.userId(), ticker).toPositionDto())
        }
    }
}
