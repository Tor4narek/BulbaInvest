package com.bulbainvest.gateway.domain

import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.Url

data class ProxyRequest(
    val target: DownstreamTarget,
    val method: HttpMethod,
    val encodedPath: String,
    val queryString: String?,
    val headers: Headers,
    val body: ByteArray,
)

data class ProxyResponse(
    val statusCode: Int,
    val headers: Headers,
    val body: ByteArray,
)

interface DownstreamProxyClient {
    suspend fun execute(request: ProxyRequest): ProxyResponse
}
