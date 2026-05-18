package com.bulbainvest.gateway.presentation

import com.bulbainvest.gateway.application.ProxyUseCase
import com.bulbainvest.gateway.domain.DownstreamService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.queryString
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.options
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.domainProxyRoutes(proxyUseCase: ProxyUseCase) {
    route("/api") {
        installProxyHandlers(proxyUseCase)

        route("{...}") {
            installProxyHandlers(proxyUseCase)
        }
    }
}

private fun Route.installProxyHandlers(proxyUseCase: ProxyUseCase) {
    get { call.proxyCurrentCall(proxyUseCase) }
    post { call.proxyCurrentCall(proxyUseCase) }
    put { call.proxyCurrentCall(proxyUseCase) }
    patch { call.proxyCurrentCall(proxyUseCase) }
    delete { call.proxyCurrentCall(proxyUseCase) }
    options { call.proxyCurrentCall(proxyUseCase) }
    head { call.proxyCurrentCall(proxyUseCase) }
}

private suspend fun io.ktor.server.application.ApplicationCall.proxyCurrentCall(proxyUseCase: ProxyUseCase) {
    val proxied = proxyUseCase.forwardTo(
        service = DownstreamService.DOMAIN,
        method = request.httpMethod,
        encodedPath = request.path(),
        queryString = request.queryString().ifBlank { null },
        headers = request.headers,
        body = receiveNullable<ByteArray>() ?: ByteArray(0),
    )

    proxied.headers.forEach { name, values ->
        values.forEach { value ->
            response.headers.append(name, value)
        }
    }

    respondBytes(
        bytes = proxied.body,
        status = HttpStatusCode.fromValue(proxied.statusCode),
    )
}
