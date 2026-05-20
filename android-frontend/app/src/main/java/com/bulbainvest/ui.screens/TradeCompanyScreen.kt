package com.bulbainvest.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bulbainvest.repository.Repository
import com.bulbainvest.viewmodels.MarketViewModel
import kotlinx.coroutines.launch
import java.text.DecimalFormat

@Composable
fun TradeCompanyScreen(
    repository: Repository,
    marketViewModel: MarketViewModel,
    ticker: String,
    onBack: () -> Unit,
    onTradeComplete: (String, Int, Double) -> Unit
) {
    var quantity by remember { mutableStateOf("") }
    var isBuying by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Получаем реальную цену из MarketViewModel
    val quotes by marketViewModel.quotes.collectAsState()
    val currentQuote = quotes[ticker.uppercase()]
    val currentPrice = currentQuote?.midPrice ?: 0.0
    val isLoadingPrice = currentPrice == 0.0

    val quantityInt = quantity.toIntOrNull() ?: 0
    val effectivePrice = if (isBuying) currentPrice else currentPrice * 0.98
    val totalAmount = quantityInt * effectivePrice

    val decimalFormat = DecimalFormat("#,##0.00")

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
            Column {
                Text(
                    text = ticker.uppercase(),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isBuying) "Покупка у компании" else "Продажа компании",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onBack) {
                Text("Назад")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Переключатель Купить/Продать
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { isBuying = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isBuying) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isBuying) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("Купить", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = { isBuying = false },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isBuying) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (!isBuying) MaterialTheme.colorScheme.onError
                    else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("Продать", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Карточка с ценой
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isBuying)
                    MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isBuying) "Цена покупки" else "Цена продажи",
                    fontSize = 14.sp,
                    color = if (isBuying)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onErrorContainer
                )
                if (isLoadingPrice) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                } else {
                    Text(
                        text = "${decimalFormat.format(effectivePrice)} BYN",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isBuying)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                if (!isBuying) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠️ Продажа по цене на 2% ниже рыночной",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Поле ввода количества
        OutlinedTextField(
            value = quantity,
            onValueChange = {
                if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                    quantity = it
                }
            },
            label = { Text("Количество акций") },
            placeholder = { Text("Введите количество") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            supportingText = {
                if (quantity.isNotEmpty() && quantityInt == 0) {
                    Text("Введите корректное количество", color = MaterialTheme.colorScheme.error)
                }
            }
        )

        // Карточка с итогом (показываем всегда, но с заглушкой если 0)
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (quantityInt > 0)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Итого к оплате",
                        fontSize = 12.sp,
                        color = if (quantityInt > 0)
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${decimalFormat.format(totalAmount)} BYN",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (quantityInt > 0)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (quantityInt > 0) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (isBuying)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Text(
                            text = "${quantityInt} шт.",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBuying)
                                MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onError
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Кнопка действия
        Button(
            onClick = {
                if (quantityInt <= 0) {
                    resultMessage = "Введите количество акций"
                    return@Button
                }
                if (currentPrice <= 0) {
                    resultMessage = "Цена не загружена, попробуйте позже"
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
                        val tradeType = if (isBuying) "Покупка" else "Продажа"
                        resultMessage = "$tradeType ${trade.quantity} акций $ticker на сумму ${decimalFormat.format(trade.totalAmount.toDouble())} BYN"
                        onTradeComplete(tradeType, quantityInt, totalAmount)
                    }.onFailure { error ->
                        resultMessage = when {
                            error.message?.contains("INSUFFICIENT_STOCKS") == true ->
                                "❌ Недостаточно акций у компании"
                            error.message?.contains("INSUFFICIENT_FUNDS") == true ->
                                "❌ Недостаточно средств на счете.\nПополните баланс в портфеле"
                            else -> "❌ Ошибка: ${error.message}"
                        }
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
                Text(
                    if (isBuying) "Купить" else "Продать",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Сообщение о результате
        if (resultMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (resultMessage!!.contains("Покупка") || resultMessage!!.contains("Продажа"))
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = resultMessage!!,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 14.sp
                )
            }
        }
    }
}