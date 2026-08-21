-- V2 — EXPAND phase of the money type migration (double precision -> numeric).
--
-- Why: `price DOUBLE PRECISION` is binary floating point. Money in a double cannot represent
-- 0.10 exactly, so per-line rounding error accumulates and an order total stops matching the
-- sum of its lines. Money belongs in NUMERIC (exact decimal), read into a Java BigDecimal.
--
-- Why this is split across migrations (expand / contract):
-- During a rolling deploy two code versions run at once. The OLD version does not know about
-- `price_amount`, so if this migration set NOT NULL immediately, every insert from an old
-- instance would fail. So:
--   V2 (here)   - add the column NULLABLE, backfill existing rows.        <- old code still fine
--   (deploy)    - new code writes BOTH `price` and `price_amount`.
--   V3          - SET NOT NULL, once every writer populates it.
--   (later)     - a separate migration finally DROPs `price`.
-- The old column is intentionally left in place and still NOT NULL this week.

ALTER TABLE products ADD COLUMN price_amount NUMERIC(12, 2);

-- Backfill: every existing row gets the exact decimal equivalent of its stored double.
-- Rounded HALF_UP to 2 decimals, matching the application's rounding policy.
UPDATE products
SET price_amount = ROUND(price::numeric, 2)
WHERE price_amount IS NULL;

-- An amount without a currency is not money. ISO-4217 alpha-3.
-- DEFAULT makes this safe for old code paths that don't set it.
-- VARCHAR(3), not CHAR(3): Postgres reports CHAR as `bpchar`, which fails Hibernate's
-- schema validation against a String field (it expects varchar). CHAR also blank-pads
-- its values and buys nothing in Postgres.
ALTER TABLE products ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'EUR';

ALTER TABLE products ADD CONSTRAINT products_currency_check
    CHECK (currency ~ '^[A-Z]{3}$');

-- Guard: prices are never negative. Cheap to add now, awkward to add once bad data exists.
ALTER TABLE products ADD CONSTRAINT products_price_amount_non_negative
    CHECK (price_amount IS NULL OR price_amount >= 0);
