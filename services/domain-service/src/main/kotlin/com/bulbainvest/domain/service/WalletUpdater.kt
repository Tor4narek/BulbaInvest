package com.bulbainvest.domain.service

import com.bulbainvest.domain.db.UserWallets
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

object WalletUpdater {
    fun credit(walletId: UUID, amount: BigDecimal) {
        val row = UserWallets.selectAll().where { UserWallets.id eq walletId }.first()
        UserWallets.update({ UserWallets.id eq walletId }) {
            it[UserWallets.amount] = row[UserWallets.amount].add(amount)
            it[updatedAt] = Instant.now()
        }
    }

    fun debit(walletId: UUID, amount: BigDecimal) {
        val row = UserWallets.selectAll().where { UserWallets.id eq walletId }.first()
        val available = row[UserWallets.amount].subtract(row[UserWallets.reservedAmount])
        require(available >= amount) { "Insufficient funds" }
        UserWallets.update({ UserWallets.id eq walletId }) {
            it[UserWallets.amount] = row[UserWallets.amount].subtract(amount)
            it[updatedAt] = Instant.now()
        }
    }
}
