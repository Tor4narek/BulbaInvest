package redis

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/BaraGodLike/BulbaInvest/services/market-service/internal/market"
)

type CompanyTradeHandler interface {
	HandleCompanyBuy(ctx context.Context, event market.CompanyBuyRequested) (market.CompanyBuyResult, error)
	HandleCompanySell(ctx context.Context, event market.CompanySellRequested) (market.CompanySellResult, error)
}

type Consumer struct {
	client       *Client
	handler      CompanyTradeHandler
	group        string
	consumerName string
	log          *log.Logger
}

type StreamMessage struct {
	ID     string
	Fields map[string]string
}

func NewConsumer(client *Client, handler CompanyTradeHandler, group string, consumerName string, logger *log.Logger) *Consumer {
	if group == "" {
		group = "market-service"
	}
	if consumerName == "" {
		consumerName = "market-service-1"
	}
	if logger == nil {
		logger = log.Default()
	}
	return &Consumer{
		client:       client,
		handler:      handler,
		group:        group,
		consumerName: consumerName,
		log:          logger,
	}
}

func (c *Consumer) Start(ctx context.Context) {
	var wg sync.WaitGroup
	wg.Add(2)

	go func() {
		defer wg.Done()
		c.consume(ctx, DomainCompanyBuyStream, c.handleBuyMessage)
	}()
	go func() {
		defer wg.Done()
		c.consume(ctx, DomainCompanySellStream, c.handleSellMessage)
	}()

	wg.Wait()
}

func (c *Consumer) consume(ctx context.Context, stream string, handler func(context.Context, string, StreamMessage)) {
	for ctx.Err() == nil {
		if err := c.ensureGroup(ctx, stream); err != nil {
			c.log.Printf("redis stream group setup failed stream=%s: %v", stream, err)
			sleepContext(ctx, 2*time.Second)
			continue
		}
		break
	}

	c.log.Printf("redis stream consumer started stream=%s group=%s consumer=%s", stream, c.group, c.consumerName)
	for ctx.Err() == nil {
		messages, err := c.readMessages(ctx, stream)
		if err != nil {
			c.log.Printf("redis stream read failed stream=%s: %v", stream, err)
			sleepContext(ctx, time.Second)
			continue
		}

		for _, message := range messages {
			handler(ctx, stream, message)
		}
	}
	c.log.Printf("redis stream consumer stopped stream=%s", stream)
}

func (c *Consumer) ensureGroup(ctx context.Context, stream string) error {
	_, err := c.client.Do(ctx, "XGROUP", "CREATE", stream, c.group, "0", "MKSTREAM")
	if err != nil && !isBusyGroup(err) {
		return err
	}
	return nil
}

func (c *Consumer) readMessages(ctx context.Context, stream string) ([]StreamMessage, error) {
	reply, err := c.client.Do(
		ctx,
		"XREADGROUP",
		"GROUP", c.group, c.consumerName,
		"COUNT", 10,
		"BLOCK", defaultConsumerReadTimeout,
		"STREAMS", stream, ">",
	)
	if err != nil {
		return nil, err
	}
	if reply == nil {
		return nil, nil
	}
	return parseStreamMessages(reply), nil
}

func (c *Consumer) handleBuyMessage(ctx context.Context, stream string, message StreamMessage) {
	event, err := decodeBuyRequested(message)
	if err != nil {
		c.log.Printf("buy event decode failed streamId=%s: %v", message.ID, err)
		c.ack(ctx, stream, message.ID)
		return
	}
	if event.EventID == "" {
		event.EventID = message.ID
	}

	if c.isProcessed(ctx, event.EventID) {
		c.log.Printf("buy event skipped duplicate eventId=%s streamId=%s", event.EventID, message.ID)
		c.ack(ctx, stream, message.ID)
		return
	}

	if _, err := c.handler.HandleCompanyBuy(ctx, event); err != nil {
		c.log.Printf("buy event handling failed eventId=%s streamId=%s: %v", event.EventID, message.ID, err)
		return
	}

	c.markProcessed(ctx, event.EventID)
	c.ack(ctx, stream, message.ID)
}

func (c *Consumer) handleSellMessage(ctx context.Context, stream string, message StreamMessage) {
	event, err := decodeSellRequested(message)
	if err != nil {
		c.log.Printf("sell event decode failed streamId=%s: %v", message.ID, err)
		c.ack(ctx, stream, message.ID)
		return
	}
	if event.EventID == "" {
		event.EventID = message.ID
	}

	if c.isProcessed(ctx, event.EventID) {
		c.log.Printf("sell event skipped duplicate eventId=%s streamId=%s", event.EventID, message.ID)
		c.ack(ctx, stream, message.ID)
		return
	}

	if _, err := c.handler.HandleCompanySell(ctx, event); err != nil {
		c.log.Printf("sell event handling failed eventId=%s streamId=%s: %v", event.EventID, message.ID, err)
		return
	}

	c.markProcessed(ctx, event.EventID)
	c.ack(ctx, stream, message.ID)
}

func (c *Consumer) isProcessed(ctx context.Context, eventID string) bool {
	if eventID == "" {
		return false
	}

	reply, err := c.client.Do(ctx, "EXISTS", processedEventKeyPrefix+eventID)
	if err != nil {
		c.log.Printf("processed event check failed eventId=%s: %v", eventID, err)
		return false
	}

	value, ok := reply.(int64)
	return ok && value > 0
}

func (c *Consumer) markProcessed(ctx context.Context, eventID string) {
	if eventID == "" {
		return
	}

	if _, err := c.client.Do(ctx, "SET", processedEventKeyPrefix+eventID, "1", "EX", processedEventTTLSeconds); err != nil {
		c.log.Printf("processed event mark failed eventId=%s: %v", eventID, err)
	}
}

func (c *Consumer) ack(ctx context.Context, stream string, id string) {
	if _, err := c.client.Do(ctx, "XACK", stream, c.group, id); err != nil {
		c.log.Printf("stream ack failed stream=%s id=%s: %v", stream, id, err)
	}
}

func parseStreamMessages(reply any) []StreamMessage {
	streams, ok := reply.([]any)
	if !ok {
		return nil
	}

	var messages []StreamMessage
	for _, streamValue := range streams {
		streamEntry, ok := streamValue.([]any)
		if !ok || len(streamEntry) < 2 {
			continue
		}
		rawMessages, ok := streamEntry[1].([]any)
		if !ok {
			continue
		}

		for _, rawMessage := range rawMessages {
			messageEntry, ok := rawMessage.([]any)
			if !ok || len(messageEntry) < 2 {
				continue
			}
			id, ok := stringValue(messageEntry[0])
			if !ok {
				continue
			}
			rawFields, ok := messageEntry[1].([]any)
			if !ok {
				continue
			}

			fields := make(map[string]string, len(rawFields)/2)
			for i := 0; i+1 < len(rawFields); i += 2 {
				key, keyOK := stringValue(rawFields[i])
				value, valueOK := stringValue(rawFields[i+1])
				if keyOK && valueOK {
					fields[key] = value
				}
			}
			messages = append(messages, StreamMessage{ID: id, Fields: fields})
		}
	}

	return messages
}

func decodeBuyRequested(message StreamMessage) (market.CompanyBuyRequested, error) {
	if payload := message.Fields["payload"]; payload != "" {
		var event market.CompanyBuyRequested
		if err := json.Unmarshal([]byte(payload), &event); err != nil {
			return event, err
		}
		return event, nil
	}

	quantity, err := parseUintField(message.Fields, "quantity")
	if err != nil {
		return market.CompanyBuyRequested{}, err
	}
	price, err := parseFloatField(message.Fields, "price")
	if err != nil {
		return market.CompanyBuyRequested{}, err
	}
	return market.CompanyBuyRequested{
		EventID:  message.Fields["eventId"],
		TradeID:  message.Fields["tradeId"],
		UserID:   message.Fields["userId"],
		Ticker:   message.Fields["ticker"],
		Quantity: quantity,
		Price:    price,
	}, nil
}

func decodeSellRequested(message StreamMessage) (market.CompanySellRequested, error) {
	if payload := message.Fields["payload"]; payload != "" {
		var event market.CompanySellRequested
		if err := json.Unmarshal([]byte(payload), &event); err != nil {
			return event, err
		}
		return event, nil
	}

	quantity, err := parseUintField(message.Fields, "quantity")
	if err != nil {
		return market.CompanySellRequested{}, err
	}
	price, err := parseFloatField(message.Fields, "price")
	if err != nil {
		return market.CompanySellRequested{}, err
	}
	return market.CompanySellRequested{
		EventID:  message.Fields["eventId"],
		TradeID:  message.Fields["tradeId"],
		UserID:   message.Fields["userId"],
		Ticker:   message.Fields["ticker"],
		Quantity: quantity,
		Price:    price,
	}, nil
}

func parseUintField(fields map[string]string, name string) (uint64, error) {
	value := strings.TrimSpace(fields[name])
	if value == "" {
		return 0, fmt.Errorf("missing %s", name)
	}
	return strconv.ParseUint(value, 10, 64)
}

func parseFloatField(fields map[string]string, name string) (float64, error) {
	value := strings.TrimSpace(fields[name])
	if value == "" {
		return 0, nil
	}
	return strconv.ParseFloat(value, 64)
}

func sleepContext(ctx context.Context, duration time.Duration) {
	timer := time.NewTimer(duration)
	defer timer.Stop()

	select {
	case <-ctx.Done():
	case <-timer.C:
	}
}
