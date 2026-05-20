package inventorypg

import (
	"context"
	"database/sql"
	"fmt"
	"log"
	"math"
	"time"

	_ "github.com/jackc/pgx/v5/stdlib"

	"github.com/BaraGodLike/BulbaInvest/services/market-service/internal/config"
)

const schemaSQL = `
CREATE TABLE IF NOT EXISTS market_company_inventory (
    ticker TEXT PRIMARY KEY,
    available_quantity BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
)`

type Repository struct {
	db  *sql.DB
	log *log.Logger
}

func Open(ctx context.Context, cfg config.InventoryDBConfig, logger *log.Logger) (*Repository, error) {
	if !cfg.Enabled() {
		return nil, nil
	}

	if logger == nil {
		logger = log.Default()
	}

	dsn := fmt.Sprintf(
		"host=%s port=%d dbname=%s user=%s password=%s sslmode=%s",
		cfg.Host,
		cfg.Port,
		cfg.Name,
		cfg.User,
		cfg.Password,
		cfg.SSLMode,
	)

	db, err := sql.Open("pgx", dsn)
	if err != nil {
		return nil, err
	}
	db.SetMaxOpenConns(4)
	db.SetMaxIdleConns(2)
	db.SetConnMaxLifetime(30 * time.Minute)

	pingCtx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()
	if err := db.PingContext(pingCtx); err != nil {
		_ = db.Close()
		return nil, err
	}

	if _, err := db.ExecContext(ctx, schemaSQL); err != nil {
		_ = db.Close()
		return nil, err
	}

	logger.Printf("market inventory persistence enabled host=%s db=%s", cfg.Host, cfg.Name)
	return &Repository{db: db, log: logger}, nil
}

func (r *Repository) Close() error {
	if r == nil || r.db == nil {
		return nil
	}
	return r.db.Close()
}

func (r *Repository) LoadQuantities(ctx context.Context) (map[string]uint64, error) {
	rows, err := r.db.QueryContext(ctx, `SELECT ticker, available_quantity FROM market_company_inventory`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	quantities := make(map[string]uint64)
	for rows.Next() {
		var ticker string
		var quantity int64
		if err := rows.Scan(&ticker, &quantity); err != nil {
			return nil, err
		}
		if quantity < 0 {
			if r.log != nil {
				r.log.Printf("market inventory row ignored ticker=%s reason=negative quantity", ticker)
			}
			continue
		}
		quantities[ticker] = uint64(quantity)
	}
	return quantities, rows.Err()
}

func (r *Repository) SaveQuantity(ctx context.Context, ticker string, quantity uint64) error {
	if quantity > math.MaxInt64 {
		return fmt.Errorf("quantity %d exceeds postgres BIGINT range", quantity)
	}

	_, err := r.db.ExecContext(ctx, `
		INSERT INTO market_company_inventory (ticker, available_quantity, updated_at)
		VALUES ($1, $2, NOW())
		ON CONFLICT (ticker) DO UPDATE
		SET available_quantity = EXCLUDED.available_quantity,
		    updated_at = NOW()
	`, ticker, int64(quantity))
	return err
}
