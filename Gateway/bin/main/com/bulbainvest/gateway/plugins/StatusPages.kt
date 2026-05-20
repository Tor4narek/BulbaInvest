package com.bulbainvest.gateway.plugins

import com.bulbainvest.gateway.domain.BadGatewayException
import com.bulbainvest.gateway.domain.GatewayTimeoutException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable
import java.util.concurrent.CancellationException
import org.slf4j.LoggerFactory

@Serializable
data class ErrorResponse(val error: String, val message: String? = null)

fun Application.configureStatusPages() {
    val log = LoggerFactory.getLogger("StatusPages")
    install(StatusPages) {
        exception<GatewayTimeoutException> { call, cause ->
            call.respond(HttpStatusCode.GatewayTimeout, ErrorResponse("gateway_timeout", cause.message))
        }
        exception<BadGatewayException> { call, cause ->
            call.respond(HttpStatusCode.BadGateway, ErrorResponse("bad_gateway", cause.message))
        }
        exception<CancellationException> { _, _ ->
            // Normal for long-lived websocket sessions during shutdown/disconnect.
        }
        exception<Throwable> { call, cause ->
            log.error("Unhandled exception", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal_error", cause.message))
        }
    }
}
