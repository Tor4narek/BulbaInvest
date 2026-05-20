// app/src/main/java/com/bulbainvest/models/UserWallet.kt
package com.bulbainvest.models

data class UserWallet(
    val id: String,
    val currency: String,
    val amount: String,
    val reservedAmount: String,
    val availableAmount: String,
    val isDefault: Boolean
)