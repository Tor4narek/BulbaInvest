package com.stocksim.graphservice.consumer

import com.stocksim.graphservice.config.AppConfig
import com.stocksim.graphservice.model.PriceTick
import com.stocksim.graphservice.model.QuoteRow
import com.stocksim.graphservice.repository.QuoteRepository
import io.lettuce.core.RedisClient
import io.lettuce.core.pubsub.RedisPubSubAdapter
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.util.concurrent.CopyOnWriteArrayList

private val logger = KotlinLogging.logger {}

/**
 * Subscribes to the Redis price channel published by StocksMarketAPI (Go).
 *
 * Messages are accumulated in an in-memory [batch]. A background coroutine
 * flushes the batch to ClickHouse every [AppConfig.batch.flushIntervalSeconds]
 * seconds, or immediately when [AppConfig.batch.maxSize] is reached.
 *
 * Thread-safety: [batch] uses [CopyOnWriteArrayList] so the listener callback
 * (Lettuce I/O thread) and the flusher coroutine (dispatcher thread) don't
 * require explicit locking for most operations. The drain-and-flush is protected
 * by a [Mutex] to ensure only one flush runs at a time.
 */
class PriceConsumer(
    private val redisClient: RedisClient,
    private val repo: QuoteRepository
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val batch = CopyOnWriteArrayList<QuoteRow>()
    private val flushMutex = kotlinx.coroutines.sync.Mutex()

    private var scope: CoroutineScope? = null
    private var pubSubConn: StatefulRedisPubSubConnection<String, String>? = null

    fun start() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        subscribeToRedis()
        startFlusher()
    }

    fun stop() {
        runBlocking { flushBatch() }      // drain remaining items on shutdown
        pubSubConn?.close()
        scope?.cancel()
        logger.info { "PriceConsumer stopped. Flushed remaining ${batch.size} items." }
    }

    // ─── Redis subscription ────────────────────────────────────────────────────

    private fun subscribeToRedis() {
        pubSubConn = redisClient.connectPubSub().also { conn ->
            conn.addListener(object : RedisPubSubAdapter<String, String>() {
                override fun message(channel: String, message: String) {
                    handleMessage(message)
                }
            })
            conn.sync().subscribe(AppConfig.redis.channel)
            logger.info { "Subscribed to Redis channel '${AppConfig.redis.channel}'" }
        }
    }

    private fun handleMessage(raw: String) {
        try {
            val tick = json.decodeFromString<PriceTick>(raw)
            val row = QuoteRow(
                ticker = tick.ticker.uppercase(),
                buyPrice = tick.buyPrice,
                sellPrice = tick.sellPrice,
                midPrice = (tick.buyPrice + tick.sellPrice) / 2.0,
                timestamp = tick.timestamp
            )
            batch.add(row)
            logger.trace { "Received tick: $row" }

            // Eager flush when batch is full
            if (batch.size >= AppConfig.batch.maxSize) {
                scope?.launch { flushBatch() }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse price tick: $raw" }
        }
    }

    // ─── Periodic flusher ──────────────────────────────────────────────────────

    private fun startFlusher() {
        scope?.launch {
            val intervalMs = AppConfig.batch.flushIntervalSeconds * 1_000L
            while (isActive) {
                delay(intervalMs)
                flushBatch()
            }
        }
        logger.info { "Batch flusher started (interval=${AppConfig.batch.flushIntervalSeconds}s, maxSize=${AppConfig.batch.maxSize})" }
    }

    /**
     * Drains [batch] and writes all accumulated rows to ClickHouse.
     * Uses a mutex so concurrent eager flushes don't produce double-writes.
     */
    private suspend fun flushBatch() = withContext(Dispatchers.IO) {
        flushMutex.withLock {
            if (batch.isEmpty()) return@withLock

            // Snapshot and clear atomically from our perspective
            val rows = batch.toList()
            batch.clear()

            try {
                repo.insertBatch(rows)
                logger.info { "Flushed ${rows.size} rows to ClickHouse" }
            } catch (e: Exception) {
                // On failure, put rows back so they're not lost
                batch.addAll(rows)
                logger.error(e) { "Failed to flush ${rows.size} rows to ClickHouse — will retry next interval" }
            }
        }
    }
}
