package com.bulbainvest.gateway.infrastructure

import com.bulbainvest.gateway.config.BrokerRedisConfig
import com.bulbainvest.gateway.domain.MarketQuotesStream
import com.bulbainvest.gateway.domain.QuotesUpdatedEvent
import com.bulbainvest.gateway.domain.StockQuote
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.JedisPubSub
import redis.clients.jedis.Protocol
import kotlin.concurrent.thread
import java.util.concurrent.atomic.AtomicBoolean

class RedisMarketQuotesStream(
    private val config: BrokerRedisConfig,
) : MarketQuotesStream {
    private val log = LoggerFactory.getLogger(RedisMarketQuotesStream::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val updatesFlow = MutableSharedFlow<QuotesUpdatedEvent>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    private val running = AtomicBoolean(false)
    private val pool = JedisPool(
        JedisPoolConfig(),
        config.host,
        config.port,
        Protocol.DEFAULT_TIMEOUT,
        config.password,
        config.db,
    )

    @Volatile
    private var subscriberThread: Thread? = null

    @Volatile
    private var pubSub: JedisPubSub? = null

    override val updates: SharedFlow<QuotesUpdatedEvent> = updatesFlow.asSharedFlow()

    override suspend fun currentQuote(ticker: String): StockQuote? {
        val key = config.stockKeyPrefix + ticker.uppercase()
        return try {
            pool.getResource().use { jedis ->
                val payload = jedis.get(key) ?: return null
                json.decodeFromString<StockQuote>(payload)
            }
        } catch (ex: Exception) {
            log.warn("Failed to read current quote key={}: {}", key, ex.message)
            null
        }
    }

    override fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }

        subscriberThread = thread(
            start = true,
            isDaemon = true,
            name = "market-quotes-subscriber",
        ) {
            runSubscriberLoop()
        }
    }

    override fun stop() {
        if (!running.compareAndSet(true, false)) {
            return
        }

        try {
            pubSub?.unsubscribe()
        } catch (_: Exception) {
        }
        try {
            subscriberThread?.interrupt()
        } catch (_: Exception) {
        }
        try {
            pool.close()
        } catch (_: Exception) {
        }
    }

    private fun runSubscriberLoop() {
        while (running.get()) {
            val subscriber = object : JedisPubSub() {
                override fun onMessage(channel: String, message: String) {
                    handleMessage(channel, message)
                }
            }

            pubSub = subscriber

            try {
                pool.getResource().use { jedis: Jedis ->
                    log.info("Subscribing to Redis channel {}", config.quotesChannel)
                    jedis.subscribe(subscriber, config.quotesChannel)
                }
            } catch (ex: Exception) {
                if (running.get()) {
                    log.warn("Redis subscription failed: {}", ex.message)
                    Thread.sleep(1_000)
                }
            } finally {
                pubSub = null
            }
        }
    }

    private fun handleMessage(channel: String, message: String) {
        val event = runCatching {
            json.decodeFromString<QuotesUpdatedEvent>(message)
        }.getOrElse { ex ->
            log.warn("Failed to parse message from {}: {}", channel, ex.message)
            return
        }

        updatesFlow.tryEmit(event)
    }
}
