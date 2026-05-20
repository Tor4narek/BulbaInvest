CREATE TABLE users (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(320) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE user_wallets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    currency VARCHAR(8) NOT NULL,
    amount NUMERIC(20, 4) NOT NULL DEFAULT 0,
    reserved_amount NUMERIC(20, 4) NOT NULL DEFAULT 0,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_user_wallets_user ON user_wallets(user_id);
CREATE UNIQUE INDEX idx_user_wallets_default ON user_wallets(user_id) WHERE is_default;

CREATE TABLE portfolio_positions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    ticker VARCHAR(32) NOT NULL,
    quantity NUMERIC(20, 4) NOT NULL DEFAULT 0,
    reserved_quantity NUMERIC(20, 4) NOT NULL DEFAULT 0,
    average_buy_price NUMERIC(20, 4) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_portfolio_user_ticker UNIQUE (user_id, ticker)
);

CREATE TABLE sell_orders (
    id UUID PRIMARY KEY,
    seller_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    ticker VARCHAR(32) NOT NULL,
    quantity NUMERIC(20, 4) NOT NULL,
    price NUMERIC(20, 4) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    matched_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    expired_at TIMESTAMP
);
CREATE INDEX idx_sell_orders_ticker_status ON sell_orders(ticker, status);
CREATE INDEX idx_sell_orders_seller ON sell_orders(seller_user_id);

CREATE TABLE trades (
    id UUID PRIMARY KEY,
    buyer_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    seller_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    buyer_wallet_id UUID REFERENCES user_wallets(id) ON DELETE SET NULL,
    seller_wallet_id UUID REFERENCES user_wallets(id) ON DELETE SET NULL,
    counterparty_type VARCHAR(16) NOT NULL,
    trade_type VARCHAR(32) NOT NULL,
    ticker VARCHAR(32) NOT NULL,
    quantity NUMERIC(20, 4) NOT NULL,
    price NUMERIC(20, 4) NOT NULL,
    total_amount NUMERIC(20, 4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_trades_buyer ON trades(buyer_user_id);
CREATE INDEX idx_trades_seller ON trades(seller_user_id);
CREATE INDEX idx_trades_ticker ON trades(ticker);
