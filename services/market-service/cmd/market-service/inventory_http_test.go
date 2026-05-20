package main

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/BaraGodLike/BulbaInvest/services/market-service/internal/market"
)

type stubInventoryService struct {
	buyResult  market.CompanyBuyResult
	sellResult market.CompanySellResult
}

func (s stubInventoryService) Snapshot() ([]market.StockQuote, error) {
	return nil, nil
}

func (s stubInventoryService) HandleCompanyBuy(_ context.Context, event market.CompanyBuyRequested) (market.CompanyBuyResult, error) {
	result := s.buyResult
	if result.Ticker == "" {
		result.Ticker = event.Ticker
	}
	if result.TradeID == "" {
		result.TradeID = event.TradeID
	}
	return result, nil
}

func (s stubInventoryService) HandleCompanySell(_ context.Context, event market.CompanySellRequested) (market.CompanySellResult, error) {
	result := s.sellResult
	if result.Ticker == "" {
		result.Ticker = event.Ticker
	}
	if result.TradeID == "" {
		result.TradeID = event.TradeID
	}
	return result, nil
}

type stubRedis struct {
	values map[string]string
}

func newStubRedis() *stubRedis {
	return &stubRedis{values: map[string]string{}}
}

func (r *stubRedis) Do(_ context.Context, args ...any) (any, error) {
	command := strings.ToUpper(args[0].(string))
	switch command {
	case "SET":
		key := args[1].(string)
		value := args[2].(string)
		if len(args) >= 5 && strings.ToUpper(args[3].(string)) == "NX" {
			if _, exists := r.values[key]; exists {
				return nil, nil
			}
			r.values[key] = value
			return "OK", nil
		}
		r.values[key] = value
		return "OK", nil
	case "GET":
		key := args[1].(string)
		if value, ok := r.values[key]; ok {
			return value, nil
		}
		return nil, nil
	default:
		return nil, nil
	}
}

func TestInventoryDecrementReturnsDomainCompatibleResponse(t *testing.T) {
	handler := newInventoryHandler(context.Background(), stubInventoryService{
		buyResult: market.CompanyBuyResult{
			Accepted:                   true,
			Ticker:                     "AAPL",
			RemainingAvailableQuantity: 9997,
		},
	}, newStubRedis())

	req := httptest.NewRequest(http.MethodPost, "/api/v1/inventory/decrement", strings.NewReader(`{"ticker":"AAPL","quantity":"3","idempotencyKey":"evt-1"}`))
	w := httptest.NewRecorder()

	handler.handleDecrement(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Code)
	}

	var resp inventoryResponse
	if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if resp.Ticker != "AAPL" || resp.Remaining != "9997" || resp.IdempotencyKey != "evt-1" || !resp.Applied {
		t.Fatalf("unexpected response: %+v", resp)
	}
}

func TestInventoryDuplicateRequestReturnsAppliedFalse(t *testing.T) {
	service := stubInventoryService{
		buyResult: market.CompanyBuyResult{
			Accepted:                   true,
			Ticker:                     "AAPL",
			RemainingAvailableQuantity: 9997,
		},
	}
	redis := newStubRedis()
	handler := newInventoryHandler(context.Background(), service, redis)

	firstReq := httptest.NewRequest(http.MethodPost, "/api/v1/inventory/decrement", strings.NewReader(`{"ticker":"AAPL","quantity":"3","idempotencyKey":"evt-1"}`))
	firstW := httptest.NewRecorder()
	handler.handleDecrement(firstW, firstReq)

	secondReq := httptest.NewRequest(http.MethodPost, "/api/v1/inventory/decrement", strings.NewReader(`{"ticker":"AAPL","quantity":"3","idempotencyKey":"evt-1"}`))
	secondW := httptest.NewRecorder()
	handler.handleDecrement(secondW, secondReq)

	if secondW.Code != http.StatusOK {
		t.Fatalf("expected 200 for duplicate, got %d", secondW.Code)
	}

	var resp inventoryResponse
	if err := json.Unmarshal(secondW.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode duplicate response: %v", err)
	}
	if resp.Applied {
		t.Fatalf("expected applied=false for duplicate, got %+v", resp)
	}
}

func TestInventoryRejectsFractionalQuantity(t *testing.T) {
	handler := newInventoryHandler(context.Background(), stubInventoryService{}, newStubRedis())

	req := httptest.NewRequest(http.MethodPost, "/api/v1/inventory/decrement", strings.NewReader(`{"ticker":"AAPL","quantity":"1.5","idempotencyKey":"evt-1"}`))
	w := httptest.NewRecorder()

	handler.handleDecrement(w, req)

	if w.Code != http.StatusUnprocessableEntity {
		t.Fatalf("expected 422, got %d", w.Code)
	}
}
