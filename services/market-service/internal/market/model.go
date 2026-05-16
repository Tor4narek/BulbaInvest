package market

type StockQuote struct {
	Ticker            string  `json:"ticker"`
	Price             float64 `json:"price"`
	AvailableQuantity uint64  `json:"availableQuantity"`
	Volatility        float64 `json:"volatility,omitempty"`
	UpdatedAtUnix     int64   `json:"updatedAt"`
}

type CompanyBuyRequested struct {
	EventID  string  `json:"eventId"`
	TradeID  string  `json:"tradeId"`
	UserID   string  `json:"userId"`
	Ticker   string  `json:"ticker"`
	Quantity uint64  `json:"quantity"`
	Price    float64 `json:"price"`
}

type CompanyBuyResult struct {
	EventID                    string  `json:"eventId"`
	TradeID                    string  `json:"tradeId"`
	Ticker                     string  `json:"ticker"`
	Quantity                   uint64  `json:"quantity"`
	Accepted                   bool    `json:"accepted"`
	ActualPrice                float64 `json:"actualPrice"`
	RemainingAvailableQuantity uint64  `json:"remainingAvailableQuantity"`
	Reason                     *string `json:"reason,omitempty"`
}

type CompanySellRequested struct {
	EventID  string  `json:"eventId"`
	TradeID  string  `json:"tradeId"`
	UserID   string  `json:"userId"`
	Ticker   string  `json:"ticker"`
	Quantity uint64  `json:"quantity"`
	Price    float64 `json:"price"`
}

type CompanySellResult struct {
	EventID                    string  `json:"eventId"`
	TradeID                    string  `json:"tradeId"`
	Ticker                     string  `json:"ticker"`
	Quantity                   uint64  `json:"quantity"`
	Accepted                   bool    `json:"accepted"`
	ActualPrice                float64 `json:"actualPrice"`
	RemainingAvailableQuantity uint64  `json:"remainingAvailableQuantity"`
	Reason                     *string `json:"reason,omitempty"`
}
