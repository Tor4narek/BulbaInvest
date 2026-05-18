package com.bulbainvest.domain

import com.bulbainvest.domain.auth.AuthCodeStore
import com.bulbainvest.domain.auth.JwtService
import com.bulbainvest.domain.config.AppConfig
import com.bulbainvest.domain.config.DatabaseFactory
import com.bulbainvest.domain.config.appModule
import com.bulbainvest.domain.config.loadAppConfig
import com.bulbainvest.domain.external.MailService
import com.bulbainvest.domain.plugins.configureSecurity
import com.bulbainvest.domain.plugins.configureSerialization
import com.bulbainvest.domain.plugins.configureStatusPages
import com.bulbainvest.domain.routes.authRoutes
import com.bulbainvest.domain.routes.orderRoutes
import com.bulbainvest.domain.routes.portfolioRoutes
import com.bulbainvest.domain.routes.tradeRoutes
import com.bulbainvest.domain.routes.userRoutes
import com.bulbainvest.domain.routes.walletRoutes
import com.bulbainvest.domain.service.OrderService
import com.bulbainvest.domain.service.PortfolioService
import com.bulbainvest.domain.service.TradeService
import com.bulbainvest.domain.service.UserService
import com.bulbainvest.domain.service.WalletService
import io.ktor.server.application.Application
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

@Suppress("unused")
fun Application.module() {
    val cfg: AppConfig = environment.config.loadAppConfig()

    DatabaseFactory.init(cfg.db)

    install(Koin) {
        slf4jLogger()
        modules(appModule(cfg))
    }

    install(CallLogging)
    install(CORS) {
        anyHost()
        allowHeader(io.ktor.http.HttpHeaders.ContentType)
        allowHeader(io.ktor.http.HttpHeaders.Authorization)
        io.ktor.http.HttpMethod.DefaultMethods.forEach { allowMethod(it) }
    }

    // Резолвим зависимости в Application-скоупе: внутри routing { } receiver — Routing,
    // и расширение Koin get() там недоступно.
    val jwtService = get<JwtService>()
    val authCodeStore = get<AuthCodeStore>()
    val mailService = get<MailService>()
    val userService = get<UserService>()
    val walletService = get<WalletService>()
    val portfolioService = get<PortfolioService>()
    val tradeService = get<TradeService>()
    val orderService = get<OrderService>()

    configureSerialization()
    configureStatusPages()
    configureSecurity(cfg.jwt, jwtService)

    routing {
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")

        get("/health") { call.respond(mapOf("status" to "ok")) }

        authRoutes(authCodeStore, mailService, userService, jwtService)
        userRoutes(userService)
        walletRoutes(walletService)
        portfolioRoutes(portfolioService)
        tradeRoutes(tradeService)
        orderRoutes(orderService)
    }
}
