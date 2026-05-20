package com.bulbainvest.gateway.application

import com.bulbainvest.gateway.domain.DownstreamProxyClient
import com.bulbainvest.gateway.domain.DownstreamService
import com.bulbainvest.gateway.domain.DownstreamServiceRegistry
import com.bulbainvest.gateway.domain.ProxyRequest
import com.bulbainvest.gateway.domain.ProxyResponse
import io.ktor.http.Headers
import io.ktor.http.HttpMethod

class ProxyUseCase(
    private val registry: DownstreamServiceRegistry,
    private val proxyClient: DownstreamProxyClient,
) {
    suspend fun forwardTo(
        service: DownstreamService,
        method: HttpMethod,
        encodedPath: String,
        queryString: String?,
        headers: Headers,
        body: ByteArray,
    ): ProxyResponse {
        val target = registry.targetFor(service)
        return proxyClient.execute(
            ProxyRequest(
                target = target,
                method = method,
                encodedPath = encodedPath,
                queryString = queryString,
                headers = headers,
                body = body,
            )
        )
    }
}
