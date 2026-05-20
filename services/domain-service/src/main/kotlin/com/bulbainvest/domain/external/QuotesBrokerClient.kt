package com.bulbainvest.domain.external

import com.bulbainvest.domain.api.NotFoundException
import com.bulbainvest.domain.config.BrokerRedisConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import java.math.BigDecimal
import java.time.Instant

class QuotesBrokerClient(
    private val pool: JedisPool,
    private val cfg: BrokerRedisConfig,
) {
    private val log = LoggerFactory.getLogger(QuotesBrokerClient::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    fun getLastPrice(ticker: String): BigDecimal {
        val quote = getLastQuote(ticker)
            ?: throw NotFoundException("No quote for ticker $ticker")
        return BigDecimal(quote.price)
    }

    fun getLastQuote(ticker: String): Quote? {
        val key = cfg.quotesKeyPrefix + ticker.uppercase()
        return try {
            pool.resource.use { jedis ->
                val raw = jedis.get(key) ?: return null
                parseQuote(raw)
            }
        } catch (e: Exception) {
            log.error("Failed to read/parse quote for {} (key={}): {}", ticker, key, e.message)
            null
        }
    }

    private fun parseQuote(raw: String): Quote {
        runCatching { json.decodeFromString<Quote>(raw) }
            .getOrNull()
            ?.let { return it }

        val marketQuote = json.decodeFromString<MarketQuote>(raw)
        return Quote(
            ticker = marketQuote.ticker,
            price = BigDecimal.valueOf(marketQuote.price).toPlainString(),
            timestamp = Instant.ofEpochSecond(marketQuote.updatedAt).toString(),
        )
    }

    @Serializable
    data class Quote(
        val ticker: String,
        val price: String,
        val bid: String? = null,
        val ask: String? = null,
        val volume: Long? = null,
        val timestamp: String,
        val source: String? = null,
    )

    @Serializable
    data class MarketQuote(
        val ticker: String,
        val price: Double,
        val updatedAt: Long,
    )
}
