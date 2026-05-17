package com.bulbainvest.models

data class UserWallet(
    val id: String,
    val currency: String,
    val amount: Double,
    val reservedAmount: Double,
    val availableAmount: Double,
    val isDefault: Boolean
)