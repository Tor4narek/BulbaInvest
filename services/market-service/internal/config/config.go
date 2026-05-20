package config

import (
	"os"
	"strconv"
	"time"
)

type Config struct {
	HTTPAddr           string
	RedisAddr          string
	RedisPassword      string
	RedisDB            int
	InventoryDB        InventoryDBConfig
	MarketDriverConfig string
	TickInterval       time.Duration
	ConsumerGroup      string
	ConsumerName       string
}

type InventoryDBConfig struct {
	Host     string
	Port     int
	Name     string
	User     string
	Password string
	SSLMode  string
}

func (c InventoryDBConfig) Enabled() bool {
	return c.Host != "" && c.Name != "" && c.User != ""
}

func Load() Config {
	return Config{
		HTTPAddr:           envString("HTTP_ADDR", ":8050"),
		RedisAddr:          envString("REDIS_ADDR", "localhost:6379"),
		RedisPassword:      envString("REDIS_PASSWORD", ""),
		RedisDB:            envInt("REDIS_DB", 0),
		InventoryDB: InventoryDBConfig{
			Host:     envString("MARKET_DB_HOST", ""),
			Port:     envInt("MARKET_DB_PORT", 5432),
			Name:     envString("MARKET_DB_NAME", ""),
			User:     envString("MARKET_DB_USER", ""),
			Password: envString("MARKET_DB_PASSWORD", ""),
			SSLMode:  envString("MARKET_DB_SSLMODE", "disable"),
		},
		MarketDriverConfig: envString("MARKET_DRIVER_CONFIG", ""),
		TickInterval:       envDuration("MARKET_TICK_INTERVAL", 500*time.Millisecond),
		ConsumerGroup:      envString("MARKET_CONSUMER_GROUP", "market-service"),
		ConsumerName:       envString("MARKET_CONSUMER_NAME", "market-service-1"),
	}
}

func envString(key string, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}

func envInt(key string, fallback int) int {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}

	parsed, err := strconv.Atoi(value)
	if err != nil {
		return fallback
	}
	return parsed
}

func envDuration(key string, fallback time.Duration) time.Duration {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}

	parsed, err := time.ParseDuration(value)
	if err != nil {
		return fallback
	}
	return parsed
}
