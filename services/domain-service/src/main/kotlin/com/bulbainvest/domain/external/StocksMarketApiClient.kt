package com.bulbainvest.domain.external

import com.bulbainvest.domain.api.BadRequestException
import com.bulbainvest.domain.api.ConflictException
import com.bulbainvest.domain.config.StocksMarketApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.util.UUID

/**
 * Клиент StocksMarketAPI (Go).
 *
 * Контракт см. docs/stocks-market-api.md
 *
 * - decrement: вызывается при BUY_FROM_COMPANY (юзер покупает у компании, остаток ↓)
 * - increment: вызывается при SELL_TO_COMPANY (юзер продаёт компании, остаток ↑)
 *
 * idempotencyKey — UUID сделки/операции. Сервис обязан гарантировать,
 * что повторный вызов с тем же ключом не изменит остаток.
 */
class StocksMarketApiClient(private val cfg: StocksMarketApiConfig) {
    private val log = LoggerFactory.getLogger(StocksMarketApiClient::class.java)

    private val client = HttpClient(CIO) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 5_000
            connectTimeoutMillis = 2_000
            socketTimeoutMillis = 5_000
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 2)
            exponentialDelay(base = 2.0, maxDelayMs = 1_000)
        }
    }

    fun decrementCompanyStocks(ticker: String, quantity: BigDecimal, idempotencyKey: UUID): InventoryResponse =
        call("/api/v1/inventory/decrement", ticker, quantity, idempotencyKey)

    fun incrementCompanyStocks(ticker: String, quantity: BigDecimal, idempotencyKey: UUID): InventoryResponse =
        call("/api/v1/inventory/increment", ticker, quantity, idempotencyKey)

    private fun call(
        path: String,
        ticker: String,
        quantity: BigDecimal,
        idempotencyKey: UUID,
    ): InventoryResponse = runBlocking {
        val url = cfg.baseUrl.trimEnd('/') + path
        val request = InventoryRequest(
            ticker = ticker,
            quantity = quantity.toPlainString(),
            idempotencyKey = idempotencyKey.toString(),
        )
        log.info("StocksMarketAPI POST {} ticker={} qty={} key={}", url, ticker, quantity, idempotencyKey)

        val resp: HttpResponse = client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        when (resp.status) {
            HttpStatusCode.OK -> resp.body<InventoryResponse>()
            HttpStatusCode.Conflict -> {
                val err = runCatching { resp.body<ErrorBody>() }.getOrNull()
                throw ConflictException(err?.message ?: "stocks-market conflict")
            }
            HttpStatusCode.UnprocessableEntity -> {
                val err = runCatching { resp.body<ErrorBody>() }.getOrNull()
                throw BadRequestException(err?.message ?: "stocks-market validation error")
            }
            else -> {
                val text = runCatching { resp.body<String>() }.getOrDefault("")
                error("StocksMarketAPI ${resp.status.value}: $text")
            }
        }
    }

    @Serializable
    data class InventoryRequest(
        val ticker: String,
        val quantity: String,           // decimal as string
        val idempotencyKey: String,     // UUID
    )

    @Serializable
    data class InventoryResponse(
        val ticker: String,
        val remaining: String,          // decimal as string
        val idempotencyKey: String,
        val applied: Boolean,           // false = повторный вызов с тем же ключом, остаток не менялся
    )

    @Serializable
    data class ErrorBody(val code: String? = null, val message: String? = null)
}
