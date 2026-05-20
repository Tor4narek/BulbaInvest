// StockDetailsScreen.kt - добавьте импорт и график
package com.bulbainvest.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bulbainvest.repository.Repository
import com.bulbainvest.ui.components.StockChart
import com.bulbainvest.viewmodels.MarketViewModel
import com.bulbainvest.viewmodels.StockChartViewModel
import com.bulbainvest.viewmodels.StockDetailsViewModel
import com.bulbainvest.viewmodels.StockDetailsViewModelFactory
import kotlinx.coroutines.launch
import com.bulbainvest.viewmodels.StockChartViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDetailsScreen(
    repository: Repository,
    marketViewModel: MarketViewModel,
    ticker: String,
    onBack: () -> Unit,
    onTradeClick: () -> Unit
) {
    val viewModel: StockDetailsViewModel = viewModel(
        factory = StockDetailsViewModelFactory(repository, marketViewModel, ticker)
    )

    val chartViewModel: StockChartViewModel = viewModel(
        factory = StockChartViewModelFactory(repository, ticker)
    )

    val quote by viewModel.quote.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    val scope = rememberCoroutineScope()

    var lastUpdateTime = 0L

    LaunchedEffect(quote) {
        quote?.let {
            val now = System.currentTimeMillis()
            // Обновляем график не чаще раза в секунду
            if (now - lastUpdateTime > 1000) {
                lastUpdateTime = now
                chartViewModel.updateWithNewPrice(it.midPrice)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(ticker.uppercase(), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        if (quote != null && !loading) {
                            Text(
                                text = String.format("%.2f BYN", quote?.midPrice ?: 0.0),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    Button(onClick = onTradeClick) {
                        Text("Торговать")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Карточка с ценой
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when {
                        loading -> CircularProgressIndicator()
                        error != null -> Text("Ошибка: $error", color = MaterialTheme.colorScheme.error)
                        quote != null -> {
                            Text("Текущая цена", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = String.format("%.2f", quote?.midPrice ?: 0.0),
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("BYN", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("Покупка", fontSize = 12.sp)
                                        Text(
                                            text = String.format("%.2f", quote?.buyPrice ?: 0.0),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("Продажа", fontSize = 12.sp)
                                        Text(
                                            text = String.format("%.2f", quote?.sellPrice ?: 0.0),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // График
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("График цен", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    StockChart(
                        viewModel = chartViewModel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Здесь можно добавить стакан в будущем
        }
    }
}

// Фабрика для StockChartViewModel
class StockChartViewModelFactory(
    private val repository: Repository,
    private val ticker: String
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StockChartViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StockChartViewModel(repository, ticker) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}