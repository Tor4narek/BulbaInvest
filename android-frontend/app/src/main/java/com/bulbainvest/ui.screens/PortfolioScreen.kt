package com.bulbainvest.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bulbainvest.models.PortfolioPosition
import com.bulbainvest.models.UserWallet
import androidx.compose.ui.Alignment

@Composable
fun PortfolioScreen(
    onLogout: () -> Unit,
    onTradeClick: (String) -> Unit,
    onP2PClick: () -> Unit
) {
    val wallets = listOf(
        UserWallet("1", "BYN", 1000.0, 0.0, 1000.0, true)
    )

    val portfolio = listOf(
        PortfolioPosition("AAPL", 10, 0, 10, 150.0),
        PortfolioPosition("GOOGL", 5, 0, 5, 2800.0)
    )

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
            Button(onClick = onLogout) {
                Text("Выйти")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Баланс", fontSize = MaterialTheme.typography.titleMedium.fontSize)
                Text(
                    text = "${wallets.first().amount} BYN",
                    fontSize = MaterialTheme.typography.headlineLarge.fontSize
                )
                Text("Доступно: ${wallets.first().availableAmount} BYN")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Мои акции", fontSize = MaterialTheme.typography.titleLarge.fontSize)
            Button(
                onClick = { onTradeClick("AAPL") }
            ) {
                Text("Купить акции")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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
                            Text(
                                "${position.quantity * position.averageBuyPrice} BYN",
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