package com.bulbainvest.domain.plugins

import com.bulbainvest.domain.auth.JwtService
import com.bulbainvest.domain.config.JwtConfig
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import java.util.UUID

const val USER_AUTH = "user-auth"

fun Application.configureSecurity(jwtCfg: JwtConfig, jwtService: JwtService) {
    install(Authentication) {
        jwt(USER_AUTH) {
            realm = jwtCfg.realm
            verifier(jwtService.verifier)
            validate { credential ->
                val sub = credential.payload.subject
                if (!sub.isNullOrBlank() && runCatching { UUID.fromString(sub) }.isSuccess) {
                    JWTPrincipal(credential.payload)
                } else null
            }
        }
    }
}
