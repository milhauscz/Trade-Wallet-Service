CREATE TABLE wallets (
    id UUID PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    balance NUMERIC(19, 4) NOT NULL,
    reserved_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    currency CHAR(3) NOT NULL,
    CONSTRAINT uk_wallets_user_id UNIQUE (user_id),
    CONSTRAINT chk_wallet_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT chk_wallet_reserved_non_negative CHECK (reserved_amount >= 0),
    CONSTRAINT chk_wallet_reserved_lte_balance CHECK (reserved_amount <= balance)
);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    price NUMERIC(19, 4) NOT NULL,
    quantity NUMERIC(19, 4) NOT NULL,
    stop_loss NUMERIC(19, 4),
    status VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_orders_user_idempotency UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_orders_user_id ON orders (user_id);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    topic VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;

INSERT INTO wallets (id, user_id, balance, reserved_amount, currency)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'alice', 10000.0000, 0, 'USD'),
    ('22222222-2222-2222-2222-222222222222', 'bob', 10000.0000, 0, 'USD');
