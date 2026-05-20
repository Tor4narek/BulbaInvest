// app/src/main/java/com/bulbainvest/repository/Repository.kt
package com.bulbainvest.repository

import com.bulbainvest.models.*
import com.bulbainvest.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Repository(private val apiService: ApiService) {

    // Auth
    suspend fun requestCode(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            apiService.requestCode(CodeRequest(email))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun confirmCode(email: String, code: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.confirmCode(ConfirmRequest(email, code))
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // User
    suspend fun getCurrentUser(): Result<User> = withContext(Dispatchers.IO) {
        try {
            Result.success(apiService.getMe())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Wallets
    suspend fun getWallets(): Result<List<UserWallet>> = withContext(Dispatchers.IO) {
        try {
            Result.success(apiService.getWallets())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Portfolio
    suspend fun getPortfolio(): Result<List<PortfolioPosition>> = withContext(Dispatchers.IO) {
        try {
            Result.success(apiService.getPortfolio())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Company trades
    suspend fun buyFromCompany(ticker: String, quantity: String, walletId: String? = null): Result<Trade> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.buyFromCompany(CompanyTradeRequest(ticker, quantity, walletId))
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sellToCompany(ticker: String, quantity: String, walletId: String? = null): Result<Trade> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.sellToCompany(CompanyTradeRequest(ticker, quantity, walletId))
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Order book (P2P)
    suspend fun getOrderBook(ticker: String): Result<OrderBookResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getOrderBook(ticker)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Market Data - НОВЫЕ МЕТОДЫ
    suspend fun getTickers(): Result<TickerListResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getTickers()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getQuoteLatest(ticker: String): Result<Quote> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getQuoteLatest(ticker)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getQuoteStats(ticker: String, granularity: String = "day"): Result<QuoteStats> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getQuoteStats(ticker, granularity)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMarketOrderBook(ticker: String): Result<OrderBook> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getMarketOrderBook(ticker)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Sell orders
    suspend fun createSellOrder(ticker: String, quantity: String, price: String): Result<SellOrder> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.createSellOrder(CreateSellOrderRequest(ticker, quantity, price))
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun buySpecificOrder(orderId: String, quantity: String, maxPrice: String, walletId: String? = null): Result<Trade> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.buySpecificOrder(orderId, BuySpecificOrderRequest(quantity, maxPrice, walletId))
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelOrder(orderId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            apiService.cancelOrder(orderId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMyOrders(): Result<List<SellOrder>> = withContext(Dispatchers.IO) {
        try {
            Result.success(apiService.getMyOrders())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Trades history
    suspend fun getTrades(ticker: String? = null, tradeType: String? = null): Result<List<Trade>> = withContext(Dispatchers.IO) {
        try {
            Result.success(apiService.getTrades(ticker, tradeType, null, null))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}