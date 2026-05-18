package redis

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/BaraGodLike/BulbaInvest/services/market-service/internal/market"
)

type Publisher struct {
	client *Client
}

func NewPublisher(client *Client) *Publisher {
	return &Publisher{client: client}
}

func (p *Publisher) StoreQuotes(ctx context.Context, quotes []market.StockQuote) error {
	if len(quotes) == 0 {
		return nil
	}

	args := make([]any, 0, 2+len(quotes)*2)
	args = append(args, "HSET", MarketStocksHashKey)

	for _, quote := range quotes {
		payload, err := json.Marshal(quote)
		if err != nil {
			return fmt.Errorf("marshal quote %s: %w", quote.Ticker, err)
		}
		args = append(args, quote.Ticker, string(payload))

		if _, err := p.client.Do(ctx, "SET", MarketStockKeyPrefix+quote.Ticker, string(payload)); err != nil {
			return fmt.Errorf("store quote key %s: %w", quote.Ticker, err)
		}
	}

	if _, err := p.client.Do(ctx, args...); err != nil {
		return fmt.Errorf("store quote hash: %w", err)
	}

	return nil
}

func (p *Publisher) PublishQuotesUpdated(ctx context.Context, quotes []market.StockQuote) error {
	payload, err := json.Marshal(QuotesUpdatedEvent{
		Type:   quotesUpdatedMessageType,
		Quotes: quotes,
	})
	if err != nil {
		return fmt.Errorf("marshal quotes updated event: %w", err)
	}

	if _, err := p.client.Do(ctx, "PUBLISH", MarketQuotesUpdated, string(payload)); err != nil {
		return fmt.Errorf("publish quotes updated: %w", err)
	}
	return nil
}

func (p *Publisher) PublishCompanyBuyResult(ctx context.Context, result market.CompanyBuyResult) error {
	return p.xaddJSON(ctx, MarketCompanyBuyResult, result)
}

func (p *Publisher) PublishCompanySellResult(ctx context.Context, result market.CompanySellResult) error {
	return p.xaddJSON(ctx, MarketCompanySellResult, result)
}

func (p *Publisher) xaddJSON(ctx context.Context, stream string, event any) error {
	payload, err := json.Marshal(event)
	if err != nil {
		return fmt.Errorf("marshal %s event: %w", stream, err)
	}

	if _, err := p.client.Do(ctx, "XADD", stream, "*", "payload", string(payload)); err != nil {
		return fmt.Errorf("xadd %s: %w", stream, err)
	}
	return nil
}

func (p *Publisher) LoadQuotes(ctx context.Context) ([]market.StockQuote, error) {
	reply, err := p.client.Do(ctx, "HGETALL", MarketStocksHashKey)
	if err != nil {
		return nil, fmt.Errorf("hgetall quotes: %w", err)
	}

	items, ok := reply.([]any)
	if !ok || len(items) == 0 {
		return nil, nil
	}

	quotes := make([]market.StockQuote, 0, len(items)/2)
	for i := 0; i+1 < len(items); i += 2 {
		value, ok := stringValue(items[i+1])
		if !ok {
			continue
		}
		var quote market.StockQuote
		if err := json.Unmarshal([]byte(value), &quote); err != nil {
			continue
		}
		quotes = append(quotes, quote)
	}

	return quotes, nil
}

func (p *Publisher) LoadQuote(ctx context.Context, ticker string) (market.StockQuote, bool, error) {
	reply, err := p.client.Do(ctx, "HGET", MarketStocksHashKey, ticker)
	if err != nil {
		return market.StockQuote{}, false, fmt.Errorf("hget quote %s: %w", ticker, err)
	}
	if reply == nil {
		return market.StockQuote{}, false, nil
	}

	value, ok := stringValue(reply)
	if !ok {
		return market.StockQuote{}, false, nil
	}

	var quote market.StockQuote
	if err := json.Unmarshal([]byte(value), &quote); err != nil {
		return market.StockQuote{}, false, err
	}
	return quote, true, nil
}
