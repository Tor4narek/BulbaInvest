package redis

import "github.com/BaraGodLike/BulbaInvest/services/market-service/internal/market"

const (
	MarketStocksHashKey        = "market:stocks"
	MarketStockKeyPrefix       = "market:stocks:"
	MarketQuotesUpdated        = "market.quotes.updated"
	DomainCompanyBuyStream     = "domain.trade.company.buy"
	DomainCompanySellStream    = "domain.trade.company.sell"
	MarketCompanyBuyResult     = "market.trade.company.buy.result"
	MarketCompanySellResult    = "market.trade.company.sell.result"
	processedEventKeyPrefix    = "market:processed:"
	processedEventTTLSeconds   = 86400
	quotesUpdatedMessageType   = "MARKET_QUOTES_UPDATED"
	defaultConsumerReadTimeout = 5_000
)

type QuotesUpdatedEvent struct {
	Type   string              `json:"type"`
	Quotes []market.StockQuote `json:"quotes"`
}
