package market

import (
	"context"
	"errors"
	"sync"
	"testing"
	"time"
)

type fakeDriver struct {
	mu     sync.Mutex
	quotes []StockQuote
}

func (d *fakeDriver) Tick() error {
	return nil
}

func (d *fakeDriver) Snapshot() ([]StockQuote, error) {
	d.mu.Lock()
	defer d.mu.Unlock()

	quotes := make([]StockQuote, len(d.quotes))
	copy(quotes, d.quotes)
	return quotes, nil
}

func (d *fakeDriver) ApplyBuy(ticker string, quantity uint64) error {
	d.mu.Lock()
	defer d.mu.Unlock()

	for i := range d.quotes {
		if d.quotes[i].Ticker == ticker {
			if d.quotes[i].AvailableQuantity < quantity {
				return errors.New("insufficient available quantity")
			}
			d.quotes[i].AvailableQuantity -= quantity
			return nil
		}
	}
	return errors.New("unknown ticker")
}

func (d *fakeDriver) ApplySell(ticker string, quantity uint64) error {
	d.mu.Lock()
	defer d.mu.Unlock()

	for i := range d.quotes {
		if d.quotes[i].Ticker == ticker {
			d.quotes[i].AvailableQuantity += quantity
			return nil
		}
	}
	return errors.New("unknown ticker")
}

type fakePublisher struct {
	buyResults []CompanyBuyResult
}

type fakeInventoryStore struct {
	ticker   string
	quantity uint64
	calls    int
}

func (s *fakeInventoryStore) SaveQuantity(_ context.Context, ticker string, quantity uint64) error {
	s.ticker = ticker
	s.quantity = quantity
	s.calls++
	return nil
}

func (p *fakePublisher) StoreQuotes(context.Context, []StockQuote) error {
	return nil
}

func (p *fakePublisher) PublishQuotesUpdated(context.Context, []StockQuote) error {
	return nil
}

func (p *fakePublisher) PublishCompanyBuyResult(_ context.Context, result CompanyBuyResult) error {
	p.buyResults = append(p.buyResults, result)
	return nil
}

func (p *fakePublisher) PublishCompanySellResult(context.Context, CompanySellResult) error {
	return nil
}

func TestHandleCompanyBuyAccepted(t *testing.T) {
	driver := &fakeDriver{quotes: []StockQuote{{Ticker: "AAPL", Price: 100, AvailableQuantity: 10, UpdatedAtUnix: time.Now().Unix()}}}
	publisher := &fakePublisher{}
	service := NewService(driver, publisher, time.Second, nil)
	store := &fakeInventoryStore{}
	service.SetInventoryStore(store)

	result, err := service.HandleCompanyBuy(context.Background(), CompanyBuyRequested{
		EventID:  "evt-1",
		TradeID:  "trade-1",
		Ticker:   "aapl",
		Quantity: 3,
	})
	if err != nil {
		t.Fatalf("HandleCompanyBuy() error = %v", err)
	}
	if !result.Accepted {
		t.Fatalf("Accepted = false, reason = %v", result.Reason)
	}
	if result.RemainingAvailableQuantity != 7 {
		t.Fatalf("RemainingAvailableQuantity = %d, want 7", result.RemainingAvailableQuantity)
	}
	if store.calls != 1 || store.ticker != "AAPL" || store.quantity != 7 {
		t.Fatalf("inventory store = %+v, want ticker=AAPL quantity=7 calls=1", store)
	}
}

func TestHandleCompanyBuyRejectedWhenInsufficient(t *testing.T) {
	driver := &fakeDriver{quotes: []StockQuote{{Ticker: "AAPL", Price: 100, AvailableQuantity: 2, UpdatedAtUnix: time.Now().Unix()}}}
	publisher := &fakePublisher{}
	service := NewService(driver, publisher, time.Second, nil)

	result, err := service.HandleCompanyBuy(context.Background(), CompanyBuyRequested{
		EventID:  "evt-2",
		TradeID:  "trade-2",
		Ticker:   "AAPL",
		Quantity: 3,
	})
	if err != nil {
		t.Fatalf("HandleCompanyBuy() error = %v", err)
	}
	if result.Accepted {
		t.Fatal("Accepted = true, want false")
	}
	if result.Reason == nil {
		t.Fatal("Reason is nil")
	}
	if result.RemainingAvailableQuantity != 2 {
		t.Fatalf("RemainingAvailableQuantity = %d, want 2", result.RemainingAvailableQuantity)
	}
}
