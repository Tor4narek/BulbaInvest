package com.stocksim.graphservice.routes

import com.stocksim.graphservice.model.Granularity
import com.stocksim.graphservice.service.GraphService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    val graphService by inject<GraphService>()

    routing {

        // ── Health ────────────────────────────────────────────────────────────
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        route("/api") {

            // ── Tickers ───────────────────────────────────────────────────────
            /**
             * GET /api/tickers
             * Returns the list of tickers for which quote history is available.
             */
            get("/tickers") {
                val tickers = graphService.getKnownTickers()
                call.respond(mapOf("tickers" to tickers))
            }

            // ── Latest price ──────────────────────────────────────────────────
            /**
             * GET /api/quotes/{ticker}/latest
             * Returns the most recent price snapshot for the given ticker.
             *
             * Path params:
             *   ticker – company ticker, e.g. "AAPL" (case-insensitive)
             *
             * Response 200: LatestPriceResponse JSON
             * Response 404: {"error": "No data for ticker AAPL"}
             */
            get("/quotes/{ticker}/latest") {
                val ticker = call.parameters["ticker"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ticker is required"))

                val result = graphService.getLatestPrice(ticker)
                if (result == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "No data for ticker ${ticker.uppercase()}"))
                } else {
                    call.respond(result)
                }
            }

            // ── Candle stats ──────────────────────────────────────────────────
            /**
             * GET /api/quotes/{ticker}/stats?granularity={hour|day|week|month}
             * Returns OHLCV candles aggregated by ClickHouse.
             *
             * Path params:
             *   ticker      – company ticker (case-insensitive)
             *
             * Query params:
             *   granularity – one of: hour, day, week, month  (default: day)
             *                 Determines both bucket size and lookback window:
             *                   hour  → 1-minute buckets, last 1 hour
             *                   day   → 5-minute buckets, last 24 hours
             *                   week  → 1-hour buckets, last 7 days
             *                   month → 1-day buckets, last 30 days
             *
             * Response 200: StatsResponse JSON
             * Response 400: {"error": "Unknown granularity: ..."}
             */
            get("/quotes/{ticker}/stats") {
                val ticker = call.parameters["ticker"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ticker is required"))

                val granularityParam = call.request.queryParameters["granularity"] ?: "day"
                val granularity = Granularity.from(granularityParam)

                val stats = graphService.getStats(ticker, granularity)
                call.respond(stats)
            }
        }
    }
}
