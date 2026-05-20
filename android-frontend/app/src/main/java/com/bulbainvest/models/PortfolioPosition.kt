package com.bulbainvest.models

data class PortfolioPosition(
    val ticker: String,
    val quantity: String,
    val reservedQuantity: String,
    val availableQuantity: String,
    val averageBuyPrice: String
)