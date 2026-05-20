package com.bulbainvest.websocket

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import org.json.JSONArray
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

    private val activeSockets = mutableMapOf<String, WebSocket>()
    private val activeClients = mutableMapOf<String, OkHttpClient>()
    private val tickerCallbacks = mutableMapOf<String, MutableList<(QuoteUpdate) -> Unit>>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null

    fun subscribeToQuote(ticker: String, callback: (QuoteUpdate) -> Unit) {
        subscribeToQuotes(listOf(ticker), callback)
    }

    fun subscribeToQuotes(tickers: List<String>, callback: (QuoteUpdate) -> Unit) {
        val normalizedTickers = tickers.map { it.uppercase() }

        normalizedTickers.forEach { ticker ->
            synchronized(tickerCallbacks) {
                tickerCallbacks.getOrPut(ticker) { mutableListOf() }.add(callback)
            }
        }

        val socketKey = normalizedTickers.sorted().joinToString(",")
        if (!activeSockets.containsKey(socketKey)) {
            connectForTickers(normalizedTickers)
        }
    }

    fun unsubscribeFromQuote(ticker: String, callback: (QuoteUpdate) -> Unit) {
        val normalizedTicker = ticker.uppercase()

        synchronized(tickerCallbacks) {
            tickerCallbacks[normalizedTicker]?.remove(callback)
            if (tickerCallbacks[normalizedTicker].isNullOrEmpty()) {
                tickerCallbacks.remove(normalizedTicker)
            }
        }
    }

    private fun connectForTickers(tickers: List<String>) {
        val tickersParam = tickers.joinToString(",")
        val url = "$WS_BASE_URL?tickers=$tickersParam"
        val socketKey = tickers.sorted().joinToString(",")

        Log.d(TAG, "Connecting to $url")

        val client = OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val request = Request.Builder()
            .url(url)
            .build()

        val webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened for ${tickers.joinToString()}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    Log.d(TAG, "Received: $text")

                    // Парсим как JSON массив
                    val jsonArray = JSONArray(text)

                    for (i in 0 until jsonArray.length()) {
                        val json = jsonArray.getJSONObject(i)
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
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing message: ${e.message}", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error", t)
                activeSockets.remove(socketKey)
                activeClients.remove(socketKey)

                reconnectJob = scope.launch {
                    delay(5000)
                    if (tickerCallbacks.keys.any { tickers.contains(it) }) {
                        connectForTickers(tickers)
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                activeSockets.remove(socketKey)
                activeClients.remove(socketKey)
            }
        })

        activeSockets[socketKey] = webSocket
        activeClients[socketKey] = client
    }

    fun disconnectAll() {
        reconnectJob?.cancel()
        synchronized(tickerCallbacks) {
            tickerCallbacks.clear()
        }
        activeSockets.keys.toList().forEach { socketKey ->
            activeSockets[socketKey]?.close(1000, "Normal closure")
            activeClients[socketKey]?.dispatcher?.executorService?.shutdown()
        }
        activeSockets.clear()
        activeClients.clear()
    }
}