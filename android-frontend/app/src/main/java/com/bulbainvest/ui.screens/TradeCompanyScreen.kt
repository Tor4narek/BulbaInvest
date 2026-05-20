package com.bulbainvest.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bulbainvest.repository.Repository
import kotlinx.coroutines.launch

@Composable
fun TradeCompanyScreen(
    repository: Repository,
    ticker: String,
    onBack: () -> Unit,
    onTradeComplete: (String, Int, Double) -> Unit
) {
    var quantity by remember { mutableStateOf("") }
    var isBuying by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var currentPrice by remember { mutableStateOf("0") }
    var isLoadingPrice by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    // Загрузка текущей цены из стакана (лучший ask)
    LaunchedEffect(ticker) {
        isLoadingPrice = true
        try {
            val orderBook = repository.getOrderBook(ticker)
            val bestAsk = orderBook.getOrNull()?.orders?.minByOrNull { it.price.toDouble() }
            if (bestAsk != null) {
                currentPrice = bestAsk.price
            }
        } catch (e: Exception) {
            currentPrice = when (ticker) {
                "AAPL" -> "150.00"
                "GOOGL" -> "2800.00"
                else -> "100.00"
            }
        }
        isLoadingPrice = false
    }

    val quantityInt = quantity.toIntOrNull() ?: 0
    val priceDouble = currentPrice.toDoubleOrNull() ?: 0.0
    val effectivePrice = if (isBuying) priceDouble else priceDouble * 0.98
    val totalAmount = quantityInt * effectivePrice

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$ticker - ${if (isBuying) "Покупка" else "Продажа"}",
                fontSize = 24.sp
            )
            Button(onClick = onBack) {
                Text("Назад")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            FilterChip(
                selected = isBuying,
                onClick = { isBuying = true },
                label = { Text("Купить") }
            )
            Spacer(modifier = Modifier.width(16.dp))
            FilterChip(
                selected = !isBuying,
                onClick = { isBuying = false },
                label = { Text("Продать") }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Текущая цена", fontSize = 14.sp)
                if (isLoadingPrice) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text(
                        text = "$effectivePrice BYN",
                        fontSize = 28.sp
                    )
                }
                if (!isBuying) {
                    Text(
                        text = "Продажа по цене на 2% ниже рыночной",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = quantity,
            onValueChange = {
                if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                    quantity = it
                }
            },
            label = { Text("Количество акций") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (quantityInt > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Итого:", fontSize = 14.sp)
                    Text(
                        text = "$totalAmount BYN",
                        fontSize = 20.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (quantityInt <= 0) {
                    resultMessage = "Введите количество"
                    return@Button
                }

                isLoading = true
                resultMessage = null

                coroutineScope.launch {
                    val result = if (isBuying) {
                        repository.buyFromCompany(ticker, quantityInt.toString())
                    } else {
                        repository.sellToCompany(ticker, quantityInt.toString())
                    }

                    isLoading = false
                    result.onSuccess { trade ->
                        val tradeType = if (isBuying) "покупка" else "продажа"
                        resultMessage = "$tradeType ${trade.quantity} акций $ticker на сумму ${trade.totalAmount} BYN"
                        onTradeComplete(tradeType, quantityInt, totalAmount)
                    }.onFailure { error ->
                        resultMessage = "Ошибка: ${error.message}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && quantityInt > 0 && !isLoadingPrice,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isBuying)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text(if (isBuying) "Купить" else "Продать")
            }
        }

        if (resultMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (resultMessage!!.contains("успешно") || resultMessage!!.contains("покупка") || resultMessage!!.contains("продажа"))
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = resultMessage!!,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}