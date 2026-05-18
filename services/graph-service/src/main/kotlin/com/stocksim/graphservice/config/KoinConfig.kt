package com.stocksim.graphservice.config

import com.stocksim.graphservice.consumer.PriceConsumer
import com.stocksim.graphservice.repository.QuoteRepository
import com.stocksim.graphservice.service.GraphService
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.ktor.server.application.*
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import java.sql.DriverManager

fun Application.configureKoin() {
    install(Koin) {
        slf4jLogger()
        modules(appModule)
    }
}

val appModule = module {

    single {
        val uri = RedisURI.Builder
            .redis(AppConfig.redis.host, AppConfig.redis.port)
            .build()
        RedisClient.create(uri)
    }

    single {
        val cfg = AppConfig.clickhouse
        DriverManager.getConnection(cfg.url, cfg.user, cfg.password)
    }

    single { QuoteRepository(get()) }
    single { GraphService(get()) }
    single { PriceConsumer(get(), get()) }
}
