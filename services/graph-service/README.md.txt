# GraphService

Микросервис для сбора, хранения и выдачи истории котировок акций.  
Часть экосистемы **StockSim** — учебного аналога инвестиционного приложения с фейковыми данными.

---

## Что делает сервис

- Подписывается на Redis-канал и получает ценовые тики от **StocksMarketAPI (Go)**
- Накапливает тики в памяти и пачками записывает в **ClickHouse**
- Отдаёт OHLCV-свечи и последние цены через REST API
- Агрегацию делает на стороне ClickHouse — сервис не занимается математикой

Сервис **не знает** о пользователях, портфелях и сделках. Только котировки.

---

## Стек

| Компонент | Технология |
|---|---|
| HTTP-сервер | Kotlin + Ktor 2.3 (Netty) |
| DI | Koin 3.5 |
| Redis-клиент | Lettuce 6 (async pub/sub) |
| БД | ClickHouse 24.3 (JDBC) |
| Сериализация | kotlinx.serialization |
| Логирование | Logback + kotlin-logging |

---

## Архитектура

```
StocksMarketAPI (Go)
        │
        │  PUBLISH stock_prices {...}
        ▼
      Redis
        │
        │  pub/sub (Lettuce)
        ▼
  PriceConsumer
  (in-memory batch)
        │
        │  bulk INSERT каждые 10 сек
        ▼
    ClickHouse
    (таблица quotes)
        │
        │  SELECT + агрегация
        ▼
    REST API  ◄──── APIGateway / мобильные приложения
```

### Батчинг

Тики не пишутся поштучно — это убило бы ClickHouse. Вместо этого:

- **Time-based flush** — каждые `BATCH_FLUSH_INTERVAL_SECONDS` секунд (по умолчанию 10)
- **Size-based flush** — сразу при накоплении `BATCH_MAX_SIZE` тиков (по умолчанию 1000)
- При ошибке записи строки возвращаются в буфер и будут отправлены при следующей попытке
- На graceful shutdown буфер сбрасывается синхронно

---

## Схема данных

### Redis — формат тика

Сообщение, которое публикует StocksMarketAPI:

```json
{
  "ticker": "AAPL",
  "buyPrice": 120.50,
  "sellPrice": 119.80,
  "timestamp": 1747561200000
}
```

`timestamp` — Unix epoch в **миллисекундах**.

### ClickHouse — таблица `quotes`

```sql
CREATE TABLE stocksim.quotes (
    ticker      LowCardinality(String),
    buy_price   Float64,
    sell_price  Float64,
    mid_price   Float64,       -- (buy + sell) / 2, вычисляется при вставке
    ts          DateTime('UTC')
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(ts)
ORDER BY (ticker, ts)
TTL ts + INTERVAL 12 MONTH
```

Схема создаётся автоматически при старте сервиса (`CREATE TABLE IF NOT EXISTS`).

---

## REST API

**Base URL:** `http://<host>:8083`  
**Авторизация:** не требуется (internal-only сервис за APIGateway)  
**Content-Type:** `application/json`

### Health check

```
GET /health
```

```json
{ "status": "ok" }
```

---

### Список тикеров

```
GET /api/tickers
```

Возвращает все тикеры, по которым есть хоть одна запись в ClickHouse.

```json
{ "tickers": ["AAPL", "GOOG", "MSFT"] }
```

---

### Последняя цена

```
GET /api/quotes/{ticker}/latest
```

| Параметр | Описание |
|---|---|
| `ticker` | Тикер компании, регистронезависимо (`aapl` = `AAPL`) |

**200 OK:**
```json
{
  "ticker": "AAPL",
  "buyPrice": 120.5,
  "sellPrice": 119.8,
  "midPrice": 120.15,
  "timestamp": 1747561200000
}
```

**404** — если тикер не найден:
```json
{ "error": "No data for ticker AAPL" }
```

---

### OHLCV-свечи

```
GET /api/quotes/{ticker}/stats?granularity={hour|day|week|month}
```

| Параметр | Описание |
|---|---|
| `ticker` | Тикер компании |
| `granularity` | Горизонт и размер свечи (по умолчанию `day`) |

Маппинг `granularity`:

| Значение | Размер свечи | Глубина данных |
|---|---|---|
| `hour` | 1 минута | последний 1 час |
| `day` | 5 минут | последние 24 часа |
| `week` | 1 час | последние 7 дней |
| `month` | 1 день | последние 30 дней |

**200 OK:**
```json
{
  "ticker": "AAPL",
  "granularity": "hour",
  "candles": [
    {
      "time": "2026-05-18 10:12:00",
      "open": 119.5,
      "high": 121.0,
      "low": 119.0,
      "close": 120.5,
      "volume": 342
    }
  ]
}
```

`volume` — количество тиков в свече (не количество акций).

**400** — неизвестный `granularity`:
```json
{ "error": "Unknown granularity: 'year'. Allowed: hour, day, week, month" }
```

---

## Конфигурация

Все параметры читаются из `application.conf` и переопределяются переменными окружения.

| Переменная | По умолчанию | Описание |
|---|---|---|
| `SERVER_PORT` | `8083` | Порт HTTP-сервера |
| `REDIS_HOST` | `localhost` | Хост Redis |
| `REDIS_PORT` | `6379` | Порт Redis |
| `REDIS_CHANNEL` | `stock_prices` | Канал pub/sub |
| `CLICKHOUSE_URL` | `jdbc:clickhouse://localhost:8123/stocksim?compress=0` | JDBC URL |
| `CLICKHOUSE_USER` | `default` | Пользователь ClickHouse |
| `CLICKHOUSE_PASSWORD` | *(пусто)* | Пароль ClickHouse |
| `CLICKHOUSE_DATABASE` | `stocksim` | База данных |
| `BATCH_FLUSH_INTERVAL_SECONDS` | `10` | Интервал flush в секундах |
| `BATCH_MAX_SIZE` | `1000` | Размер буфера для eager flush |

---

## Запуск

### Docker Compose (рекомендуется)

Поднимает GraphService + Redis + ClickHouse:

```bash
docker-compose up --build
```

Остановить и очистить данные:

```bash
docker-compose down -v
```

### Локально (Gradle)

Требуется запущенный Redis и ClickHouse.

```bash
./gradlew run
```

---

## Подключение к другим сервисам

### StocksMarketAPI (Go) → GraphService

StocksMarketAPI должен публиковать тики в Redis-канал `stock_prices`.  
GraphService подписывается на него автоматически при старте.

Формат сообщения:
```json
{
  "ticker": "AAPL",
  "buyPrice": 120.50,
  "sellPrice": 119.80,
  "timestamp": 1747561200000
}
```

GraphService **не делает** никаких HTTP-запросов к StocksMarketAPI — только читает из Redis.

---

### APIGateway → GraphService

APIGateway проксирует запросы от мобильных приложений к GraphService напрямую по HTTP.

Пример конфигурации роутинга в APIGateway (Ktor):

```kotlin
// В APIGateway — проксируем запросы на графики
get("/api/quotes/{ticker}/stats") {
    val ticker = call.parameters["ticker"]!!
    val granularity = call.request.queryParameters["granularity"] ?: "day"
    val response = httpClient.get("http://graph-service:8083/api/quotes/$ticker/stats") {
        parameter("granularity", granularity)
    }
    call.respond(response.body<StatsResponse>())
}
```

GraphService не требует авторизации — она происходит на уровне APIGateway.

---

### DomainService → GraphService

DomainService **не обращается** к GraphService напрямую.  
Котировки для сделок DomainService получает от StocksMarketAPI.  
GraphService — только для отображения графиков клиентам.

---

## Проверка работы вручную

### 1. Опубликовать тик в Redis

```bash
docker exec -it graph-service-redis-1 redis-cli
```

```
PUBLISH stock_prices '{"ticker":"AAPL","buyPrice":120.50,"sellPrice":119.80,"timestamp":1747561200000}'
```

Ответ `(integer) 1` означает что GraphService получил сообщение.

### 2. Подождать 10 секунд (интервал flush)

### 3. Проверить API

```bash
curl http://localhost:8083/health
curl http://localhost:8083/api/tickers
curl http://localhost:8083/api/quotes/AAPL/latest
curl "http://localhost:8083/api/quotes/AAPL/stats?granularity=hour"
```

### 4. Спаммер для генерации данных

```python
import redis, json, time, random

r = redis.Redis(host='localhost', port=6379)
tickers = ["AAPL", "GOOG", "MSFT", "AMZN", "TSLA"]
prices = {t: random.uniform(80, 200) for t in tickers}

while True:
    ticker = random.choice(tickers)
    prices[ticker] += random.uniform(-1.5, 1.5)
    base = max(10, prices[ticker])
    tick = {
        "ticker": ticker,
        "buyPrice": round(base + random.uniform(0.1, 0.5), 2),
        "sellPrice": round(base - random.uniform(0.1, 0.5), 2),
        "timestamp": int(time.time() * 1000)
    }
    r.publish("stock_prices", json.dumps(tick))
    print(f"→ {tick['ticker']} buy={tick['buyPrice']} sell={tick['sellPrice']}")
    time.sleep(0.3)
```

```bash
pip install redis
python spammer.py
```

---

## Структура проекта

```
graph-service/
├── Dockerfile
├── docker-compose.yml
├── clickhouse-users.xml          # конфиг пользователя ClickHouse
├── build.gradle.kts
└── src/main/kotlin/com/stocksim/graphservice/
    ├── Application.kt            # точка входа, Ktor module
    ├── config/
    │   ├── AppConfig.kt          # типизированная конфигурация
    │   └── KoinConfig.kt         # DI-модуль
    ├── consumer/
    │   └── PriceConsumer.kt      # Redis pub/sub + батчинг
    ├── model/
    │   └── Models.kt             # PriceTick, Candle, Granularity, ...
    ├── repository/
    │   └── QuoteRepository.kt    # ClickHouse DDL + read/write
    ├── service/
    │   └── GraphService.kt       # бизнес-слой
    └── routes/
        └── Routes.kt             # Ktor routing
```

---

## Коды ошибок

| HTTP | Описание |
|---|---|
| `200 OK` | Успешный ответ |
| `400 Bad Request` | Неверный параметр (например, неизвестный `granularity`) |
| `404 Not Found` | Тикер не найден в ClickHouse |
| `500 Internal Server Error` | Внутренняя ошибка, подробности в логах |

Тело ошибки всегда:
```json
{ "error": "описание ошибки" }
```