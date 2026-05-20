package com.bulbainvest.gateway

import com.bulbainvest.gateway.application.ProxyUseCase
import com.bulbainvest.gateway.application.QuoteSubscriptionUseCase
import com.bulbainvest.gateway.config.AppConfig
import com.bulbainvest.gateway.config.appModule
import com.bulbainvest.gateway.config.loadAppConfig
import com.bulbainvest.gateway.domain.MarketQuotesStream
import com.bulbainvest.gateway.plugins.configureSerialization
import com.bulbainvest.gateway.plugins.configureStatusPages
import com.bulbainvest.gateway.plugins.configureWebSockets
import com.bulbainvest.gateway.presentation.domainProxyRoutes
import com.bulbainvest.gateway.presentation.graphProxyRoutes
import com.bulbainvest.gateway.presentation.quoteSocketRoutes
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

data class GatewayDependencies(
    val proxyUseCase: ProxyUseCase,
    val quoteSubscriptionUseCase: QuoteSubscriptionUseCase,
)

@Suppress("unused")
fun Application.module() {
    val cfg: AppConfig = environment.config.loadAppConfig()

    install(Koin) {
        slf4jLogger()
        modules(appModule(cfg))
    }

    val marketQuotesStream: MarketQuotesStream = get()
    monitor.subscribe(ApplicationStarted) {
        marketQuotesStream.start()
    }
    monitor.subscribe(ApplicationStopping) {
        marketQuotesStream.stop()
    }

    gatewayModule(
        GatewayDependencies(
            proxyUseCase = get(),
            quoteSubscriptionUseCase = get(),
        )
    )
}

fun Application.gatewayModule(
    dependencies: GatewayDependencies,
) {
    install(CallLogging)
    install(CORS) {
        anyHost()
        allowCredentials = true
        allowNonSimpleContentTypes = true
        allowHeaders { true }
        io.ktor.http.HttpMethod.DefaultMethods.forEach { allowMethod(it) }
    }

    configureSerialization()
    configureStatusPages()
    configureWebSockets()

    routing {
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        quoteSocketRoutes(dependencies.quoteSubscriptionUseCase)
        graphProxyRoutes(dependencies.proxyUseCase)
        domainProxyRoutes(dependencies.proxyUseCase)
    }
}
