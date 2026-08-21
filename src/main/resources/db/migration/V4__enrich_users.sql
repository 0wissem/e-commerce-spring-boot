-- V4 — user enrichment: audit timestamps, phone, default address.
--
-- All additive and safe under a rolling deploy: timestamps have defaults, the rest are nullable.

ALTER TABLE users ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE users ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE users ADD COLUMN phone VARCHAR(32);

-- JSONB, mirroring orders.shipping_address. This is the address the checkout pre-fills;
-- the order keeps its own frozen COPY, because a customer moving house must not rewrite
-- where past orders were shipped. Same reasoning as the product snapshot.
ALTER TABLE users ADD COLUMN default_address JSONB;

-- Supports "newest customers first" without a sort.
CREATE INDEX idx_users_created_at ON users (created_at DESC);
