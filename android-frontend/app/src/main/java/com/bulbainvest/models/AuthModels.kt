// app/src/main/java/com/bulbainvest/models/AuthModels.kt
package com.bulbainvest.models

data class CodeRequest(
    val email: String
)

data class DepositRequest(
    val amount: String
)
data class ConfirmRequest(
    val email: String,
    val code: String
)

data class AuthResponse(
    val token: String
)

// Модели для торговли с компанией
data class CompanyTradeRequest(
    val ticker: String,
    val quantity: String,
    val walletId: String? = null
)

// Модели для P2P ордеров
data class CreateSellOrderRequest(
    val ticker: String,
    val quantity: String,
    val price: String
)

data class BuyFromOrderBookRequest(
    val ticker: String,
    val quantity: String,
    val maxPrice: String,
    val walletId: String? = null
)

data class BuySpecificOrderRequest(
    val quantity: String,
    val maxPrice: String,
    val walletId: String? = null
)

// Ответ для P2P стакана
data class OrderBookResponse(
    val ticker: String,
    val orders: List<SellOrder>
)