package com.bulbainvest.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bulbainvest.models.SellOrder
import com.bulbainvest.repository.Repository
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun TradeP2PScreen(
    repository: Repository,
    onBack: () -> Unit,
    onOrderExecuted: () -> Unit = {}
) {
    var showCreateForm by remember { mutableStateOf(false) }
    var ticker by remember { mutableStateOf("AAPL") }
    var quantity by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var openOrders by remember { mutableStateOf<List<SellOrder>>(emptyList()) }
    var isLoadingOrders by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    // Загрузка ордеров
    fun loadOrders() {
        coroutineScope.launch {
            isLoadingOrders = true
            val result = repository.getOrderBook(ticker)
            result.onSuccess { orderBook ->
                openOrders = orderBook.orders.filter { it.status == "OPEN" }
            }.onFailure { error ->
                message = "Ошибка загрузки: ${error.message}"
            }
            isLoadingOrders = false
        }
    }

    LaunchedEffect(ticker) {
        loadOrders()
    }

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
            Text("P2P Торговля", fontSize = 24.sp)
            Row {
                IconButton(onClick = { showCreateForm = !showCreateForm }) {
                    Icon(
                        if (showCreateForm) Icons.Default.ArrowDropUp else Icons.Default.Add,
                        contentDescription = if (showCreateForm) "Скрыть" else "Создать"
                    )
                }
                Button(onClick = onBack) {
                    Text("Назад")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Выбор тикера
        val tickers = listOf("AAPL", "MSFT", "GOOGL", "AMZN", "TSLA", "NVDA", "META", "NFLX", "JPM", "DIS")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tickers.take(5).forEach { t ->
                FilterChip(
                    selected = ticker == t,
                    onClick = {
                        ticker = t
                        loadOrders()
                    },
                    label = { Text(t) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Форма создания ордера
        if (showCreateForm) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Создать ордер на продажу", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = quantity,
                        onValueChange = {
                            if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                quantity = it
                            }
                        },
                        label = { Text("Количество") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("Цена за акцию (BYN)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val qty = quantity.toIntOrNull()
                            val prc = price.toDoubleOrNull()

                            if (qty == null || qty <= 0) {
                                message = "Введите корректное количество"
                                return@Button
                            }
                            if (prc == null || prc <= 0) {
                                message = "Введите корректную цену"
                                return@Button
                            }

                            isLoading = true
                            coroutineScope.launch {
                                val result = repository.createSellOrder(ticker, qty.toString(), prc.toString())
                                isLoading = false
                                result.onSuccess {
                                    message = "Ордер создан: продажа $qty $ticker по $prc BYN"
                                    quantity = ""
                                    price = ""
                                    showCreateForm = false
                                    loadOrders()
                                }.onFailure { error ->
                                    message = "Ошибка: ${error.message}"
                                }
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Text("Разместить ордер")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Сообщения
        if (message != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (message!!.contains("создан") || message!!.contains("купили"))
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(message!!, modifier = Modifier.padding(12.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Список ордеров
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Активные ордера на продажу $ticker", fontSize = 18.sp)
            IconButton(onClick = { loadOrders() }) {
                Icon(Icons.Default.Add, contentDescription = "Обновить")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        when {
            isLoadingOrders -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            openOrders.isEmpty() -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("Нет активных ордеров", modifier = Modifier.padding(16.dp))
                }
            }
            else -> {
                LazyColumn {
                    items(openOrders) { order ->
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
                                    Text("📊 ${order.quantity} шт.", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                    Text("💰 ${order.price} BYN за шт.")
                                    Text("👤 ${order.sellerUserId.take(8)}...", fontSize = 12.sp)
                                    Text("🕐 ${order.createdAt.take(19)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                Button(
                                    onClick = {
                                        isLoading = true
                                        coroutineScope.launch {
                                            val result = repository.buySpecificOrder(
                                                order.id,
                                                order.quantity,
                                                order.price
                                            )
                                            isLoading = false
                                            result.onSuccess {
                                                message = "Вы купили ${order.quantity} акций по ${order.price} BYN"
                                                loadOrders()
                                                onOrderExecuted()
                                            }.onFailure { error ->
                                                message = "Ошибка: ${error.message}"
                                            }
                                        }
                                    },
                                    enabled = !isLoading,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("Купить")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}