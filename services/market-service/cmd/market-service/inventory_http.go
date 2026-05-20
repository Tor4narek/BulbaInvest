package main

import (
	"context"
	"encoding/json"
	"errors"
	"math/big"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/BaraGodLike/BulbaInvest/services/market-service/internal/market"
)

const (
	httpInventoryIdempotencyPrefix = "market:http:idempotency:"
	httpInventoryPendingMarker     = "__pending__"
	httpInventoryTTL               = 24 * time.Hour
	httpInventoryPendingTTL        = 30 * time.Second
)

type quoteReader interface {
	LoadQuotes(ctx context.Context) ([]market.StockQuote, error)
	LoadQuote(ctx context.Context, ticker string) (market.StockQuote, bool, error)
}

type inventoryService interface {
	Snapshot() ([]market.StockQuote, error)
	HandleCompanyBuy(ctx context.Context, event market.CompanyBuyRequested) (market.CompanyBuyResult, error)
	HandleCompanySell(ctx context.Context, event market.CompanySellRequested) (market.CompanySellResult, error)
}

type redisCommander interface {
	Do(ctx context.Context, args ...any) (any, error)
}

type inventoryHandler struct {
	ctx     context.Context
	service inventoryService
	redis   redisCommander
}

type inventoryRequest struct {
	Ticker         string `json:"ticker"`
	Quantity       string `json:"quantity"`
	IdempotencyKey string `json:"idempotencyKey"`
}

type inventoryResponse struct {
	Ticker         string `json:"ticker"`
	Remaining      string `json:"remaining"`
	IdempotencyKey string `json:"idempotencyKey"`
	Applied        bool   `json:"applied"`
}

type errorResponse struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

type storedOutcome struct {
	StatusCode      int                `json:"statusCode"`
	Response        *inventoryResponse `json:"response,omitempty"`
	Error           *errorResponse     `json:"error,omitempty"`
	DuplicateApplied bool              `json:"duplicateApplied,omitempty"`
}

func newInventoryHandler(ctx context.Context, service inventoryService, redis redisCommander) *inventoryHandler {
	return &inventoryHandler{ctx: ctx, service: service, redis: redis}
}

func (h *inventoryHandler) handleDecrement(w http.ResponseWriter, r *http.Request) {
	h.handleInventory(w, r, true)
}

func (h *inventoryHandler) handleIncrement(w http.ResponseWriter, r *http.Request) {
	h.handleInventory(w, r, false)
}

func (h *inventoryHandler) handleInventory(w http.ResponseWriter, r *http.Request, decrement bool) {
	var req inventoryRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusUnprocessableEntity, errorResponse{Code: "invalid_request", Message: "invalid JSON body"})
		return
	}

	ticker := strings.ToUpper(strings.TrimSpace(req.Ticker))
	if ticker == "" {
		writeJSON(w, http.StatusUnprocessableEntity, errorResponse{Code: "invalid_ticker", Message: "ticker is required"})
		return
	}

	idempotencyKey := strings.TrimSpace(req.IdempotencyKey)
	if idempotencyKey == "" {
		writeJSON(w, http.StatusUnprocessableEntity, errorResponse{Code: "invalid_idempotency_key", Message: "idempotencyKey is required"})
		return
	}

	quantity, err := parseWholeQuantity(req.Quantity)
	if err != nil {
		writeJSON(w, http.StatusUnprocessableEntity, errorResponse{Code: "invalid_quantity", Message: err.Error()})
		return
	}

	lockState, err := h.claimIdempotency(idempotencyKey)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err)
		return
	}
	if lockState != nil {
		h.writeStoredOutcome(w, *lockState, true)
		return
	}

	var outcome storedOutcome
	if decrement {
		outcome, err = h.processDecrement(ticker, quantity, idempotencyKey)
	} else {
		outcome, err = h.processIncrement(ticker, quantity, idempotencyKey)
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, err)
		return
	}
	if err := h.storeOutcome(idempotencyKey, outcome); err != nil {
		writeError(w, http.StatusInternalServerError, err)
		return
	}

	h.writeStoredOutcome(w, outcome, false)
}

func (h *inventoryHandler) processDecrement(ticker string, quantity uint64, idempotencyKey string) (storedOutcome, error) {
	result, err := h.service.HandleCompanyBuy(h.ctx, market.CompanyBuyRequested{
		EventID:  idempotencyKey,
		TradeID:  idempotencyKey,
		UserID:   "http-domain-service",
		Ticker:   ticker,
		Quantity: quantity,
	})
	if err != nil {
		return storedOutcome{}, err
	}
	if !result.Accepted {
		return rejectionOutcome(idempotencyKey, result.Reason), nil
	}

	return storedOutcome{
		StatusCode: http.StatusOK,
		Response: &inventoryResponse{
			Ticker:         result.Ticker,
			Remaining:      strconv.FormatUint(result.RemainingAvailableQuantity, 10),
			IdempotencyKey: idempotencyKey,
			Applied:        true,
		},
		DuplicateApplied: false,
	}, nil
}

func (h *inventoryHandler) processIncrement(ticker string, quantity uint64, idempotencyKey string) (storedOutcome, error) {
	result, err := h.service.HandleCompanySell(h.ctx, market.CompanySellRequested{
		EventID:  idempotencyKey,
		TradeID:  idempotencyKey,
		UserID:   "http-domain-service",
		Ticker:   ticker,
		Quantity: quantity,
	})
	if err != nil {
		return storedOutcome{}, err
	}
	if !result.Accepted {
		return rejectionOutcome(idempotencyKey, result.Reason), nil
	}

	return storedOutcome{
		StatusCode: http.StatusOK,
		Response: &inventoryResponse{
			Ticker:         result.Ticker,
			Remaining:      strconv.FormatUint(result.RemainingAvailableQuantity, 10),
			IdempotencyKey: idempotencyKey,
			Applied:        true,
		},
		DuplicateApplied: false,
	}, nil
}

func rejectionOutcome(idempotencyKey string, reason *string) storedOutcome {
	message := "market request rejected"
	if reason != nil && strings.TrimSpace(*reason) != "" {
		message = strings.TrimSpace(*reason)
	}
	return storedOutcome{
		StatusCode: http.StatusConflict,
		Error: &errorResponse{
			Code:    "inventory_conflict",
			Message: message,
		},
	}
}

func (h *inventoryHandler) claimIdempotency(idempotencyKey string) (*storedOutcome, error) {
	key := httpInventoryIdempotencyPrefix + idempotencyKey
	reply, err := h.redis.Do(h.ctx, "SET", key, httpInventoryPendingMarker, "NX", "EX", int(httpInventoryPendingTTL.Seconds()))
	if err != nil {
		return nil, err
	}
	if reply != nil {
		return nil, nil
	}

	stored, err := h.redis.Do(h.ctx, "GET", key)
	if err != nil {
		return nil, err
	}

	value, ok := redisString(stored)
	if !ok || value == "" || value == httpInventoryPendingMarker {
		return &storedOutcome{
			StatusCode: http.StatusConflict,
			Error: &errorResponse{
				Code:    "request_in_progress",
				Message: "request with this idempotencyKey is already being processed",
			},
		}, nil
	}

	var outcome storedOutcome
	if err := json.Unmarshal([]byte(value), &outcome); err != nil {
		return nil, err
	}
	return &outcome, nil
}

func (h *inventoryHandler) storeOutcome(idempotencyKey string, outcome storedOutcome) error {
	payload, err := json.Marshal(outcome)
	if err != nil {
		return err
	}
	_, err = h.redis.Do(h.ctx, "SET", httpInventoryIdempotencyPrefix+idempotencyKey, string(payload), "EX", int(httpInventoryTTL.Seconds()))
	return err
}

func (h *inventoryHandler) writeStoredOutcome(w http.ResponseWriter, outcome storedOutcome, duplicate bool) {
	switch {
	case outcome.Response != nil:
		response := *outcome.Response
		if duplicate {
			response.Applied = outcome.DuplicateApplied
		}
		writeJSON(w, outcome.StatusCode, response)
	case outcome.Error != nil:
		writeJSON(w, outcome.StatusCode, outcome.Error)
	default:
		writeJSON(w, http.StatusInternalServerError, errorResponse{Code: "internal_error", Message: "empty stored outcome"})
	}
}

func parseWholeQuantity(raw string) (uint64, error) {
	value := strings.TrimSpace(raw)
	if value == "" {
		return 0, errors.New("quantity is required")
	}

	rat, ok := new(big.Rat).SetString(value)
	if !ok {
		return 0, errors.New("quantity must be a valid number")
	}
	if rat.Sign() <= 0 {
		return 0, errors.New("quantity must be greater than zero")
	}
	if !rat.IsInt() {
		return 0, errors.New("quantity must be a whole number")
	}

	num := rat.Num()
	if !num.IsUint64() {
		return 0, errors.New("quantity is too large")
	}
	return num.Uint64(), nil
}

func redisString(value any) (string, bool) {
	switch typed := value.(type) {
	case string:
		return typed, true
	case []byte:
		return string(typed), true
	default:
		return "", false
	}
}
