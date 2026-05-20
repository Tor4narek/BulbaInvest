package main

import (
	"context"
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/BaraGodLike/BulbaInvest/services/market-service/internal/config"
	"github.com/BaraGodLike/BulbaInvest/services/market-service/internal/driver"
	"github.com/BaraGodLike/BulbaInvest/services/market-service/internal/inventorypg"
	"github.com/BaraGodLike/BulbaInvest/services/market-service/internal/market"
	marketredis "github.com/BaraGodLike/BulbaInvest/services/market-service/internal/redis"
)

func main() {
	logger := log.New(os.Stdout, "", log.LstdFlags|log.LUTC)
	cfg := config.Load()

	if err := driver.Init(cfg.MarketDriverConfig); err != nil {
		logger.Fatalf("driver init failed: %v", err)
	}
	defer driver.Shutdown()

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	inventoryRepo, err := inventorypg.Open(ctx, cfg.InventoryDB, logger)
	if err != nil {
		logger.Fatalf("inventory repository init failed: %v", err)
	}
	if inventoryRepo != nil {
		defer func() {
			if err := inventoryRepo.Close(); err != nil {
				logger.Printf("inventory repository close failed: %v", err)
			}
		}()
		if err := restoreAvailableQuantities(ctx, inventoryRepo, logger); err != nil {
			logger.Fatalf("inventory restore failed: %v", err)
		}
	}

	redisClient := marketredis.NewClient(cfg.RedisAddr, cfg.RedisPassword, cfg.RedisDB)
	publisher := marketredis.NewPublisher(redisClient)
	service := market.NewService(driver.New(), publisher, cfg.TickInterval, logger)
	if inventoryRepo != nil {
		service.SetInventoryStore(inventoryRepo)
	}
	consumer := marketredis.NewConsumer(redisClient, service, cfg.ConsumerGroup, cfg.ConsumerName, logger)

	go service.StartTickLoop(ctx)
	go consumer.Start(ctx)

	server := &http.Server{
		Addr:              cfg.HTTPAddr,
		Handler:           routes(ctx, service, publisher, redisClient),
		ReadHeaderTimeout: 5 * time.Second,
	}

	go func() {
		logger.Printf("http server listening addr=%s", cfg.HTTPAddr)
		if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			logger.Fatalf("http server failed: %v", err)
		}
	}()

	<-ctx.Done()

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := server.Shutdown(shutdownCtx); err != nil {
		logger.Printf("http server shutdown failed: %v", err)
	}
}

func routes(ctx context.Context, service inventoryService, publisher quoteReader, redisClient redisCommander) http.Handler {
	mux := http.NewServeMux()
	inventoryHandler := newInventoryHandler(ctx, service, redisClient)
	mux.HandleFunc("GET /health", func(w http.ResponseWriter, _ *http.Request) {
		writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
	})
	mux.HandleFunc("POST /api/v1/inventory/decrement", inventoryHandler.handleDecrement)
	mux.HandleFunc("POST /api/v1/inventory/increment", inventoryHandler.handleIncrement)
	mux.HandleFunc("GET /stocks", func(w http.ResponseWriter, _ *http.Request) {
		quotes, err := publisher.LoadQuotes(ctx)
		if err != nil || len(quotes) == 0 {
			quotes, err = service.Snapshot()
		}
		if err != nil {
			writeError(w, http.StatusInternalServerError, err)
			return
		}
		writeJSON(w, http.StatusOK, quotes)
	})
	mux.HandleFunc("GET /stocks/", func(w http.ResponseWriter, r *http.Request) {
		ticker := strings.ToUpper(strings.Trim(strings.TrimPrefix(r.URL.Path, "/stocks/"), "/"))
		if ticker == "" {
			http.NotFound(w, r)
			return
		}

		quote, found, err := publisher.LoadQuote(ctx, ticker)
		if err != nil || !found {
			var snapshotErr error
			quote, found, snapshotErr = snapshotQuote(service, ticker)
			if snapshotErr != nil {
				writeError(w, http.StatusInternalServerError, snapshotErr)
				return
			}
		}
		if err != nil && !found {
			writeError(w, http.StatusInternalServerError, err)
			return
		}
		if !found {
			http.NotFound(w, r)
			return
		}
		writeJSON(w, http.StatusOK, quote)
	})
	return mux
}

type inventorySnapshotLoader interface {
	LoadQuantities(ctx context.Context) (map[string]uint64, error)
}

func restoreAvailableQuantities(ctx context.Context, loader inventorySnapshotLoader, logger *log.Logger) error {
	quantities, err := loader.LoadQuantities(ctx)
	if err != nil {
		return err
	}
	for ticker, quantity := range quantities {
		if err := driver.SetAvailableQuantity(ticker, quantity); err != nil {
			if errors.Is(err, driver.ErrUnknownTicker) {
				logger.Printf("market inventory restore skipped ticker=%s reason=unknown ticker in driver", ticker)
				continue
			}
			return err
		}
	}
	if len(quantities) > 0 {
		logger.Printf("market inventory restored tickers=%d", len(quantities))
	}
	return nil
}

func snapshotQuote(service inventoryService, ticker string) (market.StockQuote, bool, error) {
	quotes, err := service.Snapshot()
	if err != nil {
		return market.StockQuote{}, false, err
	}
	for _, quote := range quotes {
		if quote.Ticker == ticker {
			return quote, true, nil
		}
	}
	return market.StockQuote{}, false, nil
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}

func writeError(w http.ResponseWriter, status int, err error) {
	writeJSON(w, status, map[string]string{"error": err.Error()})
}
