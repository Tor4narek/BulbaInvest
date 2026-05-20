package com.bulbainvest.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.bulbainvest.models.Candle
import com.bulbainvest.repository.Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StockChartViewModel(
    private val repository: Repository,
    private val ticker: String
) : ViewModel() {

    private val _candles = MutableStateFlow<List<Candle>>(emptyList())
    val candles: StateFlow<List<Candle>> = _candles.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadCandles("day")
    }

    fun loadCandles(granularity: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val result = repository.getQuoteStats(ticker, granularity)
                result.onSuccess { stats ->
                    // Берем только последние 50 свечей для производительности
                    val limitedCandles = if (stats.candles.size > 50) {
                        stats.candles.takeLast(50)
                    } else {
                        stats.candles
                    }
                    _candles.value = limitedCandles
                    Log.d("StockChartVM", "Loaded ${limitedCandles.size} candles for $ticker")
                }.onFailure { error ->
                    _error.value = error.message
                    Log.e("StockChartVM", "Error: ${error.message}")
                }
            } catch (e: Exception) {
                _error.value = e.message
                Log.e("StockChartVM", "Exception: ${e.message}")
            } finally {
                _loading.value = false
            }
        }
    }

    fun updateWithNewPrice(price: Double) {
        val currentCandles = _candles.value.toMutableList()
        if (currentCandles.isNotEmpty()) {
            val lastCandle = currentCandles.last()
            val updatedLastCandle = lastCandle.copy(
                high = maxOf(lastCandle.high, price),
                low = minOf(lastCandle.low, price),
                close = price
            )
            currentCandles[currentCandles.size - 1] = updatedLastCandle
            _candles.value = currentCandles
        }
    }
}

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