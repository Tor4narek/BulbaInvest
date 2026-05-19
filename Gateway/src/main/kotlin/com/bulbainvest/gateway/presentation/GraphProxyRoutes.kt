package com.bulbainvest.gateway.presentation

import com.bulbainvest.gateway.application.ProxyUseCase
import com.bulbainvest.gateway.domain.DownstreamService
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

fun Route.graphProxyRoutes(proxyUseCase: ProxyUseCase) {
    route("/api/tickers") {
        installProxyHandlers(proxyUseCase, DownstreamService.GRAPH)
    }

    route("/api/quotes/{ticker}/latest") {
        installProxyHandlers(proxyUseCase, DownstreamService.GRAPH)
    }

    route("/api/quotes/{ticker}/stats") {
        installProxyHandlers(proxyUseCase, DownstreamService.GRAPH)
    }
}
