package com.bulbainvest.domain.service

import com.bulbainvest.domain.api.BadRequestException
import com.bulbainvest.domain.api.NotFoundException
import com.bulbainvest.domain.db.UserWallets
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class WalletService {

    fun list(userId: UUID): List<ResultRow> = transaction {
        UserWallets.selectAll().where { UserWallets.userId eq userId }
            .orderBy(UserWallets.isDefault to SortOrder.DESC, UserWallets.createdAt to SortOrder.ASC)
            .toList()
    }

    fun deposit(userId: UUID, amount: BigDecimal): ResultRow = transaction {
        if (amount <= BigDecimal.ZERO) {
            throw BadRequestException("amount must be positive")
        }

        val wallet = defaultWallet(userId)
        val nextAmount = wallet[UserWallets.amount].add(amount)

        UserWallets.update({ UserWallets.id eq wallet[UserWallets.id] }) {
            it[UserWallets.amount] = nextAmount
            it[updatedAt] = Instant.now()
        }

        UserWallets.selectAll().where { UserWallets.id eq wallet[UserWallets.id] }.first()
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

    private fun defaultWallet(userId: UUID): ResultRow =
        UserWallets.selectAll()
            .where { (UserWallets.userId eq userId) and (UserWallets.isDefault eq true) }
            .firstOrNull() ?: throw NotFoundException("Wallet not found")
}
