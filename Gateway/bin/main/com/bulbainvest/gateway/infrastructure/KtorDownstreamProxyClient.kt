package com.bulbainvest.gateway.infrastructure

import com.bulbainvest.gateway.config.HttpClientConfig
import com.bulbainvest.gateway.domain.BadGatewayException
import com.bulbainvest.gateway.domain.DownstreamProxyClient
import com.bulbainvest.gateway.domain.GatewayTimeoutException
import com.bulbainvest.gateway.domain.ProxyRequest
import com.bulbainvest.gateway.domain.ProxyResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

class KtorDownstreamProxyClient(
    httpClientConfig: HttpClientConfig,
) : DownstreamProxyClient {
    private val client = HttpClient(CIO) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = httpClientConfig.requestTimeoutMillis
            connectTimeoutMillis = httpClientConfig.connectTimeoutMillis
            socketTimeoutMillis = httpClientConfig.socketTimeoutMillis
        }
    }

    override suspend fun execute(request: ProxyRequest): ProxyResponse {
        val url = buildTargetUrl(request.target.baseUrl, request.encodedPath, request.queryString)

        try {
            val response = client.request(url) {
                method = request.method
                setBody(request.body)
                headers {
                    request.headers.forEach { name, values ->
                        if (name !in EXCLUDED_REQUEST_HEADERS) {
                            values.forEach { value -> append(name, value) }
                        }
                    }
                }
            }

            return ProxyResponse(
                statusCode = response.status.value,
                headers = Headers.build {
                    response.headers.forEach { name, values ->
                        if (name !in EXCLUDED_RESPONSE_HEADERS) {
                            values.forEach { value -> append(name, value) }
                        }
                    }
                },
                body = response.body<ByteArray>(),
            )
        } catch (ex: HttpRequestTimeoutException) {
            throw GatewayTimeoutException("Downstream request timed out", ex)
        } catch (ex: SocketTimeoutException) {
            throw GatewayTimeoutException("Downstream socket timed out", ex)
        } catch (ex: ConnectException) {
            throw BadGatewayException("Failed to connect to downstream service", ex)
        } catch (ex: IOException) {
            throw BadGatewayException("Downstream I/O error", ex)
        }
    }

    private fun buildTargetUrl(baseUrl: String, encodedPath: String, queryString: String?): String {
        val normalizedBaseUrl = baseUrl.trimEnd('/')
        val normalizedPath = if (encodedPath.startsWith("/")) encodedPath else "/$encodedPath"
        return buildString {
            append(normalizedBaseUrl)
            append(normalizedPath)
            if (!queryString.isNullOrBlank()) {
                append('?')
                append(queryString)
            }
        }
    }

    private companion object {
        val EXCLUDED_REQUEST_HEADERS = setOf(
            HttpHeaders.Host,
            HttpHeaders.ContentLength,
            HttpHeaders.TransferEncoding,
            HttpHeaders.Connection,
            "Keep-Alive",
            "Proxy-Authenticate",
            "Proxy-Authorization",
            HttpHeaders.Upgrade,
            HttpHeaders.TE,
            HttpHeaders.Trailer,
        )

        val EXCLUDED_RESPONSE_HEADERS = setOf(
            HttpHeaders.ContentLength,
            HttpHeaders.TransferEncoding,
            HttpHeaders.Connection,
            "Keep-Alive",
            "Proxy-Authenticate",
            "Proxy-Authorization",
            HttpHeaders.Upgrade,
            HttpHeaders.TE,
            HttpHeaders.Trailer,
        )
    }
}
