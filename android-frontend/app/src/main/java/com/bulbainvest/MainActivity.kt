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
import com.bulbainvest.ui.screens.AuthScreen
import com.bulbainvest.ui.screens.PortfolioScreen
import com.bulbainvest.ui.screens.TradeCompanyScreen
import com.bulbainvest.ui.screens.TradeP2PScreen
import com.bulbainvest.ui.theme.BulbaInvestTheme
import com.bulbainvest.utils.TokenManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenManager = TokenManager(this)

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
        val coroutineScope = rememberCoroutineScope()

        // Проверяем сохранённый токен при запуске
        LaunchedEffect(Unit) {
            val token = tokenManager.getToken()
            isLoggedIn = token != null
            isLoading = false
        }

        if (isLoading) {
            // Экран загрузки
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
                    onLoginSuccess = { token, userId ->
                        coroutineScope.launch {
                            tokenManager.saveAuthToken(token, userId)
                            isLoggedIn = true
                        }
                    }
                )
            }
            currentScreen == Screen.Portfolio -> {
                PortfolioScreen(
                    onLogout = {
                        coroutineScope.launch {
                            tokenManager.clearAuthToken()
                            isLoggedIn = false
                        }
                    },
                    onTradeClick = { ticker ->
                        selectedTicker = ticker
                        currentScreen = Screen.TradeCompany
                    },
                    onP2PClick = {
                        currentScreen = Screen.TradeP2P
                    }
                )
            }
            currentScreen == Screen.TradeCompany -> {
                TradeCompanyScreen(
                    ticker = selectedTicker,
                    onBack = { currentScreen = Screen.Portfolio },
                    onTradeComplete = { type, quantity, total ->
                        // TODO: обновить портфель
                        currentScreen = Screen.Portfolio
                    }
                )
            }
            currentScreen == Screen.TradeP2P -> {
                TradeP2PScreen(
                    onBack = { currentScreen = Screen.Portfolio },
                    onOrderExecuted = {
                        println("Order executed in P2P")
                    }
                )
            }
        }
    }
}

enum class Screen {
    Portfolio,
    TradeCompany,
    TradeP2P
}