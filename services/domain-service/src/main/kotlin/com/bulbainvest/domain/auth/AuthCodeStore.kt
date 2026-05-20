package com.bulbainvest.domain.auth

import redis.clients.jedis.JedisPool
import kotlin.random.Random

class AuthCodeStore(
    private val pool: JedisPool,
    private val ttlSeconds: Long,
    private val codeLength: Int,
) {
    private fun key(email: String) = "auth:code:${email.lowercase()}"

    fun generateAndStore(email: String): String {
        val code = (1..codeLength).joinToString("") { Random.nextInt(0, 10).toString() }
        pool.resource.use { it.setex(key(email), ttlSeconds, code) }
        return code
    }

    fun verifyAndConsume(email: String, code: String): Boolean {
        pool.resource.use { jedis ->
            val stored = jedis.get(key(email)) ?: return false
            if (stored != code) return false
            jedis.del(key(email))
            return true
        }
    }
}
