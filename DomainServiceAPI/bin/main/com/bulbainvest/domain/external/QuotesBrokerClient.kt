package com.bulbainvest.domain.external

import com.bulbainvest.domain.api.NotFoundException
import com.bulbainvest.domain.config.BrokerRedisConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import java.math.BigDecimal

/**
 * Чтение последней котировки из Redis-брокера.
 *
 * Контракт публикации со стороны StocksService см. docs/quotes-broker.md
 *
 * Ключ:   <quotesKeyPrefix><TICKER>            например: "quotes:last:AAPL"
 * Тип:    String с JSON-payload (см. [Quote])
 * Опц.:   Pub/Sub канал "<quotesKeyPrefix>updates" с тем же payload — для live-обновлений.
 *         Domain сейчас читает только snapshot, подписка не нужна.
 */
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
                json.decodeFromString<Quote>(raw)
            }
        } catch (e: Exception) {
            log.error("Failed to read/parse quote for {} (key={}): {}", ticker, key, e.message)
            null
        }
    }

    @Serializable
    data class Quote(
        val ticker: String,
        val price: String,          // last trade price, decimal as string
        val bid: String? = null,    // лучшая цена покупки
        val ask: String? = null,    // лучшая цена продажи
        val volume: Long? = null,   // объём за сессию
        val timestamp: String,      // ISO-8601 UTC, e.g. "2026-05-12T10:00:00Z"
        val source: String? = null, // например "MOEX"
    )
}
