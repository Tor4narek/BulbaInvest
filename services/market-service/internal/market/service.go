package market

import (
	"context"
	"log"
	"strings"
	"time"
)

type Driver interface {
	Tick() error
	Snapshot() ([]StockQuote, error)
	ApplyBuy(ticker string, quantity uint64) error
	ApplySell(ticker string, quantity uint64) error
}

type InventoryStore interface {
	SaveQuantity(ctx context.Context, ticker string, quantity uint64) error
}

type Publisher interface {
	StoreQuotes(ctx context.Context, quotes []StockQuote) error
	PublishQuotesUpdated(ctx context.Context, quotes []StockQuote) error
	PublishCompanyBuyResult(ctx context.Context, result CompanyBuyResult) error
	PublishCompanySellResult(ctx context.Context, result CompanySellResult) error
}

type Service struct {
	driver       Driver
	publisher    Publisher
	inventory    InventoryStore
	tickInterval time.Duration
	log          *log.Logger
}

func NewService(driver Driver, publisher Publisher, tickInterval time.Duration, logger *log.Logger) *Service {
	if logger == nil {
		logger = log.Default()
	}
	if tickInterval <= 0 {
		tickInterval = 10 * time.Second
	}

	return &Service{
		driver:       driver,
		publisher:    publisher,
		tickInterval: tickInterval,
		log:          logger,
	}
}

func (s *Service) SetInventoryStore(store InventoryStore) {
	s.inventory = store
}

func (s *Service) StartTickLoop(ctx context.Context) {
	s.log.Printf("market tick loop started interval=%s", s.tickInterval)
	s.publishCurrentSnapshot(ctx)

	ticker := time.NewTicker(s.tickInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			s.log.Printf("market tick loop stopped")
			return
		case <-ticker.C:
			if err := s.TickOnce(ctx); err != nil {
				s.log.Printf("market tick failed: %v", err)
			}
		}
	}
}

func (s *Service) TickOnce(ctx context.Context) error {
	if err := s.driver.Tick(); err != nil {
		return err
	}

	quotes, err := s.driver.Snapshot()
	if err != nil {
		return err
	}

	if err := s.publisher.StoreQuotes(ctx, quotes); err != nil {
		return err
	}
	if err := s.publisher.PublishQuotesUpdated(ctx, quotes); err != nil {
		return err
	}

	s.log.Printf("market quotes updated count=%d", len(quotes))
	return nil
}

func (s *Service) Snapshot() ([]StockQuote, error) {
	return s.driver.Snapshot()
}

func (s *Service) HandleCompanyBuy(ctx context.Context, event CompanyBuyRequested) (CompanyBuyResult, error) {
	event.Ticker = normalizeTicker(event.Ticker)
	result := CompanyBuyResult{
		EventID:  event.EventID,
		TradeID:  event.TradeID,
		Ticker:   event.Ticker,
		Quantity: event.Quantity,
	}

	quote, found, err := s.quoteByTicker(event.Ticker)
	if err != nil {
		return result, err
	}
	if !found {
		result.Reason = reason("unknown ticker")
		return s.publishBuyResult(ctx, result)
	}

	result.ActualPrice = quote.Price
	result.RemainingAvailableQuantity = quote.AvailableQuantity

	if event.Quantity == 0 {
		result.Reason = reason("quantity must be greater than zero")
		return s.publishBuyResult(ctx, result)
	}

	if err := s.driver.ApplyBuy(event.Ticker, event.Quantity); err != nil {
		result.Reason = reason(err.Error())
		return s.publishBuyResult(ctx, result)
	}

	quotes, err := s.driver.Snapshot()
	if err != nil {
		return result, err
	}

	if next, ok := findQuote(quotes, event.Ticker); ok {
		result.ActualPrice = next.Price
		result.RemainingAvailableQuantity = next.AvailableQuantity
	}
	result.Accepted = true

	if s.inventory != nil {
		if err := s.inventory.SaveQuantity(ctx, result.Ticker, result.RemainingAvailableQuantity); err != nil {
			return result, err
		}
	}

	if err := s.publisher.StoreQuotes(ctx, quotes); err != nil {
		return result, err
	}
	if err := s.publisher.PublishQuotesUpdated(ctx, quotes); err != nil {
		return result, err
	}

	return s.publishBuyResult(ctx, result)
}

func (s *Service) HandleCompanySell(ctx context.Context, event CompanySellRequested) (CompanySellResult, error) {
	event.Ticker = normalizeTicker(event.Ticker)
	result := CompanySellResult{
		EventID:  event.EventID,
		TradeID:  event.TradeID,
		Ticker:   event.Ticker,
		Quantity: event.Quantity,
	}

	quote, found, err := s.quoteByTicker(event.Ticker)
	if err != nil {
		return result, err
	}
	if !found {
		result.Reason = reason("unknown ticker")
		return s.publishSellResult(ctx, result)
	}

	result.ActualPrice = quote.Price
	result.RemainingAvailableQuantity = quote.AvailableQuantity

	if event.Quantity == 0 {
		result.Reason = reason("quantity must be greater than zero")
		return s.publishSellResult(ctx, result)
	}

	if err := s.driver.ApplySell(event.Ticker, event.Quantity); err != nil {
		result.Reason = reason(err.Error())
		return s.publishSellResult(ctx, result)
	}

	quotes, err := s.driver.Snapshot()
	if err != nil {
		return result, err
	}

	if next, ok := findQuote(quotes, event.Ticker); ok {
		result.ActualPrice = next.Price
		result.RemainingAvailableQuantity = next.AvailableQuantity
	}
	result.Accepted = true

	if s.inventory != nil {
		if err := s.inventory.SaveQuantity(ctx, result.Ticker, result.RemainingAvailableQuantity); err != nil {
			return result, err
		}
	}

	if err := s.publisher.StoreQuotes(ctx, quotes); err != nil {
		return result, err
	}
	if err := s.publisher.PublishQuotesUpdated(ctx, quotes); err != nil {
		return result, err
	}

	return s.publishSellResult(ctx, result)
}

func (s *Service) quoteByTicker(ticker string) (StockQuote, bool, error) {
	if ticker == "" {
		return StockQuote{}, false, nil
	}

	quotes, err := s.driver.Snapshot()
	if err != nil {
		return StockQuote{}, false, err
	}

	quote, ok := findQuote(quotes, ticker)
	return quote, ok, nil
}

func (s *Service) publishCurrentSnapshot(ctx context.Context) {
	quotes, err := s.driver.Snapshot()
	if err != nil {
		s.log.Printf("market initial snapshot failed: %v", err)
		return
	}
	if err := s.publisher.StoreQuotes(ctx, quotes); err != nil {
		s.log.Printf("market initial quote store failed: %v", err)
		return
	}
	if err := s.publisher.PublishQuotesUpdated(ctx, quotes); err != nil {
		s.log.Printf("market initial quote publish failed: %v", err)
		return
	}
	s.log.Printf("market initial quotes published count=%d", len(quotes))
}

func (s *Service) publishBuyResult(ctx context.Context, result CompanyBuyResult) (CompanyBuyResult, error) {
	if err := s.publisher.PublishCompanyBuyResult(ctx, result); err != nil {
		return result, err
	}
	s.log.Printf("company buy handled eventId=%s tradeId=%s ticker=%s accepted=%t", result.EventID, result.TradeID, result.Ticker, result.Accepted)
	return result, nil
}

func (s *Service) publishSellResult(ctx context.Context, result CompanySellResult) (CompanySellResult, error) {
	if err := s.publisher.PublishCompanySellResult(ctx, result); err != nil {
		return result, err
	}
	s.log.Printf("company sell handled eventId=%s tradeId=%s ticker=%s accepted=%t", result.EventID, result.TradeID, result.Ticker, result.Accepted)
	return result, nil
}

func findQuote(quotes []StockQuote, ticker string) (StockQuote, bool) {
	for _, quote := range quotes {
		if strings.EqualFold(quote.Ticker, ticker) {
			return quote, true
		}
	}
	return StockQuote{}, false
}

func normalizeTicker(ticker string) string {
	return strings.ToUpper(strings.TrimSpace(ticker))
}

func reason(message string) *string {
	message = strings.TrimSpace(message)
	if message == "" {
		message = "market request rejected"
	}
	return &message
}
