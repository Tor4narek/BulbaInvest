package com.bulbainvest.domain.service

import com.bulbainvest.domain.api.NotFoundException
import com.bulbainvest.domain.config.AppConfig
import com.bulbainvest.domain.db.UserWallets
import com.bulbainvest.domain.db.Users
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class UserService(private val cfg: AppConfig) {

    fun findOrCreateByEmail(email: String): UUID = transaction {
        val normalized = email.lowercase().trim()
        val existing = Users.selectAll().where { Users.email eq normalized }.firstOrNull()
        if (existing != null) return@transaction existing[Users.id]

        val now = Instant.now()
        val userId = UUID.randomUUID()
        Users.insert {
            it[id] = userId
            it[name] = normalized.substringBefore('@')
            it[Users.email] = normalized
            it[createdAt] = now
            it[updatedAt] = now
        }
        // Create default wallet
        UserWallets.insert {
            it[id] = UUID.randomUUID()
            it[UserWallets.userId] = userId
            it[currency] = cfg.defaults.currency
            it[amount] = BigDecimal.ZERO
            it[reservedAmount] = BigDecimal.ZERO
            it[isDefault] = true
            it[createdAt] = now
            it[updatedAt] = now
        }
        userId
    }

    fun getById(userId: UUID): ResultRow = transaction {
        Users.selectAll().where { Users.id eq userId }.firstOrNull()
            ?: throw NotFoundException("User not found")
    }

    fun updateName(userId: UUID, newName: String) = transaction {
        Users.update({ Users.id eq userId }) {
            it[name] = newName
            it[updatedAt] = Instant.now()
        }
    }
}
