# Market Service

`market-service` is the market simulation service for BulbaInvest. It owns only the market side of company trades: current quotes and the amount of company stock still available for direct company buy/sell operations.

Domain-service remains the owner of users, wallets, portfolios, orders, trade history, and balance checks.

## Architecture

- `driver/exchange_driver.c` is a pure C simulation library.
- `internal/driver` embeds the C driver through cgo and converts C structs to Go models.
- `internal/market` runs the tick loop and handles company buy/sell market events.
- `internal/redis` stores quotes and exchanges events with domain-service through Redis.
- `cmd/market-service` wires config, Redis, HTTP, graceful shutdown, and background loops.

The C driver intentionally does not talk to Redis, HTTP, users, wallets, or portfolios. Keeping it as a pure library makes memory ownership clear, keeps the simulation reusable, and prevents business rules from leaking out of domain-service.

## Redis Keys

- `market:stocks`: Redis Hash.
  - field: ticker, for example `AAPL`
  - value: JSON `StockQuote`
- `market:stocks:{ticker}`: single JSON quote copy for direct reads.
- `market.quotes.updated`: Pub/Sub channel with quote update events.

Quote update event:

```json
{
  "type": "MARKET_QUOTES_UPDATED",
  "quotes": [
    {
      "ticker": "AAPL",
      "price": 192.45,
      "availableQuantity": 10000,
      "updatedAt": 1710000000
    }
  ]
}
```

## Redis Streams

Incoming commands from domain-service:

- `domain.trade.company.buy`
- `domain.trade.company.sell`

Outgoing results from market-service:

- `market.trade.company.buy.result`
- `market.trade.company.sell.result`

Incoming events may be sent as a `payload` JSON field or as direct Redis Stream fields.

Buy request:

```json
{
  "eventId": "evt-1",
  "tradeId": "trade-1",
  "userId": "user-1",
  "ticker": "AAPL",
  "quantity": 3,
  "price": 192.45
}
```

Buy result:

```json
{
  "eventId": "evt-1",
  "tradeId": "trade-1",
  "ticker": "AAPL",
  "quantity": 3,
  "accepted": true,
  "actualPrice": 192.45,
  "remainingAvailableQuantity": 9997
}
```

Sell events use the same shape with `CompanySellRequested` and `CompanySellResult`.

The service creates the Redis consumer group automatically. Processed `eventId` values are remembered in Redis for a basic idempotency guard.

## Environment

```env
HTTP_ADDR=:8050
MARKET_TICK_INTERVAL=10s
REDIS_ADDR=localhost:6379
REDIS_PASSWORD=
REDIS_DB=0
MARKET_DRIVER_CONFIG=
MARKET_CONSUMER_GROUP=market-service
MARKET_CONSUMER_NAME=market-service-1
```

`MARKET_DRIVER_CONFIG` is optional. If it is empty or the file cannot be read, the C driver starts with 10 default tickers: AAPL, MSFT, GOOGL, AMZN, TSLA, NVDA, META, NFLX, JPM, DIS.

Optional config file format:

```csv
# ticker,price,available_quantity,volatility
AAPL,192.45,10000,2.20
MSFT,421.30,8500,2.00
```

## HTTP API

- `GET /health`
- `GET /stocks`
- `GET /stocks/{ticker}`

The debug API reads from Redis first and falls back to the in-process driver snapshot.

## Local Run

Start Redis:

```bash
docker run --rm -p 6379:6379 redis:7-alpine
```

Run the service:

```bash
cd services/market-service
go run ./cmd/market-service
```

Check quotes:

```bash
curl http://localhost:8050/stocks
redis-cli HGETALL market:stocks
redis-cli SUBSCRIBE market.quotes.updated
```

Send a manual buy event:

```bash
redis-cli XADD domain.trade.company.buy '*' \
  eventId evt-manual-buy-1 \
  tradeId trade-manual-1 \
  userId user-1 \
  ticker AAPL \
  quantity 2 \
  price 0
```

Read the result:

```bash
redis-cli XRANGE market.trade.company.buy.result - + COUNT 10
```

## C Driver

Build and run C tests:

```bash
cd services/market-service/driver
make test
```

The public C API is declared in `driver/exchange_driver.h`. `exchange_get_snapshot` returns a heap-allocated copy and callers must release it through `exchange_free_snapshot`.

## Go Build

Run Go tests:

```bash
cd services/market-service
go test ./...
```

Build the service:

```bash
CGO_ENABLED=1 go build ./cmd/market-service
```

## Docker

From the repository root:

```bash
docker compose -f DomainServiceAPI/docker-compose.yml up --build market-service redis
```

The Dockerfile builds the C tests, Go tests, and the cgo-enabled service binary.
