FROM golang:1.23-bookworm AS build

WORKDIR /src
COPY go.mod ./
RUN go mod download

COPY . .
RUN make -C driver test
RUN CGO_ENABLED=1 go test ./...
RUN CGO_ENABLED=1 go build -o /out/market-service ./cmd/market-service

FROM debian:bookworm-slim

WORKDIR /app
COPY --from=build /out/market-service /usr/local/bin/market-service

EXPOSE 8050
ENTRYPOINT ["market-service"]
