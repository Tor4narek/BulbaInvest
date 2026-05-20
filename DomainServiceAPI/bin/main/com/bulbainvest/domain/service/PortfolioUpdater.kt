package com.bulbainvest.domain.service

import com.bulbainvest.domain.db.PortfolioPositions
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

/**
 * Helpers that mutate portfolio_positions. ВСЕ методы должны вызываться
 * внутри уже открытой transaction { ... }.
 */
object PortfolioUpdater {

    fun addPosition(userId: UUID, ticker: String, qty: BigDecimal, price: BigDecimal) {
        val existing = PortfolioPositions.selectAll()
            .where { (PortfolioPositions.userId eq userId) and (PortfolioPositions.ticker eq ticker) }
            .firstOrNull()
        val now = Instant.now()
        if (existing == null) {
            PortfolioPositions.insert {
                it[id] = UUID.randomUUID()
                it[PortfolioPositions.userId] = userId
                it[PortfolioPositions.ticker] = ticker
                it[quantity] = qty
                it[reservedQuantity] = BigDecimal.ZERO
                it[averageBuyPrice] = price
                it[createdAt] = now
                it[updatedAt] = now
            }
        } else {
            val oldQty = existing[PortfolioPositions.quantity]
            val oldAvg = existing[PortfolioPositions.averageBuyPrice]
            val newQty = oldQty.add(qty)
            val newAvg = if (newQty.compareTo(BigDecimal.ZERO) == 0) BigDecimal.ZERO
            else oldQty.multiply(oldAvg).add(qty.multiply(price))
                .divide(newQty, 4, RoundingMode.HALF_UP)
            PortfolioPositions.update({ PortfolioPositions.id eq existing[PortfolioPositions.id] }) {
                it[quantity] = newQty
                it[averageBuyPrice] = newAvg
                it[updatedAt] = now
            }
        }
    }

    /** Уменьшает quantity. Если позиция исчерпана — оставляем строку с 0 (история average). */
    fun removeFromPosition(userId: UUID, ticker: String, qty: BigDecimal) {
        val row = PortfolioPositions.selectAll()
            .where { (PortfolioPositions.userId eq userId) and (PortfolioPositions.ticker eq ticker) }
            .firstOrNull() ?: error("Position $ticker not found for user $userId")
        val newQty = row[PortfolioPositions.quantity].subtract(qty)
        PortfolioPositions.update({ PortfolioPositions.id eq row[PortfolioPositions.id] }) {
            it[quantity] = newQty
            it[updatedAt] = Instant.now()
        }
    }

    fun reserveQuantity(userId: UUID, ticker: String, qty: BigDecimal) {
        val row = PortfolioPositions.selectAll()
            .where { (PortfolioPositions.userId eq userId) and (PortfolioPositions.ticker eq ticker) }
            .firstOrNull() ?: error("Position $ticker not found for user $userId")
        val available = row[PortfolioPositions.quantity].subtract(row[PortfolioPositions.reservedQuantity])
        require(available >= qty) { "Insufficient available quantity" }
        PortfolioPositions.update({ PortfolioPositions.id eq row[PortfolioPositions.id] }) {
            it[reservedQuantity] = row[PortfolioPositions.reservedQuantity].add(qty)
            it[updatedAt] = Instant.now()
        }
    }

    fun releaseReservedQuantity(userId: UUID, ticker: String, qty: BigDecimal) {
        val row = PortfolioPositions.selectAll()
            .where { (PortfolioPositions.userId eq userId) and (PortfolioPositions.ticker eq ticker) }
            .firstOrNull() ?: error("Position $ticker not found for user $userId")
        PortfolioPositions.update({ PortfolioPositions.id eq row[PortfolioPositions.id] }) {
            it[reservedQuantity] = row[PortfolioPositions.reservedQuantity].subtract(qty).max(BigDecimal.ZERO)
            it[updatedAt] = Instant.now()
        }
    }

    /** На исполнении sell-order: списать и резерв, и сам quantity. */
    fun consumeReserved(userId: UUID, ticker: String, qty: BigDecimal) {
        val row = PortfolioPositions.selectAll()
            .where { (PortfolioPositions.userId eq userId) and (PortfolioPositions.ticker eq ticker) }
            .firstOrNull() ?: error("Position $ticker not found for user $userId")
        PortfolioPositions.update({ PortfolioPositions.id eq row[PortfolioPositions.id] }) {
            it[quantity] = row[PortfolioPositions.quantity].subtract(qty)
            it[reservedQuantity] = row[PortfolioPositions.reservedQuantity].subtract(qty).max(BigDecimal.ZERO)
            it[updatedAt] = Instant.now()
        }
    }
}
