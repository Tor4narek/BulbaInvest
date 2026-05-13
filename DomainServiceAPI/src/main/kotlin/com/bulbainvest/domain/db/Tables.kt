package com.bulbainvest.domain.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.math.BigDecimal

object Users : Table("users") {
    val id = uuid("id")
    val name = varchar("name", 255)
    val email = varchar("email", 320).uniqueIndex()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object UserWallets : Table("user_wallets") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val currency = varchar("currency", 8)
    val amount = decimal("amount", 20, 4).default(BigDecimal.ZERO)
    val reservedAmount = decimal("reserved_amount", 20, 4).default(BigDecimal.ZERO)
    val isDefault = bool("is_default").default(false)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object PortfolioPositions : Table("portfolio_positions") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val ticker = varchar("ticker", 32)
    val quantity = decimal("quantity", 20, 4).default(BigDecimal.ZERO)
    val reservedQuantity = decimal("reserved_quantity", 20, 4).default(BigDecimal.ZERO)
    val averageBuyPrice = decimal("average_buy_price", 20, 4).default(BigDecimal.ZERO)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object SellOrders : Table("sell_orders") {
    val id = uuid("id")
    val sellerUserId = uuid("seller_user_id")
    val ticker = varchar("ticker", 32)
    val quantity = decimal("quantity", 20, 4)
    val price = decimal("price", 20, 4)
    val status = varchar("status", 16)
    val createdAt = timestamp("created_at")
    val matchedAt = timestamp("matched_at").nullable()
    val cancelledAt = timestamp("cancelled_at").nullable()
    val expiredAt = timestamp("expired_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object Trades : Table("trades") {
    val id = uuid("id")
    val buyerUserId = uuid("buyer_user_id").nullable()
    val sellerUserId = uuid("seller_user_id").nullable()
    val buyerWalletId = uuid("buyer_wallet_id").nullable()
    val sellerWalletId = uuid("seller_wallet_id").nullable()
    val counterpartyType = varchar("counterparty_type", 16)
    val tradeType = varchar("trade_type", 32)
    val ticker = varchar("ticker", 32)
    val quantity = decimal("quantity", 20, 4)
    val price = decimal("price", 20, 4)
    val totalAmount = decimal("total_amount", 20, 4)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

enum class SellOrderStatus { OPEN, MATCHED, CANCELLED, EXPIRED }
enum class CounterpartyType { COMPANY, USER }
enum class TradeType { BUY_FROM_COMPANY, SELL_TO_COMPANY, USER_TO_USER }
