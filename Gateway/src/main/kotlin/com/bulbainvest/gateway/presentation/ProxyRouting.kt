package com.bulbainvest.gateway.presentation

import com.bulbainvest.gateway.application.ProxyUseCase
import com.bulbainvest.gateway.domain.DownstreamService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
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

internal fun Route.installProxyHandlers(
    proxyUseCase: ProxyUseCase,
    service: DownstreamService,
) {
    get { call.proxyCurrentCall(proxyUseCase, service) }
    post { call.proxyCurrentCall(proxyUseCase, service) }
    put { call.proxyCurrentCall(proxyUseCase, service) }
    patch { call.proxyCurrentCall(proxyUseCase, service) }
    delete { call.proxyCurrentCall(proxyUseCase, service) }
    options { call.proxyCurrentCall(proxyUseCase, service) }
    head { call.proxyCurrentCall(proxyUseCase, service) }
}

private suspend fun ApplicationCall.proxyCurrentCall(
    proxyUseCase: ProxyUseCase,
    service: DownstreamService,
) {
    val proxied = proxyUseCase.forwardTo(
        service = service,
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
