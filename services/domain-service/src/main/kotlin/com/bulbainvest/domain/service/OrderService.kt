package com.bulbainvest.domain.service

import com.bulbainvest.domain.api.BadRequestException
import com.bulbainvest.domain.api.ConflictException
import com.bulbainvest.domain.api.NotFoundException
import com.bulbainvest.domain.db.CounterpartyType
import com.bulbainvest.domain.db.PortfolioPositions
import com.bulbainvest.domain.db.SellOrderStatus
import com.bulbainvest.domain.db.SellOrders
import com.bulbainvest.domain.db.TradeType
import com.bulbainvest.domain.db.Trades
import com.bulbainvest.domain.db.UserWallets
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class OrderService {

    fun createSellOrder(sellerId: UUID, ticker: String, quantity: BigDecimal, price: BigDecimal): ResultRow {
        if (quantity <= BigDecimal.ZERO) throw BadRequestException("quantity must be positive")
        if (price <= BigDecimal.ZERO) throw BadRequestException("price must be positive")

        val orderId = transaction {
            val pos = PortfolioPositions.selectAll()
                .where { (PortfolioPositions.userId eq sellerId) and (PortfolioPositions.ticker eq ticker) }
                .firstOrNull() ?: throw BadRequestException("No position for $ticker")
            val available = pos[PortfolioPositions.quantity].subtract(pos[PortfolioPositions.reservedQuantity])
            if (available < quantity) throw BadRequestException("Insufficient available quantity")

            PortfolioUpdater.reserveQuantity(sellerId, ticker, quantity)

            val id = UUID.randomUUID()
            SellOrders.insert {
                it[SellOrders.id] = id
                it[sellerUserId] = sellerId
                it[SellOrders.ticker] = ticker
                it[SellOrders.quantity] = quantity
                it[SellOrders.price] = price
                it[status] = SellOrderStatus.OPEN.name
                it[createdAt] = Instant.now()
            }
            id
        }
        return transaction { SellOrders.selectAll().where { SellOrders.id eq orderId }.first() }
    }

    fun orderBook(ticker: String): List<ResultRow> = transaction {
        SellOrders.selectAll()
            .where { (SellOrders.ticker eq ticker) and (SellOrders.status eq SellOrderStatus.OPEN.name) }
            .orderBy(SellOrders.price to SortOrder.ASC, SellOrders.createdAt to SortOrder.ASC)
            .toList()
    }

    fun listMyOrders(userId: UUID): List<ResultRow> = transaction {
        SellOrders.selectAll()
            .where { SellOrders.sellerUserId eq userId }
            .orderBy(SellOrders.createdAt to SortOrder.DESC)
            .toList()
    }

    fun cancel(userId: UUID, orderId: UUID) = transaction {
        val order = SellOrders.selectAll().where { SellOrders.id eq orderId }.firstOrNull()
            ?: throw NotFoundException("Order not found")
        if (order[SellOrders.sellerUserId] != userId) throw NotFoundException("Order not found")
        if (order[SellOrders.status] != SellOrderStatus.OPEN.name) {
            throw ConflictException("Order is not open")
        }
        PortfolioUpdater.releaseReservedQuantity(userId, order[SellOrders.ticker], order[SellOrders.quantity])
        SellOrders.update({ SellOrders.id eq orderId }) {
            it[status] = SellOrderStatus.CANCELLED.name
            it[cancelledAt] = Instant.now()
        }
    }

    /** Купить конкретный sell order целиком. */
    fun buySpecificOrder(buyerId: UUID, orderId: UUID, walletId: UUID?) = transaction {
        val order = SellOrders.selectAll().where { SellOrders.id eq orderId }.firstOrNull()
            ?: throw NotFoundException("Order not found")
        if (order[SellOrders.status] != SellOrderStatus.OPEN.name) {
            throw ConflictException("Order is not open")
        }
        if (order[SellOrders.sellerUserId] == buyerId) {
            throw BadRequestException("Cannot buy your own order")
        }
        executeMatch(
            buyerId = buyerId,
            buyerWalletId = walletId,
            order = order,
            quantity = order[SellOrders.quantity],
        )
    }

    /**
     * Купить из стакана: матчим OPEN ордера по возрастанию цены, пока не наберём quantity
     * или цена не превысит maxPrice.
     */
    fun buyFromBook(buyerId: UUID, ticker: String, quantity: BigDecimal, maxPrice: BigDecimal, walletId: UUID?) =
        transaction {
            if (quantity <= BigDecimal.ZERO) throw BadRequestException("quantity must be positive")
            var remaining = quantity
            val openOrders = SellOrders.selectAll()
                .where {
                    (SellOrders.ticker eq ticker) and
                            (SellOrders.status eq SellOrderStatus.OPEN.name) and
                            (SellOrders.price lessEq maxPrice) and
                            (SellOrders.sellerUserId neq buyerId)
                }
                .orderBy(SellOrders.price to SortOrder.ASC, SellOrders.createdAt to SortOrder.ASC)
                .toList()

            if (openOrders.isEmpty()) throw BadRequestException("No matching orders")

            for (order in openOrders) {
                if (remaining <= BigDecimal.ZERO) break
                val take = remaining.min(order[SellOrders.quantity])
                executeMatch(buyerId, walletId, order, take)
                remaining = remaining.subtract(take)
            }
            if (remaining > BigDecimal.ZERO) {
                throw BadRequestException("Order book has insufficient liquidity at maxPrice")
            }
        }

    /** Внутри уже открытой транзакции. */
    private fun executeMatch(buyerId: UUID, buyerWalletId: UUID?, order: ResultRow, quantity: BigDecimal) {
        val price = order[SellOrders.price]
        val total = price.multiply(quantity)
        val ticker = order[SellOrders.ticker]
        val sellerId = order[SellOrders.sellerUserId]

        val buyerWallet = resolveWallet(buyerId, buyerWalletId)
        val available = buyerWallet[UserWallets.amount].subtract(buyerWallet[UserWallets.reservedAmount])
        if (available < total) throw BadRequestException("Insufficient funds")

        val sellerWallet = UserWallets.selectAll()
            .where { (UserWallets.userId eq sellerId) and (UserWallets.isDefault eq true) }
            .first()

        WalletUpdater.debit(buyerWallet[UserWallets.id], total)
        WalletUpdater.credit(sellerWallet[UserWallets.id], total)

        PortfolioUpdater.consumeReserved(sellerId, ticker, quantity)
        PortfolioUpdater.addPosition(buyerId, ticker, quantity, price)

        val orderQty = order[SellOrders.quantity]
        if (quantity >= orderQty) {
            SellOrders.update({ SellOrders.id eq order[SellOrders.id] }) {
                it[status] = SellOrderStatus.MATCHED.name
                it[matchedAt] = Instant.now()
            }
        } else {
            // Частичное исполнение: уменьшаем quantity у ордера, оставляем OPEN.
            SellOrders.update({ SellOrders.id eq order[SellOrders.id] }) {
                it[SellOrders.quantity] = orderQty.subtract(quantity)
            }
        }

        Trades.insert {
            it[Trades.id] = UUID.randomUUID()
            it[Trades.buyerUserId] = buyerId
            it[Trades.sellerUserId] = sellerId
            it[Trades.buyerWalletId] = buyerWallet[UserWallets.id]
            it[Trades.sellerWalletId] = sellerWallet[UserWallets.id]
            it[Trades.counterpartyType] = CounterpartyType.USER.name
            it[Trades.tradeType] = TradeType.USER_TO_USER.name
            it[Trades.ticker] = ticker
            it[Trades.quantity] = quantity
            it[Trades.price] = price
            it[Trades.totalAmount] = total
            it[Trades.createdAt] = Instant.now()
        }
    }

    private fun resolveWallet(userId: UUID, walletId: UUID?): ResultRow {
        val row = if (walletId != null) {
            UserWallets.selectAll()
                .where { (UserWallets.id eq walletId) and (UserWallets.userId eq userId) }
                .firstOrNull()
        } else {
            UserWallets.selectAll()
                .where { (UserWallets.userId eq userId) and (UserWallets.isDefault eq true) }
                .firstOrNull()
        }
        return row ?: throw NotFoundException("Wallet not found")
    }
}
