package com.bulbainvest.gateway.config

import io.ktor.server.config.ApplicationConfig

data class AppConfig(
    val services: ServicesConfig,
    val brokerRedis: BrokerRedisConfig,
    val httpClient: HttpClientConfig,
)

data class ServicesConfig(
    val domain: ServiceConfig,
    val graph: ServiceConfig,
)

data class ServiceConfig(
    val baseUrl: String,
)

data class HttpClientConfig(
    val requestTimeoutMillis: Long,
    val connectTimeoutMillis: Long,
    val socketTimeoutMillis: Long,
)

data class BrokerRedisConfig(
    val host: String,
    val port: Int,
    val password: String?,
    val db: Int,
    val quotesChannel: String,
    val stockKeyPrefix: String,
)

fun ApplicationConfig.loadAppConfig(): AppConfig = AppConfig(
    services = ServicesConfig(
        domain = ServiceConfig(
            baseUrl = System.getenv("DOMAIN_SERVICE_URL")
                ?: property("services.domain.baseUrl").getString(),
        ),
        graph = ServiceConfig(
            baseUrl = System.getenv("GRAPH_SERVICE_URL")
                ?: property("services.graph.baseUrl").getString(),
        ),
    ),
    brokerRedis = BrokerRedisConfig(
        host = System.getenv("BROKER_REDIS_HOST")
            ?: property("brokerRedis.host").getString(),
        port = System.getenv("BROKER_REDIS_PORT")?.toInt()
            ?: property("brokerRedis.port").getString().toInt(),
        password = System.getenv("BROKER_REDIS_PASSWORD")
            ?: property("brokerRedis.password").getString().ifBlank { null },
        db = System.getenv("BROKER_REDIS_DB")?.toInt()
            ?: property("brokerRedis.db").getString().toInt(),
        quotesChannel = System.getenv("BROKER_QUOTES_CHANNEL")
            ?: property("brokerRedis.quotesChannel").getString(),
        stockKeyPrefix = System.getenv("BROKER_STOCK_KEY_PREFIX")
            ?: property("brokerRedis.stockKeyPrefix").getString(),
    ),
    httpClient = HttpClientConfig(
        requestTimeoutMillis = System.getenv("HTTP_CLIENT_REQUEST_TIMEOUT_MS")?.toLong()
            ?: property("httpClient.requestTimeoutMillis").getString().toLong(),
        connectTimeoutMillis = System.getenv("HTTP_CLIENT_CONNECT_TIMEOUT_MS")?.toLong()
            ?: property("httpClient.connectTimeoutMillis").getString().toLong(),
        socketTimeoutMillis = System.getenv("HTTP_CLIENT_SOCKET_TIMEOUT_MS")?.toLong()
            ?: property("httpClient.socketTimeoutMillis").getString().toLong(),
    ),
)
