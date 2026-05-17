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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TradeCompanyScreen(
    ticker: String,
    onBack: () -> Unit,
    onTradeComplete: (String, Int, Double) -> Unit  // type, quantity, total
) {
    var quantity by remember { mutableStateOf("") }
    var isBuying by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    // Get coroutine scope
    val scope = rememberCoroutineScope()

    // Моковые цены
    val currentPrice = if (ticker == "AAPL") 150.0 else 2800.0
    val buyPrice = currentPrice
    val sellPrice = currentPrice * 0.98  // продажа на 2% дешевле

    val effectivePrice = if (isBuying) buyPrice else sellPrice
    val quantityInt = quantity.toIntOrNull() ?: 0
    val totalAmount = quantityInt * effectivePrice

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Заголовок
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

        // Переключатель Покупка/Продажа
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

        // Информация о цене
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Текущая цена", fontSize = 14.sp)
                Text(
                    text = "$effectivePrice BYN",
                    fontSize = 28.sp
                )
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

        // Ввод количества
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

        // Итого
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

        // Кнопка действия
        Button(
            onClick = {
                if (quantityInt <= 0) {
                    resultMessage = "Введите количество"
                    return@Button
                }

                isLoading = true
                resultMessage = null

                // Моковая имитация запроса
                // TODO: заменить на реальный API вызов
                // POST /api/trades/company/buy или /sell

                scope.launch {
                    delay(1000) // симуляция сети
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        val tradeType = if (isBuying) "покупка" else "продажа"
                        resultMessage = "$tradeType $quantityInt акций $ticker на сумму $totalAmount BYN"
                        onTradeComplete(tradeType, quantityInt, totalAmount)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && quantityInt > 0,
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

        // Сообщение о результате
        if (resultMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (resultMessage!!.contains("успешно"))
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