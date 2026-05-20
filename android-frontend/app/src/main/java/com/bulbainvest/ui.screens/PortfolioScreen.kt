package com.bulbainvest.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bulbainvest.models.PortfolioPosition
import com.bulbainvest.models.UserWallet
import com.bulbainvest.repository.Repository
import kotlinx.coroutines.launch

@Composable
fun PortfolioScreen(
    repository: Repository,
    onLogout: () -> Unit,
    onTradeClick: (String) -> Unit,
    onP2PClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var wallets by remember { mutableStateOf<List<UserWallet>>(emptyList()) }
    var portfolio by remember { mutableStateOf<List<PortfolioPosition>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Загрузка данных
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val walletsResult = repository.getWallets()
            val portfolioResult = repository.getPortfolio()

            wallets = walletsResult.getOrNull() ?: emptyList()
            portfolio = portfolioResult.getOrNull() ?: emptyList()
            errorMessage = null
        } catch (e: Exception) {
            errorMessage = e.message
        }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Портфель", fontSize = MaterialTheme.typography.headlineMedium.fontSize)
            Row {
                Button(onClick = onP2PClick) {
                    Text("P2P")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onLogout) {
                    Text("Выйти")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            errorMessage != null -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Ошибка: $errorMessage",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            else -> {
                // Баланс
                val defaultWallet = wallets.firstOrNull()
                if (defaultWallet != null) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Баланс", fontSize = MaterialTheme.typography.titleMedium.fontSize)
                            Text(
                                text = "${defaultWallet.amount} ${defaultWallet.currency}",
                                fontSize = MaterialTheme.typography.headlineLarge.fontSize
                            )
                            Text("Доступно: ${defaultWallet.availableAmount} ${defaultWallet.currency}")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Мои акции", fontSize = MaterialTheme.typography.titleLarge.fontSize)
                    Button(onClick = { onTradeClick("AAPL") }) {
                        Text("Купить акции")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (portfolio.isEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text("У вас пока нет акций", modifier = Modifier.padding(16.dp))
                    }
                } else {
                    LazyColumn {
                        items(portfolio) { position ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(position.ticker, fontSize = MaterialTheme.typography.titleMedium.fontSize)
                                        Text("${position.quantity} шт.")
                                        Text("ср. цена: ${position.averageBuyPrice} BYN")
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        val total = position.quantity.toDoubleOrNull()?.times(position.averageBuyPrice.toDoubleOrNull() ?: 0.0) ?: 0.0
                                        Text(
                                            text = "${total} BYN",
                                            fontSize = MaterialTheme.typography.titleMedium.fontSize
                                        )
                                        Button(
                                            onClick = { onTradeClick(position.ticker) },
                                            modifier = Modifier.padding(top = 8.dp)
                                        ) {
                                            Text("Торговать")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}