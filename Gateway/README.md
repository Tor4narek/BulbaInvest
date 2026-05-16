# Gateway

Gateway принимает клиентские HTTP-запросы и проксирует их в downstream-сервисы.

На первом этапе реализован прокси в `DomainService`:

- локальный `GET /health`
- все маршруты `/api/**` форвардятся в `DomainService`

Адрес `DomainService` задаётся через `DOMAIN_SERVICE_URL`.

## Запуск

```bash
./gradlew run
```

По умолчанию Gateway слушает порт `8050`.
