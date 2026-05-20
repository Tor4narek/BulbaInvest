package com.bulbainvest.domain.config

import com.bulbainvest.domain.auth.AuthCodeStore
import com.bulbainvest.domain.auth.JwtService
import com.bulbainvest.domain.external.MailService
import com.bulbainvest.domain.external.QuotesBrokerClient
import com.bulbainvest.domain.external.StocksMarketApiClient
import com.bulbainvest.domain.service.OrderService
import com.bulbainvest.domain.service.PortfolioService
import com.bulbainvest.domain.service.TradeService
import com.bulbainvest.domain.service.UserService
import com.bulbainvest.domain.service.WalletService
import org.koin.dsl.module
import redis.clients.jedis.JedisPool

fun appModule(cfg: AppConfig) = module {
    single { cfg }

    // Redis: основной (для auth-кодов)
    single(qualifier = org.koin.core.qualifier.named("authRedis")) {
        JedisPool(cfg.redis.host, cfg.redis.port)
    }
    // Redis: брокер котировок
    single(qualifier = org.koin.core.qualifier.named("brokerRedis")) {
        JedisPool(cfg.brokerRedis.host, cfg.brokerRedis.port)
    }

    single { JwtService(cfg.jwt) }
    single {
        AuthCodeStore(
            pool = get(org.koin.core.qualifier.named("authRedis")),
            ttlSeconds = cfg.authCode.ttlSeconds,
            codeLength = cfg.authCode.length,
        )
    }
    single { MailService(cfg.mail) }
    single { StocksMarketApiClient(cfg.stocksMarketApi) }
    single {
        QuotesBrokerClient(
            pool = get(org.koin.core.qualifier.named("brokerRedis")),
            cfg = cfg.brokerRedis,
        )
    }

    single { UserService(cfg) }
    single { WalletService(cfg) }
    single { PortfolioService() }
    single { TradeService(get(), get()) }
    single { OrderService() }
}
