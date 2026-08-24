-- V3 — order enrichment: audit timestamps, a human-readable reference, shipping address.
--
-- created_at is the important one. Without it an order history cannot be sorted by date,
-- and keyset pagination has no key to page on. This migration is what makes
-- "WHERE (created_at, id) < (?, ?) ORDER BY created_at DESC, id DESC" possible.

ALTER TABLE orders ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE orders ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Human-readable reference. Customers quote this in support tickets; nobody reads a UUID
-- over the phone. Nullable first so existing rows can be backfilled deterministically.
ALTER TABLE orders ADD COLUMN order_number VARCHAR(20);

UPDATE orders
SET order_number = 'ORD-' || to_char(created_at, 'YYYY') || '-' || upper(substr(md5(id), 1, 8))
WHERE order_number IS NULL;

ALTER TABLE orders ALTER COLUMN order_number SET NOT NULL;
ALTER TABLE orders ADD CONSTRAINT orders_order_number_key UNIQUE (order_number);

-- JSONB, matching the project's existing snapshot convention (see order_product_snapshot).
-- An address is a value object read as a whole, never queried field-by-field, so a JSON
-- document beats six nullable columns.
ALTER TABLE orders ADD COLUMN shipping_address JSONB;

-- THE index for keyset pagination of a customer's order history.
-- Column order matters: customer_id first (equality filter), then the sort keys in the
-- exact direction the query uses, so Postgres walks the index instead of sorting.
-- id is the tiebreaker — two orders can share a created_at timestamp.
CREATE INDEX idx_orders_customer_created
    ON orders (customer_id, created_at DESC, id DESC);
