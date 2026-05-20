package com.bulbainvest.domain.api

import com.bulbainvest.domain.db.PortfolioPositions
import com.bulbainvest.domain.db.SellOrders
import com.bulbainvest.domain.db.Trades
import com.bulbainvest.domain.db.UserWallets
import com.bulbainvest.domain.db.Users
import org.jetbrains.exposed.sql.ResultRow

fun ResultRow.toUserDto() = UserDto(
    id = this[Users.id].toString(),
    name = this[Users.name],
    email = this[Users.email],
)

fun ResultRow.toWalletDto(): WalletDto {
    val amount = this[UserWallets.amount]
    val reserved = this[UserWallets.reservedAmount]
    return WalletDto(
        id = this[UserWallets.id].toString(),
        currency = this[UserWallets.currency],
        amount = amount.toPlainString(),
        reservedAmount = reserved.toPlainString(),
        availableAmount = amount.subtract(reserved).toPlainString(),
        isDefault = this[UserWallets.isDefault],
    )
}

fun ResultRow.toPositionDto(): PortfolioPositionDto {
    val qty = this[PortfolioPositions.quantity]
    val reserved = this[PortfolioPositions.reservedQuantity]
    return PortfolioPositionDto(
        ticker = this[PortfolioPositions.ticker],
        quantity = qty.toPlainString(),
        reservedQuantity = reserved.toPlainString(),
        availableQuantity = qty.subtract(reserved).toPlainString(),
        averageBuyPrice = this[PortfolioPositions.averageBuyPrice].toPlainString(),
    )
}

fun ResultRow.toSellOrderDto() = SellOrderDto(
    id = this[SellOrders.id].toString(),
    sellerUserId = this[SellOrders.sellerUserId].toString(),
    ticker = this[SellOrders.ticker],
    quantity = this[SellOrders.quantity].toPlainString(),
    price = this[SellOrders.price].toPlainString(),
    status = this[SellOrders.status],
    createdAt = this[SellOrders.createdAt].toString(),
)

fun ResultRow.toTradeDto() = TradeDto(
    id = this[Trades.id].toString(),
    buyerUserId = this[Trades.buyerUserId]?.toString(),
    sellerUserId = this[Trades.sellerUserId]?.toString(),
    counterpartyType = this[Trades.counterpartyType],
    tradeType = this[Trades.tradeType],
    ticker = this[Trades.ticker],
    quantity = this[Trades.quantity].toPlainString(),
    price = this[Trades.price].toPlainString(),
    totalAmount = this[Trades.totalAmount].toPlainString(),
    createdAt = this[Trades.createdAt].toString(),
)
