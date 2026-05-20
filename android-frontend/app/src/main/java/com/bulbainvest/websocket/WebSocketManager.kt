package com.bulbainvest.websocket

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class QuoteUpdate(
    val ticker: String,
    val price: Double,
    val availableQuantity: Long?,
    val updatedAt: Long,
    val volatility: Double?
)

class WebSocketManager {
    companion object {
        private const val TAG = "WebSocketManager"
        private const val WS_BASE_URL = "wss://bulba.onix.fun/api/ws/quotes"
    }

    private var currentClient: OkHttpClient? = null
    private var currentWebSocket: WebSocket? = null
    private var currentTicker: String? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 10
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val tickerCallbacks = mutableMapOf<String, MutableList<(QuoteUpdate) -> Unit>>()
    private val connectionCallbacks = mutableListOf<(Boolean) -> Unit>()

    fun subscribeToQuote(ticker: String, callback: (QuoteUpdate) -> Unit) {
        val normalizedTicker = ticker.uppercase()

        synchronized(tickerCallbacks) {
            tickerCallbacks.getOrPut(normalizedTicker) { mutableListOf() }.add(callback)
        }

        if (tickerCallbacks[normalizedTicker]?.size == 1) {
            connectForTicker(normalizedTicker)
        }
    }

    fun unsubscribeFromQuote(ticker: String, callback: (QuoteUpdate) -> Unit) {
        val normalizedTicker = ticker.uppercase()

        synchronized(tickerCallbacks) {
            tickerCallbacks[normalizedTicker]?.remove(callback)
            if (tickerCallbacks[normalizedTicker].isNullOrEmpty()) {
                tickerCallbacks.remove(normalizedTicker)
                if (currentTicker == normalizedTicker) {
                    disconnect()
                }
            }
        }
    }

    fun addConnectionListener(callback: (Boolean) -> Unit) {
        connectionCallbacks.add(callback)
    }

    fun removeConnectionListener(callback: (Boolean) -> Unit) {
        connectionCallbacks.remove(callback)
    }

    private fun connectForTicker(ticker: String) {
        if (currentTicker == ticker && currentWebSocket != null) {
            Log.d(TAG, "Already connected to $ticker")
            return
        }

        disconnect()

        currentTicker = ticker
        val url = "$WS_BASE_URL?ticker=$ticker"

        Log.d(TAG, "Connecting to $url")

        currentClient = OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val request = Request.Builder()
            .url(url)
            .build()

        currentWebSocket = currentClient?.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened for $ticker")
                reconnectAttempts = 0
                connectionCallbacks.forEach { it(true) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val quote = QuoteUpdate(
                        ticker = json.getString("ticker"),
                        price = json.getDouble("price"),
                        availableQuantity = if (json.has("availableQuantity")) json.getLong("availableQuantity") else null,
                        updatedAt = json.getLong("updatedAt"),
                        volatility = if (json.has("volatility")) json.getDouble("volatility") else null
                    )

                    synchronized(tickerCallbacks) {
                        tickerCallbacks[quote.ticker]?.forEach { callback ->
                            callback(quote)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing message: ${e.message}", e)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d(TAG, "Received binary message")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Closing WebSocket: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed for $ticker")
                connectionCallbacks.forEach { it(false) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error for $ticker", t)
                connectionCallbacks.forEach { it(false) }

                if (reconnectAttempts < maxReconnectAttempts && tickerCallbacks.containsKey(ticker)) {
                    reconnectAttempts++
                    val delayMs = (reconnectAttempts * 1000L).coerceAtMost(30000)
                    Log.d(TAG, "Reconnecting in ${delayMs}ms (attempt $reconnectAttempts/$maxReconnectAttempts)")

                    reconnectJob = scope.launch {
                        delay(delayMs)
                        connectForTicker(ticker)
                    }
                }
            }
        })
    }

    private fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        currentWebSocket?.close(1000, "Normal closure")
        currentWebSocket = null
        currentClient?.dispatcher?.executorService?.shutdown()
        currentClient = null
        currentTicker = null
    }

    fun disconnectAll() {
        synchronized(tickerCallbacks) {
            tickerCallbacks.clear()
        }
        disconnect()
    }
}