# BulbaInvest

Educational investment platform prototype.

- Domain-service API contract: [OpenAPI (Swagger UI)](https://editor.swagger.io/?url=https://raw.githubusercontent.com/BaraGodLike/BulbaInvest/main/docs/domainservice.yaml?v=2)
- Domain-service implementation: `services/domain-service`
- Market simulation service: `services/market-service`

`market-service` embeds a pure C exchange simulation driver through cgo, updates stock quotes on a timer, stores current quotes in Redis, and exchanges company buy/sell market events with domain-service through Redis Streams.

See [services/market-service/README.md](services/market-service/README.md) for architecture, Redis keys, event formats, and run instructions.
