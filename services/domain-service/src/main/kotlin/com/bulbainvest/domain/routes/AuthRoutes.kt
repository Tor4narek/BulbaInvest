package com.bulbainvest.domain.routes

import com.bulbainvest.domain.api.AuthCodeConfirm
import com.bulbainvest.domain.api.AuthCodeRequest
import com.bulbainvest.domain.api.AuthTokenResponse
import com.bulbainvest.domain.api.BadRequestException
import com.bulbainvest.domain.auth.AuthCodeStore
import com.bulbainvest.domain.auth.JwtService
import com.bulbainvest.domain.external.MailService
import com.bulbainvest.domain.service.UserService
import org.slf4j.LoggerFactory
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

private val authLog = LoggerFactory.getLogger("AuthRoutes")

fun Route.authRoutes(
    codeStore: AuthCodeStore,
    mail: MailService,
    users: UserService,
    jwt: JwtService,
) {
    route("/api/auth/code") {
        post("/request") {
            val body = call.receive<AuthCodeRequest>()
            val email = body.email.trim().lowercase()
            if (!email.contains('@')) throw BadRequestException("invalid email")
            val code = codeStore.generateAndStore(email)
            runCatching { mail.sendAuthCode(email, code) }
                .onFailure { authLog.warn("Mail send failed: ${it.message}") }
            call.respond(HttpStatusCode.OK, mapOf("status" to "sent"))
        }

        post("/confirm") {
            val body = call.receive<AuthCodeConfirm>()
            val email = body.email.trim().lowercase()
            if (!codeStore.verifyAndConsume(email, body.code.trim())) {
                throw BadRequestException("invalid or expired code")
            }
            val userId = users.findOrCreateByEmail(email)
            val token = jwt.issue(userId)
            call.respond(AuthTokenResponse(token))
        }
    }
}
