package com.bulbainvest.gateway

import com.bulbainvest.gateway.application.ProxyUseCase
import com.bulbainvest.gateway.application.QuoteSubscriptionUseCase
import com.bulbainvest.gateway.domain.BadGatewayException
import com.bulbainvest.gateway.domain.DownstreamProxyClient
import com.bulbainvest.gateway.domain.DownstreamService
import com.bulbainvest.gateway.domain.DownstreamServiceRegistry
import com.bulbainvest.gateway.domain.DownstreamTarget
import com.bulbainvest.gateway.domain.GatewayTimeoutException
import com.bulbainvest.gateway.domain.MarketQuotesStream
import com.bulbainvest.gateway.domain.ProxyRequest
import com.bulbainvest.gateway.domain.ProxyResponse
import com.bulbainvest.gateway.domain.QuotesUpdatedEvent
import com.bulbainvest.gateway.domain.StockQuote
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GatewayModuleTest {
    @Test
    fun `health endpoint is local`() = testApplication {
        application {
            gatewayModule(
                GatewayDependencies(
                    proxyUseCase = ProxyUseCase(FakeRegistry(), RecordingProxyClient()),
                    quoteSubscriptionUseCase = QuoteSubscriptionUseCase(FakeMarketQuotesStream()),
                )
            )
        }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"status":"ok"}""", response.bodyAsText())
    }

    @Test
    fun `gateway proxies request method path query body and auth header`() = testApplication {
        val proxyClient = RecordingProxyClient()
        application {
            gatewayModule(
                GatewayDependencies(
                    proxyUseCase = ProxyUseCase(FakeRegistry(), proxyClient),
                    quoteSubscriptionUseCase = QuoteSubscriptionUseCase(FakeMarketQuotesStream()),
                )
            )
        }

        val response = client.post("/api/orders/buy?ticker=AAPL&mode=fast") {
            header(HttpHeaders.Authorization, "Bearer test-token")
            contentType(ContentType.Application.Json)
            setBody("""{"ticker":"AAPL","quantity":"2","maxPrice":"100"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"status":"executed"}""", response.bodyAsText())

        val captured = proxyClient.captured.single()
        assertEquals("/api/orders/buy", captured.encodedPath)
        assertEquals("ticker=AAPL&mode=fast", captured.queryString)
        assertEquals("Bearer test-token", captured.headers[HttpHeaders.Authorization])
        assertEquals("""{"ticker":"AAPL","quantity":"2","maxPrice":"100"}""", captured.body.decodeToString())
        assertEquals("http://domain-service:8040", captured.target.baseUrl)
    }

    @Test
    fun `gateway routes graph tickers endpoint to graph service`() = testApplication {
        val proxyClient = RecordingProxyClient()
        application {
            gatewayModule(
                GatewayDependencies(
                    proxyUseCase = ProxyUseCase(FakeRegistry(), proxyClient),
                    quoteSubscriptionUseCase = QuoteSubscriptionUseCase(FakeMarketQuotesStream()),
                )
            )
        }

        val response = client.get("/api/tickers")

        assertEquals(HttpStatusCode.OK, response.status)
        val captured = proxyClient.captured.single()
        assertEquals("/api/tickers", captured.encodedPath)
        assertEquals("http://graph-service:8083", captured.target.baseUrl)
        assertEquals(DownstreamService.GRAPH, captured.target.service)
    }

    @Test
    fun `gateway routes graph quote endpoints to graph service and preserves query`() = testApplication {
        val proxyClient = RecordingProxyClient()
        application {
            gatewayModule(
                GatewayDependencies(
                    proxyUseCase = ProxyUseCase(FakeRegistry(), proxyClient),
                    quoteSubscriptionUseCase = QuoteSubscriptionUseCase(FakeMarketQuotesStream()),
                )
            )
        }

        val response = client.get("/api/quotes/aapl/stats?granularity=week")

        assertEquals(HttpStatusCode.OK, response.status)
        val captured = proxyClient.captured.single()
        assertEquals("/api/quotes/aapl/stats", captured.encodedPath)
        assertEquals("granularity=week", captured.queryString)
        assertEquals("http://graph-service:8083", captured.target.baseUrl)
        assertEquals(DownstreamService.GRAPH, captured.target.service)
    }

    @Test
    fun `gateway preserves downstream status and content type`() = testApplication {
        application {
            gatewayModule(
                GatewayDependencies(
                    proxyUseCase = ProxyUseCase(FakeRegistry(), RecordingProxyClient(statusCode = 201)),
                    quoteSubscriptionUseCase = QuoteSubscriptionUseCase(FakeMarketQuotesStream()),
                )
            )
        }

        val response = client.post("/api/orders/sell") {
            contentType(ContentType.Application.Json)
            setBody("""{"ticker":"AAPL","quantity":"1","price":"150.00"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(ContentType.Application.Json.toString(), response.headers[HttpHeaders.ContentType])
    }

    @Test
    fun `gateway maps downstream timeout to 504`() = testApplication {
        application {
            gatewayModule(
                GatewayDependencies(
                    proxyUseCase = ProxyUseCase(FakeRegistry(), ThrowingProxyClient(GatewayTimeoutException("timeout"))),
                    quoteSubscriptionUseCase = QuoteSubscriptionUseCase(FakeMarketQuotesStream()),
                )
            )
        }

        val response = client.get("/api/orders/my")

        assertEquals(HttpStatusCode.GatewayTimeout, response.status)
        assertTrue(response.bodyAsText().contains("gateway_timeout"))
    }

    @Test
    fun `gateway maps downstream connectivity failures to 502`() = testApplication {
        application {
            gatewayModule(
                GatewayDependencies(
                    proxyUseCase = ProxyUseCase(FakeRegistry(), ThrowingProxyClient(BadGatewayException("downstream unavailable"))),
                    quoteSubscriptionUseCase = QuoteSubscriptionUseCase(FakeMarketQuotesStream()),
                )
            )
        }

        val response = client.get("/api/trades")

        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertTrue(response.bodyAsText().contains("bad_gateway"))
    }

    @Test
    fun `websocket sends current quotes and later matching updates for all requested tickers`() = testApplication {
        val marketQuotesStream = FakeMarketQuotesStream(
            currentQuotes = mapOf(
                "AAPL" to StockQuote(
                    ticker = "AAPL",
                    price = 192.45,
                    availableQuantity = 10_000,
                    updatedAt = 1710000000,
                ),
                "MSFT" to StockQuote(
                    ticker = "MSFT",
                    price = 401.11,
                    availableQuantity = 2_000,
                    updatedAt = 1710000001,
                ),
            )
        )

        application {
            gatewayModule(
                GatewayDependencies(
                    proxyUseCase = ProxyUseCase(FakeRegistry(), RecordingProxyClient()),
                    quoteSubscriptionUseCase = QuoteSubscriptionUseCase(marketQuotesStream),
                )
            )
        }

        val wsClient = createClient {
            install(WebSockets)
        }

        val session = wsClient.webSocketSession("/ws/quotes?ticker=AAPL&ticker=MSFT")

        val initialFrame = session.incoming.receive() as Frame.Text
        val initialPayload = initialFrame.readText()
        assertTrue(initialPayload.startsWith("["))
        assertTrue(initialPayload.contains(""""ticker":"AAPL""""))
        assertTrue(initialPayload.contains(""""ticker":"MSFT""""))

        marketQuotesStream.emit(
            QuotesUpdatedEvent(
                type = "MARKET_QUOTES_UPDATED",
                quotes = listOf(
                    StockQuote(
                        ticker = "GOOG",
                        price = 155.33,
                        availableQuantity = 3_000,
                        updatedAt = 1710000002,
                    ),
                    StockQuote(
                        ticker = "MSFT",
                        price = 402.0,
                        availableQuantity = 1_950,
                        updatedAt = 1710000003,
                    ),
                    StockQuote(
                        ticker = "AAPL",
                        price = 193.10,
                        availableQuantity = 9_900,
                        updatedAt = 1710000004,
                    ),
                ),
            )
        )

        val nextFrame = session.incoming.receive() as Frame.Text
        val nextPayload = nextFrame.readText()
        assertTrue(nextPayload.startsWith("["))
        assertTrue(nextPayload.contains(""""ticker":"AAPL""""))
        assertTrue(nextPayload.contains(""""ticker":"MSFT""""))
        assertTrue(nextPayload.contains(""""price":193.1"""))
        assertTrue(!nextPayload.contains(""""ticker":"GOOG""""))
    }

    private class FakeRegistry : DownstreamServiceRegistry {
        override fun targetFor(service: DownstreamService): DownstreamTarget =
            when (service) {
                DownstreamService.DOMAIN -> DownstreamTarget(service, "http://domain-service:8040")
                DownstreamService.GRAPH -> DownstreamTarget(service, "http://graph-service:8083")
            }
    }

    private class FakeMarketQuotesStream(
        private val currentQuotes: Map<String, StockQuote> = emptyMap(),
    ) : MarketQuotesStream {
        private val updatesFlow = MutableSharedFlow<QuotesUpdatedEvent>(extraBufferCapacity = 16)

        override val updates: SharedFlow<QuotesUpdatedEvent> = updatesFlow.asSharedFlow()

        override suspend fun currentQuote(ticker: String): StockQuote? = currentQuotes[ticker]

        override fun start() = Unit

        override fun stop() = Unit

        fun emit(event: QuotesUpdatedEvent) {
            updatesFlow.tryEmit(event)
        }
    }

    private class RecordingProxyClient(
        private val statusCode: Int = 200,
    ) : DownstreamProxyClient {
        val captured = mutableListOf<ProxyRequest>()

        override suspend fun execute(request: ProxyRequest): ProxyResponse {
            captured += request
            return ProxyResponse(
                statusCode = statusCode,
                headers = Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                },
                body = if (statusCode == 201) {
                    """{"id":"wallet-1"}""".encodeToByteArray()
                } else {
                    """{"status":"executed"}""".encodeToByteArray()
                },
            )
        }
    }

    private class ThrowingProxyClient(
        private val exception: RuntimeException,
    ) : DownstreamProxyClient {
        override suspend fun execute(request: ProxyRequest): ProxyResponse {
            throw exception
        }
    }
}
