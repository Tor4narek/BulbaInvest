package com.bulbainvest.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.bulbainvest.viewmodels.StockChartViewModel
import kotlin.math.max
import kotlin.math.min
@Composable
fun StockChart(
    viewModel: StockChartViewModel,
    modifier: Modifier = Modifier,
    initialGranularity: String = "day"
) {
    val candles = viewModel.candles.value
    val loading = viewModel.loading.value
    val error = viewModel.error.value

    var currentGranularity by remember { mutableStateOf(initialGranularity) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Отдельный LaunchedEffect для сброса флага загрузки
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            delay(1000)
            isRefreshing = false
        }
    }

    Column(modifier = modifier) {
        // Переключатели
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("hour", "day", "week", "month").forEach { g ->
                FilterChip(
                    selected = currentGranularity == g,
                    onClick = {
                        if (!isRefreshing) {
                            currentGranularity = g
                            isRefreshing = true
                            viewModel.loadCandles(g)
                        }
                    },
                    label = { Text(g.uppercase(), fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    enabled = !isRefreshing
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Статус загрузки
        when {
            loading -> {
                Box(modifier = Modifier.height(200.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Загрузка графика...", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
            error != null -> {
                Box(modifier = Modifier.height(200.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚠️", fontSize = 32.sp)
                        Text("Ошибка: $error", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.loadCandles(currentGranularity) }) {
                            Text("Повторить", fontSize = 12.sp)
                        }
                    }
                }
            }
            candles.isEmpty() -> {
                Box(modifier = Modifier.height(200.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📊", fontSize = 32.sp)
                        Text("Нет данных для отображения", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
            else -> {
                SimpleCandleChart(
                    candles = candles,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            }
        }

        // Информация о последней свече
        if (candles.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            val lastCandle = candles.last()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                InfoChip("O", String.format("%.2f", lastCandle.open))
                InfoChip("H", String.format("%.2f", lastCandle.high))
                InfoChip("L", String.format("%.2f", lastCandle.low))
                InfoChip("C", String.format("%.2f", lastCandle.close))
            }
        }
    }
}

@Composable
fun SimpleCandleChart(
    candles: List<com.bulbainvest.models.Candle>,
    modifier: Modifier = Modifier
) {
    if (candles.isEmpty()) return

    // Находим min/max для масштабирования
    val allPrices = candles.flatMap { listOf(it.high, it.low) }
    val minPrice = allPrices.minOrNull() ?: 0.0
    val maxPrice = allPrices.maxOrNull() ?: 1.0
    val priceRange = (maxPrice - minPrice).coerceAtLeast(0.01)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val candleWidth = (width / candles.size) * 0.7f
        val spacing = (width / candles.size) * 0.3f

        candles.forEachIndexed { index, candle ->
            val x = index * (candleWidth + spacing) + spacing / 2

            // Вычисляем позиции
            val highY = height * (1 - ((candle.high - minPrice) / priceRange)).toFloat()
            val lowY = height * (1 - ((candle.low - minPrice) / priceRange)).toFloat()
            val openY = height * (1 - ((candle.open - minPrice) / priceRange)).toFloat()
            val closeY = height * (1 - ((candle.close - minPrice) / priceRange)).toFloat()

            // Цвет свечи (зеленый если close > open)
            val isGreen = candle.close > candle.open
            val candleColor = if (isGreen) Color(0xFF26A69A) else Color(0xFFEF5350)

            // Рисуем тень (high-low)
            drawLine(
                color = candleColor,
                start = Offset(x + candleWidth / 2, highY),
                end = Offset(x + candleWidth / 2, lowY),
                strokeWidth = 1f
            )

            // Рисуем тело свечи
            val topY = min(openY, closeY)
            val bottomY = max(openY, closeY)
            val bodyHeight = (bottomY - topY).coerceAtLeast(1f)

            drawRect(
                color = candleColor,
                topLeft = Offset(x, topY),
                size = androidx.compose.ui.geometry.Size(candleWidth, bodyHeight)
            )
        }
    }
}

@Composable
fun InfoChip(label: String, value: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(label, fontSize = 10.sp, color = Color.Gray)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}