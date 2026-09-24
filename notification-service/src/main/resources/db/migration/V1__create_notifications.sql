-- V1: notification-service schema. Structure only — rows are created by consumed events.

CREATE TABLE notifications (
    id             VARCHAR(36)  NOT NULL,
    -- The producer's event id. UNIQUE is the real idempotency guard: the "have I seen it?"
    -- lookup in code is a fast path, but two deliveries racing past that lookup still cannot
    -- both insert. The database is the only place a check-then-act race is truly closed.
    event_id       VARCHAR(64)  NOT NULL,
    type           VARCHAR(32)  NOT NULL,
    status         VARCHAR(16)  NOT NULL,
    product_id     VARCHAR(255) NOT NULL,   -- cross-service reference (no FK)
    product_name   VARCHAR(255),
    stock_quantity INTEGER      NOT NULL,
    threshold      INTEGER      NOT NULL,
    recipient      VARCHAR(255) NOT NULL,
    attempts       INTEGER      NOT NULL DEFAULT 0,
    occurred_at    TIMESTAMPTZ  NOT NULL,   -- when product-service saw the crossing
    created_at     TIMESTAMPTZ  NOT NULL,   -- when we consumed it (the gap = consumer lag)
    sent_at        TIMESTAMPTZ,
    CONSTRAINT notifications_pkey PRIMARY KEY (id),
    CONSTRAINT uq_notifications_event_id UNIQUE (event_id),
    CONSTRAINT notifications_status_check CHECK (status IN ('PENDING', 'SENT')),
    CONSTRAINT notifications_type_check CHECK (type IN ('STOCK_LOW'))
);

-- GET /api/notifications lists newest first
CREATE INDEX idx_notifications_created_at ON notifications (created_at DESC);
