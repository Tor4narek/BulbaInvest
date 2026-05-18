package com.stocksim.graphservice.config

import com.typesafe.config.ConfigFactory

object AppConfig {
    private val config = ConfigFactory.load()

    object server {
        val port: Int = config.getInt("server.port")
    }

    object redis {
        val host: String = config.getString("redis.host")
        val port: Int = config.getInt("redis.port")
        val channel: String = config.getString("redis.channel")
    }

    object clickhouse {
        val url: String = config.getString("clickhouse.url")
        val user: String = config.getString("clickhouse.user")
        val password: String = config.getString("clickhouse.password")
        val database: String = config.getString("clickhouse.database")
    }

    object batch {
        /** How often to flush accumulated prices to ClickHouse, in seconds */
        val flushIntervalSeconds: Long = config.getLong("batch.flushIntervalSeconds")
        val maxSize: Int = config.getInt("batch.maxSize")
    }
}
