package market

import (
	"encoding/json"
	"strings"
	"testing"
)

func TestStockQuoteJSON(t *testing.T) {
	quote := StockQuote{
		Ticker:            "AAPL",
		Price:             192.45,
		AvailableQuantity: 10000,
		UpdatedAtUnix:     1710000000,
	}

	payload, err := json.Marshal(quote)
	if err != nil {
		t.Fatalf("Marshal() error = %v", err)
	}

	got := string(payload)
	for _, want := range []string{`"ticker":"AAPL"`, `"price":192.45`, `"availableQuantity":10000`, `"updatedAt":1710000000`} {
		if !strings.Contains(got, want) {
			t.Fatalf("JSON %s does not contain %s", got, want)
		}
	}
}
