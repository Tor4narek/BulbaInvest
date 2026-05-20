package com.bulbainvest.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.bulbainvest.models.Quote
import com.bulbainvest.repository.Repository
import com.bulbainvest.websocket.QuoteUpdate
import com.bulbainvest.websocket.WebSocketManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MarketViewModel(
    private val repository: Repository,
    private val webSocketManager: WebSocketManager
) : ViewModel() {

    private val _tickers = MutableStateFlow<List<String>>(emptyList())
    val tickers: StateFlow<List<String>> = _tickers.asStateFlow()

    private val _quotes = MutableStateFlow<Map<String, Quote>>(emptyMap())
    val quotes: StateFlow<Map<String, Quote>> = _quotes.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val quoteCallbacks = mutableMapOf<String, (QuoteUpdate) -> Unit>()

    init {
        loadTickers()
    }

    fun loadTickers() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val result = repository.getTickers()
                result.onSuccess { tickerResponse ->
                    _tickers.value = tickerResponse.tickers
                    tickerResponse.tickers.forEach { ticker ->
                        loadQuote(ticker)
                    }
                    subscribeToAllTickers(tickerResponse.tickers)
                }.onFailure { error ->
                    _error.value = error.message
                    Log.e("MarketViewModel", "Error loading tickers: ${error.message}")
                }
            } catch (e: Exception) {
                _error.value = e.message
                Log.e("MarketViewModel", "Exception: ${e.message}")
            } finally {
                _loading.value = false
            }
        }
    }

    private fun loadQuote(ticker: String) {
        viewModelScope.launch {
            try {
                val result = repository.getQuoteLatest(ticker)
                result.onSuccess { quote ->
                    _quotes.value = _quotes.value.toMutableMap().apply {
                        put(ticker.uppercase(), quote)
                    }
                }.onFailure { error ->
                    Log.e("MarketViewModel", "Error loading $ticker: ${error.message}")
                }
            } catch (e: Exception) {
                Log.e("MarketViewModel", "Exception loading $ticker: ${e.message}")
            }
        }
    }

    private fun subscribeToAllTickers(tickers: List<String>) {
        val callback: (QuoteUpdate) -> Unit = { update ->
            viewModelScope.launch {
                val currentQuote = _quotes.value[update.ticker]
                if (currentQuote != null) {
                    val updatedQuote = currentQuote.copy(
                        midPrice = update.price,
                        timestamp = update.updatedAt
                    )
                    _quotes.value = _quotes.value.toMutableMap().apply {
                        put(update.ticker, updatedQuote)
                    }
                }
            }
        }
        webSocketManager.subscribeToQuotes(tickers, callback)
    }

    fun subscribeToSingleTicker(ticker: String) {
        if (!_tickers.value.contains(ticker)) {
            loadQuote(ticker)
        }
        if (!quoteCallbacks.containsKey(ticker)) {
            val callback: (QuoteUpdate) -> Unit = { update ->
                viewModelScope.launch {
                    val currentQuote = _quotes.value[update.ticker]
                    if (currentQuote != null) {
                        val updatedQuote = currentQuote.copy(
                            midPrice = update.price,
                            timestamp = update.updatedAt
                        )
                        _quotes.value = _quotes.value.toMutableMap().apply {
                            put(update.ticker, updatedQuote)
                        }
                    }
                }
            }
            quoteCallbacks[ticker] = callback
            webSocketManager.subscribeToQuote(ticker, callback)
        }
    }

    fun getQuote(ticker: String): Quote? = _quotes.value[ticker.uppercase()]

    override fun onCleared() {
        super.onCleared()
        quoteCallbacks.values.forEach { callback ->
            _tickers.value.forEach { ticker ->
                webSocketManager.unsubscribeFromQuote(ticker, callback)
            }
        }
        webSocketManager.disconnectAll()
    }
}

class MarketViewModelFactory(
    private val repository: Repository,
    private val webSocketManager: WebSocketManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MarketViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MarketViewModel(repository, webSocketManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}