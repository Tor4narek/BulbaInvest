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
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropUp

// Модель ордера
data class SellOrder(
    val id: String,
    val sellerName: String,
    val quantity: Int,
    val price: Double,
    val createdAt: String
)

@Composable
fun TradeP2PScreen(
    onBack: () -> Unit,
    onOrderExecuted: () -> Unit = {}
) {
    var showCreateForm by remember { mutableStateOf(false) }
    var ticker by remember { mutableStateOf("AAPL") }
    var quantity by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Моковые ордера
    var openOrders by remember {
        mutableStateOf(
            listOf(
                SellOrder("1", "user123", 10, 155.0, "2024-01-15 10:30"),
                SellOrder("2", "investor42", 5, 152.0, "2024-01-15 11:00"),
                SellOrder("3", "trader99", 20, 158.0, "2024-01-15 12:15")
            )
        )
    }

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
            Text("P2P Торговля", fontSize = 24.sp)
            Row {
                IconButton(onClick = { showCreateForm = !showCreateForm }) {
                    Icon(
                        if (showCreateForm)
                            androidx.compose.material.icons.Icons.Default.ArrowDropUp
                        else
                            androidx.compose.material.icons.Icons.Default.Add,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = ticker == "AAPL",
                onClick = { ticker = "AAPL" },
                label = { Text("AAPL") }
            )
            FilterChip(
                selected = ticker == "GOOGL",
                onClick = { ticker = "GOOGL" },
                label = { Text("GOOGL") }
            )
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
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("Цена за акцию (BYN)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                                kotlinx.coroutines.delay(500)
                                // TODO: POST /api/orders/sell
                                val newOrder = SellOrder(
                                    id = System.currentTimeMillis().toString(),
                                    sellerName = "Я",
                                    quantity = qty,
                                    price = prc,
                                    createdAt = "только что"
                                )
                                openOrders = listOf(newOrder) + openOrders
                                isLoading = false
                                message = "Ордер создан: продажа $qty $ticker по $prc BYN"
                                quantity = ""
                                price = ""
                                showCreateForm = false
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
                    containerColor = if (message!!.contains("успешно") || message!!.contains("создан"))
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
        Text("Активные ордера на продажу $ticker", fontSize = 18.sp)
        Spacer(modifier = Modifier.height(8.dp))

        if (openOrders.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text("Нет активных ордеров", modifier = Modifier.padding(16.dp))
            }
        } else {
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
                                Text("👤 ${order.sellerName}", fontSize = 12.sp)
                                Text("🕐 ${order.createdAt}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Button(
                                onClick = {
                                    isLoading = true
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(500)
                                        // TODO: POST /api/orders/sell/{orderId}/buy
                                        openOrders = openOrders.filter { it.id != order.id }
                                        isLoading = false
                                        message = "Вы купили ${order.quantity} акций по ${order.price} BYN"
                                        onOrderExecuted()
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