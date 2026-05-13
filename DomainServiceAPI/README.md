# DomainService

Сервис домена BulbaInvest: пользователи, кошельки, портфели, ордера и сделки.

## Стек

- **Kotlin** + **Ktor 3** (Netty), порт `8040`
- **PostgreSQL** + **Exposed** + **Flyway**
- **Redis (Jedis)** — хранение временных кодов входа
- **Redis (broker)** — заглушка для получения котировок от StocksService
- **java-jwt** (Auth0) — JWT
- **Jakarta Mail** + **MailHog** — отправка кодов входа (SMTP-симулятор с веб-интерфейсом)
- **Koin** — DI

## Запуск

Поднимает приложение, PostgreSQL, Redis (основной), Redis-брокер котировок и MailHog:

```bash
docker compose up --build
```

- API: <http://localhost:8040>
- MailHog UI (письма с кодом): <http://localhost:8025>
- Health: `GET /health`

Локально без Docker:

```bash
./gradlew run
```

(нужны запущенные PostgreSQL/Redis/MailHog — параметры в `application.yaml` / переменных окружения).

## Заглушки

- **StocksMarketAPI (Go)** — `external/StocksMarketApiClient.kt`: только логирует. Подставить реальные HTTP-вызовы, когда сервис появится.
- **Котировки из Redis-брокера** — `external/QuotesBrokerClient.kt`: пытается читать строковое значение цены по ключу `quotes:<TICKER>` из broker-redis, иначе возвращает `100.00`. Заменить при получении реального формата.

Установить тестовую цену вручную:

```bash
docker exec -it $(docker compose ps -q broker-redis) redis-cli set quotes:AAPL 215.50
```

## Auth flow

1. `POST /api/auth/code/request {"email":"u@ex.com"}` — генерирует 6-значный код, кладёт в Redis (TTL 5 мин), отправляет на email (видно в MailHog).
2. `POST /api/auth/code/confirm {"email":"u@ex.com","code":"123456"}` — создаёт пользователя (если новый) с дефолтным кошельком в BYN и возвращает JWT.
3. Остальные эндпоинты — `Authorization: Bearer <token>`.

## Схема БД

См. `src/main/resources/db/migration/V1__init.sql`. Применяется Flyway при старте.

## Эндпоинты

Полный контракт — `../docs/domainservice.yaml`. Реализованы:

- `/api/auth/code/{request,confirm}`
- `/api/users/me` (GET, PATCH)
- `/api/wallets/me`, `POST /api/wallets`, `/api/wallets/{id}/make-default`, `DELETE /api/wallets/{id}`
- `/api/portfolio`, `/api/portfolio/{ticker}`
- `/api/trades/company/{buy,sell}`, `/api/trades`
- `/api/order-book/{ticker}`, `/api/orders/{sell,buy,my}`, `/api/orders/sell/{id}/{buy,cancel}`
