package com.stocksim.graphservice.repository

import com.stocksim.graphservice.config.AppConfig
import com.stocksim.graphservice.model.Candle
import com.stocksim.graphservice.model.Granularity
import com.stocksim.graphservice.model.LatestPriceResponse
import com.stocksim.graphservice.model.QuoteRow
import mu.KotlinLogging
import java.sql.Connection

private val logger = KotlinLogging.logger {}

class QuoteRepository(private val conn: Connection) {

    init {
        initSchema()
    }

    private fun initSchema() {
        val db = AppConfig.clickhouse.database
        conn.createStatement().use { st ->
            st.execute("CREATE DATABASE IF NOT EXISTS $db")
        }
        conn.createStatement().use { st ->
            st.execute("""
                CREATE TABLE IF NOT EXISTS $db.quotes (
                    ticker              LowCardinality(String),
                    price               Float64,
                    available_quantity  Int64,
                    volatility          Float64,
                    ts                  DateTime('UTC')
                )
                ENGINE = MergeTree()
                PARTITION BY toYYYYMM(ts)
                ORDER BY (ticker, ts)
                TTL ts + INTERVAL 12 MONTH
                SETTINGS index_granularity = 8192
            """.trimIndent())
        }
        // Migrate existing table from old schema (buy_price, sell_price, mid_price)
        // to new schema (price, available_quantity, volatility).
        conn.createStatement().use { st ->
            st.execute("ALTER TABLE $db.quotes ADD COLUMN IF NOT EXISTS price Float64")
            st.execute("ALTER TABLE $db.quotes ADD COLUMN IF NOT EXISTS available_quantity Int64")
            st.execute("ALTER TABLE $db.quotes ADD COLUMN IF NOT EXISTS volatility Float64")
            st.execute("ALTER TABLE $db.quotes DROP COLUMN IF EXISTS buy_price")
            st.execute("ALTER TABLE $db.quotes DROP COLUMN IF EXISTS sell_price")
            st.execute("ALTER TABLE $db.quotes DROP COLUMN IF EXISTS mid_price")
        }
        logger.info { "ClickHouse schema initialised (db=$db)" }
    }

    fun insertBatch(rows: List<QuoteRow>) {
        if (rows.isEmpty()) return
        val db = AppConfig.clickhouse.database
        val sql = "INSERT INTO $db.quotes (ticker, price, available_quantity, volatility, ts) VALUES (?, ?, ?, ?, ?)"
        conn.prepareStatement(sql).use { ps ->
            for (row in rows) {
                ps.setString(1, row.ticker)
                ps.setDouble(2, row.price)
                ps.setLong(3, row.availableQuantity)
                ps.setDouble(4, row.volatility)
                ps.setLong(5, row.timestamp)
                ps.addBatch()
            }
            ps.executeBatch()
        }
        logger.debug { "Inserted ${rows.size} rows into ClickHouse" }
    }

    fun getCandles(ticker: String, granularity: Granularity): List<Candle> {
        val db = AppConfig.clickhouse.database
        val intervalFn = granularity.clickhouseInterval
        val lookbackHours = granularity.lookbackHours

        val sql = """
            SELECT
                toString(${intervalFn}(ts)) AS bucket,
                argMin(price, ts)            AS open,
                max(price)                   AS high,
                min(price)                   AS low,
                argMax(price, ts)            AS close,
                count()                      AS volume
            FROM $db.quotes
            WHERE ticker = ?
              AND ts >= now() - INTERVAL $lookbackHours HOUR
            GROUP BY bucket
            ORDER BY bucket ASC
        """.trimIndent()

        return conn.prepareStatement(sql).use { ps ->
            ps.setString(1, ticker)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(Candle(
                            time = rs.getString("bucket"),
                            open = rs.getDouble("open"),
                            high = rs.getDouble("high"),
                            low = rs.getDouble("low"),
                            close = rs.getDouble("close"),
                            volume = rs.getLong("volume")
                        ))
                    }
                }
            }
        }
    }

    fun getLatestPrice(ticker: String): LatestPriceResponse? {
        val db = AppConfig.clickhouse.database
        val sql = """
            SELECT
                ticker,
                price,
                available_quantity,
                volatility,
                toUnixTimestamp(ts) AS ts_sec
            FROM $db.quotes
            WHERE ticker = ?
            ORDER BY ts DESC
            LIMIT 1
        """.trimIndent()

        return conn.prepareStatement(sql).use { ps ->
            ps.setString(1, ticker)
            ps.executeQuery().use { rs ->
                if (rs.next()) {
                    LatestPriceResponse(
                        ticker = rs.getString("ticker"),
                        price = rs.getDouble("price"),
                        availableQuantity = rs.getLong("available_quantity"),
                        volatility = rs.getDouble("volatility"),
                        timestamp = rs.getLong("ts_sec") * 1000L
                    )
                } else null
            }
        }
    }

    fun getKnownTickers(): List<String> {
        val db = AppConfig.clickhouse.database
        return conn.createStatement().use { st ->
            st.executeQuery("SELECT DISTINCT ticker FROM $db.quotes ORDER BY ticker").use { rs ->
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
        }
    }
}
