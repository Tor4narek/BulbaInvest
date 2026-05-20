package com.bulbainvest.models

data class Trade(
    val id: String,
    val buyerUserId: String?,
    val sellerUserId: String?,
    val counterpartyType: String,  // "COMPANY" или "USER"
    val tradeType: String,          // "BUY_FROM_COMPANY", "SELL_TO_COMPANY", "USER_TO_USER"
    val ticker: String,
    val quantity: String,
    val price: String,
    val totalAmount: String,
    val createdAt: String
)