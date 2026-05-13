# StocksMarketAPI — контракт для DomainService

Сервис на Go, владеет остатками акций компании. DomainService дёргает его при сделках
с компанией. Идемпотентность обязательна: domain может ретраить вызов с тем же
`idempotencyKey` (UUID сделки) — остаток должен измениться ровно один раз.

Base URL берётся из переменной окружения `STOCKS_MARKET_API_URL` (например, `http://stocks-market:8080`).

## Эндпоинты

### `POST /api/v1/inventory/decrement`

Вызывается при покупке акций у компании (`BUY_FROM_COMPANY`). Уменьшает остаток.

**Request**
```json
{
  "ticker": "AAPL",
  "quantity": "10",
  "idempotencyKey": "9c3f4d2a-1a2b-4c3d-9e7f-1234567890ab"
}
```
- `ticker` — строка, тикер в верхнем регистре.
- `quantity` — десятичное число строкой (точность до 4 знаков). Всегда > 0.
- `idempotencyKey` — UUID сделки в DomainService.

**Response 200 OK**
```json
{
  "ticker": "AAPL",
  "remaining": "12345.0000",
  "idempotencyKey": "9c3f4d2a-1a2b-4c3d-9e7f-1234567890ab",
  "applied": true
}
```
- `remaining` — новый остаток акций у компании после операции.
- `applied` — `true`, если операция фактически применилась; `false`, если это повтор по тому же `idempotencyKey` (тогда `remaining` — текущее состояние без изменений).

### `POST /api/v1/inventory/increment`

Вызывается при продаже акций компании (`SELL_TO_COMPANY`). Увеличивает остаток. Тело и ответ — идентичны `decrement`.

## Коды ошибок

| HTTP | Тело | Когда |
|------|------|-------|
| `409 Conflict` | `{"code":"INSUFFICIENT_STOCKS","message":"..."}` | (только для decrement) у компании меньше `quantity` |
| `409 Conflict` | `{"code":"UNKNOWN_TICKER","message":"..."}` | тикер не зарегистрирован |
| `422 Unprocessable Entity` | `{"code":"VALIDATION","message":"..."}` | невалидный формат полей, отрицательное `quantity` и т.п. |
| `5xx` | произвольно | domain ретраит до 2 раз с экспонентой |

## Требования к идемпотентности

- Хранить пары `(idempotencyKey, ticker, quantity, delta_sign, resulting_remaining)` минимум 7 дней.
- При повторе с **тем же** ключом и **теми же** параметрами — вернуть `applied=false` и текущий `remaining`, **не меняя** состояние.
- При повторе с тем же ключом, но другими параметрами — `409` с `code=IDEMPOTENCY_MISMATCH`.

## Дополнительно (опционально, на будущее)

`GET /api/v1/inventory/{ticker}` — текущий остаток. Domain пока не использует, удобно для отладки.

```json
{ "ticker": "AAPL", "remaining": "12345.0000" }
```
