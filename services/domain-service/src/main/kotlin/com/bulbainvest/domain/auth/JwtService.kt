package com.bulbainvest.domain.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.bulbainvest.domain.config.JwtConfig
import java.util.Date
import java.util.UUID

class JwtService(private val cfg: JwtConfig) {
    private val algorithm: Algorithm = Algorithm.HMAC256(cfg.secret)

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(cfg.issuer)
        .withAudience(cfg.audience)
        .build()

    fun issue(userId: UUID): String {
        val now = System.currentTimeMillis()
        return JWT.create()
            .withIssuer(cfg.issuer)
            .withAudience(cfg.audience)
            .withSubject(userId.toString())
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + cfg.ttlSeconds * 1000))
            .sign(algorithm)
    }
}
