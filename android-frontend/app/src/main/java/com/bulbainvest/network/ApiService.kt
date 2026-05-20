package com.bulbainvest.network

import com.bulbainvest.models.*
import retrofit2.http.*

interface ApiService {

    // Auth
    @POST("api/auth/code/request")
    suspend fun requestCode(@Body request: CodeRequest)

    @POST("api/auth/code/confirm")
    suspend fun confirmCode(@Body request: ConfirmRequest): AuthResponse

    // User
    @GET("api/users/me")
    suspend fun getMe(): User

    @PATCH("api/users/me")
    suspend fun updateMe(@Body request: Map<String, String>): User

    // Wallets
    @GET("api/wallets/me")
    suspend fun getWallets(): List<UserWallet>

    @POST("api/wallets")
    suspend fun createWallet(@Body request: Map<String, String>): UserWallet

    @POST("api/wallets/{walletId}/make-default")
    suspend fun makeDefault(@Path("walletId") walletId: String): UserWallet

    @DELETE("api/wallets/{walletId}")
    suspend fun deleteWallet(@Path("walletId") walletId: String)

    // Portfolio
    @GET("api/portfolio")
    suspend fun getPortfolio(): List<PortfolioPosition>

    @GET("api/portfolio/{ticker}")
    suspend fun getPortfolioPosition(@Path("ticker") ticker: String): PortfolioPosition

    // Company trades
    @POST("api/trades/company/buy")
    suspend fun buyFromCompany(@Body request: CompanyTradeRequest): Trade

    @POST("api/trades/company/sell")
    suspend fun sellToCompany(@Body request: CompanyTradeRequest): Trade

    // Order book
    @GET("api/order-book/{ticker}")
    suspend fun getOrderBook(@Path("ticker") ticker: String): OrderBookResponse

    // Orders
    @POST("api/orders/sell")
    suspend fun createSellOrder(@Body request: CreateSellOrderRequest): SellOrder

    @POST("api/orders/buy")
    suspend fun buyFromOrderBook(@Body request: BuyFromOrderBookRequest): Trade

    @POST("api/orders/sell/{orderId}/buy")
    suspend fun buySpecificOrder(
        @Path("orderId") orderId: String,
        @Body request: BuySpecificOrderRequest
    ): Trade

    @POST("api/orders/sell/{orderId}/cancel")
    suspend fun cancelOrder(@Path("orderId") orderId: String)

    @GET("api/orders/my")
    suspend fun getMyOrders(): List<SellOrder>

    // Trades history
    @GET("api/trades")
    suspend fun getTrades(
        @Query("ticker") ticker: String? = null,
        @Query("tradeType") tradeType: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): List<Trade>
}

// Request models
data class CompanyTradeRequest(
    val ticker: String,
    val quantity: String,
    val walletId: String? = null
)

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

data class OrderBookResponse(
    val ticker: String,
    val orders: List<SellOrder>
)