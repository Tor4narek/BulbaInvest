package com.bulbainvest.domain.api

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val error: String, val message: String? = null)

@Serializable
data class AuthCodeRequest(val email: String)

@Serializable
data class AuthCodeConfirm(val email: String, val code: String)

@Serializable
data class AuthTokenResponse(val token: String)

@Serializable
data class UserDto(val id: String, val name: String, val email: String)

@Serializable
data class UpdateUserRequest(val name: String? = null)

@Serializable
data class WalletDto(
    val id: String,
    val currency: String,
    val amount: String,
    val reservedAmount: String,
    val availableAmount: String,
    val isDefault: Boolean,
)

@Serializable
data class DepositWalletRequest(val amount: String)

@Serializable
data class PortfolioPositionDto(
    val ticker: String,
    val quantity: String,
    val reservedQuantity: String,
    val availableQuantity: String,
    val averageBuyPrice: String,
)

@Serializable
data class CompanyTradeRequest(val ticker: String, val quantity: String, val walletId: String? = null)

@Serializable
data class TradeDto(
    val id: String,
    val buyerUserId: String?,
    val sellerUserId: String?,
    val counterpartyType: String,
    val tradeType: String,
    val ticker: String,
    val quantity: String,
    val price: String,
    val totalAmount: String,
    val createdAt: String,
)

@Serializable
data class CreateSellOrderRequest(val ticker: String, val quantity: String, val price: String)

@Serializable
data class BuyFromOrderBookRequest(
    val ticker: String,
    val quantity: String,
    val maxPrice: String,
    val walletId: String? = null,
)

@Serializable
data class SellOrderDto(
    val id: String,
    val sellerUserId: String,
    val ticker: String,
    val quantity: String,
    val price: String,
    val status: String,
    val createdAt: String,
)

@Serializable
data class OrderBookDto(val ticker: String, val orders: List<SellOrderDto>)
