package com.bulbainvest.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.bulbainvest.models.Quote
import com.bulbainvest.repository.Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class StockDetailsViewModel(
    private val repository: Repository,
    private val marketViewModel: MarketViewModel,
    private val ticker: String
) : ViewModel() {

    private val _quote = MutableStateFlow<Quote?>(null)
    val quote: StateFlow<Quote?> = _quote.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            marketViewModel.quotes.collect { quotesMap ->
                val updatedQuote = quotesMap[ticker]
                if (updatedQuote != null) {
                    _quote.value = updatedQuote
                    if (_loading.value) {
                        _loading.value = false
                    }
                }
            }
        }

        viewModelScope.launch {
            val currentQuote = marketViewModel.quotes.first()[ticker]
            if (currentQuote == null) {
                loadQuote()
            } else {
                _quote.value = currentQuote
                _loading.value = false
            }
        }

        marketViewModel.subscribeToSingleTicker(ticker)
    }

    private fun loadQuote() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val result = repository.getQuoteLatest(ticker)
                result.onSuccess { quote ->
                    _quote.value = quote
                    Log.d("StockDetailsVM", "Loaded: ${quote.midPrice}")
                }.onFailure { error ->
                    _error.value = error.message
                    Log.e("StockDetailsVM", "Error: ${error.message}")
                }
            } catch (e: Exception) {
                _error.value = e.message
                Log.e("StockDetailsVM", "Exception: ${e.message}")
            } finally {
                _loading.value = false
            }
        }
    }

    fun retryLoad() {
        loadQuote()
    }
}

class StockDetailsViewModelFactory(
    private val repository: Repository,
    private val marketViewModel: MarketViewModel,
    private val ticker: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StockDetailsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StockDetailsViewModel(repository, marketViewModel, ticker) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}