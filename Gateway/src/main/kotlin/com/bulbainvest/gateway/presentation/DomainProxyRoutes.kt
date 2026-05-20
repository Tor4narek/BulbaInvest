package com.bulbainvest.gateway.presentation

import com.bulbainvest.gateway.application.ProxyUseCase
import com.bulbainvest.gateway.domain.DownstreamService
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

fun Route.domainProxyRoutes(proxyUseCase: ProxyUseCase) {
    route("/api") {
        installProxyHandlers(proxyUseCase, DownstreamService.DOMAIN)

        route("{...}") {
            installProxyHandlers(proxyUseCase, DownstreamService.DOMAIN)
        }
    }
}
