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

class StockDetailsViewModel(
    private val repository: Repository,
    private val webSocketManager: WebSocketManager,
    private val ticker: String
) : ViewModel() {

    private val _quote = MutableStateFlow<Quote?>(null)
    val quote: StateFlow<Quote?> = _quote.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var quoteCallback: ((QuoteUpdate) -> Unit)? = null

    init {
        loadQuote()
        subscribeToUpdates()
    }

    // StockDetailsViewModel.kt - добавьте этот метод в класс

    fun retryLoad() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val result = repository.getQuoteLatest(ticker)
                result.onSuccess { quote ->
                    _quote.value = quote
                }.onFailure { error ->
                    _error.value = error.message
                    Log.e("StockDetailsVM", "Error loading quote: ${error.message}")
                }
            } catch (e: Exception) {
                _error.value = e.message
                Log.e("StockDetailsVM", "Exception: ${e.message}")
            } finally {
                _loading.value = false
            }
        }
    }

    private fun loadQuote() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val result = repository.getQuoteLatest(ticker)
                result.onSuccess { quote ->
                    _quote.value = quote
                }.onFailure { error ->
                    _error.value = error.message
                    Log.e("StockDetailsVM", "Error loading quote: ${error.message}")
                }
            } catch (e: Exception) {
                _error.value = e.message
                Log.e("StockDetailsVM", "Exception: ${e.message}")
            } finally {
                _loading.value = false
            }
        }
    }

    private fun subscribeToUpdates() {
        quoteCallback = { update ->
            viewModelScope.launch {
                val currentQuote = _quote.value
                if (currentQuote != null) {
                    val updatedQuote = currentQuote.copy(
                        midPrice = update.price,
                        timestamp = update.updatedAt
                    )
                    _quote.value = updatedQuote
                }
            }
        }
        quoteCallback?.let {
            webSocketManager.subscribeToQuote(ticker, it)
        }
    }

    override fun onCleared() {
        super.onCleared()
        quoteCallback?.let {
            webSocketManager.unsubscribeFromQuote(ticker, it)
        }
    }
}

class StockDetailsViewModelFactory(
    private val repository: Repository,
    private val webSocketManager: WebSocketManager,
    private val ticker: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StockDetailsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StockDetailsViewModel(repository, webSocketManager, ticker) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}