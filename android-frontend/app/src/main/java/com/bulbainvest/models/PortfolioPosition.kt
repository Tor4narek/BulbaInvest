package com.bulbainvest.models

data class PortfolioPosition(
    val ticker: String,
    val quantity: Int,
    val reservedQuantity: Int,
    val availableQuantity: Int,
    val averageBuyPrice: Double
)