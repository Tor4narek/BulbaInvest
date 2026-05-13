# Quotes Broker (Redis) — контракт публикации котировок

StocksService публикует котировки в общий Redis-инстанс. DomainService читает оттуда
последнюю известную цену по тикеру для расчёта `BUY_FROM_COMPANY` / `SELL_TO_COMPANY`.

Параметры подключения для domain: `BROKER_REDIS_HOST`, `BROKER_REDIS_PORT`,
`BROKER_QUOTES_PREFIX` (по умолчанию `quotes:last:`).

## Snapshot последней котировки — обязательно

**Тип ключа:** `String`
**Имя ключа:** `quotes:last:<TICKER>`, тикер в верхнем регистре.
**TTL:** не задавать (либо ≥ 24 ч). DomainService падает с `404`, если ключ отсутствует.
**Payload:** JSON в UTF-8.

```json
{
  "ticker": "AAPL",
  "price": "215.50",
  "bid": "215.45",
  "ask": "215.55",
  "volume": 12345678,
  "timestamp": "2026-05-12T10:00:00Z",
  "source": "MOEX"
}
```

| Поле | Тип | Обязательно | Описание |
|------|------|------------|----------|
| `ticker` | string | да | Совпадает с суффиксом ключа |
| `price` | string (decimal) | да | Цена последней сделки. **Строка**, чтобы не терять точность |
| `bid` | string (decimal) | нет | Лучшая цена покупки |
| `ask` | string (decimal) | нет | Лучшая цена продажи |
| `volume` | int64 | нет | Объём за торговую сессию |
| `timestamp` | string (ISO-8601 UTC) | да | Время формирования котировки |
| `source` | string | нет | Источник: `"MOEX"`, `"NASDAQ"`, и т.д. |

Пример записи через `redis-cli`:
```bash
redis-cli SET quotes:last:AAPL '{"ticker":"AAPL","price":"215.50","timestamp":"2026-05-12T10:00:00Z"}'
```

## Pub/Sub живых обновлений — опционально

Если в будущем понадобится стримить котировки клиентам через GraphService, удобно
параллельно публиковать в канал:

**Канал:** `quotes:updates`
**Payload:** тот же JSON, что и в snapshot.

Подписчик должен сам обновлять локальный snapshot ключ — StocksService обязан делать
`SET quotes:last:<TICKER>` + `PUBLISH quotes:updates` атомарно (в одном пайплайне или
Lua-скрипте), чтобы snapshot и стрим не расходились.

DomainService **подписку не использует** — читает только snapshot перед каждой сделкой.

## Гарантии, которых ждёт DomainService

1. `quotes:last:<TICKER>` всегда содержит **последнюю** известную цену (а не цену на момент открытия и т.п.).
2. Поле `price` — строка с десятичной точкой (`"215.50"`), не число с плавающей запятой.
3. Если торги по тикеру остановлены — ключ не удалять, оставлять последнюю цену.
