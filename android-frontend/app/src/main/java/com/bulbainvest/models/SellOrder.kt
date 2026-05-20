package com.bulbainvest.models

data class SellOrder(
    val id: String,
    val sellerUserId: String,
    val ticker: String,
    val quantity: String,
    val price: String,
    val status: String,  // "OPEN", "MATCHED", "CANCELLED", "EXPIRED"
    val createdAt: String
)