package com.stocksim.graphservice

import com.stocksim.graphservice.config.AppConfig
import com.stocksim.graphservice.config.configureKoin
import com.stocksim.graphservice.consumer.PriceConsumer
import com.stocksim.graphservice.routes.configureRouting
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.koin.ktor.ext.inject

private val logger = KotlinLogging.logger {}

fun main() {
    embeddedServer(Netty, port = AppConfig.server.port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureKoin()
    configureSerialization()
    configureStatusPages()
    configureCallLogging()
    configureRouting()
    startPriceConsumer()
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = false
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
}

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (cause.message ?: "Bad request")))
        }
        exception<Throwable> { call, cause ->
            logger.error(cause) { "Unhandled exception" }
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
        }
    }
}

fun Application.configureCallLogging() {
    install(CallLogging)
}

fun Application.startPriceConsumer() {
    val consumer by inject<PriceConsumer>()
    environment.monitor.subscribe(ApplicationStarted) {
        consumer.start()
        logger.info { "PriceConsumer started" }
    }
    environment.monitor.subscribe(ApplicationStopped) {
        consumer.stop()
        logger.info { "PriceConsumer stopped" }
    }
}
