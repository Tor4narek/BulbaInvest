package com.bulbainvest.domain.service

import com.bulbainvest.domain.api.BadRequestException
import com.bulbainvest.domain.api.ConflictException
import com.bulbainvest.domain.api.NotFoundException
import com.bulbainvest.domain.config.AppConfig
import com.bulbainvest.domain.db.UserWallets
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class WalletService(private val cfg: AppConfig) {

    fun list(userId: UUID): List<ResultRow> = transaction {
        UserWallets.selectAll().where { UserWallets.userId eq userId }
            .orderBy(UserWallets.isDefault to SortOrder.DESC, UserWallets.createdAt to SortOrder.ASC)
            .toList()
    }

    fun create(userId: UUID, currency: String?): ResultRow = transaction {
        val c = (currency ?: cfg.defaults.currency).uppercase()
        val existing = UserWallets.selectAll()
            .where { (UserWallets.userId eq userId) and (UserWallets.currency eq c) }
            .firstOrNull()
        if (existing != null) throw ConflictException("Wallet for currency $c already exists")

        val now = Instant.now()
        val id = UUID.randomUUID()
        UserWallets.insert {
            it[UserWallets.id] = id
            it[UserWallets.userId] = userId
            it[UserWallets.currency] = c
            it[amount] = BigDecimal.ZERO
            it[reservedAmount] = BigDecimal.ZERO
            it[isDefault] = false
            it[createdAt] = now
            it[updatedAt] = now
        }
        UserWallets.selectAll().where { UserWallets.id eq id }.first()
    }

    fun makeDefault(userId: UUID, walletId: UUID) = transaction {
        val wallet = UserWallets.selectAll()
            .where { (UserWallets.id eq walletId) and (UserWallets.userId eq userId) }
            .firstOrNull() ?: throw NotFoundException("Wallet not found")

        UserWallets.update({ (UserWallets.userId eq userId) and (UserWallets.isDefault eq true) }) {
            it[isDefault] = false
            it[updatedAt] = Instant.now()
        }
        UserWallets.update({ UserWallets.id eq walletId }) {
            it[isDefault] = true
            it[updatedAt] = Instant.now()
        }
        wallet
    }

    fun delete(userId: UUID, walletId: UUID) = transaction {
        val wallet = UserWallets.selectAll()
            .where { (UserWallets.id eq walletId) and (UserWallets.userId eq userId) }
            .firstOrNull() ?: throw NotFoundException("Wallet not found")

        if (wallet[UserWallets.isDefault]) {
            throw BadRequestException("Cannot delete default wallet")
        }
        if (wallet[UserWallets.amount] > BigDecimal.ZERO || wallet[UserWallets.reservedAmount] > BigDecimal.ZERO) {
            throw BadRequestException("Cannot delete wallet with non-zero balance")
        }
        UserWallets.deleteWhere { UserWallets.id eq walletId }
    }

    fun resolveWalletForTrade(userId: UUID, walletId: UUID?): ResultRow = transaction {
        val row = if (walletId != null) {
            UserWallets.selectAll()
                .where { (UserWallets.id eq walletId) and (UserWallets.userId eq userId) }
                .firstOrNull()
        } else {
            UserWallets.selectAll()
                .where { (UserWallets.userId eq userId) and (UserWallets.isDefault eq true) }
                .firstOrNull()
        }
        row ?: throw NotFoundException("Wallet not found")
    }
}
