package com.bulbainvest.domain.config

import io.ktor.server.config.ApplicationConfig

data class DbConfig(val jdbcUrl: String, val user: String, val password: String)
data class RedisConfig(val host: String, val port: Int)
data class BrokerRedisConfig(val host: String, val port: Int, val quotesKeyPrefix: String)
data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val realm: String,
    val ttlSeconds: Long,
)
data class MailConfig(
    val host: String,
    val port: Int,
    val from: String,
    val user: String?,
    val password: String?,
)
data class StocksMarketApiConfig(val baseUrl: String)
data class AuthCodeConfig(val ttlSeconds: Long, val length: Int)
data class DefaultsConfig(val currency: String)

data class AppConfig(
    val db: DbConfig,
    val redis: RedisConfig,
    val brokerRedis: BrokerRedisConfig,
    val jwt: JwtConfig,
    val mail: MailConfig,
    val stocksMarketApi: StocksMarketApiConfig,
    val authCode: AuthCodeConfig,
    val defaults: DefaultsConfig,
)

fun ApplicationConfig.loadAppConfig(): AppConfig {
    fun s(path: String) = property(path).getString()
    fun i(path: String) = s(path).toInt()
    fun l(path: String) = s(path).toLong()
    fun sOrNull(path: String) = propertyOrNull(path)?.getString()?.takeIf { it.isNotBlank() }

    return AppConfig(
        db = DbConfig(s("db.jdbcUrl"), s("db.user"), s("db.password")),
        redis = RedisConfig(s("redis.host"), i("redis.port")),
        brokerRedis = BrokerRedisConfig(
            s("brokerRedis.host"), i("brokerRedis.port"), s("brokerRedis.quotesKeyPrefix"),
        ),
        jwt = JwtConfig(
            secret = s("jwt.secret"),
            issuer = s("jwt.issuer"),
            audience = s("jwt.audience"),
            realm = s("jwt.realm"),
            ttlSeconds = l("jwt.ttlSeconds"),
        ),
        mail = MailConfig(
            host = s("mail.host"),
            port = i("mail.port"),
            from = s("mail.from"),
            user = sOrNull("mail.user"),
            password = sOrNull("mail.password"),
        ),
        stocksMarketApi = StocksMarketApiConfig(s("stocksMarketApi.baseUrl")),
        authCode = AuthCodeConfig(l("authCode.ttlSeconds"), i("authCode.length")),
        defaults = DefaultsConfig(s("defaults.currency")),
    )
}
