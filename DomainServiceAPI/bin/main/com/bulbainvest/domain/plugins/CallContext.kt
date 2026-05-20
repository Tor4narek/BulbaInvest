package com.bulbainvest.domain.plugins

import com.bulbainvest.domain.api.UnauthorizedException
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import java.util.UUID

fun ApplicationCall.userId(): UUID {
    val principal = principal<JWTPrincipal>() ?: throw UnauthorizedException()
    val sub = principal.subject ?: throw UnauthorizedException()
    return UUID.fromString(sub)
}
