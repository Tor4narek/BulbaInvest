package com.bulbainvest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.bulbainvest.network.RetrofitClient
import com.bulbainvest.repository.Repository
import com.bulbainvest.ui.screens.*
import com.bulbainvest.ui.theme.BulbaInvestTheme
import com.bulbainvest.utils.TokenManager
import com.bulbainvest.websocket.WebSocketManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var tokenManager: TokenManager
    private lateinit var repository: Repository
    private lateinit var webSocketManager: WebSocketManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenManager = TokenManager(this)

        val apiService = RetrofitClient.create(tokenManager)
        repository = Repository(apiService)
        webSocketManager = WebSocketManager()

        setContent {
            BulbaInvestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BulbaInvestApp()
                }
            }
        }
    }

    @Composable
    fun BulbaInvestApp() {
        var isLoggedIn by remember { mutableStateOf(false) }
        var isLoading by remember { mutableStateOf(true) }
        var currentScreen by remember { mutableStateOf(Screen.Portfolio) }
        var selectedTicker by remember { mutableStateOf("AAPL") }
        var selectedPrice by remember { mutableStateOf(0.0) } // Добавить переменную для цены
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            val token = tokenManager.getToken()
            isLoggedIn = token != null
            isLoading = false
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return
        }

        when {
            !isLoggedIn -> {
                AuthScreen(
                    repository = repository,
                    onLoginSuccess = { token ->
                        coroutineScope.launch {
                            tokenManager.saveAuthToken(token)
                            isLoggedIn = true
                        }
                    }
                )
            }

            currentScreen == Screen.Market -> {
                MarketScreen(
                    repository = repository,
                    webSocketManager = webSocketManager,
                    onTickerClick = { ticker, price ->
                        selectedTicker = ticker
                        currentScreen = Screen.StockDetails
                    },
                    onBack = { currentScreen = Screen.Portfolio }
                )
            }

            currentScreen == Screen.StockDetails -> {
                StockDetailsScreen(
                    repository = repository,
                    webSocketManager = webSocketManager,
                    ticker = selectedTicker,
                    onBack = { currentScreen = Screen.Market },
                    onTradeClick = {
                        currentScreen = Screen.TradeCompany
                    }
                )
            }
            currentScreen == Screen.TradeCompany -> {
                TradeCompanyScreen(
                    repository = repository,
                    ticker = selectedTicker,
                    onBack = { currentScreen = Screen.StockDetails },
                    onTradeComplete = { _, _, _ ->
                        currentScreen = Screen.Portfolio
                    }
                )
            }
            currentScreen == Screen.TradeP2P -> {
                TradeP2PScreen(
                    repository = repository,
                    onBack = { currentScreen = Screen.Portfolio }
                )
            }
        }
    }
}

enum class Screen {
    Portfolio,
    Market,
    StockDetails,
    TradeCompany,
    TradeP2P
}