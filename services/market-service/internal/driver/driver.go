package driver

/*
#cgo CFLAGS: -I${SRCDIR}/../../driver
#include <stdlib.h>
#include "exchange_driver.h"
*/
import "C"

import (
	"errors"
	"fmt"
	"sync"
	"unsafe"

	"github.com/BaraGodLike/BulbaInvest/services/market-service/internal/market"
)

var mu sync.Mutex

var (
	ErrNotInitialized       = errors.New("driver is not initialized")
	ErrUnknownTicker        = errors.New("unknown ticker")
	ErrInsufficientQuantity = errors.New("insufficient available quantity")
	ErrQuantityOverflow     = errors.New("quantity overflow")
)

type PackageDriver struct{}

func New() PackageDriver {
	return PackageDriver{}
}

func (PackageDriver) Tick() error {
	return Tick()
}

func (PackageDriver) Snapshot() ([]market.StockQuote, error) {
	return Snapshot()
}

func (PackageDriver) ApplyBuy(ticker string, quantity uint64) error {
	return ApplyBuy(ticker, quantity)
}

func (PackageDriver) ApplySell(ticker string, quantity uint64) error {
	return ApplySell(ticker, quantity)
}

func Init(configPath string) error {
	mu.Lock()
	defer mu.Unlock()

	var cPath *C.char
	if configPath != "" {
		cPath = C.CString(configPath)
		defer C.free(unsafe.Pointer(cPath))
	}

	if rc := C.exchange_init(cPath); rc != 0 {
		return fmt.Errorf("exchange init failed: code %d", int(rc))
	}
	return nil
}

func Tick() error {
	mu.Lock()
	defer mu.Unlock()

	if rc := C.exchange_tick(); rc != 0 {
		return exchangeError("tick", int(rc))
	}
	return nil
}

func Snapshot() ([]market.StockQuote, error) {
	mu.Lock()
	defer mu.Unlock()

	var snapshot C.MarketSnapshot
	if rc := C.exchange_get_snapshot(&snapshot); rc != 0 {
		return nil, exchangeError("snapshot", int(rc))
	}
	defer C.exchange_free_snapshot(&snapshot)

	if snapshot.len == 0 || snapshot.items == nil {
		return nil, nil
	}

	cItems := unsafe.Slice(snapshot.items, int(snapshot.len))
	quotes := make([]market.StockQuote, 0, len(cItems))
	for _, item := range cItems {
		quotes = append(quotes, market.StockQuote{
			Ticker:            C.GoString(&item.ticker[0]),
			Price:             float64(item.price),
			AvailableQuantity: uint64(item.available_quantity),
			Volatility:        float64(item.volatility),
			UpdatedAtUnix:     int64(item.updated_at_unix),
		})
	}

	return quotes, nil
}

func ApplyBuy(ticker string, quantity uint64) error {
	mu.Lock()
	defer mu.Unlock()

	cTicker := C.CString(ticker)
	defer C.free(unsafe.Pointer(cTicker))

	if rc := C.exchange_apply_buy(cTicker, C.uint64_t(quantity)); rc != 0 {
		return exchangeError("buy", int(rc))
	}
	return nil
}

func ApplySell(ticker string, quantity uint64) error {
	mu.Lock()
	defer mu.Unlock()

	cTicker := C.CString(ticker)
	defer C.free(unsafe.Pointer(cTicker))

	if rc := C.exchange_apply_sell(cTicker, C.uint64_t(quantity)); rc != 0 {
		return exchangeError("sell", int(rc))
	}
	return nil
}

func Shutdown() {
	mu.Lock()
	defer mu.Unlock()
	C.exchange_shutdown()
}

func exchangeError(operation string, code int) error {
	switch code {
	case -1:
		return fmt.Errorf("exchange %s failed: %w", operation, ErrNotInitialized)
	case -2:
		return fmt.Errorf("exchange %s failed: %w", operation, ErrUnknownTicker)
	case -3:
		return fmt.Errorf("exchange %s failed: %w", operation, ErrInsufficientQuantity)
	case -4:
		return fmt.Errorf("exchange %s failed: %w", operation, ErrQuantityOverflow)
	default:
		return fmt.Errorf("exchange %s failed: code %d", operation, code)
	}
}

func IsInsufficientQuantity(err error) bool {
	return errors.Is(err, ErrInsufficientQuantity)
}
