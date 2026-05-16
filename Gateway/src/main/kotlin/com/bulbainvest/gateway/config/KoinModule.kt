package com.bulbainvest.gateway.config

import com.bulbainvest.gateway.application.ProxyUseCase
import com.bulbainvest.gateway.application.QuoteSubscriptionUseCase
import com.bulbainvest.gateway.domain.DownstreamProxyClient
import com.bulbainvest.gateway.domain.DownstreamServiceRegistry
import com.bulbainvest.gateway.domain.MarketQuotesStream
import com.bulbainvest.gateway.infrastructure.DefaultDownstreamServiceRegistry
import com.bulbainvest.gateway.infrastructure.KtorDownstreamProxyClient
import com.bulbainvest.gateway.infrastructure.RedisMarketQuotesStream
import org.koin.dsl.module

fun appModule(cfg: AppConfig) = module {
    single { cfg }
    single<DownstreamServiceRegistry> { DefaultDownstreamServiceRegistry(cfg) }
    single<DownstreamProxyClient> { KtorDownstreamProxyClient(cfg.httpClient) }
    single<MarketQuotesStream> { RedisMarketQuotesStream(cfg.brokerRedis) }
    single { ProxyUseCase(get(), get()) }
    single { QuoteSubscriptionUseCase(get()) }
}
